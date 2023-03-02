/*
 * File: bru.scala                                                             *
 * Created Date: 2023-02-26 09:21:29 am                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-26 09:30:21 am                                       *
 * Modified By: Mathieu Escouteloup                                            *
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.core.abondance.int

import chisel3._
import chisel3.util._
import chisel3.experimental.ChiselEnum

import herd.common.gen._
import herd.common.mem.cbo.{CboIO, CboBus, OP => CBOOP, SORT => CBOSORT, BLOCK => CBOBLOCK}
import herd.core.abondance.common._
import herd.core.abondance.back.{BranchBus, StatBus, GprWriteIO, EndIO}
import herd.core.aubrac.nlp.{BranchInfoBus}


object BruFSM extends ChiselEnum {
  val s0IDLE, s1CBO, s2HINT = Value
}

class Bru (p: IntUnitParams) extends Module {
  import herd.core.abondance.int.INTUOP._
  import herd.core.abondance.int.BruFSM._

  val io = IO(new Bundle {
    val i_flush = Input(Bool())
    val o_free = Output(Bool())

    val i_field = if (p.useField) Some(Input(UInt(log2Ceil(p.nField).W))) else None
    val b_in = Flipped(new GenRVIO(p, new IntUnitCtrlBus(p), new IntUnitDataBus(p)))

    val i_br_up = Input(UInt(p.nSpecBranch.W))
    val i_br_new = Input(new BranchBus(p.nAddrBit, p.nSpecBranch, p.nRobEntry))

    val b_write = Flipped(new GprWriteIO(p.nDataBit, p.nGprPhy))
    val b_end = new EndIO(p.debug, p.nAddrBit, p.nDataBit, p.nRobEntry)
    
    val o_br_up = Output(UInt(p.nSpecBranch.W))
    val o_br_new = Output(new BranchBus(p.nAddrBit, p.nSpecBranch, p.nRobEntry))
    val o_br_info = Output(new BranchInfoBus(p.nAddrBit))
    val o_flush = Output(Bool())
    val b_cbo = if (p.useCbo) Some(new CboIO(1, p.useField, p.nField, p.nAddrBit)) else None
  })

  val init_cbo = Wire(new CboBus(p.nHart, p.useField, p.nField, p.nAddrBit))

  if (p.useExtZicbo) {
    init_cbo := DontCare
    init_cbo.valid := false.B
    init_cbo.ready := false.B
  } else {
    init_cbo := DontCare
    init_cbo.valid := false.B
    init_cbo.ready := false.B
    init_cbo.op := CBOOP.FLUSH
    init_cbo.sort := CBOSORT.E
    init_cbo.block := CBOBLOCK.FULL
  }

  val r_fsm = RegInit(s0IDLE)
  val r_cbo = RegInit(init_cbo)
  val r_write = RegInit(true.B)

  val w_done = Wire(Bool())
  val w_lock = Wire(Bool())
  val w_flush_in = Wire(Bool())

  w_flush_in := io.i_flush | (io.i_br_new.valid & io.b_in.ctrl.get.br.mask(io.i_br_new.tag))

  // ******************************
  //             FSM
  // ******************************
  w_done := false.B
  
  switch(r_fsm) {
    is (s0IDLE) {
      w_done := true.B

      when (io.b_in.valid) {
        if (p.useExtZifencei) {
          switch (io.b_in.ctrl.get.ctrl.uop) {
            is (FENCEI) {
              r_fsm := s1CBO
              w_done := false.B

              r_cbo.valid := true.B
              r_cbo.ready := false.B
              r_cbo.hart := 0.U
              if (p.useField) r_cbo.field.get := io.i_field.get
              r_cbo.op := CBOOP.FLUSH
              r_cbo.sort := CBOSORT.E
              r_cbo.block := CBOBLOCK.FULL
            }
          }
        } 
        if (p.useExtZicbo) {
          switch (io.b_in.ctrl.get.ctrl.uop) {
            is (CLEAN, INVAL, FLUSH, ZERO) {
              r_fsm := s1CBO
              w_done := false.B

              r_cbo.valid := true.B
              r_cbo.ready := false.B
              r_cbo.hart := 0.U
              if (p.useField) r_cbo.field.get := io.i_field.get
              r_cbo.sort := CBOSORT.A
              r_cbo.block := CBOBLOCK.LINE
              r_cbo.addr := io.b_in.data.get.s1

              switch (io.b_in.ctrl.get.ctrl.uop) {
                is (CLEAN)  {r_cbo.op := CBOOP.CLEAN}
                is (INVAL)  {r_cbo.op := CBOOP.INVAL}
                is (FLUSH)  {r_cbo.op := CBOOP.FLUSH}
                is (ZERO)   {r_cbo.op := CBOOP.ZERO}
              }
            }

            is (PFTCHE, PFTCHR, PFTCHW) {
              r_fsm := s2HINT
              w_done := false.B

              r_cbo.valid := true.B
              r_cbo.ready := false.B
              r_cbo.hart := 0.U
              if (p.useField) r_cbo.field.get := io.i_field.get
              r_cbo.op := CBOOP.PFTCH
              r_cbo.addr := io.b_in.data.get.s1

              switch (io.b_in.ctrl.get.ctrl.uop) {
                is (PFTCHE) {r_cbo.sort := CBOSORT.E}
                is (PFTCHR) {r_cbo.sort := CBOSORT.R}
                is (PFTCHW) {r_cbo.sort := CBOSORT.W}
              }
            }
          }
        } 
      } 
    }

    is (s1CBO) {
      w_done := r_cbo.ready
      r_cbo.ready := io.b_cbo.get.ready
      when (r_cbo.ready & ~w_lock) {
        r_fsm := s0IDLE
        r_cbo.valid := false.B   
      }
    }

    is (s2HINT) {    
      w_done := true.B  
      when (~w_lock) {
        r_fsm := s0IDLE   
        r_cbo.valid := false.B      
      } 
    }
  }

  when (w_flush_in) {
    r_fsm := s0IDLE
  }

  // ******************************
  //            LOGIC
  // ******************************
  val w_sign = Wire(Bool())

  val w_br = Wire(Bool())
  val w_taken = Wire(Bool())
  val w_jal = Wire(Bool())
  val w_jalr = Wire(Bool())
  val w_call = Wire(Bool())
  val w_ret = Wire(Bool())
  val w_flush_pipe = Wire(Bool())
  val w_use_tag = Wire(Bool())

  // ------------------------------
  //            DEFAULT
  // ------------------------------  
  w_sign := io.b_in.ctrl.get.ctrl.ssign(0) | io.b_in.ctrl.get.ctrl.ssign(1)

  w_br := false.B
  w_taken := false.B
  w_jal := false.B
  w_jalr := false.B
  w_call := false.B
  w_ret := false.B
  w_flush_pipe := false.B
  if (p.useFastJal) {
    w_use_tag := w_jalr | w_br
  } else {
    w_use_tag := w_jal | w_jalr | w_br
  }

  switch (io.b_in.ctrl.get.ctrl.uop) {
    // ------------------------------
    //             JUMP
    // ------------------------------
    is (JAL) {
      w_jal := true.B
      w_call := io.b_in.ctrl.get.ctrl.call
    }
    is (JALR) {
      w_jalr := true.B
      w_call := io.b_in.ctrl.get.ctrl.call
      w_ret := io.b_in.ctrl.get.ctrl.ret
    }

    // ------------------------------
    //            BRANCH
    // ------------------------------
    is (BEQ) {
      w_br := true.B
      w_taken := (io.b_in.data.get.s1 === io.b_in.data.get.s2)
    }
    is (BNE) {
      w_br := true.B
      w_taken := (io.b_in.data.get.s1 =/= io.b_in.data.get.s2)
    }
    is (BLT) {
      w_br := true.B
      when (w_sign) {
        w_taken := ((io.b_in.data.get.s1).asSInt < (io.b_in.data.get.s2).asSInt)
      }.otherwise {
        w_taken := (io.b_in.data.get.s1 < io.b_in.data.get.s2)
      }         
    }
    is (BGE) {
      w_br := true.B
      when (w_sign) {
        w_taken := ((io.b_in.data.get.s1).asSInt >= (io.b_in.data.get.s2).asSInt)
      }.otherwise {
        w_taken := (io.b_in.data.get.s1 >= io.b_in.data.get.s2)
      }         
    }

    // ------------------------------
    //            FENCE
    // ------------------------------
    is (FENCEI) {
      if (p.useExtZifencei) {
        w_flush_pipe := true.B
      }        
    }

    // ------------------------------
    //             CBO
    // ------------------------------
    is (INVAL) {
      w_flush_pipe := true.B
    }
    is (FLUSH) {
      w_flush_pipe := true.B      
    }
    is (ZERO) {
      w_flush_pipe := true.B      
    }
  }

  // ******************************
  //              CBO
  // ******************************
  if (p.useCbo) {
    io.b_cbo.get.valid := r_cbo.valid
    io.b_cbo.get.hart := r_cbo.hart
    if (p.useField) io.b_cbo.get.field.get := r_cbo.field.get 
    io.b_cbo.get.op := r_cbo.op
    io.b_cbo.get.sort := r_cbo.sort
    io.b_cbo.get.block := r_cbo.block  
    io.b_cbo.get.addr := r_cbo.addr 
  }

  // ******************************
  //            ADDRESS
  // ******************************
  val w_addr = Wire(UInt(p.nAddrBit.W))
  val w_redirect = Wire(Bool())

  when (w_jal | w_jalr) {
    w_addr := io.b_in.data.get.s1 + io.b_in.data.get.s2
  }.elsewhen(w_br & w_taken) {
    w_addr := io.b_in.ctrl.get.info.pc + io.b_in.data.get.s3
  }.otherwise {
    w_addr := io.b_in.ctrl.get.info.pc + 4.U
  }

  w_redirect := io.b_in.valid & (~io.b_in.ctrl.get.next.valid | (io.b_in.ctrl.get.next.addr =/= w_addr))  

  // ******************************
  //           REGISTERS
  // ******************************
  // Default
  val init_out = Wire(new GenVBus(p, new IntUnitCtrlBus(p), UInt(p.nDataBit.W)))

  init_out := DontCare
  init_out.valid := false.B

  val init_br_new = Wire(new BranchBus(p.nAddrBit, p.nSpecBranch, p.nRobEntry))

  init_br_new := DontCare
  init_br_new.valid := false.B

  val init_stat = Wire(new StatBus())

  init_stat := 0.U.asTypeOf(init_stat)
  init_stat.br := false.B
  init_stat.mispred := false.B

  val r_out = RegInit(init_out)
  val r_flush_pipe = RegInit(false.B)
  val r_br_new = RegInit(init_br_new)
  val r_br_up = RegInit(VecInit(Seq.fill(p.nSpecBranch)(false.B)))
  val r_stat = RegInit(init_stat)
  
  // Connect
  io.b_in.ready := ~w_lock & (r_fsm === s0IDLE)
  
  w_lock := r_out.valid & ((r_out.ctrl.get.gpr.en & (r_write & ~io.b_write.ready)) | ~io.b_end.ready) & ~(io.i_br_new.valid & r_out.ctrl.get.br.mask(io.i_br_new.tag))

  switch(r_fsm) {
    is (s0IDLE) {
      when (~w_lock | io.i_flush | (io.i_br_new.valid & r_out.ctrl.get.br.mask(io.i_br_new.tag))) {
        r_out.valid := io.b_in.valid & w_done & ~w_flush_in
        r_out.ctrl.get := io.b_in.ctrl.get
        r_out.ctrl.get.br.mask := io.b_in.ctrl.get.br.mask & io.i_br_up
        r_out.data.get := io.b_in.ctrl.get.info.pc + 4.U 
        r_flush_pipe := ((w_done & w_redirect) | w_flush_pipe) & ~w_flush_in
        r_br_new.valid := ((w_done & w_redirect) | w_flush_pipe) & ~w_flush_in
        r_br_new.tag := io.b_in.ctrl.get.br.tag
        r_br_new.entry := io.b_in.ctrl.get.info.entry
        r_br_new.addr := Cat(w_addr(p.nAddrBit-1,2), 0.U(2.W))
        
        for (sp <- 0 until p.nSpecBranch) {
          r_br_up(sp) := ~(io.b_in.valid & w_use_tag & (sp.U === io.b_in.ctrl.get.br.tag))
        }

        r_stat.br := io.b_in.valid & (w_jal | w_jalr | w_br)
        r_stat.mispred := w_redirect
      }.otherwise {
        r_out.ctrl.get.br.mask := r_out.ctrl.get.br.mask & io.i_br_up
      }
    }

    is (s1CBO) {
      r_out.valid := w_done & ~(io.i_flush | (io.i_br_new.valid & r_out.ctrl.get.br.mask(io.i_br_new.tag)))
      r_out.ctrl.get.br.mask := r_out.ctrl.get.br.mask & io.i_br_up
      r_br_new.valid := w_done & ~(io.i_flush | (io.i_br_new.valid & r_out.ctrl.get.br.mask(io.i_br_new.tag))) & w_flush_pipe
    }

    is (s2HINT) {
      r_out.valid := w_done & ~(io.i_flush | (io.i_br_new.valid & r_out.ctrl.get.br.mask(io.i_br_new.tag)))
      r_out.ctrl.get.br.mask := r_out.ctrl.get.br.mask & io.i_br_up
    }
  }

  // Write register
  when (r_write) {
    r_write := ~(r_out.valid & r_out.ctrl.get.gpr.en & io.b_write.ready & ~io.b_end.ready)
  }.otherwise {
    r_write := io.b_end.ready
  }

  // ******************************
  //             I/Os
  // ******************************
  io.b_in.ready := ~w_lock & w_done

  io.b_write.valid := r_out.valid & r_out.ctrl.get.gpr.en & r_write
  io.b_write.rdp := r_out.ctrl.get.gpr.rdp
  io.b_write.data := r_out.data.get

  io.b_end.valid := r_out.valid & (~r_out.ctrl.get.gpr.en | ~r_out.ctrl.get.gpr.en | ~r_write | io.b_write.ready)
  io.b_end.entry := r_out.ctrl.get.info.entry
  io.b_end.replay := false.B
  io.b_end.trap := DontCare
  io.b_end.trap.valid := false.B

  io.b_end.stat := r_stat

  io.o_br_up := r_br_up.asUInt
  io.o_br_new := r_br_new
  io.o_flush := r_flush_pipe

  // ******************************
  //         PREDICTOR INFOS
  // ******************************
  io.o_br_info.valid := io.b_in.valid & w_done & (w_jal | w_jalr | w_br)
  io.o_br_info.pc := io.b_in.ctrl.get.info.pc
  io.o_br_info.br := w_br
  io.o_br_info.taken := w_taken
  io.o_br_info.jmp := w_jal | w_jalr
  io.o_br_info.call := w_call
  io.o_br_info.ret := w_ret
  io.o_br_info.target := w_addr   

  // ******************************
  //             FREE
  // ******************************
  io.o_free := (r_fsm === s0IDLE) & ~r_out.valid

  // ******************************
  //             DEBUG
  // ******************************
  if (p.debug) {
    // ------------------------------
    //            SIGNALS
    // ------------------------------
    dontTouch(r_out.ctrl.get.info)

    // ------------------------------
    //         DATA FOOTPRINT
    // ------------------------------

    // ------------------------------
    //       EXECUTION TRACKER
    // ------------------------------  
    switch(r_fsm) {
      is (s0IDLE) {
        when (~w_lock | io.i_flush | (io.i_br_new.valid & r_out.ctrl.get.br.mask(io.i_br_new.tag))) {
          r_out.ctrl.get.etd.get := io.b_in.ctrl.get.etd.get
        }
      }
    }

    dontTouch(r_out.ctrl.get.etd.get)
    io.b_end.etd.get := DontCare
  }
}

object Bru extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new Bru(IntUnitConfigBase), args)
}

/*
 * File: id.scala                                                              *
 * Created Date: 2023-02-26 09:21:29 am                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-03-02 12:12:07 pm                                       *
 * Modified By: Mathieu Escouteloup                                            *
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.core.abondance.back

import chisel3._
import chisel3.util._

import herd.common.gen._
import herd.common.field._
import herd.core.aubrac.front.{FrontBus}
import herd.core.abondance.common._


class IdStage(p: BackParams) extends Module {
  val io = IO(new Bundle {
    val b_hart = if (p.useField) Some(new RsrcIO(p.nHart, p.nField, 1)) else None
    val b_back = if (p.useField) Some(Vec(p.nBackPort, new RsrcIO(p.nHart, p.nField, 1))) else None

    val i_init = Input(Bool())
    val i_flush = Input(Bool())
    val o_flush = Output(Bool())

    val b_in = Vec(p.nBackPort, Flipped(new GenRVIO(p,new FrontBus(p.debug, p.nAddrBit, p.nInstrBit), UInt(0.W))))

    val o_br_act = Output(UInt(p.nSpecBranch.W))
    val i_br_up = Input(UInt(p.nSpecBranch.W))
    val i_br_new = Input(new BranchBus(p.nAddrBit, p.nSpecBranch, p.nRobEntry))

    val b_out = Vec(p.nBackPort, new GenRVIO(p, new RenCtrlBus(p), UInt(0.W)))
  })

  val m_decoder = Seq.fill(p.nBackPort){Module(new Decoder(p))}
  //val m_imm1 = Seq.fill(p.nBackPort){Module(new SlctImm(p.nInstrBit, p.nDataBit))}
  //val m_imm2 = Seq.fill(p.nBackPort){Module(new SlctImm(p.nInstrBit, p.nDataBit))}
  
  val w_bp_lock = Wire(Vec(p.nBackPort, Bool()))
  val w_bp_wait = Wire(Vec(p.nBackPort, Bool()))
  val w_bp_flush = Wire(Vec(p.nBackPort, Bool()))

  val w_wait_br = Wire(Bool())

  // ******************************
  //        BACK PORT STATUS
  // ******************************
  val w_hart_flush = Wire(Bool())
  val w_back_valid = Wire(Vec(p.nBackPort, Bool()))
  val w_back_flush = Wire(Vec(p.nBackPort, Bool()))

  for (bp <- 0 until p.nBackPort) {
    if (p.useField) {
      w_hart_flush := io.b_hart.get.flush | io.i_init
      w_back_valid(bp) := io.b_back.get(bp).valid & ~io.b_back.get(bp).flush
      w_back_flush(bp) := w_hart_flush | io.b_back.get(bp).flush | io.i_flush
    } else {
      w_hart_flush := io.i_init
      w_back_valid(bp) := true.B
      w_back_flush(bp) := w_hart_flush | io.i_flush
    }
  }

  // ******************************
  //         DECODER & IMM
  // ******************************
  for (bp <- 0 until p.nBackPort) {
    m_decoder(bp).io.i_instr := io.b_in(bp).ctrl.get.instr

//    m_imm1(bp).io.i_instr := io.b_in(bp).ctrl.get.instr
//    m_imm1(bp).io.i_imm_type := m_decoder(bp).io.o_data.imm1type
//
//    m_imm2(bp).io.i_instr := io.b_in(bp).ctrl.get.instr
//    m_imm2(bp).io.i_imm_type := m_decoder(bp).io.o_data.imm2type
  }

  // ******************************
  //       BRANCH SPECULATION
  // ******************************
  val r_br_act = RegInit(VecInit(Seq.fill(p.nSpecBranch)(false.B)))
  val r_br_alloc  = Reg(Vec(p.nSpecBranch, Vec(p.nSpecBranch, Bool())))

  // ------------------------------
  //           CALCULATE
  // ------------------------------
  val w_br_act = Wire(Vec(p.nBackPort + 1, Vec(p.nSpecBranch, Bool())))

  val w_br_valid = Wire(Vec(p.nBackPort, Bool()))
  val w_br_av = Wire(Vec(p.nBackPort, Bool()))
  val w_br_tag = Wire(Vec(p.nBackPort, UInt(log2Ceil(p.nSpecBranch).W)))

  w_br_act(0) := r_br_act

  for (bp <- 0 until p.nBackPort) {
    if (p.useFastJal) {
      w_br_valid(bp) := io.b_in(bp).valid & ~w_bp_flush(bp) & m_decoder(bp).io.o_br.br
    } else {
      w_br_valid(bp) := io.b_in(bp).valid & ~w_bp_flush(bp) & (m_decoder(bp).io.o_br.jal | m_decoder(bp).io.o_br.br)
    }
    w_br_av(bp) := ~w_br_act(bp).asUInt.andR
    w_br_tag(bp) := PriorityEncoder(~w_br_act(bp).asUInt)

    w_br_act(bp + 1) := w_br_act(bp)
    when (w_br_valid(bp) & w_br_av(bp)) {
      w_br_act(bp + 1)(w_br_tag(bp)) := true.B
    }
  }

  // ------------------------------
  //             WAIT
  // ------------------------------
  w_wait_br := false.B
  for (bp <- 0 until p.nBackPort) {
    when (w_br_valid(bp) & ~w_br_av(bp)) {
      w_wait_br := true.B
    }
  }

  // ------------------------------
  //             RESTORE
  // ------------------------------
  val w_mask_rest = Wire(Vec(p.nSpecBranch, Bool()))

  w_mask_rest := r_br_alloc(io.i_br_new.tag)
  w_mask_rest(io.i_br_new.tag) := true.B
  
  // ------------------------------
  //          SPEC BRANCH
  // ------------------------------
  val w_br = Wire(Vec(p.nBackPort, new SpecBus(p.nSpecBranch)))

  for (bp <- 0 until p.nBackPort) {
    w_br(bp).valid := w_br_valid(bp)
    w_br(bp).tag := w_br_tag(bp)
    w_br(bp).mask := w_br_act(bp).asUInt
  }

  // ------------------------------
  //           REGISTERS
  // ------------------------------
  for (sp <- 0 until p.nSpecBranch) {
    when (w_hart_flush) {
      r_br_act(sp) := false.B
    }.elsewhen (io.i_br_new.valid) {
      r_br_act(sp) := r_br_act(sp) & ~w_mask_rest(sp)
    }.elsewhen (w_bp_lock.asUInt.orR | w_bp_wait.asUInt.orR) {
      r_br_act(sp) := r_br_act(sp) & io.i_br_up(sp)
    }.otherwise {
      r_br_act(sp) := w_br_act(p.nBackPort)(sp) & io.i_br_up(sp)
    } 
  }

  for (bp <- 0 until p.nBackPort) {
    when (~(w_bp_lock.asUInt.orR | w_bp_wait.asUInt.orR)) {    
      when (w_br(bp).valid) {
        for (sp <- 0 until p.nSpecBranch) {
          // Init branch allocation mask
          r_br_alloc(w_br(bp).tag)(sp) := false.B

          // Set bit in previous branch allocation mask
          when (w_br(bp).mask(sp)) {
            r_br_alloc(sp)(w_br(bp).tag) := true.B
          }
        }
      }
    }    
  }

  io.o_br_act := r_br_act.asUInt

  // ******************************
  //             TRAP
  // ******************************
  val w_is_trap = Wire(Vec(p.nBackPort, Bool()))

  for (bp <- 0 until p.nBackPort) {
    w_is_trap(bp) := io.b_in(bp).valid & m_decoder(bp).io.o_trap.valid
  }

  // ******************************
  //             FLUSH
  // ******************************
  val r_flush = RegInit(false.B)
  
  when (r_flush) {
    r_flush := ~io.i_flush & ~w_hart_flush
  }.otherwise {
    r_flush := w_is_trap.asUInt.orR
  }

  for (bp0 <- 0 until p.nBackPort) {
    w_bp_flush(bp0) := w_back_flush(bp0)
    for (bp1 <- 0 until bp0) {
      when (w_is_trap(bp1)) {
        w_bp_flush(bp0) := true.B
      }
    }
  }

  io.o_flush := r_flush

  // ******************************
  //            OUTPUT
  // ******************************
  val m_out = if (p.useIdStage) Some(Seq.fill(p.nBackPort){Module(new SpecReg(p, new RenCtrlBus(p), UInt(0.W), true, p.nSpecBranch))}) else None

  // Wait
  for (bp0 <- 0 until p.nBackPort) {
    w_bp_wait(bp0) := w_wait_br
    for (bp1 <- 0 until p.nBackPort) {
      if (bp0 != bp1) {
        when (w_bp_lock(bp1)) {
          w_bp_wait(bp0) := true.B
        }
      }
    }
  }

  // Input
  for (bp <- 0 until p.nBackPort) {
    io.b_in(bp).ready := (~w_bp_wait(bp) & ~w_bp_lock(bp)) | w_hart_flush | io.i_flush | io.i_init
  }

  // ------------------------------
  //           REGISTERS
  // ------------------------------
  if (p.useIdStage) {
    // Write
    for (bp <- 0 until p.nBackPort) {
      w_bp_lock(bp) := ~m_out.get(bp).io.b_in.ready
      m_out.get(bp).io.i_flush := w_back_flush(bp)
      m_out.get(bp).io.i_br_up := io.i_br_up

      m_out.get(bp).io.b_in.valid := io.b_in(bp).valid & w_back_valid(bp) & ~w_bp_flush(bp) & ~w_bp_wait(bp)

      m_out.get(bp).io.b_in.ctrl.get.info := m_decoder(bp).io.o_info
      m_out.get(bp).io.b_in.ctrl.get.info.pc := io.b_in(bp).ctrl.get.pc
      m_out.get(bp).io.b_in.ctrl.get.trap := m_decoder(bp).io.o_trap
      m_out.get(bp).io.b_in.ctrl.get.br := w_br(bp)
      m_out.get(bp).io.b_in.ctrl.get.ex := m_decoder(bp).io.o_ex
      m_out.get(bp).io.b_in.ctrl.get.data := m_decoder(bp).io.o_data
      m_out.get(bp).io.b_in.ctrl.get.ext := m_decoder(bp).io.o_ext
    }

    // Read
    for (bp <- 0 until p.nBackPort) {
      m_out.get(bp).io.b_out <> io.b_out(bp)
    }

  // ------------------------------
  //             DIRECT
  // ------------------------------
  } else {
    // Output
    for (bp <- 0 until p.nBackPort) {
      w_bp_lock(bp) := ~io.b_out(bp).ready

      io.b_out(bp).valid := io.b_in(bp).valid & w_back_valid(bp) & ~w_back_flush(bp) & ~w_bp_wait(bp) & ~w_bp_flush(bp)

      io.b_out(bp).ctrl.get.info := m_decoder(bp).io.o_info
      io.b_out(bp).ctrl.get.info.pc := io.b_in(bp).ctrl.get.pc
      io.b_out(bp).ctrl.get.trap := m_decoder(bp).io.o_trap
      io.b_out(bp).ctrl.get.br := w_br(bp)
      io.b_out(bp).ctrl.get.ex := m_decoder(bp).io.o_ex
      io.b_out(bp).ctrl.get.data := m_decoder(bp).io.o_data
      io.b_out(bp).ctrl.get.ext := m_decoder(bp).io.o_ext
    }
  }

  // ******************************
  //             FIELD
  // ******************************
  if (p.useField) {
    io.b_hart.get.free := true.B
    for (bp <- 0 until p.nBackPort) {      
      if (p.useIdStage) {
        io.b_back.get(bp).free := ~m_out.get(bp).io.o_val.valid
      } else {
        io.b_back.get(bp).free := true.B
      }      
    }
  }  

  // ******************************
  //             DEBUG
  // ******************************
  if (p.debug) {
    // ------------------------------
    //            SIGNALS
    // ------------------------------

    // ------------------------------
    //         DATA FOOTPRINT
    // ------------------------------

    // ------------------------------
    //       EXECUTION TRACKER
    // ------------------------------
    for (bp <- 0 until p.nBackPort) {
      if (p.useIdStage) {
        m_out.get(bp).io.b_in.ctrl.get.etd.get := io.b_in(bp).ctrl.get.etd.get
      } else {
        io.b_out(bp).ctrl.get.etd.get := io.b_in(bp).ctrl.get.etd.get
      }
    }
  }
}

object IdStage extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new IdStage(BackConfigBase), args)
}

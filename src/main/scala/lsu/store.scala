/*
 * File: store.scala                                                           *
 * Created Date: 2023-02-26 09:21:29 am                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-03-02 12:18:13 pm                                       *
 * Modified By: Mathieu Escouteloup                                            *
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.core.abondance.lsu

import chisel3._
import chisel3.util._

import herd.common.gen._
import herd.common.field._
import herd.common.mem.mb4s.{OP => LSUUOP}
import herd.core.abondance.common._
import herd.core.abondance.back.{BranchBus, BypassBus, CommitBus, EndIO}
 

class StoreQueue(p: LsuParams) extends Module {  
  val io = IO(new Bundle {
    val b_hart = if (p.useField) Some(new RsrcIO(p.nHart, p.nField, 1)) else None

    val i_flush = Input(Bool())

    val b_in = Vec(p.nBackPort, Flipped(new GenRVIO(p, new StoreQueueBus(p), UInt(0.W))))

    val o_rs1p = Output(Vec(p.nStoreQueue, new GenVIO(p, UInt(log2Ceil(p.nGprPhy).W), UInt(0.W))))
    val b_fwd = Vec(p.nLoad, new StoreForwardIO(p))

    val i_br_up = Input(UInt(p.nSpecBranch.W))
    val i_br_new = Input(new BranchBus(p.nAddrBit, p.nSpecBranch, p.nRobEntry))
    val i_busy = Input(Vec(p.nBypass, new BypassBus(p.nDataBit, p.nGprPhy)))
    val i_commit = Input(Vec(p.nCommit, new CommitBus(p.nRobEntry, 1, p.nGprPhy)))
    val o_rel = Output(UInt(p.nStoreQueue.W))
    
    val b_ex = new LsuExIO(p, p.nStoreQueue)
    val b_mem = new GenRVIO(p, new MemCtrlBus(p), UInt(p.nDataBit.W))

    val b_end = new EndIO(p.debug, p.nAddrBit, p.nDataBit, p.nRobEntry)
  })

  // ******************************
  //           REGISTERS
  // ******************************
  val init_queue = Wire(Vec(p.nStoreQueue, new GenVBus(p, new StoreQueueBus(p), UInt(p.nDataBit.W))))
  
  for (sq <- 0 until p.nStoreQueue) {
    init_queue(sq) := DontCare
    init_queue(sq).valid := false.B
  }

  val r_queue = RegInit(init_queue)
  val r_wav = RegInit(true.B)

  val m_mem = Module(new GenReg(p, new MemCtrlBus(p), UInt(p.nDataBit.W), false, false, true))

  val w_mem = Wire(new GenVBus(p, new MemCtrlBus(p), UInt(p.nDataBit.W)))
  val w_mlock = Wire(Bool())

  // ******************************
  //          HART STATUS
  // ******************************
  val w_hart_flush = Wire(Bool())

  if (p.useField) {
    w_hart_flush := io.b_hart.get.flush
  } else {
    w_hart_flush := false.B
  } 

  // ******************************
  //            UPDATE
  // ******************************
  val w_upqueue = Wire(Vec(p.nStoreQueue, new GenVBus(p, new StoreQueueBus(p), UInt(p.nDataBit.W))))
  w_upqueue := r_queue


  // ------------------------------
  //           LINK READY
  // ------------------------------
  for (sq <- 0 until p.nStoreQueue) {
    for (c <- 0 until p.nCommit) {
      when (r_queue(sq).valid & io.i_commit(c).valid & (io.i_commit(c).entry === r_queue(sq).ctrl.get.dep.entry)) {
        w_upqueue(sq).ctrl.get.dep.av(0) := true.B
      }           
    }
  }

  // ------------------------------
  //           DATA READY
  // ------------------------------
  for (sq <- 0 until p.nStoreQueue) {
    for (b <- 0 until p.nBypass) {
      when (r_queue(sq).valid & io.i_busy(b).valid & (io.i_busy(b).rdp === r_queue(sq).ctrl.get.dep.rs1p)) {
        w_upqueue(sq).ctrl.get.dep.av(1) := true.B
      }
      when (r_queue(sq).valid & io.i_busy(b).valid & (io.i_busy(b).rdp === r_queue(sq).ctrl.get.dep.rs2p)) {
        w_upqueue(sq).ctrl.get.dep.av(2) := true.B
      }
    }    
  }

  // ------------------------------
  //             BRANCH
  // ------------------------------
  for (sq <- 0 until p.nStoreQueue) {
    w_upqueue(sq).valid := r_queue(sq).valid & (~((io.i_br_new.valid & r_queue(sq).ctrl.get.br.mask(io.i_br_new.tag)) | io.i_flush | w_hart_flush) | r_queue(sq).ctrl.get.state.commit)
    w_upqueue(sq).ctrl.get.br.mask := r_queue(sq).ctrl.get.br.mask & io.i_br_up
  }

  // ------------------------------
  //            COMMIT
  // ------------------------------
  for (sq <- 0 until p.nStoreQueue) {
    for (c <- 0 until p.nCommit) {
      when (r_queue(sq).valid & io.i_commit(c).valid & (io.i_commit(c).entry === r_queue(sq).ctrl.get.info.entry)) {
        w_upqueue(sq).ctrl.get.state.commit := true.B
      }           
    }
  }

  // ******************************
  //             WRITE
  // ******************************
  val w_wav = Wire(Vec(p.nBackPort + 1, Vec(p.nStoreQueue, Bool())))
  val w_wslct = Wire(Vec(p.nBackPort, UInt(log2Ceil(p.nStoreQueue).W)))

  w_wav(0)(p.nStoreQueue - 1) := ~r_queue(p.nStoreQueue - 1).valid
  for (sq <- (p.nStoreQueue - 2) to 0 by -1) {
    w_wav(0)(sq) := ~r_queue(sq).valid & w_wav(0)(sq + 1)
  }

  for (bp <- 0 until p.nBackPort) {
    w_wslct(bp) := PriorityEncoder(w_wav(bp).asUInt)
    w_wav(bp + 1) := w_wav(bp)
    when (io.b_in(bp).valid) {
      w_wav(bp + 1)(w_wslct(bp)) := false.B
    }   
  }

  for (bp <- 0 until p.nBackPort) {    
    io.b_in(bp).ready := r_wav
    when (io.b_in(bp).valid & r_wav) {
      w_upqueue(w_wslct(bp)).valid := true.B
      w_upqueue(w_wslct(bp)).ctrl.get := io.b_in(bp).ctrl.get
    }
  }
  
  // Free places for next cycle
  r_wav := (PopCount(w_wav(p.nBackPort).asUInt) >= p.nBackPort.U)

  // ******************************
  //            EXECUTE
  // ******************************
  // ------------------------------
  //         REGISTER READ
  // ------------------------------
  val w_rrav = Wire(Vec(p.nStoreQueue, Bool()))
  val w_rrslct = Wire(UInt(log2Ceil(p.nStoreQueue).W))

  for (sq <- 0 until p.nStoreQueue) {
    w_rrav(sq) := r_queue(sq).valid & ~r_queue(sq).ctrl.get.state.rr & r_queue(sq).ctrl.get.dep.av(1) & r_queue(sq).ctrl.get.dep.av(2)
  }
  w_rrslct := PriorityEncoder(w_rrav.asUInt)

  when (w_rrav.asUInt.orR) {
    w_upqueue(w_rrslct).ctrl.get.state.rr := io.b_ex.req.ready
  }

  io.b_ex.req.valid := w_rrav.asUInt.orR
  io.b_ex.req.ctrl.get.fwd := (r_queue(w_rrslct).ctrl.get.ctrl.uop === LSUUOP.W)
  io.b_ex.req.ctrl.get.br := r_queue(w_rrslct).ctrl.get.br
  io.b_ex.req.ctrl.get.tag := r_queue(w_rrslct).ctrl.get.tag
  io.b_ex.req.ctrl.get.size := r_queue(w_rrslct).ctrl.get.ctrl.size
  io.b_ex.req.ctrl.get.rs1p := r_queue(w_rrslct).ctrl.get.dep.rs1p
  io.b_ex.req.ctrl.get.rs2p := r_queue(w_rrslct).ctrl.get.dep.rs2p
  io.b_ex.req.ctrl.get.imm := r_queue(w_rrslct).ctrl.get.info.addr  

  // ------------------------------
  //         ADDRESS WRITE
  // ------------------------------
  for (sq <- 0 until p.nStoreQueue) {
    when (r_queue(sq).valid & io.b_ex.ack.valid & (io.b_ex.ack.ctrl.get.tag === r_queue(sq).ctrl.get.tag)) {
      w_upqueue(sq).ctrl.get.state.addr := true.B
      w_upqueue(sq).ctrl.get.info.addr := io.b_ex.ack.ctrl.get.addr
      w_upqueue(sq).data.get := io.b_ex.ack.data.get
    }    
  }

  // ******************************
  //        ADDRESS REGISTER
  // ******************************
  for (sq <- 0 until p.nStoreQueue) {
    io.o_rs1p(sq) := DontCare  
    io.o_rs1p(sq).valid := false.B
  }
  
  for (sq <- 0 until p.nStoreQueue) {
    when (r_queue(sq).valid) {
      io.o_rs1p(r_queue(sq).ctrl.get.tag).valid := true.B
      io.o_rs1p(r_queue(sq).ctrl.get.tag).ctrl.get := r_queue(sq).ctrl.get.dep.rs1p
    }
  }

  // ******************************
  //            FORWARD
  // ******************************
  for (l <- 0 until p.nLoad) {
    io.b_fwd(l).ready := false.B
    io.b_fwd(l).data := DontCare
    for (sq <- 0 until p.nStoreQueue) {
      when (r_queue(sq).valid & r_queue(sq).ctrl.get.state.addr & io.b_fwd(l).mask(r_queue(sq).ctrl.get.tag)) {
        io.b_fwd(l).ready := true.B
        io.b_fwd(l).data := r_queue(sq).data.get
      }
    }
  }

  // ******************************
  //             END
  // ******************************
  val w_endav = Wire(Vec(p.nStoreQueue, Bool()))
  val w_endslct = Wire(UInt(log2Ceil(p.nStoreQueue).W))

  for (sq <- 0 until p.nStoreQueue) {
    w_endav(sq) := r_queue(sq).valid & r_queue(sq).ctrl.get.state.addr & ~r_queue(sq).ctrl.get.state.end
  }
  w_endslct := PriorityEncoder(w_endav.asUInt)
  
  for (sq <- 0 until p.nStoreQueue) {
    when (w_endav.asUInt.orR & (sq.U === w_endslct) & io.b_end.ready) {
      w_upqueue(sq).ctrl.get.state.end := true.B
    }
  }

  io.b_end.valid := w_endav.asUInt.orR
  io.b_end.entry := r_queue(w_endslct).ctrl.get.info.entry
  io.b_end.replay := false.B
  io.b_end.trap := DontCare
  io.b_end.trap.valid := false.B  

  io.b_end.stat := 0.U.asTypeOf(io.b_end.stat)

  // ******************************
  //             MEMORY
  // ******************************
  // ------------------------------
  //            SELECT
  // ------------------------------
  val w_mav = Wire(Vec(p.nStoreQueue, Bool()))
  val w_mslct = Wire(UInt(log2Ceil(p.nStoreQueue).W))

  for (sq <- 0 until p.nStoreQueue) {
    w_mav(sq) := r_queue(sq).valid & r_queue(sq).ctrl.get.state.addr & r_queue(sq).ctrl.get.state.commit & r_queue(sq).ctrl.get.dep.av(0)
  }
  w_mslct := PriorityEncoder(w_mav.asUInt)  

  for (sq <- 0 until p.nStoreQueue) {
    when (r_queue(sq).valid & (sq.U === w_mslct)) {
      w_upqueue(sq).valid := ~(w_mav.asUInt.orR & ~w_mlock)
    }
  }

  // ------------------------------
  //           REGISTERS
  // ------------------------------
  w_mem := m_mem.io.o_val

  w_mlock := ~m_mem.io.b_in.ready

  m_mem.io.i_flush := w_hart_flush

  m_mem.io.b_in.valid := w_mav.asUInt.orR
  m_mem.io.b_in.ctrl.get.br := w_upqueue(w_mslct).ctrl.get.br
  m_mem.io.b_in.ctrl.get.tag := w_upqueue(w_mslct).ctrl.get.tag
  m_mem.io.b_in.ctrl.get.fwd := 0.U
  m_mem.io.b_in.ctrl.get.entry := w_upqueue(w_mslct).ctrl.get.info.entry
  m_mem.io.b_in.ctrl.get.addr := w_upqueue(w_mslct).ctrl.get.info.addr  
  m_mem.io.b_in.ctrl.get.ctrl := w_upqueue(w_mslct).ctrl.get.ctrl
  if (p.useExtA) {
    m_mem.io.b_in.ctrl.get.rdp := w_upqueue(w_mslct).ctrl.get.info.rdp
    m_mem.io.b_in.ctrl.get.ctrl.uop := w_upqueue(w_mslct).ctrl.get.ctrl.uop
  } else {
    m_mem.io.b_in.ctrl.get.rdp := 0.U
    m_mem.io.b_in.ctrl.get.ctrl.uop := LSUUOP.W
  }    
  m_mem.io.b_in.data.get := w_upqueue(w_mslct).data.get

  io.b_mem <> m_mem.io.b_out

  // ******************************
  //           COLLAPSE
  // ******************************
  val w_collapse = Wire(Vec(p.nStoreQueue, Bool()))

  for (sq <- 0 until p.nStoreQueue) {
    w_collapse(sq) := false.B    
  }

  for (sq <- 1 until p.nStoreQueue) {
    w_collapse(sq) := w_collapse(sq - 1) | ~w_upqueue(sq - 1).valid   
  } 

  // ******************************
  //            RELEASE
  // ******************************
  val w_rel = Wire(Vec(p.nStoreQueue, Bool()))

  for (sq <- 0 until p.nStoreQueue) {
    w_rel(sq) := true.B
  }

  for (sq <- 0 until p.nStoreQueue) {
    when (r_queue(sq).valid & ~r_queue(sq).ctrl.get.state.commit & ((io.i_br_new.valid & r_queue(sq).ctrl.get.br.mask(io.i_br_new.tag)) | io.i_flush)) {
      w_rel(r_queue(sq).ctrl.get.tag) := false.B
    }
  }

  io.o_rel := w_rel.asUInt

  // ******************************
  //            UPDATE
  // ******************************
  // Queue registers
  for (sq <- 0 until p.nStoreQueue) {
    r_queue(sq).valid := false.B
  }

  for (sq <- 0 until p.nStoreQueue) {
    r_queue(sq.U - PopCount(w_collapse(sq))) := w_upqueue(sq)
  }

  // ******************************
  //             FIELD
  // ******************************
  if (p.useField) {
    io.b_hart.get.free := ~w_mem.valid
    for (sq <- 0 until p.nStoreQueue) {
      when (r_queue(sq).valid) {
        io.b_hart.get.free := false.B
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
    for (sq <- 0 until p.nStoreQueue) {
      when (io.b_ex.ack.valid & (io.b_ex.ack.ctrl.get.tag === r_queue(sq).ctrl.get.tag)) {
        w_upqueue(sq).ctrl.get.etd.get.daddr := io.b_ex.ack.ctrl.get.addr
      }    
    }

    io.b_end.etd.get := r_queue(w_endslct).ctrl.get.etd.get

    m_mem.io.b_in.ctrl.get.etd.get := w_upqueue(w_mslct).ctrl.get.etd.get

    for (sq <- 0 until p.nStoreQueue) {
      dontTouch(r_queue(sq).ctrl.get.etd.get)
      dontTouch(w_upqueue(sq).ctrl.get.etd.get)
    }
  }
}

object StoreQueue extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new StoreQueue(LsuConfigBase), args)
}
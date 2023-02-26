/*
 * File: mem.scala                                                             *
 * Created Date: 2023-02-26 09:21:29 am                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-26 09:31:35 am                                       *
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
import herd.common.dome._
import herd.common.mem.mb4s.{OP => LSUUOP}
import herd.core.abondance.common._
import herd.core.abondance.back.{BranchBus}
 

class MemQueue(p: LsuParams) extends Module {  
  val io = IO(new Bundle {
    val b_hart = if (p.useDome) Some(new RsrcIO(p.nHart, p.nDome, 1)) else None

    val i_flush = Input(Bool())

    val b_in = Flipped(new GenRVIO(p, new MemQueueCtrlBus(p), UInt(p.nDataBit.W)))

    val i_br_up = Input(UInt(p.nSpecBranch.W))
    val i_br_new = Input(new BranchBus(p.nAddrBit, p.nSpecBranch, p.nRobEntry))

    val b_fwd = Vec(p.nLoad, new StoreForwardIO(p))

    val b_out = new MemQueueOutIO(p)
  })

  // ******************************
  //           REGISTERS
  // ******************************
  val init_queue = Wire(Vec(p.nMemQueue, new GenVBus(p, new MemQueueCtrlBus(p), UInt(p.nDataBit.W))))
  for (mq <- 0 until p.nMemQueue) {
    init_queue(mq) := DontCare
    init_queue(mq).valid := false.B
  }

  val r_queue = RegInit(init_queue)
  val r_wav = RegInit(true.B)

  // ******************************
  //          HART STATUS
  // ******************************
  val w_hart_flush = Wire(Bool())

  if (p.useDome) {
    w_hart_flush := io.b_hart.get.flush
  } else {
    w_hart_flush := false.B
  } 

  // ******************************
  //            UPDATE
  // ******************************
  val w_upqueue = Wire(Vec(p.nMemQueue, new GenVBus(p, new MemQueueCtrlBus(p), UInt(p.nDataBit.W))))
  w_upqueue := r_queue

  for (mq <- 0 until p.nMemQueue) {
    w_upqueue(mq).ctrl.get.abort := r_queue(mq).ctrl.get.abort | (r_queue(mq).valid & ((io.i_br_new.valid & r_queue(mq).ctrl.get.br.mask(io.i_br_new.tag)) | io.i_flush | w_hart_flush))
    w_upqueue(mq).ctrl.get.br.mask := r_queue(mq).ctrl.get.br.mask & io.i_br_up
  }

  // ******************************
  //             WRITE
  // ******************************
  val w_wav = Wire(Vec(2, Vec(p.nMemQueue, Bool())))
  val w_wslct = Wire(UInt(log2Ceil(p.nMemQueue).W))

  w_wav(0)(p.nMemQueue - 1) := ~r_queue(p.nMemQueue - 1).valid
  for (mq <- (p.nMemQueue - 2) to 0 by -1) {
    w_wav(0)(mq) := ~r_queue(mq).valid & w_wav(0)(mq + 1)
  }

  w_wslct := PriorityEncoder(w_wav(0).asUInt)
  w_wav(1) := w_wav(0)
  when (io.b_in.valid) {
    w_wav(1)(w_wslct) := false.B
  }   
 
  io.b_in.ready := r_wav
  when (io.b_in.valid & r_wav) {
    w_upqueue(w_wslct).valid := true.B
    w_upqueue(w_wslct).ctrl.get := io.b_in.ctrl.get
    w_upqueue(w_wslct).data.get := io.b_in.data.get
  }  
  
  // Free places for next cycle
  r_wav := w_wav(1).asUInt.orR

  // ******************************
  //            FORWARD
  // ******************************
  for (l <- 0 until p.nLoad) {
    io.b_fwd(l).ready := false.B
    io.b_fwd(l).data := DontCare
    for (mq <- 0 until p.nMemQueue) {
      when (r_queue(mq).valid & r_queue(mq).ctrl.get.ctrl.st & io.b_fwd(l).mask(r_queue(mq).ctrl.get.tag)) {
        io.b_fwd(l).ready := true.B
        io.b_fwd(l).data := r_queue(mq).data.get
      }
    }
  }

  // ******************************
  //             READ
  // ******************************
  // ------------------------------
  //            SELECT
  // ------------------------------
  val w_rav = Wire(Vec(p.nMemQueue, Bool()))
  val w_rslct = Wire(UInt(log2Ceil(p.nMemQueue).W))

  for (mq <- 0 until p.nMemQueue) {
    w_rav(mq) := r_queue(mq).valid
  }
  w_rslct := PriorityEncoder(w_rav.asUInt)

  // ------------------------------
  //           REGISTERS
  // ------------------------------
  io.b_out := DontCare
  io.b_out.valid := false.B

  for (mq <- 0 until p.nMemQueue) {
    when (mq.U === w_rslct) {
      io.b_out.valid := r_queue(mq).valid
      io.b_out.ctrl := w_upqueue(mq).ctrl.get
      io.b_out.data := w_upqueue(mq).data.get

      when (r_queue(mq).valid & io.b_out.ready) {
        w_upqueue(mq).valid := false.B
      }
    }
  }

  // ******************************
  //           COLLAPSE
  // ******************************
  val w_collapse = Wire(Vec(p.nMemQueue, Bool()))

  for (mq <- 0 until p.nMemQueue) {
    w_collapse(mq) := false.B    
  }

  for (mq <- 1 until p.nMemQueue) {
    w_collapse(mq) := w_collapse(mq - 1) | ~w_upqueue(mq - 1).valid   
  } 

  // ******************************
  //            UPDATE
  // ******************************
  // Queue registers
  for (mq <- 0 until p.nMemQueue) {
    r_queue(mq).valid := false.B
  }

  r_queue(0) := w_upqueue(0)
  for (mq <- 1 until p.nMemQueue) {
    when (w_collapse(mq)) {
      r_queue(mq - 1) := w_upqueue(mq)
    }.otherwise {
      r_queue(mq) := w_upqueue(mq)
    }
  }

  // ******************************
  //             DOME
  // ******************************
  if (p.useDome) {
    io.b_hart.get.free := true.B
    for (mq <- 0 until p.nMemQueue) {
      when (r_queue(mq).valid) {
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
    dontTouch(w_wav)
    dontTouch(w_wslct)
    dontTouch(w_rav)
    dontTouch(w_rslct)

    // ------------------------------
    //         DATA FOOTPRINT
    // ------------------------------

    // ------------------------------
    //       EXECUTION TRACKER
    // ------------------------------  
    for (mq <- 0 until p.nMemQueue) {
      dontTouch(r_queue(mq).ctrl.get.etd.get)
      dontTouch(w_upqueue(mq).ctrl.get.etd.get)
    }
  }
}

object MemQueue extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new MemQueue(LsuConfigBase), args)
}
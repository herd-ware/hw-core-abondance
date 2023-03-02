/*
 * File: req.scala                                                             *
 * Created Date: 2023-02-26 09:21:29 am                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-03-02 12:14:06 pm                                       *
 * Modified By: Mathieu Escouteloup                                            *
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.core.abondance.ext

import chisel3._
import chisel3.util._

import herd.common.gen._
import herd.common.field._
import herd.core.abondance.common._
import herd.core.abondance.back.{BranchBus, BypassBus, CommitBus}
import herd.core.abondance.back.{BackConfigBase}


class ExtReqQueue[UC <: Data](p: ExUnitParams, uc: UC, nQueue: Int, nOut: Int) extends Module {  
  val io = IO(new Bundle {
    val b_unit = if (p.useField) Some(new RsrcIO(p.nHart, p.nField, 1)) else None

    val i_flush = Input(Bool())

    val b_in = Vec(p.nBackPort, Flipped(new GenRVIO(p, new ExtReqQueueBus(p, uc), UInt(0.W))))

    val i_br_up = Input(UInt(p.nSpecBranch.W))
    val i_br_new = Input(new BranchBus(p.nAddrBit, p.nSpecBranch, p.nRobEntry))
    val i_busy = Input(Vec(p.nBypass, new BypassBus(p.nDataBit, p.nGprPhy)))
    val i_commit = Input(Vec(p.nCommit, new CommitBus(p.nRobEntry, 1, p.nGprPhy)))

    val b_out = Vec(nOut, new GenRVIO(p, new ExtReqQueueBus(p, uc), UInt(0.W)))
  })

  // ******************************
  //           REGISTERS
  // ******************************
  val init_queue = Wire(Vec(nQueue, new GenVBus(p, new ExtReqQueueBus(p, uc), UInt(0.W))))
  for (rq <- 0 until nQueue) {
    init_queue(rq) := DontCare
    init_queue(rq).valid := false.B
  }

  val r_queue = RegInit(init_queue)
  val r_wav = RegInit(true.B)

  // ******************************
  //          UNIT STATUS
  // ******************************
  val w_unit_flush = Wire(Bool())

  if (p.useField) {
    w_unit_flush := io.b_unit.get.flush
  } else {
    w_unit_flush := false.B
  } 

  // ******************************
  //            UPDATE
  // ******************************
  val w_upqueue = Wire(Vec(nQueue, new GenVBus(p, new ExtReqQueueBus(p, uc), UInt(0.W))))
  w_upqueue := r_queue

  // ------------------------------
  //           LINK READY
  // ------------------------------
  for (rq <- 0 until nQueue) {
    for (c <- 0 until p.nCommit) {
      when (r_queue(rq).valid & io.i_commit(c).valid & (io.i_commit(c).entry === r_queue(rq).ctrl.get.dep.entry)) {
        w_upqueue(rq).ctrl.get.dep.av(0) := true.B
      }           
    }
  }

  // ------------------------------
  //           DATA READY
  // ------------------------------
  for (rq <- 0 until nQueue) {
    for (b <- 0 until p.nBypass) {
      when (r_queue(rq).valid & io.i_busy(b).valid & (io.i_busy(b).rdp === r_queue(rq).ctrl.get.dep.rs1p)) {
        w_upqueue(rq).ctrl.get.dep.av(1) := true.B
      }
      when (r_queue(rq).valid & io.i_busy(b).valid & (io.i_busy(b).rdp === r_queue(rq).ctrl.get.dep.rs2p)) {
        w_upqueue(rq).ctrl.get.dep.av(2) := true.B
      }
    }    
  }

  // ------------------------------
  //         BRANCH & FLUSH
  // ------------------------------
  for (rq <- 0 until nQueue) {
    w_upqueue(rq).valid := r_queue(rq).valid & ~((io.i_br_new.valid & r_queue(rq).ctrl.get.br.mask(io.i_br_new.tag)) | io.i_flush | w_unit_flush)
    w_upqueue(rq).ctrl.get.br.mask := r_queue(rq).ctrl.get.br.mask & io.i_br_up
  }

  // ******************************
  //             WRITE
  // ******************************
  val w_wav = Wire(Vec(p.nBackPort + 1, Vec(nQueue, Bool())))
  val w_wslct = Wire(Vec(p.nBackPort, UInt(log2Ceil(nQueue).W)))

  w_wav(0)(nQueue - 1) := ~r_queue(nQueue - 1).valid
  for (q <- (nQueue - 2) to 0 by -1) {
    w_wav(0)(q) := ~r_queue(q).valid & w_wav(0)(q + 1)
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
  //             READ
  // ******************************
  for (r <- 0 until nOut) {
    when (io.b_out(r).ready & r_queue(r).ctrl.get.dep.av(1) & r_queue(r).ctrl.get.dep.av(2) & ~r_queue(r).ctrl.get.br.mask.orR) {
      w_upqueue(r).valid := false.B
    }

    io.b_out(r).valid := r_queue(r).valid & r_queue(r).ctrl.get.dep.av(1) & r_queue(r).ctrl.get.dep.av(2) & ~r_queue(r).ctrl.get.br.mask.orR
    io.b_out(r).ctrl.get := r_queue(r).ctrl.get
  }

  // ******************************
  //           COLLAPSE
  // ******************************
  val w_collapse = Wire(Vec(nQueue, Vec(nOut, Bool())))

  for (rq <- 0 until nQueue) {
    for (nc <- 0 until nOut) {
      w_collapse(rq)(nc) := false.B
    }
  }

  for (rq <- 1 until nQueue) {
    w_collapse(rq)(0) := w_collapse(rq - 1)(0) | ~w_upqueue(rq - 1).valid
    for (nc <- 1 until nOut) {
      w_collapse(rq)(nc) := w_collapse(rq - 1)(nc) | (~w_upqueue(rq - 1).valid & w_collapse(rq - 1)(nc - 1))
    }    
  }  

  // ******************************
  //            UPDATE
  // ******************************
  // Write pointer
  when (io.i_flush | w_unit_flush) {
    r_wav := true.B
  }

  // Queue registers
  for (rq <- 0 until nQueue) {
    r_queue(rq).valid := false.B
  }

  for (rq <- 0 until nQueue) {
    r_queue(rq.U - PopCount(w_collapse(rq))) := w_upqueue(rq)
  }

  // ******************************
  //             FIELD
  // ******************************
  if (p.useField) {
    io.b_unit.get.free := true.B
    for (rq <- 0 until nQueue) {
      when (r_queue(rq).valid) {
        io.b_unit.get.free := false.B
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

    // ------------------------------
    //         DATA FOOTPRINT
    // ------------------------------

    // ------------------------------
    //       EXECUTION TRACKER
    // ------------------------------  
    for (o <- 0 until nOut) {
      dontTouch(io.b_out(o).ctrl.get.etd.get)
    }
  }
}

object ExtReqQueue extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new ExtReqQueue(BackConfigBase, new ExtReqQueueBus(BackConfigBase, UInt(4.W)), 8, 2), args)
}
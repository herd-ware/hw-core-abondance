/*
 * File: ack.scala                                                             *
 * Created Date: 2023-02-26 09:21:29 am                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-26 09:29:56 am                                       *
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
import herd.common.dome._
import herd.core.abondance.common._
import herd.core.abondance.back.{CommitBus}
import herd.core.abondance.back.{BackConfigBase}


class ExtAckQueue(p: ExUnitParams, nQueue: Int, nBus: Int) extends Module {  
  val io = IO(new Bundle {
    val b_unit = if (p.useDome) Some(new RsrcIO(p.nHart, p.nDome, 1)) else None
    
    val i_flush = Input(Bool())

    val b_in = Vec(nBus, Flipped(new GenRVIO(p, new ExtAckQueueBus(p), UInt(0.W))))

    val i_run = Input(Bool())
    val i_commit = Input(Vec(p.nCommit, new CommitBus(p.nRobEntry, 1, p.nGprPhy)))

    val b_out = Vec(nBus, new GenRVIO(p, new ExtAckQueueBus(p), UInt(0.W)))
  })

  // ******************************
  //           REGISTERS
  // ******************************
  val init_queue = Wire(Vec(nQueue, new GenVBus(p, new ExtAckQueueBus(p), UInt(0.W))))
  for (aq <- 0 until nQueue) {
    init_queue(aq) := DontCare
    init_queue(aq).valid := false.B
  }

  val r_queue = RegInit(init_queue)
  val r_wav = RegInit(true.B)

  // ******************************
  //          UNIT STATUS
  // ******************************
  val w_unit_flush = Wire(Bool())

  if (p.useDome) {
    w_unit_flush := io.b_unit.get.flush
  } else {
    w_unit_flush := false.B
  } 

  // ******************************
  //            UPDATE
  // ******************************
  val w_upqueue = Wire(Vec(nQueue, new GenVBus(p, new ExtAckQueueBus(p), UInt(0.W))))
  w_upqueue := r_queue

  // ------------------------------
  //           LINK READY
  // ------------------------------
  for (aq <- 0 until nQueue) {
    for (c <- 0 until p.nCommit) {
      when (r_queue(aq).valid & io.i_commit(c).valid & (io.i_commit(c).entry === r_queue(aq).ctrl.get.link.entry)) {
        w_upqueue(aq).ctrl.get.link.av := true.B
      }           
    }
  }

  // ------------------------------
  //             FLUSH
  // ------------------------------
  for (aq <- 0 until nQueue) {
    w_upqueue(aq).valid := r_queue(aq).valid & ~io.i_flush & ~w_unit_flush
  }

  // ******************************
  //             WRITE
  // ******************************
  val w_wav = Wire(Vec(nBus + 1, Vec(nQueue, Bool())))
  val w_wslct = Wire(Vec(nBus, UInt(log2Ceil(nQueue).W)))

  w_wav(0)(nQueue - 1) := ~r_queue(nQueue - 1).valid
  for (q <- (nQueue - 2) to 0 by -1) {
    w_wav(0)(q) := ~r_queue(q).valid & w_wav(0)(q + 1)
  }

  for (b <- 0 until nBus) {
    w_wslct(b) := PriorityEncoder(w_wav(b).asUInt)
    w_wav(b + 1) := w_wav(b)
    when (io.b_in(b).valid) {
      w_wav(b + 1)(w_wslct(b)) := false.B
    } 
  }  

  for (b <- 0 until nBus) { 
    io.b_in(b).ready := r_wav
    when (io.b_in(b).valid & r_wav) {
      w_upqueue(w_wslct(b)).valid := true.B
      w_upqueue(w_wslct(b)).ctrl.get := io.b_in(b).ctrl.get
    }  
  } 
  
  // Free places for next cycle
  r_wav := (PopCount(w_wav(nBus).asUInt) >= nBus.U)

  // ******************************
  //             READ
  // ******************************
  // ------------------------------
  //           AVAILABLE
  // ------------------------------
  val w_rav = Wire(Vec(nBus, Bool()))

  w_rav(0) := r_queue(0).valid & r_queue(0).ctrl.get.link.av
  for (b <- 1 until nBus) {
    w_rav(b) := r_queue(b).valid & r_queue(b).ctrl.get.link.av | (io.i_run & r_queue(b - 1).valid & (r_queue(b - 1).ctrl.get.info.entry === r_queue(b).ctrl.get.link.entry))
  }

  // ------------------------------
  //             PORT
  // ------------------------------
  for (b <- 0 until nBus) {
    when (w_rav(b) & io.b_out(b).ready) {
      w_upqueue(b).valid := false.B
    }

    io.b_out(b).valid := r_queue(b).valid & w_rav(b)
    io.b_out(b).ctrl.get := r_queue(b).ctrl.get
  }

  // ******************************
  //           COLLAPSE
  // ******************************
  val w_collapse = Wire(Vec(nQueue, Vec(nBus, Bool())))

  for (aq <- 0 until nQueue) {
    for (nc <- 0 until nBus) {
      w_collapse(aq)(nc) := false.B
    }
  }

  for (aq <- 1 until nQueue) {
    w_collapse(aq)(0) := w_collapse(aq - 1)(0) | ~w_upqueue(aq - 1).valid
    for (nc <- 1 until nBus) {
      w_collapse(aq)(nc) := w_collapse(aq - 1)(nc) | (~w_upqueue(aq - 1).valid & w_collapse(aq - 1)(nc - 1))
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
  for (aq <- 0 until nQueue) {
    r_queue(aq).valid := false.B
  }

  for (aq <- 0 until nQueue) {
    r_queue(aq.U - PopCount(w_collapse(aq))) := w_upqueue(aq)
  }

  // ******************************
  //             DOME
  // ******************************
  if (p.useDome) {
    io.b_unit.get.free := true.B
    for (aq <- 0 until nQueue) {
      when (r_queue(aq).valid) {
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
    dontTouch(w_rav)

    // ------------------------------
    //         DATA FOOTPRINT
    // ------------------------------

    // ------------------------------
    //       EXECUTION TRACKER
    // ------------------------------  
    for (b <- 0 until nBus) {
      dontTouch(io.b_out(b).ctrl.get.etd.get)
    }
  }
}

object ExtAckQueue extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new ExtAckQueue(BackConfigBase, 8, 2), args)
}
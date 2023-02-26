/*
 * File: queue.scala                                                           *
 * Created Date: 2023-02-26 09:21:29 am                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-26 09:30:58 am                                       *
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

import herd.common.gen._
import herd.common.dome._
import herd.core.abondance.common._
import herd.core.abondance.back.{BranchBus, BypassBus, CommitBus, GprWriteIO}
 

class IntQueue(p: IntParams) extends Module {  
  import herd.core.abondance.int.INTUNIT._

  val io = IO(new Bundle {
    val b_hart = if (p.useDome) Some(new RsrcIO(p.nHart, p.nDome, 1)) else None

    val i_flush = Input(Bool())

    val b_in = Vec(p.nBackPort, Flipped(new GenRVIO(p, new IntQueueBus(p), UInt(0.W))))

    val i_br_up = Input(UInt(p.nSpecBranch.W))
    val i_br_new = Input(new BranchBus(p.nAddrBit, p.nSpecBranch, p.nRobEntry))
    val i_busy = Input(Vec(p.nBypass, new BypassBus(p.nDataBit, p.nGprPhy)))
    val i_commit = Input(Vec(p.nCommit, new CommitBus(p.nRobEntry, 1, p.nGprPhy)))

    val b_out = Vec(p.nIntUnit, new IntUnitRVIO(p, new IntQueueBus(p), UInt(0.W)))
  })

  // ******************************
  //           REGISTERS
  // ******************************
  val init_queue = Wire(Vec(p.nIntQueue, new GenVBus(p, new IntQueueBus(p), UInt(0.W))))
  for (iq <- 0 until p.nIntQueue) {
    init_queue(iq) := DontCare
    init_queue(iq).valid := false.B
  }

  val r_queue = RegInit(init_queue)
  val r_pt = RegInit(0.U(log2Ceil(p.nIntQueue + 1).W))

  val m_out = Seq.fill(p.nIntUnit){Module(new SpecReg(p, new IntQueueBus(p), UInt(0.W), true, p.nSpecBranch))}  

  val w_out = Wire(Vec(p.nIntUnit, new GenVIO(p, new IntQueueBus(p), UInt(0.W))))

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
  val w_upqueue = Wire(Vec(p.nIntQueue, new GenVBus(p, new IntQueueBus(p), UInt(0.W))))
  w_upqueue := r_queue

  // ------------------------------
  //           LINK READY
  // ------------------------------
  for (iq <- 0 until p.nIntQueue) {
    for (c <- 0 until p.nCommit) {
      when (r_queue(iq).valid & io.i_commit(c).valid & (io.i_commit(c).entry === r_queue(iq).ctrl.get.dep.entry)) {
        w_upqueue(iq).ctrl.get.dep.av(0) := true.B
      }           
    }
  }

  // ------------------------------
  //           DATA READY
  // ------------------------------
  for (iq <- 0 until p.nIntQueue) {
    for (b <- 0 until p.nBypass) {
      when (r_queue(iq).valid & io.i_busy(b).valid & (io.i_busy(b).rdp === r_queue(iq).ctrl.get.dep.rs1p)) {
        w_upqueue(iq).ctrl.get.dep.av(1) := true.B
      }
      when (r_queue(iq).valid & io.i_busy(b).valid & (io.i_busy(b).rdp === r_queue(iq).ctrl.get.dep.rs2p)) {
        w_upqueue(iq).ctrl.get.dep.av(2) := true.B
      }
    }    
  }

  // ------------------------------
  //             BRANCH
  // ------------------------------
  for (iq <- 0 until p.nIntQueue) {
    w_upqueue(iq).valid := r_queue(iq).valid & ~(io.i_br_new.valid & r_queue(iq).ctrl.get.br.mask(io.i_br_new.tag)) & ~io.i_flush
    w_upqueue(iq).ctrl.get.br.mask := r_queue(iq).ctrl.get.br.mask & io.i_br_up
  }

  // ------------------------------
  //           SERIALIZE
  // ------------------------------
  val w_ser_av = Wire(Vec(p.nIntQueue, Bool()))
  val w_ser_unit = Wire(Vec(p.nIntQueue, UInt((log2Ceil(p.nIntUnit).W))))

  for (iq <- 0 until p.nIntQueue) {
    w_ser_av(iq) := false.B
    w_ser_unit(iq) := DontCare

    for (c <- 0 until p.nCommit) {
      for (iu <- 0 until p.nIntUnit) {
        when ((r_queue(iq).ctrl.get.ctrl.unit === CSR) & w_out(iu).valid & (w_out(iu).ctrl.get.ctrl.unit === CSR) & (w_out(iu).ctrl.get.info.entry === r_queue(iq).ctrl.get.dep.entry)) {
          w_ser_av(iq) := true.B
          w_ser_unit(iq) := iu.U
        }
      }        
    }          
  }  

  // ******************************
  //             WRITE
  // ******************************
  val w_wqueue = Wire(Vec(p.nIntQueue, new GenVBus(p, new IntQueueBus(p), UInt(0.W))))
  val w_pt_max = Wire(UInt(log2Ceil(p.nIntQueue + p.nBackPort).W))
  val w_wav = Wire(Bool())
  val w_pt = Wire(Vec(p.nBackPort + 1, UInt(log2Ceil(p.nIntQueue + 1).W)))

  w_wqueue := w_upqueue
  w_pt_max := r_pt + p.nBackPort.U(log2Ceil(p.nIntQueue + p.nBackPort).W)
  w_wav := (w_pt_max < p.nIntQueue.U(log2Ceil(p.nIntQueue + p.nBackPort).W))

  for (bp <- 0 until p.nBackPort) {
    io.b_in(bp).ready := w_wav
  }  

  w_pt(0) := r_pt
  for (bp <- 0 until p.nBackPort) {
    when (w_wav & io.b_in(bp).valid) {
      w_pt(bp + 1) := w_pt(bp) + 1.U
      w_wqueue(w_pt(bp)) := io.b_in(bp)
    }.otherwise {
      w_pt(bp + 1) := w_pt(bp)
    }
  }

  // ******************************
  //             READ
  // ******************************
  val w_rqueue = Wire(Vec(p.nIntQueue, new GenVBus(p, new IntQueueBus(p), UInt(0.W))))
  w_rqueue := w_wqueue

  // ------------------------------
  //          UOP READY ?
  // ------------------------------
  val w_ready_uop = Wire(Vec(p.nIntQueue, Vec(p.nIntUnit, Bool())))
  
  for (iq <- 0 until p.nIntQueue) {
    for (iu <- 0 until p.nIntUnit) {
      w_ready_uop(iq)(iu) := w_upqueue(iq).valid & w_upqueue(iq).ctrl.get.dep.av.asUInt.andR

      when (w_upqueue(iq).valid & w_upqueue(iq).ctrl.get.dep.av(1) & w_upqueue(iq).ctrl.get.dep.av(2) & w_ser_av(iq) & (iu.U === w_ser_unit(iq))) {
        w_ready_uop(iq)(iu) := true.B
      }  
    }  
  } 

  // ------------------------------
  //       INT UNIT READY ?
  // ------------------------------
  val w_ready_unit = Wire(Vec(p.nIntQueue, Vec(p.nIntUnit, Bool())))

  for (iq <- 0 until p.nIntQueue) {
    for (iu <- 0 until p.nIntUnit) {
      w_ready_unit(iq)(iu) := false.B
      switch (w_upqueue(iq).ctrl.get.ctrl.unit) {
        is (ALU)    {w_ready_unit(iq)(iu) := io.b_out(iu).av.alu}
        is (BRU)    {w_ready_unit(iq)(iu) := io.b_out(iu).av.bru}
        is (MUL)    {w_ready_unit(iq)(iu) := io.b_out(iu).av.mul}
        is (DIV)    {w_ready_unit(iq)(iu) := io.b_out(iu).av.div}
        is (CSR)    {w_ready_unit(iq)(iu) := io.b_out(iu).av.csr}
        is (BALU)   {w_ready_unit(iq)(iu) := io.b_out(iu).av.balu}
        is (CLMUL)  {w_ready_unit(iq)(iu) := io.b_out(iu).av.clmul}
      }
    }
  }

  // ------------------------------
  //           SLCT UOP
  // ------------------------------
  val w_rready = Wire(Vec(p.nIntUnit, Vec(p.nIntQueue, Bool())))
  val w_rav = Wire(Vec(p.nIntUnit, Bool()))
  val w_rslct = Wire(Vec(p.nIntUnit, UInt(log2Ceil(p.nIntQueue).W)))

  for (iu <- 0 until p.nIntUnit) {
    for (iq <- 0 until p.nIntQueue) {
      w_rready(iu)(iq) := w_ready_uop(iq)(iu) & w_ready_unit(iq)(iu)
    }
  }

  w_rav(0) := w_rready(0).asUInt.orR
  w_rslct(0) := PriorityEncoder(w_rready(0).asUInt)
  when (w_rav(0)) {
    for (iu <- 1 until p.nIntUnit) {
      w_rready(iu)(w_rslct(0)) := false.B
    }
  }

  for (iu0 <- 1 until p.nIntUnit) {
    w_rav(iu0) := w_rready(iu0).asUInt.orR
    w_rslct(iu0) := PriorityEncoder(w_rready(iu0).asUInt)
    when (w_rav(iu0)) {
      for (iu1 <- (iu0 + 1) until p.nIntUnit) {
        w_rready(iu1)(w_rslct(iu0)) := false.B
      }
    }
  }

  // ------------------------------
  //             QUEUE
  // ------------------------------
  for (iu <- 0 until p.nIntUnit) {
    when (w_rav(iu) & m_out(iu).io.b_in.ready) {
      w_rqueue(w_rslct(iu)).valid := false.B
    }
  }

  // ******************************
  //           COLLAPSE
  // ******************************
  val w_collapse = Wire(Vec(p.nIntQueue, Vec(p.nIntCollapse, Bool())))

  for (iq <- 0 until p.nIntQueue) {
    for (nc <- 0 until p.nIntCollapse) {
      w_collapse(iq)(nc) := false.B
    }
  }

  for (iq <- 1 until p.nIntQueue) {
    w_collapse(iq)(0) := w_collapse(iq - 1)(0) | ~w_wqueue(iq - 1).valid
    for (nc <- 1 until p.nIntCollapse) {
      w_collapse(iq)(nc) := w_collapse(iq - 1)(nc) | (~w_wqueue(iq - 1).valid & w_collapse(iq - 1)(nc - 1))
    }    
  }  

  // ******************************
  //            UPDATE
  // ******************************
  // Queue pointer
  when (w_hart_flush | io.i_flush) {
    r_pt := 0.U
  }.otherwise {
    r_pt := w_pt(p.nBackPort) - PopCount(w_collapse(w_pt(p.nBackPort)))
  }

  // Queue registers
  for (iq <- 0 until p.nIntQueue) {
    r_queue(iq).valid := false.B
  }

  for (iq <- 0 until p.nIntQueue) {
    r_queue(iq.U - PopCount(w_collapse(iq))) := w_rqueue(iq)
  }

  // ******************************
  //            OUTPUTS
  // ******************************  
  for (iu <- 0 until p.nIntUnit) {
    w_out(iu) := m_out(iu).io.o_val

    m_out(iu).io.i_flush := io.i_flush | (io.i_br_new.valid & w_out(iu).ctrl.get.br.mask(io.i_br_new.tag))
    m_out(iu).io.i_br_up := io.i_br_up

    m_out(iu).io.b_in.valid := w_rav(iu)
    m_out(iu).io.b_in.ctrl.get := w_upqueue(w_rslct(iu)).ctrl.get

    io.b_out(iu).valid := m_out(iu).io.b_out.valid
    io.b_out(iu).ctrl.get := m_out(iu).io.b_out.ctrl.get
    m_out(iu).io.b_out.ready := io.b_out(iu).ready
  }

  // ******************************
  //             DOME
  // ******************************
  if (p.useDome) {
    io.b_hart.get.free := true.B
    when (io.b_hart.get.flush) {
      for (iq <- 0 until p.nIntQueue) {
        r_queue(iq).valid := false.B
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
    for (iu <- 0 until p.nIntUnit) {
      dontTouch(io.b_out(iu).ctrl.get.info)
    }

    // ------------------------------
    //         DATA FOOTPRINT
    // ------------------------------

    // ------------------------------
    //       EXECUTION TRACKER
    // ------------------------------     
  }
}

object IntQueue extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new IntQueue(IntConfigBase), args)
}

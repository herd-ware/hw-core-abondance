/*
 * File: pipeline.scala                                                        *
 * Created Date: 2023-02-26 09:21:29 am                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-26 09:32:04 am                                       *
 * Modified By: Mathieu Escouteloup                                            *
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.core.abondance

import chisel3._
import chisel3.util._

import herd.common.dome._
import herd.common.mem.mb4s._
import herd.common.mem.cbo._
import herd.core.aubrac.common._
import herd.core.aubrac.nlp._
import herd.core.aubrac.front._
import herd.core.aubrac.back.csr.{Csr, CsrMemIO}
import herd.core.aubrac.dmu._
import herd.core.abondance.common._
import herd.core.abondance.back._
import herd.core.abondance.back.{BranchBus}
import herd.core.abondance.int._
import herd.core.abondance.lsu._
import herd.core.abondance.ext._
import herd.io.core.clint.{ClintIO}


class Pipeline (p: PipelineParams) extends Module {
  val io = IO(new Bundle {
    val b_dome = if (p.useDome) Some(Vec(p.nDome, new DomeIO(p.nAddrBit, p.nDataBit))) else None
    val b_hart = if (p.useDome) Some(new RsrcIO(p.nHart, p.nDome, 1)) else None

    val b_imem = new Mb4sIO(p.pL0IBus)

    val b_d0mem = new Mb4sIO(p.pL0D0Bus)
    val b_d1mem = new Mb4sIO(p.pL0D1Bus)    
    val b_cbo = if (p.useCbo) Some(new CboIO(1, p.useDome, p.nDome, p.nAddrBit)) else None

    val b_dmu = if (p.useCeps) Some(Flipped(new DmuIO(p, p.nAddrBit, p.nDataBit, p.nCepsTrapLvl))) else None
    val b_csr_mem = new CsrMemIO()
    val b_clint = Flipped(new ClintIO(p.nDataBit))

    val o_dbg = if (p.debug) Some(Output(new PipelineDbgBus(p))) else None
    val o_etd = if (p.debug) Some(Output(Vec(p.nCommit, new EtdBus(p.nHart, p.nAddrBit, p.nInstrBit)))) else None
  })

  val m_nlp = if (p.useNlp) Some(Module(new Nlp(p))) else None
  val m_front = Module(new Front(p))
  val m_back = Module(new Back(p))
  val m_int = Module(new IntPipeline(p))
  val m_lsu = Module(new Lsu(p))
  val m_csr = Module(new Csr(p))
  val m_dmu = if (p.useCeps) Some(Module(new Ext(p, new DmuCtrlBus(), 1, p.nDmuQueue, 4))) else None

  // ******************************
  //           NEW BRANCH
  // ******************************
  val w_br_new = Wire(new BranchBus(p.nAddrBit, p.nSpecBranch, p.nRobEntry))

  w_br_new := DontCare
  when (m_csr.io.o_br_trap(0).valid) {
    w_br_new.valid := m_csr.io.o_br_trap(0).valid
    w_br_new.addr := m_csr.io.o_br_trap(0).addr
  }.elsewhen (m_back.io.o_br_new.valid) {
    w_br_new := m_back.io.o_br_new  
  }.otherwise {
    w_br_new := m_int.io.o_br_new
  }  

  // ******************************
  //          FRONT & NLP
  // ******************************
  if (p.useNlp) {    
    if (p.useDome) m_nlp.get.io.b_hart.get <> io.b_hart.get
    if (p.useFastJal) {
      m_nlp.get.io.i_mispred := w_br_new.valid | m_front.io.o_br_new.get.valid
    } else {
      m_nlp.get.io.i_mispred := w_br_new.valid
    }    
    m_nlp.get.io.b_read <> m_front.io.b_nlp.get
    m_nlp.get.io.i_info := m_int.io.o_br_info
  }
  
  if (p.useDome) {
    m_front.io.b_hart.get <> io.b_hart.get
    m_front.io.i_flush := m_back.io.o_flush | m_back.io.o_init | m_int.io.o_flush | m_csr.io.o_br_trap(0).valid | io.b_dmu.get.ctrl.pipe_flush
    m_front.io.i_br_dome.get := io.b_dmu.get.ctrl.pipe_br
  } else {
    m_front.io.i_flush := m_back.io.o_flush | m_back.io.o_init | m_int.io.o_flush | m_csr.io.o_br_trap(0).valid
  }
  m_front.io.i_br_new.valid := w_br_new.valid
  m_front.io.i_br_new.addr := w_br_new.addr
  m_front.io.b_imem <> io.b_imem

  // ******************************
  //             BACK
  // ******************************
  if (p.useDome) {
    m_back.io.b_hart.get <> io.b_hart.get
    for (bp <- 0 until p.nBackPort) {
      m_back.io.b_back.get(bp) <> io.b_hart.get
    }
    m_back.io.i_flush := m_int.io.o_flush | m_csr.io.o_br_trap(0).valid | io.b_dmu.get.ctrl.pipe_flush
  } else {
    m_back.io.i_flush := m_int.io.o_flush | m_csr.io.o_br_trap(0).valid
  }
  m_back.io.i_flush := m_int.io.o_flush | m_csr.io.o_br_trap(0).valid
  m_back.io.b_in <> m_front.io.b_out
  m_back.io.i_br_up := m_int.io.o_br_up
  m_back.io.i_br_new := w_br_new
  m_back.io.b_clint <> io.b_clint

  // ------------------------------
  //             READ
  // ------------------------------
  for (iu <- 0 until p.nIntUnit) {
    m_back.io.b_read(iu * 2) <> m_int.io.b_read(iu)(0)
    m_back.io.b_read(iu * 2 + 1) <> m_int.io.b_read(iu)(1)
  }
  for (r <- 0 until 3) {
    m_back.io.b_read(2 * p.nIntUnit + r) <> m_lsu.io.b_read(r)
  }
  if (p.useCeps) {
    m_dmu.get.io.b_read(0) := DontCare
    m_dmu.get.io.b_read(0).ready := false.B
    m_back.io.b_read(2 * p.nIntUnit + 3) <> m_dmu.get.io.b_read(1)
  }

  // ------------------------------
  //            BYPASS
  // ------------------------------
  for (ib <- 0 until p.nIntBypass) {
    m_back.io.i_byp(ib) <> m_int.io.o_byp(ib)
  }
  for (l <- 0 until p.nLoad) {
    m_back.io.i_byp(p.nIntBypass + l) := m_lsu.io.o_byp(l)
  }
  if (p.useCeps) m_back.io.i_byp(p.nIntBypass + p.nLoad) := m_dmu.get.io.o_byp(0)

  // ------------------------------
  //              PC
  // ------------------------------
  for (iu <- 0 until p.nIntUnit) {
    m_back.io.b_pc(iu) <> m_int.io.b_pc(iu)
  }
  if (p.useCeps) m_dmu.get.io.b_pc(0) := DontCare

  // ------------------------------
  //             WRITE
  // ------------------------------
  for (iu <- 0 until p.nIntUnit) {    
    m_back.io.b_write(iu) <> m_int.io.b_write(iu)
  }
  for (l <- 0 until p.nLoad) {
    m_back.io.b_write(p.nIntUnit + l) <> m_lsu.io.b_write(l)
  }
  if (p.useCeps) m_back.io.b_write(p.nIntUnit + p.nLoad) <> m_dmu.get.io.b_write(0)

  // ------------------------------
  //              END
  // ------------------------------
  for (iu <- 0 until p.nIntUnit) {
    m_back.io.b_end(iu) <> m_int.io.b_end(iu)
  }
  for (l <- 0 until p.nLoad) {
    m_back.io.b_end(p.nIntUnit + l) <> m_lsu.io.b_end(l)
  }
  if (p.useCeps) m_back.io.b_end(p.nIntUnit + p.nLoad) <> m_dmu.get.io.b_end(0)

  // ******************************
  //             INT
  // ******************************
  if (p.useDome) {
    m_int.io.b_hart.get <> io.b_hart.get
    m_int.io.b_unit.get <> io.b_hart.get
    m_int.io.i_flush := m_back.io.o_init | io.b_dmu.get.ctrl.pipe_flush
  } else {
    m_int.io.i_flush := m_back.io.o_init
  }
  m_int.io.i_flush := m_back.io.o_init
  m_int.io.b_in <> m_back.io.b_int
  m_int.io.i_commit := m_back.io.o_commit
  m_int.io.i_busy := m_back.io.o_busy
  
  if (p.useCbo) m_int.io.b_cbo.get <> io.b_cbo.get

  // ******************************
  //             LSU
  // ******************************
  if (p.useDome) {
    m_lsu.io.b_hart.get <> io.b_hart.get
    m_lsu.io.i_flush := m_back.io.o_init | io.b_dmu.get.ctrl.pipe_flush
  } else {
    m_lsu.io.i_flush := m_back.io.o_init
  }
  m_lsu.io.b_in <> m_back.io.b_lsu
  m_lsu.io.i_br_up := m_int.io.o_br_up
  m_lsu.io.i_br_new := w_br_new
  m_lsu.io.i_commit := m_back.io.o_commit
  m_lsu.io.i_busy := m_back.io.o_busy

  m_lsu.io.b_d0mem <> io.b_d0mem
  m_lsu.io.b_d1mem <> io.b_d1mem

  // ******************************
  //             DMU
  // ******************************
  if (p.useCeps) {
    m_dmu.get.io.b_unit.get <> io.b_hart.get
    m_dmu.get.io.i_flush := m_back.io.o_init
    m_dmu.get.io.b_in <> m_back.io.b_dmu.get
    m_dmu.get.io.i_run := m_back.io.o_run
    m_dmu.get.io.i_br_up := m_int.io.o_br_up
    m_dmu.get.io.i_br_new := w_br_new
    m_dmu.get.io.i_commit := m_back.io.o_commit
    m_dmu.get.io.i_busy := m_back.io.o_busy

    io.b_dmu.get.ctrl.dmu_flush := m_back.io.o_init

    // Request & trap
    when (m_csr.io.b_trap.get(0).valid) {
      io.b_dmu.get.req <> m_csr.io.b_trap.get(0)
      m_dmu.get.io.b_port.req(0).ready := false.B
    }.otherwise {
      m_csr.io.b_trap.get(0).ready := false.B

      m_dmu.get.io.b_port.req(0).ready := io.b_dmu.get.req.ready
      io.b_dmu.get.req.valid := m_dmu.get.io.b_port.req(0).valid
      io.b_dmu.get.req.ctrl.get.code := m_dmu.get.io.b_port.req(0).ctrl.get.ctrl.code
      io.b_dmu.get.req.ctrl.get.op1 := m_dmu.get.io.b_port.req(0).ctrl.get.ctrl.op1
      io.b_dmu.get.req.ctrl.get.op2 := m_dmu.get.io.b_port.req(0).ctrl.get.ctrl.op2
      io.b_dmu.get.req.ctrl.get.op3 := m_dmu.get.io.b_port.req(0).ctrl.get.ctrl.op3
      io.b_dmu.get.req.ctrl.get.dcs1 := m_dmu.get.io.b_port.req(0).ctrl.get.ctrl.dcs1
      io.b_dmu.get.req.ctrl.get.dcs2 := m_dmu.get.io.b_port.req(0).ctrl.get.ctrl.dcs2
      io.b_dmu.get.req.ctrl.get.wb := m_dmu.get.io.b_port.req(0).ctrl.get.gpr.en

      io.b_dmu.get.req.data.get.s2 := m_dmu.get.io.b_port.req(0).data.get.s2
      io.b_dmu.get.req.data.get.s3 := m_dmu.get.io.b_port.req(0).data.get.s3
    }

    // Acknowledgement
    io.b_dmu.get.ack.ready := m_dmu.get.io.b_port.ack(0).ready
    m_dmu.get.io.b_port.ack(0).valid := io.b_dmu.get.ack.valid
    m_dmu.get.io.b_port.ack(0).ctrl.get.trap := io.b_dmu.get.ack.ctrl.get.trap
    m_dmu.get.io.b_port.ack(0).ctrl.get.stat := 0.U.asTypeOf(m_dmu.get.io.b_port.ack(0).ctrl.get.stat)
    m_dmu.get.io.b_port.ack(0).data.get := io.b_dmu.get.ack.data.get
  }

  // ******************************
  //             CSR
  // ******************************
  if (p.useDome) {
    m_csr.io.b_dome.get <> io.b_dome.get
    m_csr.io.b_hart.get(0) <> io.b_hart.get
  }

  m_csr.io.b_read(0) <> m_int.io.b_csr.read
  m_csr.io.b_write(0) <> m_int.io.b_csr.write

  m_csr.io.i_stat(0) := m_back.io.o_stat
  m_csr.io.b_mem(0) <> io.b_csr_mem
  m_csr.io.i_trap(0) := m_back.io.o_trap
  if (p.useCeps) m_csr.io.b_dmu.get(0) <> io.b_dmu.get.csr
  m_csr.io.b_clint(0) <> io.b_clint
  
  // ******************************
  //             DOME
  // ******************************
  if (p.useDome) {
    val w_back_free = Wire(Vec(p.nBackPort, Bool()))
    val w_hart_free = Wire(Vec(9, Bool()))

    // ------------------------------
    //           BACKPORT
    // ------------------------------
    for (bp <- 0 until p.nBackPort) {
      w_back_free(bp) := m_back.io.b_back.get(bp).free
    }

    // ------------------------------
    //             HART
    // ------------------------------
    if (p.useNlp) w_hart_free(0) := m_front.io.b_hart.get.free else w_hart_free(0) := true.B
    w_hart_free(1) := m_front.io.b_hart.get.free
    w_hart_free(2) := m_back.io.b_hart.get.free
    w_hart_free(3) := w_back_free.asUInt.andR
    w_hart_free(4) := m_int.io.b_hart.get.free
    w_hart_free(5) := m_int.io.b_unit.get.free
    w_hart_free(6) := m_csr.io.b_hart.get(0).free
    w_hart_free(7) := m_lsu.io.b_hart.get.free
    w_hart_free(8) := m_dmu.get.io.b_unit.get.free

    io.b_hart.get.free := w_hart_free.asUInt.andR
  }

  // ******************************
  //             DEBUG
  // ******************************
  if (p.debug) {
    // ------------------------------
    //            SIGNALS
    // ------------------------------
    io.o_dbg.get.last := m_back.io.o_dbg.get.last
    io.o_dbg.get.x := m_back.io.o_dbg.get.x
    io.o_dbg.get.csr := m_csr.io.o_dbg.get(0)

    // ------------------------------
    //         DATA FOOTPRINT
    // ------------------------------

    // ------------------------------
    //       EXECUTION TRACKER
    // ------------------------------  
    io.o_etd.get := m_back.io.o_etd.get
    if (p.useCeps) {
      io.b_dmu.get.req.ctrl.get.etd.get := m_dmu.get.io.b_port.req(0).ctrl.get.etd.get
      m_dmu.get.io.b_port.ack(0).ctrl.get.etd.get := DontCare
    }
  }
}

object Pipeline extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new Pipeline(PipelineConfigBase), args)
}

/*
 * File: pipeline.scala
 * Created Date: 2023-02-26 09:21:29 am                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-03-01 12:22:36 pm
 * Modified By: Mathieu Escouteloup
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.core.abondance

import chisel3._
import chisel3.util._

import herd.common.field._
import herd.common.mem.mb4s._
import herd.common.mem.cbo._
import herd.core.aubrac.common._
import herd.core.aubrac.nlp._
import herd.core.aubrac.front._
import herd.core.aubrac.back.csr.{Csr, CsrMemIO}
import herd.core.aubrac.hfu._
import herd.core.abondance.common._
import herd.core.abondance.back._
import herd.core.abondance.back.{BranchBus}
import herd.core.abondance.int._
import herd.core.abondance.lsu._
import herd.core.abondance.ext._
import herd.io.core.clint.{ClintIO}


class Pipeline (p: PipelineParams) extends Module {
  val io = IO(new Bundle {
    val b_field = if (p.useField) Some(Vec(p.nField, new FieldIO(p.nAddrBit, p.nDataBit))) else None
    val b_hart = if (p.useField) Some(new RsrcIO(p.nHart, p.nField, 1)) else None

    val b_imem = new Mb4sIO(p.pL0IBus)

    val b_d0mem = new Mb4sIO(p.pL0D0Bus)
    val b_d1mem = new Mb4sIO(p.pL0D1Bus)    
    val b_cbo = if (p.useCbo) Some(new CboIO(1, p.useField, p.nField, p.nAddrBit)) else None

    val b_hfu = if (p.useChamp) Some(Flipped(new HfuIO(p, p.nAddrBit, p.nDataBit, p.nChampTrapLvl))) else None
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
  val m_hfu = if (p.useChamp) Some(Module(new Ext(p, new HfuCtrlBus(), 1, p.nHfuQueue, 4))) else None

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
    if (p.useField) m_nlp.get.io.b_hart.get <> io.b_hart.get
    if (p.useFastJal) {
      m_nlp.get.io.i_mispred := w_br_new.valid | m_front.io.o_br_new.get.valid
    } else {
      m_nlp.get.io.i_mispred := w_br_new.valid
    }    
    m_nlp.get.io.b_read <> m_front.io.b_nlp.get
    m_nlp.get.io.i_info := m_int.io.o_br_info
  }
  
  if (p.useField) {
    m_front.io.b_hart.get <> io.b_hart.get
    m_front.io.i_flush := m_back.io.o_flush | m_back.io.o_init | m_int.io.o_flush | m_csr.io.o_br_trap(0).valid | io.b_hfu.get.ctrl.pipe_flush
    m_front.io.i_br_field.get := io.b_hfu.get.ctrl.pipe_br
  } else {
    m_front.io.i_flush := m_back.io.o_flush | m_back.io.o_init | m_int.io.o_flush | m_csr.io.o_br_trap(0).valid
  }
  m_front.io.i_br_new.valid := w_br_new.valid
  m_front.io.i_br_new.addr := w_br_new.addr
  m_front.io.b_imem <> io.b_imem

  // ******************************
  //             BACK
  // ******************************
  if (p.useField) {
    m_back.io.b_hart.get <> io.b_hart.get
    for (bp <- 0 until p.nBackPort) {
      m_back.io.b_back.get(bp) <> io.b_hart.get
    }
    m_back.io.i_flush := m_int.io.o_flush | m_csr.io.o_br_trap(0).valid | io.b_hfu.get.ctrl.pipe_flush
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
  if (p.useChamp) {
    m_hfu.get.io.b_read(0) := DontCare
    m_hfu.get.io.b_read(0).ready := false.B
    m_back.io.b_read(2 * p.nIntUnit + 3) <> m_hfu.get.io.b_read(1)
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
  if (p.useChamp) m_back.io.i_byp(p.nIntBypass + p.nLoad) := m_hfu.get.io.o_byp(0)

  // ------------------------------
  //              PC
  // ------------------------------
  for (iu <- 0 until p.nIntUnit) {
    m_back.io.b_pc(iu) <> m_int.io.b_pc(iu)
  }
  if (p.useChamp) m_hfu.get.io.b_pc(0) := DontCare

  // ------------------------------
  //             WRITE
  // ------------------------------
  for (iu <- 0 until p.nIntUnit) {    
    m_back.io.b_write(iu) <> m_int.io.b_write(iu)
  }
  for (l <- 0 until p.nLoad) {
    m_back.io.b_write(p.nIntUnit + l) <> m_lsu.io.b_write(l)
  }
  if (p.useChamp) m_back.io.b_write(p.nIntUnit + p.nLoad) <> m_hfu.get.io.b_write(0)

  // ------------------------------
  //              END
  // ------------------------------
  for (iu <- 0 until p.nIntUnit) {
    m_back.io.b_end(iu) <> m_int.io.b_end(iu)
  }
  for (l <- 0 until p.nLoad) {
    m_back.io.b_end(p.nIntUnit + l) <> m_lsu.io.b_end(l)
  }
  if (p.useChamp) m_back.io.b_end(p.nIntUnit + p.nLoad) <> m_hfu.get.io.b_end(0)

  // ******************************
  //             INT
  // ******************************
  if (p.useField) {
    m_int.io.b_hart.get <> io.b_hart.get
    m_int.io.b_unit.get <> io.b_hart.get
    m_int.io.i_flush := m_back.io.o_init | io.b_hfu.get.ctrl.pipe_flush
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
  if (p.useField) {
    m_lsu.io.b_hart.get <> io.b_hart.get
    m_lsu.io.i_flush := m_back.io.o_init | io.b_hfu.get.ctrl.pipe_flush
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
  //             HFU
  // ******************************
  if (p.useChamp) {
    m_hfu.get.io.b_unit.get <> io.b_hart.get
    m_hfu.get.io.i_flush := m_back.io.o_init
    m_hfu.get.io.b_in <> m_back.io.b_hfu.get
    m_hfu.get.io.i_run := m_back.io.o_run
    m_hfu.get.io.i_br_up := m_int.io.o_br_up
    m_hfu.get.io.i_br_new := w_br_new
    m_hfu.get.io.i_commit := m_back.io.o_commit
    m_hfu.get.io.i_busy := m_back.io.o_busy

    io.b_hfu.get.ctrl.hfu_flush := m_back.io.o_init

    // Request & trap
    when (m_csr.io.b_trap.get(0).valid) {
      io.b_hfu.get.req <> m_csr.io.b_trap.get(0)
      m_hfu.get.io.b_port.req(0).ready := false.B
    }.otherwise {
      m_csr.io.b_trap.get(0).ready := false.B

      m_hfu.get.io.b_port.req(0).ready := io.b_hfu.get.req.ready
      io.b_hfu.get.req.valid := m_hfu.get.io.b_port.req(0).valid
      io.b_hfu.get.req.ctrl.get.code := m_hfu.get.io.b_port.req(0).ctrl.get.ctrl.code
      io.b_hfu.get.req.ctrl.get.op1 := m_hfu.get.io.b_port.req(0).ctrl.get.ctrl.op1
      io.b_hfu.get.req.ctrl.get.op2 := m_hfu.get.io.b_port.req(0).ctrl.get.ctrl.op2
      io.b_hfu.get.req.ctrl.get.op3 := m_hfu.get.io.b_port.req(0).ctrl.get.ctrl.op3
      io.b_hfu.get.req.ctrl.get.hfs1 := m_hfu.get.io.b_port.req(0).ctrl.get.ctrl.hfs1
      io.b_hfu.get.req.ctrl.get.hfs2 := m_hfu.get.io.b_port.req(0).ctrl.get.ctrl.hfs2
      io.b_hfu.get.req.ctrl.get.wb := m_hfu.get.io.b_port.req(0).ctrl.get.gpr.en

      io.b_hfu.get.req.data.get.s2 := m_hfu.get.io.b_port.req(0).data.get.s2
      io.b_hfu.get.req.data.get.s3 := m_hfu.get.io.b_port.req(0).data.get.s3
    }

    // Acknowledgement
    io.b_hfu.get.ack.ready := m_hfu.get.io.b_port.ack(0).ready
    m_hfu.get.io.b_port.ack(0).valid := io.b_hfu.get.ack.valid
    m_hfu.get.io.b_port.ack(0).ctrl.get.trap := io.b_hfu.get.ack.ctrl.get.trap
    m_hfu.get.io.b_port.ack(0).ctrl.get.stat := 0.U.asTypeOf(m_hfu.get.io.b_port.ack(0).ctrl.get.stat)
    m_hfu.get.io.b_port.ack(0).data.get := io.b_hfu.get.ack.data.get
  }

  // ******************************
  //             CSR
  // ******************************
  if (p.useField) {
    m_csr.io.b_field.get <> io.b_field.get
    m_csr.io.b_hart.get(0) <> io.b_hart.get
  }

  m_csr.io.b_read(0) <> m_int.io.b_csr.read
  m_csr.io.b_write(0) <> m_int.io.b_csr.write

  m_csr.io.i_stat(0) := m_back.io.o_stat
  m_csr.io.b_mem(0) <> io.b_csr_mem
  m_csr.io.i_trap(0) := m_back.io.o_trap
  if (p.useChamp) m_csr.io.b_hfu.get(0) <> io.b_hfu.get.csr
  m_csr.io.b_clint(0) <> io.b_clint
  
  // ******************************
  //            FIELD
  // ******************************
  if (p.useField) {
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
    w_hart_free(8) := m_hfu.get.io.b_unit.get.free

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
    if (p.useChamp) {
      io.b_hfu.get.req.ctrl.get.etd.get := m_hfu.get.io.b_port.req(0).ctrl.get.etd.get
      m_hfu.get.io.b_port.ack(0).ctrl.get.etd.get := DontCare
    }
  }
}

object Pipeline extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new Pipeline(PipelineConfigBase), args)
}

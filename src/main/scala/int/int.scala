/*
 * File: int.scala
 * Created Date: 2023-02-26 09:21:29 am                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-28 10:42:03 pm
 * Modified By: Mathieu Escouteloup
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
import herd.common.isa.riscv._
import herd.common.mem.cbo._
import herd.core.aubrac.back.csr.{CsrIO}
import herd.core.aubrac.nlp.{BranchInfoBus}
import herd.core.abondance.common._
import herd.core.abondance.back.{BypassBus,BranchBus,GprReadIO,GprWriteIO,RobPcIO,EndIO,CommitBus}


class IntPipeline (p: IntParams) extends Module {
  require (p.nBru == 1, "The whole core must have only one BRU unit.")
  require (p.nCsr == 1, "The whole core must have only one CSR unit.")

  val io = IO(new Bundle {
    val b_hart = if (p.useDome) Some(new RsrcIO(p.nHart, p.nDome, 1)) else None
    val b_unit = if (p.useDome) Some(new RsrcIO(p.nHart, p.nDome, p.nIntUnit)) else None

    val i_flush = Input(Bool())

    val b_in = Vec(p.nBackPort, Flipped(new GenRVIO(p, new IntQueueBus(p), UInt(0.W))))

    val i_commit = Input(Vec(p.nCommit, new CommitBus(p.nRobEntry, 1, p.nGprPhy)))
    val i_busy = Input(Vec(p.nBypass, new BypassBus(p.nDataBit, p.nGprPhy)))

    val b_read = Flipped(Vec(p.nIntUnit, Vec(2, new GprReadIO(p.nDataBit, p.nGprPhy))))
    val b_pc = Flipped(Vec(p.nIntUnit, new RobPcIO(p.nAddrBit, p.nRobEntry)))
    val b_csr = Flipped(new CsrIO(p.nDataBit))

    val o_flush = Output(Bool())
    val b_cbo = if (p.useCbo) Some(new CboIO(1, p.useDome, p.nDome, p.nAddrBit)) else None

    val o_br_up = Output(UInt(p.nSpecBranch.W))
    val o_br_new = Output(new BranchBus(p.nAddrBit, p.nSpecBranch, p.nRobEntry))
    val o_br_info = Output(new BranchInfoBus(p.nAddrBit))

    val o_byp = Output(Vec(p.nIntBypass, new BypassBus(p.nDataBit, p.nGprPhy)))
    val b_write = Flipped(Vec(p.nIntUnit, new GprWriteIO(p.nDataBit, p.nGprPhy)))
    val b_end = Vec(p.nIntUnit, new EndIO(p.debug, p.nAddrBit, p.nDataBit, p.nRobEntry))
  })

  val m_queue = Module(new IntQueue(p))

  val m_unit = for (iu <- 0 until p.nIntUnit) yield {
    val m_unit = Module(new IntUnit(p.pIntUnit(iu)))
    m_unit
  }

  val w_br_up = Wire(UInt(p.nSpecBranch.W))
  val w_br_new = Wire(new BranchBus(p.nAddrBit, p.nSpecBranch, p.nRobEntry))

  // ******************************
  //             QUEUE
  // ******************************
  if (p.useDome) m_queue.io.b_hart.get <> io.b_hart.get
  m_queue.io.i_flush := io.i_flush

  m_queue.io.b_in <> io.b_in

  m_queue.io.i_br_up := w_br_up
  m_queue.io.i_br_new := w_br_new
  m_queue.io.i_busy := io.i_busy
  m_queue.io.i_commit := io.i_commit

  // ******************************
  //             UNIT
  // ******************************
  for (iu <- 0 until p.nIntUnit) {
    if (p.useDome) m_unit(iu).io.b_unit.get <> io.b_unit.get
    m_unit(iu).io.i_flush := io.i_flush
    m_unit(iu).io.b_in <> m_queue.io.b_out(iu)

    m_unit(iu).io.i_br_up := w_br_up
    m_unit(iu).io.i_br_new := w_br_new

    m_unit(iu).io.b_read <> io.b_read(iu)
    m_unit(iu).io.b_pc <> io.b_pc(iu)
    if (p.pIntUnit(iu).useCsr) m_unit(iu).io.b_csr.get <> io.b_csr

    if (p.pIntUnit(iu).useBru) {
      w_br_up := m_unit(iu).io.o_br_up.get
      w_br_new := m_unit(iu).io.o_br_new.get

      io.o_br_up := m_unit(iu).io.o_br_up.get
      io.o_br_new := m_unit(iu).io.o_br_new.get
      io.o_br_info := m_unit(iu).io.o_br_info.get
      io.o_flush := m_unit(iu).io.o_flush.get
      if (p.useCbo) io.b_cbo.get <> m_unit(iu).io.b_cbo.get
    }

    m_unit(iu).io.b_write <> io.b_write(iu)
    io.b_end(iu) <> m_unit(iu).io.b_end
  }

  // ******************************
  //            BYPASS
  // ******************************
  var byp: Int = 0
  for (iu <- 0 until p.nIntUnit) {
    for (b <- 0 until p.pIntUnit(iu).nBypass) {
      io.o_byp(byp + b) := m_unit(iu).io.o_byp(b)
    }
    byp = byp + p.pIntUnit(iu).nBypass
  }

  // ******************************
  //             DOME
  // ******************************
  if (p.useDome) {
    val w_unit_free = Wire(Vec(p.nIntUnit, Bool()))

    for (iu <- 0 until p.nIntUnit) {
      w_unit_free(iu) := m_unit(iu).io.b_unit.get.free
    }

    io.b_hart.get.free := m_queue.io.b_hart.get.free
    io.b_unit.get.free := w_unit_free.asUInt.andR
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
  }
}

object IntPipeline extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new IntPipeline(IntConfigBase), args)
}

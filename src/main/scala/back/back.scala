/*
 * File: back.scala
 * Created Date: 2023-02-26 09:21:29 am                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-03-01 12:21:23 pm
 * Modified By: Mathieu Escouteloup
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
import herd.common.dome._
import herd.common.isa.riscv._
import herd.common.isa.count.{CsrBus => CountBus}
import herd.core.aubrac.common._
import herd.core.aubrac.front.{FrontBus}
import herd.core.abondance.common._
import herd.core.abondance.int.{IntQueueBus}
import herd.core.abondance.lsu.{LsuQueueBus}
import herd.core.abondance.ext.{ExtReqQueueBus}
import herd.io.core.clint.{ClintIO}


class Back (p: BackParams) extends Module {
  val io = IO(new Bundle {
    val b_hart = if (p.useDome) Some(new RsrcIO(1, 1, 1)) else None
    val b_back = if (p.useDome) Some(Vec(p.nBackPort, new RsrcIO(1, 1, 1))) else None

    val b_in = Vec(p.nBackPort, Flipped(new GenRVIO(p,new FrontBus(p.debug, p.nAddrBit, p.nInstrBit), UInt(0.W))))

    val o_run = Output(Bool())
    val o_init = Output(Bool())
    val o_flush = Output(Bool())
    val o_br_new = Output(new BranchBus(p.nAddrBit, p.nSpecBranch, p.nRobEntry))
    val i_flush = Input(Bool())
    val i_br_up = Input(UInt(p.nSpecBranch.W))
    val i_br_new = Input(new BranchBus(p.nAddrBit, p.nSpecBranch, p.nRobEntry))

    val i_byp = Input(Vec(p.nBypass, new BypassBus(p.nDataBit, p.nGprPhy)))
    val b_read = Vec(p.nGprReadLog, new GprReadIO(p.nDataBit, p.nGprPhy))
    val b_write = Vec(p.nGprWriteLog, new GprWriteIO(p.nDataBit, p.nGprPhy))
    
    val b_pc = Vec(p.nRobPcRead, new RobPcIO(p.nAddrBit, p.nRobEntry))
    val b_end = Vec(p.nExUnit, Flipped(new EndIO(p.debug, p.nAddrBit, p.nDataBit, p.nRobEntry)))
    val o_commit = Output(Vec(p.nCommit, new CommitBus(p.nRobEntry, p.nGprLog, p.nGprPhy)))
    val o_busy = Output(Vec(p.nBypass, new BypassBus(p.nDataBit, p.nGprPhy)))
    val o_trap = Output(new TrapBus(p.nAddrBit, p.nDataBit))

    val b_clint = Flipped(new ClintIO(p.nDataBit))

    val o_stat = Output(new CountBus())

    val b_int = Vec(p.nBackPort, new GenRVIO(p, new IntQueueBus(p), UInt(0.W)))
    val b_lsu = Vec(p.nBackPort, new GenRVIO(p, new LsuQueueBus(p), UInt(0.W)))
    val b_hfu = if (p.useChamp) Some(Vec(p.nBackPort, new GenRVIO(p, new ExtReqQueueBus(p, new HfuCtrlBus()), UInt(0.W)))) else None
  
    val o_dbg = if (p.debug) Some(Output(new DbgBus(p))) else None
    val o_etd = if (p.debug) Some(Output(Vec(p.nCommit, new EtdBus(p.nHart, p.nAddrBit, p.nInstrBit)))) else None
  })

  val m_id = Module(new IdStage(p))
  val m_ren = Module(new RenStage(p))
  val m_rob = Module(new Rob(p))
  val m_gpr = Module(new Gpr(p))
  val m_iss = Module(new IssStage(p))

  // ******************************
  //              ID
  // ******************************
  if (p.useDome) {
    m_id.io.b_hart.get <> io.b_hart.get
    m_id.io.b_back.get <> io.b_back.get
  }

  m_id.io.i_init := m_rob.io.o_init
  m_id.io.i_flush := io.i_flush | m_rob.io.o_init

  m_id.io.b_in <> io.b_in

  m_id.io.i_br_up := io.i_br_up
  m_id.io.i_br_new := io.i_br_new

  // ******************************
  //              REN
  // ******************************
  if (p.useDome) {
    m_ren.io.b_back.get <> io.b_back.get
  }
  m_ren.io.i_flush := io.i_flush | m_rob.io.o_init

  m_ren.io.b_in <> m_id.io.b_out

  m_ren.io.i_br_up := io.i_br_up
  m_ren.io.b_map <> m_gpr.io.b_map
  m_ren.io.b_remap <> m_gpr.io.b_remap
  m_ren.io.b_rob <> m_rob.io.b_write
  
  // ******************************
  //              GPR
  // ******************************
  if (p.useDome) {
    m_gpr.io.b_hart.get <> io.b_hart.get
  }
  m_gpr.io.i_br_act := m_id.io.o_br_act  
  m_gpr.io.i_br_new := io.i_br_new   
  m_gpr.io.i_commit := m_rob.io.o_commit
  m_gpr.io.i_init := m_rob.io.o_init
  m_gpr.io.i_byp := io.i_byp
  m_gpr.io.b_read <> io.b_read
  m_gpr.io.b_write <> io.b_write

  // ******************************
  //              ROB
  // ******************************
  if (p.useDome) {
    m_rob.io.b_hart.get <> io.b_hart.get
  }
  m_rob.io.b_pc <> io.b_pc
  m_rob.io.i_br_up := io.i_br_up
  m_rob.io.i_br_new := io.i_br_new
  for (eu <- 0 until p.nExUnit) {
    m_rob.io.b_end(eu) <> io.b_end(eu)
  }
  m_rob.io.b_clint <> io.b_clint

  // ******************************
  //              ISS
  // ******************************
  if (p.useDome) {
    m_iss.io.b_hart.get <> io.b_hart.get
    m_iss.io.b_back.get <> io.b_back.get
  }
  m_iss.io.i_init := m_rob.io.o_init
  m_iss.io.i_flush := io.i_flush | m_rob.io.o_init

  m_iss.io.b_in <> m_ren.io.b_out

  m_iss.io.i_br_new := io.i_br_new
  m_iss.io.i_br_up := io.i_br_up
  m_iss.io.b_busy <> m_gpr.io.b_busy
  m_iss.io.i_commit := m_rob.io.o_commit

  m_iss.io.b_int <> io.b_int
  m_iss.io.b_lsu <> io.b_lsu
  if (p.useChamp) m_iss.io.b_hfu.get <> io.b_hfu.get

  // ******************************
  //              I/O
  // ******************************
  io.o_run := m_rob.io.o_run
  io.o_init := m_rob.io.o_init
  io.o_flush := io.i_flush | m_rob.io.o_init
  io.o_br_new := m_rob.io.o_br_new

  io.o_commit := m_rob.io.o_commit
  io.o_busy := io.i_byp
  io.o_trap := m_rob.io.o_trap

  io.o_stat := m_rob.io.o_stat

  // ******************************
  //             DOME
  // ******************************
  if (p.useDome) {
    io.b_hart.get.free := m_id.io.b_hart.get.free & m_gpr.io.b_hart.get.free & m_rob.io.b_hart.get.free & m_iss.io.b_hart.get.free

    for (bp <- 0 until p.nBackPort) {
      io.b_back.get(bp).free := m_id.io.b_back.get(bp).free & m_ren.io.b_back.get(bp).free & m_iss.io.b_back.get(bp).free
    }
  }

  // ******************************
  //             DEBUG
  // ******************************
  if (p.debug) {
    // ------------------------------
    //            SIGNALS
    // ------------------------------
    io.o_dbg.get.last := m_rob.io.o_dbg.get
    io.o_dbg.get.x := m_gpr.io.o_dbg.get

    // ------------------------------
    //         DATA FOOTPRINT
    // ------------------------------

    // ------------------------------
    //       EXECUTION TRACKER
    // ------------------------------
    io.o_etd.get := m_rob.io.o_etd.get
  }
}

object Back extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new Back(BackConfigBase), args)
}

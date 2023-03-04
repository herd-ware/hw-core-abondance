/*
 * File: bus.scala
 * Created Date: 2023-02-26 09:21:29 am                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-03-03 08:00:20 am
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
import herd.common.core.{HpcInstrBus}
import herd.core.aubrac.common.{TrapBus, EtdBus}
import herd.core.aubrac.back.{OP, IMM}
import herd.core.aubrac.hfu.{CODE => HFUCODE, OP => HFUOP}
import herd.core.abondance.common._
import herd.core.abondance.int._
import herd.core.abondance.lsu._


// ******************************
//            BRANCH
// ******************************
class BranchBus (nAddrBit: Int, nSpecBranch: Int, nRobEntry: Int) extends Bundle {
  val valid = Bool()
  val addr = UInt(nAddrBit.W)
  val tag = UInt(log2Ceil(nSpecBranch).W)
  val entry = UInt(log2Ceil(nRobEntry).W)
}

class BranchDecodeBus extends Bundle {
  val jal = Bool()
  val br = Bool()
}

// ******************************
//       INFORMATIONS BUS
// ******************************
class InfoBus(nAddrBit: Int, nInstrBit: Int, nRobEntry: Int) extends Bundle {
  val pc = UInt(nAddrBit.W)
  val instr = UInt(nInstrBit.W)
  val entry = UInt(log2Ceil(nRobEntry).W)
  val ser = Bool()
}

// ******************************
//          CONTROL BUS
// ******************************
// ------------------------------
//            INTERNAL
// ------------------------------
class GprCtrlBus(nGprPhy: Int) extends Bundle {
  val en = Bool()
  val rdp = UInt(log2Ceil(nGprPhy).W)
}

class ExCtrlBus (p: BackParams) extends Bundle {
  val ex_type = UInt(EXTYPE.NBIT.W)
  val int = new IntCtrlBus()
  val lsu = new LsuCtrlBus()
  val gpr = new GprCtrlBus(p.nGprPhy)
}

class DependBus(nRobEntry: Int, nGprPhy: Int) extends Bundle {
  val av = Vec(3, Bool())
  val entry = UInt(log2Ceil(nRobEntry).W)
  val rs1p = UInt(log2Ceil(nGprPhy).W)
  val rs2p = UInt(log2Ceil(nGprPhy).W)
}

class DataDecodeBus (p: BackParams) extends Bundle {
  val s1type = UInt(OP.NBIT.W)
  val s2type = UInt(OP.NBIT.W)
  val s3type = UInt(OP.NBIT.W)
  val imm1type = UInt(IMM.NBIT.W)
  val imm2type = UInt(IMM.NBIT.W)
  val rs1l = UInt(log2Ceil(p.nGprLog).W)
  val rs2l = UInt(log2Ceil(p.nGprLog).W)
  val rdl = UInt(log2Ceil(p.nGprLog).W)
}

class DataExBus (nDataBit: Int, nGprPhy: Int) extends Bundle {
  val s1type = UInt(OP.NBIT.W)
  val s2type = UInt(OP.NBIT.W)
  val s3type = UInt(OP.NBIT.W)
  val imm1 = UInt(nDataBit.W)
  val imm2 = UInt(nDataBit.W)
}

// ------------------------------
//            EXTERNAL
// ------------------------------
class ExtCtrlBus extends Bundle {
  val code = UInt(8.W)
  val op1 = UInt(3.W)
  val op2 = UInt(3.W)
  val op3 = UInt(3.W)
  val rs1 = UInt(5.W)
  val rs2 = UInt(5.W)
  val rd = UInt(5.W)
}

class HfuCtrlBus extends Bundle {
  val code = UInt(HFUCODE.NBIT.W)
  val op1 = UInt(HFUOP.NBIT.W)
  val op2 = UInt(HFUOP.NBIT.W)
  val op3 = UInt(HFUOP.NBIT.W)
  val hfs1 = UInt(5.W)
  val hfs2 = UInt(5.W)
}

// ******************************
//       STAGE CONTROL BUS
// ******************************
class RenCtrlBus(p: BackParams) extends SpecCtrlBus(p.nSpecBranch) {
  val info = new InfoBus(p.nAddrBit, p.nInstrBit, p.nRobEntry)
  val trap = new TrapBus(p.nAddrBit, p.nDataBit)

  val ex = new ExCtrlBus(p)
  val data = new DataDecodeBus(p)

  val ext = new ExtCtrlBus()

  val etd = if (p.debug) Some(new EtdBus(p.nHart, p.nAddrBit, p.nInstrBit)) else None
}

class IssCtrlBus(p: BackParams) extends SpecCtrlBus(p.nSpecBranch) {
  val info = new InfoBus(p.nAddrBit, p.nInstrBit, p.nRobEntry)

  val ex = new ExCtrlBus(p)
  val dep = new DependBus(p.nRobEntry, p.nGprPhy)
  val data = new DataExBus(p.nDataBit, p.nGprPhy)
  
  val ext = new ExtCtrlBus()

  val etd = if (p.debug) Some(new EtdBus(p.nHart, p.nAddrBit, p.nInstrBit)) else None
}

// ******************************
//             GPR
// ******************************
class MapIO (p: BackParams) extends Bundle {
  val rsl = Input(UInt(log2Ceil(p.nGprLog).W))
  val rsp = Output(UInt(log2Ceil(p.nGprPhy).W))
}

class RemapIO (p: BackParams) extends Bundle {
  val valid = Input(Bool())
  val alloc = Input(Bool())
  val br = Input(new SpecBus(p.nSpecBranch))
  val rdl = Input(UInt(log2Ceil(p.nGprLog).W))
  val ready = Output(Bool())
  val rdp = Output(UInt(log2Ceil(p.nGprPhy).W))
}

class GprReadIO (nDataBit: Int, nGprPhy: Int) extends Bundle {
  val valid = Input(Bool())
  val rsp = Input(UInt(log2Ceil(nGprPhy).W))
  val ready = Output(Bool())
  val data = Output(UInt(nDataBit.W))
}

class GprWriteIO (nDataBit: Int, nGprPhy: Int) extends Bundle {
  val valid = Input(Bool())
  val rdp = Input(UInt(log2Ceil(nGprPhy).W))
  val data = Input(UInt(nDataBit.W))
  val ready = Output(Bool())
}

class BypassBus(nDataBit: Int, nGprPhy: Int) extends Bundle {
  val valid = Bool()
  val done = Bool()
  val rdp = UInt(log2Ceil(nGprPhy).W)
  val data = UInt(nDataBit.W)
}

// ******************************
//             ROB
// ******************************
class RobEntryBus (p: BackParams) extends Bundle {
  val valid = Bool()

  val pc = UInt(p.nAddrBit.W)
  val busy = Bool()
  val replay = Bool()
  val exc = Bool()
  val br_mask = UInt(p.nSpecBranch.W)
  val rdl = UInt(log2Ceil(p.nGprLog).W)
  val rdp = UInt(log2Ceil(p.nGprPhy).W)

  val hpc = new HpcInstrBus()

  val etd = if (p.debug) Some(new EtdBus(p.nHart, p.nAddrBit, p.nInstrBit)) else None
}

class RobWriteIO (p: BackParams) extends Bundle {
  val valid = Input(Bool())
  val data = Input(new RobEntryBus(p))
  val trap = Input(new TrapBus(p.nAddrBit, p.nDataBit))
  val ready = Output(Bool())
  val entry = Output(UInt(log2Ceil(p.nRobEntry).W))
}

class RobPcIO (nAddrBit: Int, nRobEntry: Int) extends Bundle {
  val entry = Input(UInt(log2Ceil(nRobEntry).W))
  val pc = Output(UInt(nAddrBit.W))
  val nready = Output(Bool())
  val npc = Output(UInt(nAddrBit.W))
}

class EndIO (debug: Boolean, nAddrBit: Int, nDataBit: Int, nRobEntry: Int) extends Bundle {
  val ready = Input(Bool())
  val valid = Output(Bool())
  val entry = Output(UInt(log2Ceil(nRobEntry).W))
  val replay = Output(Bool())
  val trap = Output(new TrapBus(nAddrBit, nDataBit))
  
  val hpc = Output(new HpcInstrBus())

  val etd = if (debug) Some(Output(new EtdBus(1, nAddrBit, 0))) else None
}

class CommitBus (nRobEntry: Int, nGprLog: Int, nGprPhy: Int) extends Bundle {
  val valid = Bool()
  val entry = UInt(log2Ceil(nRobEntry).W)
  val rdl = UInt(log2Ceil(nGprLog).W)
  val rdp = UInt(log2Ceil(nGprPhy).W)
}

// ******************************
//           BACK BUS
// ******************************
class BackBus(nAddrBit: Int, nDataBit: Int, nRobEntry: Int) extends Bundle {
  val state = UInt(STATE.NBIT.W)
  val pc = UInt(nAddrBit.W)
  val entry = UInt(log2Ceil(nRobEntry).W)
  val cause = UInt((nDataBit - 1).W)
  val info = UInt(nDataBit.W)
}

class LinkBus (p: BackParams) extends Bundle {
  val prev = new GenVBus(p, UInt(log2Ceil(p.nRobEntry).W), UInt(0.W))
  val edge = new GenVBus(p, UInt(log2Ceil(p.nRobEntry).W), UInt(0.W))
}

// ******************************
//             DEBUG
// ******************************
class DbgBus (p: BackParams) extends Bundle {
  val last = UInt(p.nAddrBit.W)
  val x = Vec(32, UInt(p.nDataBit.W))
}

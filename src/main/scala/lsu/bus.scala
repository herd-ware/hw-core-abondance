/*
 * File: bus.scala                                                             *
 * Created Date: 2023-02-26 09:21:29 am                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-26 09:31:15 am                                       *
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
import scala.math._

import herd.common.gen._
import herd.common.mem.mb4s.{OP => LSUUOP, AMO => LSUAMO}
import herd.core.aubrac.common._
import herd.core.abondance.common._
import herd.core.abondance.back.{InfoBus,DependBus,GprCtrlBus,DataExBus}


// ******************************
//              LSU
// ******************************
class LsuCtrlBus extends Bundle {
  val uop = UInt(LSUUOP.NBIT.W)
  val size = UInt(LSUSIZE.NBIT.W)
  val sign = UInt(LSUSIGN.NBIT.W)
  val amo = UInt(LSUAMO.NBIT.W)

  def ldo: Bool = (uop === LSUUOP.R) | (uop === LSUUOP.LR)
  def ld: Bool = (uop === LSUUOP.R) | (uop === LSUUOP.LR) | (uop === LSUUOP.SC) | (uop === LSUUOP.AMO)
  def lr: Bool = (uop === LSUUOP.LR)
  def st: Bool = (uop === LSUUOP.W) | (uop === LSUUOP.SC) | (uop === LSUUOP.AMO)
  def sc: Bool = (uop === LSUUOP.SC)
  def a: Bool = (uop === LSUUOP.AMO) | (uop === LSUUOP.SC)
}

class LsuQueueBus (p: ExUnitParams) extends SpecCtrlBus(p.nSpecBranch) {
  val info = new InfoBus(p.nAddrBit, p.nInstrBit, p.nRobEntry)

  val ctrl = new LsuCtrlBus()
  val dep = new DependBus(p.nRobEntry, p.nGprPhy)
  val data = new DataExBus(p.nDataBit, p.nGprPhy)
  val gpr = new GprCtrlBus(p.nGprPhy)

  val etd = if (p.debug) Some(new EtdBus(p.nHart, p.nAddrBit, p.nInstrBit)) else None
}

class TagBus (nElement: Int) extends Bundle {
  val valid = Bool()
  val tag = UInt(log2Ceil(nElement).W)
}

// ******************************
//             LOAD
// ******************************
class LoadInfoBus(p: LsuParams) extends Bundle {
  val entry = UInt(log2Ceil(p.nRobEntry).W)
  val addr = UInt(p.nAddrBit.W)
  val rdp = UInt(log2Ceil(p.nGprPhy).W)
}

class LoadStateBus extends Bundle {
  val rr = Bool()
  val addr = Bool()
  val mem = Bool()
  val done = Bool()
  val replay = Bool()  
}

class LoadQueueBus (p: LsuParams) extends SpecCtrlBus(p.nSpecBranch) {
  val info = new LoadInfoBus(p)
  val state = new LoadStateBus()
  val ctrl = new LsuCtrlBus()
  val dep = new DependBus(p.nRobEntry, p.nGprPhy)

  val ld_prev = UInt(p.nLoadQueue.W)
  val ld_spec = UInt(p.nLoadQueue.W)
  val st_prev = UInt(p.nStoreQueue.W)
  val st_spec = UInt(p.nStoreQueue.W)
  val fwd = UInt(p.nStoreQueue.W)

  val etd = if (p.debug) Some(new EtdBus(p.nHart, p.nAddrBit, p.nInstrBit)) else None
}

// ******************************
//             STORE
// ******************************
class StoreInfoBus(p: LsuParams) extends Bundle {
  val entry = UInt(log2Ceil(p.nRobEntry).W)
  val addr = UInt(p.nAddrBit.W)
  val rdp = UInt(log2Ceil(p.nGprPhy).W)
}

class StoreStateBus(p: LsuParams) extends Bundle {
  val rr = Bool()
  val addr = Bool()
  val end = Bool()
  val commit = Bool()
}

class StoreQueueBus (p: LsuParams) extends SpecCtrlBus(p.nSpecBranch) {
  val info = new StoreInfoBus(p)
  val state = new StoreStateBus(p)
  val ctrl = new LsuCtrlBus()
  val dep = new DependBus(p.nRobEntry, p.nGprPhy)
  
//  val st = new SpecBus(p.nStoreQueue)
  val tag = UInt(log2Ceil(p.nStoreQueue).W)

  val etd = if (p.debug) Some(new EtdBus(p.nHart, p.nAddrBit, p.nInstrBit)) else None
}

class StoreForwardIO(p: LsuParams) extends Bundle {
//  val tag = Input(UInt(log2Ceil(p.nStoreQueue).W))
  val mask = Input(UInt(p.nStoreQueue.W))
  val ready = Output(Bool())
  val data = Output(UInt(p.nDataBit.W))
}

// ******************************
//            EXECUTE
// ******************************
class LsuExReqCtrlBus (p: LsuParams, nQueue: Int) extends SpecCtrlBus(p.nSpecBranch) {
  val fwd = Bool()
  val tag = UInt(log2Ceil(nQueue).W)
  val size = UInt(LSUSIZE.NBIT.W)
  val rs1p = UInt(log2Ceil(p.nGprPhy).W)
  val rs2p = UInt(log2Ceil(p.nGprPhy).W)
  val imm = UInt(p.nDataBit.W)
}

class LsuExAckCtrlBus (p: LsuParams, nQueue: Int) extends Bundle {
  val fwd = Bool()
  val addr = UInt(p.nAddrBit.W)
  val rs1p = UInt(log2Ceil(p.nGprPhy).W)
  val imm = UInt(p.nDataBit.W)
  val tag = UInt(log2Ceil(nQueue).W)
  val size = UInt(LSUSIZE.NBIT.W)
}

class LsuExIO (p: LsuParams, nQueue: Int) extends Bundle {
 val req = new GenRVIO(p, new LsuExReqCtrlBus(p, nQueue), UInt(0.W))
 val ack = Input(new GenVBus(p, new LsuExAckCtrlBus(p, nQueue), UInt(p.nDataBit.W)))
}

class LsuExCtrlBus (p: LsuParams) extends SpecCtrlBus(p.nSpecBranch) {
  val is_st = Bool()
  val fwd = Bool()
  val tag = UInt(log2Ceil(max(p.nLoadQueue, p.nStoreQueue)).W)
  val size = UInt(LSUSIZE.NBIT.W)
  val rs1p = UInt(log2Ceil(p.nGprPhy).W)
  val imm = UInt(p.nDataBit.W)
}

class LsuExDataBus (p: LsuParams) extends Bundle {
  val s1 = UInt(p.nDataBit.W)
  val s2 = UInt(p.nDataBit.W)
}

// ******************************
//             MEMORY
// ******************************
class MemCtrlBus (p: LsuParams) extends SpecCtrlBus(p.nSpecBranch) {
  val ctrl = new LsuCtrlBus()
  val tag = UInt(log2Ceil(max(p.nLoadQueue, p.nStoreQueue)).W)
  val fwd = UInt(p.nStoreQueue.W)
  val entry = UInt(log2Ceil(p.nRobEntry).W)
  val addr = UInt(p.nAddrBit.W)
  val rdp = UInt(log2Ceil(p.nGprPhy).W)

  val etd = if (p.debug) Some(new EtdBus(p.nHart, p.nAddrBit, p.nInstrBit)) else None
}

class MemQueueCtrlBus (p: LsuParams) extends SpecCtrlBus(p.nSpecBranch) {
  val abort = Bool()
  val id = UInt(log2Ceil(p.nMemOp).W)
  val addr = UInt(p.nAddrBit.W)
  val ctrl = new LsuCtrlBus()
  val tag = UInt(log2Ceil(max(p.nLoadQueue, p.nStoreQueue)).W)
  val entry = UInt(log2Ceil(p.nRobEntry).W)
  val rdp = UInt(log2Ceil(p.nGprPhy).W)

  val etd = if (p.debug) Some(new EtdBus(p.nHart, p.nAddrBit, p.nInstrBit)) else None
}

class MemQueueOutIO (p: LsuParams) extends Bundle {
  val valid = Output(Bool())
  val ctrl = Output(new MemQueueCtrlBus(p))
  val data = Output(UInt(p.nDataBit.W))

  val id = Input(UInt(log2Ceil(p.nMemOp).W))
  val ready = Input(Bool())
}

class MemEndCtrlBus (p: LsuParams) extends SpecCtrlBus(p.nSpecBranch) {
  val ctrl = new LsuCtrlBus()
  val tag = UInt(log2Ceil(p.nLoadQueue).W)
  val entry = UInt(log2Ceil(p.nRobEntry).W)
  val rdp = UInt(log2Ceil(p.nGprPhy).W)

  val etd = if (p.debug) Some(new EtdBus(p.nHart, p.nAddrBit, p.nInstrBit)) else None
}
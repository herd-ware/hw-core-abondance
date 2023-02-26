/*
 * File: bus.scala                                                             *
 * Created Date: 2023-02-26 09:21:29 am                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-26 09:30:37 am                                       *
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
import herd.core.aubrac.common._
import herd.core.abondance.common._
import herd.core.abondance.back.{InfoBus,DependBus,GprCtrlBus,DataExBus,BranchBus}


// ******************************
//             QUEUE
// ******************************
class IntCtrlBus extends Bundle {
  val unit = UInt(INTUNIT.NBIT.W)
  val uop = UInt(INTUOP.NBIT.W)
  val ssign = Vec(3, Bool())
  val ssize = Vec(3, UInt(INTSIZE.NBIT.W))
  val rsize = UInt(INTSIZE.NBIT.W)
  val call = Bool()
  val ret = Bool()
}

class IntQueueBus (p: ExUnitParams) extends SpecCtrlBus(p.nSpecBranch) {
  val info = new InfoBus(p.nAddrBit, p.nInstrBit, p.nRobEntry)

  val ctrl = new IntCtrlBus()
  val dep = new DependBus(p.nRobEntry, p.nGprPhy)
  val data = new DataExBus(p.nDataBit, p.nGprPhy)
  val gpr = new GprCtrlBus(p.nGprPhy)

  val etd = if (p.debug) Some(new EtdBus(p.nHart, p.nAddrBit, p.nInstrBit)) else None
}

// ******************************
//             UNIT
// ******************************
class IntUnitAvBus extends Bundle {
  val alu = Bool()
  val bru = Bool()
  val mul = Bool()
  val div = Bool()
  val csr = Bool()
  val balu = Bool()
  val clmul = Bool()
}

class IntUnitRVIO[TC <: Data, TD <: Data](p: GenParams, tc: TC, td: TD) extends GenRVIO[TC, TD](p, tc, td) {
  val av = Input(new IntUnitAvBus())
}

class IntUnitCtrlBus (p: IntUnitParams) extends SpecCtrlBus(p.nSpecBranch) {
  val info = new InfoBus(p.nAddrBit, p.nInstrBit, p.nRobEntry)

  val ctrl = new IntCtrlBus()
  val next = new BranchBus(p.nAddrBit, p.nSpecBranch, p.nRobEntry)
  val gpr = new GprCtrlBus(p.nGprPhy)

  val etd = if (p.debug) Some(new EtdBus(p.nHart, p.nAddrBit, 0)) else None
}

class IntUnitDataBus (p: IntUnitParams) extends Bundle {
  val s1 = UInt(p.nDataBit.W)
  val s2 = UInt(p.nDataBit.W)
  val s3 = UInt(p.nDataBit.W)
}

// ******************************
//            UNITS
// ******************************
// ------------------------------
//              CSR
// ------------------------------
class CsrDataBus (p: IntUnitParams) extends Bundle {
  val s1 = UInt(p.nDataBit.W)
  val s3 = UInt(p.nDataBit.W)
  val res = UInt(p.nDataBit.W)
}

// ------------------------------
//              MUL
// ------------------------------
class MulStageCtrlBus extends Bundle {
  val cl = Bool()
  val high = Bool()
  val rev = Bool()
  val rsize = UInt(INTSIZE.NBIT.W)
}

class MulCtrlBus (p: IntUnitParams, isStageN: Int) extends SpecCtrlBus(p.nSpecBranch) {
  val info = new InfoBus(p.nAddrBit, p.nInstrBit, p.nRobEntry)
  val stage = new MulStageCtrlBus()
  val gpr = new GprCtrlBus(p.nGprPhy)
  
  val etd = if (p.debug) Some(new EtdBus(p.nHart, p.nAddrBit, 0)) else None
}

class MulDataBus (p: IntUnitParams, isStageN: Int) extends Bundle {
  val sign = Bool()
  val us1 = UInt(p.nDataBit.W)
  val us2 = UInt(p.nDataBit.W)
  val res = if (isStageN > 0) Some(UInt((p.nDataBit * 2).W)) else None
}

// ------------------------------
//              DIV
// ------------------------------
class DivStageCtrlBus extends Bundle {
  val is_rem = Bool()
}

class DivCtrlBus (p: IntUnitParams) extends SpecCtrlBus(p.nSpecBranch) {
  val info = new InfoBus(p.nAddrBit, p.nInstrBit, p.nRobEntry)
  val ctrl = new IntCtrlBus()
  val stage = new DivStageCtrlBus()
  val gpr = new GprCtrlBus(p.nGprPhy)

  val etd = if (p.debug) Some(new EtdBus(p.nHart, p.nAddrBit, 0)) else None
}

class DivDataBus (p: IntUnitParams) extends Bundle {
  val s1_sign = Bool()
  val s2_sign = Bool()
  val us1 = UInt(p.nDataBit.W)
  val us2 = UInt(p.nDataBit.W)
  val uquo = UInt(p.nDataBit.W)
  val urem = UInt(p.nDataBit.W)
}
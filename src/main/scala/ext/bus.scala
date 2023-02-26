/*
 * File: bus.scala                                                             *
 * Created Date: 2023-02-26 09:21:29 am                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-26 09:29:58 am                                       *
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
import scala.math._

import herd.common.gen._
import herd.core.aubrac.common._
import herd.core.abondance.common._
import herd.core.abondance.back.{InfoBus,DependBus,GprCtrlBus,DataExBus,StatBus}


// ******************************
//         EXTERNAL UNIT
// ******************************
class ExtReqQueueBus[UC <: Data](p: ExUnitParams, uc: UC) extends SpecCtrlBus(p.nSpecBranch) {
  val info = new InfoBus(p.nAddrBit, p.nInstrBit, p.nRobEntry)

  val ctrl = uc
  val dep = new DependBus(p.nRobEntry, p.nGprPhy)
  val data = new DataExBus(p.nDataBit, p.nGprPhy)
  val gpr = new GprCtrlBus(p.nGprPhy)

  val etd = if (p.debug) Some(new EtdBus(p.nHart, p.nAddrBit, p.nInstrBit)) else None
}

class LinkBus (p: ExUnitParams) extends Bundle {
  val av = Bool()
  val entry = UInt(log2Ceil(p.nRobEntry).W)
}

class ExtCtrlBus [UC <: Data](p: ExUnitParams, uc: UC) extends SpecCtrlBus(p.nSpecBranch) {
  val info = new InfoBus(p.nAddrBit, p.nInstrBit, p.nRobEntry)

  val ctrl = uc
  val link = new LinkBus(p)
  val gpr = new GprCtrlBus(p.nGprPhy)

  val etd = if (p.debug) Some(new EtdBus(p.nHart, p.nAddrBit, p.nInstrBit)) else None
}

class ExtDataBus (p: ExUnitParams) extends Bundle {
  val s1 = UInt(p.nDataBit.W)
  val s2 = UInt(p.nDataBit.W)
  val s3 = UInt(p.nDataBit.W)
}

class ExtAckQueueBus (p: ExUnitParams) extends Bundle {
  val info = new InfoBus(p.nAddrBit, p.nInstrBit, p.nRobEntry)
  val link = new LinkBus(p)
  val gpr = new GprCtrlBus(p.nGprPhy)

  val etd = if (p.debug) Some(new EtdBus(p.nHart, p.nAddrBit, p.nInstrBit)) else None
}

// ******************************
//             PORT
// ******************************
class ExtReqCtrlBus[UC <: Data](p: ExUnitParams, uc: UC) extends Bundle {
  val info = new InfoBus(p.nAddrBit, p.nInstrBit, p.nRobEntry)
  val ctrl = uc
  val gpr = new GprCtrlBus(p.nGprPhy)

  val etd = if (p.debug) Some(new EtdBus(p.nHart, p.nAddrBit, p.nInstrBit)) else None
}

class ExtAckCtrlBus[UC <: Data](p: ExUnitParams, uc: UC) extends Bundle {
  val trap = Output(new TrapBus(p.nAddrBit, p.nDataBit))
  val stat = Output(new StatBus())

  val etd = if (p.debug) Some(new EtdBus(p.nHart, p.nAddrBit, p.nInstrBit)) else None
}

class ExtPortIO[UC <: Data](p: ExUnitParams, uc: UC, nBus: Int) extends Bundle {
  val req = Vec(nBus, new GenRVIO(p, new ExtReqCtrlBus(p, uc), new ExtDataBus(p)))
  val ack = Vec(nBus, Flipped(new GenRVIO(p, new ExtAckCtrlBus(p, uc), UInt(p.nDataBit.W))))
}

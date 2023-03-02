/*
 * File: ext.scala                                                             *
 * Created Date: 2023-02-26 09:21:29 am                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-03-02 12:13:59 pm                                       *
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
import herd.common.field._
import herd.core.abondance.common._
import herd.core.abondance.back.{BranchBus, BypassBus, CommitBus, GprReadIO, GprWriteIO, RobPcIO, EndIO}
import herd.core.abondance.back.{BackConfigBase}


class Ext[UC <: Data](p: ExUnitParams, uc: UC, nBus: Int, nReqQueue: Int, nAckQueue: Int) extends Module {  
  val io = IO(new Bundle {
    val b_unit = if (p.useField) Some(new RsrcIO(p.nHart, p.nField, 1)) else None

    val i_flush = Input(Bool())

    val b_in = Vec(p.nBackPort, Flipped(new GenRVIO(p, new ExtReqQueueBus(p, uc), UInt(0.W))))

    val i_run = Input(Bool())
    val i_br_up = Input(UInt(p.nSpecBranch.W))
    val i_br_new = Input(new BranchBus(p.nAddrBit, p.nSpecBranch, p.nRobEntry))
    val i_busy = Input(Vec(p.nBypass, new BypassBus(p.nDataBit, p.nGprPhy)))
    val i_commit = Input(Vec(p.nCommit, new CommitBus(p.nRobEntry, 1, p.nGprPhy)))

    val b_read = Vec(nBus * 2, Flipped(new GprReadIO(p.nDataBit, p.nGprPhy)))
    val b_pc = Vec(nBus, Flipped(new RobPcIO(p.nAddrBit, p.nRobEntry)))

    val b_port = new ExtPortIO(p, uc, nBus)

    val o_byp = Output(Vec(nBus, new BypassBus(p.nDataBit, p.nGprPhy)))
    val b_write = Flipped(Vec(nBus, new GprWriteIO(p.nDataBit, p.nGprPhy)))
    val b_end = Vec(nBus, new EndIO(p.debug, p.nAddrBit, p.nDataBit, p.nRobEntry))
  })

  val m_req = Module(new ExtReqQueue(p, uc, nReqQueue, nBus))
  val m_rr = Module(new ExtRrStage(p, uc, nBus))
  val m_ack = Module(new ExtAckQueue(p, nAckQueue, nBus))

  // ******************************
  //           REQ QUEUE
  // ******************************
  if (p.useField) m_req.io.b_unit.get <> io.b_unit.get
  m_req.io.i_flush := io.i_flush

  m_req.io.b_in <> io.b_in
  m_req.io.i_br_up := io.i_br_up
  m_req.io.i_br_new := io.i_br_new
  m_req.io.i_busy := io.i_busy
  m_req.io.i_commit := io.i_commit

  // ******************************
  //              RR
  // ******************************
  if (p.useField) m_rr.io.b_unit.get <> io.b_unit.get
  m_rr.io.i_flush := io.i_flush

  m_rr.io.b_in <> m_req.io.b_out
  m_rr.io.i_br_up := io.i_br_up
  m_rr.io.i_br_new := io.i_br_new
  m_rr.io.i_commit := io.i_commit

  m_rr.io.b_read <> io.b_read
  m_rr.io.b_pc <> io.b_pc

  // ******************************
  //           REQ PORT
  // ******************************
  for (b <- 0 until nBus) {
    m_rr.io.b_out(b).ready := io.b_port.req(b).ready & m_ack.io.b_in(b).ready

    io.b_port.req(b).valid := m_rr.io.b_out(b).valid & m_ack.io.b_in(b).ready
    io.b_port.req(b).ctrl.get.info := m_rr.io.b_out(b).ctrl.get.info
    io.b_port.req(b).ctrl.get.ctrl := m_rr.io.b_out(b).ctrl.get.ctrl
    io.b_port.req(b).ctrl.get.gpr := m_rr.io.b_out(b).ctrl.get.gpr
    io.b_port.req(b).data.get := m_rr.io.b_out(b).data.get

    m_ack.io.b_in(b).valid := m_rr.io.b_out(b).valid & io.b_port.req(b).ready
    m_ack.io.b_in(b).ctrl.get.info := m_rr.io.b_out(b).ctrl.get.info
    m_ack.io.b_in(b).ctrl.get.link := m_rr.io.b_out(b).ctrl.get.link
    m_ack.io.b_in(b).ctrl.get.gpr := m_rr.io.b_out(b).ctrl.get.gpr
  }

  // ******************************
  //             ACK
  // ******************************
  if (p.useField) m_ack.io.b_unit.get <> io.b_unit.get
  m_ack.io.i_flush := io.i_flush

  m_ack.io.i_run := io.i_run
  m_ack.io.i_commit := io.i_commit

  // ******************************
  //             I/Os
  // ******************************
  for (b <- 0 until nBus) {
    io.b_port.ack(b).ready := m_ack.io.b_out(b).valid & (~m_ack.io.b_out(b).ctrl.get.gpr.en | io.b_write(b).ready) & io.b_end(b).ready
    m_ack.io.b_out(b).ready := io.b_port.ack(b).valid & (~m_ack.io.b_out(b).ctrl.get.gpr.en | io.b_write(b).ready) & io.b_end(b).ready

    io.o_byp(b).valid := m_ack.io.b_out(b).valid & io.b_port.ack(b).valid & m_ack.io.b_out(b).ctrl.get.gpr.en
    io.o_byp(b).done := true.B
    io.o_byp(b).rdp := m_ack.io.b_out(b).ctrl.get.gpr.rdp
    io.o_byp(b).data := io.b_port.ack(b).data.get

    io.b_write(b).valid := m_ack.io.b_out(b).valid & io.b_port.ack(b).valid & m_ack.io.b_out(b).ctrl.get.gpr.en
    io.b_write(b).rdp := m_ack.io.b_out(b).ctrl.get.gpr.rdp
    io.b_write(b).data := io.b_port.ack(b).data.get

    io.b_end(b).valid := m_ack.io.b_out(b).valid & io.b_port.ack(b).valid & (~m_ack.io.b_out(b).ctrl.get.gpr.en | io.b_write(b).ready)
    io.b_end(b).entry := m_ack.io.b_out(b).ctrl.get.info.entry
    io.b_end(b).replay := false.B
    io.b_end(b).trap := io.b_port.ack(b).ctrl.get.trap
    io.b_end(b).stat := io.b_port.ack(b).ctrl.get.stat
  }

  // ******************************
  //             FIELD
  // ******************************
  if (p.useField) {
    io.b_unit.get.free := m_req.io.b_unit.get.free & m_rr.io.b_unit.get.free & m_ack.io.b_unit.get.free
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
    for (b <- 0 until nBus) {
      io.b_port.req(b).ctrl.get.etd.get := m_rr.io.b_out(b).ctrl.get.etd.get
      m_ack.io.b_in(b).ctrl.get.etd.get := m_rr.io.b_out(b).ctrl.get.etd.get
      io.b_end(b).etd.get := io.b_port.ack(b).ctrl.get.etd.get
    }
  }
}

object Ext extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new Ext(BackConfigBase, UInt(4.W), 1, 4, 4), args)
}
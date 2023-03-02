/*
 * File: rr.scala                                                              *
 * Created Date: 2023-02-26 09:21:29 am                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-03-02 12:14:14 pm                                       *
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
import herd.core.aubrac.back.{OP}
import herd.core.abondance.back.{BranchBus, CommitBus, GprReadIO, RobPcIO}
import herd.core.abondance.back.{BackConfigBase}
 

class ExtRrStage[UC <: Data](p: ExUnitParams, uc: UC, nBus: Int) extends Module {  
  val io = IO(new Bundle {
    val b_unit = if (p.useField) Some(new RsrcIO(p.nHart, p.nField, 1)) else None

    val i_flush = Input(Bool())

    val b_in = Vec(nBus, Flipped(new GenRVIO(p, new ExtReqQueueBus(p, uc), UInt(0.W))))

    val i_br_up = Input(UInt(p.nSpecBranch.W))
    val i_br_new = Input(new BranchBus(p.nDataBit, p.nSpecBranch, p.nRobEntry))
    val i_commit = Input(Vec(p.nCommit, new CommitBus(p.nRobEntry, 1, p.nGprPhy)))

    val b_read = Vec(nBus * 2, Flipped(new GprReadIO(p.nDataBit, p.nGprPhy)))
    val b_pc = Vec(nBus, Flipped(new RobPcIO(p.nAddrBit, p.nRobEntry)))

    val b_out = Vec(nBus, new GenRVIO(p, new ExtCtrlBus(p, uc), new ExtDataBus(p)))
  })

  val m_out = Seq.fill(nBus){Module(new ExtReg(p, uc, true))}

  val w_out = Wire(Vec(nBus, new GenVBus(p, new ExtCtrlBus(p, uc), new ExtDataBus(p))))
  val w_lock = Wire(Vec(nBus, Bool()))
  val w_wait = Wire(Vec(nBus, Bool()))

  // ******************************
  //          UNIT STATUS
  // ******************************
  val w_unit_flush = Wire(Bool())

  if (p.useField) {
    w_unit_flush := io.b_unit.get.flush
  } else {
    w_unit_flush := false.B
  } 

  // ******************************
  //         FORMAT SOURCES
  // ******************************  
  val w_wait_read = Wire(Vec(2 * nBus, Bool()))
  val w_s1 = Wire(Vec(nBus, UInt(p.nDataBit.W)))
  val w_s2 = Wire(Vec(nBus, UInt(p.nDataBit.W)))
  val w_s3 = Wire(Vec(nBus, UInt(p.nDataBit.W)))

  for (b <- 0 until nBus) {
    // ------------------------------
    //            READ GPR
    // ------------------------------
    io.b_read(2 * b).valid := io.b_in(b).valid & (io.b_in(b).ctrl.get.data.s1type === OP.XREG)
    io.b_read(2 * b).rsp := io.b_in(b).ctrl.get.dep.rs1p
    w_wait_read(2 * b) := io.b_in(b).valid & ~io.i_flush & (io.b_in(b).ctrl.get.data.s1type === OP.XREG) & ~io.b_read(2 * b).ready

    io.b_read(2 * b + 1).valid := io.b_in(b).valid & (io.b_in(b).ctrl.get.data.s2type === OP.XREG)
    io.b_read(2 * b + 1).rsp := io.b_in(b).ctrl.get.dep.rs2p
    w_wait_read(2 * b + 1) := io.b_in(b).valid & ~io.i_flush & ((io.b_in(b).ctrl.get.data.s2type === OP.XREG) | (io.b_in(b).ctrl.get.data.s3type === OP.XREG)) & ~io.b_read(2 * b + 1).ready

    // ------------------------------
    //            READ PC
    // ------------------------------
    io.b_pc(b).entry := io.b_in(b).ctrl.get.info.entry

    // ------------------------------
    //              S1
    // ------------------------------
    w_s1(b) := DontCare

    switch (io.b_in(b).ctrl.get.data.s1type) {
      is (OP.XREG)  {w_s1(b) := io.b_read(2 * b).data}
      is (OP.IMM1)  {w_s1(b) := io.b_in(b).ctrl.get.data.imm1}
      is (OP.IMM2)  {w_s1(b) := io.b_in(b).ctrl.get.data.imm2}
      is (OP.PC)    {w_s1(b) := io.b_pc(b).pc}
    }

    // ------------------------------
    //              S2
    // ------------------------------
    w_s2(b) := DontCare

    switch (io.b_in(b).ctrl.get.data.s2type) {
      is (OP.XREG)  {w_s2(b) := io.b_read(2 * b + 1).data}
      is (OP.IMM1)  {w_s2(b) := io.b_in(b).ctrl.get.data.imm1}
      is (OP.IMM2)  {w_s2(b) := io.b_in(b).ctrl.get.data.imm2}
      is (OP.PC)    {w_s2(b) := io.b_pc(b).pc}
    }

    // ------------------------------
    //              S3
    // ------------------------------
    w_s3(b) := DontCare

    switch (io.b_in(b).ctrl.get.data.s3type) {
      is (OP.XREG)  {w_s3(b) := io.b_read(2 * b + 1).data}
      is (OP.IMM1)  {w_s3(b) := io.b_in(b).ctrl.get.data.imm1}
      is (OP.IMM2)  {w_s3(b) := io.b_in(b).ctrl.get.data.imm2}
      is (OP.PC)    {w_s3(b) := io.b_pc(b).pc}
    }
  }

  // ******************************
  //            OUTPUTS
  // ******************************  
  for (b <- 0 until nBus) {
    w_wait(b) := w_lock(b) | w_wait_read(2 * b) | w_wait_read(2 * b + 1)
  }

  for (b0 <- 0 until nBus) {
    for (b1 <- (b0 + 1) until nBus) {
      when (io.b_in(b0).valid & (w_lock(b0) | w_wait_read(2 * b0) | w_wait_read(2 * b0 + 1))) {
        w_wait(b1) := true.B
      }
    }
  }

  for (b <- 0 until nBus) {
    io.b_in(b).ready := ~w_wait(b)

    w_lock(b) := ~m_out(b).io.b_in.ready & ~(io.i_br_new.valid & w_out(b).ctrl.get.br.mask(io.i_br_new.tag))
    w_out(b) := m_out(b).io.o_val

    m_out(b).io.i_flush := io.i_flush | w_unit_flush | (io.i_br_new.valid & w_out(b).ctrl.get.br.mask(io.i_br_new.tag))
    m_out(b).io.i_br_up := io.i_br_up  
    m_out(b).io.i_commit := io.i_commit  

    m_out(b).io.b_in.valid := io.b_in(b).valid & ~w_wait(b) & ~w_unit_flush
    m_out(b).io.b_in.ctrl.get.info := io.b_in(b).ctrl.get.info
    m_out(b).io.b_in.ctrl.get.info.pc := io.b_pc(b).pc
    m_out(b).io.b_in.ctrl.get.br := io.b_in(b).ctrl.get.br
    m_out(b).io.b_in.ctrl.get.link.av := io.b_in(b).ctrl.get.dep.av(0)
    m_out(b).io.b_in.ctrl.get.link.entry := io.b_in(b).ctrl.get.dep.entry
    m_out(b).io.b_in.ctrl.get.ctrl := io.b_in(b).ctrl.get.ctrl
    m_out(b).io.b_in.ctrl.get.gpr := io.b_in(b).ctrl.get.gpr

    m_out(b).io.b_in.data.get.s1 := w_s1(b)
    m_out(b).io.b_in.data.get.s2 := w_s2(b)
    m_out(b).io.b_in.data.get.s3 := w_s3(b)

    io.b_out(b) <> m_out(b).io.b_out
  }

  // ******************************
  //             FIELD
  // ******************************
  if (p.useField) {
    val w_unit_free = Wire(Vec(nBus, Bool()))

    for (b <- 0 until nBus) {
      w_unit_free(b) := ~m_out(b).io.o_val.valid
    }

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
    for (b <- 0 until nBus) {
      m_out(b).io.b_in.ctrl.get.etd.get := io.b_in(b).ctrl.get.etd.get
      dontTouch(m_out(b).io.b_out.ctrl.get.etd.get)
    }
  }
}

object ExtRrStage extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new ExtRrStage(BackConfigBase, UInt(4.W), 2), args)
}
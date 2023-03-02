/*
 * File: rr.scala                                                              *
 * Created Date: 2023-02-26 09:21:29 am                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-03-02 12:15:34 pm                                       *
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
import herd.common.field._
import herd.common.isa.riscv._
import herd.core.aubrac.back.{OP, SlctSize}
import herd.core.abondance.common._
import herd.core.abondance.back.{BranchBus,BypassBus,GprReadIO,RobPcIO}


class RrStage(p: IntUnitParams) extends Module {
  import herd.core.abondance.int.INTUNIT._
  
  val io = IO(new Bundle {
    val b_unit = if (p.useField) Some(new RsrcIO(p.nHart, p.nField, 1)) else None

    val i_flush = Input(Bool())

    val b_in = Flipped(new IntUnitRVIO(p, new IntQueueBus(p), UInt(0.W)))

    val i_br_up = Input(UInt(p.nSpecBranch.W))
    val i_br_new = Input(new BranchBus(p.nAddrBit, p.nSpecBranch, p.nRobEntry))
    
    val b_read = Flipped(Vec(2, new GprReadIO(p.nDataBit, p.nGprPhy)))
    val b_pc = Flipped(new RobPcIO(p.nAddrBit, p.nRobEntry))

    val o_byp = Output(new BypassBus(p.nDataBit, p.nGprPhy))

    val b_out = new IntUnitRVIO(p, new IntUnitCtrlBus(p), new IntUnitDataBus(p))
  })

  val m_out = Module(new SpecReg(p, new IntUnitCtrlBus(p), new IntUnitDataBus(p), true, p.nSpecBranch))

  val w_lock = Wire(Bool())
  val w_out = Wire(new GenVBus(p, new IntUnitCtrlBus(p), new IntUnitDataBus(p)))

  // ******************************
  //          UNIT STATUS
  // ******************************
  val w_unit_flush = Wire(Bool())

  if (p.useField) {
    w_unit_flush := io.b_unit.get.flush | io.i_flush | (io.i_br_new.valid & io.b_in.ctrl.get.br.mask(io.i_br_new.tag))
  } else {
    w_unit_flush := io.i_flush | (io.i_br_new.valid & io.b_in.ctrl.get.br.mask(io.i_br_new.tag))
  }  

  // ******************************
  //         FORMAT SOURCES
  // ******************************
  // ------------------------------
  //            READ GPR
  // ------------------------------
  val w_wait_read = Wire(Vec(2, Bool()))

  io.b_read(0).valid := io.b_in.valid & ~w_unit_flush & (io.b_in.ctrl.get.data.s1type === OP.XREG)
  io.b_read(0).rsp := io.b_in.ctrl.get.dep.rs1p
  w_wait_read(0) := io.b_in.valid & ~w_unit_flush & (io.b_in.ctrl.get.data.s1type === OP.XREG) & ~io.b_read(0).ready

  io.b_read(1).valid := io.b_in.valid & ~w_unit_flush & (io.b_in.ctrl.get.data.s2type === OP.XREG)
  io.b_read(1).rsp := io.b_in.ctrl.get.dep.rs2p
  w_wait_read(1) := io.b_in.valid & ~w_unit_flush & ((io.b_in.ctrl.get.data.s2type === OP.XREG) | (io.b_in.ctrl.get.data.s3type === OP.XREG)) & ~io.b_read(1).ready

  // ------------------------------
  //            READ PC
  // ------------------------------
  val w_wait_next = Wire(Bool())

  io.b_pc.entry := io.b_in.ctrl.get.info.entry
  if (p.useFastJal & p.useBru) {
    w_wait_next := (io.b_in.ctrl.get.ctrl.unit === INTUNIT.BRU) & (io.b_in.ctrl.get.ctrl.uop === INTUOP.JAL) & ~io.b_pc.nready
  } else {
    w_wait_next := false.B
  } 

  // ------------------------------
  //              S1
  // ------------------------------
  val w_s1_src = Wire(UInt(p.nDataBit.W))
  val w_s1 = Wire(UInt(p.nDataBit.W))

  w_s1_src := DontCare
  switch (io.b_in.ctrl.get.data.s1type) {
    is (OP.XREG)  {w_s1_src := io.b_read(0).data}
    is (OP.IMM1)  {w_s1_src := io.b_in.ctrl.get.data.imm1}
    is (OP.IMM2)  {w_s1_src := io.b_in.ctrl.get.data.imm2}
    is (OP.PC)    {w_s1_src := io.b_pc.pc}
  }

  val m_s1_size = Module(new SlctSize(p.nDataBit))
  
  m_s1_size.io.i_val := w_s1_src
  m_s1_size.io.i_size := io.b_in.ctrl.get.ctrl.ssize(0)
  m_s1_size.io.i_sign := io.b_in.ctrl.get.ctrl.ssign(0)
  w_s1 := m_s1_size.io.o_val

  // ------------------------------
  //              S2
  // ------------------------------
  val w_s2_src = Wire(UInt(p.nDataBit.W))
  val w_s2 = Wire(UInt(p.nDataBit.W))

  w_s2_src := DontCare
  switch (io.b_in.ctrl.get.data.s2type) {
    is (OP.XREG)  {w_s2_src := io.b_read(1).data}
    is (OP.IMM1)  {w_s2_src := io.b_in.ctrl.get.data.imm1}
    is (OP.IMM2)  {w_s2_src := io.b_in.ctrl.get.data.imm2}
    is (OP.PC)    {w_s2_src := io.b_pc.pc}
  }

  val m_s2_size = Module(new SlctSize(p.nDataBit))
  
  m_s2_size.io.i_val := w_s2_src
  m_s2_size.io.i_size := io.b_in.ctrl.get.ctrl.ssize(1)
  m_s2_size.io.i_sign := io.b_in.ctrl.get.ctrl.ssign(1)
  w_s2 := m_s2_size.io.o_val

  // ------------------------------
  //              S3
  // ------------------------------
  val w_s3_src = Wire(UInt(p.nDataBit.W))
  val w_s3 = Wire(UInt(p.nDataBit.W))

  w_s3_src := DontCare
  switch (io.b_in.ctrl.get.data.s3type) {
    is (OP.XREG)  {w_s3_src := io.b_read(1).data}
    is (OP.IMM1)  {w_s3_src := io.b_in.ctrl.get.data.imm1}
    is (OP.IMM2)  {w_s3_src := io.b_in.ctrl.get.data.imm2}
    is (OP.PC)    {w_s3_src := io.b_pc.pc}
  }

  val m_s3_size = Module(new SlctSize(p.nDataBit))
  
  m_s3_size.io.i_val := w_s3_src
  m_s3_size.io.i_size := io.b_in.ctrl.get.ctrl.ssize(2)
  m_s3_size.io.i_sign := io.b_in.ctrl.get.ctrl.ssign(2)
  w_s3 := m_s3_size.io.o_val
  
  // ******************************
  //            BYPASS
  // ******************************
  io.o_byp.valid := io.b_in.valid & ~w_unit_flush & (io.b_in.ctrl.get.ctrl.unit === ALU) & (io.b_in.ctrl.get.gpr.rdp =/= REG.X0.U)
  io.o_byp.done := false.B
  io.o_byp.rdp := io.b_in.ctrl.get.gpr.rdp
  io.o_byp.data := DontCare

  // ******************************
  //          INPUT READY
  // ******************************
  io.b_in.ready := w_unit_flush | ~(w_lock | w_wait_read.asUInt.orR | w_wait_next)
  
  io.b_in.av.alu := io.b_out.av.alu
  io.b_in.av.bru := io.b_out.av.bru
  io.b_in.av.mul := io.b_out.av.mul
  io.b_in.av.div := io.b_out.av.div & ~(w_out.valid & (w_out.ctrl.get.ctrl.unit === DIV))
  io.b_in.av.csr := io.b_out.av.csr
  io.b_in.av.balu := io.b_out.av.balu
  io.b_in.av.clmul := io.b_out.av.clmul

  // ******************************
  //            OUTPUTS
  // ******************************  
  w_lock := ~m_out.io.b_in.ready & ~(io.i_br_new.valid & w_out.ctrl.get.br.mask(io.i_br_new.tag))
  w_out := m_out.io.o_val

  m_out.io.i_flush := io.i_flush | (io.i_br_new.valid & w_out.ctrl.get.br.mask(io.i_br_new.tag))
  m_out.io.i_br_up := io.i_br_up  

  m_out.io.b_in.valid := io.b_in.valid & ~w_unit_flush & ~w_wait_read.asUInt.orR & ~w_wait_next
  m_out.io.b_in.ctrl.get.info := io.b_in.ctrl.get.info
  m_out.io.b_in.ctrl.get.info.pc := io.b_pc.pc
  m_out.io.b_in.ctrl.get.br := io.b_in.ctrl.get.br
  m_out.io.b_in.ctrl.get.ctrl := io.b_in.ctrl.get.ctrl
  m_out.io.b_in.ctrl.get.next := DontCare
  m_out.io.b_in.ctrl.get.next.valid := io.b_pc.nready
  m_out.io.b_in.ctrl.get.next.addr := io.b_pc.npc
  m_out.io.b_in.ctrl.get.gpr := io.b_in.ctrl.get.gpr

  m_out.io.b_in.data.get.s1 := w_s1
  m_out.io.b_in.data.get.s2 := w_s2
  m_out.io.b_in.data.get.s3 := w_s3

  io.b_out.valid := m_out.io.b_out.valid
  io.b_out.ctrl.get := m_out.io.b_out.ctrl.get
  io.b_out.data.get := m_out.io.b_out.data.get
  m_out.io.b_out.ready := io.b_out.ready

  // ******************************
  //             FIELD
  // ******************************
  if (p.useField) {
    io.b_unit.get.free := ~m_out.io.o_val.valid    
  }  

  // ******************************
  //             DEBUG
  // ******************************
  if (p.debug) {
    // ------------------------------
    //            SIGNALS
    // ------------------------------
    dontTouch(io.b_out.ctrl.get.info)

    // ------------------------------
    //         DATA FOOTPRINT
    // ------------------------------

    // ------------------------------
    //       EXECUTION TRACKER
    // ------------------------------  
    m_out.io.b_in.ctrl.get.etd.get := io.b_in.ctrl.get.etd.get
  }
}

object RrStage extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new RrStage(IntUnitConfigBase), args)
}

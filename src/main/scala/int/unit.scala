/*
 * File: unit.scala                                                            *
 * Created Date: 2023-02-26 09:21:29 am                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-26 09:31:06 am                                       *
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
import herd.common.dome._
import herd.common.isa.base._
import herd.common.mem.cbo._
import herd.core.aubrac.back.csr.{CsrIO}
import herd.core.aubrac.nlp.{BranchInfoBus}
import herd.core.abondance.common._
import herd.core.abondance.back.{BypassBus,BranchBus,GprReadIO,GprWriteIO,RobPcIO,EndIO}


class IntUnit (p: IntUnitParams) extends Module {
  import herd.core.abondance.int.INTUNIT._

  val io = IO(new Bundle {
    val b_unit = if (p.useDome) Some(new RsrcIO(p.nHart, p.nDome, 1)) else None

    val i_flush = Input(Bool())

    val b_in = Flipped(new IntUnitRVIO(p, new IntQueueBus(p), UInt(0.W)))

    val i_br_up = Input(UInt(p.nSpecBranch.W))
    val i_br_new = Input(new BranchBus(p.nAddrBit, p.nSpecBranch, p.nRobEntry))

    val b_read = Flipped(Vec(2, new GprReadIO(p.nDataBit, p.nGprPhy)))
    val b_pc = Flipped(new RobPcIO(p.nAddrBit, p.nRobEntry))
    val b_csr = if (p.useCsr) Some( Flipped(new CsrIO(p.nDataBit))) else None
    val b_write = Flipped(new GprWriteIO(p.nDataBit, p.nGprPhy))

    val o_flush = if (p.useBru) Some(Output(Bool())) else None
    val b_cbo = if (p.useCbo) Some(new CboIO(1, p.useDome, p.nDome, p.nAddrBit)) else None

    val o_byp = Output(Vec(p.nBypass, new BypassBus(p.nDataBit, p.nGprPhy)))
    val o_br_up = if (p.useBru) Some(Output(UInt(p.nSpecBranch.W))) else None
    val o_br_new = if (p.useBru) Some(Output(new BranchBus(p.nAddrBit, p.nSpecBranch, p.nRobEntry))) else None
    val o_br_info = if (p.useBru) Some(Output(new BranchInfoBus(p.nAddrBit))) else None
    val b_end = new EndIO(p.debug, p.nAddrBit, p.nDataBit, p.nRobEntry)
  })

  val w_lock = Wire(Bool())
  val w_byp = Wire(new BypassBus(p.nDataBit, p.nGprPhy))

  w_lock := false.B

  // ******************************
  //            RR STAGE
  // ******************************
  val m_rr = Module(new RrStage(p))

  m_rr.io.i_flush := io.i_flush
  m_rr.io.b_in <> io.b_in

  m_rr.io.i_br_up := io.i_br_up
  m_rr.io.i_br_new := io.i_br_new

  m_rr.io.b_read <> io.b_read
  m_rr.io.b_pc <> io.b_pc

  m_rr.io.b_out.ready := ~w_lock
  m_rr.io.b_out.av.alu := false.B
  m_rr.io.b_out.av.bru := false.B
  m_rr.io.b_out.av.mul := false.B
  m_rr.io.b_out.av.div := false.B
  m_rr.io.b_out.av.csr := false.B
  m_rr.io.b_out.av.balu := false.B
  m_rr.io.b_out.av.clmul := false.B

  // ******************************
  //            EX STAGE
  // ******************************
  // ------------------------------
  //              ALU
  // ------------------------------
  val m_alu = if (p.useAlu) Some(Module(new Alu(p))) else None

  if (p.useAlu) {
    m_rr.io.b_out.av.alu := true.B
    if (p.useExtB) m_rr.io.b_out.av.balu := true.B

    m_alu.get.io.i_flush := io.i_flush
    m_alu.get.io.b_in.valid := false.B
    m_alu.get.io.b_in.ctrl.get := m_rr.io.b_out.ctrl.get
    m_alu.get.io.b_in.data.get := m_rr.io.b_out.data.get

    m_alu.get.io.i_br_up := io.i_br_up
    m_alu.get.io.i_br_new := io.i_br_new
  
    when (m_rr.io.b_out.ctrl.get.ctrl.unit === ALU) {
      m_alu.get.io.b_in.valid := m_rr.io.b_out.valid
      w_lock := ~m_alu.get.io.b_in.ready
    }

    if (p.useExtB) {
      when (m_rr.io.b_out.ctrl.get.ctrl.unit === BALU) {
        m_alu.get.io.b_in.valid := m_rr.io.b_out.valid
        w_lock := ~m_alu.get.io.b_in.ready
      }
    }
  }

  // ------------------------------
  //             BRU
  // ------------------------------
  val m_bru = if (p.useBru) Some(Module(new Bru(p))) else None

  if (p.useBru) {
    m_rr.io.b_out.av.bru := true.B

    m_bru.get.io.i_flush := io.i_flush
    if (p.useDome) m_bru.get.io.i_dome.get := io.b_unit.get.dome
    m_bru.get.io.b_in.valid := false.B
    m_bru.get.io.b_in.ctrl.get := m_rr.io.b_out.ctrl.get
    m_bru.get.io.b_in.data.get := m_rr.io.b_out.data.get

    m_bru.get.io.i_br_up := io.i_br_up
    m_bru.get.io.i_br_new := io.i_br_new
  
    when (m_rr.io.b_out.ctrl.get.ctrl.unit === BRU) {
      m_bru.get.io.b_in.valid := m_rr.io.b_out.valid
      w_lock := ~m_bru.get.io.b_in.ready
    }

    if (p.useCbo) io.b_cbo.get <> m_bru.get.io.b_cbo.get    
    io.o_br_up.get := m_bru.get.io.o_br_up
    io.o_br_new.get := m_bru.get.io.o_br_new
    io.o_br_info.get := m_bru.get.io.o_br_info
    io.o_flush.get := m_bru.get.io.o_flush
  }

  // ------------------------------
  //              MUL
  // ------------------------------
  val m_mul = if (p.useMul) Some(Module(new Mul(p))) else None  

  if (p.useMul) {
    m_rr.io.b_out.av.mul := true.B
    if (p.useExtB) m_rr.io.b_out.av.clmul := true.B

    m_mul.get.io.i_flush := io.i_flush
    m_mul.get.io.b_in.valid := false.B
    m_mul.get.io.b_in.ctrl.get := m_rr.io.b_out.ctrl.get
    m_mul.get.io.b_in.data.get := m_rr.io.b_out.data.get

    m_mul.get.io.i_br_up := io.i_br_up
    m_mul.get.io.i_br_new := io.i_br_new

    when (m_rr.io.b_out.ctrl.get.ctrl.unit === MUL) {
      m_mul.get.io.b_in.valid := m_rr.io.b_out.valid
      w_lock := ~m_mul.get.io.b_in.ready
    }

    if (p.useExtB) {
      when (m_rr.io.b_out.ctrl.get.ctrl.unit === CLMUL) {
        m_mul.get.io.b_in.valid := m_rr.io.b_out.valid
        w_lock := ~m_mul.get.io.b_in.ready
      }
    }
  }

  // ------------------------------
  //              DIV
  // ------------------------------
  val m_div = if (p.useDiv) Some(Module(new Div(p))) else None  

  if (p.useDiv) {
    m_rr.io.b_out.av.div := m_div.get.io.b_in.ready

    m_div.get.io.i_flush := io.i_flush
    m_div.get.io.b_in.valid := false.B
    m_div.get.io.b_in.ctrl.get := m_rr.io.b_out.ctrl.get
    m_div.get.io.b_in.data.get := m_rr.io.b_out.data.get

    m_div.get.io.i_br_up := io.i_br_up
    m_div.get.io.i_br_new := io.i_br_new

    when (m_rr.io.b_out.ctrl.get.ctrl.unit === DIV) {
      m_div.get.io.b_in.valid := m_rr.io.b_out.valid
      w_lock := ~m_div.get.io.b_in.ready
    }
  }

  // ------------------------------
  //              CSR
  // ------------------------------
  val m_csr = if (p.useCsr) Some(Module(new Csr(p))) else None  

  if (p.useCsr) {
    m_rr.io.b_out.av.csr := true.B

    m_csr.get.io.b_in.valid := false.B
    m_csr.get.io.b_in.ctrl.get := m_rr.io.b_out.ctrl.get
    m_csr.get.io.b_in.data.get := m_rr.io.b_out.data.get

    m_csr.get.io.b_csr <> io.b_csr.get

    when (m_rr.io.b_out.ctrl.get.ctrl.unit === INTUNIT.CSR) {
      m_csr.get.io.b_in.valid := m_rr.io.b_out.valid
      w_lock := ~m_csr.get.io.b_in.ready
    }
  }

  // ******************************
  //         BYPASS & WRITE
  // ******************************
  // ------------------------------
  //            DEFAULT
  // ------------------------------
  w_byp := DontCare
  w_byp.valid := false.B

  io.b_write := DontCare
  io.b_write.valid := false.B

  if (p.useAlu) m_alu.get.io.b_write.ready := false.B
  if (p.useBru) m_bru.get.io.b_write.ready := false.B
  if (p.useMul) m_mul.get.io.b_write.ready := false.B
  if (p.useDiv) m_div.get.io.b_write.ready := false.B
  if (p.useCsr) m_csr.get.io.b_write.ready := false.B

  // ------------------------------
  //              ALU
  // ------------------------------
  if (p.useAlu) {
    when (m_alu.get.io.b_write.valid) {
      io.b_write <> m_alu.get.io.b_write

      w_byp.valid := (m_alu.get.io.b_write.rdp =/= REG.X0.U)
      w_byp.rdp := m_alu.get.io.b_write.rdp
      w_byp.data := m_alu.get.io.b_write.data

      if (p.useBru) m_bru.get.io.b_write.ready := false.B
      if (p.useMul) m_mul.get.io.b_write.ready := false.B
      if (p.useDiv) m_div.get.io.b_write.ready := false.B
      if (p.useCsr) m_csr.get.io.b_write.ready := false.B
    }
  }

  // ------------------------------
  //              BRU
  // ------------------------------
  if (p.useBru) {
    when (m_bru.get.io.b_write.valid) {
      io.b_write <> m_bru.get.io.b_write

      w_byp.valid := (m_bru.get.io.b_write.rdp =/= REG.X0.U)
      w_byp.rdp := m_bru.get.io.b_write.rdp
      w_byp.data := m_bru.get.io.b_write.data

      if (p.useAlu) m_alu.get.io.b_write.ready := false.B
      if (p.useMul) m_mul.get.io.b_write.ready := false.B
      if (p.useDiv) m_div.get.io.b_write.ready := false.B
      if (p.useCsr) m_csr.get.io.b_write.ready := false.B
    }
  }

  // ------------------------------
  //              MUL
  // ------------------------------
  if (p.useMul) {
    when (m_mul.get.io.b_write.valid) {
      io.b_write <> m_mul.get.io.b_write

      w_byp.valid := (m_mul.get.io.b_write.rdp =/= REG.X0.U)
      w_byp.rdp := m_mul.get.io.b_write.rdp
      w_byp.data := m_mul.get.io.b_write.data

      if (p.useAlu) m_alu.get.io.b_write.ready := false.B
      if (p.useBru) m_bru.get.io.b_write.ready := false.B
      if (p.useDiv) m_div.get.io.b_write.ready := false.B
      if (p.useCsr) m_csr.get.io.b_write.ready := false.B
    }
  }

  // ------------------------------
  //              DIV
  // ------------------------------
  if (p.useDiv) {
    when (m_div.get.io.b_write.valid) {
      io.b_write <> m_div.get.io.b_write

      w_byp.valid := (m_div.get.io.b_write.rdp =/= REG.X0.U)
      w_byp.rdp := m_div.get.io.b_write.rdp
      w_byp.data := m_div.get.io.b_write.data

      if (p.useAlu) m_alu.get.io.b_write.ready := false.B
      if (p.useBru) m_bru.get.io.b_write.ready := false.B
      if (p.useMul) m_mul.get.io.b_write.ready := false.B
      if (p.useCsr) m_csr.get.io.b_write.ready := false.B
    }
  }

  // ------------------------------
  //              CSR
  // ------------------------------
  if (p.useCsr) {
    when (m_csr.get.io.b_write.valid) {
      io.b_write <> m_csr.get.io.b_write

      w_byp.valid := (m_csr.get.io.b_write.rdp =/= REG.X0.U)
      w_byp.rdp := m_csr.get.io.b_write.rdp
      w_byp.data := m_csr.get.io.b_write.data

      if (p.useAlu) m_alu.get.io.b_write.ready := false.B
      if (p.useBru) m_bru.get.io.b_write.ready := false.B
      if (p.useMul) m_mul.get.io.b_write.ready := false.B
      if (p.useDiv) m_div.get.io.b_write.ready := false.B
    }
  }

  // ------------------------------
  //            OTHERS
  // ------------------------------
  io.o_byp(0) := w_byp
  if (p.useAlu && p.useAluBypass) {
    io.o_byp(1) := m_rr.io.o_byp
    io.o_byp(2) := m_alu.get.io.o_byp
  }

  // ******************************
  //              END
  // ******************************
  // ------------------------------
  //            DEFAULT
  // ------------------------------
  io.b_end := DontCare
  io.b_end.valid := false.B
  
  if (p.useAlu) m_alu.get.io.b_end.ready := false.B
  if (p.useBru) m_bru.get.io.b_end.ready := false.B
  if (p.useMul) m_mul.get.io.b_end.ready := false.B
  if (p.useDiv) m_div.get.io.b_end.ready := false.B
  if (p.useCsr) m_csr.get.io.b_end.ready := false.B

  // ------------------------------
  //              ALU
  // ------------------------------
  if (p.useAlu) {
    when (m_alu.get.io.b_end.valid) {
      io.b_end <> m_alu.get.io.b_end
      
      if (p.useBru) m_bru.get.io.b_end.ready := false.B
      if (p.useMul) m_mul.get.io.b_end.ready := false.B
      if (p.useDiv) m_div.get.io.b_end.ready := false.B
      if (p.useCsr) m_csr.get.io.b_end.ready := false.B
    }
  }

  // ------------------------------
  //              BRU
  // ------------------------------
  if (p.useBru) {
    when (m_bru.get.io.b_end.valid) {
      io.b_end <> m_bru.get.io.b_end

      if (p.useAlu) m_alu.get.io.b_end.ready := false.B
      if (p.useMul) m_mul.get.io.b_end.ready := false.B
      if (p.useDiv) m_div.get.io.b_end.ready := false.B
      if (p.useCsr) m_csr.get.io.b_end.ready := false.B
    }
  }

  // ------------------------------
  //              MUL
  // ------------------------------
  if (p.useMul) {
    when (m_mul.get.io.b_end.valid) {
      io.b_end <> m_mul.get.io.b_end

      if (p.useAlu) m_alu.get.io.b_end.ready := false.B
      if (p.useBru) m_bru.get.io.b_end.ready := false.B
      if (p.useDiv) m_div.get.io.b_end.ready := false.B
      if (p.useCsr) m_csr.get.io.b_end.ready := false.B
    }
  }

  // ------------------------------
  //              DIV
  // ------------------------------
  if (p.useDiv) {
    when (m_div.get.io.b_end.valid) {
      io.b_end <> m_div.get.io.b_end

      if (p.useAlu) m_alu.get.io.b_end.ready := false.B
      if (p.useBru) m_bru.get.io.b_end.ready := false.B
      if (p.useMul) m_mul.get.io.b_end.ready := false.B
      if (p.useCsr) m_csr.get.io.b_end.ready := false.B
    }
  }

  // ------------------------------
  //              CSR
  // ------------------------------
  if (p.useCsr) {
    when (m_csr.get.io.b_end.valid) {
      io.b_end <> m_csr.get.io.b_end

      if (p.useAlu) m_alu.get.io.b_end.ready := false.B
      if (p.useBru) m_bru.get.io.b_end.ready := false.B
      if (p.useMul) m_mul.get.io.b_end.ready := false.B
      if (p.useDiv) m_div.get.io.b_end.ready := false.B
    }
  }

  // ******************************
  //             DOME
  // ******************************
  if (p.useDome) {
    val w_unit_free = Wire(Vec(4, Bool()))

    m_rr.io.b_unit.get <> io.b_unit.get

    if (p.useAlu) w_unit_free(0) := m_alu.get.io.o_free else w_unit_free(0) := true.B
    if (p.useBru) w_unit_free(1) := m_bru.get.io.o_free else w_unit_free(1) := true.B
    if (p.useMul) w_unit_free(2) := m_mul.get.io.o_free else w_unit_free(2) := true.B
    if (p.useDiv) w_unit_free(3) := m_div.get.io.o_free else w_unit_free(3) := true.B

    io.b_unit.get.free := m_rr.io.b_unit.get.free & w_unit_free.asUInt.andR
  }  

  // ******************************
  //             DEBUG
  // ******************************
  if (p.debug) {
    // ------------------------------
    //            SIGNALS
    // ------------------------------
    dontTouch(io.b_end)

    // ------------------------------
    //         DATA FOOTPRINT
    // ------------------------------

    // ------------------------------
    //       EXECUTION TRACKER
    // ------------------------------  
  }
}

object IntUnit extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new IntUnit(IntUnitConfigBase), args)
}

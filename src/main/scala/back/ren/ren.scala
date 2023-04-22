/*
 * File: ren.scala                                                             *
 * Created Date: 2023-02-26 09:21:29 am                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-04-03 01:12:10 pm                                       *
 * Modified By: Mathieu Escouteloup                                            *
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
import herd.common.field._
import herd.common.isa.riscv.{CSR}
import herd.common.mem.mb4s.{OP => LSUUOP}
import herd.core.aubrac.back.{SlctImm}
import herd.core.abondance.common._
import herd.core.abondance.int.{INTUNIT, INTUOP}


class RenStage(p: BackParams) extends Module {
  val io = IO(new Bundle {
    val b_back = if (p.useField) Some(Vec(p.nBackPort, new RsrcIO(p.nHart, p.nField, 1))) else None

    val i_flush = Input(Bool())

    val b_in = Vec(p.nBackPort, Flipped(new GenRVIO(p, new RenCtrlBus(p), UInt(0.W))))

    val i_br_up = Input(UInt(p.nSpecBranch.W))
    val b_map = Flipped(Vec(p.nBackPort, Vec(2, new MapIO(p))))
    val b_remap = Flipped(Vec(p.nBackPort, new RemapIO(p)))
    val b_rob = Flipped(Vec(p.nBackPort, new RobWriteIO(p)))

    val b_out = Vec(p.nBackPort, new GenRVIO(p, new IssCtrlBus(p), UInt(0.W)))
  })

  val m_imm1 = Seq.fill(p.nBackPort){Module(new SlctImm(p.nInstrBit, p.nDataBit))}
  val m_imm2 = Seq.fill(p.nBackPort){Module(new SlctImm(p.nInstrBit, p.nDataBit))}
  
  val w_bp_lock = Wire(Vec(p.nBackPort, Bool()))
  val w_bp_wait = Wire(Vec(p.nBackPort, Bool()))

  val w_wait_remap = Wire(Vec(p.nBackPort, Bool()))
  val w_wait_rob = Wire(Vec(p.nBackPort, Bool()))

  // ******************************
  //        BACK PORT STATUS
  // ******************************
  val w_back_valid = Wire(Vec(p.nBackPort, Bool()))
  val w_back_flush = Wire(Vec(p.nBackPort, Bool()))

  for (bp <- 0 until p.nBackPort) {
    if (p.useField) {
      w_back_valid(bp) := io.b_back.get(bp).valid & ~io.b_back.get(bp).flush
      w_back_flush(bp) := io.b_back.get(bp).flush
    } else {
      w_back_valid(bp) := true.B
      w_back_flush(bp) := false.B
    }
  }

  // ******************************
  //           IMMEDIATE
  // ******************************
  for (bp <- 0 until p.nBackPort) {
    m_imm1(bp).io.i_instr := io.b_in(bp).ctrl.get.info.instr
    m_imm1(bp).io.i_imm_type := io.b_in(bp).ctrl.get.data.imm1type

    m_imm2(bp).io.i_instr := io.b_in(bp).ctrl.get.info.instr
    m_imm2(bp).io.i_imm_type := io.b_in(bp).ctrl.get.data.imm2type
  }

  // ******************************
  //           RENAMING
  // ******************************
  for (bp <- 0 until p.nBackPort) {
    io.b_map(bp)(0).rsl := io.b_in(bp).ctrl.get.data.rs1l
    io.b_map(bp)(1).rsl := io.b_in(bp).ctrl.get.data.rs2l

    io.b_remap(bp).valid := io.b_in(bp).valid & w_back_valid(bp) & ~w_back_flush(bp) & ~io.i_flush & ~w_wait_rob.asUInt.orR & ~w_bp_lock.asUInt.orR
    io.b_remap(bp).alloc := io.b_in(bp).ctrl.get.ex.gpr.en
    io.b_remap(bp).br := io.b_in(bp).ctrl.get.br
    io.b_remap(bp).rdl := io.b_in(bp).ctrl.get.data.rdl
    w_wait_remap(bp) := io.b_in(bp).ctrl.get.ex.gpr.en & ~io.b_remap(bp).ready
  }
  
  // ******************************
  //             ROB
  // ******************************
  for (bp <- 0 until p.nBackPort) {
    io.b_rob(bp).valid := io.b_in(bp).valid & w_back_valid(bp) & ~w_back_flush(bp) & ~io.i_flush & ~w_wait_remap.asUInt.orR  & ~w_bp_lock.asUInt.orR
    io.b_rob(bp).data.valid := true.B
    io.b_rob(bp).data.pc := io.b_in(bp).ctrl.get.info.pc
    io.b_rob(bp).data.busy := true.B
    io.b_rob(bp).data.exc := io.b_in(bp).ctrl.get.trap.valid
    io.b_rob(bp).data.replay := false.B
    io.b_rob(bp).data.br_mask := io.b_in(bp).ctrl.get.br.mask & io.i_br_up 
    io.b_rob(bp).data.rdl := Mux(io.b_in(bp).ctrl.get.ex.gpr.en, io.b_in(bp).ctrl.get.data.rdl, 0.U)
    io.b_rob(bp).data.rdp := Mux(io.b_in(bp).ctrl.get.ex.gpr.en, io.b_remap(bp).rdp, 0.U)

    io.b_rob(bp).data.hpc.instret := false.B
    io.b_rob(bp).data.hpc.alu := (io.b_in(bp).ctrl.get.ex.ex_type === EXTYPE.INT) & (io.b_in(bp).ctrl.get.ex.int.unit === INTUNIT.ALU)
    io.b_rob(bp).data.hpc.ld := (io.b_in(bp).ctrl.get.ex.ex_type === EXTYPE.LSU) & (io.b_in(bp).ctrl.get.ex.lsu.uop === LSUUOP.R)
    io.b_rob(bp).data.hpc.st := (io.b_in(bp).ctrl.get.ex.ex_type === EXTYPE.LSU) & (io.b_in(bp).ctrl.get.ex.lsu.uop === LSUUOP.W)
    io.b_rob(bp).data.hpc.bru := (io.b_in(bp).ctrl.get.ex.ex_type === EXTYPE.INT) & (io.b_in(bp).ctrl.get.ex.int.unit === INTUNIT.BRU)
    io.b_rob(bp).data.hpc.mispred := false.B
    io.b_rob(bp).data.hpc.rdcycle := (io.b_in(bp).ctrl.get.ex.ex_type === EXTYPE.INT) & (io.b_in(bp).ctrl.get.ex.int.unit === INTUNIT.CSR) & (m_imm2(bp).io.o_val(11, 0) === CSR.CYCLE.U) 
    io.b_rob(bp).data.hpc.jal := (io.b_in(bp).ctrl.get.ex.ex_type === EXTYPE.INT) & (io.b_in(bp).ctrl.get.ex.int.unit === INTUNIT.BRU) & (io.b_in(bp).ctrl.get.ex.int.uop === INTUOP.JAL)
    io.b_rob(bp).data.hpc.jalr := (io.b_in(bp).ctrl.get.ex.ex_type === EXTYPE.INT) & (io.b_in(bp).ctrl.get.ex.int.unit === INTUNIT.BRU) & (io.b_in(bp).ctrl.get.ex.int.uop === INTUOP.JALR)
    io.b_rob(bp).data.hpc.call := (io.b_in(bp).ctrl.get.ex.ex_type === EXTYPE.INT) & (io.b_in(bp).ctrl.get.ex.int.unit === INTUNIT.BRU) & (io.b_in(bp).ctrl.get.ex.int.uop === INTUOP.JALR) & io.b_in(bp).ctrl.get.ex.int.call
    io.b_rob(bp).data.hpc.ret := (io.b_in(bp).ctrl.get.ex.ex_type === EXTYPE.INT) & (io.b_in(bp).ctrl.get.ex.int.unit === INTUNIT.BRU) & (io.b_in(bp).ctrl.get.ex.int.uop === INTUOP.JALR) & io.b_in(bp).ctrl.get.ex.int.ret
    io.b_rob(bp).data.hpc.cflush := (io.b_in(bp).ctrl.get.ex.ex_type === EXTYPE.INT) & (io.b_in(bp).ctrl.get.ex.int.unit === INTUNIT.BRU) & (io.b_in(bp).ctrl.get.ex.int.uop === INTUOP.FLUSH)

    io.b_rob(bp).trap := io.b_in(bp).ctrl.get.trap
    w_wait_rob(bp) := ~io.b_rob(bp).ready
  }
  // ******************************
  //            OUTPUT
  // ******************************
  val m_out = Seq.fill(p.nBackPort){Module(new SpecReg(p, new IssCtrlBus(p), UInt(0.W), true, p.nSpecBranch))}

  // Wait
  for (bp0 <- 0 until p.nBackPort) {
    w_bp_wait(bp0) := w_wait_remap.asUInt.orR | w_wait_rob.asUInt.orR
    for (bp1 <- 0 until p.nBackPort) {
      if (bp0 != bp1) {
        when (w_bp_lock(bp1)) {
          w_bp_wait(bp0) := true.B
        }
      }
    }
  }

  // Input
  for (bp <- 0 until p.nBackPort) {
    io.b_in(bp).ready := w_back_flush(bp) | io.i_flush | (~w_bp_wait(bp) & ~w_bp_lock(bp))
  }

  // Write
  for (bp <- 0 until p.nBackPort) {
    w_bp_lock(bp) := ~m_out(bp).io.b_in.ready
    m_out(bp).io.i_flush := w_back_flush(bp) | io.i_flush
    m_out(bp).io.i_br_up := io.i_br_up

    m_out(bp).io.b_in.valid := io.b_in(bp).valid & w_back_valid(bp) & ~w_back_flush(bp) & ~io.i_flush & ~w_bp_wait(bp)

    m_out(bp).io.b_in.ctrl.get.info :=io.b_in(bp).ctrl.get.info
    m_out(bp).io.b_in.ctrl.get.info.entry := io.b_rob(bp).entry

    m_out(bp).io.b_in.ctrl.get.br := io.b_in(bp).ctrl.get.br

    m_out(bp).io.b_in.ctrl.get.ex := io.b_in(bp).ctrl.get.ex
    m_out(bp).io.b_in.ctrl.get.ex.gpr.rdp := io.b_remap(bp).rdp

    m_out(bp).io.b_in.ctrl.get.data.s1type := io.b_in(bp).ctrl.get.data.s1type
    m_out(bp).io.b_in.ctrl.get.data.s2type := io.b_in(bp).ctrl.get.data.s2type
    m_out(bp).io.b_in.ctrl.get.data.s3type := io.b_in(bp).ctrl.get.data.s3type
    m_out(bp).io.b_in.ctrl.get.data.imm1 := m_imm1(bp).io.o_val
    m_out(bp).io.b_in.ctrl.get.data.imm2 := m_imm2(bp).io.o_val

    m_out(bp).io.b_in.ctrl.get.dep.av(0) := true.B
    m_out(bp).io.b_in.ctrl.get.dep.av(1) := true.B
    m_out(bp).io.b_in.ctrl.get.dep.av(2) := true.B
    m_out(bp).io.b_in.ctrl.get.dep.entry := DontCare
    m_out(bp).io.b_in.ctrl.get.dep.rs1p := io.b_map(bp)(0).rsp
    m_out(bp).io.b_in.ctrl.get.dep.rs2p := io.b_map(bp)(1).rsp

    m_out(bp).io.b_in.ctrl.get.ext := io.b_in(bp).ctrl.get.ext
  }

  // Read
  for (bp <- 0 until p.nBackPort) {
    m_out(bp).io.b_out <> io.b_out(bp)
  }

  // ******************************
  //             FIELD
  // ******************************
  if (p.useField) {
    for (bp <- 0 until p.nBackPort) { 
      io.b_back.get(bp).free := ~m_out(bp).io.o_val.valid    
    }
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
    for (bp <- 0 until p.nBackPort) {
      m_out(bp).io.b_in.ctrl.get.etd.get := io.b_in(bp).ctrl.get.etd.get
      io.b_rob(bp).data.etd.get := io.b_in(bp).ctrl.get.etd.get
    }
  }
}

object RenStage extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new RenStage(BackConfigBase), args)
}

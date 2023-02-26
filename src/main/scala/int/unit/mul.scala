/*
 * File: mul.scala                                                             *
 * Created Date: 2023-02-26 09:21:29 am                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-26 09:30:33 am                                       *
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
import scala.math._

import herd.common.gen._
import herd.common.isa.base._
import herd.core.abondance.common._
import herd.core.abondance.back.{BranchBus, BypassBus, GprWriteIO, EndIO}


class MulStage (p: IntUnitParams, isStageN: Int) extends Module {
  val io = IO(new Bundle {
    val i_flush = Input(Bool())
    val o_free = Output(Bool())

    val b_in = Flipped(new GenRVIO(p, new MulCtrlBus(p, isStageN), new MulDataBus(p, isStageN)))

    val i_br_up = Input(UInt(p.nSpecBranch.W))
    val i_br_new = Input(new BranchBus(p.nAddrBit, p.nSpecBranch, p.nRobEntry))

    val b_out = new GenRVIO(p, new MulCtrlBus(p, isStageN + 1), new MulDataBus(p, isStageN + 1))
  })

  val w_flush = Wire(Bool())
  val w_lock = Wire(Bool())

  w_flush := io.i_flush | (io.i_br_new.valid & io.b_in.ctrl.get.br.mask(io.i_br_new.tag))

  // ******************************
  //              ADD
  // ******************************
  val w_add = Wire(Vec(pow(2, p.nMulAddLvl + 1).toInt - 1, UInt((p.nDataBit * 2).W)))

  // Format inputs
  for (na <- 0 until pow(2, p.nMulAddLvl).toInt) {
    val w_bit = (isStageN.U << p.nMulAddLvl.U) + na.U

    w_add(na) := Mux(io.b_in.data.get.us2(w_bit), (io.b_in.data.get.us1 << w_bit), 0.U)
  }

  var addoff: Int = 0

  // Add levels
  for (al <- p.nMulAddLvl to 1 by -1) {
    for (na <- 0 until pow(2, al - 1).toInt) {      
      w_add(addoff + pow(2, al).toInt + na) := w_add(addoff + na * 2) + w_add(addoff + na * 2 + 1)
    }

    addoff = addoff + pow(2, al).toInt
  }

  // ******************************
  //         XOR (CARRY LESS)
  // ******************************
  val w_xor = Wire(Vec(pow(2, p.nMulAddLvl + 1).toInt - 1, UInt((p.nDataBit * 2).W)))

  // Format inputs
  for (na <- 0 until pow(2, p.nMulAddLvl).toInt) {
    val w_bit = (isStageN.U << p.nMulAddLvl.U) + na.U

    w_xor(na) := Mux(io.b_in.data.get.us2(w_bit), (io.b_in.data.get.us1 << w_bit), 0.U)
  }

  var xoroff: Int = 0

  // Xor levels
  for (al <- p.nMulAddLvl to 1 by -1) {
    for (na <- 0 until pow(2, al - 1).toInt) {      
      w_xor(xoroff + pow(2, al).toInt + na) := w_xor(xoroff + na * 2) ^ w_xor(xoroff + na * 2 + 1)
    }

    xoroff = xoroff + pow(2, al).toInt
  }

  // ******************************
  //             RESULT
  // ******************************
  val w_tmp = Wire(UInt((p.nDataBit * 2).W))
  val w_size = Wire(UInt((p.nDataBit * 2).W))
  val w_res = Wire(UInt((p.nDataBit * 2).W))

  if (p.useExtB) {
    when (io.b_in.ctrl.get.stage.cl) {
      if (isStageN == 0) {
        w_tmp := w_xor(pow(2, p.nMulAddLvl + 1).toInt - 2)
      } else {
        w_tmp := w_xor(pow(2, p.nMulAddLvl + 1).toInt - 2) ^ io.b_in.data.get.res.get
      }
    }.otherwise {
      if (isStageN == 0) {
        w_tmp := w_add(pow(2, p.nMulAddLvl + 1).toInt - 2)
      } else {
        w_tmp := w_add(pow(2, p.nMulAddLvl + 1).toInt - 2) + io.b_in.data.get.res.get
      }
    }
  } else {
    if (isStageN == 0) {
      w_tmp := w_add(pow(2, p.nMulAddLvl + 1).toInt - 2)
    } else {
      w_tmp := w_add(pow(2, p.nMulAddLvl + 1).toInt - 2) + io.b_in.data.get.res.get
    }
  }

  if (isStageN == (p.nMulStage - 1)) {
    w_size := w_tmp

    if (p.nDataBit >= 64) {
      switch (io.b_in.ctrl.get.stage.rsize) {
        is (INTSIZE.W)  {w_size := w_tmp(31, 0)}
      }
    }

    if (p.useExtB) {
      when (io.b_in.ctrl.get.stage.rev) {
        w_res := ~w_size
      }.elsewhen (io.b_in.data.get.sign) {
        w_res := ~w_size + 1.U
      }.otherwise {
        w_res := w_size
      }
    } else {
      when (io.b_in.data.get.sign) {
        w_res := ~w_size + 1.U
      }.otherwise {
        w_res := w_size
      }
    }
  } else {
    w_size := DontCare
    w_res := w_tmp
  }

  // ******************************
  //           REGISTERS
  // ******************************
  val m_out = Module(new SpecReg(p, new MulCtrlBus(p, isStageN + 1), new MulDataBus(p, isStageN + 1), true, p.nSpecBranch))

  val w_out = Wire(new GenVBus(p, new MulCtrlBus(p, isStageN + 1), new MulDataBus(p, isStageN + 1)))

  io.b_in.ready := ~w_lock | io.i_flush

  // Output register
  w_lock := ~m_out.io.b_in.ready & ~(io.i_br_new.valid & w_out.ctrl.get.br.mask(io.i_br_new.tag))
  w_out := m_out.io.o_val

  m_out.io.i_flush := io.i_flush | (io.i_br_new.valid & w_out.ctrl.get.br.mask(io.i_br_new.tag))
  m_out.io.i_br_up := io.i_br_up 

  m_out.io.b_in.valid := io.b_in.valid & ~w_flush
  m_out.io.b_in.ctrl.get := io.b_in.ctrl.get

  m_out.io.b_in.data.get.sign := io.b_in.data.get.sign
  m_out.io.b_in.data.get.us1 := io.b_in.data.get.us1
  m_out.io.b_in.data.get.us2 := io.b_in.data.get.us2
  m_out.io.b_in.data.get.res.get := w_res

  m_out.io.b_out <> io.b_out  

  // ******************************
  //             FREE
  // ******************************
  io.o_free := ~w_out.valid

  // ******************************
  //             DEBUG
  // ******************************
  if (p.debug) {
    // ------------------------------
    //            SIGNALS
    // ------------------------------
    dontTouch(w_out.ctrl.get.info)

    // ------------------------------
    //         DATA FOOTPRINT
    // ------------------------------

    // ------------------------------
    //       EXECUTION TRACKER
    // ------------------------------  
    m_out.io.b_in.ctrl.get.etd.get := io.b_in.ctrl.get.etd.get
  }
}

class Mul (p: IntUnitParams) extends Module {
  import herd.core.abondance.int.INTUOP._
  
  val io = IO(new Bundle {
    val i_flush = Input(Bool())
    val o_free = Output(Bool())

    val b_in = Flipped(new GenRVIO(p, new IntUnitCtrlBus(p), new IntUnitDataBus(p)))

    val i_br_up = Input(UInt(p.nSpecBranch.W))
    val i_br_new = Input(new BranchBus(p.nAddrBit, p.nSpecBranch, p.nRobEntry))

    val b_write = Flipped(new GprWriteIO(p.nDataBit, p.nGprPhy))
    val b_end = new EndIO(p.debug, p.nAddrBit, p.nDataBit, p.nRobEntry)
  })

  val m_stage = for (s <- 0 until p.nMulStage) yield {
    val m_stage = Module(new MulStage(p, s))
    m_stage
  } 

  // ******************************
  //             INPUTS
  // ******************************
  // ------------------------------
  //             DECODE
  // ------------------------------
  val w_stage = Wire(new MulStageCtrlBus())

  val w_us1_sign = Wire(Bool())
  val w_us2_sign = Wire(Bool())

  w_stage.cl := false.B
  w_stage.high := false.B
  w_stage.rev := false.B
  w_stage.rsize := io.b_in.ctrl.get.ctrl.rsize
  w_us1_sign := false.B
  w_us2_sign := false.B

  switch (io.b_in.ctrl.get.ctrl.uop) {
    is (MUL) {
      w_stage.high := false.B
      w_us1_sign := false.B
      w_us2_sign := false.B
    }
    is (MULH) {
      w_stage.high := true.B
      w_us1_sign := io.b_in.ctrl.get.ctrl.ssign(0) & io.b_in.data.get.s1(p.nDataBit - 1)
      w_us2_sign := io.b_in.ctrl.get.ctrl.ssign(1) & io.b_in.data.get.s2(p.nDataBit - 1)
    }
  }

  if (p.useExtB) {
    switch (io.b_in.ctrl.get.ctrl.uop) {
      is (CLMUL) {
        w_stage.cl := true.B
        w_stage.high := false.B
      }
      is (CLMULH) {
        w_stage.cl := true.B
        w_stage.high := true.B
      }
      is (CLMULR) {
        w_stage.cl := true.B
        w_stage.high := false.B
        w_stage.rev := false.B
      }
    }
  }

  // ------------------------------
  //             FORMAT
  // ------------------------------
  val w_sign = Wire(Bool())
  val w_us1 = Wire(UInt(p.nDataBit.W))
  val w_us2 = Wire(UInt(p.nDataBit.W))

  w_sign := w_us1_sign ^ w_us2_sign
  w_us1 := Mux(w_us1_sign, ~(io.b_in.data.get.s1 - 1.U), io.b_in.data.get.s1)
  w_us2 := Mux(w_us2_sign, ~(io.b_in.data.get.s2 - 1.U), io.b_in.data.get.s2)

  if (p.useExtB) {
    when (w_stage.rev) {
      w_us1 := ~io.b_in.data.get.s1
      w_us2 := ~io.b_in.data.get.s2
    }
  }

  // ******************************
  //           FIRST STAGE
  // ******************************
  io.b_in.ready := m_stage(0).io.b_in.ready

  m_stage(0).io.i_flush := io.i_flush
  m_stage(0).io.i_br_up := io.i_br_up
  m_stage(0).io.i_br_new := io.i_br_new

  m_stage(0).io.b_in.valid := io.b_in.valid
  m_stage(0).io.b_in.ctrl.get.info := io.b_in.ctrl.get.info
  m_stage(0).io.b_in.ctrl.get.br := io.b_in.ctrl.get.br
  m_stage(0).io.b_in.ctrl.get.stage := w_stage
  m_stage(0).io.b_in.ctrl.get.gpr := io.b_in.ctrl.get.gpr

  m_stage(0).io.b_in.data.get.sign := w_sign
  m_stage(0).io.b_in.data.get.us1 := w_us1
  m_stage(0).io.b_in.data.get.us2 := w_us2

  // ******************************
  //          OTHER STAGES
  // ******************************
  for (s <- 1 until p.nMulStage) {
    m_stage(s).io.i_flush := io.i_flush
    m_stage(s).io.i_br_up := io.i_br_up
    m_stage(s).io.i_br_new := io.i_br_new
    m_stage(s).io.b_in <> m_stage(s - 1).io.b_out
  }

  // ******************************
  //          FINAL RESULT
  // ******************************
  val w_fin = Wire(UInt(p.nDataBit.W))

  when (m_stage(p.nMulStage - 1).io.b_out.ctrl.get.stage.high) {
    w_fin := m_stage(p.nMulStage - 1).io.b_out.data.get.res.get(p.nDataBit * 2 - 1, p.nDataBit)
  }.otherwise {
    w_fin := m_stage(p.nMulStage - 1).io.b_out.data.get.res.get(p.nDataBit - 1, 0)
  }

  if (p.useExtB) {
    when (m_stage(p.nMulStage - 1).io.b_out.ctrl.get.stage.cl & m_stage(p.nMulStage - 1).io.b_out.ctrl.get.stage.rev) {
      w_fin := m_stage(p.nMulStage - 1).io.b_out.data.get.res.get(p.nDataBit * 2 - 2, p.nDataBit - 1)
    }
  }

  // ******************************
  //              I/O
  // ******************************
  val r_write = RegInit(true.B)

  m_stage(p.nMulStage - 1).io.b_out.ready := io.b_end.ready & (io.b_write.ready | ~r_write)

  when (r_write) {
    r_write := ~(m_stage(p.nMulStage - 1).io.b_out.valid & io.b_write.ready & ~io.b_end.ready)
  }.otherwise {
    r_write := io.b_end.ready
  }

  io.b_write.valid := m_stage(p.nMulStage - 1).io.b_out.valid & r_write
  io.b_write.rdp := m_stage(p.nMulStage - 1).io.b_out.ctrl.get.gpr.rdp
  io.b_write.data := w_fin

  io.b_end.valid := m_stage(p.nMulStage - 1).io.b_out.valid & (io.b_write.ready | ~r_write)
  io.b_end.entry := m_stage(p.nMulStage - 1).io.b_out.ctrl.get.info.entry
  io.b_end.replay := false.B
  io.b_end.trap := DontCare
  io.b_end.trap.valid := false.B
  
  io.b_end.stat := 0.U.asTypeOf(io.b_end.stat)

  // ******************************
  //              FREE
  // ******************************
  val w_free = Wire(Vec(p.nMulStage, Bool()))

  for (s <- 0 until p.nMulStage) {
    w_free(s) := m_stage(s).io.o_free
  }

  io.o_free := w_free.asUInt.andR

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
    m_stage(0).io.b_in.ctrl.get.etd.get := io.b_in.ctrl.get.etd.get
    dontTouch(m_stage(p.nMulStage - 1).io.b_out.ctrl.get.etd.get)
    io.b_end.etd.get := DontCare
  }
}

object MulStage extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new MulStage(IntUnitConfigBase, 2), args)
}

object Mul extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new Mul(IntUnitConfigBase), args)
}

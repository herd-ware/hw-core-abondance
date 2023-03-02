/*
 * File: div.scala                                                             *
 * Created Date: 2023-02-26 09:21:29 am                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-03-02 07:12:57 pm                                       *
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
import herd.core.abondance.common._
import herd.core.abondance.back.{GprWriteIO, EndIO}
import herd.core.abondance.back.{BranchBus, InfoBus}
import herd.core.abondance.int.INTUOP._


class Div (p: IntUnitParams) extends Module {
  val io = IO(new Bundle {
    val i_flush = Input(Bool())
    val o_free = Output(Bool())

    val b_in = Flipped(new GenRVIO(p, new IntUnitCtrlBus(p), new IntUnitDataBus(p)))

    val i_br_up = Input(UInt(p.nSpecBranch.W))
    val i_br_new = Input(new BranchBus(p.nDataBit, p.nSpecBranch, p.nRobEntry))

    val b_write = Flipped(new GprWriteIO(p.nDataBit, p.nGprPhy))
    val b_end = new EndIO(p.debug, p.nAddrBit, p.nDataBit, p.nRobEntry)
  })

  val m_bus = Module(new SpecReg(p, new DivCtrlBus(p), new DivDataBus(p), true, p.nSpecBranch))
  val r_round = RegInit(0.U(log2Ceil(p.nDataBit + 1).W))
  val r_write = RegInit(true.B)

  val w_bus = Wire(new GenBus(p, new DivCtrlBus(p), new DivDataBus(p)))
  val w_start = Wire(Bool())
  val w_run = Wire(Bool())
  val w_end = Wire(Bool())
  val w_write = Wire(Bool())

  val w_res_spec = Wire(Bool())
  val w_src_zero = Wire(Bool())
  val w_div_zero = Wire(Bool())
  val w_div_over = Wire(Bool())
  val w_div_start = Wire(UInt(log2Ceil(p.nDataBit + 1).W))

  val w_flush = Wire(Bool())

  w_flush := io.i_flush | io.i_br_new.valid & ((w_start & io.b_in.ctrl.get.br.mask(io.i_br_new.tag)) | (~w_start & w_bus.ctrl.get.br.mask(io.i_br_new.tag)))

  // ******************************
  //              FSM
  // ******************************  
  w_start := (r_round === 0.U)
  w_run := (r_round > 0.U) & (r_round < (p.nDataBit - 1).U)
  w_end := (r_round === (p.nDataBit - 1).U)
  w_write := (r_round === p.nDataBit.U)

  when (w_flush) {
    r_round := 0.U
  }.elsewhen (w_start) {
    when (io.b_in.valid) {
      when (w_res_spec) {
        r_round := p.nDataBit.U
      }.otherwise {
        r_round := 1.U
      }      
    }
  }.elsewhen (w_write) {
    when (io.b_end.ready & (io.b_write.ready | ~r_write)) {
      r_round := 0.U           
    }    
  }.otherwise {
    when (r_round < (w_div_start)) {
      r_round := w_div_start
    }.otherwise {
      r_round := r_round + 1.U
    }    
  }

  // ******************************
  //             INPUTS
  // ******************************
  // ------------------------------
  //             DECODE
  // ------------------------------
  val w_is_rem = Wire(Bool())
  val w_s1_sign = Wire(Bool())
  val w_s2_sign = Wire(Bool())

  w_is_rem := false.B
  w_s1_sign := false.B
  w_s2_sign := false.B

  switch(io.b_in.ctrl.get.ctrl.uop) {
    is (DIV) {
      w_is_rem := false.B
      w_s1_sign := io.b_in.ctrl.get.ctrl.ssign(0) & io.b_in.data.get.s1(p.nDataBit - 1)
      w_s2_sign := io.b_in.ctrl.get.ctrl.ssign(1) & io.b_in.data.get.s2(p.nDataBit - 1)
    }
    is (REM) {
      w_is_rem := true.B
      w_s1_sign := io.b_in.ctrl.get.ctrl.ssign(0) & io.b_in.data.get.s1(p.nDataBit - 1)
      w_s2_sign := io.b_in.ctrl.get.ctrl.ssign(1) & io.b_in.data.get.s2(p.nDataBit - 1)
    }
  }

  // ------------------------------
  //   MUX BUS & UNSIGN (S TO U)
  // ------------------------------
  when (w_start) {
    w_bus.ctrl.get.info := io.b_in.ctrl.get.info
    w_bus.ctrl.get.br := io.b_in.ctrl.get.br
    w_bus.ctrl.get.ctrl := io.b_in.ctrl.get.ctrl
    w_bus.ctrl.get.gpr := io.b_in.ctrl.get.gpr
    
    w_bus.ctrl.get.stage.is_rem := w_is_rem

    w_bus.data.get := DontCare
    w_bus.data.get.s1_sign := w_s1_sign
    w_bus.data.get.s2_sign := w_s2_sign
    w_bus.data.get.us1 := Mux(w_s1_sign, ~(io.b_in.data.get.s1 - 1.U), io.b_in.data.get.s1)
    w_bus.data.get.us2 := Mux(w_s2_sign, ~(io.b_in.data.get.s2 - 1.U), io.b_in.data.get.s2)

    if (p.debug) w_bus.ctrl.get.etd.get := io.b_in.ctrl.get.etd.get
  }.otherwise {
    w_bus.ctrl.get := m_bus.io.b_out.ctrl.get
    w_bus.data.get := m_bus.io.b_out.data.get
  }

  // ******************************
  //            DIVIDE
  // ****************************** 
  // ------------------------------
  //        SPECIAL RESULTS
  // ------------------------------
  w_res_spec := w_src_zero | w_div_zero | w_div_over
  w_src_zero := (w_bus.data.get.us1 === 0.U)  
  w_div_zero := (w_bus.data.get.us2 === 0.U)  
  w_div_over := (w_bus.data.get.s1_sign ^ w_bus.data.get.s2_sign) & (w_bus.data.get.us1 === Cat(1.B, Fill(p.nDataBit - 1, 0.B))) & (w_bus.data.get.us2 === Fill(p.nDataBit, 1.B))

  // Accelerated result
  w_div_start := PriorityEncoder(Reverse(w_bus.data.get.us1))

  // ------------------------------
  //          NEW DIVIDEND
  // ------------------------------
  val w_div = Wire(Vec(p.nDataBit, Bool()))
  when (w_start | w_write) {
    for (b <- 1 until p.nDataBit) {
      w_div(b) := 0.B
    }
    w_div(0) := w_bus.data.get.us1((p.nDataBit - 1).U)
  }.otherwise {
    for (b <- 1 until p.nDataBit) {
      w_div(b) := w_bus.data.get.urem(b - 1)
    }
    w_div(0) := w_bus.data.get.us1((p.nDataBit - 1).U - r_round)
  }
  

  // ------------------------------
  //   NEW QUOTIENT AND REMAINDER
  // ------------------------------
  val w_uquo = Wire(Vec(p.nDataBit, Bool()))
  val w_urem = Wire(UInt(p.nDataBit.W))

  when (w_start | w_write) {
    for (b <- 1 until p.nDataBit) {
      w_uquo(b) := 0.B
    }
  }.otherwise {
    for (b <- 1 until p.nDataBit) {
      w_uquo(b) := w_bus.data.get.uquo(b - 1)
    }
  }
  
  when (w_div.asUInt >= w_bus.data.get.us2) {
    w_uquo(0) := 1.B
    w_urem := w_div.asUInt - w_bus.data.get.us2
  }.otherwise {
    w_uquo(0) := 0.B
    w_urem := w_div.asUInt
  }

  // ******************************
  //           REGISTERS
  // ******************************  
  // Output register
  m_bus.io.i_flush := io.i_flush | (io.i_br_new.valid & w_bus.ctrl.get.br.mask(io.i_br_new.tag))
  m_bus.io.i_br_up := io.i_br_up 

  m_bus.io.b_in.valid := ~w_flush & ((w_start | w_write) & io.b_in.valid) | (~w_start & ~w_write)
  m_bus.io.b_in.ctrl.get := w_bus.ctrl.get

  m_bus.io.b_in.data.get.s1_sign := w_bus.data.get.s1_sign
  m_bus.io.b_in.data.get.s2_sign := w_bus.data.get.s2_sign
  m_bus.io.b_in.data.get.us1 := w_bus.data.get.us1
  m_bus.io.b_in.data.get.us2 := w_bus.data.get.us2
  m_bus.io.b_in.data.get.uquo := w_uquo.asUInt
  m_bus.io.b_in.data.get.urem := w_urem

  // Write register
  when (r_write) {
    r_write := ~(w_write & io.b_write.ready & ~io.b_end.ready)
  }.otherwise {
    r_write := io.b_end.ready
  }

  m_bus.io.b_out.ready := ~w_write | (w_write & io.b_end.ready & (io.b_write.ready | ~r_write))

  // ******************************
  //             RESULT
  // ******************************
  val w_fin = Wire(UInt(p.nDataBit.W))

  // ------------------------------
  //            RESIZE
  // ------------------------------
  val w_size_uquo = Wire(UInt(p.nDataBit.W))
  val w_size_urem = Wire(UInt(p.nDataBit.W))

  if (p.nDataBit >= 64) {
    when (m_bus.io.b_out.ctrl.get.ctrl.rsize === INTSIZE.W) {
      w_size_uquo := m_bus.io.b_out.data.get.uquo(31, 0)
      w_size_urem := m_bus.io.b_out.data.get.urem(31, 0)
    }.otherwise {
      w_size_uquo := m_bus.io.b_out.data.get.uquo
      w_size_urem := m_bus.io.b_out.data.get.urem
    }
  } else {
    w_size_uquo := m_bus.io.b_out.data.get.uquo
    w_size_urem := m_bus.io.b_out.data.get.urem
  }

  // ------------------------------
  //         SIGN (U TO S)
  // ------------------------------
  val w_quo = Wire(UInt(p.nDataBit.W))
  val w_rem = Wire(UInt(p.nDataBit.W))

  when (w_div_zero) {
    w_quo := Fill(p.nDataBit, 1.B)
    w_rem := Mux(m_bus.io.b_out.data.get.s1_sign, (~m_bus.io.b_out.data.get.us1) + 1.U, m_bus.io.b_out.data.get.us1)
  }.elsewhen (w_div_over) {
    w_quo := (~m_bus.io.b_out.data.get.us1) + 1.U
    w_rem := 0.U
  }.elsewhen (w_src_zero) {
    w_quo := 0.U
    w_rem := 0.U
  }.otherwise {
    w_quo := Mux(m_bus.io.b_out.data.get.s1_sign ^ m_bus.io.b_out.data.get.s2_sign, (~w_size_uquo.asUInt) + 1.U, w_size_uquo.asUInt)
    w_rem := Mux(m_bus.io.b_out.data.get.s1_sign, (~w_size_urem) + 1.U, w_size_urem)
  }

  // ------------------------------
  //            SELECT
  // ------------------------------
  when (m_bus.io.b_out.ctrl.get.stage.is_rem) {
    w_fin := w_rem
  }.otherwise {
    w_fin := w_quo       
  }

  // ******************************
  //             I/Os
  // ******************************
  io.b_in.ready := w_start

  io.b_write.valid := w_write & r_write
  io.b_write.rdp := m_bus.io.b_out.ctrl.get.gpr.rdp
  io.b_write.data := w_fin

  io.b_end.valid := w_write & (io.b_write.ready | ~r_write)
  io.b_end.entry := m_bus.io.b_out.ctrl.get.info.entry
  io.b_end.replay := false.B
  io.b_end.trap := DontCare
  io.b_end.trap.valid := false.B
  
  io.b_end.hpc := 0.U.asTypeOf(io.b_end.hpc)

  // ******************************
  //             FREE
  // ******************************
  io.o_free := ~m_bus.io.o_val.valid

  // ******************************
  //             DEBUG
  // ******************************
  if (p.debug) {
    // ------------------------------
    //            SIGNALS
    // ------------------------------
    dontTouch(w_bus.ctrl.get.info)

    // ------------------------------
    //         DATA FOOTPRINT
    // ------------------------------

    // ------------------------------
    //       EXECUTION TRACKER
    // ------------------------------  
    when (w_start | w_write) {
      w_bus.ctrl.get.etd.get := io.b_in.ctrl.get.etd.get
    }.otherwise {
      w_bus.ctrl.get.etd.get := m_bus.io.b_out.ctrl.get.etd.get
    }

    dontTouch(w_bus.ctrl.get.etd.get)
    io.b_end.etd.get := DontCare
  }
}

object Div extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new Div(IntUnitConfigBase), args)
}

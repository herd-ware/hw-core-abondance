/*
 * File: ex.scala                                                              *
 * Created Date: 2023-02-26 09:21:29 am                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-03-02 12:17:22 pm                                       *
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

import herd.common.gen._
import herd.common.field._
import herd.core.abondance.common._
import herd.core.abondance.back.{BranchBus, GprReadIO}
 

class LsuEx(p: LsuParams) extends Module {  
  val io = IO(new Bundle {
    val b_hart = if (p.useField) Some(new RsrcIO(p.nHart, p.nField, 1)) else None

    val i_flush = Input(Bool())

    val b_ld = Vec(p.nLoad, Flipped(new LsuExIO(p, p.nLoadQueue)))
    val b_st = Flipped(new LsuExIO(p, p.nStoreQueue))

    val i_br_up = Input(UInt(p.nSpecBranch.W))
    val i_br_new = Input(new BranchBus(p.nDataBit, p.nSpecBranch, p.nRobEntry))

    val b_read = Flipped(Vec(3, new GprReadIO(p.nDataBit, p.nGprPhy)))
  })

  val m_bus = Seq.fill(2){Module(new SpecReg(p, new LsuExCtrlBus(p), new LsuExDataBus(p), true, p.nSpecBranch))}

  val w_bus = Wire(Vec(2, new GenVBus(p, new LsuExCtrlBus(p), new LsuExDataBus(p))))

  // ******************************
  //          HART STATUS
  // ******************************
  val w_hart_flush = Wire(Bool())

  if (p.useField) {
    w_hart_flush := io.b_hart.get.flush
  } else {
    w_hart_flush := false.B
  } 

  // ******************************
  //       PORT 0: LOAD ONLY
  // ******************************
  // ------------------------------
  //            READ GPR
  // ------------------------------
  io.b_ld(0).req.ready := io.b_read(0).ready
  
  io.b_read(0).valid := io.b_ld(0).req.valid
  io.b_read(0).rsp := io.b_ld(0).req.ctrl.get.rs1p

  // ------------------------------
  //           REGISTERS
  // ------------------------------
  w_bus(0) := m_bus(0).io.o_val

  m_bus(0).io.i_flush := io.i_flush | w_hart_flush | (io.i_br_new.valid & w_bus(0).ctrl.get.br.mask(io.i_br_new.tag))  
  m_bus(0).io.i_br_up := io.i_br_up

  m_bus(0).io.b_in.valid := io.b_ld(0).req.valid & io.b_read(0).ready & ~io.i_flush & ~w_hart_flush & ~(io.i_br_new.valid & io.b_ld(0).req.ctrl.get.br.mask(io.i_br_new.tag))
  m_bus(0).io.b_in.ctrl.get.br := io.b_ld(0).req.ctrl.get.br
  m_bus(0).io.b_in.ctrl.get.is_st := false.B
  m_bus(0).io.b_in.ctrl.get.fwd := false.B
  m_bus(0).io.b_in.ctrl.get.tag := io.b_ld(0).req.ctrl.get.tag
  m_bus(0).io.b_in.ctrl.get.rs1p := io.b_ld(0).req.ctrl.get.rs1p
  m_bus(0).io.b_in.ctrl.get.imm := io.b_ld(0).req.ctrl.get.imm
  m_bus(0).io.b_in.ctrl.get.size := io.b_ld(0).req.ctrl.get.size

  m_bus(0).io.b_in.data.get.s1 := io.b_read(0).data
  m_bus(0).io.b_in.data.get.s2 := DontCare

  m_bus(0).io.b_out.ready := true.B

  // ------------------------------
  //        ADDRESS AND DATA
  // ------------------------------
  io.b_ld(0).ack := DontCare
  io.b_ld(0).ack.valid := m_bus(0).io.b_out.valid
  io.b_ld(0).ack.ctrl.get.tag := m_bus(0).io.b_out.ctrl.get.tag
  io.b_ld(0).ack.ctrl.get.size := m_bus(0).io.b_out.ctrl.get.size
  io.b_ld(0).ack.ctrl.get.rs1p := m_bus(0).io.b_out.ctrl.get.rs1p
  io.b_ld(0).ack.ctrl.get.imm := m_bus(0).io.b_out.ctrl.get.imm
  io.b_ld(0).ack.ctrl.get.addr := m_bus(0).io.b_out.data.get.s1 + m_bus(0).io.b_out.ctrl.get.imm

  // ******************************
  //      PORT 1: STORE OR LOAD
  // ******************************
  // ------------------------------
  //            READ GPR
  // ------------------------------
  when (io.b_st.req.valid) {
    io.b_ld(1).req.ready := false.B
    io.b_st.req.ready := io.b_read(1).ready & io.b_read(2).ready
    
    io.b_read(1).valid := io.b_st.req.valid
    io.b_read(1).rsp := io.b_st.req.ctrl.get.rs1p
    io.b_read(2).valid := io.b_st.req.valid
    io.b_read(2).rsp := io.b_st.req.ctrl.get.rs2p
  }.otherwise {
    io.b_ld(1).req.ready := io.b_read(1).ready
    io.b_st.req.ready := false.B

    io.b_read(1).valid := io.b_ld(1).req.valid
    io.b_read(1).rsp := io.b_ld(1).req.ctrl.get.rs1p
    io.b_read(2).valid := false.B
    io.b_read(2).rsp := DontCare
  }

  // ------------------------------
  //           REGISTERS
  // ------------------------------
  val w_st_valid = Wire(Bool())
  val w_ld1_valid = Wire(Bool())

  w_st_valid := io.b_st.req.valid & ~(io.i_br_new.valid & io.b_st.req.ctrl.get.br.mask(io.i_br_new.tag))
  w_ld1_valid := io.b_ld(1).req.valid & ~(io.i_br_new.valid & io.b_ld(1).req.ctrl.get.br.mask(io.i_br_new.tag))

  w_bus(1) := m_bus(1).io.o_val

  m_bus(1).io.i_flush := io.i_flush | w_hart_flush | (io.i_br_new.valid & w_bus(1).ctrl.get.br.mask(io.i_br_new.tag))  
  m_bus(1).io.i_br_up := io.i_br_up

  m_bus(1).io.b_in.valid := ~io.i_flush & ~w_hart_flush & io.b_read(1).ready & ((w_st_valid & io.b_read(2).ready) | w_ld1_valid)
  m_bus(1).io.b_in.ctrl.get.br := io.b_ld(1).req.ctrl.get.br
  m_bus(1).io.b_in.ctrl.get.is_st := w_st_valid
  m_bus(1).io.b_in.ctrl.get.fwd := io.b_st.req.valid & io.b_st.req.ctrl.get.fwd
  m_bus(1).io.b_in.ctrl.get.tag := Mux(io.b_st.req.valid, io.b_st.req.ctrl.get.tag, io.b_ld(1).req.ctrl.get.tag)
  m_bus(1).io.b_in.ctrl.get.rs1p := Mux(io.b_st.req.valid, io.b_st.req.ctrl.get.rs1p, io.b_ld(1).req.ctrl.get.rs1p)
  m_bus(1).io.b_in.ctrl.get.imm := Mux(io.b_st.req.valid, io.b_st.req.ctrl.get.imm, io.b_ld(1).req.ctrl.get.imm)
  m_bus(1).io.b_in.ctrl.get.size := Mux(io.b_st.req.valid, io.b_st.req.ctrl.get.size, io.b_ld(1).req.ctrl.get.size)

  m_bus(1).io.b_in.data.get.s1 := io.b_read(1).data
  m_bus(1).io.b_in.data.get.s2 := io.b_read(2).data

  m_bus(1).io.b_out.ready := true.B

  // ------------------------------
  //        ADDRESS AND DATA
  // ------------------------------
  io.b_st.ack.valid := m_bus(1).io.b_out.valid & m_bus(1).io.b_out.ctrl.get.is_st
  io.b_st.ack.ctrl.get.fwd := m_bus(1).io.b_out.ctrl.get.fwd
  io.b_st.ack.ctrl.get.tag := m_bus(1).io.b_out.ctrl.get.tag
  io.b_st.ack.ctrl.get.size := m_bus(1).io.b_out.ctrl.get.size
  io.b_st.ack.ctrl.get.rs1p := m_bus(1).io.b_out.ctrl.get.rs1p
  io.b_st.ack.ctrl.get.imm := m_bus(1).io.b_out.ctrl.get.imm
  io.b_st.ack.ctrl.get.addr := m_bus(1).io.b_out.data.get.s1 + m_bus(1).io.b_out.ctrl.get.imm
  io.b_st.ack.data.get := m_bus(1).io.b_out.data.get.s2

  io.b_ld(1).ack.valid := m_bus(1).io.b_out.valid & ~m_bus(1).io.b_out.ctrl.get.is_st
  io.b_ld(1).ack.ctrl.get.fwd := false.B
  io.b_ld(1).ack.ctrl.get.tag := m_bus(1).io.b_out.ctrl.get.tag
  io.b_ld(1).ack.ctrl.get.size := m_bus(1).io.b_out.ctrl.get.size
  io.b_ld(1).ack.ctrl.get.rs1p := m_bus(1).io.b_out.ctrl.get.rs1p
  io.b_ld(1).ack.ctrl.get.imm := m_bus(1).io.b_out.ctrl.get.imm
  io.b_ld(1).ack.ctrl.get.addr := m_bus(1).io.b_out.data.get.s1 + m_bus(1).io.b_out.ctrl.get.imm
  io.b_ld(1).ack.data.get := DontCare

  // ******************************
  //             FIELD
  // ******************************
  if (p.useField) {
    io.b_hart.get.free := ~w_bus(0).valid & ~w_bus(1).valid
  }
  
  // ******************************
  //             DEBUG
  // ******************************
  if (p.debug) {
    // ------------------------------
    //            SIGNALS
    // ------------------------------
    dontTouch(io.b_ld)
    dontTouch(io.b_st)

    // ------------------------------
    //         DATA FOOTPRINT
    // ------------------------------

    // ------------------------------
    //       EXECUTION TRACKER
    // ------------------------------  
  }
}

object LsuEx extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new LsuEx(LsuConfigBase), args)
}
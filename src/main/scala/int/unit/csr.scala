/*
 * File: csr.scala
 * Created Date: 2023-02-26 09:21:29 am                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-03-03 04:27:21 pm
 * Modified By: Mathieu Escouteloup
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
import herd.core.abondance.int.INTUOP._
import herd.core.aubrac.back.csr.{CsrIO, UOP => CSRUOP}


class Csr(p: IntUnitParams) extends Module {
  val io = IO(new Bundle {
    val b_in = Flipped(new GenRVIO(p, new IntUnitCtrlBus(p), new IntUnitDataBus(p)))

    val b_csr = Flipped(new CsrIO(p.nDataBit))

    val b_write = Flipped(new GprWriteIO(p.nDataBit, p.nGprPhy))
    val b_end = new EndIO(p.debug, p.nAddrBit, p.nDataBit, p.nRobEntry)
  })

  val w_lock = Wire(Bool())
  val w_wait_rcsr = Wire(Bool())
  val w_wait_wcsr = Wire(Bool())

  val w_is_read = Wire(Bool())
  val w_is_write = Wire(Bool())

  // ******************************
  //             READ
  // ******************************
  w_is_read := io.b_in.ctrl.get.ctrl.uop(2)
  w_wait_rcsr := io.b_in.valid & w_is_read & ~io.b_csr.read.ready

  io.b_csr.read.valid := io.b_in.valid & w_is_read
  io.b_csr.read.addr := io.b_in.data.get.s3(11, 0)

  // ******************************
  //           REGISTERS
  // ******************************
  val m_out = Module(new SpecReg(p, new IntUnitCtrlBus(p), new CsrDataBus(p), true, p.nSpecBranch))  

  val w_out = Wire(new GenVBus(p, new IntUnitCtrlBus(p), new CsrDataBus(p)))

  // Output register
  w_lock := ~m_out.io.b_in.ready
  w_out := m_out.io.o_val

  m_out.io.i_flush := false.B
  m_out.io.i_br_up := 0.U

  m_out.io.b_in.valid := io.b_in.valid & ~w_wait_rcsr
  m_out.io.b_in.ctrl.get := io.b_in.ctrl.get
  m_out.io.b_in.data.get.s1 := io.b_in.data.get.s1
  m_out.io.b_in.data.get.s3 := io.b_in.data.get.s3
  m_out.io.b_in.data.get.res := io.b_csr.read.data

  m_out.io.b_out.ready := ~w_wait_wcsr

  // ******************************
  //             WRITE
  // ******************************
  w_wait_wcsr := false.B

  io.b_csr.write.valid := w_is_write
  io.b_csr.write.addr := m_out.io.b_out.data.get.s3(11, 0)
  io.b_csr.write.uop := DontCare
  io.b_csr.write.mask := m_out.io.b_out.data.get.s1
  io.b_csr.write.data := m_out.io.b_out.data.get.res

  w_is_write := false.B
  switch(m_out.io.b_out.ctrl.get.ctrl.uop(1, 0)) {
    is (CSRW, CSRRW)   {
      w_is_write := true.B
      io.b_csr.write.uop := CSRUOP.W
    }
    is (CSRS, CSRRS)   {
      w_is_write := true.B
      io.b_csr.write.uop := CSRUOP.S
    }
    is (CSRC, CSRRC)   {
      w_is_write := true.B
      io.b_csr.write.uop := CSRUOP.C
    }
  }

  // ******************************
  //              I/O
  // ******************************
  io.b_in.ready := ~w_lock & ~w_wait_rcsr

  io.b_write.valid := m_out.io.b_out.valid & m_out.io.b_out.ctrl.get.gpr.en
  io.b_write.rdp := m_out.io.b_out.ctrl.get.gpr.rdp
  io.b_write.data := m_out.io.b_out.data.get.res

  io.b_end.valid := m_out.io.b_out.valid
  io.b_end.entry := m_out.io.b_out.ctrl.get.info.entry
  io.b_end.replay := false.B
  io.b_end.trap := DontCare
  io.b_end.trap.valid := false.B    
  
  io.b_end.hpc := 0.U.asTypeOf(io.b_end.hpc)

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
    dontTouch(m_out.io.b_out.ctrl.get.etd.get)
    
    io.b_end.etd.get := DontCare
  }
}

object Csr extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new Csr(IntUnitConfigBase), args)
}

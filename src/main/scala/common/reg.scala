/*
 * File: reg.scala                                                             *
 * Created Date: 2023-02-26 09:21:29 am                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-26 09:29:50 am                                       *
 * Modified By: Mathieu Escouteloup                                            *
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.core.abondance.common

import chisel3._
import chisel3.util._

import herd.common.gen._


class SpecReg[TC <: SpecCtrlBus, TD <: Data](p: GenParams, tc: TC, td: TD, isPipe: Boolean, nSpecBranch: Int) extends Module {  
  // ******************************
  //             I/Os
  // ******************************
  val io = IO(new Bundle {
    val i_flush = Input(Bool())

    val i_br_up = Input(UInt(nSpecBranch.W))

    val b_in = Flipped(new GenRVIO(p, tc, td))

    val o_val = Output(new GenVBus(p, tc, td))
    val o_reg = Output(new GenVBus(p, tc, td))

    val b_out = new GenRVIO(p, tc, td)
  })

  // ******************************
  //         INIT REGISTERS
  // ******************************
  val init_reg = Wire(new GenVBus(p, tc, td))

  init_reg := DontCare
  init_reg.valid := false.B   

  val r_reg = RegInit(init_reg)

  // ******************************
  //            OUTPUT
  // ******************************  
  r_reg.valid := r_reg.valid & ~io.b_out.ready
  io.b_out.valid := r_reg.valid
  if (tc.getWidth > 0) io.b_out.ctrl.get := r_reg.ctrl.get
  if (td.getWidth > 0) io.b_out.data.get := r_reg.data.get

  // ******************************
  //            INPUT
  // ******************************
  val w_lock = Wire(Bool())

  if (isPipe) {
    w_lock := r_reg.valid & ~io.b_out.ready & ~io.i_flush
  } else {
    w_lock := r_reg.valid
  }
  io.b_in.ready := ~w_lock

  // Flush
  when (io.i_flush) {
    r_reg.valid := false.B
  } 
  
  when (io.b_in.valid & ~w_lock) {
    r_reg.valid := true.B
    if (tc.getWidth > 0) {
      r_reg.ctrl.get := io.b_in.ctrl.get
      r_reg.ctrl.get.br.mask := io.b_in.ctrl.get.br.mask & io.i_br_up 
    }
    if (td.getWidth > 0) r_reg.data.get := io.b_in.data.get
  }.otherwise {
    r_reg.ctrl.get.br.mask := r_reg.ctrl.get.br.mask & io.i_br_up 
  } 

  // ******************************
  //        EXTERNAL ACCESS
  // ******************************
  io.o_val := r_reg
  io.o_reg := r_reg
}
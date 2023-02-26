/*
 * File: reg.scala                                                             *
 * Created Date: 2023-02-26 09:21:29 am                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-26 09:30:05 am                                       *
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
import herd.core.abondance.common._
import herd.core.abondance.back.{CommitBus}


class ExtReg[UC <: Data](p: ExUnitParams, uc: UC, isPipe: Boolean) extends Module {  
  // ******************************
  //             I/Os
  // ******************************
  val io = IO(new Bundle {
    val i_flush = Input(Bool())

    val b_in = Flipped(new GenRVIO(p, new ExtCtrlBus(p, uc), new ExtDataBus(p)))

    val i_br_up = Input(UInt(p.nSpecBranch.W))
    val i_commit = Input(Vec(p.nCommit, new CommitBus(p.nRobEntry, 1, p.nGprPhy)))

    val o_val = Output(new GenVBus(p, new ExtCtrlBus(p, uc), new ExtDataBus(p)))
    val o_reg = Output(new GenVBus(p, new ExtCtrlBus(p, uc), new ExtDataBus(p)))

    val b_out = new GenRVIO(p, new ExtCtrlBus(p, uc), new ExtDataBus(p))
  })

  // ******************************
  //         INIT REGISTERS
  // ******************************
  val init_reg = Wire(new GenVBus(p, new ExtCtrlBus(p, uc), new ExtDataBus(p)))

  init_reg := DontCare
  init_reg.valid := false.B   

  val r_reg = RegInit(init_reg)

  // ******************************
  //            OUTPUT
  // ******************************  
  r_reg.valid := r_reg.valid & ~io.b_out.ready
  io.b_out.valid := r_reg.valid
  io.b_out.ctrl.get := r_reg.ctrl.get
  io.b_out.data.get := r_reg.data.get

  // ******************************
  //            INPUT
  // ******************************
  val w_lock = Wire(Bool())

  if (isPipe) {
    w_lock := r_reg.valid & (~io.b_out.ready | io.i_flush)
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
    r_reg.ctrl.get := io.b_in.ctrl.get
    r_reg.ctrl.get.br.mask := io.b_in.ctrl.get.br.mask & io.i_br_up     

    for (c <- 0 until p.nCommit) {
      when (io.i_commit(c).valid & (io.i_commit(c).entry === io.b_in.ctrl.get.link.entry)) {
        r_reg.ctrl.get.link.av := true.B
      }           
    }

    r_reg.data.get := io.b_in.data.get
  }.otherwise {
    r_reg.ctrl.get.br.mask := r_reg.ctrl.get.br.mask & io.i_br_up   

    for (c <- 0 until p.nCommit) {
      when (io.i_commit(c).valid & (io.i_commit(c).entry === r_reg.ctrl.get.link.entry)) {
        r_reg.ctrl.get.link.av := true.B
      }           
    }
  } 

  // ******************************
  //        EXTERNAL ACCESS
  // ******************************
  io.o_val := r_reg
  io.o_reg := r_reg
}
/*
 * File: iss.scala                                                             *
 * Created Date: 2023-02-26 09:21:29 am                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-03-02 12:13:16 pm                                       *
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
import herd.core.aubrac.back.{OP}
import herd.core.abondance.common._
import herd.core.abondance.int.{IntQueueBus}
import herd.core.abondance.lsu.{LsuQueueBus}
import herd.core.abondance.ext.{ExtReqQueueBus}


class IssStage(p: BackParams) extends Module {
  val io = IO(new Bundle {
    val b_hart = if (p.useField) Some(new RsrcIO(p.nHart, p.nField, 1)) else None
    val b_back = if (p.useField) Some(Vec(p.nBackPort, new RsrcIO(p.nHart, p.nField, 1))) else None
    
    val i_init = Input(Bool())
    val i_flush = Input(Bool())

    val b_in = Vec(p.nBackPort, Flipped(new GenRVIO(p, new IssCtrlBus(p), UInt(0.W))))

    val i_br_new = Input(new BranchBus(32, p.nSpecBranch, p.nRobEntry))
    val i_br_up = Input(UInt(p.nSpecBranch.W))
    val b_busy = Flipped(Vec(p.nBackPort, Vec(2, new GprReadIO(p.nDataBit, p.nGprPhy))))
    val i_commit = Input(Vec(p.nCommit, new CommitBus(p.nRobEntry, p.nGprLog, p.nGprPhy)))

    val b_int = Vec(p.nBackPort, new GenRVIO(p, new IntQueueBus(p), UInt(0.W)))
    val b_lsu = Vec(p.nBackPort, new GenRVIO(p, new LsuQueueBus(p), UInt(0.W)))
    val b_hfu = if (p.useChamp) Some(Vec(p.nBackPort, new GenRVIO(p, new ExtReqQueueBus(p, new HfuCtrlBus()), UInt(0.W)))) else None
  })

  val w_wait = Wire(Bool())
  val w_int_wait = Wire(Vec(p.nBackPort, Bool()))
  val w_lsu_wait = Wire(Vec(p.nBackPort, Bool()))
  val w_hfu_wait = Wire(Vec(p.nBackPort, Bool()))

  w_wait := w_int_wait.asUInt.orR | w_lsu_wait.asUInt.orR | w_hfu_wait.asUInt.orR


  // ******************************
  //        BACK PORT STATUS
  // ******************************
  val w_hart_flush = Wire(Bool())
  val w_back_valid = Wire(Vec(p.nBackPort, Bool()))
  val w_back_flush = Wire(Vec(p.nBackPort, Bool()))

  for (bp <- 0 until p.nBackPort) {
    if (p.useField) {
      w_hart_flush := io.b_hart.get.flush | io.i_init
      w_back_valid(bp) := io.b_back.get(bp).valid & ~io.b_back.get(bp).flush
      w_back_flush(bp) := w_hart_flush | io.b_back.get(bp).flush | io.i_flush
    } else {
      w_hart_flush := io.i_init
      w_back_valid(bp) := true.B
      w_back_flush(bp) := w_hart_flush | io.i_flush
    }
  }

  // ******************************
  //   DEPEND: INSTRUCTIONS LINK
  // ******************************
  val w_dep = Wire(Vec(p.nBackPort, new DependBus(p.nRobEntry, p.nGprPhy)))

  for (bp <- 0 until p.nBackPort) {
    w_dep(bp).rs1p := io.b_in(bp).ctrl.get.dep.rs1p
    w_dep(bp).rs2p := io.b_in(bp).ctrl.get.dep.rs2p
  }

  // ------------------------------
  //             INIT
  // ------------------------------
  val init_dep_spec = Wire(new LinkBus(p))

  // Previous valid operation ?
  init_dep_spec.prev.valid := false.B
  init_dep_spec.prev.ctrl.get := DontCare
  // Previous valid operation making an edge in execution ?
  init_dep_spec.edge.valid := false.B
  init_dep_spec.edge.ctrl.get := DontCare

  val r_dep_spec = RegInit(init_dep_spec)
  val r_dep_br = Reg(Vec(p.nSpecBranch, new LinkBus(p)))

  val w_dep_spec = Wire(Vec(p.nBackPort + 1, new LinkBus(p)))

  // ------------------------------
  //            COMMIT
  // ------------------------------
  w_dep_spec(0) := r_dep_spec
  for (c <- 0 until p.nCommit) {
    when (io.i_commit(c).valid & (io.i_commit(c).entry === r_dep_spec.prev.ctrl.get)) {
      w_dep_spec(0).prev.valid := false.B
    }

    when (io.i_commit(c).valid & (io.i_commit(c).entry === r_dep_spec.edge.ctrl.get)) {
      w_dep_spec(0).edge.valid := false.B
    }
    
    for (sb <- 0 until p.nSpecBranch) {
      when (io.i_commit(c).valid & (io.i_commit(c).entry === r_dep_br(sb).prev.ctrl.get)) {
        r_dep_br(sb).prev.valid := false.B
      }

      when (io.i_commit(c).valid & (io.i_commit(c).entry === r_dep_br(sb).edge.ctrl.get)) {
        r_dep_br(sb).edge.valid := false.B
      }    
    }
  }  

  // ------------------------------
  //            BACKPORT
  // ------------------------------
  // Dependencies for each port
  for (bp <- 1 to p.nBackPort) {
    w_dep_spec(bp) := w_dep_spec(bp - 1)
  }

  // Update for new inputs
  for (bp <- 0 until p.nBackPort) {
    w_dep(bp).av(0) := ~(w_dep_spec(bp).edge.valid | (w_dep_spec(bp).prev.valid & io.b_in(bp).ctrl.get.info.ser))
    w_dep(bp).entry := Mux(io.b_in(bp).ctrl.get.info.ser, w_dep_spec(bp).prev.ctrl.get, w_dep_spec(bp).edge.ctrl.get)

    when (io.b_in(bp).valid & w_back_valid(bp) & ~w_back_flush(bp)) {
      w_dep_spec(bp + 1).prev.valid := true.B
      w_dep_spec(bp + 1).prev.ctrl.get := io.b_in(bp).ctrl.get.info.entry
    }

    when (io.b_in(bp).valid & w_back_valid(bp) & ~w_back_flush(bp) & io.b_in(bp).ctrl.get.info.ser) {
      w_dep_spec(bp + 1).edge.valid := true.B
      w_dep_spec(bp + 1).edge.ctrl.get := io.b_in(bp).ctrl.get.info.entry
    }
  }

  // ------------------------------
  //            REGISTER
  // ------------------------------
  // Branches
  for (bp <- 0 until p.nBackPort) {
    when (~w_wait & io.b_in(bp).valid & w_back_valid(bp) & ~w_back_flush(bp) & io.b_in(bp).ctrl.get.br.valid) {
      r_dep_br(io.b_in(bp).ctrl.get.br.tag) := w_dep_spec(bp)
    }
  }

  // Speculation
  when (w_hart_flush) {
    r_dep_spec.prev.valid := false.B
    r_dep_spec.edge.valid := false.B
  }.elsewhen (io.i_br_new.valid) {
    r_dep_spec := r_dep_br(io.i_br_new.tag)
  }.elsewhen(~w_wait) {
    r_dep_spec := w_dep_spec(p.nBackPort)
  }

  // ******************************
  //          DEPEND: DATA
  // ******************************
  for (bp <- 0 until p.nBackPort) {
    // RS1
    io.b_busy(bp)(0).valid := true.B
    io.b_busy(bp)(0).rsp := io.b_in(bp).ctrl.get.dep.rs1p
    w_dep(bp).av(1) := (io.b_in(bp).ctrl.get.data.s1type =/= OP.XREG) | io.b_busy(bp)(0).ready

    // RS2
    io.b_busy(bp)(1).valid := true.B
    io.b_busy(bp)(1).rsp := io.b_in(bp).ctrl.get.dep.rs2p
    w_dep(bp).av(2) := (io.b_in(bp).ctrl.get.data.s2type =/= OP.XREG) | io.b_busy(bp)(1).ready
  }

  // ******************************
  //           EXECUTION
  // ******************************
  for (bp <- 0 until p.nBackPort) {

    // ------------------------------
    //             INT
    // ------------------------------
    w_int_wait(bp) := io.b_in(bp).valid & w_back_valid(bp) & ~w_back_flush(bp) & (io.b_in(bp).ctrl.get.ex.ex_type === EXTYPE.INT) & ~io.b_int(bp).ready
    io.b_int(bp).valid := io.b_in(bp).valid & w_back_valid(bp) & ~w_back_flush(bp) & (io.b_in(bp).ctrl.get.ex.ex_type === EXTYPE.INT) & ~w_lsu_wait.asUInt.orR & ~w_hfu_wait.asUInt.orR
    io.b_int(bp).ctrl.get.info := io.b_in(bp).ctrl.get.info
    io.b_int(bp).ctrl.get.br := io.b_in(bp).ctrl.get.br
    io.b_int(bp).ctrl.get.br.mask := io.b_in(bp).ctrl.get.br.mask & io.i_br_up
    io.b_int(bp).ctrl.get.ctrl := io.b_in(bp).ctrl.get.ex.int
    io.b_int(bp).ctrl.get.gpr := io.b_in(bp).ctrl.get.ex.gpr
    io.b_int(bp).ctrl.get.dep := w_dep(bp)
    io.b_int(bp).ctrl.get.data := io.b_in(bp).ctrl.get.data

    // ------------------------------
    //             LSU
    // ------------------------------
    w_lsu_wait(bp) := io.b_in(bp).valid & w_back_valid(bp) & ~w_back_flush(bp) & (io.b_in(bp).ctrl.get.ex.ex_type === EXTYPE.LSU) & ~io.b_lsu(bp).ready
    io.b_lsu(bp).valid := io.b_in(bp).valid & w_back_valid(bp) & ~w_back_flush(bp) & (io.b_in(bp).ctrl.get.ex.ex_type === EXTYPE.LSU) & ~w_int_wait.asUInt.orR & ~w_hfu_wait.asUInt.orR
    io.b_lsu(bp).ctrl.get.info := io.b_in(bp).ctrl.get.info
    io.b_lsu(bp).ctrl.get.br := io.b_in(bp).ctrl.get.br
    io.b_lsu(bp).ctrl.get.br.mask := io.b_in(bp).ctrl.get.br.mask & io.i_br_up
    io.b_lsu(bp).ctrl.get.ctrl := io.b_in(bp).ctrl.get.ex.lsu
    io.b_lsu(bp).ctrl.get.gpr := io.b_in(bp).ctrl.get.ex.gpr
    io.b_lsu(bp).ctrl.get.dep := w_dep(bp)
    io.b_lsu(bp).ctrl.get.data := io.b_in(bp).ctrl.get.data
    
    // ------------------------------
    //             HFU
    // ------------------------------
    if (p.useChamp) {
      w_hfu_wait(bp) := io.b_in(bp).valid & w_back_valid(bp) & ~w_back_flush(bp) & (io.b_in(bp).ctrl.get.ex.ex_type === EXTYPE.HFU) & ~io.b_hfu.get(bp).ready
      io.b_hfu.get(bp).valid := io.b_in(bp).valid & w_back_valid(bp) & ~w_back_flush(bp) & (io.b_in(bp).ctrl.get.ex.ex_type === EXTYPE.HFU) & ~w_int_wait.asUInt.orR & ~w_lsu_wait.asUInt.orR
      io.b_hfu.get(bp).ctrl.get.info := io.b_in(bp).ctrl.get.info
      io.b_hfu.get(bp).ctrl.get.br := io.b_in(bp).ctrl.get.br
      io.b_hfu.get(bp).ctrl.get.br.mask := io.b_in(bp).ctrl.get.br.mask & io.i_br_up
      io.b_hfu.get(bp).ctrl.get.dep := w_dep(bp)
      io.b_hfu.get(bp).ctrl.get.data := io.b_in(bp).ctrl.get.data
      io.b_hfu.get(bp).ctrl.get.gpr := io.b_in(bp).ctrl.get.ex.gpr

      io.b_hfu.get(bp).ctrl.get.ctrl.code := io.b_in(bp).ctrl.get.ext.code
      io.b_hfu.get(bp).ctrl.get.ctrl.op1 := io.b_in(bp).ctrl.get.ext.op1
      io.b_hfu.get(bp).ctrl.get.ctrl.op2 := io.b_in(bp).ctrl.get.ext.op2
      io.b_hfu.get(bp).ctrl.get.ctrl.op3 := io.b_in(bp).ctrl.get.ext.op3
      io.b_hfu.get(bp).ctrl.get.ctrl.hfs1 := io.b_in(bp).ctrl.get.ext.rs1
      io.b_hfu.get(bp).ctrl.get.ctrl.hfs2 := io.b_in(bp).ctrl.get.ext.rs2
    } else {
      w_hfu_wait(bp) := false.B
    }

    // ******************************
    //            READY
    // ******************************
    io.b_in(bp).ready := w_back_flush(bp) | ~w_wait
  }

  // ******************************
  //             FIELD
  // ******************************
  if (p.useField) {
    io.b_hart.get.free := true.B
    for (bp <- 0 until p.nBackPort) { 
      io.b_back.get(bp).free := true.B
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
      io.b_int(bp).ctrl.get.etd.get := io.b_in(bp).ctrl.get.etd.get
      io.b_lsu(bp).ctrl.get.etd.get := io.b_in(bp).ctrl.get.etd.get
      if (p.useChamp) io.b_hfu.get(bp).ctrl.get.etd.get := io.b_in(bp).ctrl.get.etd.get
    }
  }
}

object IssStage extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new IssStage(BackConfigBase), args)
}

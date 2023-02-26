/*
 * File: lsu.scala                                                             *
 * Created Date: 2023-02-26 09:21:29 am                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-26 09:31:31 am                                       *
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
import herd.common.dome._
import herd.common.mem.mb4s._
import herd.common.mem.mb4s.{OP => LSUUOP}
import herd.core.abondance.common._
import herd.core.abondance.back.{BranchBus, BypassBus, CommitBus, GprReadIO, GprWriteIO, EndIO}
 

class Lsu(p: LsuParams) extends Module {  
  val io = IO(new Bundle {
    val b_hart = if (p.useDome) Some(new RsrcIO(p.nHart, p.nDome, 1)) else None

    val i_flush = Input(Bool())

    val b_in = Vec(p.nBackPort, Flipped(new GenRVIO(p, new LsuQueueBus(p), UInt(0.W))))

    val i_br_up = Input(UInt(p.nSpecBranch.W))
    val i_br_new = Input(new BranchBus(p.nDataBit, p.nSpecBranch, p.nRobEntry))
    val i_busy = Input(Vec(p.nBypass, new BypassBus(p.nDataBit, p.nGprPhy)))
    val i_commit = Input(Vec(p.nCommit, new CommitBus(p.nRobEntry, 1, p.nGprPhy)))

    val b_read = Flipped(Vec(3, new GprReadIO(p.nDataBit, p.nGprPhy)))

    val b_d0mem = new Mb4sIO(p.pL0D0Bus)
    val b_d1mem = new Mb4sIO(p.pL0D1Bus)

    val o_byp = Output(Vec(p.nLoad, new BypassBus(p.nDataBit, p.nGprPhy)))
    val b_write = Flipped(Vec(p.nLoad, new GprWriteIO(p.nDataBit, p.nGprPhy)))
    val b_end = Vec(p.nLoad, new EndIO(p.debug, p.nAddrBit, p.nDataBit, p.nRobEntry))
  })

  val w_wait_st = Wire(Bool())
  val w_wait_st_reg = Wire(Bool())
  val w_wait_st_queue = Wire(Bool())
  val w_st_end = Wire(new GenVBus(p, new LsuExAckCtrlBus(p, p.nStoreQueue), UInt(0.W)))

  val w_wait_ld = Wire(Bool())
  val w_ld_done = Wire(Vec(p.nLoad, new TagBus(p.nLoadQueue)))
  val w_ld_data = Wire(Vec(p.nLoad, UInt(p.nDataBit.W)))

  val w_wait_d0_req = Wire(Bool())
  val w_wait_d0_fwd = Wire(Bool())
  val w_wait_d0_ack = Wire(Bool())
  val w_wait_d1_req = Wire(Bool())
  val w_wait_d1_fwd = Wire(Bool())  
  val w_wait_d1_ack = Wire(Bool())

  w_wait_st := w_wait_st_reg | w_wait_st_queue

  // ******************************
  //          HART STATUS
  // ******************************
  val w_hart_flush = Wire(Bool())

  if (p.useDome) {
    w_hart_flush := io.b_hart.get.flush
  } else {
    w_hart_flush := false.B
  } 

  // ******************************
  //             MODULES
  // ******************************
  val m_st = Module(new StoreQueue(p))
  val m_ld = Module(new LoadQueue(p))
  val m_ex = Module(new LsuEx(p))
  val r_fwd_av = RegInit(VecInit(Seq.fill(p.nLoad)(true.B)))
  val m_mem = Seq.fill(p.nLoad){Module(new MemQueue(p))}
  val m_fwd = Seq.fill(p.nLoad){Module(new SpecReg(p, new MemQueueCtrlBus(p), UInt(p.nDataBit.W), true, p.nSpecBranch))}
  val m_end = Seq.fill(p.nLoad){Module(new SpecReg(p, new MemEndCtrlBus(p), UInt(p.nDataBit.W), true, p.nSpecBranch))}

  // ******************************
  //             INPUT
  // ******************************
  for (bp <- 0 until p.nBackPort) {
    io.b_in(bp).ready := ~w_wait_st & ~w_wait_ld
  }

  // ******************************
  //             STORE
  // ******************************
  val r_st_act = RegInit(VecInit(Seq.fill(p.nStoreQueue)(false.B)))

  // ------------------------------
  //           CALCULATE
  // ------------------------------
  val w_st_act = Wire(Vec(p.nBackPort + 1, Vec(p.nStoreQueue, Bool())))

  val w_st_valid = Wire(Vec(p.nBackPort, Bool()))
  val w_st_av = Wire(Vec(p.nBackPort, Bool()))
  val w_st_tag = Wire(Vec(p.nBackPort, UInt(log2Ceil(p.nStoreQueue).W)))

  w_st_act(0) := r_st_act

  for (bp <- 0 until p.nBackPort) {
    w_st_valid(bp) := io.b_in(bp).valid & ~io.i_flush & ~w_hart_flush & io.b_in(bp).ctrl.get.ctrl.st
    w_st_av(bp) := ~w_st_act(bp).asUInt.andR
    w_st_tag(bp) := PriorityEncoder(~w_st_act(bp).asUInt)

    w_st_act(bp + 1) := w_st_act(bp)
    when (w_st_valid(bp) & w_st_av(bp)) {
      w_st_act(bp + 1)(w_st_tag(bp)) := true.B
    }
  }

  // ------------------------------
  //          SPECULATIVE
  // ------------------------------
  val w_st_spec = Wire(Vec(p.nBackPort, Vec(p.nStoreQueue, Bool())))

  for (bp <- 0 until p.nBackPort) {
    for (sq <- 0 until p.nStoreQueue) {
      w_st_spec(bp)(sq) := w_st_act(bp)(sq) & (~m_st.io.o_rs1p(sq).valid | (io.b_in(bp).ctrl.get.dep.rs1p === m_st.io.o_rs1p(sq).ctrl.get))
    }
  }

  // ------------------------------
  //             WAIT
  // ------------------------------
  w_wait_st_reg := false.B
  for (bp <- 0 until p.nBackPort) {
    when (w_st_valid(bp) & ~w_st_av(bp)) {
      w_wait_st_reg := true.B
    }
  }

  // ------------------------------
  //            RELEASE
  // ------------------------------
  val w_st_rel = Wire(Vec(p.nStoreQueue, Bool()))

  w_st_rel := m_st.io.o_rel.asBools
  for (sq <- 0 until p.nStoreQueue) {
    when (w_st_end.valid & (sq.U === w_st_end.ctrl.get.tag)) {
      w_st_rel(sq) := false.B
    }
  }

  // ------------------------------
  //           REGISTERS
  // ------------------------------
  for (sq <- 0 until p.nStoreQueue) {
    when (w_hart_flush) {
      r_st_act(sq) := false.B
    }.elsewhen (io.i_flush | io.i_br_new.valid | w_wait_st | w_wait_ld) {
      r_st_act(sq) := r_st_act(sq) & w_st_rel(sq)
    }.otherwise {
      r_st_act(sq) := w_st_act(p.nBackPort)(sq) & w_st_rel(sq)
    }
  }

  // ------------------------------
  //             QUEUE
  // ------------------------------
  w_wait_st_queue := false.B

  if (p.useDome) m_st.io.b_hart.get <> io.b_hart.get
  m_st.io.i_flush := io.i_flush

  m_st.io.i_br_up := io.i_br_up
  m_st.io.i_br_new := io.i_br_new
  m_st.io.i_busy := io.i_busy
  m_st.io.i_commit := io.i_commit

  for (bp <- 0 until p.nBackPort) {
    when (w_st_valid(bp) & ~m_st.io.b_in(bp).ready) {
      w_wait_st_queue := true.B
    }

    m_st.io.b_in(bp).valid := w_st_valid(bp) & ~w_wait_ld & ~w_wait_st_reg
    m_st.io.b_in(bp).ctrl.get.info.entry := io.b_in(bp).ctrl.get.info.entry
    m_st.io.b_in(bp).ctrl.get.info.addr := io.b_in(bp).ctrl.get.data.imm1
    m_st.io.b_in(bp).ctrl.get.info.rdp := io.b_in(bp).ctrl.get.gpr.rdp
    m_st.io.b_in(bp).ctrl.get.br := io.b_in(bp).ctrl.get.br
    m_st.io.b_in(bp).ctrl.get.state.rr := false.B
    m_st.io.b_in(bp).ctrl.get.state.addr := false.B
    m_st.io.b_in(bp).ctrl.get.state.end := false.B
    m_st.io.b_in(bp).ctrl.get.state.commit := false.B
    m_st.io.b_in(bp).ctrl.get.ctrl := io.b_in(bp).ctrl.get.ctrl
    m_st.io.b_in(bp).ctrl.get.dep := io.b_in(bp).ctrl.get.dep
    m_st.io.b_in(bp).ctrl.get.tag := w_st_tag(bp)
  }

  // ******************************
  //             LOAD
  // ******************************
  w_wait_ld := false.B

  if (p.useDome) m_ld.io.b_hart.get <> io.b_hart.get
  m_ld.io.i_flush := io.i_flush

  m_ld.io.i_br_up := io.i_br_up
  m_ld.io.i_br_new := io.i_br_new
  m_ld.io.i_busy := io.i_busy
  m_ld.io.i_commit := io.i_commit

  m_ld.io.i_st_ex.valid := m_ex.io.b_st.ack.valid
  m_ld.io.i_st_ex.ctrl.get := m_ex.io.b_st.ack.ctrl.get
  m_ld.io.i_st_end := w_st_end

  m_ld.io.i_done := w_ld_done

  for (bp <- 0 until p.nBackPort) {
    when (io.b_in(bp).valid & ~io.i_flush & io.b_in(bp).ctrl.get.ctrl.ldo & ~m_ld.io.b_in(bp).ready) {
      w_wait_ld := true.B
    }  

    m_ld.io.b_in(bp).valid := io.b_in(bp).valid & ~io.i_flush & io.b_in(bp).ctrl.get.ctrl.ldo & ~w_wait_st
    m_ld.io.b_in(bp).ctrl.get.info.entry := io.b_in(bp).ctrl.get.info.entry
    m_ld.io.b_in(bp).ctrl.get.info.addr := io.b_in(bp).ctrl.get.data.imm1
    m_ld.io.b_in(bp).ctrl.get.info.rdp := io.b_in(bp).ctrl.get.gpr.rdp
    m_ld.io.b_in(bp).ctrl.get.br := io.b_in(bp).ctrl.get.br
    m_ld.io.b_in(bp).ctrl.get.state.rr := false.B
    m_ld.io.b_in(bp).ctrl.get.state.addr := false.B
    m_ld.io.b_in(bp).ctrl.get.state.mem := false.B
    m_ld.io.b_in(bp).ctrl.get.state.done := false.B
    m_ld.io.b_in(bp).ctrl.get.state.replay := false.B  
    m_ld.io.b_in(bp).ctrl.get.ctrl := io.b_in(bp).ctrl.get.ctrl
    m_ld.io.b_in(bp).ctrl.get.dep := io.b_in(bp).ctrl.get.dep
    m_ld.io.b_in(bp).ctrl.get.ld_prev := DontCare
    m_ld.io.b_in(bp).ctrl.get.ld_spec := DontCare
    m_ld.io.b_in(bp).ctrl.get.st_prev := w_st_act(bp).asUInt
    m_ld.io.b_in(bp).ctrl.get.st_spec := w_st_spec(bp).asUInt
    m_ld.io.b_in(bp).ctrl.get.fwd := 0.U
  }

  // ******************************
  //            EX STAGE
  // ******************************
  if (p.useDome) m_ex.io.b_hart.get <> io.b_hart.get
  m_ex.io.i_flush := io.i_flush
  m_ex.io.i_br_up := io.i_br_up
  m_ex.io.i_br_new := io.i_br_new

  m_ex.io.b_ld <> m_ld.io.b_ex
  m_ex.io.b_st <> m_st.io.b_ex
  m_ex.io.b_read <> io.b_read

  // ******************************
  //         MEMORY PORT 0
  // ******************************
  m_ld.io.b_mem(0).ready := ~w_wait_d0_req & ~w_wait_d0_fwd  

  // ------------------------------
  //              REQ
  // ------------------------------
  w_wait_d0_req := (~m_ld.io.b_mem(0).ctrl.get.fwd.asUInt.orR | ~r_fwd_av(0)) & ~(m_mem(0).io.b_in.ready & io.b_d0mem.req.ready(0))

  io.b_d0mem.req.valid := m_ld.io.b_mem(0).valid & (~m_ld.io.b_mem(0).ctrl.get.fwd.asUInt.orR | ~r_fwd_av(0)) & m_mem(0).io.b_in.ready
  if (p.useDome) io.b_d0mem.req.dome.get := io.b_hart.get.dome
  io.b_d0mem.req.ctrl.hart := 0.U
  if (p.useExtA) {
    io.b_d0mem.req.ctrl.op := m_ld.io.b_mem(0).ctrl.get.ctrl.uop
    io.b_d0mem.req.ctrl.amo.get := m_ld.io.b_mem(0).ctrl.get.ctrl.amo
  } else {
    io.b_d0mem.req.ctrl.op := LSUUOP.R
  }
  io.b_d0mem.req.ctrl.size := LSUSIZE.toMb4s(m_ld.io.b_mem(0).ctrl.get.ctrl.size)
  io.b_d0mem.req.ctrl.addr := m_ld.io.b_mem(0).ctrl.get.addr

  // ------------------------------
  //             QUEUE
  // ------------------------------
  if (p.useDome) m_mem(0).io.b_hart.get <> io.b_hart.get
  m_mem(0).io.i_flush := false.B
  m_mem(0).io.i_br_up := io.i_br_up
  m_mem(0).io.i_br_new := io.i_br_new

  m_mem(0).io.b_fwd := DontCare

  m_mem(0).io.b_in.valid := m_ld.io.b_mem(0).valid & (~m_ld.io.b_mem(0).ctrl.get.fwd.asUInt.orR | ~r_fwd_av(0)) & io.b_d0mem.req.ready(0)
  m_mem(0).io.b_in.ctrl.get.br := m_ld.io.b_mem(0).ctrl.get.br
  m_mem(0).io.b_in.ctrl.get.abort := io.i_br_new.valid & m_ld.io.b_mem(0).ctrl.get.br.mask(io.i_br_new.tag)
  m_mem(0).io.b_in.ctrl.get.addr := DontCare
  m_mem(0).io.b_in.ctrl.get.id := DontCare
  m_mem(0).io.b_in.ctrl.get.ctrl := m_ld.io.b_mem(0).ctrl.get.ctrl
  if (!p.useExtA) m_mem(0).io.b_in.ctrl.get.ctrl.uop := LSUUOP.R
  m_mem(0).io.b_in.ctrl.get.tag := m_ld.io.b_mem(0).ctrl.get.tag
  m_mem(0).io.b_in.ctrl.get.entry := m_ld.io.b_mem(0).ctrl.get.entry
  m_mem(0).io.b_in.ctrl.get.rdp := m_ld.io.b_mem(0).ctrl.get.rdp
  m_mem(0).io.b_in.data.get := DontCare

  m_mem(0).io.b_out.id := DontCare
  m_mem(0).io.b_out.ready := (m_end(0).io.b_in.ready | m_mem(0).io.b_out.ctrl.abort) & ~w_wait_d0_ack

  // ------------------------------
  //            FORWARD
  // ------------------------------
  w_wait_d0_fwd := m_ld.io.b_mem(0).valid & m_ld.io.b_mem(0).ctrl.get.fwd.asUInt.orR & r_fwd_av(0) & (~m_fwd(0).io.b_in.ready | ~(m_st.io.b_fwd(0).ready | m_mem(1).io.b_fwd(0).ready))

  m_st.io.b_fwd(0).mask := m_ld.io.b_mem(0).ctrl.get.fwd
  m_mem(1).io.b_fwd(0).mask := m_ld.io.b_mem(0).ctrl.get.fwd

  m_fwd(0).io.i_flush := io.i_flush | w_hart_flush | (io.i_br_new.valid & m_fwd(0).io.o_val.ctrl.get.br.mask(io.i_br_new.tag))
  m_fwd(0).io.i_br_up := io.i_br_up

  m_fwd(0).io.b_in.valid := ~w_hart_flush & m_ld.io.b_mem(0).valid & m_ld.io.b_mem(0).ctrl.get.fwd.asUInt.orR & r_fwd_av(0) & (m_st.io.b_fwd(0).ready | m_mem(1).io.b_fwd(0).ready)
  m_fwd(0).io.b_in.ctrl.get := DontCare
  m_fwd(0).io.b_in.ctrl.get.ctrl := m_ld.io.b_mem(0).ctrl.get.ctrl
  m_fwd(0).io.b_in.ctrl.get.ctrl.uop := LSUUOP.R
  m_fwd(0).io.b_in.ctrl.get.tag := m_ld.io.b_mem(0).ctrl.get.tag
  m_fwd(0).io.b_in.ctrl.get.br := m_ld.io.b_mem(0).ctrl.get.br
  m_fwd(0).io.b_in.ctrl.get.entry := m_ld.io.b_mem(0).ctrl.get.entry
  m_fwd(0).io.b_in.ctrl.get.rdp := m_ld.io.b_mem(0).ctrl.get.rdp
  m_fwd(0).io.b_in.data.get := Mux(m_st.io.b_fwd(0).ready, m_st.io.b_fwd(0).data, m_mem(1).io.b_fwd(0).data)

  when (r_fwd_av(0)) {
    r_fwd_av(0) := ~(m_ld.io.b_mem(0).valid & m_ld.io.b_mem(0).ctrl.get.fwd.asUInt.orR & ~m_st.io.b_fwd(0).ready & ~m_mem(1).io.b_fwd(0).ready)
  }.otherwise {
    r_fwd_av(0) := m_mem(0).io.b_in.ready & io.b_d0mem.req.ready(0)
  }

  m_fwd(0).io.b_out.ready := m_end(0).io.b_in.ready & ~(m_mem(0).io.b_out.valid & io.b_d0mem.read.valid)

  // ------------------------------
  //             ACK
  // ------------------------------
  w_wait_d0_ack := ~io.b_d0mem.read.valid

  io.b_d0mem.read.ready(0) := m_end(0).io.b_in.ready & m_mem(0).io.b_out.valid

  io.b_d0mem.write.valid := false.B
  if (p.useDome) io.b_d0mem.write.dome.get := io.b_hart.get.dome
  io.b_d0mem.write.data := DontCare

  // ------------------------------
  //             END
  // ------------------------------
  m_end(0).io.i_flush := io.i_flush | w_hart_flush | (io.i_br_new.valid & m_end(0).io.o_val.ctrl.get.br.mask(io.i_br_new.tag))
  m_end(0).io.i_br_up := io.i_br_up

  m_end(0).io.b_in.valid := ~w_hart_flush & ((m_mem(0).io.b_out.valid & ~m_mem(0).io.b_out.ctrl.abort & ~w_wait_d0_ack) | m_fwd(0).io.b_out.valid)

  when (m_mem(0).io.b_out.valid & io.b_d0mem.read.valid) {
    m_end(0).io.b_in.ctrl.get.br := m_mem(0).io.b_out.ctrl.br
    m_end(0).io.b_in.ctrl.get.ctrl := m_mem(0).io.b_out.ctrl.ctrl
    m_end(0).io.b_in.ctrl.get.tag := m_mem(0).io.b_out.ctrl.tag
    m_end(0).io.b_in.ctrl.get.entry := m_mem(0).io.b_out.ctrl.entry
    m_end(0).io.b_in.ctrl.get.rdp := m_mem(0).io.b_out.ctrl.rdp
    m_end(0).io.b_in.data.get := io.b_d0mem.read.data
  }.otherwise {
    m_end(0).io.b_in.ctrl.get.br := m_fwd(0).io.b_out.ctrl.get.br
    m_end(0).io.b_in.ctrl.get.ctrl := m_fwd(0).io.b_out.ctrl.get.ctrl
    m_end(0).io.b_in.ctrl.get.tag := m_fwd(0).io.b_out.ctrl.get.tag
    m_end(0).io.b_in.ctrl.get.entry := m_fwd(0).io.b_out.ctrl.get.entry
    m_end(0).io.b_in.ctrl.get.rdp := m_fwd(0).io.b_out.ctrl.get.rdp
    m_end(0).io.b_in.data.get := m_fwd(0).io.b_out.data.get
  }

  m_end(0).io.b_out.ready := io.b_write(0).ready

  // ******************************
  //         MEMORY PORT 1
  // ******************************
  m_ld.io.b_mem(1).ready := ~w_wait_d1_req & ~w_wait_d1_fwd & ~m_st.io.b_mem.valid
  m_st.io.b_mem.ready := ~w_wait_d1_req & ~w_wait_d1_fwd

  // ------------------------------
  //              REQ
  // ------------------------------
  w_wait_d1_req := (m_st.io.b_mem.valid & ~io.b_d1mem.req.ready(0)) | (~m_st.io.b_mem.valid & (~m_ld.io.b_mem(1).ctrl.get.fwd.asUInt.orR | ~r_fwd_av(1)) & ~(m_mem(1).io.b_in.ready & io.b_d1mem.req.ready(0)))

  io.b_d1mem.req.valid := m_mem(1).io.b_in.ready & (m_st.io.b_mem.valid | (m_ld.io.b_mem(1).valid & (~m_ld.io.b_mem(1).ctrl.get.fwd.asUInt.orR | ~r_fwd_av(1))))
  if (p.useDome) io.b_d1mem.req.dome.get := io.b_hart.get.dome
  io.b_d1mem.req.ctrl.hart := 0.U
  if (p.useExtA) io.b_d1mem.req.ctrl.amo.get := m_st.io.b_mem.ctrl.get.ctrl.amo
  when (m_st.io.b_mem.valid) {
    io.b_d1mem.req.ctrl.size := LSUSIZE.toMb4s(m_st.io.b_mem.ctrl.get.ctrl.size)
    io.b_d1mem.req.ctrl.addr := m_st.io.b_mem.ctrl.get.addr
    if (p.useExtA) {
      io.b_d1mem.req.ctrl.op := m_st.io.b_mem.ctrl.get.ctrl.uop
    } else {
      io.b_d1mem.req.ctrl.op := LSUUOP.W
    }
  }.otherwise {
    io.b_d1mem.req.ctrl.size := LSUSIZE.toMb4s(m_ld.io.b_mem(1).ctrl.get.ctrl.size)
    io.b_d1mem.req.ctrl.addr := m_ld.io.b_mem(1).ctrl.get.addr
    if (p.useExtA) {
      io.b_d1mem.req.ctrl.op := m_ld.io.b_mem(1).ctrl.get.ctrl.uop
    } else {
      io.b_d1mem.req.ctrl.op := LSUUOP.R
    }
  }

  // ------------------------------
  //             QUEUE
  // ------------------------------
  if (p.useDome) m_mem(1).io.b_hart.get <> io.b_hart.get
  m_mem(1).io.i_flush := false.B
  m_mem(1).io.i_br_up := io.i_br_up
  m_mem(1).io.i_br_new := io.i_br_new

  m_mem(1).io.b_in.valid := (m_st.io.b_mem.valid | (m_ld.io.b_mem(1).valid & (~m_ld.io.b_mem(1).ctrl.get.fwd.asUInt.orR | ~r_fwd_av(1)) & io.b_d1mem.req.ready(0))) & io.b_d1mem.req.ready(0)
  when (m_st.io.b_mem.valid) {
    m_mem(1).io.b_in.ctrl.get.br := m_st.io.b_mem.ctrl.get.br
    m_mem(1).io.b_in.ctrl.get.abort := io.i_br_new.valid & m_st.io.b_mem.ctrl.get.br.mask(io.i_br_new.tag)
    m_mem(1).io.b_in.ctrl.get.id := DontCare
    m_mem(1).io.b_in.ctrl.get.addr := m_st.io.b_mem.ctrl.get.addr
    m_mem(1).io.b_in.ctrl.get.ctrl := m_st.io.b_mem.ctrl.get.ctrl
    if (!p.useExtA) m_mem(1).io.b_in.ctrl.get.ctrl.uop := LSUUOP.W
    m_mem(1).io.b_in.ctrl.get.tag := m_st.io.b_mem.ctrl.get.tag
    m_mem(1).io.b_in.ctrl.get.entry := m_st.io.b_mem.ctrl.get.entry
    m_mem(1).io.b_in.ctrl.get.rdp := m_st.io.b_mem.ctrl.get.rdp
    m_mem(1).io.b_in.data.get := m_st.io.b_mem.data.get
  }.otherwise {
    m_mem(1).io.b_in.ctrl.get.br := m_ld.io.b_mem(1).ctrl.get.br
    m_mem(1).io.b_in.ctrl.get.abort := io.i_br_new.valid & m_ld.io.b_mem(1).ctrl.get.br.mask(io.i_br_new.tag)
    m_mem(1).io.b_in.ctrl.get.id := DontCare
    m_mem(1).io.b_in.ctrl.get.addr := DontCare
    m_mem(1).io.b_in.ctrl.get.ctrl := m_ld.io.b_mem(1).ctrl.get.ctrl
    if (!p.useExtA) m_mem(1).io.b_in.ctrl.get.ctrl.uop := LSUUOP.R
    m_mem(1).io.b_in.ctrl.get.tag := m_ld.io.b_mem(1).ctrl.get.tag
    m_mem(1).io.b_in.ctrl.get.entry := m_ld.io.b_mem(1).ctrl.get.entry
    m_mem(1).io.b_in.ctrl.get.rdp := m_ld.io.b_mem(1).ctrl.get.rdp
    m_mem(1).io.b_in.data.get := DontCare
  }

  m_mem(1).io.b_out.id := DontCare
  m_mem(1).io.b_out.ready := ~w_wait_d1_ack & (~m_mem(1).io.b_out.ctrl.ctrl.ld | m_end(1).io.b_in.ready | m_mem(1).io.b_out.ctrl.abort)
  
  // ------------------------------
  //            FORWARD
  // ------------------------------
  w_wait_d1_fwd := ~m_st.io.b_mem.valid & m_ld.io.b_mem(1).valid & m_ld.io.b_mem(1).ctrl.get.fwd.asUInt.orR & r_fwd_av(1) & (~m_fwd(1).io.b_in.ready | ~(m_st.io.b_fwd(1).ready | m_mem(1).io.b_fwd(1).ready))

  m_st.io.b_fwd(1).mask := m_ld.io.b_mem(1).ctrl.get.fwd
  m_mem(1).io.b_fwd(1).mask := m_ld.io.b_mem(1).ctrl.get.fwd

  m_fwd(1).io.i_flush := io.i_flush | w_hart_flush | (io.i_br_new.valid & m_fwd(1).io.o_val.ctrl.get.br.mask(io.i_br_new.tag))
  m_fwd(1).io.i_br_up := io.i_br_up

  m_fwd(1).io.b_in.valid := ~w_hart_flush & ~m_st.io.b_mem.valid & m_ld.io.b_mem(1).valid & m_ld.io.b_mem(1).ctrl.get.fwd.asUInt.orR & r_fwd_av(1) & (m_st.io.b_fwd(1).ready | m_mem(1).io.b_fwd(1).ready)
  m_fwd(1).io.b_in.ctrl.get := DontCare
  m_fwd(1).io.b_in.ctrl.get.ctrl := m_ld.io.b_mem(1).ctrl.get.ctrl
  m_fwd(1).io.b_in.ctrl.get.ctrl.uop := LSUUOP.R
  m_fwd(1).io.b_in.ctrl.get.tag := m_ld.io.b_mem(1).ctrl.get.tag
  m_fwd(1).io.b_in.ctrl.get.br := m_ld.io.b_mem(1).ctrl.get.br
  m_fwd(1).io.b_in.ctrl.get.entry := m_ld.io.b_mem(1).ctrl.get.entry
  m_fwd(1).io.b_in.ctrl.get.rdp := m_ld.io.b_mem(1).ctrl.get.rdp
  m_fwd(1).io.b_in.data.get := Mux(m_st.io.b_fwd(1).ready, m_st.io.b_fwd(1).data, m_mem(1).io.b_fwd(1).data)

  when (r_fwd_av(1)) {
    r_fwd_av(1) := ~(m_st.io.b_mem.valid & m_ld.io.b_mem(1).valid & m_ld.io.b_mem(1).ctrl.get.fwd.asUInt.orR & ~m_st.io.b_fwd(1).ready & ~m_mem(1).io.b_fwd(1).ready)
  }.otherwise {
    r_fwd_av(1) := m_mem(1).io.b_in.ready & io.b_d1mem.req.ready(0)
  }

  m_fwd(1).io.b_out.ready := m_end(1).io.b_in.ready & ~(m_mem(1).io.b_out.valid & io.b_d1mem.read.valid)

  // ------------------------------
  //             ACK
  // ------------------------------
  val m_rd1mem = if (p.useExtA) Some(Module(new Mb4sDataSReg(p.pL0D1Bus))) else None
  val r_wd1mem = RegInit(true.B)

  val w_d1_rvalid = Wire(Bool())

  if (p.useExtA) {
    w_d1_rvalid := m_rd1mem.get.io.b_sout.valid
    w_wait_d1_ack := (m_mem(1).io.b_out.ctrl.ctrl.ld & ~m_rd1mem.get.io.b_sout.valid) | (m_mem(1).io.b_out.ctrl.ctrl.st & ~io.b_d1mem.write.ready(0) & r_wd1mem)

    m_rd1mem.get.io.b_port <> io.b_d1mem.read
    m_rd1mem.get.io.b_sout.ready := m_end(1).io.b_in.ready & m_mem(1).io.b_out.valid & m_mem(1).io.b_out.ctrl.ctrl.ld

    io.b_d1mem.write.valid := m_mem(1).io.b_out.valid & m_mem(1).io.b_out.ctrl.ctrl.st & r_wd1mem
    if (p.useDome) io.b_d1mem.write.dome.get := io.b_hart.get.dome
    io.b_d1mem.write.data := m_mem(1).io.b_out.data

    when (m_mem(1).io.b_out.valid & m_mem(1).io.b_out.ctrl.ctrl.a) {      
      when (r_wd1mem) {
        r_wd1mem := (m_rd1mem.get.io.b_sout.valid & m_end(1).io.b_in.ready) | ~io.b_d1mem.write.ready(0)
      }.otherwise {
        r_wd1mem := (m_rd1mem.get.io.b_sout.valid & m_end(1).io.b_in.ready)
      }
    }
  } else {
    w_d1_rvalid := io.b_d1mem.read.valid
    w_wait_d1_ack := (m_mem(1).io.b_out.ctrl.ctrl.ld & ~io.b_d1mem.read.valid) | (m_mem(1).io.b_out.ctrl.ctrl.st & ~io.b_d1mem.write.ready(0))

    io.b_d1mem.read.ready(0) := m_end(1).io.b_in.ready & m_mem(1).io.b_out.valid & m_mem(1).io.b_out.ctrl.ctrl.ld

    io.b_d1mem.write.valid := m_mem(1).io.b_out.valid & m_mem(1).io.b_out.ctrl.ctrl.st
    if (p.useDome) io.b_d1mem.write.dome.get := io.b_hart.get.dome
    io.b_d1mem.write.data := m_mem(1).io.b_out.data
  }

  // ------------------------------
  //             END
  // ------------------------------
  m_end(1).io.i_flush := io.i_flush | w_hart_flush | (io.i_br_new.valid & m_end(1).io.o_val.ctrl.get.br.mask(io.i_br_new.tag))
  m_end(1).io.i_br_up := io.i_br_up

  m_end(1).io.b_in.valid := ~w_hart_flush & ((m_mem(1).io.b_out.valid & m_mem(1).io.b_out.ctrl.ctrl.ld & ~m_mem(1).io.b_out.ctrl.abort & ~w_wait_d1_ack) | m_fwd(1).io.b_out.valid)

  when (m_mem(1).io.b_out.valid & m_mem(1).io.b_out.ctrl.ctrl.ld & w_d1_rvalid) {
    m_end(1).io.b_in.ctrl.get.br := m_mem(1).io.b_out.ctrl.br
    m_end(1).io.b_in.ctrl.get.ctrl := m_mem(1).io.b_out.ctrl.ctrl
    m_end(1).io.b_in.ctrl.get.tag := m_mem(1).io.b_out.ctrl.tag
    m_end(1).io.b_in.ctrl.get.entry := m_mem(1).io.b_out.ctrl.entry
    m_end(1).io.b_in.ctrl.get.rdp := m_mem(1).io.b_out.ctrl.rdp
    if (p.useExtA) {
      m_end(1).io.b_in.data.get := m_rd1mem.get.io.b_sout.data.get
    } else {
      m_end(1).io.b_in.data.get := io.b_d1mem.read.data
    }
    
  }.otherwise {
    m_end(1).io.b_in.ctrl.get.br := m_fwd(1).io.b_out.ctrl.get.br
    m_end(1).io.b_in.ctrl.get.ctrl := m_fwd(1).io.b_out.ctrl.get.ctrl
    m_end(1).io.b_in.ctrl.get.tag := m_fwd(1).io.b_out.ctrl.get.tag
    m_end(1).io.b_in.ctrl.get.entry := m_fwd(1).io.b_out.ctrl.get.entry
    m_end(1).io.b_in.ctrl.get.rdp := m_fwd(1).io.b_out.ctrl.get.rdp
    m_end(1).io.b_in.data.get := m_fwd(1).io.b_out.data.get
  }

  w_st_end.valid := m_mem(1).io.b_out.valid & m_mem(1).io.b_out.ctrl.ctrl.st & r_wd1mem & io.b_d1mem.write.ready(0)
  w_st_end.ctrl.get := DontCare
  w_st_end.ctrl.get.addr := m_mem(1).io.b_out.ctrl.addr
  w_st_end.ctrl.get.tag := m_mem(1).io.b_out.ctrl.tag
  w_st_end.ctrl.get.size := m_mem(1).io.b_out.ctrl.ctrl.size

  m_end(1).io.b_out.ready := io.b_write(1).ready

  // ******************************
  //              END
  // ******************************
  // ------------------------------
  //             DATA
  // ------------------------------
  for (l <- 0 until p.nLoad) {
    w_ld_data(l) := m_end(l).io.b_out.data.get

    switch(m_end(l).io.b_out.ctrl.get.ctrl.size) {
      is (LSUSIZE.B) {
        when (m_end(l).io.b_out.ctrl.get.ctrl.sign === LSUSIGN.S) {
          w_ld_data(l) := Cat(Fill(p.nDataBit - 8, m_end(l).io.b_out.data.get(7)), m_end(l).io.b_out.data.get(7,0))
        }.otherwise {
          w_ld_data(l) := Cat(Fill(p.nDataBit - 8, 0.B), m_end(l).io.b_out.data.get(7,0))
        }
      }
      is (LSUSIZE.H) {
        when (m_end(l).io.b_out.ctrl.get.ctrl.sign === LSUSIGN.S) {
          w_ld_data(l) := Cat(Fill(p.nDataBit - 16, m_end(l).io.b_out.data.get(15)), m_end(l).io.b_out.data.get(15,0))
        }.otherwise {
          w_ld_data(l) := Cat(Fill(p.nDataBit - 16, 0.B), m_end(l).io.b_out.data.get(15,0))
        }
      }
      is (LSUSIZE.W) {
        if (p.nDataBit >= 64) {
          when (m_end(l).io.b_out.ctrl.get.ctrl.sign === LSUSIGN.S) {
            w_ld_data(l) := Cat(Fill(p.nDataBit - 32, m_end(l).io.b_out.data.get(31)), m_end(l).io.b_out.data.get(31,0))
          }.otherwise {
            w_ld_data(l) := Cat(Fill(p.nDataBit - 32, 0.B), m_end(l).io.b_out.data.get(31,0))
          }
        }
      }
    }
  }

  // ------------------------------
  //             I/Os
  // ------------------------------
  for (l <- 0 until p.nLoad) {
    w_ld_done(l).valid := m_end(l).io.b_out.valid & io.b_write(l).ready & m_end(l).io.b_out.ctrl.get.ctrl.ldo
    w_ld_done(l).tag := m_end(l).io.b_out.ctrl.get.tag

    io.o_byp(l).valid := m_end(l).io.b_out.valid
    io.o_byp(l).done := true.B
    io.o_byp(l).rdp := m_end(l).io.b_out.ctrl.get.rdp
    io.o_byp(l).data := w_ld_data(l)

    io.b_write(l).valid := m_end(l).io.b_out.valid
    io.b_write(l).rdp := m_end(l).io.b_out.ctrl.get.rdp
    io.b_write(l).data := w_ld_data(l)
  }

  // ******************************
  //             END
  // ******************************
  m_ld.io.b_end(0) <> io.b_end(0)
  when (m_st.io.b_end.valid) {
    m_st.io.b_end <> io.b_end(1)
    m_ld.io.b_end(1).ready := false.B
  }.otherwise {
    m_st.io.b_end.ready := false.B
    m_ld.io.b_end(1) <> io.b_end(1)
  }

  // ******************************
  //             DOME
  // ******************************
  if (p.useDome) {
    val w_hart_free = Wire(Vec(9, Bool()))

    w_hart_free(0) := m_st.io.b_hart.get.free
    w_hart_free(1) := m_ld.io.b_hart.get.free
    w_hart_free(2) := m_ex.io.b_hart.get.free
    w_hart_free(3) := m_mem(0).io.b_hart.get.free
    w_hart_free(4) := ~m_fwd(0).io.o_val.valid
    w_hart_free(5) := ~m_end(0).io.o_val.valid
    w_hart_free(6) := m_mem(1).io.b_hart.get.free
    w_hart_free(7) := ~m_fwd(1).io.o_val.valid
    w_hart_free(8) := ~m_end(1).io.o_val.valid

    io.b_hart.get.free := w_hart_free.asUInt.andR
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
      m_st.io.b_in(bp).ctrl.get.etd.get := io.b_in(bp).ctrl.get.etd.get
      m_ld.io.b_in(bp).ctrl.get.etd.get := io.b_in(bp).ctrl.get.etd.get
    }

    m_fwd(0).io.b_in.ctrl.get.etd.get := m_ld.io.b_mem(0).ctrl.get.etd.get
    m_mem(0).io.b_in.ctrl.get.etd.get := m_ld.io.b_mem(0).ctrl.get.etd.get
    m_fwd(1).io.b_in.ctrl.get.etd.get := m_ld.io.b_mem(1).ctrl.get.etd.get
    when (m_st.io.b_mem.valid) {
      m_mem(1).io.b_in.ctrl.get.etd.get := m_st.io.b_mem.ctrl.get.etd.get
    }.otherwise {
      m_mem(1).io.b_in.ctrl.get.etd.get := m_ld.io.b_mem(1).ctrl.get.etd.get
    }

    for (l <- 0 until p.nLoad) {
      when (m_mem(l).io.b_out.valid) {
        m_end(l).io.b_in.ctrl.get.etd.get := m_mem(l).io.b_out.ctrl.etd.get
      }.otherwise {
        m_end(l).io.b_in.ctrl.get.etd.get := m_fwd(l).io.b_out.ctrl.get.etd.get
      }
      dontTouch(m_end(l).io.b_out.ctrl.get.etd.get)
    }
  }
}

object Lsu extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new Lsu(LsuConfigBase), args)
}
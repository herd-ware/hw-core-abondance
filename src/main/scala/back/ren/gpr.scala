/*
 * File: gpr.scala                                                             *
 * Created Date: 2023-02-26 09:21:29 am                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-03-02 12:12:28 pm                                       *
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

import herd.common.isa.riscv._
import herd.common.field._
import herd.core.abondance.common._


class GprRMux (p: BackParams) extends Module {
  val io = IO(new Bundle {
    val b_log = Vec(p.nGprReadLog, new GprReadIO(p.nDataBit, p.nGprPhy))
    val i_byp = Input(Vec(p.nBypass, new BypassBus(p.nDataBit, p.nGprPhy)))
    val b_phy = Flipped(Vec(p.nGprReadPhy, new GprReadIO(p.nDataBit, p.nGprPhy)))
  })

  // ******************************
  //        READ PHYSICAL GPR
  // ******************************
  // ------------------------------
  //   DIRECT CONNECT (LOG == PHY)
  // ------------------------------
  if (p.nGprReadLog == p.nGprReadPhy) {
    io.b_log <> io.b_phy
    
  // ------------------------------
  //        MUX (LOG > PHY)
  // ------------------------------
  } else {
    val w_av = Wire(Vec(p.nGprReadLog, Vec(p.nGprReadPhy, Bool())))
    val w_done = Wire(Vec(p.nGprReadLog, Bool()))
    val w_slct = Wire(Vec(p.nGprReadLog, UInt(log2Ceil(p.nGprReadPhy).W)))

    // Default
    for (rl <- 0 until p.nGprReadLog) {
      io.b_log(rl).ready := false.B
      io.b_log(rl).data := DontCare
      w_done(rl) := false.B
      w_slct(rl) := 0.U
    }

    for (rp <- 0 until p.nGprReadPhy) {
      w_av(0)(rp) := true.B
      io.b_phy(rp).valid := false.B
      io.b_phy(rp).rsp := DontCare
    }

    // Select
    when (io.b_log(0).valid) {
      w_done(0) := true.B
      w_slct(0) := 0.U
    }

    for (rl <- 1 until p.nGprReadLog) {
      w_av(rl) := w_av(rl - 1)

      when (w_done(rl - 1)) {
        w_av(rl)(w_slct(rl -1)) := false.B
      }

      w_done(rl) := io.b_log(rl).valid & w_av(rl).asUInt.orR
      w_slct(rl) := PriorityEncoder(w_av(rl).asUInt)
    }

    for (rl <- 0 until p.nGprReadLog) {
      when (w_done(rl)) {
        io.b_phy(w_slct(rl)) <> io.b_log(rl)
      }
    }
  }

  // ******************************
  //            BYPASS
  // ******************************
  for (rl <- 0 until p.nGprReadLog) {
    for (b <- 0 until p.nBypass) {
      when (io.i_byp(b).valid & (io.i_byp(b).rdp === io.b_log(rl).rsp)) {
        io.b_log(rl).ready := io.i_byp(b).done
        io.b_log(rl).data := io.i_byp(b).data
      }
    }
  }
}

class GprWMux(p: BackParams) extends Module {
  val io = IO(new Bundle {
    val b_log = Vec(p.nGprWriteLog, new GprWriteIO(p.nDataBit, p.nGprPhy))
    val b_phy = Flipped(Vec(p.nGprWritePhy, new GprWriteIO(p.nDataBit, p.nGprPhy)))
  })

  // ******************************
  //   DIRECT CONNECT (LOG == PHY)
  // ******************************
  if (p.nGprWriteLog == p.nGprWritePhy) {
    io.b_log <> io.b_phy

  // ******************************
  //        MUX (LOG > PHY)
  // ******************************
  } else {
    val w_av = Wire(Vec(p.nGprWriteLog, Vec(p.nGprWritePhy, Bool())))
    val w_done = Wire(Vec(p.nGprWriteLog, Bool()))
    val w_slct = Wire(Vec(p.nGprWriteLog, UInt(log2Ceil(p.nGprWritePhy).W)))

    // Default
    for (wl <- 0 until p.nGprWriteLog) {
      io.b_log(wl).ready := false.B
      w_done(wl) := false.B
      w_slct(wl) := 0.U
    }

    for (wp <- 0 until p.nGprWritePhy) {
      w_av(0)(wp) := true.B
      io.b_phy(wp).valid := false.B
      io.b_phy(wp).rdp := DontCare
      io.b_phy(wp).data := DontCare
    }

    // Select
    when (io.b_log(0).valid) {
      w_done(0) := true.B
      w_slct(0) := 0.U
    }

    for (wl <- 1 until p.nGprWriteLog) {
      w_av(wl) := w_av(wl - 1)

      when (w_done(wl - 1)) {
        w_av(wl)(w_slct(wl -1)) := false.B
      }

      w_done(wl) := io.b_log(wl).valid & w_av(wl).asUInt.orR
      w_slct(wl) := PriorityEncoder(w_av(wl).asUInt)
    }

    for (wl <- 0 until p.nGprWriteLog) {
      when (w_done(wl)) {
        io.b_phy(w_slct(wl)) <> io.b_log(wl)
      }
    }
  }
}

class Gpr(p: BackParams) extends Module {
  val io = IO(new Bundle {
    val b_hart = if (p.useField) Some(new RsrcIO(p.nHart, p.nField, 1)) else None

    val b_map = Vec(p.nBackPort, Vec(2, new MapIO(p)))
    val b_remap = Vec(p.nBackPort, new RemapIO(p))
    
    val i_br_act = Input(UInt(p.nSpecBranch.W))
    val i_br_new = Input(new BranchBus(32, p.nSpecBranch, p.nRobEntry))
    val b_busy = Vec(p.nBackPort, Vec(2, new GprReadIO(p.nDataBit, p.nGprPhy)))
    val i_commit = Input(Vec(p.nCommit, new CommitBus(p.nRobEntry, p.nGprLog, p.nGprPhy)))
    val i_init = Input(Bool())

    val i_byp = Input(Vec(p.nBypass, new BypassBus(p.nDataBit, p.nGprPhy)))
    val b_read = Vec(p.nGprReadLog, new GprReadIO(p.nDataBit, p.nGprPhy))
    val b_write = Vec(p.nGprWriteLog, new GprWriteIO(p.nDataBit, p.nGprPhy))

    val o_dbg = if (p.debug) Some(Output(Vec(p.nGprLog, UInt(p.nDataBit.W)))) else None
  })

  // ******************************
  //           INITIALIZE
  // ******************************
  // GPR  
  val r_gpr = Reg(Vec(p.nGprPhy, UInt(p.nDataBit.W)))

  // Renaming registers
  val init_map_commit = Wire(Vec(p.nGprLog, UInt(log2Ceil(p.nGprPhy).W)))
  val init_map_spec = Wire(Vec(p.nGprLog, UInt(log2Ceil(p.nGprPhy).W)))

  init_map_commit(0) := 0.U
  init_map_spec(0) := 0.U  
  for (gl <- 1 until p.nGprLog) {
    init_map_commit(gl) := 0.U
    init_map_spec(gl) := DontCare 
  }

  val r_map_commit = RegInit(init_map_commit)
  val r_map_spec = RegInit(init_map_spec)
  val r_map_br = Reg(Vec(p.nSpecBranch, Vec(p.nGprLog, UInt(log2Ceil(p.nGprPhy).W))))

  // State registers
  val init_busy = Wire(Vec(p.nGprPhy, Bool()))
  val init_free = Wire(Vec(p.nGprPhy, Bool()))

  init_busy(0) := false.B
  init_free(0) := false.B
  for (gp <- 1 until p.nGprPhy) {
    init_busy(gp) := false.B
    init_free(gp) := true.B
  }

  val r_busy = RegInit(init_busy)
  val r_free_spec = RegInit(init_free)
  val r_free_commit = RegInit(init_free)
  val r_br_alloc  = Reg(Vec(p.nSpecBranch, Vec(p.nGprPhy, Bool())))

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
  //       PHYSICAL REGISTERS
  // ******************************
  // -----------------------------
  //         BYPASS & MUX
  // -----------------------------
  val m_rmux = Module(new GprRMux(p))
  val m_wmux = Module(new GprWMux(p))

  m_rmux.io.i_byp := io.i_byp
  m_rmux.io.b_log <> io.b_read
  m_wmux.io.b_log <> io.b_write

  // -----------------------------
  //            WRITE
  // -----------------------------
  for (wp <- 0 until p.nGprWritePhy) {
    m_wmux.io.b_phy(wp).ready := true.B
    when (m_wmux.io.b_phy(wp).valid) {
      r_gpr(m_wmux.io.b_phy(wp).rdp) := m_wmux.io.b_phy(wp).data
    }
  }

  // -----------------------------
  //             READ
  // -----------------------------
  for (rp <- 0 until p.nGprReadPhy) {
    m_rmux.io.b_phy(rp).ready := ~r_busy(m_rmux.io.b_phy(rp).rsp)
    m_rmux.io.b_phy(rp).data := r_gpr(m_rmux.io.b_phy(rp).rsp)
  }

  // ******************************
  //            RENAMING
  // ******************************
  // ------------------------------
  //           REMAPPING
  // ------------------------------
  val w_remap_req = Wire(Vec(p.nBackPort, Bool()))
  val w_remap_av = Wire(Vec(p.nBackPort, Bool()))
  val w_remap_rdp = Wire(Vec(p.nBackPort, UInt(log2Ceil(p.nGprPhy).W)))

  val w_remap_map = Wire(Vec(p.nBackPort, Vec(p.nGprLog, UInt(log2Ceil(p.nGprPhy).W))))
  val w_remap_free = Wire(Vec(p.nBackPort, Vec(p.nGprPhy, Bool())))
  
  val w_remap_wait = Wire(Vec(p.nBackPort, Bool()))

  //First remap port
  w_remap_req(0) := io.b_remap(0).valid & io.b_remap(0).alloc
  w_remap_av(0) := Mux((io.b_remap(0).rdl === REG.X0.U), true.B, r_free_spec.asUInt.orR)
  w_remap_rdp(0) := Mux((io.b_remap(0).rdl === REG.X0.U), 0.U, PriorityEncoder(r_free_spec.asUInt))

  w_remap_map(0) := r_map_spec
  w_remap_free(0) := r_free_spec
  when (w_remap_req(0) & w_remap_av(0)) {
    w_remap_map(0)(io.b_remap(0).rdl) := w_remap_rdp(0)
    w_remap_free(0)(w_remap_rdp(0)) := false.B
  }

  // Other remap ports
  for (bp <- 1 until p.nBackPort) {
    w_remap_req(bp) := io.b_remap(bp).valid & io.b_remap(bp).alloc
    w_remap_av(bp) := Mux((io.b_remap(bp).rdl === REG.X0.U), true.B, w_remap_free(bp - 1).asUInt.orR)
    w_remap_rdp(bp) := Mux((io.b_remap(bp).rdl === REG.X0.U), 0.U, PriorityEncoder(w_remap_free(bp - 1).asUInt))

    w_remap_map(bp) := w_remap_map(bp - 1)
    w_remap_free(bp) := w_remap_free(bp - 1)
    when (w_remap_req(bp) & w_remap_av(bp)) {
      w_remap_map(bp)(io.b_remap(bp).rdl) := w_remap_rdp(bp)
      w_remap_free(bp)(w_remap_rdp(bp)) := false.B
    }
  }

  // Wait and connect outputs
  for (bp <- 0 until p.nBackPort) {
    w_remap_wait(bp) := w_remap_req(bp) & ~w_remap_av(bp)

    io.b_remap(bp).ready := ~w_remap_wait.asUInt.orR
    io.b_remap(bp).rdp := w_remap_rdp(bp)
  }

  // ------------------------------
  //          READ MAPPING
  // ------------------------------
  io.b_map(0)(0).rsp := r_map_spec(io.b_map(0)(0).rsl)
  io.b_map(0)(1).rsp := r_map_spec(io.b_map(0)(1).rsl)

  for (bp <- 1 until p.nBackPort) {
    io.b_map(bp)(0).rsp := w_remap_map(bp - 1)(io.b_map(bp)(0).rsl)
    io.b_map(bp)(1).rsp := w_remap_map(bp - 1)(io.b_map(bp)(1).rsl)
  }

  // ------------------------------
  //            COMMIT
  // ------------------------------
  val w_commit_free = Wire(Vec(p.nGprPhy, Bool()))
  val w_commit_rdp = Wire(Vec(p.nCommit, UInt(log2Ceil(p.nGprPhy).W)))

  for (gp <- 0 until p.nGprPhy) {
    w_commit_free(gp) := false.B
  }

  for (c0 <- 0 until p.nCommit) {
    w_commit_rdp(c0) := r_map_commit(io.i_commit(c0).rdl)
    for (c1 <- 0 until c0) {
      when (io.i_commit(c1).valid & (io.i_commit(c0).rdl === io.i_commit(c1).rdl)) {
        w_commit_rdp(c0) := io.i_commit(c1).rdp
      }
    }

    when (io.i_commit(c0).valid) {
      r_map_commit(io.i_commit(c0).rdl) := io.i_commit(c0).rdp
      r_free_commit(io.i_commit(c0).rdp) := false.B
      r_free_commit(w_commit_rdp(c0)) := true.B
      w_commit_free(w_commit_rdp(c0)) := true.B
    }
  }

  // ------------------------------
  //        UPDATE REGISTERS
  // ------------------------------
  when (~w_remap_wait.asUInt.orR) {
    r_map_spec := w_remap_map(p.nBackPort - 1)
  }

  when (w_hart_flush | io.i_init) {
    r_map_spec := r_map_commit
    r_free_spec := r_free_commit
//    for (gp <- 0 until p.nGprPhy) {
//      val w_br_alloc = Wire(Vec(p.nSpecBranch, Bool())) 
//      
//      for (sp <- 0 until p.nSpecBranch) {
//        w_br_alloc(sp) := io.i_br_act(sp) & r_br_alloc(sp)(gp)
//      }
//
//      r_free_spec(gp) := r_free_spec(gp) | w_br_alloc.asUInt.orR  
//    }
  }.elsewhen (io.i_br_new.valid) {
    r_map_spec := r_map_br(io.i_br_new.tag)
    for (gp <- 0 until p.nGprPhy) {
      r_free_spec(gp) := r_free_spec(gp) | w_commit_free(gp) | r_br_alloc(io.i_br_new.tag)(gp)
    }    
  }.otherwise {
    for (gp <- 0 until p.nGprPhy) {
      r_free_spec(gp) := w_remap_free(p.nBackPort - 1)(gp) | w_commit_free(gp)
    }
  }

  // ------------------------------
  //             BUSY
  // ------------------------------
  val w_busy = Wire(Vec(p.nGprPhy, Bool()))

  // Write
  w_busy := r_busy

  for (bp <- 0 until p.nBackPort) {
    when (w_remap_req(bp) & ~w_remap_wait.asUInt.orR) {
      w_busy(w_remap_rdp(bp)) := true.B
    }
  }

  for (wp <- 0 until p.nGprWritePhy) {
    when (m_wmux.io.b_phy(wp).valid) {
      w_busy(m_wmux.io.b_phy(wp).rdp) := false.B
    }
  }

  r_busy := w_busy

  // Read
  for (bp <- 0 until p.nBackPort) {
    io.b_busy(bp)(0).ready := ~w_busy(io.b_busy(bp)(0).rsp)
    io.b_busy(bp)(0).data := 0.U
    io.b_busy(bp)(1).ready := ~w_busy(io.b_busy(bp)(1).rsp)
    io.b_busy(bp)(1).data := 0.U
  }

  // ------------------------------
  //            BRANCH 
  // ------------------------------  
  when (~w_remap_wait.asUInt.orR) {
    for (bp <- 0 until p.nBackPort) {   
      // New branch backup & initialization
      when (io.b_remap(bp).valid & io.b_remap(bp).br.valid) {
        r_map_br(io.b_remap(bp).br.tag) := w_remap_map(bp)

        for (gp <- 0 until p.nGprPhy) {
          r_br_alloc(io.b_remap(bp).br.tag)(gp) := false.B
        }
      } 

      // Allocation backup for each branch
      for (sp <- 0 until p.nSpecBranch) {
        when (w_remap_req(bp) & io.b_remap(bp).br.mask(sp)) {
          r_br_alloc(sp)(w_remap_rdp(bp)) := true.B
        }
      }
    }
  }  

  // ******************************
  //              X0
  // ******************************
  r_free_spec(0) := false.B
  r_free_commit(0) := false.B
  for (sp <- 0 until p.nSpecBranch) {
    r_br_alloc(sp)(0) := false.B
  }
  r_gpr(0) := 0.U

  r_busy(0) := false.B
  w_busy(0) := false.B

  // ******************************
  //             FIELD
  // ******************************
  if (p.useField) {
    io.b_hart.get.free := true.B
  }

  // ******************************
  //             DEBUG
  // ******************************
  if (p.debug) {
    // ------------------------------
    //            SIGNALS
    // ------------------------------
    for (gl <- 0 until p.nGprLog) {
      io.o_dbg.get(gl) := r_gpr(r_map_commit(gl))
    }

    dontTouch(r_map_br)
    dontTouch(r_br_alloc)
    dontTouch(r_free_commit)

    // ------------------------------
    //         DATA FOOTPRINT
    // ------------------------------
  }
}

object GprRMux extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new GprRMux(BackConfigBase), args)
}

object GprWMux extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new GprWMux(BackConfigBase), args)
}

object Gpr extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new Gpr(BackConfigBase), args)
}
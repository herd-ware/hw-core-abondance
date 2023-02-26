/*
 * File: rob.scala                                                             *
 * Created Date: 2023-02-26 09:21:29 am                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-26 09:28:33 am                                       *
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

import herd.common.isa.base._
import herd.common.isa.count.{CsrBus => CountBus}
import herd.common.dome._
import herd.core.aubrac.common.{TrapBus,EtdBus}
import herd.core.aubrac.common.{TRAPSRC}
import herd.core.abondance.common._
import herd.io.core.clint.{ClintIO}


class Rob(p: BackParams) extends Module {
  val io = IO(new Bundle {
    val b_hart = if (p.useDome) Some(new RsrcIO(p.nHart, p.nDome, 1)) else None

    val b_write = Vec(p.nBackPort, new RobWriteIO(p))

    val b_pc = Vec(p.nRobPcRead, new RobPcIO(p.nAddrBit, p.nRobEntry))
    val i_br_up = Input(UInt(p.nSpecBranch.W))
    val i_br_new = Input(new BranchBus(p.nAddrBit, p.nSpecBranch, p.nRobEntry))
    val o_br_new = Output(new BranchBus(p.nAddrBit, p.nSpecBranch, p.nRobEntry))
    val b_end = Vec(p.nExUnit, Flipped(new EndIO(p.debug, p.nAddrBit, p.nDataBit, p.nRobEntry)))
    val b_clint = Flipped(new ClintIO(p.nDataBit))

    val o_run = Output(Bool())
    val o_init = Output(Bool())
    val o_commit = Output(Vec(p.nCommit, new CommitBus(p.nRobEntry, p.nGprLog, p.nGprPhy)))
    val o_trap = Output(new TrapBus(p.nAddrBit, p.nDataBit))

    val o_stat = Output(new CountBus())

    val o_dbg = if (p.debug) Some(Output(UInt(p.nAddrBit.W))) else None
    val o_etd = if (p.debug) Some(Output(Vec(p.nCommit, new EtdBus(p.nHart, p.nAddrBit, p.nInstrBit)))) else None
  })

  val init_entry = Wire(Vec(p.nRobEntry, new RobEntryBus(p)))
  for (re <- 0 until p.nRobEntry) {
    init_entry(re) := DontCare
    init_entry(re).valid := false.B
  }

  val r_entry = RegInit(init_entry)
  val r_pt_loop = RegInit(false.B)
  val r_wpt = RegInit(0.U(log2Ceil(p.nRobEntry).W))
  val r_cpt = RegInit(0.U(log2Ceil(p.nRobEntry).W))  
  val r_commit = Reg(UInt(p.nAddrBit.W))

  val w_lock = Wire(Bool())
  val w_init = Wire(Bool())
  val w_commit = Wire(UInt(p.nAddrBit.W))

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
  //             WRITE
  // ******************************
  val w_wfree = Wire(Vec(p.nRobEntry, Bool()))
  val w_wready = Wire(Bool())
  val w_wpt = Wire(Vec(p.nBackPort + 1, UInt(log2Ceil(p.nRobEntry).W)))
  val w_wpt_loop = Wire(Bool())
  
  // ------------------------------
  //            SELECT
  // ------------------------------
  w_wpt(0) := r_wpt
  for (bp <- 0 until p.nBackPort) {
    when (io.b_write(bp).valid) {
      w_wpt(bp + 1) := Mux((w_wpt(bp) === (p.nRobEntry - 1).U), 0.U, w_wpt(bp) + 1.U)
    }.otherwise {
      w_wpt(bp + 1) := w_wpt(bp)
    }
  }

  w_wpt_loop := w_wready & (w_wpt(p.nBackPort) < r_wpt)

  // ------------------------------
  //          READY PORT
  // ------------------------------
  for (re <- 0 until p.nRobEntry) {
    w_wfree(re) := (~r_pt_loop & ((re.U >= r_wpt) | (re.U < r_cpt))) | (r_pt_loop & (re.U < r_cpt) & (re.U >= r_wpt))
  }

  w_wready := (PopCount(w_wfree.asUInt) >= p.nBackPort.U)

  for (bp <- 0 until p.nBackPort) {
    io.b_write(bp).ready := w_wready & ~w_lock
    io.b_write(bp).entry := w_wpt(bp)
  }

  // ------------------------------
  //           UPDATE ROB
  // ------------------------------
  for (bp <- 0 until p.nBackPort) {
    when (w_wready & io.b_write(bp).valid) {
      r_entry(w_wpt(bp)) := io.b_write(bp).data
    }
  }

  // ******************************
  //          END & BUSY
  // ******************************
  for (eu <- 0 until p.nExUnit) {
    io.b_end(eu).ready := true.B
    for (re <- 0 until p.nRobEntry) {
      when (io.b_end(eu).valid & (re.U === io.b_end(eu).entry)) {
        r_entry(re).busy := false.B        
        if (p.useRobReplay) {
          r_entry(re).replay := io.b_end(eu).replay
        } else {
          r_entry(re).replay := false.B
        }
        r_entry(re).stat.br := io.b_end(eu).stat.br
        r_entry(re).stat.mispred := io.b_end(eu).stat.mispred
      }
    }
  }

  // ******************************
  //            BRANCH
  // ******************************
  val w_br_flush = Wire(Vec(p.nRobEntry, Bool()))
  val w_br_mask = Wire(Vec(p.nRobEntry, UInt(p.nSpecBranch.W)))
  val w_br_unloop = Wire(Bool())
  val w_br_pt = Wire(UInt(log2Ceil(p.nRobEntry).W))

  for (re <- 0 until p.nRobEntry) {
    w_br_flush(re) := io.i_br_new.valid & r_entry(re).br_mask(io.i_br_new.tag)
    w_br_mask(re) := r_entry(re).br_mask & io.i_br_up

    when (r_entry(re).valid) {
      r_entry(re).br_mask := w_br_mask(re)
      when (w_br_flush(re)) {
        r_entry(re).valid := false.B
      }
    }
  }

  when (io.i_br_new.entry === (p.nRobEntry - 1).U) {
    w_br_pt := 0.U
   }.otherwise {
    w_br_pt := io.i_br_new.entry + 1.U
  }  

  w_br_unloop := io.i_br_new.valid & (w_br_pt > r_wpt)

  // ******************************
  //            COMMIT
  // ******************************
  val w_cready = Wire(Vec(p.nCommit, Bool()))
  val w_cpt = Wire(Vec(p.nCommit + 1, UInt(log2Ceil(p.nRobEntry).W)))
  val w_cpt_slct = Wire(UInt(log2Ceil(p.nRobEntry).W))
  val w_cpt_loop = Wire(Vec(p.nCommit + 1, Bool()))  

  w_cpt(0) := r_cpt
  w_cpt_loop(0) := r_pt_loop
  w_cready(0) := (r_pt_loop | (r_cpt < r_wpt)) & (~r_entry(r_cpt).valid | (~r_entry(r_cpt).replay & ~r_entry(r_cpt).br_mask.orR & (~r_entry(r_cpt).busy | r_entry(r_cpt).exc)))

  for (c <- 1 to p.nCommit) {
    w_cpt(c) := Mux((w_cpt(c - 1) === (p.nRobEntry - 1).U), 0.U, w_cpt(c - 1) + 1.U)
    w_cpt_loop(c) := (w_cready(c - 1) & w_cpt_loop(c - 1) & (w_cpt(c) =/= 0.U)) | (~w_cready(c - 1) & w_cpt_loop(c - 1))
  }

  for (c <- 1 until p.nCommit) {
    w_cready(c) := w_cready(c - 1) & (w_cpt_loop(c) | (w_cpt(c) < r_wpt)) & (~r_entry(w_cpt(c)).valid | (~r_entry(w_cpt(c - 1)).exc & ~r_entry(r_cpt).replay & ~r_entry(w_cpt(c)).br_mask.orR & ~r_entry(w_cpt(c)).busy & ~r_entry(w_cpt(c)).exc))
  }

  w_commit := r_commit
  for (c <- 0 until p.nCommit) {
    io.o_commit(c).valid := r_entry(w_cpt(c)).valid & w_cready(c) & ~r_entry(r_cpt).exc
    io.o_commit(c).entry := w_cpt(c)
    io.o_commit(c).rdl := r_entry(w_cpt(c)).rdl
    io.o_commit(c).rdp := r_entry(w_cpt(c)).rdp    
    when (w_cready(c)) {
      r_entry(w_cpt(c)).valid := false.B
      when (~r_entry(r_cpt).exc) {
        w_commit := r_entry(w_cpt(c)).pc
      }
    }
  }

  r_commit := w_commit

  when (w_cready.asUInt.andR) {
    w_cpt_slct := w_cpt(p.nCommit)
  }.otherwise {
    w_cpt_slct := w_cpt(PriorityEncoder(~w_cready.asUInt))
  }

  // ------------------------------
  //             STATS
  // ------------------------------
  val w_cinstret = Wire(Vec(p.nCommit, Bool()))
  val w_calu = Wire(Vec(p.nCommit, Bool()))
  val w_cld = Wire(Vec(p.nCommit, Bool()))
  val w_cst = Wire(Vec(p.nCommit, Bool()))
  val w_cbr = Wire(Vec(p.nCommit, Bool()))
  val w_cmispred = Wire(Vec(p.nCommit, Bool()))

  for (c <- 0 until p.nCommit) {
    w_cinstret(c) := w_cready(c) & r_entry(w_cpt(c)).valid & ~r_entry(w_cpt(c)).exc
    w_calu(c) := w_cready(c) & r_entry(w_cpt(c)).valid & r_entry(w_cpt(c)).stat.alu
    w_cld(c) := w_cready(c) & r_entry(w_cpt(c)).valid & r_entry(w_cpt(c)).stat.ld
    w_cst(c) := w_cready(c) & r_entry(w_cpt(c)).valid & r_entry(w_cpt(c)).stat.st
    w_cbr(c) := w_cready(c) & r_entry(w_cpt(c)).valid & r_entry(w_cpt(c)).stat.br
    w_cmispred(c) := w_cready(c) & r_entry(w_cpt(c)).valid & r_entry(w_cpt(c)).stat.mispred
  }

  io.o_stat := 0.U.asTypeOf(io.o_stat)
  io.o_stat.instret := PopCount(w_cready.asUInt)
  io.o_stat.alu := PopCount(w_calu.asUInt)
  io.o_stat.ld := PopCount(w_cld.asUInt)
  io.o_stat.st := PopCount(w_cst.asUInt)
  io.o_stat.br := PopCount(w_cbr.asUInt)
  io.o_stat.mispred := PopCount(w_cmispred.asUInt)

  // ******************************
  //              PT
  // ******************************
  // ------------------------------
  //            WRITE
  // ------------------------------
  when (w_init | w_hart_flush) {
    r_wpt := 0.U
  }.elsewhen (io.i_br_new.valid) {
    r_wpt := w_br_pt  
  }.elsewhen (w_wready & ~w_lock) {
    r_wpt := w_wpt(p.nBackPort)
  }

  // ------------------------------
  //           COMMIT
  // ------------------------------
  when (w_init | w_hart_flush) {
    r_cpt := 0.U
  }.otherwise {
    r_cpt := w_cpt_slct
  }

  // ------------------------------
  //            LOOP
  // ------------------------------
  when (w_init | w_hart_flush | w_br_unloop) {
    r_pt_loop := false.B
  }.elsewhen (w_wpt_loop) {
    r_pt_loop := true.B
  }.elsewhen (~w_cpt_loop(p.nCommit)) {
    r_pt_loop := false.B
  }

  // ******************************
  //            READ PC
  // ******************************
  for (rpc <- 0 until p.nRobPcRead) {
    io.b_pc(rpc).pc := r_entry(io.b_pc(rpc).entry).pc
    when (io.b_pc(rpc).entry === (p.nRobEntry - 1).U) {
      io.b_pc(rpc).nready := r_entry(0).valid
      io.b_pc(rpc).npc := r_entry(0).pc
    }.otherwise {
      io.b_pc(rpc).nready := r_entry(io.b_pc(rpc).entry + 1.U).valid
      io.b_pc(rpc).npc := r_entry(io.b_pc(rpc).entry + 1.U).pc
    } 
  }

  // ******************************
  //           BACK STATE
  // ******************************
  val init_back = Wire(new BackBus(p.nAddrBit, p.nDataBit, p.nRobEntry))

  init_back := DontCare
  init_back.state := STATE.RUN

  val r_back = RegInit(init_back)

  val w_irq = Wire(Bool())

  w_irq := w_wfree.asUInt.andR | (r_entry(r_cpt).valid & (r_entry(r_cpt).exc | r_entry(r_cpt).replay))

  // ------------------------------
  //             FSM
  // ------------------------------
  switch (r_back.state) {
    // ..............................
    //             RUN
    // ..............................
    is (STATE.RUN) {
//      for (bp <- (p.nBackPort - 1) to 0 by -1) {
//        // Write exception
//        when (io.b_write(bp).valid & io.b_write(bp).data.exc) {
//          r_back.state := STATE.STOP
//          r_back.pc := io.b_write(bp).data.pc
//          r_back.cause := io.b_write(bp).trap.cause
//          r_back.info := io.b_write(bp).trap.info
//          if (bp == 0) {
//            r_back.entry := r_wpt 
//          } else {
//            r_back.entry := w_wpt(bp - 1)
//          }
//        }
//      }
//
//      // Replay
//      if (p.useRobReplay) {
//        for (eu <- 0 until p.nExUnit) {
//          for (re <- 0 until p.nRobEntry) {
//            when (io.b_end(eu).valid & io.b_end(eu).replay & (re.U === io.b_end(eu).entry)) {
//              r_back.state := STATE.STOP
//              r_back.pc := r_entry(re).pc
//            }
//          }
//        }
//      }


      for (re <- 0 until p.nRobEntry) {
        when (r_entry(re).valid & (re.U === r_cpt)) {
          when (r_entry(re).replay) {
            r_back.state := STATE.REPLAY
            r_back.pc := r_entry(re).pc
          }
          
          when (r_entry(re).exc) {
            r_back.state := STATE.EXC
            r_back.pc := r_entry(re).pc
            r_back.cause := DontCare
            r_back.info := DontCare
          }
        }
      }

      // Execution unit exception
      for (eu <- 0 until p.nExUnit) {

      }

      // Interrupt
      when (io.b_clint.en) {
        r_back.state := STATE.IRQ
        r_back.pc := w_commit
        r_back.cause := io.b_clint.ecause
        r_back.info := 0.U
      }

      // Hart flush
      when (w_hart_flush) {
        r_back.state := STATE.RUN
      }
    }

    // ..............................
    //             STOP
    // ..............................
    is (STATE.STOP) {
      for (re <- 0 until p.nRobEntry) {
        when (r_entry(re).valid & (re.U === r_cpt)) {
          when (r_entry(re).replay) {
            r_back.state := STATE.REPLAY
            r_back.pc := r_entry(re).pc
          }
          when (r_entry(re).exc) {
            r_back.state := STATE.EXC
          }
        }
      }

      when (io.b_clint.en) {
        r_back.state := STATE.IRQ
        r_back.pc := w_commit
        r_back.cause := io.b_clint.ecause
        r_back.info := 0.U
      }

      // Hart flush
      when (w_hart_flush) {
        r_back.state := STATE.RUN
      }
    }

    // ..............................
    //            REPLAY
    // ..............................
    is (STATE.REPLAY) {
      r_back.state := STATE.RUN
    }

    // ..............................
    //             IRQ
    // ..............................
    is (STATE.IRQ) {
      r_back.pc := w_commit
      when (w_irq) {
        r_back.state := STATE.RUN
      }

      when (io.b_clint.en) {
        r_back.cause := io.b_clint.ecause        
      }

      // Hart flush
      when (w_hart_flush) {
        r_back.state := STATE.RUN
      }
    }

    // ..............................
    //             WFI
    // ..............................
    is (STATE.WFI) {
      when (io.b_clint.en) {
        r_back.state := STATE.IRQ
        r_back.pc := w_commit
        r_back.cause := io.b_clint.ecause
        r_back.info := 0.U      
      }

      // Hart flush
      when (w_hart_flush) {
        r_back.state := STATE.RUN
      }
    }

    // ..............................
    //             EXC
    // ..............................
    is (STATE.EXC) {
      r_back.state := STATE.RUN

      // Interrupt
      when (io.b_clint.en) {
        r_back.state := STATE.IRQ
        r_back.pc := w_commit
        r_back.cause := io.b_clint.ecause
        r_back.info := 0.U
      }

      // Hart flush
      when (w_hart_flush) {
        r_back.state := STATE.RUN
      }
    }
  }
  
  // ------------------------------
  //      LOCK, INIT & REPLAY
  // ------------------------------
  w_lock := (r_back.state =/= STATE.RUN)

  io.o_run := (r_back.state === STATE.RUN)
  
  io.o_br_new := DontCare
  if (p.useRobReplay) {
    io.o_br_new.valid := (r_back.state === STATE.REPLAY)
    io.o_br_new.addr := r_back.pc
  } else {
    io.o_br_new.valid := false.B
  }

  // ------------------------------
  //             TRAP
  // ------------------------------
  io.b_clint := DontCare 
  
  io.o_trap.gen := false.B
  io.o_trap.valid := (r_back.state === STATE.EXC) | ((r_back.state === STATE.IRQ) & w_irq)
  io.o_trap.pc := r_back.pc
  io.o_trap.cause := r_back.cause
  io.o_trap.info := r_back.info
  io.o_trap.src := DontCare
  switch (r_back.state) {
    is (STATE.IRQ)  {io.o_trap.src := TRAPSRC.IRQ}
    is (STATE.EXC)  {io.o_trap.src := TRAPSRC.EXC}
  }

  // ------------------------------
  //             INIT
  // ------------------------------
  w_init := (r_back.state === STATE.REPLAY) | (r_back.state === STATE.EXC) | ((r_back.state === STATE.IRQ) & w_irq)

  io.o_init := w_init 

  for (re <- 0 until p.nRobEntry) {
    when (w_init) {    
      r_entry(re).valid := false.B
    }
  }

  // ******************************
  //             DOME
  // ******************************
  if (p.useDome) {
    io.b_hart.get.free := true.B

    when (io.b_hart.get.flush) {
      for (re <- 0 until p.nRobEntry) {
        r_entry(re).valid := false.B
      }
    }
  }
  // ******************************
  //             LAST
  // ******************************
  val r_last = Reg(UInt(p.nAddrBit.W))

  for (c <- 0 until p.nCommit) {
    when (w_cready(c) & r_entry(w_cpt(c)).valid & ~r_entry(r_cpt).exc) {
      r_last := r_entry(w_cpt(c)).pc
    }
  }

  // ******************************
  //             DEBUG
  // ******************************
  if (p.debug) {
    // ------------------------------
    //            SIGNALS
    // ------------------------------
    io.o_dbg.get := r_last

    // ------------------------------
    //         DATA FOOTPRINT
    // ------------------------------

    // ------------------------------
    //       EXECUTION TRACKER
    // ------------------------------
    // Default
    val init_etd = Wire(Vec(p.nCommit, new EtdBus(p.nHart, p.nAddrBit, p.nInstrBit)))

    for (c <- 0 until p.nCommit) {
      init_etd(c) := DontCare
      init_etd(c).done := false.B
    }

    val r_etd = RegInit(init_etd)
    val r_time = RegInit(0.U(64.W))
    
    // Registers
    for (c <- 0 until p.nCommit) {
      r_etd(c).done := false.B
      when (w_cready(c) & r_entry(w_cpt(c)).valid & ~r_entry(r_cpt).exc) {
        r_etd(c) := r_entry(w_cpt(c)).etd.get
        r_etd(c).done := true.B
        r_etd(c).tend := r_time + 1.U
      }
    }

    // Data address update
    for (eu <- 0 until p.nExUnit) {
      when (io.b_end(eu).valid) {
        r_entry(io.b_end(eu).entry).etd.get.daddr := io.b_end(eu).etd.get.daddr
      }
    }

    // Time update
    r_time := r_time + 1.U

    // Output
    io.o_etd.get := r_etd
  }
}

object Rob extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new Rob(BackConfigBase), args)
}

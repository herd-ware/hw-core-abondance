/*
 * File: load.scala                                                            *
 * Created Date: 2023-02-26 09:21:29 am                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-03-02 12:17:30 pm                                       *
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
import herd.common.mem.mb4s.{OP => LSUUOP}
import herd.core.abondance.common._
import herd.core.abondance.back.{BranchBus, BypassBus, CommitBus, EndIO}
 

class LoadQueue(p: LsuParams) extends Module {  
  val io = IO(new Bundle {
    val b_hart = if (p.useField) Some(new RsrcIO(p.nHart, p.nField, 1)) else None

    val i_flush = Input(Bool())

    val b_in = Vec(p.nBackPort, Flipped(new GenRVIO(p, new LoadQueueBus(p), UInt(0.W))))

    val i_br_up = Input(UInt(p.nSpecBranch.W))
    val i_br_new = Input(new BranchBus(p.nAddrBit, p.nSpecBranch, p.nRobEntry))
    val i_busy = Input(Vec(p.nBypass, new BypassBus(p.nDataBit, p.nGprPhy)))
    val i_commit = Input(Vec(p.nCommit, new CommitBus(p.nRobEntry, 1, p.nGprPhy)))

    val i_st_ex = Input(new GenVBus(p, new LsuExAckCtrlBus(p, p.nStoreQueue), UInt(0.W)))
    val i_st_end = Input(new GenVBus(p, new LsuExAckCtrlBus(p, p.nStoreQueue), UInt(0.W)))

    val b_ex = Vec(p.nLoad, new LsuExIO(p, p.nLoadQueue))
    val b_mem = Vec(p.nLoad, new GenRVIO(p, new MemCtrlBus(p), UInt(0.W)))
    val i_done = Vec(p.nLoad, Input(new TagBus(p.nLoadQueue)))

    val b_end = Vec(p.nLoad, new EndIO(p.debug, p.nAddrBit, p.nDataBit, p.nRobEntry))
  })

  // ******************************
  //           REGISTERS
  // ******************************
  val init_queue = Wire(Vec(p.nLoadQueue, new GenVBus(p, new LoadQueueBus(p), UInt(0.W))))
  for (lq <- 0 until p.nLoadQueue) {
    init_queue(lq) := DontCare
    init_queue(lq).valid := false.B
  }

  val r_queue = RegInit(init_queue)
  val r_wav = RegInit(true.B)

  val m_mem = Seq.fill(p.nLoad){Module(new GenReg(p, new MemCtrlBus(p), UInt(0.W), false, false, true))}

  val w_mem = Wire(Vec(p.nLoad, new GenVBus(p, new MemCtrlBus(p), UInt(0.W))))
  val w_mlock = Wire(Vec(p.nLoad, Bool()))

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
  //            UPDATE
  // ******************************
  val w_upqueue = Wire(Vec(p.nLoadQueue, new GenVBus(p, new LoadQueueBus(p), UInt(0.W))))
  w_upqueue := r_queue

  // ------------------------------
  //           LINK READY
  // ------------------------------
  for (lq <- 0 until p.nLoadQueue) {
    for (c <- 0 until p.nCommit) {
      when (r_queue(lq).valid & io.i_commit(c).valid & (io.i_commit(c).entry === r_queue(lq).ctrl.get.dep.entry)) {
        w_upqueue(lq).ctrl.get.dep.av(0) := true.B
      }           
    }
  }

  // ------------------------------
  //           DATA READY
  // ------------------------------
  for (lq <- 0 until p.nLoadQueue) {
    for (b <- 0 until p.nBypass) {
      when (r_queue(lq).valid & io.i_busy(b).valid & (io.i_busy(b).rdp === r_queue(lq).ctrl.get.dep.rs1p)) {
        w_upqueue(lq).ctrl.get.dep.av(1) := true.B
      }
    }    
  }

  // ------------------------------
  //         BRANCH & FLUSH
  // ------------------------------
  for (lq <- 0 until p.nLoadQueue) {
    w_upqueue(lq).valid := r_queue(lq).valid & ~((io.i_br_new.valid & r_queue(lq).ctrl.get.br.mask(io.i_br_new.tag)) | io.i_flush)
    w_upqueue(lq).ctrl.get.br.mask := r_queue(lq).ctrl.get.br.mask & io.i_br_up
  }

  // ------------------------------
  //             LOAD
  // ------------------------------
  val w_up_ld = Wire(Vec(p.nLoadQueue, Vec(p.nLoadQueue, Bool())))

  for (lq0 <- 0 until p.nLoadQueue) {
    for (lq1 <- 0 until p.nLoadQueue) {
      w_up_ld(lq0)(lq1) := true.B

      for (l <- 0 until p.nLoad) {
        when (io.b_ex(l).ack.valid & r_queue(lq0).valid & r_queue(lq0).ctrl.get.state.addr & (lq1.U === io.b_ex(l).ack.ctrl.get.tag)) {
          when (io.b_ex(l).ack.ctrl.get.addr(p.nAddrBit - 1, log2Ceil(p.nDataBit)) =/= r_queue(lq0).ctrl.get.info.addr(p.nAddrBit - 1, log2Ceil(p.nDataBit))) {
            w_up_ld(lq0)(lq1) := false.B
          }.otherwise {
            if (p.useSpecLoad) {
              when (r_queue(lq0).ctrl.get.state.mem & r_queue(lq0).ctrl.get.ld_prev(lq1)) {
                w_upqueue(lq0).ctrl.get.state.replay := true.B
              }
            }
          }
        }

        when (w_mem(l).valid & io.b_mem(l).ready & r_queue(lq0).valid & (lq1.U === w_mem(l).ctrl.get.tag)) {
          w_up_ld(lq0)(lq1) := false.B
        }
      }
    }

    w_upqueue(lq0).ctrl.get.ld_prev := r_queue(lq0).ctrl.get.ld_prev & w_up_ld(lq0).asUInt & ~(UIntToOH(lq0.U))
    w_upqueue(lq0).ctrl.get.ld_spec := r_queue(lq0).ctrl.get.ld_spec & w_up_ld(lq0).asUInt & ~(UIntToOH(lq0.U))
  }
  
  // ------------------------------
  //             STORE
  // ------------------------------
  val w_up_st = Wire(Vec(p.nLoadQueue, Vec(p.nStoreQueue, Bool())))
  val w_up_fwd = Wire(Vec(p.nLoadQueue, Vec(p.nStoreQueue, Bool())))

  for (lq <- 0 until p.nLoadQueue) {
    for (sq <- 0 until p.nStoreQueue) {
      w_up_st(lq)(sq) := true.B
      w_up_fwd(lq)(sq) := r_queue(lq).ctrl.get.fwd(sq)

      when (sq.U === io.i_st_ex.ctrl.get.tag) {
        when (io.i_st_ex.valid & r_queue(lq).ctrl.get.state.addr) {
          when (io.i_st_ex.ctrl.get.addr((p.nAddrBit - 1), log2Ceil(p.nDataByte)) =/= r_queue(lq).ctrl.get.info.addr((p.nAddrBit - 1), log2Ceil(p.nDataByte))) {
            w_up_st(lq)(sq) := false.B
          }.otherwise {
            if (p.useSpecLoad) {
              when (r_queue(lq).ctrl.get.state.mem & r_queue(lq).ctrl.get.st_prev(sq)) {
                w_upqueue(lq).ctrl.get.state.replay := true.B
              }
            }

            when ((io.i_st_ex.ctrl.get.size >= r_queue(lq).ctrl.get.ctrl.size) & (io.i_st_ex.ctrl.get.addr(log2Ceil(p.nDataByte) - 1, 0) === r_queue(lq).ctrl.get.info.addr(log2Ceil(p.nDataByte) - 1, 0))) {              
              when (r_queue(lq).ctrl.get.st_prev(sq) & io.i_st_ex.ctrl.get.fwd) {
                w_up_st(lq)(sq) := false.B
                w_up_fwd(lq)(sq) := true.B
              }
            }
          }
        }
      }

      when (sq.U === io.i_st_end.ctrl.get.tag) {
        when (io.i_st_end.valid) {
          w_up_st(lq)(sq) := false.B
          w_up_fwd(lq)(sq) := false.B
          
          if (p.useSpecLoad) {
            when (r_queue(lq).ctrl.get.state.mem & r_queue(lq).ctrl.get.st_prev(sq) & io.i_st_end.ctrl.get.addr((p.nAddrBit - 1), log2Ceil(p.nDataByte)) =/= r_queue(lq).ctrl.get.info.addr((p.nAddrBit - 1), log2Ceil(p.nDataByte))) {
              w_upqueue(lq).ctrl.get.state.replay := true.B
            }
          }
        }
      }

      if (p.useExtA) {
        when (r_queue(lq).ctrl.get.ctrl.lr) {
          w_up_fwd(lq)(sq) := false.B
        }
      }
    }

    w_upqueue(lq).ctrl.get.st_prev := r_queue(lq).ctrl.get.st_prev & w_up_st(lq).asUInt
    w_upqueue(lq).ctrl.get.st_spec := r_queue(lq).ctrl.get.st_spec & w_up_st(lq).asUInt
    w_upqueue(lq).ctrl.get.fwd := w_up_fwd(lq).asUInt
  }

  // ******************************
  //             WRITE
  // ******************************
  // ------------------------------
  //            SELECT
  // ------------------------------
  val w_wav = Wire(Vec(p.nBackPort + 1, Vec(p.nLoadQueue, Bool())))
  val w_wslct = Wire(Vec(p.nBackPort, UInt(log2Ceil(p.nLoadQueue).W)))

  for (lq <- 0 until p.nLoadQueue) {
    w_wav(0)(lq) := ~r_queue(lq).valid
  }

  for (bp <- 0 until p.nBackPort) {
    w_wslct(bp) := PriorityEncoder(w_wav(bp).asUInt)
    w_wav(bp + 1) := w_wav(bp)
    when (io.b_in(bp).valid) {
      w_wav(bp + 1)(w_wslct(bp)) := false.B
    }    
  }

  // ------------------------------
  //             LOAD
  // ------------------------------
  val w_wld_prev = Wire(Vec(p.nBackPort, Vec(p.nLoadQueue, Bool())))
  val w_wld_spec = Wire(Vec(p.nBackPort, Vec(p.nLoadQueue, Bool())))

  for (bp0 <- 0 until p.nBackPort) {
    for (lq <- 0 until p.nLoadQueue) {
      w_wld_prev(bp0)(lq) := r_queue(lq).valid & ~w_upqueue(lq).ctrl.get.state.mem
      w_wld_spec(bp0)(lq) := w_wld_prev(bp0)(lq) & (w_upqueue(lq).ctrl.get.dep.rs1p === io.b_in(bp0).ctrl.get.dep.rs1p) & (w_upqueue(lq).ctrl.get.info.addr(p.nAddrBit - 1, log2Ceil(p.nDataByte)) === io.b_in(bp0).ctrl.get.info.addr(p.nAddrBit - 1, log2Ceil(p.nDataByte)))

      for (bp1 <- 0 until bp0) {
        when (io.b_in(bp1).valid) {
          w_wld_prev(bp0)(w_wslct(bp1)) := true.B
        }  
        when (io.b_in(bp1).valid & (io.b_in(bp1).ctrl.get.dep.rs1p === io.b_in(bp0).ctrl.get.dep.rs1p) & (io.b_in(bp1).ctrl.get.info.addr(p.nAddrBit - 1, log2Ceil(p.nDataByte)) === io.b_in(bp0).ctrl.get.info.addr(p.nAddrBit - 1, log2Ceil(p.nDataByte)))) {
          w_wld_spec(bp0)(w_wslct(bp1)) := true.B
        }        
      }     
    } 
  }

  // ------------------------------
  //             STORE
  // ------------------------------
  val w_wst_prev = Wire(Vec(p.nBackPort, Vec(p.nStoreQueue, Bool())))
  val w_wst_spec = Wire(Vec(p.nBackPort, Vec(p.nStoreQueue, Bool())))
  val w_wfwd = Wire(Vec(p.nBackPort, Vec(p.nStoreQueue, Bool())))

  for (bp <- 0 until p.nBackPort) {
    for (sq <- 0 until p.nStoreQueue) {
      w_wst_prev(bp)(sq) := io.b_in(bp).ctrl.get.st_prev(sq)
      w_wst_spec(bp)(sq) := io.b_in(bp).ctrl.get.st_spec(sq)
      w_wfwd(bp)(sq) := io.b_in(bp).ctrl.get.fwd(sq)

      when (sq.U === io.i_st_ex.ctrl.get.tag) {
        when (io.i_st_ex.valid & (io.b_in(bp).ctrl.get.dep.rs1p === io.i_st_ex.ctrl.get.rs1p)) {
          when (io.i_st_ex.ctrl.get.fwd & (io.b_in(bp).ctrl.get.info.addr === io.i_st_ex.ctrl.get.imm) & (io.b_in(bp).ctrl.get.ctrl.size <= io.i_st_ex.ctrl.get.size)) {
            w_wst_prev(bp)(sq) := false.B
            w_wst_spec(bp)(sq) := false.B
            w_wfwd(bp)(sq) := true.B
          }.elsewhen (io.b_in(bp).ctrl.get.info.addr((p.nAddrBit - 1), log2Ceil(p.nDataByte)) =/= io.i_st_ex.ctrl.get.imm((p.nAddrBit - 1), log2Ceil(p.nDataByte))) {
            w_wst_prev(bp)(sq) := false.B
            w_wst_spec(bp)(sq) := false.B
            w_wfwd(bp)(sq) := false.B
          }
        }
      }

      when (sq.U === io.i_st_end.ctrl.get.tag) {
        when (io.i_st_end.valid) {
          w_wst_prev(bp)(sq) := false.B
          w_wst_spec(bp)(sq) := false.B
          w_wfwd(bp)(sq) := false.B
        }
      }

      if (p.useExtA) {
        when (io.b_in(bp).ctrl.get.ctrl.lr) {
          w_wfwd(bp)(sq) := false.B
        }
      }
    }
  }

  // ------------------------------
  //            UPDATE
  // ------------------------------
  for (bp <- 0 until p.nBackPort) {    
    io.b_in(bp).ready := r_wav
    when (io.b_in(bp).valid & r_wav) {
      w_upqueue(w_wslct(bp)).valid := true.B
      w_upqueue(w_wslct(bp)).ctrl.get := io.b_in(bp).ctrl.get
      w_upqueue(w_wslct(bp)).ctrl.get.ld_prev := w_wld_prev(bp).asUInt
      w_upqueue(w_wslct(bp)).ctrl.get.ld_spec := w_wld_spec(bp).asUInt
      w_upqueue(w_wslct(bp)).ctrl.get.st_prev := w_wst_prev(bp).asUInt
      w_upqueue(w_wslct(bp)).ctrl.get.st_spec := w_wst_spec(bp).asUInt
      w_upqueue(w_wslct(bp)).ctrl.get.fwd := w_wfwd(bp).asUInt
    }
  }
  
  // Free places for next cycle
  r_wav := (PopCount(w_wav(p.nBackPort).asUInt) >= p.nBackPort.U)

  // ******************************
  //            EXECUTE
  // ******************************
  // ------------------------------
  //          REGISTER READ
  // ------------------------------
  val w_rrav = Wire(Vec(p.nLoad + 1, Vec(p.nLoadQueue, Bool())))
  val w_rrslct = Wire(Vec(p.nLoad, UInt(log2Ceil(p.nLoadQueue).W)))

  for (lq <- 0 until p.nLoadQueue) {
    w_rrav(0)(lq) := r_queue(lq).valid & ~r_queue(lq).ctrl.get.state.rr & r_queue(lq).ctrl.get.dep.av(1)
  }

  for (l <- 0 until p.nLoad) {
    w_rrslct(l) := PriorityEncoder(w_rrav(l).asUInt)
    for (lq <- 0 until p.nLoadQueue) {
      w_rrav(l + 1) := w_rrav(l)
      w_rrav(l + 1)(w_rrslct(l)) := false.B
    }
  }

  for (l <- 0 until p.nLoad) {
    when (w_rrav(l).asUInt.orR) {
      w_upqueue(w_rrslct(l)).ctrl.get.state.rr := io.b_ex(l).req.ready
    }

    io.b_ex(l).req.valid := w_rrav(l).asUInt.orR
    io.b_ex(l).req.ctrl.get.fwd := false.B
    io.b_ex(l).req.ctrl.get.br := r_queue(w_rrslct(l)).ctrl.get.br
    io.b_ex(l).req.ctrl.get.tag := w_rrslct(l)
    io.b_ex(l).req.ctrl.get.size := r_queue(w_rrslct(l)).ctrl.get.ctrl.size
    io.b_ex(l).req.ctrl.get.rs1p := r_queue(w_rrslct(l)).ctrl.get.dep.rs1p
    io.b_ex(l).req.ctrl.get.rs2p := DontCare
    io.b_ex(l).req.ctrl.get.imm := r_queue(w_rrslct(l)).ctrl.get.info.addr
  }

  // ------------------------------
  //         ADDRESS WRITE
  // ------------------------------
  for (lq <- 0 until p.nLoadQueue) {
    for (l <- 0 until p.nLoad) {
      when (r_queue(lq).valid & io.b_ex(l).ack.valid & (lq.U === io.b_ex(l).ack.ctrl.get.tag)) {
        w_upqueue(lq).ctrl.get.state.addr := true.B
        w_upqueue(lq).ctrl.get.info.addr := io.b_ex(l).ack.ctrl.get.addr
      }
    }
  }

  // ******************************
  //             MEMORY
  // ******************************
  // ------------------------------
  //            SELECT
  // ------------------------------
  val w_mav = Wire(Vec(p.nLoad + 1, Vec(p.nLoadQueue, Bool())))
  val w_mslct = Wire(Vec(p.nLoad, UInt(log2Ceil(p.nLoadQueue).W)))

  // TODO: Reorganize at last moment
//  for (lq <- 0 until p.nStoreQueue) {
//    for (l <- 0 until p.nLoad) {
//      w_upqueue(lq).ctrl.get.state.mem := w_mem(l).valid & (w_mem(l).ctrl.get.ld === w_upqueue(lq).ctrl.get.ld) & io.b_mem(l).ready
//    }
//  }

  for (lq <- 0 until p.nLoadQueue) {
    if (p.useSpecLoad) {
      w_mav(0)(lq) := r_queue(lq).valid & ~r_queue(lq).ctrl.get.state.mem & r_queue(lq).ctrl.get.state.addr & r_queue(lq).ctrl.get.dep.av(0) & ~r_queue(lq).ctrl.get.ld_spec.orR & ~r_queue(lq).ctrl.get.st_spec.orR
    } else {
      w_mav(0)(lq) := r_queue(lq).valid & ~r_queue(lq).ctrl.get.state.mem & r_queue(lq).ctrl.get.state.addr & r_queue(lq).ctrl.get.dep.av(0) & ~r_queue(lq).ctrl.get.ld_prev.orR & ~r_queue(lq).ctrl.get.st_prev.orR
    }
  }

  for (l <- 0 until p.nLoad) {
    w_mslct(l) := PriorityEncoder(w_mav(l).asUInt)
    w_mav(l + 1) := w_mav(l)
    w_mav(l + 1)(w_mslct(l)) := w_mav(l)(w_mslct(l)) & w_mlock(l)
  }

  for (l <- 0 until p.nLoad) {
    w_upqueue(w_mslct(l)).ctrl.get.state.mem := w_mav(l).asUInt.orR & ~w_mlock(l)
  }

  // ------------------------------
  //           REGISTERS
  // ------------------------------
  for (l <- 0 until p.nLoad) {
    w_mem(l) := m_mem(l).io.o_val

    w_mlock(l) := ~m_mem(l).io.b_in.ready

    m_mem(l).io.i_flush := false.B

    m_mem(l).io.b_in.valid := w_mav(l).asUInt.orR & ~io.i_flush & ~w_hart_flush & ~(io.i_br_new.valid & w_upqueue(w_mslct(l)).ctrl.get.br.mask(io.i_br_new.tag))
    m_mem(l).io.b_in.ctrl.get.br := w_upqueue(w_mslct(l)).ctrl.get.br
    m_mem(l).io.b_in.ctrl.get.tag := w_mslct(l)
    m_mem(l).io.b_in.ctrl.get.fwd := w_upqueue(w_mslct(l)).ctrl.get.fwd
    m_mem(l).io.b_in.ctrl.get.entry := w_upqueue(w_mslct(l)).ctrl.get.info.entry
    m_mem(l).io.b_in.ctrl.get.addr := w_upqueue(w_mslct(l)).ctrl.get.info.addr
    m_mem(l).io.b_in.ctrl.get.rdp := w_upqueue(w_mslct(l)).ctrl.get.info.rdp
    m_mem(l).io.b_in.ctrl.get.ctrl := w_upqueue(w_mslct(l)).ctrl.get.ctrl
    if (p.useExtA) {
      m_mem(l).io.b_in.ctrl.get.ctrl.uop := w_upqueue(w_mslct(l)).ctrl.get.ctrl.uop
    } else {
      m_mem(l).io.b_in.ctrl.get.ctrl.uop := LSUUOP.R
    }    

    io.b_mem(l) <> m_mem(l).io.b_out
  }

  // ******************************
  //             DONE
  // ******************************
  for (lq <- 0 until p.nLoadQueue) {
    for (l <- 0 until p.nLoad) {
      when (io.i_done(l).valid & (lq.U === io.i_done(l).tag)) {
        w_upqueue(lq).ctrl.get.state.done := true.B
      }
    }
  }

  // ******************************
  //             END
  // ******************************
  val w_endav = Wire(Vec(p.nLoad + 1, Vec(p.nLoadQueue, Bool())))
  val w_endslct = Wire(Vec(p.nLoad, UInt(log2Ceil(p.nLoadQueue).W)))

  for (lq <- 0 until p.nLoadQueue) {
    w_endav(0)(lq) := r_queue(lq).valid & r_queue(lq).ctrl.get.state.done & ~r_queue(lq).ctrl.get.ld_prev.orR & ~r_queue(lq).ctrl.get.st_prev.orR
  }

  for (l <- 0 until p.nLoad) {
    w_endslct(l) := PriorityEncoder(w_endav(l).asUInt)
    w_endav(l + 1) := w_endav(l)
    w_endav(l + 1)(w_endslct(l)) := false.B
  }

  for (l <- 0 until p.nLoad) {
    when (w_endav(l).asUInt.orR) {
      w_upqueue(w_endslct(l)).valid := ~io.b_end(l).ready
    }
    
    io.b_end(l).valid := w_endav(l).asUInt.orR
    io.b_end(l).entry := r_queue(w_endslct(l)).ctrl.get.info.entry
    io.b_end(l).replay := r_queue(w_endslct(l)).ctrl.get.state.replay
    io.b_end(l).trap := DontCare
    io.b_end(l).trap.valid := false.B

    io.b_end(l).stat := 0.U.asTypeOf(io.b_end(l).stat)
  }

  // ******************************
  //            UPDATE
  // ******************************
  r_queue := w_upqueue

  // ******************************
  //             FIELD
  // ******************************
  if (p.useField) {
    io.b_hart.get.free := ~w_mem(0).valid & ~w_mem(1).valid
    when (io.b_hart.get.flush) {
      for (lq <- 0 until p.nLoadQueue) {
        r_queue(lq).valid := false.B
      }
    }
  }

  // ******************************
  //             DEBUG
  // ******************************
  if (p.debug) {
    // ------------------------------
    //            SIGNALS
    // ------------------------------
    dontTouch(r_queue)
    dontTouch(w_upqueue)

    // ------------------------------
    //         DATA FOOTPRINT
    // ------------------------------

    // ------------------------------
    //       EXECUTION TRACKER
    // ------------------------------  
    for (lq <- 0 until p.nLoadQueue) {
      for (l <- 0 until p.nLoad) {
        when (io.b_ex(l).ack.valid & (lq.U === io.b_ex(l).ack.ctrl.get.tag)) {
          w_upqueue(lq).ctrl.get.etd.get.daddr := io.b_ex(l).ack.ctrl.get.addr
        }
      }
    }

    for (l <- 0 until p.nLoad) {
      m_mem(l).io.b_in.ctrl.get.etd.get := w_upqueue(w_mslct(l)).ctrl.get.etd.get
      io.b_end(l).etd.get := r_queue(w_endslct(l)).ctrl.get.etd.get
    }

    for (lq <- 0 until p.nLoadQueue) {
      dontTouch(r_queue(lq).ctrl.get.etd.get)
      dontTouch(w_upqueue(lq).ctrl.get.etd.get)
    }
  }
}

object LoadQueue extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new LoadQueue(LsuConfigBase), args)
}
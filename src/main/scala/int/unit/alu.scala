/*
 * File: alu.scala                                                             *
 * Created Date: 2023-02-26 09:21:29 am                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-03-02 06:59:37 pm                                       *
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
import herd.common.isa.riscv._
import herd.core.abondance.common._
import herd.core.abondance.back.{BranchBus, BypassBus, GprWriteIO, EndIO}

 
class Alu (p: IntUnitParams) extends Module {
  import herd.core.abondance.int.INTUOP._

  val io = IO(new Bundle {
    val i_flush = Input(Bool())
    val o_free = Output(Bool())

    val b_in = Flipped(new GenRVIO(p, new IntUnitCtrlBus(p), new IntUnitDataBus(p)))

    val i_br_up = Input(UInt(p.nSpecBranch.W))
    val i_br_new = Input(new BranchBus(p.nDataBit, p.nSpecBranch, p.nRobEntry))

    val o_byp = Output(new BypassBus(p.nDataBit, p.nGprPhy))
    val b_write = Flipped(new GprWriteIO(p.nDataBit, p.nGprPhy))
    val b_end = new EndIO(p.debug, p.nAddrBit, p.nDataBit, p.nRobEntry)
  })

  val w_lock = Wire(Bool())
  val w_flush = Wire(Bool())

  w_flush := io.i_flush | (io.i_br_new.valid & io.b_in.ctrl.get.br.mask(io.i_br_new.tag))

  // ******************************
  //           OPERANDS
  // ******************************
  val w_uop = Wire(UInt(NBIT.W))
  val w_sign = Wire(Bool())
  val w_s1 = Wire(UInt(p.nDataBit.W))
  val w_s2 = Wire(UInt(p.nDataBit.W))
  val w_amount = Wire(UInt(log2Ceil(p.nDataBit).W))

  if (p.nDataBit >= 64) {
    when (io.b_in.ctrl.get.ctrl.rsize === INTSIZE.W) {
      w_amount := io.b_in.data.get.s2(4,0).asUInt
    }.otherwise {
      w_amount := io.b_in.data.get.s2(5,0).asUInt
    }
  } else {
    w_amount := io.b_in.data.get.s2(4,0).asUInt
  }  

  w_sign := io.b_in.ctrl.get.ctrl.ssign(0) | io.b_in.ctrl.get.ctrl.ssign(1)
  w_uop := io.b_in.ctrl.get.ctrl.uop
  w_s1 := io.b_in.data.get.s1
  w_s2 := io.b_in.data.get.s2

  if (p.useExtB) {
    switch (io.b_in.ctrl.get.ctrl.uop) {
      is (SH1ADD) {
        w_uop := ADD
        w_s1 := (io.b_in.data.get.s1 << 1.U)
      }
      is (SH2ADD) {
        w_uop := ADD
        w_s1 := (io.b_in.data.get.s1 << 2.U)
      }
      is (SH3ADD) {
        w_uop := ADD
        w_s1 := (io.b_in.data.get.s1 << 3.U)
      }
      is (CLZ) {
        if (p.nDataBit >= 64) {
          when (io.b_in.ctrl.get.ctrl.rsize === INTSIZE.W) {
            w_s1 := Cat(Fill(32, 1.B), Reverse(io.b_in.data.get.s1(31, 0)))   
          } 
        }
      }
      is (CTZ) {
        if (p.nDataBit >= 64) {
          when (io.b_in.ctrl.get.ctrl.rsize === INTSIZE.W) {
            w_s1 := Cat(Fill(32, 1.B), io.b_in.data.get.s1(31, 0))
          }
        }
      }
      is (CPOP) {
        if (p.nDataBit >= 64) {
          when (io.b_in.ctrl.get.ctrl.rsize === INTSIZE.W) {
            w_s1 := Cat(Fill(32, 0.B), io.b_in.data.get.s1(31, 0))
          }
        }
      }
      is (ANDN) {
        w_uop := AND
        w_s2 := ~io.b_in.data.get.s2
      }
      is (ORN) {
        w_uop := OR
        w_s2 := ~io.b_in.data.get.s2
      }
    }
  }  

  // ******************************
  //            LOGIC
  // ******************************
  val w_res = Wire(UInt(p.nDataBit.W))

  w_res := 0.U

  // ------------------------------
  //             BASE
  // ------------------------------
  switch (io.b_in.ctrl.get.ctrl.uop) {
    is (ADD)  {
      w_res := w_s1 + w_s2
    }
    is (SUB)  {
      w_res := w_s1 - w_s2
    }
    is (SLT)  {
      when (w_sign) {
        w_res := w_s1.asSInt < w_s2.asSInt
      }.otherwise {
        w_res := w_s1 < w_s2
      }      
    }
    is (OR)   {
      w_res := w_s1 | w_s2
    }
    is (AND)  {
      w_res := w_s1 & w_s2
    }
    is (XOR)  {
      w_res := w_s1 ^ w_s2
    }
    is (SHR)  {
      when (w_sign) {
        w_res := ((w_s1).asSInt >> w_amount).asUInt
      }.otherwise {
        w_res := w_s1 >> w_amount
      } 
    }
    is (SHL)  {
      w_res := w_s1 << w_amount
    }
  }

  // ------------------------------
  //              B
  // ------------------------------
  if (p.useExtB) {
    switch (w_uop) {
      is (XNOR) {
        w_res := ~(w_s1 ^ w_s2)
      }
      is (CLZ, CTZ) {
        w_res := Mux(~w_s1.orR, PriorityEncoder(Reverse(w_s1)), p.nDataBit.U)                
      }
      is (CPOP) {
        w_res := PopCount(w_s1)               
      }
      is (MAX) {
        when (w_sign) {
          w_res := Mux(((w_s1).asSInt > (w_s2).asSInt), w_s1, w_s2)
        }.otherwise {
          w_res := Mux((w_s1 > w_s2), w_s1, w_s2)
        }      
      }
      is (MIN) {
        when (w_sign) {
          w_res := Mux(((w_s1).asSInt < (w_s2).asSInt), w_s1, w_s2)
        }.otherwise {
          w_res := Mux((w_s1 < w_s2), w_s1, w_s2)
        }      
      }
      is (EXTB) {
        when (w_sign) {
          w_res := Cat(Fill(p.nDataBit - 8, w_s1(7)), w_s1(7, 0))
        }.otherwise {
          w_res := Cat(Fill(p.nDataBit - 8, 0.B), w_s1(7, 0))
        }      
      }
      is (EXTH) {
        when (w_sign) {
          w_res := Cat(Fill(p.nDataBit - 16, w_s1(15)), w_s1(15, 0))
        }.otherwise {
          w_res := Cat(Fill(p.nDataBit - 16, 0.B), w_s1(15, 0))
        }      
      }
      is (ROL) {
        w_res := (Cat(w_s1, w_s1) << w_amount)(p.nDataBit * 2 - 1, p.nDataBit)
        if (p.nDataBit >= 64) {
          when (io.b_in.ctrl.get.ctrl.rsize === INTSIZE.W) {
            w_res := (Cat(w_s1(31, 0), w_s1(31, 0)) << w_amount)(63, 32)
          }
        }
      }
      is (ROR) {
        w_res := (Cat(w_s1, w_s1) >> w_amount)
        if (p.nDataBit >= 64) {
          when (io.b_in.ctrl.get.ctrl.rsize === INTSIZE.W) {
            w_res := (Cat(w_s1(31, 0), w_s1(31, 0)) >> w_amount)
          }
        }
      }
      is (ORCB) {
        val w_byte = Wire(Vec((p.nDataBit / 8), UInt(8.W)))

        for (b <- 0 until (p.nDataBit / 8)) {
          when (w_s1((b + 1) * 8 - 1, b * 8).asUInt.orR) {
            w_byte(b) := Cat(Fill(8, 1.B))
          }.otherwise {
            w_byte(b) := 0.U
          }          
        }

        w_res := w_byte.asUInt
      }
      is (REV8) {
        val w_byte = Wire(Vec((p.nDataBit / 8), UInt(8.W)))

        for (b <- 0 until (p.nDataBit / 8)) {
          w_byte(b) := w_s1(p.nDataBit - (8 * b) - 1, p.nDataBit - (8 * (b + 1)))       
        }

        w_res := w_byte.asUInt
      }
      is (BCLR) {
        val w_bit = Wire(Vec(p.nDataBit, Bool()))
        
        for (b <- 0 until p.nDataBit) {
          w_bit(b) := (b.U =/= w_amount) & w_s1(b)
        }

        w_res := w_bit.asUInt
      }
      is (BEXT) {
        w_res := w_s1(w_amount)
      }
      is (BINV) {
        val w_bit = Wire(Vec(p.nDataBit, Bool()))
        
        for (b <- 0 until p.nDataBit) {
          w_bit(b) := (b.U =/= w_amount) & w_s1(b) | ((b.U === w_amount) & ~w_s1(b))
        }

        w_res := w_bit.asUInt
      }
      is (BSET) {
        val w_bit = Wire(Vec(p.nDataBit, Bool()))
        
        for (b <- 0 until p.nDataBit) {
          w_bit(b) := (b.U =/= w_amount) & w_s1(b) | (b.U === w_amount)
        }

        w_res := w_bit.asUInt
      }
    }
  }
  
  // ******************************
  //      FINAL RESULT & BYPASS
  // ******************************
  // Format final result
  val w_fin = Wire(UInt(p.nDataBit.W))

  if (p.nDataBit >= 64) {
    when (io.b_in.ctrl.get.ctrl.rsize === INTSIZE.W) {
      w_fin := Mux(w_sign, Cat(Fill(32, w_res(31)), w_res(31, 0)), Cat(Fill(32, 0.B), w_res(31, 0)))
    }.otherwise {
      w_fin := w_res
    }    
  } else {
    w_fin := w_res
  }

  io.o_byp.valid := io.b_in.valid & (io.b_in.ctrl.get.gpr.rdp =/= REG.X0.U)
  io.o_byp.done := true.B
  io.o_byp.rdp := io.b_in.ctrl.get.gpr.rdp
  io.o_byp.data := w_fin

  // ******************************
  //           REGISTERS
  // ******************************
  val m_out = Module(new SpecReg(p, new IntUnitCtrlBus(p), UInt(p.nDataBit.W), true, p.nSpecBranch))
  val r_write = RegInit(true.B)

  val w_out = Wire(new GenVBus(p, new IntUnitCtrlBus(p), UInt(p.nDataBit.W)))

  // Output register
  w_lock := ~m_out.io.b_in.ready & ~(io.i_br_new.valid & w_out.ctrl.get.br.mask(io.i_br_new.tag))
  w_out := m_out.io.o_val

  m_out.io.i_flush := io.i_flush | (io.i_br_new.valid & w_out.ctrl.get.br.mask(io.i_br_new.tag))
  m_out.io.i_br_up := io.i_br_up 

  m_out.io.b_in.valid := io.b_in.valid & ~w_flush
  m_out.io.b_in.ctrl.get := io.b_in.ctrl.get

  m_out.io.b_in.data.get := w_fin

  m_out.io.b_out.ready := (~r_write | io.b_write.ready) & io.b_end.ready

  // Write register
  when (r_write) {
    r_write := ~(w_out.valid & io.b_write.ready & ~io.b_end.ready)
  }.otherwise {
    r_write := io.b_end.ready
  }  

  // ******************************
  //             I/Os
  // ******************************
  io.b_in.ready := ~w_lock

  io.b_write.valid := m_out.io.b_out.valid & ~(io.i_br_new.valid & w_out.ctrl.get.br.mask(io.i_br_new.tag)) & ~w_flush & r_write
  io.b_write.rdp := m_out.io.b_out.ctrl.get.gpr.rdp
  io.b_write.data := m_out.io.b_out.data.get

  io.b_end.valid := m_out.io.b_out.valid & ~(io.i_br_new.valid & w_out.ctrl.get.br.mask(io.i_br_new.tag)) & ~w_flush & (io.b_write.ready | ~r_write)
  io.b_end.entry := m_out.io.b_out.ctrl.get.info.entry
  io.b_end.replay := false.B
  io.b_end.trap := DontCare
  io.b_end.trap.valid := false.B
  io.b_end.hpc := 0.U.asTypeOf(io.b_end.hpc)

  // ******************************
  //             FREE
  // ******************************
  io.o_free := ~w_out.valid

  // ******************************
  //             DEBUG
  // ******************************
  if (p.debug) {
    // ------------------------------
    //            SIGNALS
    // ------------------------------
    dontTouch(w_out.ctrl.get.info)
    dontTouch(io.b_end)

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

object Alu extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new Alu(IntUnitConfigBase), args)
}

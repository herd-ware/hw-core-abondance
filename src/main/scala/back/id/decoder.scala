/*
 * File: decoder.scala                                                         *
 * Created Date: 2023-02-26 09:21:29 am                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-26 09:27:26 am                                       *
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

import herd.common.isa.base.{INSTR => BASE, REG, CBIE}
import herd.common.isa.priv.{INSTR => PRIV, EXC => PRIVEXC}
import herd.common.isa.ceps.{INSTR => CEPS, EXC => CEPSEXC}
import herd.core.aubrac.common._
import herd.core.abondance.int.{INTSIZE}


class Decoder (p: BackParams) extends Module {
  val io = IO(new Bundle {
    val i_instr = Input(UInt(p.nInstrBit.W))

    val o_info = Output(new InfoBus(p.nAddrBit, p.nInstrBit, p.nRobEntry))
    val o_trap = Output(new TrapBus(p.nAddrBit, p.nDataBit))

    val o_br = Output(new BranchDecodeBus())

    val o_ext = Output(new ExtCtrlBus())

    val o_ex = Output(new ExCtrlBus(p))
    val o_data = Output(new DataDecodeBus(p))
  })  

  // ******************************
  //         DECODER LOGIC
  // ******************************
  // Integer table
  var t_int = TABLEINT32I.table
                          t_int ++= TABLEINTCSR.table
  if (p.nDataBit >= 64)   t_int ++= TABLEINT64I.table
  if (p.useExtM) {
                          t_int ++= TABLEINT32M.table
    if (p.nDataBit >= 64) t_int ++= TABLEINT64M.table
  }      
  if (p.useExtA) {
                          t_int ++= TABLEINT32A.table
    if (p.nDataBit >= 64) t_int ++= TABLEINT64A.table
  }        
  if (p.useExtB) {
                          t_int ++= TABLEINT32B.table
    if (p.nDataBit >= 64) t_int ++= TABLEINT64B.table
  }  
  if (p.useExtZifencei)   t_int ++= TABLEINTZIFENCEI.table
  if (p.useExtZicbo)      t_int ++= TABLEINTZICBO.table
  if (p.useCeps)          t_int ++= TABLEINTCEPS.table
  if (!p.useCeps)         t_int ++= TABLEINTPRIV.table

  // LSU table
  var t_lsu = TABLELSU32I.table
  if (p.nDataBit >= 64)   t_lsu ++= TABLELSU64I.table    
  if (p.useExtA) {
                          t_lsu ++= TABLELSU32A.table
    if (p.nDataBit >= 64) t_lsu ++= TABLELSU64A.table
  } 

  // 64 bit table
  var t_64b = TABLE64BI.table
  if (p.useExtM)          t_64b ++= TABLE64BM.table
  if (p.useExtA)          t_64b ++= TABLE64BA.table 
  if (p.useExtB)          t_64b ++= TABLE64BB.table   

  // External table
  var t_ext = TABLEEXT32I.table
  if (p.useCeps)          t_ext ++= TABLEEXTDMU.table

  // Branch table
  var t_br = TABLEBR32I.table
  if (p.useExtZifencei)   t_br ++= TABLEBRZIFENCEI.table

  // Decoded signals
  val w_dec_int = ListLookup(io.i_instr, TABLEINT32I.default, t_int)
  val w_dec_lsu = ListLookup(io.i_instr, TABLELSU32I.default, t_lsu)
  val w_dec_64b = ListLookup(io.i_instr, TABLE64BI.default,   t_64b)
  val w_dec_ext = ListLookup(io.i_instr, TABLEEXT32I.default, t_ext)
  val w_dec_br  = ListLookup(io.i_instr, TABLEBR32I.default,  t_br)

  // ******************************
  //            INFO BUS
  // ******************************
  io.o_info.pc := 0.U
  io.o_info.instr := io.i_instr
  io.o_info.ser := w_dec_int(1)
  io.o_info.entry := DontCare

  // ******************************
  //         EXCEPTION BUS
  // ******************************
  io.o_trap.gen := w_dec_int(3)
  io.o_trap.valid := ~w_dec_int(0)
  io.o_trap.pc := DontCare
  io.o_trap.src := TRAPSRC.EXC  
  io.o_trap.info := DontCare

  if (p.useCeps) {
    io.o_trap.cause := CEPSEXC.IINSTR.U
    when (io.i_instr === CEPS.WFI) {
      io.o_trap.valid := true.B
      io.o_trap.src := TRAPSRC.WFI
    }
  } else {
    io.o_trap.cause := PRIVEXC.IINSTR.U
    when (io.i_instr === PRIV.WFI) {
      io.o_trap.valid := true.B
      io.o_trap.src := TRAPSRC.WFI
    }.elsewhen(io.i_instr === PRIV.MRET) {
      io.o_trap.valid := true.B
      io.o_trap.src := TRAPSRC.MRET
    }
  }
  
  // ******************************
  //            EXTERNAL
  // ******************************
  // ------------------------------
  //             COMMON
  // ------------------------------
  io.o_ext.code := w_dec_ext(0)
  io.o_ext.op1 := w_dec_ext(1)
  io.o_ext.op2 := w_dec_ext(2)
  io.o_ext.op3 := w_dec_ext(3)
  io.o_ext.rs1 := io.i_instr(19,15)
  io.o_ext.rs2 := io.i_instr(24,20)
  io.o_ext.rd := io.i_instr(11, 7)

  // ******************************
  //           BRANCH BUS
  // ******************************
  io.o_br.jal := w_dec_br(0)
  io.o_br.br := w_dec_br(1)

  // ******************************
  //            EX BUS
  // ******************************
  io.o_ex.ex_type := w_dec_int(4)

  // ------------------------------
  //            INT BUS
  // ------------------------------
  io.o_ex.int.unit := w_dec_int(5)
  io.o_ex.int.uop := w_dec_int(6)
  io.o_ex.int.call := (io.i_instr(11, 7) === REG.X1.U) | (io.i_instr(11, 7) === REG.X5.U)
  io.o_ex.int.ret := ((io.i_instr(19, 15) === REG.X1.U) | (io.i_instr(19, 15) === REG.X5.U)) & (io.i_instr(19, 15) =/= io.i_instr(11, 7))
  io.o_ex.int.ssign(0) := w_dec_int(7)
  io.o_ex.int.ssign(1) := w_dec_int(8)
  io.o_ex.int.ssign(2) := w_dec_int(9)

  if (p.nDataBit >= 64) {
    io.o_ex.int.ssize(0) := w_dec_64b(0)
    io.o_ex.int.ssize(1) := w_dec_64b(1)
    io.o_ex.int.ssize(2) := w_dec_64b(2)
    io.o_ex.int.rsize := w_dec_64b(3)
  } else {
    io.o_ex.int.ssize(0) := INTSIZE.X
    io.o_ex.int.ssize(1) := INTSIZE.X
    io.o_ex.int.ssize(2) := INTSIZE.X
    io.o_ex.int.rsize := INTSIZE.X
  }

  // ------------------------------
  //            LSU BUS
  // ------------------------------
  io.o_ex.lsu.uop := w_dec_lsu(0)
  io.o_ex.lsu.size := w_dec_lsu(1)
  io.o_ex.lsu.sign := w_dec_lsu(2)
  io.o_ex.lsu.amo := w_dec_lsu(3)

  // ------------------------------
  //            GPR BUS
  // ------------------------------
  io.o_ex.gpr.en := w_dec_int(2)
  io.o_ex.gpr.rdp := DontCare

  // ******************************
  //            DATA BUS
  // ******************************
  io.o_data.s1type := w_dec_int(10)
  io.o_data.s2type := w_dec_int(11)
  io.o_data.s3type := w_dec_int(12)
  io.o_data.imm1type := w_dec_int(13)
  io.o_data.imm2type := w_dec_int(14)
  io.o_data.rs1l := io.i_instr(19, 15)
  io.o_data.rs2l := io.i_instr(24, 20)
  io.o_data.rdl := io.i_instr(11, 7)
}

object Decoder extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new Decoder(BackConfigBase), args)
}

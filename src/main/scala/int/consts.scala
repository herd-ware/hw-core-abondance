/*
 * File: consts.scala
 * Created Date: 2023-02-26 09:21:29 am                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-03-03 04:26:53 pm
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


// ******************************
//             UNIT
// ******************************
object INTUNIT {
  def NBIT  = 3
  def X     = 0.U(NBIT.W)

  def ALU   = 1.U(NBIT.W)
  def BRU   = 2.U(NBIT.W)
  def MUL   = 3.U(NBIT.W)
  def DIV   = 4.U(NBIT.W)
  def CSR   = 5.U(NBIT.W)
  def BALU  = 6.U(NBIT.W)
  def CLMUL = 7.U(NBIT.W)
}

// ******************************
//              UOP
// ******************************
object INTUOP {
  def NBIT    = 5
  def X       = 0.U(NBIT.W)

  // ------------------------------
  //              BRU
  // ------------------------------  
  def JAL     = 1.U(NBIT.W)
  def JALR    = 2.U(NBIT.W)

  def BEQ     = 3.U(NBIT.W)
  def BNE     = 4.U(NBIT.W)
  def BLT     = 5.U(NBIT.W)
  def BGE     = 6.U(NBIT.W)

  def FENCE   = 12.U(NBIT.W)
  def FENCEI  = 13.U(NBIT.W)

  def CLEAN   = 16.U(NBIT.W)
  def INVAL   = 17.U(NBIT.W)
  def FLUSH   = 18.U(NBIT.W)
  def ZERO    = 19.U(NBIT.W)
  def PFTCHE  = 20.U(NBIT.W)
  def PFTCHR  = 21.U(NBIT.W)
  def PFTCHW  = 22.U(NBIT.W)

  // ------------------------------
  //             ALU
  // ------------------------------
  def ADD     = 1.U(NBIT.W)
  def SUB     = 2.U(NBIT.W)
  def SLT     = 3.U(NBIT.W)
  def OR      = 4.U(NBIT.W)
  def AND     = 5.U(NBIT.W)
  def XOR     = 6.U(NBIT.W)
  def SHR     = 7.U(NBIT.W)
  def SHL     = 8.U(NBIT.W)

  // ------------------------------
  //             BLU
  // ------------------------------
  def SH1ADD  = 9.U(NBIT.W)
  def SH2ADD  = 10.U(NBIT.W)
  def SH3ADD  = 11.U(NBIT.W)
  def ORN     = 12.U(NBIT.W)
  def ANDN    = 13.U(NBIT.W)
  def XNOR    = 14.U(NBIT.W)
  def CLZ     = 15.U(NBIT.W)
  def CTZ     = 16.U(NBIT.W)
  def CPOP    = 17.U(NBIT.W)
  def MAX     = 18.U(NBIT.W)
  def MIN     = 19.U(NBIT.W)
  def EXTB    = 20.U(NBIT.W)
  def EXTH    = 21.U(NBIT.W)
  def ROL     = 22.U(NBIT.W)
  def ROR     = 23.U(NBIT.W)
  def ORCB    = 24.U(NBIT.W)
  def REV8    = 25.U(NBIT.W)
  
  def BCLR    = 26.U(NBIT.W)
  def BEXT    = 27.U(NBIT.W)
  def BINV    = 28.U(NBIT.W)
  def BSET    = 29.U(NBIT.W)

  // ------------------------------
  //             MUL
  // ------------------------------
  def MUL     = 1.U(NBIT.W)
  def MULH    = 2.U(NBIT.W)

  def CLMUL   = 4.U(NBIT.W)
  def CLMULH  = 5.U(NBIT.W)
  def CLMULR  = 6.U(NBIT.W)

  // ------------------------------
  //             DIV
  // ------------------------------
  def DIV     = 1.U(NBIT.W)
  def REM     = 2.U(NBIT.W)

  // ------------------------------
  //             CSR
  // ------------------------------
  def CSRX    = 0.U(NBIT.W)
  def CSRW    = 1.U(NBIT.W)
  def CSRS    = 2.U(NBIT.W)
  def CSRC    = 3.U(NBIT.W)
  def CSRRX   = 4.U(NBIT.W)
  def CSRRW   = 5.U(NBIT.W)
  def CSRRS   = 6.U(NBIT.W)
  def CSRRC   = 7.U(NBIT.W)
}

// ******************************
//             SIZE
// ******************************
object INTSIZE {
  def NBIT  = 1

  def X     = 0.U(NBIT.W)
  def W     = 1.U(NBIT.W)
}

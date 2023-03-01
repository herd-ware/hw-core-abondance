/*
 * File: consts.scala
 * Created Date: 2023-02-26 09:21:29 am                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-03-01 12:23:34 pm
 * Modified By: Mathieu Escouteloup
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.core.abondance.back

import chisel3._
import chisel3.util._


// ******************************
//            PIPELINE
// ******************************
object STATE {
  def NBIT    = 3  

  def RUN     = 0.U(NBIT.W)
  def STOP    = 1.U(NBIT.W)
  def REPLAY  = 2.U(NBIT.W)
  def IRQ     = 3.U(NBIT.W)
  def WFI     = 4.U(NBIT.W)
  def EXC     = 5.U(NBIT.W)

  def MRET    = 6.U(NBIT.W)
  def SRET    = 7.U(NBIT.W)
}

// ******************************
//            EX TYPE
// ******************************
object EXTYPE {
  val NBIT  = 2
  val X     = 0.U(NBIT.W)

  val INT   = 1.U(NBIT.W)
  val LSU   = 2.U(NBIT.W)
  val HFU   = 3.U(NBIT.W)
}

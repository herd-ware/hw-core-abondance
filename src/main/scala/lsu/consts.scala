/*
 * File: consts.scala                                                          *
 * Created Date: 2023-02-26 09:21:29 am                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-26 09:31:22 am                                       *
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

import herd.common.mem.mb4s.{SIZE => MB4SSIZE}


object LSUSIGN {
  val NBIT  = 1
  val X     = 0.U(NBIT.W)

  val U     = 0.U(NBIT.W)
  val S     = 1.U(NBIT.W)
}

object LSUSIZE {
  val NBIT  = 2
  val X     = 0.U(NBIT.W)

  val B     = 0.U(NBIT.W)
  val H     = 1.U(NBIT.W)
  val W     = 2.U(NBIT.W)
  val D     = 3.U(NBIT.W)

  def toMb4s (size: UInt): UInt = {
    val w_mb4s = Wire(UInt(MB4SSIZE.NBIT.W))

    w_mb4s := MB4SSIZE.B0.U
    switch (size) {
      is (B)  {w_mb4s := MB4SSIZE.B1.U}
      is (H)  {w_mb4s := MB4SSIZE.B2.U}
      is (W)  {w_mb4s := MB4SSIZE.B4.U}
      is (D)  {w_mb4s := MB4SSIZE.B8.U}
    }

    return w_mb4s
  }
  
  def format (nbit: Int, in: UInt, size: UInt, sign: UInt): UInt = {
    val w_out = Wire(UInt(32.W))

    w_out := in
    switch (size) {
      is (B)  {w_out := Cat(Mux(sign === LSUSIGN.S, Fill((nbit - 8),  in(7)),   0.U((nbit - 8).W)),   in(7, 0))}
      is (H)  {w_out := Cat(Mux(sign === LSUSIGN.S, Fill((nbit - 16), in(15)),  0.U((nbit - 16).W)),  in(15, 0))}
      is (W)  {w_out := Cat(Mux(sign === LSUSIGN.S, Fill((nbit - 32), in(31)),  0.U((nbit - 32).W)),  in(31, 0))}
      is (D)  {w_out := in}
    }

    return w_out
  }
}

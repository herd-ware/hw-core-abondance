/*
 * File: table-br.scala
 * Created Date: 2023-02-26 09:21:29 am                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-28 10:46:30 pm
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

import herd.common.isa.riscv.{INSTR => RISCV}


trait TABLEBR {
  //                        JAL    Other br
  //                         |         |     
  val default: List[UInt] =
               List[UInt](  0.B,      0.B)
  val table: Array[(BitPat, List[UInt])]
}

object TABLEBR32I extends TABLEBR {
  val table : Array[(BitPat, List[UInt])] =
              Array[(BitPat, List[UInt])](

  //                        JAL    Other br
  //                         |         |               
  RISCV.JAL     -> List(    1.B,      0.B),
  RISCV.JALR    -> List(    0.B,      1.B),
  RISCV.BEQ     -> List(    0.B,      1.B),
  RISCV.BNE     -> List(    0.B,      1.B),
  RISCV.BLT     -> List(    0.B,      1.B),
  RISCV.BGE     -> List(    0.B,      1.B),
  RISCV.BLTU    -> List(    0.B,      1.B),
  RISCV.BGEU    -> List(    0.B,      1.B))        
}

object TABLEBRZIFENCEI extends TABLEBR {
  val table : Array[(BitPat, List[UInt])] =
              Array[(BitPat, List[UInt])](

  //                        JAL    Other br
  //                         |         |         
  RISCV.FENCEI  -> List(    1.B,      0.B))
}
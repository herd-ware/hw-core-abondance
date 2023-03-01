/*
 * File: table-64b.scala
 * Created Date: 2023-02-26 09:21:29 am                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-28 10:47:54 pm
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
import herd.core.abondance.int.{INTSIZE}


trait TABLE64B {
  //                               S1 size       S2 size     S3 size      Res size
  //                                 |             |           |             |
  val default: List[UInt] =
               List[UInt](    INTSIZE.X,    INTSIZE.X,  INTSIZE.X,    INTSIZE.X)
  val table: Array[(BitPat, List[UInt])]
}

object TABLE64BI extends TABLE64B {
  val table : Array[(BitPat, List[UInt])] =
              Array[(BitPat, List[UInt])](
  //                               S1 size       S2 size     S3 size      Res size
  //                                 |             |           |             |
  RISCV.ADDIW     -> List(    INTSIZE.W,    INTSIZE.W,  INTSIZE.X,    INTSIZE.W),
  RISCV.SLLIW     -> List(    INTSIZE.W,    INTSIZE.X,  INTSIZE.X,    INTSIZE.W),
  RISCV.SRLIW     -> List(    INTSIZE.W,    INTSIZE.X,  INTSIZE.X,    INTSIZE.W),
  RISCV.SRAIW     -> List(    INTSIZE.W,    INTSIZE.X,  INTSIZE.X,    INTSIZE.W),
  RISCV.ADDW      -> List(    INTSIZE.W,    INTSIZE.W,  INTSIZE.X,    INTSIZE.W),
  RISCV.SUBW      -> List(    INTSIZE.W,    INTSIZE.W,  INTSIZE.X,    INTSIZE.W),
  RISCV.SLLW      -> List(    INTSIZE.W,    INTSIZE.X,  INTSIZE.X,    INTSIZE.W),
  RISCV.SRLW      -> List(    INTSIZE.W,    INTSIZE.X,  INTSIZE.X,    INTSIZE.W),
  RISCV.SRAW      -> List(    INTSIZE.W,    INTSIZE.X,  INTSIZE.X,    INTSIZE.W))
}

object TABLE64BM extends TABLE64B {
  val table : Array[(BitPat, List[UInt])] =
              Array[(BitPat, List[UInt])](
  //                               S1 size       S2 size     S3 size      Res size
  //                                 |             |           |             |
  RISCV.MULW      -> List(    INTSIZE.W,    INTSIZE.W,  INTSIZE.X,    INTSIZE.W),
  RISCV.DIVW      -> List(    INTSIZE.W,    INTSIZE.W,  INTSIZE.X,    INTSIZE.W),
  RISCV.DIVUW     -> List(    INTSIZE.W,    INTSIZE.W,  INTSIZE.X,    INTSIZE.W),
  RISCV.REMW      -> List(    INTSIZE.W,    INTSIZE.W,  INTSIZE.X,    INTSIZE.W),
  RISCV.REMUW     -> List(    INTSIZE.W,    INTSIZE.W,  INTSIZE.X,    INTSIZE.W))
}

object TABLE64BA extends TABLE64B {
  val table : Array[(BitPat, List[UInt])] =
              Array[(BitPat, List[UInt])](
  //                               S1 size       S2 size     S3 size      Res size
  //                                 |             |           |             |
  RISCV.LRW         -> List(  INTSIZE.X,    INTSIZE.X,  INTSIZE.X,    INTSIZE.W), 
  RISCV.SCW         -> List(  INTSIZE.X,    INTSIZE.X,  INTSIZE.X,    INTSIZE.W), 
  RISCV.AMOSWAPW    -> List(  INTSIZE.X,    INTSIZE.X,  INTSIZE.X,    INTSIZE.W), 
  RISCV.AMOADDW     -> List(  INTSIZE.X,    INTSIZE.X,  INTSIZE.X,    INTSIZE.W), 
  RISCV.AMOXORW     -> List(  INTSIZE.X,    INTSIZE.X,  INTSIZE.X,    INTSIZE.W), 
  RISCV.AMOANDW     -> List(  INTSIZE.X,    INTSIZE.X,  INTSIZE.X,    INTSIZE.W), 
  RISCV.AMOORW      -> List(  INTSIZE.X,    INTSIZE.X,  INTSIZE.X,    INTSIZE.W), 
  RISCV.AMOMINW     -> List(  INTSIZE.X,    INTSIZE.X,  INTSIZE.X,    INTSIZE.W), 
  RISCV.AMOMAXW     -> List(  INTSIZE.X,    INTSIZE.X,  INTSIZE.X,    INTSIZE.W), 
  RISCV.AMOMINUW    -> List(  INTSIZE.X,    INTSIZE.X,  INTSIZE.X,    INTSIZE.W), 
  RISCV.AMOMAXUW    -> List(  INTSIZE.X,    INTSIZE.X,  INTSIZE.X,    INTSIZE.W)) 
}

object TABLE64BB extends TABLE64B {
  val table : Array[(BitPat, List[UInt])] =
              Array[(BitPat, List[UInt])](
  //                               S1 size       S2 size     S3 size      Res size
  //                                 |             |           |             |
  RISCV.ADDUW       -> List(  INTSIZE.W,    INTSIZE.X,  INTSIZE.X,    INTSIZE.X),
  RISCV.SH1ADDUW    -> List(  INTSIZE.W,    INTSIZE.X,  INTSIZE.X,    INTSIZE.X),
  RISCV.SH2ADDUW    -> List(  INTSIZE.W,    INTSIZE.X,  INTSIZE.X,    INTSIZE.X),
  RISCV.SH3ADDUW    -> List(  INTSIZE.W,    INTSIZE.X,  INTSIZE.X,    INTSIZE.X),
  RISCV.SLLIUW      -> List(  INTSIZE.W,    INTSIZE.X,  INTSIZE.X,    INTSIZE.X),
  RISCV.CLZW        -> List(  INTSIZE.W,    INTSIZE.X,  INTSIZE.X,    INTSIZE.X),
  RISCV.CTZW        -> List(  INTSIZE.W,    INTSIZE.X,  INTSIZE.X,    INTSIZE.X),
  RISCV.CPOPW       -> List(  INTSIZE.W,    INTSIZE.X,  INTSIZE.X,    INTSIZE.X),
  RISCV.ROLW        -> List(  INTSIZE.W,    INTSIZE.X,  INTSIZE.X,    INTSIZE.X),
  RISCV.RORW        -> List(  INTSIZE.W,    INTSIZE.X,  INTSIZE.X,    INTSIZE.X),
  RISCV.RORIW       -> List(  INTSIZE.W,    INTSIZE.X,  INTSIZE.X,    INTSIZE.X))
}
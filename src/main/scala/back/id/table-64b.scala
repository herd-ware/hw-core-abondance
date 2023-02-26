/*
 * File: table-64b.scala                                                       *
 * Created Date: 2023-02-26 09:21:29 am                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-26 09:27:38 am                                       *
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

import herd.common.isa.base.{INSTR => BASE}
import herd.core.abondance.int.{INTSIZE}


trait TABLE64B {
  //                             S1 size       S2 size     S3 size      Res size
  //                               |             |           |             |
  val default: List[UInt] =
               List[UInt](  INTSIZE.X,    INTSIZE.X,  INTSIZE.X,    INTSIZE.X)
  val table: Array[(BitPat, List[UInt])]
}

object TABLE64BI extends TABLE64B {
  val table : Array[(BitPat, List[UInt])] =
              Array[(BitPat, List[UInt])](
  //                             S1 size       S2 size     S3 size      Res size
  //                               |             |           |             |
  BASE.ADDIW    -> List(    INTSIZE.W,    INTSIZE.W,  INTSIZE.X,    INTSIZE.W),
  BASE.SLLIW    -> List(    INTSIZE.W,    INTSIZE.X,  INTSIZE.X,    INTSIZE.W),
  BASE.SRLIW    -> List(    INTSIZE.W,    INTSIZE.X,  INTSIZE.X,    INTSIZE.W),
  BASE.SRAIW    -> List(    INTSIZE.W,    INTSIZE.X,  INTSIZE.X,    INTSIZE.W),
  BASE.ADDW     -> List(    INTSIZE.W,    INTSIZE.W,  INTSIZE.X,    INTSIZE.W),
  BASE.SUBW     -> List(    INTSIZE.W,    INTSIZE.W,  INTSIZE.X,    INTSIZE.W),
  BASE.SLLW     -> List(    INTSIZE.W,    INTSIZE.X,  INTSIZE.X,    INTSIZE.W),
  BASE.SRLW     -> List(    INTSIZE.W,    INTSIZE.X,  INTSIZE.X,    INTSIZE.W),
  BASE.SRAW     -> List(    INTSIZE.W,    INTSIZE.X,  INTSIZE.X,    INTSIZE.W))
}

object TABLE64BM extends TABLE64B {
  val table : Array[(BitPat, List[UInt])] =
              Array[(BitPat, List[UInt])](
  //                             S1 size       S2 size     S3 size      Res size
  //                               |             |           |             |
  BASE.MULW     -> List(    INTSIZE.W,    INTSIZE.W,  INTSIZE.X,    INTSIZE.W),
  BASE.DIVW     -> List(    INTSIZE.W,    INTSIZE.W,  INTSIZE.X,    INTSIZE.W),
  BASE.DIVUW    -> List(    INTSIZE.W,    INTSIZE.W,  INTSIZE.X,    INTSIZE.W),
  BASE.REMW     -> List(    INTSIZE.W,    INTSIZE.W,  INTSIZE.X,    INTSIZE.W),
  BASE.REMUW    -> List(    INTSIZE.W,    INTSIZE.W,  INTSIZE.X,    INTSIZE.W))
}

object TABLE64BA extends TABLE64B {
  val table : Array[(BitPat, List[UInt])] =
              Array[(BitPat, List[UInt])](
  //                             S1 size       S2 size     S3 size      Res size
  //                               |             |           |             |
  BASE.LRW        -> List(  INTSIZE.X,    INTSIZE.X,  INTSIZE.X,    INTSIZE.W), 
  BASE.SCW        -> List(  INTSIZE.X,    INTSIZE.X,  INTSIZE.X,    INTSIZE.W), 
  BASE.AMOSWAPW   -> List(  INTSIZE.X,    INTSIZE.X,  INTSIZE.X,    INTSIZE.W), 
  BASE.AMOADDW    -> List(  INTSIZE.X,    INTSIZE.X,  INTSIZE.X,    INTSIZE.W), 
  BASE.AMOXORW    -> List(  INTSIZE.X,    INTSIZE.X,  INTSIZE.X,    INTSIZE.W), 
  BASE.AMOANDW    -> List(  INTSIZE.X,    INTSIZE.X,  INTSIZE.X,    INTSIZE.W), 
  BASE.AMOORW     -> List(  INTSIZE.X,    INTSIZE.X,  INTSIZE.X,    INTSIZE.W), 
  BASE.AMOMINW    -> List(  INTSIZE.X,    INTSIZE.X,  INTSIZE.X,    INTSIZE.W), 
  BASE.AMOMAXW    -> List(  INTSIZE.X,    INTSIZE.X,  INTSIZE.X,    INTSIZE.W), 
  BASE.AMOMINUW   -> List(  INTSIZE.X,    INTSIZE.X,  INTSIZE.X,    INTSIZE.W), 
  BASE.AMOMAXUW   -> List(  INTSIZE.X,    INTSIZE.X,  INTSIZE.X,    INTSIZE.W)) 
}

object TABLE64BB extends TABLE64B {
  val table : Array[(BitPat, List[UInt])] =
              Array[(BitPat, List[UInt])](
  //                             S1 size       S2 size     S3 size      Res size
  //                               |             |           |             |
  BASE.ADDUW    -> List(    INTSIZE.W,    INTSIZE.X,  INTSIZE.X,    INTSIZE.X),
  BASE.SH1ADDUW -> List(    INTSIZE.W,    INTSIZE.X,  INTSIZE.X,    INTSIZE.X),
  BASE.SH2ADDUW -> List(    INTSIZE.W,    INTSIZE.X,  INTSIZE.X,    INTSIZE.X),
  BASE.SH3ADDUW -> List(    INTSIZE.W,    INTSIZE.X,  INTSIZE.X,    INTSIZE.X),
  BASE.SLLIUW   -> List(    INTSIZE.W,    INTSIZE.X,  INTSIZE.X,    INTSIZE.X),
  BASE.CLZW     -> List(    INTSIZE.W,    INTSIZE.X,  INTSIZE.X,    INTSIZE.X),
  BASE.CTZW     -> List(    INTSIZE.W,    INTSIZE.X,  INTSIZE.X,    INTSIZE.X),
  BASE.CPOPW    -> List(    INTSIZE.W,    INTSIZE.X,  INTSIZE.X,    INTSIZE.X),
  BASE.ROLW     -> List(    INTSIZE.W,    INTSIZE.X,  INTSIZE.X,    INTSIZE.X),
  BASE.RORW     -> List(    INTSIZE.W,    INTSIZE.X,  INTSIZE.X,    INTSIZE.X),
  BASE.RORIW    -> List(    INTSIZE.W,    INTSIZE.X,  INTSIZE.X,    INTSIZE.X))
}
/*
 * File: table-lsu.scala                                                       *
 * Created Date: 2023-02-26 09:21:29 am                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-26 09:27:53 am                                       *
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
import herd.common.mem.mb4s.{OP => LSUUOP, AMO => LSUAMO}
import herd.core.abondance.lsu.{LSUSIZE, LSUSIGN}


trait TABLELSU {
  //                        Lsu Uop      Lsu size      Lsu sign      Amo op
  //                           |            |             |             |
  //                           |            |             |             |
  //                           |            |             |             |
  val default: List[UInt] =
               List[UInt](  LSUUOP.X,   LSUSIZE.X,    LSUSIGN.X,    LSUAMO.X)
  val table: Array[(BitPat, List[UInt])]
}

object TABLELSU32I extends TABLELSU {
  val table : Array[(BitPat, List[UInt])] =
              Array[(BitPat, List[UInt])](

  //                        Lsu Uop      Lsu size      Lsu sign      Amo op
  //                           |            |             |             |
  //                           |            |             |             |
  //                           |            |             |             |
  BASE.LB       -> List(    LSUUOP.R,   LSUSIZE.B,    LSUSIGN.S,    LSUAMO.X),
  BASE.LBU      -> List(    LSUUOP.R,   LSUSIZE.B,    LSUSIGN.U,    LSUAMO.X),
  BASE.LH       -> List(    LSUUOP.R,   LSUSIZE.H,    LSUSIGN.S,    LSUAMO.X),
  BASE.LHU      -> List(    LSUUOP.R,   LSUSIZE.H,    LSUSIGN.U,    LSUAMO.X),
  BASE.LW       -> List(    LSUUOP.R,   LSUSIZE.W,    LSUSIGN.S,    LSUAMO.X),
  BASE.SB       -> List(    LSUUOP.W,   LSUSIZE.B,    LSUSIGN.S,    LSUAMO.X),
  BASE.SH       -> List(    LSUUOP.W,   LSUSIZE.H,    LSUSIGN.S,    LSUAMO.X),
  BASE.SW       -> List(    LSUUOP.W,   LSUSIZE.W,    LSUSIGN.S,    LSUAMO.X))
}

object TABLELSU64I extends TABLELSU {
  val table : Array[(BitPat, List[UInt])] =
              Array[(BitPat, List[UInt])](

  //                        Lsu Uop      Lsu size      Lsu sign      Amo op
  //                           |            |             |             |
  //                           |            |             |             |
  //                           |            |             |             |
  BASE.LWU      -> List(    LSUUOP.R,   LSUSIZE.W,    LSUSIGN.U,    LSUAMO.X),
  BASE.LD       -> List(    LSUUOP.R,   LSUSIZE.D,    LSUSIGN.S,    LSUAMO.X),
  BASE.SD       -> List(    LSUUOP.W,   LSUSIZE.D,    LSUSIGN.S,    LSUAMO.X))
}

object TABLELSU32A extends TABLELSU {
  val table : Array[(BitPat, List[UInt])] =
              Array[(BitPat, List[UInt])](

  //                        Lsu Uop      Lsu size      Lsu sign      Amo op
  //                           |            |             |             |
  //                           |            |             |             |
  //                           |            |             |             |
  BASE.LRW      -> List(    LSUUOP.LR,  LSUSIZE.W,    LSUSIGN.S,    LSUAMO.X), 
  BASE.SCW      -> List(    LSUUOP.SC,  LSUSIZE.W,    LSUSIGN.S,    LSUAMO.X), 
  BASE.AMOSWAPW -> List(    LSUUOP.AMO, LSUSIZE.W,    LSUSIGN.S,    LSUAMO.SWAP), 
  BASE.AMOADDW  -> List(    LSUUOP.AMO, LSUSIZE.W,    LSUSIGN.S,    LSUAMO.ADD), 
  BASE.AMOXORW  -> List(    LSUUOP.AMO, LSUSIZE.W,    LSUSIGN.S,    LSUAMO.XOR), 
  BASE.AMOANDW  -> List(    LSUUOP.AMO, LSUSIZE.W,    LSUSIGN.S,    LSUAMO.AND), 
  BASE.AMOORW   -> List(    LSUUOP.AMO, LSUSIZE.W,    LSUSIGN.S,    LSUAMO.OR), 
  BASE.AMOMINW  -> List(    LSUUOP.AMO, LSUSIZE.W,    LSUSIGN.S,    LSUAMO.MIN), 
  BASE.AMOMAXW  -> List(    LSUUOP.AMO, LSUSIZE.W,    LSUSIGN.S,    LSUAMO.MAX), 
  BASE.AMOMINUW -> List(    LSUUOP.AMO, LSUSIZE.W,    LSUSIGN.S,    LSUAMO.MINU), 
  BASE.AMOMAXUW -> List(    LSUUOP.AMO, LSUSIZE.W,    LSUSIGN.S,    LSUAMO.MAXU)) 
}

object TABLELSU64A extends TABLELSU {
  val table : Array[(BitPat, List[UInt])] =
              Array[(BitPat, List[UInt])](

  //                        Lsu Uop      Lsu size      Lsu sign      Amo op
  //                           |            |             |             |
  //                           |            |             |             |
  //                           |            |             |             |
  BASE.LRD      -> List(    LSUUOP.LR,  LSUSIZE.D,    LSUSIGN.S,    LSUAMO.X), 
  BASE.SCD      -> List(    LSUUOP.SC,  LSUSIZE.D,    LSUSIGN.S,    LSUAMO.X), 
  BASE.AMOSWAPD -> List(    LSUUOP.AMO, LSUSIZE.D,    LSUSIGN.S,    LSUAMO.SWAP), 
  BASE.AMOADDD  -> List(    LSUUOP.AMO, LSUSIZE.D,    LSUSIGN.S,    LSUAMO.ADD), 
  BASE.AMOXORD  -> List(    LSUUOP.AMO, LSUSIZE.D,    LSUSIGN.S,    LSUAMO.XOR), 
  BASE.AMOANDD  -> List(    LSUUOP.AMO, LSUSIZE.D,    LSUSIGN.S,    LSUAMO.AND), 
  BASE.AMOORD   -> List(    LSUUOP.AMO, LSUSIZE.D,    LSUSIGN.S,    LSUAMO.OR), 
  BASE.AMOMIND  -> List(    LSUUOP.AMO, LSUSIZE.D,    LSUSIGN.S,    LSUAMO.MIN), 
  BASE.AMOMAXD  -> List(    LSUUOP.AMO, LSUSIZE.D,    LSUSIGN.S,    LSUAMO.MAX), 
  BASE.AMOMINUD -> List(    LSUUOP.AMO, LSUSIZE.D,    LSUSIGN.S,    LSUAMO.MINU), 
  BASE.AMOMAXUD -> List(    LSUUOP.AMO, LSUSIZE.D,    LSUSIGN.S,    LSUAMO.MAXU)) 
}
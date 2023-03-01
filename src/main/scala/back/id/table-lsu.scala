/*
 * File: table-lsu.scala
 * Created Date: 2023-02-26 09:21:29 am                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-28 10:44:28 pm
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
import herd.common.mem.mb4s.{OP => LSUUOP, AMO => LSUAMO}
import herd.core.abondance.lsu.{LSUSIZE, LSUSIGN}


trait TABLELSU {
  //                          Lsu Uop      Lsu size      Lsu sign      Amo op
  //                             |            |             |             |
  //                             |            |             |             |
  //                             |            |             |             |
  val default: List[UInt] =
               List[UInt](    LSUUOP.X,   LSUSIZE.X,    LSUSIGN.X,    LSUAMO.X)
  val table: Array[(BitPat, List[UInt])]
}

object TABLELSU32I extends TABLELSU {
  val table : Array[(BitPat, List[UInt])] =
              Array[(BitPat, List[UInt])](

  //                          Lsu Uop      Lsu size      Lsu sign      Amo op
  //                             |            |             |             |
  //                             |            |             |             |
  //                             |            |             |             |
  RISCV.LB        -> List(    LSUUOP.R,   LSUSIZE.B,    LSUSIGN.S,    LSUAMO.X),
  RISCV.LBU       -> List(    LSUUOP.R,   LSUSIZE.B,    LSUSIGN.U,    LSUAMO.X),
  RISCV.LH        -> List(    LSUUOP.R,   LSUSIZE.H,    LSUSIGN.S,    LSUAMO.X),
  RISCV.LHU       -> List(    LSUUOP.R,   LSUSIZE.H,    LSUSIGN.U,    LSUAMO.X),
  RISCV.LW        -> List(    LSUUOP.R,   LSUSIZE.W,    LSUSIGN.S,    LSUAMO.X),
  RISCV.SB        -> List(    LSUUOP.W,   LSUSIZE.B,    LSUSIGN.S,    LSUAMO.X),
  RISCV.SH        -> List(    LSUUOP.W,   LSUSIZE.H,    LSUSIGN.S,    LSUAMO.X),
  RISCV.SW        -> List(    LSUUOP.W,   LSUSIZE.W,    LSUSIGN.S,    LSUAMO.X))
}

object TABLELSU64I extends TABLELSU {
  val table : Array[(BitPat, List[UInt])] =
              Array[(BitPat, List[UInt])](

  //                          Lsu Uop      Lsu size      Lsu sign      Amo op
  //                             |            |             |             |
  //                             |            |             |             |
  //                             |            |             |             |
  RISCV.LWU       -> List(    LSUUOP.R,   LSUSIZE.W,    LSUSIGN.U,    LSUAMO.X),
  RISCV.LD        -> List(    LSUUOP.R,   LSUSIZE.D,    LSUSIGN.S,    LSUAMO.X),
  RISCV.SD        -> List(    LSUUOP.W,   LSUSIZE.D,    LSUSIGN.S,    LSUAMO.X))
}

object TABLELSU32A extends TABLELSU {
  val table : Array[(BitPat, List[UInt])] =
              Array[(BitPat, List[UInt])](

  //                          Lsu Uop      Lsu size      Lsu sign      Amo op
  //                             |            |             |             |
  //                             |            |             |             |
  //                             |            |             |             |
  RISCV.LRW       -> List(    LSUUOP.LR,  LSUSIZE.W,    LSUSIGN.S,    LSUAMO.X), 
  RISCV.SCW       -> List(    LSUUOP.SC,  LSUSIZE.W,    LSUSIGN.S,    LSUAMO.X), 
  RISCV.AMOSWAPW  -> List(    LSUUOP.AMO, LSUSIZE.W,    LSUSIGN.S,    LSUAMO.SWAP), 
  RISCV.AMOADDW   -> List(    LSUUOP.AMO, LSUSIZE.W,    LSUSIGN.S,    LSUAMO.ADD), 
  RISCV.AMOXORW   -> List(    LSUUOP.AMO, LSUSIZE.W,    LSUSIGN.S,    LSUAMO.XOR), 
  RISCV.AMOANDW   -> List(    LSUUOP.AMO, LSUSIZE.W,    LSUSIGN.S,    LSUAMO.AND), 
  RISCV.AMOORW    -> List(    LSUUOP.AMO, LSUSIZE.W,    LSUSIGN.S,    LSUAMO.OR), 
  RISCV.AMOMINW   -> List(    LSUUOP.AMO, LSUSIZE.W,    LSUSIGN.S,    LSUAMO.MIN), 
  RISCV.AMOMAXW   -> List(    LSUUOP.AMO, LSUSIZE.W,    LSUSIGN.S,    LSUAMO.MAX), 
  RISCV.AMOMINUW  -> List(    LSUUOP.AMO, LSUSIZE.W,    LSUSIGN.S,    LSUAMO.MINU), 
  RISCV.AMOMAXUW  -> List(    LSUUOP.AMO, LSUSIZE.W,    LSUSIGN.S,    LSUAMO.MAXU)) 
}

object TABLELSU64A extends TABLELSU {
  val table : Array[(BitPat, List[UInt])] =
              Array[(BitPat, List[UInt])](

  //                          Lsu Uop      Lsu size      Lsu sign      Amo op
  //                             |            |             |             |
  //                             |            |             |             |
  //                             |            |             |             |
  RISCV.LRD       -> List(    LSUUOP.LR,  LSUSIZE.D,    LSUSIGN.S,    LSUAMO.X), 
  RISCV.SCD       -> List(    LSUUOP.SC,  LSUSIZE.D,    LSUSIGN.S,    LSUAMO.X), 
  RISCV.AMOSWAPD  -> List(    LSUUOP.AMO, LSUSIZE.D,    LSUSIGN.S,    LSUAMO.SWAP), 
  RISCV.AMOADDD   -> List(    LSUUOP.AMO, LSUSIZE.D,    LSUSIGN.S,    LSUAMO.ADD), 
  RISCV.AMOXORD   -> List(    LSUUOP.AMO, LSUSIZE.D,    LSUSIGN.S,    LSUAMO.XOR), 
  RISCV.AMOANDD   -> List(    LSUUOP.AMO, LSUSIZE.D,    LSUSIGN.S,    LSUAMO.AND), 
  RISCV.AMOORD    -> List(    LSUUOP.AMO, LSUSIZE.D,    LSUSIGN.S,    LSUAMO.OR), 
  RISCV.AMOMIND   -> List(    LSUUOP.AMO, LSUSIZE.D,    LSUSIGN.S,    LSUAMO.MIN), 
  RISCV.AMOMAXD   -> List(    LSUUOP.AMO, LSUSIZE.D,    LSUSIGN.S,    LSUAMO.MAX), 
  RISCV.AMOMINUD  -> List(    LSUUOP.AMO, LSUSIZE.D,    LSUSIGN.S,    LSUAMO.MINU), 
  RISCV.AMOMAXUD  -> List(    LSUUOP.AMO, LSUSIZE.D,    LSUSIGN.S,    LSUAMO.MAXU)) 
}
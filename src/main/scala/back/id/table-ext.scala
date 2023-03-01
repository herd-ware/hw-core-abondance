/*
 * File: table-ext.scala
 * Created Date: 2023-02-26 09:21:29 am                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-03-01 12:21:35 pm
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
import herd.common.isa.champ.{INSTR => CHAMP}
import herd.core.aubrac.hfu.{CODE => HFUCODE, OP => HFUOP}

trait TABLEEXT
{
  //                               Code             S1 ?          S2 ?          S3 ?
  //                                 |               |             |             |
  val default: List[UInt] =
               List[UInt](          0.U,            0.U,          0.U,          0.U)
  val table: Array[(BitPat, List[UInt])]
}

object TABLEEXT32I extends TABLEEXT {
  val table : Array[(BitPat, List[UInt])] =    
              Array[(BitPat, List[UInt])](
  //                               Code             S1 ?          S2 ?          S3 ?
  //                                 |               |             |             |
    RISCV.ADD       -> List(        0.U,            0.U,          0.U,          0.U))
}

object TABLEEXTHFU extends TABLEEXT {
  val table : Array[(BitPat, List[UInt])] =    
              Array[(BitPat, List[UInt])](
  //                               Code             S1 ?          S2 ?          S3 ?
  //                                 |               |             |             |
    CHAMP.ADD       -> List(  HFUCODE.ADD,      HFUOP.VALUE,  HFUOP.IN,     HFUOP.IN  ),
    CHAMP.SUB       -> List(  HFUCODE.SUB,      HFUOP.VALUE,  HFUOP.IN,     HFUOP.IN  ),
    CHAMP.SET       -> List(  HFUCODE.SET,      HFUOP.VALUE,  HFUOP.IN,     HFUOP.IN  ),
    CHAMP.CLEAR     -> List(  HFUCODE.CLEAR,    HFUOP.VALUE,  HFUOP.IN,     HFUOP.IN  ),
    CHAMP.MVCX      -> List(  HFUCODE.MVCX,     HFUOP.VALUE,  HFUOP.IN,     HFUOP.IN  ),
    CHAMP.MVXC      -> List(  HFUCODE.MVXC,     HFUOP.VALUE,  HFUOP.X,      HFUOP.X   ),
    CHAMP.MV        -> List(  HFUCODE.MV,       HFUOP.CONF,   HFUOP.CONF,   HFUOP.X   ),
    CHAMP.LOAD      -> List(  HFUCODE.LOAD,     HFUOP.CONF,   HFUOP.IN,     HFUOP.IN  ),
    CHAMP.STORE     -> List(  HFUCODE.STORE,    HFUOP.CONF,   HFUOP.IN,     HFUOP.IN  ),
    CHAMP.SWITCHV   -> List(  HFUCODE.SWITCHV,  HFUOP.CONF,   HFUOP.X,      HFUOP.X   ),
    CHAMP.SWITCHL   -> List(  HFUCODE.SWITCHL,  HFUOP.CONF,   HFUOP.X,      HFUOP.X   ),
    CHAMP.SWITCHC   -> List(  HFUCODE.SWITCHC,  HFUOP.CONF,   HFUOP.X,      HFUOP.X   ),
    CHAMP.SWITCHJV  -> List(  HFUCODE.SWITCHJV, HFUOP.CONF,   HFUOP.IN,     HFUOP.X   ),
    CHAMP.SWITCHJL  -> List(  HFUCODE.SWITCHJL, HFUOP.CONF,   HFUOP.IN,     HFUOP.X   ),
    CHAMP.SWITCHJC  -> List(  HFUCODE.SWITCHJC, HFUOP.CONF,   HFUOP.IN,     HFUOP.X   ),
    CHAMP.CHECKV    -> List(  HFUCODE.CHECKV,   HFUOP.CONF,   HFUOP.X,      HFUOP.X   ),
    CHAMP.CHECKU    -> List(  HFUCODE.CHECKU,   HFUOP.CONF,   HFUOP.X,      HFUOP.X   ),
    CHAMP.CHECKL    -> List(  HFUCODE.CHECKL,   HFUOP.CONF,   HFUOP.X,      HFUOP.X   ),
    CHAMP.CHECKC    -> List(  HFUCODE.CHECKC,   HFUOP.CONF,   HFUOP.X,      HFUOP.X   ),
    CHAMP.SWITCHRL0 -> List(  HFUCODE.RETL0,    HFUOP.CONF,   HFUOP.TL0EPC, HFUOP.X   ),
    CHAMP.SWITCHRL1 -> List(  HFUCODE.RETL1,    HFUOP.CONF,   HFUOP.TL1EPC, HFUOP.X   ))
}

/*
 * File: table-ext.scala                                                       *
 * Created Date: 2023-02-26 09:21:29 am                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-27 06:28:52 pm                                       *
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
import herd.common.isa.champ.{INSTR => CHAMP}
import herd.core.aubrac.dmu.{CODE => DMUCODE, OP => DMUOP}

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
    BASE.ADD        -> List(        0.U,            0.U,          0.U,          0.U))
}

object TABLEEXTDMU extends TABLEEXT {
  val table : Array[(BitPat, List[UInt])] =    
              Array[(BitPat, List[UInt])](
  //                               Code             S1 ?          S2 ?          S3 ?
  //                                 |               |             |             |
    CHAMP.ADD       -> List(  DMUCODE.ADD,      DMUOP.VALUE,  DMUOP.IN,     DMUOP.IN  ),
    CHAMP.SUB       -> List(  DMUCODE.SUB,      DMUOP.VALUE,  DMUOP.IN,     DMUOP.IN  ),
    CHAMP.SET       -> List(  DMUCODE.SET,      DMUOP.VALUE,  DMUOP.IN,     DMUOP.IN  ),
    CHAMP.CLEAR     -> List(  DMUCODE.CLEAR,    DMUOP.VALUE,  DMUOP.IN,     DMUOP.IN  ),
    CHAMP.MVCX      -> List(  DMUCODE.MVCX,     DMUOP.VALUE,  DMUOP.IN,     DMUOP.IN  ),
    CHAMP.MVXC      -> List(  DMUCODE.MVXC,     DMUOP.VALUE,  DMUOP.X,      DMUOP.X   ),
    CHAMP.MV        -> List(  DMUCODE.MV,       DMUOP.CONF,   DMUOP.CONF,   DMUOP.X   ),
    CHAMP.LOAD      -> List(  DMUCODE.LOAD,     DMUOP.CONF,   DMUOP.IN,     DMUOP.IN  ),
    CHAMP.STORE     -> List(  DMUCODE.STORE,    DMUOP.CONF,   DMUOP.IN,     DMUOP.IN  ),
    CHAMP.SWITCHV   -> List(  DMUCODE.SWITCHV,  DMUOP.CONF,   DMUOP.X,      DMUOP.X   ),
    CHAMP.SWITCHL   -> List(  DMUCODE.SWITCHL,  DMUOP.CONF,   DMUOP.X,      DMUOP.X   ),
    CHAMP.SWITCHC   -> List(  DMUCODE.SWITCHC,  DMUOP.CONF,   DMUOP.X,      DMUOP.X   ),
    CHAMP.SWITCHJV  -> List(  DMUCODE.SWITCHJV, DMUOP.CONF,   DMUOP.IN,     DMUOP.X   ),
    CHAMP.SWITCHJL  -> List(  DMUCODE.SWITCHJL, DMUOP.CONF,   DMUOP.IN,     DMUOP.X   ),
    CHAMP.SWITCHJC  -> List(  DMUCODE.SWITCHJC, DMUOP.CONF,   DMUOP.IN,     DMUOP.X   ),
    CHAMP.CHECKV    -> List(  DMUCODE.CHECKV,   DMUOP.CONF,   DMUOP.X,      DMUOP.X   ),
    CHAMP.CHECKU    -> List(  DMUCODE.CHECKU,   DMUOP.CONF,   DMUOP.X,      DMUOP.X   ),
    CHAMP.CHECKL    -> List(  DMUCODE.CHECKL,   DMUOP.CONF,   DMUOP.X,      DMUOP.X   ),
    CHAMP.CHECKC    -> List(  DMUCODE.CHECKC,   DMUOP.CONF,   DMUOP.X,      DMUOP.X   ),
    CHAMP.SWITCHRL0 -> List(  DMUCODE.RETL0,    DMUOP.CONF,   DMUOP.TL0EPC, DMUOP.X   ),
    CHAMP.SWITCHRL1 -> List(  DMUCODE.RETL1,    DMUOP.CONF,   DMUOP.TL1EPC, DMUOP.X   ))
}

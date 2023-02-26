/*
 * File: table-ext.scala                                                       *
 * Created Date: 2023-02-26 09:21:29 am                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-26 09:27:44 am                                       *
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
import herd.common.isa.ceps.{INSTR => CEPS}
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
    CEPS.ADD        -> List(  DMUCODE.ADD,      DMUOP.FIELD,  DMUOP.IN,     DMUOP.IN  ),
    CEPS.SUB        -> List(  DMUCODE.SUB,      DMUOP.FIELD,  DMUOP.IN,     DMUOP.IN  ),
    CEPS.SET        -> List(  DMUCODE.SET,      DMUOP.FIELD,  DMUOP.IN,     DMUOP.IN  ),
    CEPS.CLEAR      -> List(  DMUCODE.CLEAR,    DMUOP.FIELD,  DMUOP.IN,     DMUOP.IN  ),
    CEPS.MVCX       -> List(  DMUCODE.MVCX,     DMUOP.FIELD,  DMUOP.IN,     DMUOP.IN  ),
    CEPS.MVXC       -> List(  DMUCODE.MVXC,     DMUOP.FIELD,  DMUOP.X,      DMUOP.X   ),
    CEPS.MV         -> List(  DMUCODE.MV,       DMUOP.CONF,   DMUOP.CONF,   DMUOP.X   ),
    CEPS.LOAD       -> List(  DMUCODE.LOAD,     DMUOP.CONF,   DMUOP.IN,     DMUOP.IN  ),
    CEPS.STORE      -> List(  DMUCODE.STORE,    DMUOP.CONF,   DMUOP.IN,     DMUOP.IN  ),
    CEPS.SWITCHV    -> List(  DMUCODE.SWITCHV,  DMUOP.CONF,   DMUOP.X,      DMUOP.X   ),
    CEPS.SWITCHL    -> List(  DMUCODE.SWITCHL,  DMUOP.CONF,   DMUOP.X,      DMUOP.X   ),
    CEPS.SWITCHC    -> List(  DMUCODE.SWITCHC,  DMUOP.CONF,   DMUOP.X,      DMUOP.X   ),
    CEPS.SWITCHJV   -> List(  DMUCODE.SWITCHJV, DMUOP.CONF,   DMUOP.IN,     DMUOP.X   ),
    CEPS.SWITCHJL   -> List(  DMUCODE.SWITCHJL, DMUOP.CONF,   DMUOP.IN,     DMUOP.X   ),
    CEPS.SWITCHJC   -> List(  DMUCODE.SWITCHJC, DMUOP.CONF,   DMUOP.IN,     DMUOP.X   ),
    CEPS.CHECKV     -> List(  DMUCODE.CHECKV,   DMUOP.CONF,   DMUOP.X,      DMUOP.X   ),
    CEPS.CHECKU     -> List(  DMUCODE.CHECKU,   DMUOP.CONF,   DMUOP.X,      DMUOP.X   ),
    CEPS.CHECKL     -> List(  DMUCODE.CHECKL,   DMUOP.CONF,   DMUOP.X,      DMUOP.X   ),
    CEPS.CHECKC     -> List(  DMUCODE.CHECKC,   DMUOP.CONF,   DMUOP.X,      DMUOP.X   ),
    CEPS.SWITCHRL0  -> List(  DMUCODE.RETL0,    DMUOP.CONF,   DMUOP.TL0EPC, DMUOP.X   ),
    CEPS.SWITCHRL1  -> List(  DMUCODE.RETL1,    DMUOP.CONF,   DMUOP.TL1EPC, DMUOP.X   ))
}

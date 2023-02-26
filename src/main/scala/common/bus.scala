/*
 * File: bus.scala                                                             *
 * Created Date: 2023-02-26 09:21:29 am                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-26 09:29:41 am                                       *
 * Modified By: Mathieu Escouteloup                                            *
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.core.abondance.common

import chisel3._
import chisel3.util._


class SpecBus (nSpec: Int) extends Bundle {
  val valid = Bool()
  val tag = UInt(log2Ceil(nSpec).W)
  val mask = UInt(nSpec.W)
}

class SpecCtrlBus (nSpecBranch: Int) extends Bundle {
  val br = new SpecBus(nSpecBranch)
}
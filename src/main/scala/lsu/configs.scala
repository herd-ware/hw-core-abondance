/*
 * File: configs.scala                                                         *
 * Created Date: 2023-02-26 09:21:29 am                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-27 05:25:49 pm                                       *
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

import herd.common.mem.mb4s._


object LsuConfigBase extends LsuConfig (
  debug = true,
  nAddrBit = 32,
  nDataBit = 32,

  useChamp = false,
  nDome = 1,
  nPart = 1,

  nBackPort = 2,
  nRobEntry = 16,
  nSpecBranch = 4,
  nCommit = 2,  
  nGprPhy = 32,
  nBypass = 4,

  useExtA = false,
  nLoadQueue = 16,
  useSpecLoad = true,
  nStoreQueue = 8,
  nMemQueue = 4
)


/*
 * File: configs.scala                                                         *
 * Created Date: 2023-02-26 09:21:29 am                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-26 09:28:14 am                                       *
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

object BackConfigBase extends BackConfig (
  debug = true,
  nAddrBit = 32,
  nDataBit = 32,

  useCeps = false,
  nDome = 1,
  nPart = 1,

  useFastJal = true,

  nBackPort = 2,
  nCommit = 2,
  nRobEntry = 16,
  nSpecBranch = 2,
  useIdStage = true,
  nExUnit = 3,
  nRobPcRead = 2,
  useRobReplay = false,
  nBypass = 5,

  nGprPhy = 64,
  nGprReadPhy = 4,
  nGprReadLog = 6,
  nGprWritePhy = 2,
  nGprWriteLog = 3,

  useExtM = true,
  useExtA = false,
  useExtB = false,
  useExtZifencei = true,
  useExtZicbo = false
)
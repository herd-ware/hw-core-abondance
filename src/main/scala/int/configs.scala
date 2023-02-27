/*
 * File: configs.scala                                                         *
 * Created Date: 2023-02-26 09:21:29 am                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-27 05:25:38 pm                                       *
 * Modified By: Mathieu Escouteloup                                            *
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.core.abondance.int

import chisel3._
import chisel3.util._


object IntUnitIntfBase0 extends IntUnitIntf (
  useAlu = true,
  useAluBypass = true,
  useBru = true,
  useExtB = true,
  useExtZifencei = true,
  useExtZicbo = true,
  useMul = true,
  nMulAddLvl = 4,
  useDiv = false,
  useCsr = false
)

object IntUnitIntfBase1 extends IntUnitIntf (
  useAlu = true,
  useAluBypass = false,
  useBru = false,
  useExtB = false,
  useExtZifencei = false,
  useExtZicbo = false,
  useMul = false,
  nMulAddLvl = 3,
  useDiv = true,
  useCsr = true
)

object IntUnitConfigBase extends IntUnitConfig (
  debug = true,
  nAddrBit = 32,
  nDataBit = 32,

  useChamp = false,
  nDome = 1,
  nPart = 1,

  useFastJal = true,

  nRobEntry = 16,
  nSpecBranch = 2,
  nCommit = 2,
  nGprPhy = 16,

  iIntUnit = IntUnitIntfBase1
)

object IntConfigBase extends IntConfig (
  debug = true,
  nAddrBit = 32,
  nDataBit = 32,

  useChamp = false,
  nDome = 1,
  nPart = 1,
  
  useFastJal = true,
  
  nBackPort = 2,
  nRobEntry = 16,
  nSpecBranch = 4,
  nCommit = 2,
  nGprPhy = 32,
  nBypass = 4,
  
  nIntQueue = 16,
  iIntUnit = Array(IntUnitIntfBase0, IntUnitIntfBase1)
)


/*
 * File: params.scala                                                          *
 * Created Date: 2023-02-26 09:21:29 am                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-27 05:24:50 pm                                       *
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

import herd.core.abondance.common._
import herd.core.abondance.int._


trait BackParams extends ExUnitParams {
  def debug: Boolean
  def nAddrBit: Int
  def nDataBit: Int

  def useChamp: Boolean
  def nDome: Int
  def multiDome: Boolean = false
  def nPart: Int

  def useFastJal: Boolean

  def nBackPort: Int
  def nCommit: Int
  def nRobEntry: Int
  def nSpecBranch: Int
  def useIdStage: Boolean
  def nExUnit: Int
  def nRobPcRead: Int
  def useRobReplay: Boolean
  def nBypass: Int

  def nGprLog: Int = 32
  def nGprPhy: Int
  def nGprReadPhy: Int
  def nGprReadLog: Int
  def nGprWritePhy: Int
  def nGprWriteLog: Int

  def useExtM: Boolean
  def useExtA: Boolean
  def useExtB: Boolean
  def useExtZifencei: Boolean
  def useExtZicbo: Boolean
}

case class BackConfig (
  debug: Boolean,
  nAddrBit: Int,
  nDataBit: Int,

  useChamp: Boolean,
  nDome: Int,
  nPart: Int,

  useFastJal: Boolean,

  nBackPort: Int,
  nCommit: Int,
  nRobEntry: Int,
  nSpecBranch: Int,
  useIdStage: Boolean,
  nExUnit: Int,
  nRobPcRead: Int,
  useRobReplay: Boolean,
  nBypass: Int,

  nGprPhy: Int,
  nGprReadPhy: Int,
  nGprReadLog: Int,
  nGprWritePhy: Int,
  nGprWriteLog: Int,

  useExtM: Boolean,
  useExtA: Boolean,
  useExtB: Boolean,
  useExtZifencei: Boolean,
  useExtZicbo: Boolean
) extends BackParams
/*
 * File: params.scala                                                          *
 * Created Date: 2023-02-26 09:21:29 am                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-27 05:26:04 pm                                       *
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

import herd.common.gen._


// ******************************
//           EX UNIT
// ******************************
trait ExUnitParams extends GenParams {
  def debug: Boolean
  def nHart: Int = 1
  def nAddrBit: Int
  def nDataBit: Int
  def nDataByte: Int = (nDataBit/8).toInt
  def nInstrBit: Int = 32

  def useChamp: Boolean
  def useField: Boolean = useChamp
  def nField: Int
  def multiField: Boolean
  def nPart: Int

  def nBackPort: Int
  def nRobEntry: Int
  def nSpecBranch: Int
  def nCommit: Int
  def nBypass: Int
  def nGprPhy: Int
}
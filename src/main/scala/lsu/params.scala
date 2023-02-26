/*
 * File: params.scala                                                          *
 * Created Date: 2023-02-26 09:21:29 am                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-26 09:31:39 am                                       *
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
import herd.core.abondance.common._


// ******************************
//              LSU
// ******************************
trait LsuParams extends ExUnitParams {
  def debug: Boolean
  def nAddrBit: Int
  def nDataBit: Int

  def useCeps: Boolean
  def nDome: Int
  def multiDome: Boolean = false
  def nPart: Int

  def nBackPort: Int
  def nRobEntry: Int
  def nSpecBranch: Int
  def nCommit: Int
  def nGprPhy: Int
  def nBypass: Int

  def useExtA: Boolean
  def nLoad: Int = 2
  def nLoadQueue: Int
  def useSpecLoad: Boolean
  def nStoreQueue: Int
  def nStoreRead: Int = nLoad + 1
  def nMemQueue: Int
  def nMemOp: Int = 1

  def pL0D0Bus: Mb4sParams = new Mb4sConfig (
    debug = debug,    
    readOnly = true,
    nHart = 1,
    nAddrBit = nAddrBit,
    useAmo = useExtA,
    nDataByte = nDataByte,
    
    useDome = useDome,
    nDome = nDome,
    multiDome = false
  )

  def pL0D1Bus: Mb4sParams = new Mb4sConfig (
    debug = debug,
    readOnly = false,
    nHart = 1,
    nAddrBit = nAddrBit,
    useAmo = useExtA,
    nDataByte = nDataByte,
    
    useDome = useDome,
    nDome = nDome,
    multiDome = false
  )
}

case class LsuConfig (
  debug: Boolean,
  nAddrBit: Int,
  nDataBit: Int,

  useCeps: Boolean,
  nDome: Int,
  nPart: Int,

  nBackPort: Int,
  nRobEntry: Int,
  nSpecBranch: Int,
  nCommit: Int,
  nGprPhy: Int,
  nBypass: Int,

  useExtA: Boolean,
  nLoadQueue: Int,
  useSpecLoad: Boolean,
  nStoreQueue: Int,
  nMemQueue: Int
) extends LsuParams
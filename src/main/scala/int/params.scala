/*
 * File: params.scala                                                          *
 * Created Date: 2023-02-26 09:21:29 am                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-27 05:25:29 pm                                       *
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
import scala.math._

import herd.common.gen._
import herd.core.abondance.common._


// ******************************
//           INT UNIT
// ******************************
trait IntUnitParams extends ExUnitParams {
  def debug: Boolean
  def nAddrBit: Int
  def nDataBit: Int

  def useChamp: Boolean
  def nDome: Int
  def multiDome: Boolean = false
  def nPart: Int
  
  def useFastJal: Boolean

  def nBackPort: Int = 0
  def nRobEntry: Int
  def nSpecBranch: Int
  def nCommit: Int

  def useAlu: Boolean
  def useAluBypass: Boolean
  def useBru: Boolean
  def useExtB: Boolean
  def useExtZifencei: Boolean
  def useExtZicbo: Boolean
  def useCbo: Boolean = useExtZifencei || useExtZicbo
  def useMul: Boolean
  def nMulAddLvl: Int
  def nMulStage: Int = nDataBit / pow(2, nMulAddLvl).toInt 
  def useDiv: Boolean
  def useCsr: Boolean
  def nGprPhy: Int
  def nBypass: Int = if (useAlu && useAluBypass) 3 else 1
}

case class IntUnitIntf (
  useAlu: Boolean,
  useAluBypass: Boolean,
  useBru: Boolean,
  useExtB: Boolean,
  useExtZifencei: Boolean,
  useExtZicbo: Boolean,
  useMul: Boolean,
  nMulAddLvl: Int,
  useDiv: Boolean,
  useCsr: Boolean
)

case class IntUnitConfig (
  debug: Boolean,
  nAddrBit: Int,
  nDataBit: Int,

  useChamp: Boolean,
  nDome: Int,
  nPart: Int,

  useFastJal: Boolean,

  nRobEntry: Int,
  nSpecBranch: Int,
  nCommit: Int,

  nGprPhy: Int,
  iIntUnit: IntUnitIntf
) extends IntUnitParams {
  def useAlu: Boolean = iIntUnit.useAlu
  def useAluBypass: Boolean = iIntUnit.useAluBypass
  def useBru: Boolean = iIntUnit.useBru
  def useExtB: Boolean = iIntUnit.useExtB
  def useExtZifencei: Boolean = iIntUnit.useExtZifencei
  def useExtZicbo: Boolean = iIntUnit.useExtZicbo
  def useMul: Boolean = iIntUnit.useMul
  def nMulAddLvl: Int = iIntUnit.nMulAddLvl
  def useDiv: Boolean = iIntUnit.useDiv
  def useCsr: Boolean = iIntUnit.useCsr
}

// ******************************
//              INT
// ******************************
trait IntParams extends ExUnitParams {
  def debug: Boolean
  def nAddrBit: Int
  def nDataBit: Int

  def useChamp: Boolean
  def nDome: Int
  def multiDome: Boolean = false
  def nPart: Int

  def useFastJal: Boolean
  
  def nBackPort: Int
  def nRobEntry: Int
  def nSpecBranch: Int
  def nCommit: Int
  def nGprPhy: Int
  def nBypass: Int

  def nIntQueue: Int
  def iIntUnit: Array[IntUnitIntf]
  def nIntUnit: Int = iIntUnit.size
  def pIntUnit: Array[IntUnitParams] = {
    var array: Array[IntUnitParams] = new Array[IntUnitParams](nIntUnit)
    for (nu <- 0 until nIntUnit) {
      array(nu) = new IntUnitConfig(
        debug = debug,
        nAddrBit = nAddrBit,
        nDataBit = nDataBit,
        useFastJal = useFastJal,
        nRobEntry = nRobEntry,
        nSpecBranch = nSpecBranch,
        nCommit = nCommit,
        nGprPhy = nGprPhy,
        useChamp = useChamp,
        nDome = nDome,
        nPart = nPart,
        iIntUnit = iIntUnit(nu)
      )
    }
    return array
  }
  def nIntCollapse: Int = 1

  def useAlu: Boolean = true
  def nAlu: Int = {
    var count: Int = 0
    for (iu <- 0 until nIntUnit) {
      if (pIntUnit(iu).useAlu) count = count + 1
    }
    return count
  }
  def useExtB: Boolean = {
    var b: Boolean = false
    for (iu <- 0 until nIntUnit) {
      if (pIntUnit(iu).useExtB) b = true
    }
    return b
  }

  def useBru: Boolean = true
  def nBru: Int = {
    var count: Int = 0
    for (iu <- 0 until nIntUnit) {
      if (pIntUnit(iu).useBru) count = count + 1
    }
    return count
  }
  def useExtZifencei: Boolean = {
    var fencei: Boolean = false
    for (iu <- 0 until nIntUnit) {
      if (pIntUnit(iu).useExtZifencei) fencei = true
    }
    return fencei
  }
  def useExtZicbo: Boolean = {
    var b: Boolean = false
    for (iu <- 0 until nIntUnit) {
      if (pIntUnit(iu).useExtZicbo) b = true
    }
    return b
  }
  def useCbo: Boolean = useExtZifencei || useExtZicbo

  def useMul: Boolean = true
  def nMul: Int = {
    var count: Int = 0
    for (iu <- 0 until nIntUnit) {
      if (pIntUnit(iu).useMul) count = count + 1
    }
    return count
  }

  def useDiv: Boolean = true
  def nDiv: Int = {
    var count: Int = 0
    for (iu <- 0 until nIntUnit) {
      if (pIntUnit(iu).useDiv) count = count + 1
    }
    return count
  }

  def useCsr: Boolean = true
  def nCsr: Int = {
    var count: Int = 0
    for (iu <- 0 until nIntUnit) {
      if (pIntUnit(iu).useCsr) count = count + 1
    }
    return count
  }

  def nIntBypass: Int = {
    var count: Int = 0
    for (iu <- 0 until nIntUnit) {
      count = count + pIntUnit(iu).nBypass
    }
    return count
  }
}

case class IntConfig (
  debug: Boolean,
  nAddrBit: Int,
  nDataBit: Int,

  useChamp: Boolean,
  nDome: Int,
  nPart: Int,

  useFastJal: Boolean,
  
  nBackPort: Int,
  nRobEntry: Int,
  nSpecBranch: Int,
  nCommit: Int,
  nGprPhy: Int,
  nBypass: Int,

  nIntQueue: Int,
  iIntUnit: Array[IntUnitIntf]
) extends IntParams
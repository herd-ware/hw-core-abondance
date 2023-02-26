/*
 * File: configs.scala                                                         *
 * Created Date: 2023-02-26 09:21:29 am                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-26 09:31:53 am                                       *
 * Modified By: Mathieu Escouteloup                                            *
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.core.abondance

import chisel3._
import chisel3.util._

import herd.core.aubrac.nlp._
import herd.core.abondance.common._
import herd.core.abondance.back._
import herd.core.abondance.int.{IntUnitIntf}


// ******************************
//    INTEGER UNIT PARAMETERS 
// ******************************
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

// ******************************
//       PIPELINE PARAMETERS 
// ******************************
object PipelineConfigBase extends PipelineConfig (
  // ------------------------------
  //            GLOBAL
  // ------------------------------
  debug = true,
  pcBoot = "00001000",
  nAddrBit = 32,
  nDataBit = 32,

  // ------------------------------
  //             CEPS
  // ------------------------------
  useCeps = false,
  nDome = 1,
  nPart = 1,
  nCepsTrapLvl = 1,

  // ------------------------------
  //           FRONT END
  // ------------------------------
  nFetchInstr = 2,
  useIMemSeq = true,
  useIf1Stage = false,
  useIf2Stage = true,
  nFetchBufferDepth = 8,  
  useFastJal = true,

  // ------------------------------
  //       NEXT-LINE PREDICTOR
  // ------------------------------
  useNlp = true,
  nBtbLine = 8,
  nBhtSet = 8,
  nBhtSetEntry = 128,
  nBhtBit = 2,
  useRsbSpec = true,
  nRsbDepth = 8,

  // ------------------------------
  //           BACK END
  // ------------------------------
  nBackPort = 2,
  nCommit = 2,
  nRobEntry = 32,
  nSpecBranch = 2,
  useIdStage = true,

  nIntQueue = 16,
  iIntUnit = Array(IntUnitIntfBase0, IntUnitIntfBase1),

  useExtA = false,
  nLoadQueue = 8,
  useSpecLoad = false,
  nStoreQueue = 8,
  nMemQueue = 4,
  nDmuQueue = 4,

  nGprPhy = 64,
  nGprReadPhy = 4,
  nGprWritePhy = 2
)

// ******************************
//      ABONDANCE PARAMETERS 
// ******************************
object AbondanceConfigBase extends AbondanceConfig (
  // ------------------------------
  //            GLOBAL
  // ------------------------------
  debug = true,
  pcBoot = "00001000",
  nAddrBit = 32,
  nDataBit = 32,

  // ------------------------------
  //             CEPS
  // ------------------------------
  useCeps = false,
  useCepsExtMie = true,
  useCepsExtFr = false,
  useCepsExtCst = false,
  nCepsTrapLvl = 2,
  nDomeFlushCycle = 20,
  nPart = 2,
  nDomeCfg = 4,

  // ------------------------------
  //           FRONT END
  // ------------------------------
  nFetchInstr = 2,
  useIMemSeq = true,
  useIf1Stage = false,
  useIf2Stage = true,
  nFetchBufferDepth = 8,  
  useFastJal = true,

  // ------------------------------
  //       NEXT-LINE PREDICTOR
  // ------------------------------
  useNlp = true,
  nBtbLine = 8,
  nBhtSet = 8,
  nBhtSetEntry = 128,
  nBhtBit = 2,
  useRsbSpec = true,
  nRsbDepth = 8,

  // ------------------------------
  //           BACK END
  // ------------------------------
  nBackPort = 2,
  nCommit = 2,
  nRobEntry = 32,
  nSpecBranch = 2,
  useIdStage = true,

  nIntQueue = 16,
  iIntUnit = Array(IntUnitIntfBase0, IntUnitIntfBase1),

  useExtA = false,
  nLoadQueue = 8,
  useSpecLoad = false,
  nStoreQueue = 8,
  nMemQueue = 4,
  nDmuQueue = 4,

  nGprPhy = 64,
  nGprReadPhy = 4,
  nGprWritePhy = 2,

  // ------------------------------
  //              I/Os
  // ------------------------------
  nIOAddrBase = "00100000",
  nScratch = 2,
  nCTimer = 2,

  nUnCacheBase = "70000000",
  nUnCacheByte = "01000000",

  // ------------------------------
  //           L1I CACHE
  // ------------------------------
  useL1I = true,
  nL1INextDataByte = 8,
  nL1INextLatency = 1,

  useL1IPftch = false,
  nL1IPftchEntry = 4,
  nL1IPftchEntryAcc = 1,
  nL1IPftchMemRead = 1,
  nL1IPftchMemWrite = 1,

  nL1IMem = 1,
  nL1IMemReadPort = 2,
  nL1IMemWritePort = 1,

  slctL1IPolicy = "BitPLRU",
  nL1ISet = 4,
  nL1ILine = 4,
  nL1IData = 4,

  // ------------------------------
  //           L1D CACHE
  // ------------------------------
  useL1D = true,
  nL1DNextDataByte = 8,
  nL1DNextLatency = 1,

  useL1DPftch = false,
  nL1DPftchEntry = 4,
  nL1DPftchEntryAcc = 1,
  nL1DPftchMemRead = 1,
  nL1DPftchMemWrite = 1,

  nL1DMem = 1,
  nL1DMemReadPort = 2,
  nL1DMemWritePort = 1,

  slctL1DPolicy = "BitPLRU",
  nL1DSet = 4,
  nL1DLine = 4,
  nL1DData = 4,

  // ------------------------------
  //           L2 CACHE
  // ------------------------------
  useL2 = true,
  nL2NextDataByte = 8,
  useL2ReqReg = true,
  useL2AccReg = false,
  useL2AckReg = false,
  nL2WriteFifoDepth = 2,
  nL2NextFifoDepth = 2,
  nL2NextLatency = 1,

  useL2Pftch = false,
  nL2PftchEntry = 4,
  nL2PftchEntryAcc = 1,
  nL2PftchMemRead = 1,
  nL2PftchMemWrite = 1,

  nL2Mem = 2,
  nL2MemReadPort = 2,
  nL2MemWritePort = 1,

  slctL2Policy = "BitPLRU",
  nL2Set = 4,
  nL2Line = 4,
  nL2Data = 4
)

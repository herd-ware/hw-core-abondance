/*
 * File: params.scala                                                          *
 * Created Date: 2023-02-26 09:21:29 am                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-26 09:32:00 am                                       *
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

import herd.common.mem.mb4s._
import herd.core.aubrac.nlp._
import herd.core.aubrac.front._
import herd.core.aubrac.back.csr._
import herd.core.aubrac.dmu._
import herd.core.aubrac.{L1Params, L1Config, L2Params, L2Config}
import herd.core.abondance.common._
import herd.core.abondance.back._
import herd.core.abondance.int._
import herd.core.abondance.lsu._
import herd.mem.hay.{HayParams}
import herd.io.core._


// ******************************
//       PIPELINE PARAMETERS 
// ******************************
trait PipelineParams extends NlpParams
                      with FrontParams
                      with BackParams
                      with IntParams
                      with LsuParams
                      with CsrParams {

  // ------------------------------
  //            GLOBAL
  // ------------------------------
  def debug: Boolean
  def pcBoot: String
  override def nHart: Int = 1
  def nAddrBit: Int
  def nInstrByte: Int = 4  
  override def nInstrBit: Int = nInstrByte * 8
  def nDataBit: Int

  // ------------------------------
  //             CEPS
  // ------------------------------
  def useCeps: Boolean
  override def useDome: Boolean = useCeps
  def nDome: Int
  override def multiDome: Boolean = false
  def nPart: Int
  def nCepsTrapLvl: Int

  // ------------------------------
  //           FRONT END
  // ------------------------------
  def nFetchInstr: Int
  def useIMemSeq: Boolean
  def useIf1Stage: Boolean
  def useIf2Stage: Boolean
  def nFetchBufferDepth: Int  
  def useFastJal: Boolean

  // ------------------------------
  //       NEXT-LINE PREDICTOR
  // ------------------------------
  def useNlp: Boolean
  def nBtbLine: Int
  def nBhtSet: Int
  def nBhtSetEntry: Int
  def nBhtBit: Int
  def useRsbSpec: Boolean
  def nRsbDepth: Int

  // ------------------------------
  //           BACK END
  // ------------------------------
  def nBackPort: Int
  def nCommit: Int
  def nRobEntry: Int
  def nSpecBranch: Int
  def useIdStage: Boolean

  def nIntQueue: Int
  def iIntUnit: Array[IntUnitIntf]  
  def useExtM: Boolean = useMul && useDiv

  def useExtA: Boolean
  def nLoadQueue: Int
  def useSpecLoad: Boolean
  def nStoreQueue: Int
  def nMemQueue: Int
  def nDmuQueue: Int

  def nExUnit: Int = {
    var n: Int = nIntUnit + nLoad
    if (useCeps) {
      n = n + 1
    }
    return n
  }
  def nRobPcRead: Int = nIntUnit
  def useRobReplay: Boolean = useSpecLoad
  def nBypass: Int = {
    var n:Int = nIntBypass + nLoad
    if (useCeps) {
      n = n + 1
    }
    return n
  }

  def nGprPhy: Int
  def nGprReadPhy: Int
  def nGprReadLog: Int = {
    var n:Int = 2 * nIntUnit + 3
    if (useCeps) {
      n = n + 1
    }
    return n
  }
  def nGprWritePhy: Int
  def nGprWriteLog: Int = {
    var n:Int = nIntUnit + nLoad
    if (useCeps) {
      n = n + 1
    }
    return n
  }
}

case class PipelineConfig (
  // ------------------------------
  //            GLOBAL
  // ------------------------------
  debug: Boolean,
  pcBoot: String,
  nAddrBit: Int,
  nDataBit: Int,

  // ------------------------------
  //             CEPS
  // ------------------------------
  useCeps: Boolean,
  nDome: Int,
  nPart: Int,
  nCepsTrapLvl: Int,

  // ------------------------------
  //           FRONT END
  // ------------------------------
  nFetchInstr: Int,
  useIMemSeq: Boolean,
  useIf1Stage: Boolean,
  useIf2Stage: Boolean,
  nFetchBufferDepth: Int,  
  useFastJal: Boolean,

  // ------------------------------
  //       NEXT-LINE PREDICTOR
  // ------------------------------
  useNlp: Boolean,
  nBtbLine: Int,
  nBhtSet: Int,
  nBhtSetEntry: Int,
  nBhtBit: Int,
  useRsbSpec: Boolean,
  nRsbDepth: Int,

  // ------------------------------
  //           BACK END
  // ------------------------------
  nBackPort: Int,
  nCommit: Int,
  nRobEntry: Int,
  nSpecBranch: Int,
  useIdStage: Boolean,

  nIntQueue: Int,
  iIntUnit: Array[IntUnitIntf],  

  useExtA: Boolean,
  nLoadQueue: Int,
  useSpecLoad: Boolean,
  nStoreQueue: Int,
  nMemQueue: Int,
  nDmuQueue: Int,

  nGprPhy: Int,
  nGprReadPhy: Int,
  nGprWritePhy: Int
) extends PipelineParams


// ******************************
//      ABONDANCE PARAMETERS 
// ******************************
trait AbondanceParams extends PipelineParams {
  // ------------------------------
  //            GLOBAL
  // ------------------------------
  def debug: Boolean
  def pcBoot: String
  def nAddrBit: Int
  def nDataBit: Int

  // ------------------------------
  //             CEPS
  // ------------------------------
  def useCeps: Boolean
  def useCepsExtMie: Boolean
  def useCepsExtFr: Boolean
  def useCepsExtCst: Boolean
  def nCepsTrapLvl: Int
  def nDomeFlushCycle: Int
  def nPart: Int
  def nDomeCfg: Int

  def pDmu: DmuParams = new DmuConfig (
    debug = debug,
    pcBoot = pcBoot,
    nHart = nHart,
    nAddrBit = nAddrBit,
    nDataBit = nDataBit,

    useCeps = useCeps,
    useCepsExtMie = useCepsExtMie,
    useCepsExtFr = useCepsExtFr,
    useCepsExtCst = useCepsExtCst,
    nCepsTrapLvl = nCepsTrapLvl,
    nDomeFlushCycle = nDomeFlushCycle,
    nPart = nPart,
    nDomeCfg = nDomeCfg
  )

  def nDome: Int = pDmu.nDome

  // ------------------------------
  //           FRONT END
  // ------------------------------
  def nFetchInstr: Int
  def useIMemSeq: Boolean
  def useIf1Stage: Boolean
  def useIf2Stage: Boolean
  def nFetchBufferDepth: Int  
  def useFastJal: Boolean

  // ------------------------------
  //       NEXT-LINE PREDICTOR
  // ------------------------------
  def useNlp: Boolean
  def nBtbLine: Int
  def nBhtSet: Int
  def nBhtSetEntry: Int
  def nBhtBit: Int
  def useRsbSpec: Boolean
  def nRsbDepth: Int

  // ------------------------------
  //           BACK END
  // ------------------------------
  def nBackPort: Int
  def nCommit: Int
  def nRobEntry: Int
  def nSpecBranch: Int
  def useIdStage: Boolean

  def nIntQueue: Int
  def iIntUnit: Array[IntUnitIntf]  
  def nCbo: Int = if (useCbo) 1 else 0

  def useExtA: Boolean
  def nLoadQueue: Int
  def useSpecLoad: Boolean
  def nStoreQueue: Int
  def nMemQueue: Int
  def nDmuQueue: Int

  def nGprPhy: Int
  def nGprReadPhy: Int
  def nGprWritePhy: Int

  // ------------------------------
  //           L1I CACHE
  // ------------------------------
  def useL1I: Boolean
  def nL1INextDataByte: Int
  def nL1INextLatency: Int

  def useL1IPftch: Boolean
  def nL1IPftchEntry: Int
  def nL1IPftchEntryAcc: Int
  def nL1IPftchMemRead: Int
  def nL1IPftchMemWrite: Int

  def nL1IMem: Int
  def nL1IMemReadPort: Int
  def nL1IMemWritePort: Int

  def slctL1IPolicy: String
  def nL1ISet: Int
  def nL1ILine: Int
  def nL1IData: Int

  // ------------------------------
  //           L1D CACHE
  // ------------------------------
  def useL1D: Boolean
  def nL1DNextDataByte: Int
  def nL1DNextLatency: Int

  def useL1DPftch: Boolean
  def nL1DPftchEntry: Int
  def nL1DPftchEntryAcc: Int
  def nL1DPftchMemRead: Int
  def nL1DPftchMemWrite: Int

  def nL1DMem: Int
  def nL1DMemReadPort: Int
  def nL1DMemWritePort: Int

  def slctL1DPolicy: String
  def nL1DSet: Int
  def nL1DLine: Int
  def nL1DData: Int

  // ------------------------------
  //           L2 CACHE
  // ------------------------------
  def useL2: Boolean
  def nL2NextDataByte: Int
  def useL2ReqReg: Boolean
  def useL2AccReg: Boolean
  def useL2AckReg: Boolean
  def nL2WriteFifoDepth: Int
  def nL2NextFifoDepth: Int
  def nL2PrevLatency: Int = {
    var latency: Int = 1
    if (useL2ReqReg) latency = latency + 1
    if (useL2AccReg) latency = latency + 1
    return latency
  }
  def nL2NextLatency: Int

  def useL2Pftch: Boolean
  def nL2PftchEntry: Int
  def nL2PftchEntryAcc: Int
  def nL2PftchMemRead: Int
  def nL2PftchMemWrite: Int

  def nL2Mem: Int
  def nL2MemReadPort: Int
  def nL2MemWritePort: Int

  def slctL2Policy: String
  def nL2Set: Int
  def nL2Line: Int
  def nL2Data: Int

  // ------------------------------
  //              I/Os
  // ------------------------------
  def nIOAddrBase: String
  def nScratch: Int
  def nCTimer: Int

  def nUnCacheBase: String
  def nUnCacheByte: String

  // ------------------------------
  //            MEMORY
  // ------------------------------
  // ..............................
  //              L0
  // ..............................
  def pL0DArray: Array[Mb4sParams] = {
    var a: Array[Mb4sParams] = Array(pL0D0Bus, pL0D1Bus)
    if (useCeps) a = a :+ pDmu.pL0DBus
    a
  }
  def pL0DCrossBus: Mb4sParams = MB4S.node(pL0DArray, false)
  
  def pUnCache: Mb4sMemParams = new Mb4sMemConfig (
    pPort = Array(pL0DCrossBus),
  
    nAddrBase = nUnCacheBase,
    nByte = nUnCacheByte
  )

  def pIO: IOCoreParams = new IOCoreConfig (
    pPort           = Array(pL0DCrossBus)   ,

    debug           = debug           ,
    nAddrBit        = nAddrBit        ,
    nAddrBase       = nIOAddrBase     ,

    nCepsTrapLvl    = nCepsTrapLvl    ,

    useReqReg       = true            ,
    nScratch        = nScratch        ,
    nCTimer         = nCTimer  
  )

  def pL0DCross: Mb4sCrossbarParams = new Mb4sCrossbarConfig (
    pMaster = pL0DArray,
    useMem = true,
    pMem = if (useL2 || useL1D){
      Array(pIO, pUnCache)
    } else {
      Array(pIO)
    },
    nDefault = 2,
    nBus = 0,
    
    debug = debug,  
    multiDome = false,
    nDepth = 3,
    useDirect = false
  )

  // ..............................
  //              L1
  // ..............................
  def pL1I: L1Params = new L1Config (
    pPrevBus = Array(pL0IBus),

    debug = debug,
    nCbo = nCbo,

    nPart = nPart,

    useReqReg = useIf1Stage,
    useAccReg = false,
    useAckReg = false,
    nNextDataByte = nL1INextDataByte,
    nNextLatency = {
      if (useL2) {
        nL2PrevLatency
      } else {
        nL1INextLatency
      }
    },

    usePftch = useL1IPftch,
    nPftchEntry = nL1IPftchEntry,
    nPftchEntryAcc = nL1IPftchEntryAcc,
    nPftchMemRead = nL1IPftchMemRead,
    nPftchMemWrite = nL1IPftchMemWrite,

    nMem = nL1IMem,
    nMemReadPort = nL1IMemReadPort,
    nMemWritePort = nL1IMemWritePort,

    slctPolicy = slctL1IPolicy,
    nSet = nL1ISet,
    nLine = nL1ILine,
    nData = nL1IData
  )

  def pL1D: L1Params = new L1Config (
    pPrevBus = Array(pL0DCrossBus, pL0DCrossBus),

    debug = debug,
    nCbo = nCbo,

    nPart = nPart,

    useReqReg = true,
    useAccReg = false,
    useAckReg = useExtA,
    nNextDataByte = nL1DNextDataByte,
    nNextLatency = {
      if (useL2) {
        nL2PrevLatency
      } else {
        nL1INextLatency
      }
    },

    usePftch = useL1DPftch,
    nPftchEntry = nL1DPftchEntry,
    nPftchEntryAcc = nL1DPftchEntryAcc,
    nPftchMemRead = nL1DPftchMemRead,
    nPftchMemWrite = nL1DPftchMemWrite,

    nMem = nL1DMem,
    nMemReadPort = nL1DMemReadPort,
    nMemWritePort = nL1DMemWritePort,

    slctPolicy = slctL1DPolicy,
    nSet = nL1DSet,
    nLine = nL1DLine,
    nData = nL1DData
  )

  def pL1Array: Array[Mb4sParams] = {
    var a: Array[Mb4sParams] = Array()
    if (useL1I) {
      a = a :+ pL1I.pNextBus
    } else {
      a = a :+ pL0IBus
    }
    if (useL1D) {
      a = a :+ pL1D.pNextBus
    } else {
      a = a :+ pL0DCrossBus
      a = a :+ pL0DCrossBus
    }
    a
  }
  def pL1Bus: Mb4sParams = MB4S.node(pL1Array, true)

  // ..............................
  //              L2
  // ..............................
  def pL2: L2Params = new L2Config (
    pPrevBus = pL1Array,

    debug = debug,
    nCbo = nCbo,

    nPart = nPart,

    nNextDataByte = nL2NextDataByte,
    useReqReg = useL2ReqReg,
    useAccReg = useL2AccReg,
    useAckReg = useL2AckReg,
    nWriteFifoDepth = nL2WriteFifoDepth,
    nNextFifoDepth = nL2NextFifoDepth,
    nNextLatency = nL2NextLatency,

    usePftch = useL2Pftch,
    nPftchEntry = nL2PftchEntry,
    nPftchEntryAcc = nL2PftchEntryAcc,
    nPftchMemRead = nL2PftchMemRead,
    nPftchMemWrite = nL2PftchMemWrite,

    nMem = nL2Mem,
    nMemReadPort = nL2MemReadPort,
    nMemWritePort = nL2MemWritePort,

    slctPolicy = slctL2Policy,
    nSet = nL2Set,
    nLine = nL2Line,
    nData = nL2Data
  )

  def pLLArray: Array[Mb4sParams] = Array(pL0DCrossBus, pL2.pNextBus)
  def pLLDArray: Array[Mb4sParams] = {
    var a: Array[Mb4sParams] = Array(pL0DCrossBus)
    if (useL1D) {
      a = a :+ pL1D.pNextBus
    } else {
      a = a :+ pL0DCrossBus
    }
    a
  }

  def pLLBus: Mb4sParams = MB4S.node(pLLArray, true)
  def pLLIBus: Mb4sParams = {
    if (useL1I) {
      pL1I.pNextBus
    } else {
      pL0IBus
    }
  }
  def pLLDBus: Mb4sParams = MB4S.node(pLLDArray, true)
  def pLLCross: Mb4sCrossbarParams = new Mb4sCrossbarConfig (
    pMaster = if (useL2) pLLArray else pLLDArray,
    useMem = false,
    pMem = Array(),
    nDefault = 0,
    nBus = 1,
    
    debug = debug,  
    multiDome = true,
    nDepth = 4,
    useDirect = false
  )
}

case class AbondanceConfig (
  // ------------------------------
  //            GLOBAL
  // ------------------------------
  debug: Boolean,
  pcBoot: String,
  nAddrBit: Int,
  nDataBit: Int,

  // ------------------------------
  //             CEPS
  // ------------------------------
  useCeps: Boolean,
  useCepsExtMie: Boolean,
  useCepsExtFr: Boolean,
  useCepsExtCst: Boolean,
  nCepsTrapLvl: Int,
  nDomeFlushCycle: Int,
  nPart: Int,
  nDomeCfg: Int,

  // ------------------------------
  //           FRONT END
  // ------------------------------
  nFetchInstr: Int,
  useIMemSeq: Boolean,
  useIf1Stage: Boolean,
  useIf2Stage: Boolean,
  nFetchBufferDepth: Int,  
  useFastJal: Boolean,

  // ------------------------------
  //       NEXT-LINE PREDICTOR
  // ------------------------------
  useNlp: Boolean,
  nBtbLine: Int,
  nBhtSet: Int,
  nBhtSetEntry: Int,
  nBhtBit: Int,
  useRsbSpec: Boolean,
  nRsbDepth: Int,

  // ------------------------------
  //           BACK END
  // ------------------------------
  nBackPort: Int,
  nCommit: Int,
  nRobEntry: Int,
  nSpecBranch: Int,
  useIdStage: Boolean,

  nIntQueue: Int,
  iIntUnit: Array[IntUnitIntf],  

  useExtA: Boolean,
  nLoadQueue: Int,
  useSpecLoad: Boolean,
  nStoreQueue: Int,
  nMemQueue: Int,
  nDmuQueue: Int,

  nGprPhy: Int,
  nGprReadPhy: Int,
  nGprWritePhy: Int,

  // ------------------------------
  //             I/Os
  // ------------------------------
  nIOAddrBase: String,
  nScratch: Int,
  nCTimer: Int,

  nUnCacheBase: String,
  nUnCacheByte: String,

  // ------------------------------
  //           L1I CACHE
  // ------------------------------
  useL1I: Boolean,
  nL1INextDataByte: Int,
  nL1INextLatency: Int,

  useL1IPftch: Boolean,
  nL1IPftchEntry: Int,
  nL1IPftchEntryAcc: Int,
  nL1IPftchMemRead: Int,
  nL1IPftchMemWrite: Int,

  nL1IMem: Int,
  nL1IMemReadPort: Int,
  nL1IMemWritePort: Int,

  slctL1IPolicy: String,
  nL1ISet: Int,
  nL1ILine: Int,
  nL1IData: Int,

  // ------------------------------
  //           L1D CACHE
  // ------------------------------
  useL1D: Boolean,
  nL1DNextDataByte: Int,
  nL1DNextLatency: Int,

  useL1DPftch: Boolean,
  nL1DPftchEntry: Int,
  nL1DPftchEntryAcc: Int,
  nL1DPftchMemRead: Int,
  nL1DPftchMemWrite: Int,

  nL1DMem: Int,
  nL1DMemReadPort: Int,
  nL1DMemWritePort: Int,

  slctL1DPolicy: String,
  nL1DSet: Int,
  nL1DLine: Int,
  nL1DData: Int,

  // ------------------------------
  //           L2 CACHE
  // ------------------------------
  useL2: Boolean,
  nL2NextDataByte: Int,
  useL2ReqReg: Boolean,
  useL2AccReg: Boolean,
  useL2AckReg: Boolean,
  nL2WriteFifoDepth: Int,
  nL2NextFifoDepth: Int,
  nL2NextLatency: Int,

  useL2Pftch: Boolean,
  nL2PftchEntry: Int,
  nL2PftchEntryAcc: Int,
  nL2PftchMemRead: Int,
  nL2PftchMemWrite: Int,

  nL2Mem: Int,
  nL2MemReadPort: Int,
  nL2MemWritePort: Int,

  slctL2Policy: String,
  nL2Set: Int,
  nL2Line: Int,
  nL2Data: Int
) extends AbondanceParams


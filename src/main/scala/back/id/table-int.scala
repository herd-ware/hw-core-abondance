/*
 * File: table-int.scala
 * Created Date: 2023-02-26 09:21:29 am                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-03-03 04:29:33 pm
 * Modified By: Mathieu Escouteloup
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.core.abondance.back

import chisel3._
import chisel3.util._

import herd.common.isa.riscv.{INSTR => RISCV}
import herd.common.isa.priv.{INSTR => PRIV}
import herd.common.isa.champ.{INSTR => CHAMP}
import herd.core.abondance.int.{INTUNIT, INTUOP}
import herd.core.aubrac.back.{OP, IMM}


trait TABLEINT {
  //                        is Valid?         Gen exc ?               Int Unit         Int Uop     S1 Sign     S3 Sign          S2 Type   S3 Type            Imm2 Type
  //                           | use Serial?     |      Ex Type          |                |          |   S2 Sign |    S1 Type     |         |      Imm1 Type     |
  //                           |     |   is WB?  |        |              |                |          |     |     |      |         |         |          |         |
  //                           |     |     |     |        |              |                |          |     |     |      |         |         |          |         |
  val default: List[UInt] =
               List[UInt](    0.B,  0.B,  0.B,  0.B,  EXTYPE.X,     INTUNIT.X,      INTUOP.X,       0.B,  0.B,  0.B,  OP.X,     OP.X,     OP.X,     IMM.X,    IMM.X)
  val table: Array[(BitPat, List[UInt])]
}

object TABLEINT32I extends TABLEINT {
  val table : Array[(BitPat, List[UInt])] =
              Array[(BitPat, List[UInt])](

  //                        is Valid?         Gen exc ?               Int Unit         Int Uop     S1 Sign     S3 Sign          S2 Type   S3 Type            Imm2 Type
  //                           | use Serial?     |      Ex Type          |                |          |   S2 Sign |    S1 Type     |         |      Imm1 Type     |
  //                           |     |   is WB?  |        |              |                |          |     |     |      |         |         |          |         |
  //                           |     |     |     |        |              |                |          |     |     |      |         |         |          |         |
  RISCV.LUI         -> List(  1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.ALU,    INTUOP.ADD,     1.B,  0.B,  0.B,  OP.IMM1,  OP.IMM2,  OP.X,     IMM.isU,  IMM.is0),
  RISCV.AUIPC       -> List(  1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.ALU,    INTUOP.ADD,     1.B,  0.B,  0.B,  OP.IMM1,  OP.PC,    OP.X,     IMM.isU,  IMM.X),
  RISCV.JAL         -> List(  1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.BRU,    INTUOP.JAL,     0.B,  1.B,  0.B,  OP.PC,    OP.IMM1,  OP.X,     IMM.isJ,  IMM.X),
  RISCV.JALR        -> List(  1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.BRU,    INTUOP.JALR,    0.B,  1.B,  0.B,  OP.XREG,  OP.IMM1,  OP.X,     IMM.isI,  IMM.X),
  RISCV.BEQ         -> List(  1.B,  0.B,  0.B,  0.B,  EXTYPE.INT,   INTUNIT.BRU,    INTUOP.BEQ,     1.B,  1.B,  1.B,  OP.XREG,  OP.XREG,  OP.IMM1,  IMM.isB,  IMM.X),
  RISCV.BNE         -> List(  1.B,  0.B,  0.B,  0.B,  EXTYPE.INT,   INTUNIT.BRU,    INTUOP.BNE,     1.B,  1.B,  1.B,  OP.XREG,  OP.XREG,  OP.IMM1,  IMM.isB,  IMM.X),
  RISCV.BLT         -> List(  1.B,  0.B,  0.B,  0.B,  EXTYPE.INT,   INTUNIT.BRU,    INTUOP.BLT,     1.B,  1.B,  1.B,  OP.XREG,  OP.XREG,  OP.IMM1,  IMM.isB,  IMM.X),
  RISCV.BGE         -> List(  1.B,  0.B,  0.B,  0.B,  EXTYPE.INT,   INTUNIT.BRU,    INTUOP.BGE,     1.B,  1.B,  1.B,  OP.XREG,  OP.XREG,  OP.IMM1,  IMM.isB,  IMM.X),
  RISCV.BLTU        -> List(  1.B,  0.B,  0.B,  0.B,  EXTYPE.INT,   INTUNIT.BRU,    INTUOP.BLT,     0.B,  0.B,  1.B,  OP.XREG,  OP.XREG,  OP.IMM1,  IMM.isB,  IMM.X),
  RISCV.BGEU        -> List(  1.B,  0.B,  0.B,  0.B,  EXTYPE.INT,   INTUNIT.BRU,    INTUOP.BGE,     0.B,  0.B,  1.B,  OP.XREG,  OP.XREG,  OP.IMM1,  IMM.isB,  IMM.X),
  RISCV.LB          -> List(  1.B,  0.B,  1.B,  0.B,  EXTYPE.LSU,   INTUNIT.X,      INTUOP.X,       0.B,  1.B,  0.B,  OP.XREG,  OP.X,     OP.IMM1,  IMM.isI,  IMM.X),
  RISCV.LBU         -> List(  1.B,  0.B,  1.B,  0.B,  EXTYPE.LSU,   INTUNIT.X,      INTUOP.X,       0.B,  1.B,  0.B,  OP.XREG,  OP.X,     OP.IMM1,  IMM.isI,  IMM.X),
  RISCV.LH          -> List(  1.B,  0.B,  1.B,  0.B,  EXTYPE.LSU,   INTUNIT.X,      INTUOP.X,       0.B,  1.B,  0.B,  OP.XREG,  OP.X,     OP.IMM1,  IMM.isI,  IMM.X),
  RISCV.LHU         -> List(  1.B,  0.B,  1.B,  0.B,  EXTYPE.LSU,   INTUNIT.X,      INTUOP.X,       0.B,  1.B,  0.B,  OP.XREG,  OP.X,     OP.IMM1,  IMM.isI,  IMM.X),
  RISCV.LW          -> List(  1.B,  0.B,  1.B,  0.B,  EXTYPE.LSU,   INTUNIT.X,      INTUOP.X,       0.B,  1.B,  0.B,  OP.XREG,  OP.X,     OP.IMM1,  IMM.isI,  IMM.X),
  RISCV.SB          -> List(  1.B,  0.B,  0.B,  0.B,  EXTYPE.LSU,   INTUNIT.X,      INTUOP.X,       0.B,  1.B,  0.B,  OP.XREG,  OP.XREG,  OP.IMM1,  IMM.isS,  IMM.X),
  RISCV.SH          -> List(  1.B,  0.B,  0.B,  0.B,  EXTYPE.LSU,   INTUNIT.X,      INTUOP.X,       0.B,  1.B,  0.B,  OP.XREG,  OP.XREG,  OP.IMM1,  IMM.isS,  IMM.X),
  RISCV.SW          -> List(  1.B,  0.B,  0.B,  0.B,  EXTYPE.LSU,   INTUNIT.X,      INTUOP.X,       0.B,  1.B,  0.B,  OP.XREG,  OP.XREG,  OP.IMM1,  IMM.isS,  IMM.X), 
  RISCV.ADDI        -> List(  1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.ALU,    INTUOP.ADD,     1.B,  1.B,  0.B,  OP.XREG,  OP.IMM1,  OP.X,     IMM.isI,  IMM.X),
  RISCV.SLTI        -> List(  1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.ALU,    INTUOP.SLT,     1.B,  1.B,  0.B,  OP.XREG,  OP.IMM1,  OP.X,     IMM.isI,  IMM.X),
  RISCV.SLTIU       -> List(  1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.ALU,    INTUOP.SLT,     0.B,  0.B,  0.B,  OP.XREG,  OP.IMM1,  OP.X,     IMM.isI,  IMM.X),
  RISCV.XORI        -> List(  1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.ALU,    INTUOP.XOR,     1.B,  1.B,  0.B,  OP.XREG,  OP.IMM1,  OP.X,     IMM.isI,  IMM.X),
  RISCV.ORI         -> List(  1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.ALU,    INTUOP.OR,      1.B,  1.B,  0.B,  OP.XREG,  OP.IMM1,  OP.X,     IMM.isI,  IMM.X),
  RISCV.ANDI        -> List(  1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.ALU,    INTUOP.AND,     1.B,  1.B,  0.B,  OP.XREG,  OP.IMM1,  OP.X,     IMM.isI,  IMM.X),
  RISCV.SLLI        -> List(  1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.ALU,    INTUOP.SHL,     1.B,  1.B,  0.B,  OP.XREG,  OP.IMM1,  OP.X,     IMM.isI,  IMM.X),
  RISCV.SRLI        -> List(  1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.ALU,    INTUOP.SHR,     0.B,  0.B,  0.B,  OP.XREG,  OP.IMM1,  OP.X,     IMM.isI,  IMM.X),
  RISCV.SRAI        -> List(  1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.ALU,    INTUOP.SHR,     1.B,  1.B,  0.B,  OP.XREG,  OP.IMM1,  OP.X,     IMM.isI,  IMM.X),
  RISCV.ADD         -> List(  1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.ALU,    INTUOP.ADD,     1.B,  1.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
  RISCV.SUB         -> List(  1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.ALU,    INTUOP.SUB,     1.B,  1.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
  RISCV.SLL         -> List(  1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.ALU,    INTUOP.SHL,     1.B,  1.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
  RISCV.SLT         -> List(  1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.ALU,    INTUOP.SLT,     1.B,  1.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
  RISCV.SLTU        -> List(  1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.ALU,    INTUOP.SLT,     0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
  RISCV.XOR         -> List(  1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.ALU,    INTUOP.XOR,     1.B,  1.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
  RISCV.SRL         -> List(  1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.ALU,    INTUOP.SHR,     0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
  RISCV.SRA         -> List(  1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.ALU,    INTUOP.SHR,     1.B,  1.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
  RISCV.OR          -> List(  1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.ALU,    INTUOP.OR,      1.B,  1.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
  RISCV.AND         -> List(  1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.ALU,    INTUOP.AND,     1.B,  1.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),

  RISCV.FENCETSO    -> List(  1.B,  1.B,  0.B,  0.B,  EXTYPE.INT,   INTUNIT.BRU,    INTUOP.FENCE,   0.B,  0.B,  0.B,  OP.X,     OP.X,     OP.X,     IMM.X,    IMM.X),
  RISCV.PAUSE       -> List(  1.B,  1.B,  0.B,  0.B,  EXTYPE.INT,   INTUNIT.BRU,    INTUOP.FENCE,   0.B,  0.B,  0.B,  OP.X,     OP.X,     OP.X,     IMM.X,    IMM.X),
  RISCV.FENCE       -> List(  1.B,  1.B,  0.B,  0.B,  EXTYPE.INT,   INTUNIT.BRU,    INTUOP.FENCE,   0.B,  0.B,  0.B,  OP.X,     OP.X,     OP.X,     IMM.X,    IMM.X))
}

object TABLEINT64I extends TABLEINT {
  val table : Array[(BitPat, List[UInt])] =
              Array[(BitPat, List[UInt])](

  //                        is Valid?         Gen exc ?               Int Unit         Int Uop     S1 Sign     S3 Sign          S2 Type   S3 Type            Imm2 Type
  //                           | use Serial?     |      Ex Type          |                |          |   S2 Sign |    S1 Type     |         |      Imm1 Type     |
  //                           |     |   is WB?  |        |              |                |          |     |     |      |         |         |          |         |
  //                           |     |     |     |        |              |                |          |     |     |      |         |         |          |         |
  RISCV.LWU         -> List(  1.B,  0.B,  1.B,  1.B,  EXTYPE.LSU,   INTUNIT.X,      INTUOP.X,       0.B,  1.B,  0.B,  OP.XREG,  OP.X,     OP.X,     IMM.isI,  IMM.X),
  RISCV.LD          -> List(  1.B,  0.B,  1.B,  1.B,  EXTYPE.LSU,   INTUNIT.X,      INTUOP.X,       0.B,  1.B,  0.B,  OP.XREG,  OP.X,     OP.X,     IMM.isI,  IMM.X),
  RISCV.SD          -> List(  1.B,  0.B,  0.B,  1.B,  EXTYPE.LSU,   INTUNIT.X,      INTUOP.X,       0.B,  1.B,  0.B,  OP.XREG,  OP.X,     OP.X,     IMM.isS,  IMM.X),
  RISCV.ADDIW       -> List(  1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.ALU,    INTUOP.ADD,     1.B,  1.B,  0.B,  OP.XREG,  OP.IMM1,  OP.X,     IMM.isI,  IMM.X),
  RISCV.SLLIW       -> List(  1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.ALU,    INTUOP.SHL,     1.B,  1.B,  0.B,  OP.XREG,  OP.IMM1,  OP.X,     IMM.isI,  IMM.X),
  RISCV.SRLIW       -> List(  1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.ALU,    INTUOP.SHR,     1.B,  1.B,  0.B,  OP.XREG,  OP.IMM1,  OP.X,     IMM.isI,  IMM.X),
  RISCV.SRAIW       -> List(  1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.ALU,    INTUOP.SHR,     1.B,  1.B,  0.B,  OP.XREG,  OP.IMM1,  OP.X,     IMM.isI,  IMM.X),
  RISCV.ADDW        -> List(  1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.ALU,    INTUOP.ADD,     1.B,  1.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
  RISCV.SUBW        -> List(  1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.ALU,    INTUOP.SUB,     1.B,  1.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
  RISCV.SLLW        -> List(  1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.ALU,    INTUOP.SHL,     1.B,  1.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
  RISCV.SRLW        -> List(  1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.ALU,    INTUOP.SHR,     1.B,  1.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
  RISCV.SRAW        -> List(  1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.ALU,    INTUOP.SHR,     1.B,  1.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X))
}

object TABLEINTCSR extends TABLEINT {
  val table : Array[(BitPat, List[UInt])] =
              Array[(BitPat, List[UInt])](

  //                        is Valid?         Gen exc ?               Int Unit         Int Uop     S1 Sign     S3 Sign          S2 Type   S3 Type            Imm2 Type
  //                           | use Serial?     |      Ex Type          |                |          |   S2 Sign |    S1 Type     |         |      Imm1 Type     |
  //                           |     |   is WB?  |        |              |                |          |     |     |      |         |         |          |         |
  //                           |     |     |     |        |              |                |          |     |     |      |         |         |          |         |
  RISCV.CSRRW0      -> List(  1.B,  1.B,  0.B,  1.B,  EXTYPE.INT,   INTUNIT.CSR,    INTUOP.CSRW,    0.B,  0.B,  0.B,  OP.XREG,  OP.X,     OP.IMM2,  IMM.X,    IMM.isI),
  RISCV.CSRRW       -> List(  1.B,  1.B,  1.B,  1.B,  EXTYPE.INT,   INTUNIT.CSR,    INTUOP.CSRRW,   0.B,  0.B,  0.B,  OP.XREG,  OP.X,     OP.IMM2,  IMM.X,    IMM.isI),
  RISCV.CSRRS0      -> List(  1.B,  1.B,  1.B,  1.B,  EXTYPE.INT,   INTUNIT.CSR,    INTUOP.CSRRX,   0.B,  0.B,  0.B,  OP.XREG,  OP.X,     OP.IMM2,  IMM.X,    IMM.isI),
  RISCV.CSRRS       -> List(  1.B,  1.B,  1.B,  1.B,  EXTYPE.INT,   INTUNIT.CSR,    INTUOP.CSRRS,   0.B,  0.B,  0.B,  OP.XREG,  OP.X,     OP.IMM2,  IMM.X,    IMM.isI),
  RISCV.CSRRC0      -> List(  1.B,  1.B,  1.B,  1.B,  EXTYPE.INT,   INTUNIT.CSR,    INTUOP.CSRRX,   0.B,  0.B,  0.B,  OP.XREG,  OP.X,     OP.IMM2,  IMM.X,    IMM.isI),
  RISCV.CSRRC       -> List(  1.B,  1.B,  1.B,  1.B,  EXTYPE.INT,   INTUNIT.CSR,    INTUOP.CSRRC,   0.B,  0.B,  0.B,  OP.XREG,  OP.X,     OP.IMM2,  IMM.X,    IMM.isI),
  RISCV.CSRRWI0     -> List(  1.B,  1.B,  0.B,  1.B,  EXTYPE.INT,   INTUNIT.CSR,    INTUOP.CSRW,    0.B,  0.B,  0.B,  OP.IMM1,  OP.X,     OP.IMM2,  IMM.isC,  IMM.isI),
  RISCV.CSRRWI      -> List(  1.B,  1.B,  1.B,  1.B,  EXTYPE.INT,   INTUNIT.CSR,    INTUOP.CSRRW,   0.B,  0.B,  0.B,  OP.IMM1,  OP.X,     OP.IMM2,  IMM.isC,  IMM.isI),
  RISCV.CSRRSI0     -> List(  1.B,  1.B,  1.B,  1.B,  EXTYPE.INT,   INTUNIT.CSR,    INTUOP.CSRRX,   0.B,  0.B,  0.B,  OP.IMM1,  OP.X,     OP.IMM2,  IMM.isC,  IMM.isI),
  RISCV.CSRRSI      -> List(  1.B,  1.B,  1.B,  1.B,  EXTYPE.INT,   INTUNIT.CSR,    INTUOP.CSRRS,   0.B,  0.B,  0.B,  OP.IMM1,  OP.X,     OP.IMM2,  IMM.isC,  IMM.isI),
  RISCV.CSRRCI0     -> List(  1.B,  1.B,  1.B,  1.B,  EXTYPE.INT,   INTUNIT.CSR,    INTUOP.CSRRX,   0.B,  0.B,  0.B,  OP.IMM1,  OP.X,     OP.IMM2,  IMM.isC,  IMM.isI),
  RISCV.CSRRCI      -> List(  1.B,  1.B,  1.B,  1.B,  EXTYPE.INT,   INTUNIT.CSR,    INTUOP.CSRRC,   0.B,  0.B,  0.B,  OP.IMM1,  OP.X,     OP.IMM2,  IMM.isC,  IMM.isI)) 
}

object TABLEINT32M extends TABLEINT {
  val table : Array[(BitPat, List[UInt])] =
              Array[(BitPat, List[UInt])](

  //                        is Valid?         Gen exc ?               Int Unit         Int Uop     S1 Sign     S3 Sign          S2 Type   S3 Type            Imm2 Type
  //                           | use Serial?     |      Ex Type          |                |          |   S2 Sign |    S1 Type     |         |      Imm1 Type     |
  //                           |     |   is WB?  |        |              |                |          |     |     |      |         |         |          |         |
  //                           |     |     |     |        |              |                |          |     |     |      |         |         |          |         |
  RISCV.MUL         -> List(  1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.MUL,    INTUOP.MUL,     1.B,  1.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
  RISCV.MULH        -> List(  1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.MUL,    INTUOP.MULH,    1.B,  1.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
  RISCV.MULHU       -> List(  1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.MUL,    INTUOP.MULH,    0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X), 
  RISCV.MULHSU      -> List(  1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.MUL,    INTUOP.MULH,    1.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X), 
  RISCV.DIV         -> List(  1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.DIV,    INTUOP.DIV,     1.B,  1.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
  RISCV.DIVU        -> List(  1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.DIV,    INTUOP.DIV,     0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
  RISCV.REM         -> List(  1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.DIV,    INTUOP.REM,     1.B,  1.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
  RISCV.REMU        -> List(  1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.DIV,    INTUOP.REM,     0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X))
}

object TABLEINT64M extends TABLEINT {
  val table : Array[(BitPat, List[UInt])] =
              Array[(BitPat, List[UInt])](

  //                        is Valid?         Gen exc ?               Int Unit         Int Uop     S1 Sign     S3 Sign          S2 Type   S3 Type            Imm2 Type
  //                           | use Serial?     |      Ex Type          |                |          |   S2 Sign |    S1 Type     |         |      Imm1 Type     |
  //                           |     |   is WB?  |        |              |                |          |     |     |      |         |         |          |         |
  //                           |     |     |     |        |              |                |          |     |     |      |         |         |          |         |
  RISCV.MULW        -> List(  1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.MUL,    INTUOP.MUL,     1.B,  1.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
  RISCV.DIVW        -> List(  1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.DIV,    INTUOP.DIV,     1.B,  1.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
  RISCV.DIVUW       -> List(  1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.DIV,    INTUOP.DIV,     0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
  RISCV.REMW        -> List(  1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.DIV,    INTUOP.REM,     1.B,  1.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),  
  RISCV.REMUW       -> List(  1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.DIV,    INTUOP.REM,     0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X))
}

object TABLEINT32A extends TABLEINT {
  val table : Array[(BitPat, List[UInt])] =
              Array[(BitPat, List[UInt])](

  //                        is Valid?         Gen exc ?               Int Unit         Int Uop     S1 Sign     S3 Sign          S2 Type   S3 Type            Imm2 Type
  //                           | use Serial?     |      Ex Type          |                |          |   S2 Sign |    S1 Type     |         |      Imm1 Type     |
  //                           |     |   is WB?  |        |              |                |          |     |     |      |         |         |          |         |
  //                           |     |     |     |        |              |                |          |     |     |      |         |         |          |         |
  RISCV.LRW         -> List(  1.B,  1.B,  1.B,  1.B,  EXTYPE.LSU,   INTUNIT.X,      INTUOP.X,       0.B,  0.B,  0.B,  OP.XREG,  OP.X,     OP.X,     IMM.X,    IMM.X),
  RISCV.SCW         -> List(  1.B,  1.B,  1.B,  1.B,  EXTYPE.LSU,   INTUNIT.X,      INTUOP.X,       0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
  RISCV.AMOSWAPW    -> List(  1.B,  0.B,  1.B,  1.B,  EXTYPE.LSU,   INTUNIT.X,      INTUOP.X,       0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
  RISCV.AMOADDW     -> List(  1.B,  0.B,  1.B,  1.B,  EXTYPE.LSU,   INTUNIT.X,      INTUOP.X,       0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
  RISCV.AMOXORW     -> List(  1.B,  0.B,  1.B,  1.B,  EXTYPE.LSU,   INTUNIT.X,      INTUOP.X,       0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
  RISCV.AMOANDW     -> List(  1.B,  0.B,  1.B,  1.B,  EXTYPE.LSU,   INTUNIT.X,      INTUOP.X,       0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
  RISCV.AMOORW      -> List(  1.B,  0.B,  1.B,  1.B,  EXTYPE.LSU,   INTUNIT.X,      INTUOP.X,       0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
  RISCV.AMOMINW     -> List(  1.B,  0.B,  1.B,  1.B,  EXTYPE.LSU,   INTUNIT.X,      INTUOP.X,       0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
  RISCV.AMOMAXW     -> List(  1.B,  0.B,  1.B,  1.B,  EXTYPE.LSU,   INTUNIT.X,      INTUOP.X,       0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
  RISCV.AMOMINUW    -> List(  1.B,  0.B,  1.B,  1.B,  EXTYPE.LSU,   INTUNIT.X,      INTUOP.X,       0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
  RISCV.AMOMAXUW    -> List(  1.B,  0.B,  1.B,  1.B,  EXTYPE.LSU,   INTUNIT.X,      INTUOP.X,       0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X))
}

object TABLEINT64A extends TABLEINT {
  val table : Array[(BitPat, List[UInt])] =
              Array[(BitPat, List[UInt])](

  //                        is Valid?         Gen exc ?               Int Unit         Int Uop     S1 Sign     S3 Sign          S2 Type   S3 Type            Imm2 Type
  //                           | use Serial?     |      Ex Type          |                |          |   S2 Sign |    S1 Type     |         |      Imm1 Type     |
  //                           |     |   is WB?  |        |              |                |          |     |     |      |         |         |          |         |
  //                           |     |     |     |        |              |                |          |     |     |      |         |         |          |         |
  RISCV.LRD         -> List(  1.B,  1.B,  1.B,  1.B,  EXTYPE.LSU,   INTUNIT.X,      INTUOP.X,       0.B,  0.B,  0.B,  OP.XREG,  OP.X,     OP.X,     IMM.X,    IMM.X),
  RISCV.SCD         -> List(  1.B,  1.B,  1.B,  1.B,  EXTYPE.LSU,   INTUNIT.X,      INTUOP.X,       0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
  RISCV.AMOSWAPD    -> List(  1.B,  0.B,  1.B,  1.B,  EXTYPE.LSU,   INTUNIT.X,      INTUOP.X,       0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
  RISCV.AMOADDD     -> List(  1.B,  0.B,  1.B,  1.B,  EXTYPE.LSU,   INTUNIT.X,      INTUOP.X,       0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
  RISCV.AMOXORD     -> List(  1.B,  0.B,  1.B,  1.B,  EXTYPE.LSU,   INTUNIT.X,      INTUOP.X,       0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
  RISCV.AMOANDD     -> List(  1.B,  0.B,  1.B,  1.B,  EXTYPE.LSU,   INTUNIT.X,      INTUOP.X,       0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
  RISCV.AMOORD      -> List(  1.B,  0.B,  1.B,  1.B,  EXTYPE.LSU,   INTUNIT.X,      INTUOP.X,       0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
  RISCV.AMOMIND     -> List(  1.B,  0.B,  1.B,  1.B,  EXTYPE.LSU,   INTUNIT.X,      INTUOP.X,       0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
  RISCV.AMOMAXD     -> List(  1.B,  0.B,  1.B,  1.B,  EXTYPE.LSU,   INTUNIT.X,      INTUOP.X,       0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
  RISCV.AMOMINUD    -> List(  1.B,  0.B,  1.B,  1.B,  EXTYPE.LSU,   INTUNIT.X,      INTUOP.X,       0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
  RISCV.AMOMAXUD    -> List(  1.B,  0.B,  1.B,  1.B,  EXTYPE.LSU,   INTUNIT.X,      INTUOP.X,       0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X))
}

object TABLEINT32B extends TABLEINT {
  val table : Array[(BitPat, List[UInt])] =
              Array[(BitPat, List[UInt])](

  //                        is Valid?         Gen exc ?               Int Unit         Int Uop     S1 Sign     S3 Sign          S2 Type   S3 Type            Imm2 Type
  //                           | use Serial?     |      Ex Type          |                |          |   S2 Sign |    S1 Type     |         |      Imm1 Type     |
  //                           |     |   is WB?  |        |              |                |          |     |     |      |         |         |          |         |
  //                           |     |     |     |        |              |                |          |     |     |      |         |         |          |         |
  RISCV.SH1ADD    -> List(    1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.BALU,   INTUOP.SH1ADD,  0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
  RISCV.SH2ADD    -> List(    1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.BALU,   INTUOP.SH2ADD,  0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
  RISCV.SH3ADD    -> List(    1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.BALU,   INTUOP.SH3ADD,  0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
  RISCV.ANDN      -> List(    1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.BALU,   INTUOP.ANDN,    0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
  RISCV.ORN       -> List(    1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.BALU,   INTUOP.ORN,     0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
  RISCV.XNOR      -> List(    1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.BALU,   INTUOP.XNOR,    0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
  RISCV.CLZ       -> List(    1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.BALU,   INTUOP.CLZ,     0.B,  0.B,  0.B,  OP.XREG,  OP.X,     OP.X,     IMM.X,    IMM.X),
  RISCV.CTZ       -> List(    1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.BALU,   INTUOP.CTZ,     0.B,  0.B,  0.B,  OP.XREG,  OP.X,     OP.X,     IMM.X,    IMM.X),
  RISCV.CPOP      -> List(    1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.BALU,   INTUOP.CPOP,    0.B,  0.B,  0.B,  OP.XREG,  OP.X,     OP.X,     IMM.X,    IMM.X),
  RISCV.MAX       -> List(    1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.BALU,   INTUOP.MAX,     1.B,  1.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
  RISCV.MAXU      -> List(    1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.BALU,   INTUOP.MAX,     0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
  RISCV.MIN       -> List(    1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.BALU,   INTUOP.MIN,     1.B,  1.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
  RISCV.MINU      -> List(    1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.BALU,   INTUOP.MIN,     0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
  RISCV.SEXTB     -> List(    1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.BALU,   INTUOP.EXTB,    1.B,  1.B,  0.B,  OP.XREG,  OP.X,     OP.X,     IMM.X,    IMM.X),
  RISCV.SEXTH     -> List(    1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.BALU,   INTUOP.EXTH,    1.B,  1.B,  0.B,  OP.XREG,  OP.X,     OP.X,     IMM.X,    IMM.X),
  RISCV.ZEXTH32   -> List(    1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.BALU,   INTUOP.EXTH,    0.B,  0.B,  0.B,  OP.XREG,  OP.X,     OP.X,     IMM.X,    IMM.X),
  RISCV.ROL       -> List(    1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.BALU,   INTUOP.ROL,     0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
  RISCV.ROR       -> List(    1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.BALU,   INTUOP.ROR,     0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
  RISCV.RORI      -> List(    1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.BALU,   INTUOP.ROR,     0.B,  0.B,  0.B,  OP.XREG,  OP.IMM1,  OP.X,     IMM.isI,  IMM.X),
  RISCV.ORCB      -> List(    1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.BALU,   INTUOP.ORCB,    0.B,  0.B,  0.B,  OP.XREG,  OP.X,     OP.X,     IMM.X,    IMM.X),
  RISCV.REV832    -> List(    1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.BALU,   INTUOP.REV8,    0.B,  0.B,  0.B,  OP.XREG,  OP.X,     OP.X,     IMM.X,    IMM.X),
  RISCV.CLMUL     -> List(    1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.CLMUL,  INTUOP.CLMUL,   0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
  RISCV.CLMULH    -> List(    1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.CLMUL,  INTUOP.CLMULH,  0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
  RISCV.CLMULR    -> List(    1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.CLMUL,  INTUOP.CLMULR,  0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
  RISCV.BCLR      -> List(    1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.BALU,   INTUOP.BCLR,    0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
  RISCV.BCLRI     -> List(    1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.BALU,   INTUOP.BCLR,    0.B,  0.B,  0.B,  OP.XREG,  OP.IMM1,  OP.X,     IMM.isI,  IMM.X),
  RISCV.BEXT      -> List(    1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.BALU,   INTUOP.BEXT,    0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
  RISCV.BEXTI     -> List(    1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.BALU,   INTUOP.BEXT,    0.B,  0.B,  0.B,  OP.XREG,  OP.IMM1,  OP.X,     IMM.isI,  IMM.X),
  RISCV.BINV      -> List(    1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.BALU,   INTUOP.BINV,    0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
  RISCV.BINVI     -> List(    1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.BALU,   INTUOP.BINV,    0.B,  0.B,  0.B,  OP.XREG,  OP.IMM1,  OP.X,     IMM.isI,  IMM.X),
  RISCV.BSET      -> List(    1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.BALU,   INTUOP.BSET,    0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
  RISCV.BSETI     -> List(    1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.BALU,   INTUOP.BSET,    0.B,  0.B,  0.B,  OP.XREG,  OP.IMM1,  OP.X,     IMM.isI,  IMM.X))
}

object TABLEINT64B extends TABLEINT {
  val table : Array[(BitPat, List[UInt])] =
              Array[(BitPat, List[UInt])](

  //                        is Valid?         Gen exc ?               Int Unit         Int Uop     S1 Sign     S3 Sign          S2 Type   S3 Type            Imm2 Type
  //                           | use Serial?     |      Ex Type          |                |          |   S2 Sign |    S1 Type     |         |      Imm1 Type     |
  //                           |     |   is WB?  |        |              |                |          |     |     |      |         |         |          |         |
  //                           |     |     |     |        |              |                |          |     |     |      |         |         |          |         |
  RISCV.ADDUW     -> List(    1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.BALU,   INTUOP.ADD,     0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
  RISCV.SH1ADDUW  -> List(    1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.BALU,   INTUOP.SH1ADD,  0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
  RISCV.SH2ADDUW  -> List(    1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.BALU,   INTUOP.SH2ADD,  0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
  RISCV.SH3ADDUW  -> List(    1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.BALU,   INTUOP.SH3ADD,  0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
  RISCV.SLLIUW    -> List(    1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.BALU,   INTUOP.SHL,     0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
  RISCV.CLZW      -> List(    1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.BALU,   INTUOP.CLZ,     0.B,  0.B,  0.B,  OP.XREG,  OP.X,     OP.X,     IMM.X,    IMM.X),
  RISCV.CTZW      -> List(    1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.BALU,   INTUOP.CTZ,     0.B,  0.B,  0.B,  OP.XREG,  OP.X,     OP.X,     IMM.X,    IMM.X),
  RISCV.CPOPW     -> List(    1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.BALU,   INTUOP.CPOP,    0.B,  0.B,  0.B,  OP.XREG,  OP.X,     OP.X,     IMM.X,    IMM.X),
  RISCV.ZEXTH64   -> List(    1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.BALU,   INTUOP.EXTH,    0.B,  0.B,  0.B,  OP.XREG,  OP.X,     OP.X,     IMM.X,    IMM.X),
  RISCV.ROLW      -> List(    1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.BALU,   INTUOP.ROL,     0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
  RISCV.RORW      -> List(    1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.BALU,   INTUOP.ROR,     1.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
  RISCV.RORIW     -> List(    1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.BALU,   INTUOP.ROR,     1.B,  0.B,  0.B,  OP.XREG,  OP.IMM1,  OP.X,     IMM.isI,  IMM.X),
  RISCV.REV864    -> List(    1.B,  0.B,  1.B,  0.B,  EXTYPE.INT,   INTUNIT.BALU,   INTUOP.REV8,    0.B,  0.B,  0.B,  OP.XREG,  OP.X,     OP.X,     IMM.X,    IMM.X))
}

object TABLEINTZIFENCEI extends TABLEINT {
  val table : Array[(BitPat, List[UInt])] =
              Array[(BitPat, List[UInt])](

  //                        is Valid?         Gen exc ?               Int Unit         Int Uop     S1 Sign     S3 Sign          S2 Type   S3 Type            Imm2 Type
  //                           | use Serial?     |      Ex Type          |                |          |   S2 Sign |    S1 Type     |         |      Imm1 Type     |
  //                           |     |   is WB?  |        |              |                |          |     |     |      |         |         |          |         |
  //                           |     |     |     |        |              |                |          |     |     |      |         |         |          |         |
  RISCV.FENCEI      -> List(  1.B,  1.B,  0.B,  0.B,  EXTYPE.INT,   INTUNIT.BRU,    INTUOP.FENCEI,  0.B,  0.B,  0.B,  OP.XREG,  OP.IMM1,  OP.X,     IMM.isI,  IMM.X))
}

object TABLEINTZICBO extends TABLEINT {
  val table : Array[(BitPat, List[UInt])] =
              Array[(BitPat, List[UInt])](

  //                        is Valid?         Gen exc ?               Int Unit         Int Uop     S1 Sign     S3 Sign          S2 Type   S3 Type            Imm2 Type
  //                           | use Serial?     |      Ex Type          |                |          |   S2 Sign |    S1 Type     |         |      Imm1 Type     |
  //                           |     |   is WB?  |        |              |                |          |     |     |      |         |         |          |         |
  //                           |     |     |     |        |              |                |          |     |     |      |         |         |          |         |
  RISCV.CBOCLEAN    -> List(  1.B,  1.B,  0.B,  1.B,  EXTYPE.INT,   INTUNIT.BRU,    INTUOP.CLEAN,   0.B,  0.B,  0.B,  OP.XREG,  OP.X,      OP.X,    IMM.X,    IMM.X),
  RISCV.CBOINVAL    -> List(  1.B,  1.B,  0.B,  1.B,  EXTYPE.INT,   INTUNIT.BRU,    INTUOP.INVAL,   0.B,  0.B,  0.B,  OP.XREG,  OP.X,      OP.X,    IMM.X,    IMM.X),
  RISCV.CBOFLUSH    -> List(  1.B,  1.B,  0.B,  1.B,  EXTYPE.INT,   INTUNIT.BRU,    INTUOP.FLUSH,   0.B,  0.B,  0.B,  OP.XREG,  OP.X,      OP.X,    IMM.X,    IMM.X),
  RISCV.CBOZERO     -> List(  1.B,  1.B,  0.B,  1.B,  EXTYPE.INT,   INTUNIT.BRU,    INTUOP.ZERO,    0.B,  0.B,  0.B,  OP.XREG,  OP.X,      OP.X,    IMM.X,    IMM.X),
  RISCV.PREFETCHI   -> List(  1.B,  0.B,  0.B,  0.B,  EXTYPE.INT,   INTUNIT.BRU,    INTUOP.PFTCHE,  0.B,  0.B,  0.B,  OP.XREG,  OP.X,      OP.X,    IMM.X,    IMM.X),
  RISCV.PREFETCHR   -> List(  1.B,  0.B,  0.B,  0.B,  EXTYPE.INT,   INTUNIT.BRU,    INTUOP.PFTCHR,  0.B,  0.B,  0.B,  OP.XREG,  OP.X,      OP.X,    IMM.X,    IMM.X),
  RISCV.PREFETCHW   -> List(  1.B,  0.B,  0.B,  0.B,  EXTYPE.INT,   INTUNIT.BRU,    INTUOP.PFTCHW,  0.B,  0.B,  0.B,  OP.XREG,  OP.X,      OP.X,    IMM.X,    IMM.X))
}

object TABLEINTPRIV extends TABLEINT {
  val table : Array[(BitPat, List[UInt])] =
              Array[(BitPat, List[UInt])](

  //                        is Valid?         Gen exc ?               Int Unit         Int Uop     S1 Sign     S3 Sign          S2 Type   S3 Type            Imm2 Type
  //                           | use Serial?     |      Ex Type          |                |          |   S2 Sign |    S1 Type     |         |      Imm1 Type     |
  //                           |     |   is WB?  |        |              |                |          |     |     |      |         |         |          |         |
  //                           |     |     |     |        |              |                |          |     |     |      |         |         |          |         |
  PRIV.MRET         -> List(  1.B,  1.B,  0.B,  1.B,  EXTYPE.X,     INTUNIT.X,      INTUOP.X,       0.B,  0.B,  0.B,  OP.XREG,  OP.X,     OP.X,     IMM.X,    IMM.X),
  PRIV.WFI          -> List(  1.B,  1.B,  0.B,  1.B,  EXTYPE.X,     INTUNIT.X,      INTUOP.X,       0.B,  0.B,  0.B,  OP.XREG,  OP.X,     OP.X,     IMM.X,    IMM.X))
}

object TABLEINTCHAMP extends TABLEINT {
  val table : Array[(BitPat, List[UInt])] =
              Array[(BitPat, List[UInt])](
  //                        is Valid?         Gen exc ?               Int Unit         Int Uop     S1 Sign     S3 Sign          S2 Type   S3 Type            Imm2 Type
  //                           | use Serial?     |      Ex Type          |                |          |   S2 Sign |    S1 Type     |         |      Imm1 Type     |
  //                           |     |   is WB?  |        |              |                |          |     |     |      |         |         |          |         |
  //                           |     |     |     |        |              |                |          |     |     |      |         |         |          |         |
  CHAMP.ADD         -> List(  1.B,  0.B,  1.B,  0.B,  EXTYPE.HFU,   INTUNIT.X,      INTUOP.X,       0.B,  0.B,  0.B,  OP.X,     OP.XREG,  OP.IMM1,  IMM.isR,  IMM.X),
  CHAMP.SUB         -> List(  1.B,  0.B,  1.B,  0.B,  EXTYPE.HFU,   INTUNIT.X,      INTUOP.X,       0.B,  0.B,  0.B,  OP.X,     OP.XREG,  OP.IMM1,  IMM.isR,  IMM.X),
  CHAMP.SET         -> List(  1.B,  0.B,  1.B,  0.B,  EXTYPE.HFU,   INTUNIT.X,      INTUOP.X,       0.B,  0.B,  0.B,  OP.X,     OP.XREG,  OP.IMM1,  IMM.isR,  IMM.X),
  CHAMP.CLEAR       -> List(  1.B,  0.B,  1.B,  0.B,  EXTYPE.HFU,   INTUNIT.X,      INTUOP.X,       0.B,  0.B,  0.B,  OP.X,     OP.XREG,  OP.IMM1,  IMM.isR,  IMM.X),
  CHAMP.MVCX        -> List(  1.B,  0.B,  1.B,  0.B,  EXTYPE.HFU,   INTUNIT.X,      INTUOP.X,       0.B,  0.B,  0.B,  OP.X,     OP.XREG,  OP.IMM1,  IMM.isR,  IMM.X),
  CHAMP.MVXC        -> List(  1.B,  0.B,  1.B,  0.B,  EXTYPE.HFU,   INTUNIT.X,      INTUOP.X,       0.B,  0.B,  0.B,  OP.X,     OP.XREG,  OP.IMM1,  IMM.isR,  IMM.X),
  CHAMP.MV          -> List(  1.B,  0.B,  1.B,  0.B,  EXTYPE.HFU,   INTUNIT.X,      INTUOP.X,       0.B,  0.B,  0.B,  OP.X,     OP.X,     OP.X,     IMM.X,    IMM.X),    
  CHAMP.LOAD        -> List(  1.B,  0.B,  0.B,  1.B,  EXTYPE.HFU,   INTUNIT.X,      INTUOP.X,       0.B,  0.B,  0.B,  OP.X,     OP.XREG,  OP.IMM1,  IMM.isS,  IMM.X),
  CHAMP.STORE       -> List(  1.B,  0.B,  0.B,  1.B,  EXTYPE.HFU,   INTUNIT.X,      INTUOP.X,       0.B,  0.B,  0.B,  OP.X,     OP.XREG,  OP.IMM1,  IMM.isS,  IMM.X),
  CHAMP.SWITCHV     -> List(  1.B,  1.B,  1.B,  0.B,  EXTYPE.HFU,   INTUNIT.X,      INTUOP.X,       0.B,  0.B,  0.B,  OP.X,     OP.X,     OP.X,     IMM.X,    IMM.X),
  CHAMP.SWITCHL     -> List(  1.B,  1.B,  1.B,  0.B,  EXTYPE.HFU,   INTUNIT.X,      INTUOP.X,       0.B,  0.B,  0.B,  OP.X,     OP.X,     OP.X,     IMM.X,    IMM.X),
  CHAMP.SWITCHC     -> List(  1.B,  1.B,  1.B,  0.B,  EXTYPE.HFU,   INTUNIT.X,      INTUOP.X,       0.B,  0.B,  0.B,  OP.X,     OP.X,     OP.X,     IMM.X,    IMM.X),
  CHAMP.SWITCHJV    -> List(  1.B,  1.B,  1.B,  0.B,  EXTYPE.HFU,   INTUNIT.X,      INTUOP.X,       0.B,  0.B,  0.B,  OP.X,     OP.XREG,  OP.X,     IMM.X,    IMM.X),
  CHAMP.SWITCHJL    -> List(  1.B,  1.B,  1.B,  0.B,  EXTYPE.HFU,   INTUNIT.X,      INTUOP.X,       0.B,  0.B,  0.B,  OP.X,     OP.XREG,  OP.X,     IMM.X,    IMM.X),
  CHAMP.SWITCHJC    -> List(  1.B,  1.B,  1.B,  0.B,  EXTYPE.HFU,   INTUNIT.X,      INTUOP.X,       0.B,  0.B,  0.B,  OP.X,     OP.XREG,  OP.X,     IMM.X,    IMM.X),
  CHAMP.CHECKV      -> List(  1.B,  0.B,  1.B,  0.B,  EXTYPE.HFU,   INTUNIT.X,      INTUOP.X,       0.B,  0.B,  0.B,  OP.X,     OP.X,     OP.X,     IMM.X,    IMM.X),
  CHAMP.CHECKU      -> List(  1.B,  0.B,  1.B,  0.B,  EXTYPE.HFU,   INTUNIT.X,      INTUOP.X,       0.B,  0.B,  0.B,  OP.X,     OP.X,     OP.X,     IMM.X,    IMM.X),
  CHAMP.CHECKL      -> List(  1.B,  0.B,  1.B,  0.B,  EXTYPE.HFU,   INTUNIT.X,      INTUOP.X,       0.B,  0.B,  0.B,  OP.X,     OP.X,     OP.X,     IMM.X,    IMM.X),
  CHAMP.CHECKC      -> List(  1.B,  0.B,  1.B,  0.B,  EXTYPE.HFU,   INTUNIT.X,      INTUOP.X,       0.B,  0.B,  0.B,  OP.X,     OP.X,     OP.X,     IMM.X,    IMM.X),
  CHAMP.SWITCHRL0   -> List(  1.B,  1.B,  1.B,  0.B,  EXTYPE.HFU,   INTUNIT.X,      INTUOP.X,       0.B,  0.B,  0.B,  OP.X,     OP.X,     OP.X,     IMM.X,    IMM.X),
  CHAMP.SWITCHRL1   -> List(  1.B,  1.B,  1.B,  0.B,  EXTYPE.HFU,   INTUNIT.X,      INTUOP.X,       0.B,  0.B,  0.B,  OP.X,     OP.X,     OP.X,     IMM.X,    IMM.X),
  CHAMP.WFI         -> List(  1.B,  1.B,  0.B,  1.B,  EXTYPE.HFU,   INTUNIT.X,      INTUOP.X,       0.B,  0.B,  0.B,  OP.X,     OP.X,     OP.X,     IMM.X,    IMM.X))
}

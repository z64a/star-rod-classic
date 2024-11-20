package asm;

import static asm.MIPS.Format.*;
import static asm.MIPS.InstructionType.*;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.StarRodException;
import app.input.InputFileException;
import app.input.InvalidInputException;
import app.input.Line;
import game.shared.DataUtils;
import util.CaseInsensitiveMap;
import util.DualHashMap;

public class MIPS
{
	private static final String NOP = "NOP";
	private static final String BRANCH = "B";

	private static final String[] cpuRegNames = {
			"R0", "AT", "V0", "V1", "A0", "A1", "A2", "A3",
			"T0", "T1", "T2", "T3", "T4", "T5", "T6", "T7",
			"S0", "S1", "S2", "S3", "S4", "S5", "S6", "S7",
			"T8", "T9", "K0", "K1", "GP", "SP", "S8", "RA"
	};
	private static final CaseInsensitiveMap<Integer> cpuRegMap;

	private static final String[] fpuRegNames = {
			"F0", "F1", "F2", "F3", "F4", "F5", "F6", "F7",
			"F8", "F9", "F10", "F11", "F12", "F13", "F14", "F15",
			"F16", "F17", "F18", "F19", "F20", "F21", "F22", "F23",
			"F24", "F25", "F26", "F27", "F28", "F29", "F30", "F31"
	};
	private static final CaseInsensitiveMap<Integer> fpuRegMap;

	private static final String[] fpuCondNames = {
			"F", "UN", "EQ", "UEQ", "OLT", "ULT", "OLE", "ULE",
			"SF", "NGLE", "SEQ", "NGL", "LT", "NGE", "LE", "NGT"
	};
	private static final CaseInsensitiveMap<Integer> fpuCondMap;

	private static final String[] cop0RegNames = {
			"Index", "Random", "EntryLo0", "EntryLo1", "Context", "PageMask", "Wired", null,
			"BadVAddr", "Count", "EntryHi", "Compare", "Status", "Cause", "EPC", "PRevID",
			"Config", "LLAddr", "WatchLo", "WatchHi", "XContext", null, null, null,
			null, null, "PErr", "CacheErr", "TagLo", "TagHi", "ErrorEPC", null
	}; // null are RESERVED
	private static final CaseInsensitiveMap<Integer> cop0RegMap;

	public static final CaseInsensitiveMap<Instruction> InstructionMap;
	public static final TreeSet<String> supportedInstructions;

	private static final Instruction[] OpcodeTable;
	private static final Instruction[] SpecialTable;
	private static final Instruction[] RegimmTable;
	private static final Instruction[] Cop0Table;
	private static final Instruction[] Cop0Table_C0;
	private static final Instruction[] Cop2Table;

	static {
		cpuRegMap = new CaseInsensitiveMap<>();
		for (int i = 0; i < cpuRegNames.length; i++)
			cpuRegMap.put(cpuRegNames[i], i);
		cpuRegMap.put("FP", cpuRegMap.get("S8"));

		fpuRegMap = new CaseInsensitiveMap<>();
		for (int i = 0; i < fpuRegNames.length; i++)
			fpuRegMap.put(fpuRegNames[i], i);

		fpuCondMap = new CaseInsensitiveMap<>();
		for (int i = 0; i < fpuCondNames.length; i++)
			fpuCondMap.put(fpuCondNames[i], i);

		cop0RegMap = new CaseInsensitiveMap<>();
		for (int i = 0; i < cop0RegNames.length; i++) {
			if (cop0RegNames[i] != null)
				cop0RegMap.put(cop0RegNames[i], i);
		}

		InstructionMap = new CaseInsensitiveMap<>();
		supportedInstructions = new TreeSet<>();
		supportedInstructions.add(NOP);
		supportedInstructions.add(BRANCH);

		OpcodeTable = new Instruction[64];
		SpecialTable = new Instruction[64];
		RegimmTable = new Instruction[64]; // only goes up to 20...
		Cop0Table = new Instruction[64];
		Cop0Table_C0 = new Instruction[64];
		Cop2Table = new Instruction[64];

		for (Instruction ins : Instruction.values()) {
			InstructionMap.put(ins.name, ins);
			supportedInstructions.add(ins.name);

			// @formatter:off
			switch(ins.type)
			{
			case NORMAL:	OpcodeTable[ins.id] = ins; break;
			case SPECIAL:	SpecialTable[ins.id] = ins; break;
			case REGIMM:	RegimmTable[ins.id] = ins; break;
			case COP0:		Cop0Table[ins.id] = ins; break;
			case COP0_C0:	Cop0Table_C0[ins.id] = ins; break;
			case COP1:		break; // nothing
			case COP2:		Cop2Table[ins.id] = ins; break;
			}
			// @formatter:on
		}
	}

	private static final int SPECIAL_OPCODE = 0;
	private static final int REGIMM_OPCODE = 1;
	private static final int COP0_OPCODE = 16; // MMU
	private static final int COP1_OPCODE = 17; // FPU
	private static final int COP2_OPCODE = 18; // RCP

	protected static enum InstructionType
	{
		NORMAL (-1),
		SPECIAL (SPECIAL_OPCODE),
		REGIMM (REGIMM_OPCODE),
		COP0 (COP0_OPCODE),
		COP0_C0 (COP0_OPCODE),
		COP1 (COP1_OPCODE),
		COP2 (COP2_OPCODE);

		public final int opcode;

		private InstructionType(int opcode)
		{
			this.opcode = opcode;
		}
	}

	protected static enum Format
	{
		// normal formats
		RS_OFFSET (2), // also used for all REGIMM
		RS_RT_OFFSET (3),
		RT_OFFSET_BASE (3),
		FPU_RT_OFFSET_BASE (3),
		LUI_FMT (2),
		RT_RS_IMMEDIATE (3),
		J_TARGET (1),
		CACHE_FMT (3),

		// special formats
		RD_RS_RT (3),
		RS_RT (2),
		RD (1),
		RS (1),
		RD_RT_SA (3),
		RD_RT_RS (3),
		JALR_FMT (2),
		JR_FMT (1),
		SYSCALL_FMT (1),

		// COP1 (FPU) formats
		FPU_OFFSET (1),
		FD_FS (3),
		FD_FS_FT (4),
		COND_FMT (4),
		RT_FS (2),

		// COP0 (MMU) format
		COP0_FMT (1);

		private final int argc;

		private Format(int argc)
		{
			this.argc = argc;
		}
	}

	public static enum Instruction
	{
		// @formatter:off
		BEQ		("BEQ", RS_RT_OFFSET, 4),
		BEQL	("BEQL",RS_RT_OFFSET, 20),
		BNE		("BNE", RS_RT_OFFSET, 5),
		BNEL	("BNEL",RS_RT_OFFSET, 21),

		BLEZ	("BLEZ", RS_OFFSET, 6),
		BGTZ	("BGTZ", RS_OFFSET, 7),
		BLEZL	("BLEZL",RS_OFFSET, 22),
		BGTZL	("BGTZL",RS_OFFSET, 23),

		BLTZ	("BLTZ",   REGIMM, RS_OFFSET, 0),
		BGEZ	("BGEZ",   REGIMM, RS_OFFSET, 1),
		BLTZL	("BLTZL",  REGIMM, RS_OFFSET, 2),
		BGEZL	("BGEZL",  REGIMM, RS_OFFSET, 3),
		BLTZAL	("BLTZAL", REGIMM, RS_OFFSET, 16),
		BGEZAL	("BGEZAL", REGIMM, RS_OFFSET, 17),
		BLTZALL	("BLTZALL",REGIMM, RS_OFFSET, 18),
		BGEZALL	("BGEZALL",REGIMM, RS_OFFSET, 19),

		J		("J",  J_TARGET, 2),
		JAL		("JAL",J_TARGET, 3),

		JR		("JR",  SPECIAL, JR_FMT, 8),
		JALR	("JALR",SPECIAL, JALR_FMT, 9),

		SYSCALL	("SYSCALL",SPECIAL, SYSCALL_FMT, 12),
		BREAK	("BREAK",SPECIAL, SYSCALL_FMT, 13),

		LB		("LB",  RT_OFFSET_BASE, 32),
		LBU		("LBU", RT_OFFSET_BASE, 36),
		LD		("LD",  RT_OFFSET_BASE, 55),
		LDL		("LDL", RT_OFFSET_BASE, 26),
		LDR		("LDR", RT_OFFSET_BASE, 27),
		LH		("LH",  RT_OFFSET_BASE, 33),
		LHU		("LHU", RT_OFFSET_BASE, 37),
		LL		("LL",  RT_OFFSET_BASE, 48),
		LLD		("LLD", RT_OFFSET_BASE, 52),
		LW		("LW",  RT_OFFSET_BASE, 35),
		LWL		("LWL", RT_OFFSET_BASE, 34),
		LWR		("LWR", RT_OFFSET_BASE, 38),
		LWU		("LWU", RT_OFFSET_BASE, 39),
		SB		("SB",  RT_OFFSET_BASE, 40),
		SC		("SC",  RT_OFFSET_BASE, 56),
		SCD		("SCD", RT_OFFSET_BASE, 60),
		SD		("SD",  RT_OFFSET_BASE, 63),
		SDL		("SDL", RT_OFFSET_BASE, 44),
		SDR		("SDR", RT_OFFSET_BASE, 45),
		SH		("SH",  RT_OFFSET_BASE, 41),
		SW		("SW",  RT_OFFSET_BASE, 43),
		SWL		("SWL", RT_OFFSET_BASE, 42),
		SWR		("SWR", RT_OFFSET_BASE, 46),

		LDC1	("LDC1", FPU_RT_OFFSET_BASE, 53),
		LWC1	("LWC1", FPU_RT_OFFSET_BASE, 49),
		SDC1	("SDC1", FPU_RT_OFFSET_BASE, 61),
		SWC1	("SWC1", FPU_RT_OFFSET_BASE, 57),

		ADDI 	("ADDI",  RT_RS_IMMEDIATE, 8),
		ADDIU	("ADDIU", RT_RS_IMMEDIATE, 9),
		SLTI	("SLTI",  RT_RS_IMMEDIATE, 10),
		SLTIU	("SLTIU", RT_RS_IMMEDIATE, 11),
		ANDI   	("ANDI",  RT_RS_IMMEDIATE, 12),
		ORI   	("ORI",   RT_RS_IMMEDIATE, 13),
		XORI   	("XORI",  RT_RS_IMMEDIATE, 14),
		DADDI 	("DADDI", RT_RS_IMMEDIATE, 24),
		DADDIU	("DADDIU",RT_RS_IMMEDIATE, 25),

		LUI		("LUI", LUI_FMT, 15),

		SLL		("SLL",   SPECIAL, RD_RT_SA, 0),
		SRL		("SRL",   SPECIAL, RD_RT_SA, 2),
		SRA		("SRA",   SPECIAL, RD_RT_SA, 3),
		DSLL	("DSLL",  SPECIAL, RD_RT_SA, 56),
		DSRL	("DSRL",  SPECIAL, RD_RT_SA, 58),
		DSRA	("DSRA",  SPECIAL, RD_RT_SA, 59),
		DSLL32	("DSLL32",SPECIAL, RD_RT_SA, 60),
		DSRL32	("DSRL32",SPECIAL, RD_RT_SA, 62),
		DSRA32	("DSRA32",SPECIAL, RD_RT_SA, 63),

		SLLV	("SLLV", SPECIAL, RD_RT_RS, 4),
		SRLV	("SRLV", SPECIAL, RD_RT_RS, 6),
		SRAV	("SRAV", SPECIAL, RD_RT_RS, 7),
		DSLLV	("DSLLV",SPECIAL, RD_RT_RS, 20),
		DSRLV	("DSRLV",SPECIAL, RD_RT_RS, 22),
		DSRAV	("DSRAV",SPECIAL, RD_RT_RS, 23),

		MULT	("MULT",  SPECIAL, RS_RT, 24),
		MULTU	("MULTU", SPECIAL, RS_RT, 25),
		DIV		("DIV",   SPECIAL, RS_RT, 26),
		DIVU	("DIVU",  SPECIAL, RS_RT, 27),
		DMULT	("DMULT", SPECIAL, RS_RT, 28),
		DMULTU	("DMULTU",SPECIAL, RS_RT, 29),
		DDIV	("DDIV",  SPECIAL, RS_RT, 30),
		DDIVU	("DDIVU", SPECIAL, RS_RT, 31),

		MFHI	("MFHI", SPECIAL, RD, 16),
		MFLO	("MFLO", SPECIAL, RD, 18),

		MTHI	("MTHI", SPECIAL, RS, 17),
		MTLO	("MTLO", SPECIAL, RS, 19),

		ADD		("ADD",  SPECIAL, RD_RS_RT, 32),
		ADDU	("ADDU", SPECIAL, RD_RS_RT, 33),
		SUB		("SUB",  SPECIAL, RD_RS_RT, 34),
		SUBU	("SUBU", SPECIAL, RD_RS_RT, 35),
		DADD	("DADD", SPECIAL, RD_RS_RT, 44),
		DADDU	("DADDU",SPECIAL, RD_RS_RT, 45),
		DSUB	("DSUB", SPECIAL, RD_RS_RT, 46),
		DSUBU	("DSUBU",SPECIAL, RD_RS_RT, 47),
		AND		("AND",  SPECIAL, RD_RS_RT, 36),
		OR		("OR",   SPECIAL, RD_RS_RT, 37),
		XOR		("XOR",  SPECIAL, RD_RS_RT, 38),
		NOR		("NOR",  SPECIAL, RD_RS_RT, 39),
		SLT		("SLT",  SPECIAL, RD_RS_RT, 42),
		SLTU	("SLTU", SPECIAL, RD_RS_RT, 43),

		BC1F	("BC1F", COP1, FPU_OFFSET, 0),
		BC1T	("BC1T", COP1, FPU_OFFSET, 1),
		BC1FL	("BC1FL",COP1, FPU_OFFSET, 2),
		BC1TL	("BC1TL",COP1, FPU_OFFSET, 3),

		FADD	("ADD.", COP1, FD_FS_FT, 0),
		FSUB	("SUB.", COP1, FD_FS_FT, 1),
		FMUL	("MUL.", COP1, FD_FS_FT, 2),
		FDIV	("DIV.", COP1, FD_FS_FT, 3),

		SQRT	("SQRT.",   COP1, FD_FS, 4),
		ABS		("ABS.",    COP1, FD_FS, 5),
		MOV		("MOV.",    COP1, FD_FS, 6),
		NEG		("NEG.",    COP1, FD_FS, 7),
		ROUNDL	("ROUND.L.",COP1, FD_FS, 8),
		TRUNCL	("TRUNC.L.",COP1, FD_FS, 9),
		CEILL	("CEIL.L.", COP1, FD_FS, 10),
		FLOORL	("FLOOR.L.",COP1, FD_FS, 11),
		ROUNDW	("ROUND.W.",COP1, FD_FS, 12),
		TRUNCW	("TRUNC.W.",COP1, FD_FS, 13),
		CEILW	("CEIL.W.", COP1, FD_FS, 14),
		FLOORW	("FLOOR.W.",COP1, FD_FS, 15),
		CVTS	("CVT.S.",  COP1, FD_FS, 32),
		CVTD	("CVT.D.",  COP1, FD_FS, 33),
		CVTW	("CVT.W.",  COP1, FD_FS, 36),
		CVTL	("CVT.L.",  COP1, FD_FS, 37),

		MFC1	("MFC1", COP1, RT_FS, 0),
		DMFC1	("DMFC1",COP1, RT_FS, 1),
		CFC1	("CFC1", COP1, RT_FS, 2),
		MTC1	("MTC1", COP1, RT_FS, 4),
		DMTC1	("DMTC1",COP1, RT_FS, 5),
		CTC1	("CTC1", COP1, RT_FS, 6),

		COND	("C.", COP1, COND_FMT, -1),

		CACHE	("CACHE", CACHE_FMT, 47),

		MFC0	("MFC0",  COP0, RT_FS, 0),
		MTC0	("MTC0",  COP0, RT_FS, 4),
		DMFC0	("DMFC0", COP0, RT_FS, 1),
		DMTC0	("DMTC0", COP0, RT_FS, 5),

		ERET	("ERET",  COP0_C0, COP0_FMT, 24),
		TLBP	("TLBP",  COP0_C0, COP0_FMT, 8),
		TLBR	("TLBR",  COP0_C0, COP0_FMT, 1),
		TLBWI	("TLBWI", COP0_C0, COP0_FMT, 2),
		TLBWR	("TLBWR", COP0_C0, COP0_FMT, 6);
		// @formatter:on

		private final String name;
		private final int id;
		private final InstructionType type;
		private final Format format;

		private Instruction(String name, Format fmt, int id)
		{
			this(name, InstructionType.NORMAL, fmt, id);
		}

		private Instruction(String name, InstructionType type, Format fmt, int id)
		{
			this.name = name;
			this.type = type;
			this.format = fmt;
			this.id = id;
		}

		public String getName()
		{
			return name;
		}
	}

	private static int SEGMENT = 0x80000000;

	public static void useSegmentOfPointer(int addr)
	{
		setSegment(addr >>> 28);
	}

	public static void setSegment(int value)
	{
		if ((value & 0xF) == value)
			SEGMENT = value << 28;
		else if ((value & 0x80000000) == value)
			SEGMENT = value;
		else
			throw new IllegalArgumentException("Invalid segment provided: " + value);
	}

	public static void resetSegment()
	{
		SEGMENT = 0x80000000;
	}

	public static class AssemblerException extends InputFileException
	{
		public AssemblerException(Line line, String msg)
		{
			super(line, msg);
		}

		public AssemblerException(Line line, String format, Object ... args)
		{
			super(line, format, args);
		}
	}

	public static class DisassemblerException extends StarRodException
	{
		public DisassemblerException(String msg)
		{
			super(msg);
		}

		public DisassemblerException(String fmt, Object ... args)
		{
			super(fmt, args);
		}
	}

	public static String[] disassemble(String[] lines)
	{
		String[] out = new String[lines.length];

		for (int i = 0; i < lines.length; i++)
			out[i] = disassemble(lines[i]);

		return out;
	}

	public static List<String> disassemble(List<String> lines)
	{
		List<String> out = new LinkedList<>();

		for (String s : lines)
			out.add(disassemble(s));

		return out;
	}

	public static String disassemble(String in)
	{
		try {
			int v = (int) Long.parseLong(in, 16);
			return disassemble(v);
		}
		catch (NumberFormatException e) {
			throw new DisassemblerException("Could not parse instruction " + in);
		}
	}

	public static String disassemble(int v)
	{
		String out = "";

		if (v == 0)
			return NOP;

		int opcode = v >>> 26;

		switch (opcode) {
			case SPECIAL_OPCODE:
				out = disassembleSpecial(v);
				break;
			case REGIMM_OPCODE:
				out = disassembleRegimm(v);
				break;
			case COP0_OPCODE:
				out = disassembleCop0(v);
				break;
			case COP1_OPCODE:
				out = disassembleCop1(v);
				break;
			case COP2_OPCODE:
				throw new DisassemblerException("RCP disassembly is not supported for instruction %08X", v);
			default:
				out = disassembleNormal(v);
		}

		return out;
	}

	private static String disassembleNormal(int v)
	{
		int opcode = v >>> 26;

		Instruction ins = OpcodeTable[opcode];
		if (ins == null)
			throw new DisassemblerException("Unknown opcode %X from instruction %08X", opcode, v);

		String line = "";
		int rs = (v >>> 21) & 0x1F;
		int rt = (v >>> 16) & 0x1F;

		int jmpoffset = v & 0x3FFFFFF;
		short immediate = (short) v;

		switch (ins.format) {
			case RS_RT_OFFSET:
				//line = String.format("%-9s %s, %s, %X", ins.name, cpuRegNames[rs], cpuRegNames[rt], 4 * immediate);
				line = AsmUtils.getFormattedLine(ins.name, "%s, %s, %X", cpuRegNames[rs], cpuRegNames[rt], 4 * immediate);
				break;
			case RS_OFFSET:
				line = AsmUtils.getFormattedLine(ins.name, "%s, %X", cpuRegNames[rs], 4 * immediate);
				break;
			case J_TARGET:
				jmpoffset = (jmpoffset << 2) + SEGMENT;
				line = AsmUtils.getFormattedLine(ins.name, "%X", jmpoffset);
				break;
			case RT_RS_IMMEDIATE:
				line = AsmUtils.getFormattedLine(ins.name, "%s, %s, %X", cpuRegNames[rt], cpuRegNames[rs], immediate);
				break;
			case LUI_FMT:
				line = AsmUtils.getFormattedLine(ins.name, "%s, %X", cpuRegNames[rt], immediate);
				break;
			case RT_OFFSET_BASE:
				line = AsmUtils.getFormattedLine(ins.name, "%s, %X (%s)", cpuRegNames[rt], immediate, cpuRegNames[rs]);
				break;
			case FPU_RT_OFFSET_BASE:
				line = AsmUtils.getFormattedLine(ins.name, "%s, %X (%s)", fpuRegNames[rt], immediate, cpuRegNames[rs]);
				break;
			case CACHE_FMT:
				line = AsmUtils.getFormattedLine(ins.name, "%X, %X (%s)", rt, immediate, cpuRegNames[rs]);
				break;
			default:
				throw new DisassemblerException("Disassembler error on instruction type: " + ins.name);
		}

		return line;
	}

	private static String disassembleSpecial(int v)
	{
		int opcode = v & 0x3F;

		Instruction ins = SpecialTable[opcode];
		if (ins == null)
			throw new DisassemblerException("Unknown special function %X from instruction %08X", opcode, v);

		String line = "";
		int rs = (v >>> 21) & 0x1F;
		int rt = (v >>> 16) & 0x1F;
		int rd = (v >>> 11) & 0x1F;
		int sa = (v >>> 6) & 0x1F;

		switch (ins.format) {
			case JR_FMT:
				line = AsmUtils.getFormattedLine(ins.name, "%s", cpuRegNames[rs]);
				break;
			case JALR_FMT:
				line = AsmUtils.getFormattedLine(ins.name, "%s, %s", cpuRegNames[rs], cpuRegNames[rd]);
				break;
			case SYSCALL_FMT:
				int offset = (v >>> 6) & 0x0FFFFF;
				line = AsmUtils.getFormattedLine(ins.name, "%X", offset);
				break;
			case RS_RT:
				line = AsmUtils.getFormattedLine(ins.name, "%s, %s", cpuRegNames[rs], cpuRegNames[rt]);
				break;
			case RD:
				line = AsmUtils.getFormattedLine(ins.name, "%s", cpuRegNames[rd]);
				break;
			case RS:
				line = AsmUtils.getFormattedLine(ins.name, "%s", cpuRegNames[rs]);
				break;
			case RD_RS_RT:
				line = AsmUtils.getFormattedLine(ins.name, "%s, %s, %s", cpuRegNames[rd], cpuRegNames[rs], cpuRegNames[rt]);
				break;
			case RD_RT_SA:
				line = AsmUtils.getFormattedLine(ins.name, "%s, %s, %X", cpuRegNames[rd], cpuRegNames[rt], sa);
				break;
			case RD_RT_RS:
				line = AsmUtils.getFormattedLine(ins.name, "%s, %s, %s", cpuRegNames[rd], cpuRegNames[rt], cpuRegNames[rs]);
				break;
			default:
				throw new DisassemblerException("Disassembler error on instruction type: " + ins.name);
		}

		return line;
	}

	private static String disassembleRegimm(int v)
	{
		int opcode = (v >> 16) & 0x1F;

		Instruction ins = RegimmTable[opcode];
		if (ins == null)
			throw new DisassemblerException("Unknown branch type %X from instruction %08X", opcode, v);

		String line = "";
		int rs = (v >>> 21) & 0x1F;
		short immediate = (short) v;

		if (ins.format == RS_OFFSET)
			line = AsmUtils.getFormattedLine(ins.name, "%s, %X", cpuRegNames[rs], 4 * immediate);
		else
			throw new DisassemblerException("Disassembler error on instruction type: " + ins.name);

		return line;
	}

	private static String disassembleCop0(int v)
	{
		int opcode = v & 0x3F;
		int fmt = (v >>> 21) & 0x1F;

		Instruction ins;
		if (fmt == 16)
			ins = Cop0Table_C0[opcode];
		else
			ins = Cop0Table[fmt];

		if (ins == null)
			throw new DisassemblerException("Unknown COP1 instruction %08X", v);

		String line = "";
		int rt = (v >>> 16) & 0x1F;
		int fs = (v >>> 16) & 0x1F;

		switch (ins.format) {
			case RT_FS:
				line = AsmUtils.getFormattedLine(ins.name, "%s, %s", cpuRegNames[rt], cop0RegNames[fs]);
				break;
			case COP0_FMT:
				line = ins.name;
				break;
			default:
				throw new DisassemblerException("Disassembler error on instruction type: " + ins.name);
		}

		return line;
	}

	private static final int FMT_S = 16;
	private static final int FMT_D = 17;
	private static final int FMT_W = 20;
	private static final int FMT_L = 21;
	private static final int FMT_BC1 = 8;

	private static String disassembleCop1(int v)
	{
		int function = v & 0x3F;
		int fmt = (v >> 21) & 0x1F;

		// @formatter:off
		Instruction ins = null;
		switch(fmt)
		{
		case FMT_S:
		case FMT_D:
			if((function & 0x30) == 0x30)
			{
				ins = Instruction.COND;
			}
			else
			{
				switch(function)
				{
				case 0: ins = Instruction.FADD; break;
				case 1: ins = Instruction.FSUB; break;
				case 2: ins = Instruction.FMUL; break;
				case 3: ins = Instruction.FDIV; break;
				case 4: ins = Instruction.SQRT; break;
				case 5: ins = Instruction.ABS; break;
				case 6: ins = Instruction.MOV; break;
				case 7: ins = Instruction.NEG; break;
				case 8: ins = Instruction.ROUNDL; break;
				case 9: ins = Instruction.TRUNCL; break;
				case 10: ins = Instruction.CEILL; break;
				case 11: ins = Instruction.FLOORL; break;
				case 12: ins = Instruction.ROUNDW; break;
				case 13: ins = Instruction.TRUNCW; break;
				case 14: ins = Instruction.CEILW; break;
				case 15: ins = Instruction.FLOORW; break;
				case 32: ins = Instruction.CVTS; break;
				case 33: ins = Instruction.CVTD; break;
				case 36: ins = Instruction.CVTW; break;
				case 37: ins = Instruction.CVTL; break;
				}
			}
			break;
		case FMT_W:
		case FMT_L:
			switch(function)
			{
			case 32: ins = Instruction.CVTS; break;
			case 33: ins = Instruction.CVTD; break;
			}
			break;
		case FMT_BC1:
			int bc = (v >> 16) & 3;
			switch(bc)
			{
			case 0: ins = Instruction.BC1F; break;
			case 1: ins = Instruction.BC1T; break;
			case 2: ins = Instruction.BC1FL; break;
			case 3: ins = Instruction.BC1TL; break;
			}
			break;

		case 0: ins = Instruction.MFC1; break;
		case 1: ins = Instruction.DMFC1; break;
		case 2: ins = Instruction.CFC1; break;
		case 4: ins = Instruction.MTC1; break;
		case 5: ins = Instruction.DMTC1; break;
		case 6: ins = Instruction.CTC1; break;

		default:
			throw new DisassemblerException("Unknown FPU format for instruction %08X", v);
		}
		// @formatter:on

		if (ins == null)
			throw new DisassemblerException("Unknown FPU instruction %08X", v);

		if (ins == Instruction.CVTS && fmt == FMT_S || ins == Instruction.CVTD && fmt == FMT_D)
			throw new DisassemblerException("Illegal conversion with FPU instruction %08X", v);

		String line = "";
		String insName = "";
		int ft = (v >> 16) & 0x1F;
		int fs = (v >> 11) & 0x1F;
		int fd = (v >> 6) & 0x1F;
		short immediate = (short) v;
		int cc;

		char fmtc = 0;
		switch (fmt) {
			case FMT_S:
				fmtc = 'S';
				break;
			case FMT_D:
				fmtc = 'D';
				break;
			case FMT_W:
				fmtc = 'W';
				break;
			case FMT_L:
				fmtc = 'L';
				break;
		}

		switch (ins.format) {
			case FD_FS_FT:
				insName = ins.name + fmtc;
				line = AsmUtils.getFormattedLine(insName, "%s, %s, %s", fpuRegNames[fd], fpuRegNames[fs], fpuRegNames[ft]);
				break;
			case FD_FS:
				insName = ins.name + fmtc;
				line = AsmUtils.getFormattedLine(insName, "%s, %s", fpuRegNames[fd], fpuRegNames[fs]);
				break;
			case RT_FS:
				line = AsmUtils.getFormattedLine(ins.name, "%s, %s", cpuRegNames[ft], fpuRegNames[fs]);
				break;
			case FPU_OFFSET:
				cc = (v >> 18) & 3;
				if (cc != 0)
					line = AsmUtils.getFormattedLine(ins.name, "%X, %X", cc, 4 * immediate);
				else
					line = AsmUtils.getFormattedLine(ins.name, "%X", 4 * immediate);
				break;
			case COND_FMT:
				int cond = v & 0xF;
				insName = ins.name + fpuCondNames[cond] + "." + fmtc;
				cc = ((v >> 8) & 3);
				if (cc != 0)
					line = AsmUtils.getFormattedLine(insName, "%X, %s, %s", cc, fpuRegNames[fs], fpuRegNames[ft]);
				else
					line = AsmUtils.getFormattedLine(insName, "%s, %s", fpuRegNames[fs], fpuRegNames[ft]);
				break;
			default:
				throw new DisassemblerException("Disassembler error on instruction type: " + ins.name);
		}

		return line;
	}

	public static Line[] assemble(Line[] lines) throws AssemblerException
	{
		for (Line line : lines)
			line.str = cleanLine(line.str);

		//XXX move this into function?
		CaseInsensitiveMap<Integer> labelMap = new CaseInsensitiveMap<>();
		Line[] out = new Line[lines.length - labelMap.size()];

		// make a label map
		int currentOffset = 0;
		for (Line line : lines) {
			if (line.str.startsWith("."))
				labelMap.put(line.str, currentOffset);
			else
				currentOffset += 4;
		}

		int currentOutLine = 0;
		for (Line line : lines) {
			if (line.str.startsWith("."))
				continue;

			substituteLabel(line, labelMap, 4 * currentOutLine);

			out[currentOutLine] = assemble(line);
			currentOutLine++;
		}

		return out;
	}

	public static List<Line> assemble(List<Line> lines) throws AssemblerException
	{
		CaseInsensitiveMap<Integer> labelMap = getLabelMap(lines);
		List<Line> out = new LinkedList<>();

		for (Line line : lines)
			line.str = cleanLine(line.str);

		int currentOffset = 0;
		for (Line line : lines) {
			if (line.str.startsWith("."))
				continue; // we don't assemble labels

			// replace any labels we find
			substituteLabel(line, labelMap, currentOffset);

			out.add(assemble(line));
			currentOffset += 4;
		}

		return out;
	}

	/**
	 * Iterates over a list of function lines and removes names defined through #DEF and #UNDEF,
	 * replacing them with the proper register names.
	 * @param lines
	 */
	public static void removeVarNames(List<Line> lines)
	{
		// for variables named with #DEF
		// type is <regname,varname>
		DualHashMap<String, String> nameMap = new DualHashMap<>();

		Iterator<Line> iter = lines.iterator();

		while (iter.hasNext()) {
			Line line = iter.next();
			String ins = line.getString(0);

			if (ins.equals("#DEF")) {
				if (line.numTokens() != 3)
					throw new InputFileException(line, "Invalid #DEF statement: %n%s", line.trimmedInput());

				// #DEF A0 *Counter
				String reg = line.getString(1).toUpperCase();
				String name = line.getString(2);

				if (!cpuRegMap.containsKey(reg) && !fpuRegMap.containsKey(reg) && !cpuRegMap.containsKey(reg))
					throw new InputFileException(line, "Cannot define unknown register: %s%n%s", reg, line.trimmedInput());

				if (nameMap.contains(reg))
					throw new InputFileException(line, "Cannot redefine register %s from %s to %s %n%s",
						reg, nameMap.get(reg), name, line.trimmedInput());

				if (nameMap.containsInverse(name))
					throw new InputFileException(line, "Register name already in use: %s is assigned to %s %n%s",
						name, nameMap.getInverse(name), line.trimmedInput());

				if (name.charAt(0) != '*')
					throw new InputFileException(line, "Register name must begin with * %n%s", line.trimmedInput());

				if (name.endsWith("[.+]"))
					throw new InputFileException(line, "Register name may not have an offset. %n%s", line.trimmedInput());

				nameMap.add(reg, name);
				iter.remove();
			}
			else if (ins.equals("#UNDEF")) {
				if (line.numTokens() < 2)
					throw new InputFileException(line, "Invalid #UNDEF statement: %n%s", line.trimmedInput());

				// #UNDEF A0 ... A3
				String reg = line.getString(1).toUpperCase();

				if (line.numTokens() == 2 && reg.equalsIgnoreCase("ALL"))
					nameMap.clear();
				else {
					for (int i = 1; i < line.numTokens(); i++) {
						reg = line.getString(i).toUpperCase();
						if (!nameMap.contains(reg))
							throw new InputFileException(line, "Register is not defined: %s %n%s", reg, line.trimmedInput());
						nameMap.remove(reg);
					}
				}
				iter.remove();
			}
			else {
				for (int i = 1; i < line.numTokens(); i++) {
					String s = line.getString(i);
					if (nameMap.contains(s.toUpperCase()))
						throw new InputFileException(line, "Cannot use bare register %s while it is defined: %n%s",
							s, line.trimmedInput());

					if (nameMap.containsInverse(s))
						line.set(i, nameMap.getInverse(s));
				}
			}
		}
	}

	/**
	 * Creates a label map for a list of lines.
	 * Does not alter the lines.
	 */
	public static CaseInsensitiveMap<Integer> getLabelMap(List<Line> lines) throws AssemblerException
	{
		CaseInsensitiveMap<Integer> labelMap = new CaseInsensitiveMap<>();

		int currentOffset = 0;
		for (Line line : lines) {
			String cleaned = cleanLine(line.str);
			if (cleaned.startsWith(".")) {
				if (labelMap.containsKey(cleaned))
					throw new InputFileException(line, "Duplicate label: %s%nLabels within a function must be unique!", cleaned);
				else
					labelMap.put(cleaned, currentOffset);
			}
			else
				currentOffset += 4;
		}

		return labelMap;
	}

	private static void substituteLabel(Line line, CaseInsensitiveMap<Integer> labelMap, int currentOffset)
	{
		// only support labels for branches
		if (line.str.contains(".") && line.str.startsWith("B")) {
			String[] tokens = line.str.split(" ");
			StringBuilder newLineBuilder = new StringBuilder();
			for (String token : tokens) {
				if (token.startsWith(".")) {
					if (!labelMap.containsKey(token))
						throw new AssemblerException(line, "Unknown label: %s %n%s", token, line.trimmedInput());

					newLineBuilder.append(String.format("%X", labelMap.get(token) - currentOffset - 4));
				}
				else
					newLineBuilder.append(token).append(" ");
			}
			line.str = newLineBuilder.toString().trim();
		}
	}

	private static String cleanLine(String line)
	{
		//	line.str = line.str.trim().toUpperCase().replaceAll("\t", " ").replaceAll("[)$]", "").replaceAll("[(]", " ");
		String s = line.trim().toUpperCase().replaceAll("\t", " ");
		return s.replaceAll("[)$]", "").replaceAll("[(]", " ");
	}

	private static final Pattern IntWithOffsetPattern = Pattern.compile(
		"((?:[0-9]+[`'])|(?:[0-9A-F]+))(?:\\[((?:[0-9]+[`'])|(?:[0-9A-F]+))\\])?");
	private static final Matcher IntWithOffsetMatcher = IntWithOffsetPattern.matcher("");

	private static int parseIntWithOffset(String s) throws InvalidInputException
	{
		IntWithOffsetMatcher.reset(s);
		if (!IntWithOffsetMatcher.matches())
			throw new InvalidInputException("Could not parse " + s + " as an integer.");

		int base = DataUtils.parseIntString(IntWithOffsetMatcher.group(1));
		int offset = IntWithOffsetMatcher.group(2) == null ? 0 : DataUtils.parseIntString(IntWithOffsetMatcher.group(2));
		return base + offset;
	}

	public static Line assemble(Line in) throws AssemblerException
	{
		return in.createLine(assemble(in, true));
	}

	private static String assemble(Line line, boolean shouldCleanLine) throws AssemblerException
	{
		if (shouldCleanLine)
			line.str = cleanLine(line.str);

		String temp = line.str;

		if (temp.equals(NOP))
			return "00000000";

		String insName = "";
		if (temp.startsWith("C.")) {
			insName = "C.";
			temp = temp.substring(2).replaceAll("\\.", ", ");
		}
		else if (temp.lastIndexOf('.') > 0) {
			insName = temp.substring(0, temp.lastIndexOf('.') + 1);
			temp = temp.substring(temp.lastIndexOf('.') + 1).trim();
		}
		else if (temp.lastIndexOf(' ') > 0) {
			insName = temp.substring(0, temp.indexOf(' '));
			temp = temp.substring(temp.indexOf(' ')).trim();
		}
		else
			insName = temp;

		if (insName.equals(BRANCH)) {
			insName = Instruction.BEQ.name;
			temp = "R0, R0, " + temp;
		}

		line.str = temp;

		String out;
		String[] args = temp.split(",* +");

		Instruction ins = InstructionMap.get(insName);

		if (ins != null) {
			if (args.length != ins.format.argc)
				throw new AssemblerException(line,
					"Incorrect format for instruction: \"%s\" from line \"%s\"", insName, line.trimmedInput());

			int v = 0;
			try {
				switch (ins.type) {
					case NORMAL:
						v = assembleNormal(line, ins, args);
						break;
					case SPECIAL:
						v = assembleSpecial(line, ins, args);
						break;
					case REGIMM:
						v = assembleRegimm(line, ins, args);
						break;
					case COP0:
					case COP0_C0:
						v = assembleCop0(line, ins, args);
						break;
					case COP1:
						v = assembleCop1(line, ins, args);
						break;
					case COP2:
						throw new UnsupportedOperationException("COP2 not supported: " + line.trimmedInput());
				}
			}
			catch (AssemblerException e) {
				throw new AssemblerException(line, "%s%n\"%s\" from line \"%s\"", e.getMessage(), insName, line.trimmedInput());
			}
			catch (InvalidInputException e) {
				throw new AssemblerException(line, "Invalid instruction caused NumberFormatException:"
					+ "%n\"%s\" from line \"%s\"", insName, line.trimmedInput());
			}
			catch (ArrayIndexOutOfBoundsException e) {
				throw new AssemblerException(line, "Invalid instruction caused ArrayIndexOutOfBoundsException:"
					+ "%n\"%s\" from line \"%s\"", insName, line.trimmedInput());
			}

			out = String.format("%08X", v);
		}
		else
			throw new AssemblerException(line, "Unrecognized instruction:"
				+ "%n\"%s\" from line \"%s\"", insName, line.trimmedInput());

		return out;
	}

	private static int assembleNormal(Line line, Instruction ins, String[] args) throws InvalidInputException
	{
		int v = 0;
		int rs, rt;
		int jmpoffset, immediate;

		switch (ins.format) {
			case RS_RT_OFFSET:
				rs = getCpuRegID(line, args[0]);
				rt = getCpuRegID(line, args[1]);
				immediate = DataUtils.parseIntString(args[2]);
				//	immediate = ((short)Long.parseLong(tokens[2], 16) / 4) & 0x0000FFFF;

				if (Math.abs(immediate) >= 0x40000)
					throw new AssemblerException(line, "Branch target meets or exceeds maxmimum range (0x40000): " + args[2]);

				v = ins.id << 26 | rs << 21 | rt << 16 | ((immediate / 4) & 0xFFFF);
				break;
			case RS_OFFSET:
				rs = getCpuRegID(line, args[0]);
				immediate = DataUtils.parseIntString(args[1]);
				//immediate = ((short)Long.parseLong(tokens[1], 16) / 4) & 0x0000FFFF;

				if (Math.abs(immediate) >= 0x40000)
					throw new AssemblerException(line, "Branch target meets or exceeds maxmimum range (0x40000): " + args[1]);

				v = ins.id << 26 | rs << 21 | ((immediate / 4) & 0xFFFF);
				break;
			case J_TARGET:
				jmpoffset = parseIntWithOffset(args[0]);
				jmpoffset = (jmpoffset - SEGMENT) >> 2;
				v = ins.id << 26 | jmpoffset;
				break;
			case RT_RS_IMMEDIATE:
				rt = getCpuRegID(line, args[0]);
				rs = getCpuRegID(line, args[1]);
				// immediate values can be supplied in decimal
				immediate = DataUtils.parseIntString(args[2]) & 0x0000FFFF;
				v = ins.id << 26 | rs << 21 | rt << 16 | immediate;
				break;
			case LUI_FMT:
				rt = getCpuRegID(line, args[0]);
				immediate = DataUtils.parseIntString(args[1]) & 0x0000FFFF;
				v = ins.id << 26 | rt << 16 | immediate;
				break;
			case RT_OFFSET_BASE:
				rs = getCpuRegID(line, args[2]);
				rt = getCpuRegID(line, args[0]);
				immediate = DataUtils.parseIntString(args[1]) & 0x0000FFFF;
				v = ins.id << 26 | rs << 21 | rt << 16 | immediate;
				break;
			case FPU_RT_OFFSET_BASE:
				rs = getCpuRegID(line, args[2]);
				rt = getFpuRegID(line, args[0]);
				immediate = DataUtils.parseIntString(args[1]) & 0x0000FFFF;
				v = ins.id << 26 | rs << 21 | rt << 16 | immediate;
				break;
			case CACHE_FMT:
				rs = getCpuRegID(line, args[2]);
				int op = (int) Long.parseLong(args[0], 16) & 0x001F;
				immediate = DataUtils.parseIntString(args[1]) & 0x0000FFFF;
				v = ins.id << 26 | rs << 21 | op << 16 | immediate;
				break;
			default:
				throw new AssemblerException(line, "Assembler error on instruction type: " + ins.name);
		}

		return v;
	}

	private static int assembleSpecial(Line line, Instruction ins, String[] args) throws InvalidInputException
	{
		int v = 0;
		int rs, rt, rd, sa;

		switch (ins.format) {
			case RD_RT_SA:
				rd = getCpuRegID(line, args[0]);
				rt = getCpuRegID(line, args[1]);
				sa = DataUtils.parseIntString(args[2]);
				v = SPECIAL_OPCODE << 26 | rt << 16 | rd << 11 | sa << 6 | ins.id;
				break;
			case RD_RT_RS:
				rd = getCpuRegID(line, args[0]);
				rt = getCpuRegID(line, args[1]);
				rs = getCpuRegID(line, args[2]);
				v = SPECIAL_OPCODE << 26 | rs << 21 | rt << 16 | rd << 11 | ins.id;
				break;
			case JR_FMT:
				rs = getCpuRegID(line, args[0]);
				v = rs << 21 | ins.id;
				break;
			case JALR_FMT:
				rs = getCpuRegID(line, args[0]);
				rd = getCpuRegID(line, args[1]);
				v = rs << 21 | rd << 11 | ins.id;
				break;
			case SYSCALL_FMT:
				int offset = DataUtils.parseIntString(args[0]) & 0x000FFFFF;
				v = offset << 6 | ins.id;
				break;
			case RS_RT:
				rs = getCpuRegID(line, args[0]);
				rt = getCpuRegID(line, args[1]);
				v = SPECIAL_OPCODE << 26 | rs << 21 | rt << 16 | ins.id;
				break;
			case RD:
				rd = getCpuRegID(line, args[0]);
				v = SPECIAL_OPCODE << 26 | rd << 11 | ins.id;
				break;
			case RS:
				rs = getCpuRegID(line, args[0]);
				v = SPECIAL_OPCODE << 26 | rs << 21 | ins.id;
				break;
			case RD_RS_RT:
				rd = getCpuRegID(line, args[0]);
				rs = getCpuRegID(line, args[1]);
				rt = getCpuRegID(line, args[2]);
				v = SPECIAL_OPCODE << 26 | rs << 21 | rt << 16 | rd << 11 | ins.id;
				break;
			default:
				throw new AssemblerException(line, "Assembler error on instruction type: " + ins.name);
		}

		return v;
	}

	private static int assembleRegimm(Line line, Instruction ins, String[] args) throws InvalidInputException
	{
		int v = 0;
		int rs, immediate;

		switch (ins.format) {
			case RS_OFFSET:
				rs = getCpuRegID(line, args[0]);
				immediate = DataUtils.parseIntString(args[1]);

				if (Math.abs(immediate) >= 0x40000)
					throw new AssemblerException(line, "Branch target meets or exceeds maxmimum range (0x40000): " + args[1]);

				v = REGIMM_OPCODE << 26 | rs << 21 | ins.id << 16 | ((immediate / 4) & 0xFFFF);
				break;
			default:
				throw new AssemblerException(line, "Assembler error on instruction type: " + ins.name);
		}

		return v;
	}

	private static int assembleCop0(Line line, Instruction ins, String[] args)
	{
		int v = 0;
		int rt, rd;

		switch (ins.format) {
			case RT_FS:
				rt = getCpuRegID(line, args[0]);
				rd = getCop0RegID(line, args[1]);
				v = COP0_OPCODE << 26 | ins.id << 21 | rt << 16 | rd << 11;
				break;
			case COP0_FMT:
				assert (ins.type == COP0_C0);
				v = COP0_OPCODE << 26 | 1 << 25 | ins.id;
				break;
			default:
				throw new AssemblerException(line, "Assembler error on instruction type: " + ins.name);
		}

		return v;
	}

	private static int assembleCop1(Line line, Instruction ins, String[] args) throws InvalidInputException
	{
		int v = 0;
		int cc = 0;
		int fmt, ft, fs, fd, immediate;

		switch (ins.format) {
			case FD_FS_FT:
				fmt = getFormat(line, args[0]);
				fd = getFpuRegID(line, args[1]);
				fs = getFpuRegID(line, args[2]);
				ft = getFpuRegID(line, args[3]);
				v = COP1_OPCODE << 26 | fmt << 21 | ft << 16 | fs << 11 | fd << 6 | ins.id;
				break;
			case FD_FS:
				fmt = getFormat(line, args[0]);
				fd = getFpuRegID(line, args[1]);
				fs = getFpuRegID(line, args[2]);
				v = COP1_OPCODE << 26 | fmt << 21 | fs << 11 | fd << 6 | ins.id;
				break;
			case RT_FS:
				int rt = getCpuRegID(line, args[0]);
				fs = getFpuRegID(line, args[1]);
				v = COP1_OPCODE << 26 | ins.id << 21 | rt << 16 | fs << 11;
				break;
			case FPU_OFFSET:
				int immToken = 0;
				if (args.length == 2) {
					cc = Integer.parseInt(args[0]) & 7; //XXX parseInt is not base-16?
					immToken = 1;
				}
				immediate = DataUtils.parseIntString(args[immToken]);

				if (Math.abs(immediate) >= 0x40000)
					throw new AssemblerException(line, "Branch target meets or exceeds maxmimum range (0x40000): " + args[immToken]);

				v = COP1_OPCODE << 26 | 8 << 21 | cc << 18 | ins.id << 16 | ((immediate / 4) & 0xFFFF);
				break;
			case COND_FMT:
				int cond = getFpuCond(line, args[0]);
				fmt = getFormat(line, args[1]);
				if (args.length == 5) {
					cc = Integer.parseInt(args[2]) & 7; //XXX parseInt is not base-16?
					fs = getFpuRegID(line, args[3]);
					ft = getFpuRegID(line, args[4]);
				}
				else {
					fs = getFpuRegID(line, args[2]);
					ft = getFpuRegID(line, args[3]);
				}
				v = COP1_OPCODE << 26 | fmt << 21 | ft << 16 | fs << 11 | cc << 8 | 3 << 4 | cond;
				break;
			default:
				throw new AssemblerException(line, "Assembler error on instruction type: " + ins.name);
		}

		return v;
	}

	public static boolean isCpuReg(String name)
	{
		return cpuRegMap.containsKey(name);
	}

	private static int getCpuRegID(Line line, String name)
	{
		if (cpuRegMap.containsKey(name))
			return cpuRegMap.get(name);

		throw new AssemblerException(line, "No such CPU register: " + name);
	}

	private static int getCop0RegID(Line line, String name)
	{
		if (cop0RegMap.containsKey(name))
			return cop0RegMap.get(name);

		throw new AssemblerException(line, "No such COP0 register: " + name);
	}

	public static boolean isFpuReg(String name)
	{
		return fpuRegMap.containsKey(name);
	}

	private static int getFpuRegID(Line line, String name)
	{
		if (fpuRegMap.containsKey(name))
			return fpuRegMap.get(name);

		throw new AssemblerException(line, "No such COP1 register: " + name);
	}

	private static int getFpuCond(Line line, String name)
	{
		if (fpuCondMap.containsKey(name))
			return fpuCondMap.get(name);

		throw new AssemblerException(line, "No such FPU comparison: " + name);
	}

	private static int getFormat(Line line, String fmts)
	{
		switch (fmts) {
			case "S":
				return FMT_S;
			case "D":
				return FMT_D;
			case "W":
				return FMT_W;
			case "L":
				return FMT_L;
			default:
				throw new AssemblerException(line, "Invalid FPU format " + fmts);
		}
	}

	// public helper method
	public static int getJumpIns(long dest)
	{
		long opcode = 0x08000000;
		long addr = (dest & 0x0FFFFFFF) >>> 2;
		return (int) (opcode | addr);
	}

	public static int getJumpAddress(int insAddress, int v)
	{
		int jumpAddr = -1;

		switch (v >>> 26) {
			case 2: // JUMP
				int seg = (insAddress & 0xF0000000);
				jumpAddr = seg + ((v & 0x03FFFFFF) << 2);
				break;
			case 1:
				switch ((v >>> 16) & 0x1F) {
					case 0: // BLTZ
					case 1: // BGEZ
					case 2: // BLTZL
					case 3: // BGEZL
					case 16: // BLTZAL
					case 17: // BGEZAL
					case 18: // BLTZALL
					case 19: // BGEZALL
						jumpAddr = insAddress + 4 * (short) v + 4; // lowest 16 bits, sign extended
						break;
				}
				break;
			case 4: // BEQ
			case 5: // BNE
			case 6: // BLEZ
			case 7: // BGTZ
			case 20: //BEQL
			case 21: //BNEL
			case 22: // BLEZL
			case 23: // BGTZL
				jumpAddr = insAddress + 4 * (short) v + 4; // lowest 16 bits, sign extended
				break;
			case COP1_OPCODE:
				// BC1F, BC1FL, BC1T, BC1TL
				if ((v >>> 21 & 0x1F) == 8) {
					jumpAddr = insAddress + 4 * (short) v + 4; // lowest 16 bits, sign extended
				}
		}

		return jumpAddr;
	}

	/*
	 ****************************************************************************
	 ** Load and Store Instructions                                            **
	 ****************************************************************************
	SYNC                      SYNChronize shared memory

	 ****************************************************************************
	 ** Special Instructions                                                   **
	 ****************************************************************************
	BREAK    offset           BREAKpoint
	SYSCALL  offset           SYStem CALL

	 ****************************************************************************
	 ** Exception Instructions                                                 **
	 ****************************************************************************
	TEQ      rs,rt            Trap if EQual
	TGE      rs,rt            Trap if Greater Than or Equal
	TGEU     rs,rt            Trap if Greater Than or Equal Unsigned
	TLT      rs,rt            Trap if Less Than
	TLTU     rs,rt            Trap if Less Than Unsigned
	TNE      rs,rt            Trap if not Equal

	TEQI     rs,immediate     Trap if EQual Immediate
	TGEI     rs,immediate     Trap if Greater Than or Equal Immediate
	TGEIU    rs,immediate     Trap if Greater Than or Equal Immediate Unsigned
	TLTI     rs,immediate     Trap if Less Than Immediate
	TLTIU    rs,immediate     Trap if Less Than Immediate Unsigned
	TNEI     rs,immediate     Trap if not Equal Immediate
	 */
}

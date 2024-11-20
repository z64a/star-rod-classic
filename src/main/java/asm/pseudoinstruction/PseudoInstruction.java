package asm.pseudoinstruction;

import static asm.MIPS.Instruction.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Stack;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;

import app.StarRodException;
import app.input.IOUtils;
import app.input.InputFileException;
import app.input.InvalidInputException;
import app.input.Line;
import asm.AsmUtils;
import asm.MIPS;
import asm.MIPS.AssemblerException;
import asm.MIPS.Instruction;
import asm.pseudoinstruction.PatternMatch.PIPattern;
import game.shared.DataUtils;
import game.shared.decoder.BaseDataDecoder;

public class PseudoInstruction
{
	public static final String RESERVED = "RESERVED";

	private static List<String> validHelperInstructions = new LinkedList<>();

	public static boolean DEBUG_PI = false;

	static {
		/*
		validHelperInstructions.add("IF");
		validHelperInstructions.add("ELIF");
		validHelperInstructions.add("ELSE");
		validHelperInstructions.add("ENDIF");
		 */

		validHelperInstructions.add("LOOP");
		validHelperInstructions.add("BREAKLOOP");
		validHelperInstructions.add("ENDLOOP");
		//	validHelperInstructions.add("PUSH");
		//	validHelperInstructions.add("POP");
		//	validHelperInstructions.add("JPOP");
		validHelperInstructions.add("SUBI");
		validHelperInstructions.add("SUBIU");
		validHelperInstructions.add("BLT");
		validHelperInstructions.add("BGT");
		validHelperInstructions.add("BLE");
		validHelperInstructions.add("BGE");
		validHelperInstructions.add("BLTL");
		validHelperInstructions.add("BGTL");
		validHelperInstructions.add("BLEL");
		validHelperInstructions.add("BGEL");
		validHelperInstructions.add("BEQI");
		validHelperInstructions.add("BNEI");
		validHelperInstructions.add("BEQIL");
		validHelperInstructions.add("BNEIL");
		validHelperInstructions.add("SWI");
		validHelperInstructions.add("SHI");
		validHelperInstructions.add("SBI");
	}

	private static class Format
	{
		private final PIPattern pattern;
		private final Instruction lastIns;

		public Format(PIPattern pattern, Instruction lastIns)
		{
			this.pattern = pattern;
			this.lastIns = lastIns;
		}

		@Override
		public int hashCode()
		{
			return (pattern.ordinal() << 16) + lastIns.ordinal();
		}

		@Override
		public boolean equals(Object obj)
		{
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			Format other = (Format) obj;
			if (lastIns != other.lastIns)
				return false;
			if (pattern != other.pattern)
				return false;
			return true;
		}
	}

	// both of these maps are generated automatically based on the Type enum
	private static HashMap<Format, Type> formatTypeMap;
	private static HashMap<String, Type> opcodeNameMap;
	static {
		formatTypeMap = new HashMap<>();
		opcodeNameMap = new HashMap<>();
		for (Type t : Type.values()) {
			opcodeNameMap.put(t.opcode, t);
			formatTypeMap.put(new Format(t.pattern, t.lastIns), t);
		}
		opcodeNameMap.put("LIA", Type.LA);
		opcodeNameMap.put("LIO", Type.LI);
	}

	public static boolean isValidOpcode(String opcode)
	{
		PushPopOpcodeMatcher.reset(opcode);
		if (PushPopOpcodeMatcher.matches())
			return true;

		if (validHelperInstructions.contains(opcode))
			return true;

		return opcodeNameMap.containsKey(opcode);
	}

	public static int getLengthFromLine(String[] tokens)
	{
		String opcode = tokens[0];

		PushPopOpcodeMatcher.reset(opcode);
		if (PushPopOpcodeMatcher.matches()) {
			if (opcode.startsWith("PUSH"))
				opcode = "PUSH";
			else if (opcode.startsWith("POP"))
				opcode = "POP";
			else if (opcode.startsWith("JPOP"))
				opcode = "JPOP";
		}

		switch (opcode) {
			case "BREAKLOOP":
			case "ENDLOOP":
				return 2;
			case "LOOP":
				return Loop.getLength(tokens);

			case "PUSH":
				return tokens.length;
			case "POP":
				return tokens.length;
			case "JPOP":
				return tokens.length + 1;

			case "SUBI":
			case "SUBIU":
				return 2;
			case "BLT":
			case "BLTL":
				return 2;
			case "BGT":
			case "BGTL":
				return 2;
			case "BLE":
			case "BLEL":
				return 2;
			case "BGE":
			case "BGEL":
				return 2;
			case "BEQI":
			case "BEQIL":
			case "BNEI":
			case "BNEIL":
				return 2;

			case "ENDIF":
				return 0;
			case "ELSE":
				return 2;
			case "IF":
			case "ELIF":
				return 4;

			case "SBI":
			case "SHI":
				return 2;

			case "SWI":
				String simmValue = tokens[1];
				try {
					if (!simmValue.startsWith("$") && !simmValue.startsWith("*")) {
						int amount = DataUtils.parseIntString(simmValue);
						if (amount >= 0 && amount < 0x10000)
							return 2;
					}
				}
				catch (InvalidInputException e) {
					throw new StarRodException("Invalid immediate value: " + tokens[2]);
				}
				return 3;

			case "LI":
				String limmValue = tokens[2];
				try {
					if (!limmValue.startsWith("$") && !limmValue.startsWith("*")) {
						int amount = DataUtils.parseIntString(limmValue);
						if (amount >= 0 && amount < 0x10000)
							return PIPattern.LIH.length;
					}
				}
				catch (InvalidInputException e) {
					throw new StarRodException("Invalid immediate value: " + tokens[2]);
				}
				return PIPattern.LIW.length;

			case "LIF":
				try {
					int bits = Float.floatToRawIntBits(Float.parseFloat(tokens[2]));
					if ((bits & 0xFFFF) == 0)
						return PIPattern.LIHF.length;
				}
				catch (NumberFormatException e) {
					throw new StarRodException("NumberFormatException caused by: %s %nExpected a float value.", tokens[2]);
				}
				return PIPattern.LIWF.length;
		}

		return opcodeNameMap.get(tokens[0]).pattern.length;
	}

	public static int getLengthFromLine(Line line)
	{
		String opcode = line.getString(0);
		int len = line.numTokens();

		PushPopOpcodeMatcher.reset(opcode);
		if (PushPopOpcodeMatcher.matches()) {
			if (opcode.startsWith("PUSH"))
				opcode = "PUSH";
			else if (opcode.startsWith("POP"))
				opcode = "POP";
			else if (opcode.startsWith("JPOP"))
				opcode = "JPOP";
		}

		switch (opcode) {
			case "BREAKLOOP":
			case "ENDLOOP":
				return 2;
			case "LOOP":
				return Loop.getLength(line);

			case "PUSH":
				return len;
			case "POP":
				return len;
			case "JPOP":
				return len + 1;

			case "SUBI":
			case "SUBIU":
				return 2;
			case "BLT":
			case "BLTL":
				return 2;
			case "BGT":
			case "BGTL":
				return 2;
			case "BLE":
			case "BLEL":
				return 2;
			case "BGE":
			case "BGEL":
				return 2;
			case "BEQI":
			case "BEQIL":
			case "BNEI":
			case "BNEIL":
				return 2;

			case "ENDIF":
				return 0;
			case "ELSE":
				return 2;
			case "IF":
			case "ELIF":
				return 4;

			case "SBI":
			case "SHI":
				return 2;

			case "SWI":
				if (line.numTokens() != 4)
					throw new InputFileException(line, "Invalid SI instruction: " + line.trimmedInput());
				String simmValue = line.getString(1);
				try {
					if (!simmValue.startsWith("$") && !simmValue.startsWith("*")) {
						int amount = DataUtils.parseIntString(simmValue);
						if (amount >= 0 && amount < 0x10000)
							return 2;
					}
				}
				catch (InvalidInputException e) {
					throw new InputFileException(line, "Invalid immediate value: " + line.trimmedInput());
				}
				return 3;

			case "LI":
				if (line.numTokens() != 3)
					throw new InputFileException(line, "Invalid LI instruction: " + line.trimmedInput());
				String limmValue = line.getString(2);
				try {
					if (!limmValue.startsWith("$")) {
						int amount = DataUtils.parseIntString(limmValue);
						if (amount >= 0 && amount < 0x10000)
							return PIPattern.LIH.length;
					}
				}
				catch (InvalidInputException e) {
					throw new InputFileException(line, "Invalid immediate value: " + line.trimmedInput());
				}
				return PIPattern.LIW.length;

			case "LIF":
				if (line.numTokens() != 3)
					throw new InputFileException(line, "Invalid LIF instruction: " + line.trimmedInput());
				int bits = Float.floatToRawIntBits(line.getFloat(2));
				if ((bits & 0xFFFF) == 0)
					return PIPattern.LIHF.length;
				else
					return PIPattern.LIWF.length;
		}

		return opcodeNameMap.get(opcode).pattern.length;
	}

	// @formatter:off
	public static enum Type
	{
		CLR ("CLEAR",2, PIPattern.CLR, DADDU, "CLEAR  A0"),
		CPY ("COPY", 3, PIPattern.CPY, DADDU, "COPY  S0, A0"),

		// actually important that these are declared before LI/LIF
		LIH ("LI",   3, PIPattern.LIH,  ADDIU, "LI   A0, 001CFFFF"),
		LIHF("LIF",  3, PIPattern.LIHF, MTC1,	 "LIF  F0, 4.0"),

		LA  ("LA",   3, PIPattern.LIW,  ADDIU, "LA   A0, 80240000"),
		LI  ("LI",   3, PIPattern.LIW,  ORI,	 "LI   A0, 001CFFFF"),
		LIF ("LIF",  3, PIPattern.LIWF, ORI,	 "LIF  F0, 4.0"),

		//	LID ("LID",  3, Pattern.LI, MTC1,	"LID  F0, 4.0"),

		LAB ("LAB",  3, PIPattern.LSA, LB, 	"LAB  A0, 80240000"),
		SAB ("SAB",  3, PIPattern.LSA, SB,	"SAB  A0, 80240000"),
		LABU("LABU", 3, PIPattern.LSA, LBU,	"LABU A0, 80240000"),
		LAH ("LAH",  3, PIPattern.LSA, LH,	"LAH  A0, 80240000"),
		SAH ("SAH",  3, PIPattern.LSA, SH,	"SAH  A0, 80240000"),
		LAHU("LAHU", 3, PIPattern.LSA, LHU,	"LAHU A0, 80240000"),
		LAW ("LAW",  3, PIPattern.LSA, LW,	"LAW  A0, 80240000"),
		SAW ("SAW",  3, PIPattern.LSA, SW,	"SAW  A0, 80240000"),
		LAF ("LAF",  3, PIPattern.LSA, LWC1,	"LAF  F0, 80240000"),
		SAF ("SAF",  3, PIPattern.LSA, SWC1,	"SAF  F0, 80240000"),
		LAD ("LAD",  3, PIPattern.LSA, LDC1,	"LAD  F0, 80240000"),

		LTB ("LTB",  4, PIPattern.LST, LB,	"LTB  A0, V0 (80240000)"),
		LTBU("LTBU", 4, PIPattern.LST, LBU,	"LTBU A0, V0 (80240000)"),
		LTH ("LTH",  4, PIPattern.LST, LH,	"LTH  A0, V0 (80240000)"),
		LTHU("LTHU", 4, PIPattern.LST, LHU,	"LTHU A0, V0 (80240000)"),
		LTW ("LTW",  4, PIPattern.LST, LW,	"LTW  A0, V0 (80240000)"),
		LTF ("LTF",  4, PIPattern.LST, LWC1,	"LTF  F0, V0 (80240000)"),
		STB ("STB",  4, PIPattern.LST, SB,	"STB  A0, V0 (80240000)"),
		STH ("STH",  4, PIPattern.LST, SH,	"STH  A0, V0 (80240000)"),
		STW ("STW",  4, PIPattern.LST, SW,	"STW  A0, V0 (80240000)"),
		STF ("STF",  4, PIPattern.LST, SWC1,	"STF  F0, V0 (80240000)");

		private Type(String opcode, int argc, PIPattern pattern, Instruction lastIns, String example)
		{
			this.opcode = opcode;
			this.pattern = pattern;
			this.lastIns = lastIns;
			this.argc = argc;
			this.example = example;
		}

		private final String opcode;
		private final PIPattern pattern;
		private final Instruction lastIns;
		private final int argc;
		private final String example;
	}
	// @formatter:on

	private final Type type;
	private final int firstLine;
	private final boolean useDelaySlot;

	// register name that takes the place of the final holds the rs register
	// this is also the register used to combine upper/lower, except in
	// some cases where AT is used instead.
	private String mainRegister;

	// register used to hold a table offset, added to table base in 'register'
	// for table pseudoinstructions
	private String auxRegister;

	private int immediate;

	public int getAddress()
	{
		return immediate;
	}

	public Type getType()
	{
		return type;
	}

	public static final Comparator<PseudoInstruction> LINE_ORDER_COMPARATOR = new Comparator<>() {
		@Override
		public int compare(PseudoInstruction a, PseudoInstruction b)
		{
			return a.firstLine - b.firstLine;
		}
	};

	private PseudoInstruction(Type type, int line, boolean delayed)
	{
		this.type = type;
		this.firstLine = line;
		this.useDelaySlot = delayed;

		mainRegister = null;
		auxRegister = null;
	}

	private static PseudoInstruction createFromPseudoASM(Line[] lines, int start)
	{
		boolean delaySlot = false;
		if (lines.length > start + 2)
			delaySlot = lines[start + 2].getString(0).equals(RESERVED);
		return createFromPseudoASM(lines[start], start, delaySlot);
	}

	/**
	 * Creates a PseudoInstruction object based on a line of pseudo-ASM.
	 */
	private static PseudoInstruction createFromPseudoASM(Line line, int start, boolean delaySlot)
	{
		Type type = opcodeNameMap.get(line.getString(0));

		if (type == null)
			return null;

		if (line.numTokens() != type.argc) {
			throw new AssemblerException(line, String.format(
				"Incorrect format for %s: %n"
					+ "\"%s\" %n"
					+ "Correct format example: %n"
					+ "\"%s\"",
				type.opcode, line.trimmedInput(), type.example));
		}

		PseudoInstruction pi = new PseudoInstruction(type, start, delaySlot);
		try {
			switch (pi.type) {
				case CLR:
					pi.mainRegister = line.getString(1);
					break;
				case CPY:
					pi.mainRegister = line.getString(1);
					pi.auxRegister = line.getString(2);
					break;
				case LA:
					pi.mainRegister = line.getString(1);
					pi.immediate = line.getHex(2);
					break;
				case LI:
					try {
						int amount = DataUtils.parseIntString(line.getString(2));
						if (amount >= 0 && amount < 0x10000) {
							pi = new PseudoInstruction(Type.LIH, start, delaySlot);
							amount = (amount << 16) >> 16;
						}
						pi.mainRegister = line.getString(1);
						pi.immediate = amount;
					}
					catch (InvalidInputException e) {
						throw new NumberFormatException(e.getMessage());
					}
					break;
				case LIF:
					int bits = Float.floatToRawIntBits(line.getFloat(2));
					if ((bits & 0xFFFF) == 0)
						pi = new PseudoInstruction(Type.LIHF, start, delaySlot);
					pi.mainRegister = line.getString(1);
					pi.immediate = bits;
					break;
				case LAB:
				case SAB:
				case LABU:
				case LAH:
				case SAH:
				case LAHU:
				case LAW:
				case SAW:
				case LAF:
				case LAD:
				case SAF:
					pi.mainRegister = line.getString(1);
					pi.immediate = line.getHex(2);
					break;
				case LTB:
				case LTBU:
				case LTH:
				case LTHU:
				case LTW:
				case LTF:
				case STB:
				case STH:
				case STW:
				case STF:
					pi.mainRegister = line.getString(1);
					pi.auxRegister = line.getString(2);
					pi.immediate = line.getHex(3);
					break;
				case LIHF:
				case LIH:
					throw new IllegalStateException();
			}
		}
		catch (NumberFormatException e) {
			throw new AssemblerException(line, "Invalid pseudoinstruction caused NumberFormatException: %n%s%n%s",
				line.trimmedInput(), e.getMessage());
		}

		return pi;
	}

	/**
	 * Creates a PseudoInstruction object based on a pattern matching some instruction sequence.
	 */
	private static PseudoInstruction createFromPattern(String[][] tokens, PatternMatch match)
	{
		Type type = formatTypeMap.get(new Format(match.pattern, match.lastIns));
		if (type == null)
			throw new IllegalArgumentException(
				"Unknown pseudoinstruction: " + match.pattern + " " + match.lastIns);
		PseudoInstruction pi = new PseudoInstruction(type, match.line, match.delaySlot);
		if (pi.createFromASM(tokens))
			return pi;
		else
			return null;
	}

	private boolean createFromASM(String[][] tokens)
	{
		String[] lineLUI = null;
		String[] lineBRJ = null;
		String[] lineLLI = null;
		String[] lineMEM = null;
		String[] lineADD = null;

		int upper, lower;

		switch (type.pattern) {
			case CLR:
				// ex:  DADDU     A0, R0, R0
				lineADD = tokens[useDelaySlot ? firstLine + 1 : firstLine];
				mainRegister = lineADD[1];
				return true;

			case CPY:
				// ex:  DADDU     A0, R0, A1
				lineADD = tokens[useDelaySlot ? firstLine + 1 : firstLine];
				mainRegister = lineADD[1];
				auxRegister = lineADD[2];
				return true;

			case LIH:
				// ex:  ADDIU     A0, R0, imm
				lineADD = tokens[useDelaySlot ? firstLine + 1 : firstLine];
				mainRegister = lineADD[1];
				try {
					immediate = DataUtils.parseIntString(lineADD[3]);
				}
				catch (InvalidInputException e) {
					throw new IllegalArgumentException(
						"Could not parse immediate field for " + type.pattern + ": " + immediate);
				}
				return true;

			case LIHF:
				// ex:  LUI       AT, 3F80
				//      MTC1      AT, F0
				lineLUI = tokens[firstLine];
				lineBRJ = useDelaySlot ? tokens[firstLine + 1] : null;
				lineLLI = tokens[firstLine + 1 + (useDelaySlot ? 1 : 0)];

				mainRegister = lineLLI[2];
				if (!lineLUI[1].equals(lineLLI[1]))
					return false;
				immediate = Integer.parseInt(lineLUI[2], 16) << 16;
				return true;

			case LIWF:
				// ex:  LUI       AT, 3F80
				//		ORI       AT, AT, A680
				//      MTC1      AT, F0
				lineLUI = tokens[firstLine];
				lineADD = tokens[firstLine + 1];
				lineBRJ = useDelaySlot ? tokens[firstLine + 2] : null;
				lineLLI = tokens[firstLine + 2 + (useDelaySlot ? 1 : 0)];

				String AT = "AT";
				if (!AT.equals(lineLUI[1]))
					return false;
				if (!AT.equals(lineADD[1]) || !AT.equals(lineADD[2]))
					return false;
				if (!AT.equals(lineLLI[1]))
					return false;

				if (DEBUG_PI && useDelaySlot)
					assert (!branchUsesRegister(lineBRJ, mainRegister));

				mainRegister = lineLLI[2];
				upper = Integer.parseInt(lineLUI[2], 16) << 16;
				lower = (short) Integer.parseInt(lineADD[3], 16);
				immediate = upper + lower;
				return true;

			case LIW:
				// ex:	LUI       A0, 8023
				//		ADDIU     A0, A0, A680
				lineLUI = tokens[firstLine];
				lineBRJ = useDelaySlot ? tokens[firstLine + 1] : null;
				lineLLI = tokens[firstLine + 1 + (useDelaySlot ? 1 : 0)];

				mainRegister = lineLUI[1];
				if (!mainRegister.equals(lineLLI[2]))
					return false;

				if (DEBUG_PI && useDelaySlot)
					assert (!branchUsesRegister(lineBRJ, mainRegister));

				upper = Integer.parseInt(lineLUI[2], 16) << 16;
				lower = (short) Integer.parseInt(lineLLI[3], 16);

				switch (type.lastIns) {
					case ADDIU:
						immediate = upper + lower;
						break;
					case ORI:
						immediate = upper | (lower & 0x0000FFFF);
						break;
					default:
						throw new IllegalArgumentException(
							"Unknown pseudoinstruction: " + type.pattern + " " + type.lastIns);
				}
				return true;

			case LSA:
				lineLUI = tokens[firstLine];
				lineBRJ = useDelaySlot ? tokens[firstLine + 1] : null;
				lineMEM = tokens[firstLine + 1 + (useDelaySlot ? 1 : 0)];

				mainRegister = lineMEM[1];
				if (!lineLUI[1].equals(lineMEM[3]))
					return false;

				upper = Integer.parseInt(lineLUI[2], 16) << 16;
				lower = (short) Integer.parseInt(lineMEM[2], 16);
				immediate = upper + lower;

				switch (type.lastIns) {
					// ex:	LUI       A0, 8024
					//		LHU       A0, 33B0 (A0)
					case LB:
					case LBU:
					case LH:
					case LHU:
					case LW:
						if (DEBUG_PI) {
							assert (lineLUI[1].equals(mainRegister));
							if (useDelaySlot)
								assert (!branchUsesRegister(lineBRJ, mainRegister));
						}
						return true;

					// ex:	LUI       AT, 8024
					//		LWC1      F0, 1B74 (AT)
					case SB:
					case SH:
					case SW:
					case LWC1:
					case LDC1:
					case SWC1:
						if (DEBUG_PI) {
							assert (lineLUI[1].equals("AT"));
							if (useDelaySlot)
								assert (!branchUsesRegister(lineBRJ, mainRegister));
						}
						return true;

					default:
						throw new IllegalArgumentException(
							"Unknown pseudoinstruction: " + type.pattern + " " + type.lastIns);
				}

			case LST:
				lineLUI = tokens[firstLine];
				lineADD = tokens[firstLine + 1];
				lineBRJ = useDelaySlot ? tokens[firstLine + 2] : null;
				lineMEM = tokens[firstLine + 2 + (useDelaySlot ? 1 : 0)];

				mainRegister = lineMEM[1];
				auxRegister = lineADD[3];

				if (!lineLUI[1].equals(lineADD[1]))
					return false;

				if (!lineLUI[1].equals(lineADD[2]))
					return false;

				if (!lineLUI[1].equals(lineMEM[3]))
					return false;

				upper = Integer.parseInt(lineLUI[2], 16) << 16;
				lower = (short) Integer.parseInt(lineMEM[2], 16);
				immediate = upper + lower;

				switch (type.lastIns) {
					// ex:	LUI       V1, 8024
					//		ADDU      V1, V1, V0
					//		LBU       V1, 521C (V1)
					case LB:
					case LBU:
					case LH:
					case LHU:
					case LW:
						if (DEBUG_PI) {
							if (mainRegister.equals(auxRegister))
								assert (lineLUI[1].equals("AT"));
							else
								assert (lineLUI[1].equals(mainRegister));
							if (useDelaySlot)
								assert (!branchUsesRegister(lineBRJ, mainRegister));
						}
						return true;

					// ex:	LUI       AT 8023
					//		ADDU      AT, AT, V0
					//		LWC1      F2, B210 (AT)
					case SB:
					case SH:
					case SW:
					case LWC1:
					case SWC1:
						if (DEBUG_PI) {
							assert (lineLUI[1].equals("AT"));
							if (useDelaySlot)
								assert (!branchUsesRegister(lineBRJ, mainRegister));
						}
						return true;
					default:
						throw new IllegalArgumentException(
							"Unknown pseudoinstruction: " + type.pattern + " " + type.lastIns);
				}
		}
		throw new IllegalArgumentException("Unknown pseudoinstruction type: " + type.pattern);
	}

	private static boolean branchUsesRegister(String[] line, String register)
	{
		Instruction ins = MIPS.InstructionMap.get(line[0]);
		switch (ins) {
			case BEQ:
			case BEQL:
			case BNE:
			case BNEL:
				// fmt: rs,rt,offset
			case JALR:
				// fmt: rs,rd
				return (line[1].equals(register) || line[2].equals(register));
			case BLEZ:
			case BGTZ:
			case BLEZL:
			case BGTZL:
			case BLTZ:
			case BGEZ:
			case BLTZL:
			case BGEZL:
			case BLTZAL:
			case BGEZAL:
			case BLTZALL:
			case BGEZALL:
				// fmt: rs,offset
			case JR:
				// fmt: rs
				return line[1].equals(register);
			case J:
			case JAL:
				// fmt: target
			case BC1F:
			case BC1T:
			case BC1FL:
			case BC1TL:
				// fmt: offset
				return false;
			default:
				throw new IllegalArgumentException("Instruction is not a branch or jump: " + ins);
		}
	}

	/**
	 * Creates a line of pseudo-ASM from this PseudoInstruction object.
	 * @param decoder	optional, used to replace pointers with symbolic names.
	 * @return
	 */
	private String generatePseudoASM(BaseDataDecoder decoder)
	{
		String replacement;
		String addrName;

		if (decoder != null)
			addrName = decoder.getVariableName(immediate);
		else
			addrName = String.format("%08X", immediate);

		switch (type.pattern) {
			case CLR:
				replacement = AsmUtils.getFormattedLine(type.opcode, "%s", mainRegister);
				break;
			case CPY:
				replacement = AsmUtils.getFormattedLine(type.opcode, "%s, %s", mainRegister, auxRegister);
				break;

			case LIH:
				replacement = AsmUtils.getFormattedLine("LI", "%s, %X", mainRegister, immediate);
				break;

			case LIW:
				// ex:	LUI       A0, 8023
				//		ADDIU     A0, A0, A680
				if (decoder != null)
					addrName = decoder.getScriptWord(immediate);
				replacement = AsmUtils.getFormattedLine(type.opcode, "%s, %s", mainRegister, addrName);
				break;

			case LIHF:
			case LIWF:
				replacement = AsmUtils.getFormattedLine("LIF", "%s, %s", mainRegister, Float.intBitsToFloat(immediate));
				break;

			case LSA:
				// ex:	LUI       A0, 8024
				//		LHU       A0, 33B0 (A0)
				replacement = AsmUtils.getFormattedLine(type.opcode, "%s, %s", mainRegister, addrName);
				break;

			case LST:
				// ex:	LUI       V1, 8024
				//		ADDU      V1, V1, V0
				//		LBU       V1, 521C (V1)
				replacement = AsmUtils.getFormattedLine(type.opcode, "%s, %s (%s)", mainRegister, auxRegister, addrName);
				break;

			///	case LA:
			//		replacement = AsmUtils.getFormattedLine(type.opcode, "%s, %s", mainRegister, addrName);
			//		break;

			default:
				throw new IllegalArgumentException(
					"Unknown pseudoinstruction type: " + type.pattern);
		}

		return replacement;
	}

	/**
	 * Generates the underlying ASM code for this PseudoInstruction object.
	 * @param line
	 */
	private String[] generateASM()
	{
		String[] replacement = new String[type.pattern.length];

		int lowerAdd = (short) immediate;
		int upperAdd = ((immediate - lowerAdd) >>> 16);
		lowerAdd &= 0x0000FFFF;

		int lowerMask = (immediate & 0xFFFF);
		int upperMask = (immediate >>> 16);

		switch (type) {
			case CLR:
				replacement[0] = AsmUtils.getFormattedLine("DADDU", "%s, R0, R0", mainRegister);
				break;
			case CPY:
				replacement[0] = AsmUtils.getFormattedLine("DADDU", "%s, %s, R0", mainRegister, auxRegister);
				break;
			case LI:
				// ex:	LUI       A0, 8023
				//		ADDIU     A0, A0, A680
				// all registers always match
				replacement[0] = AsmUtils.getFormattedLine("LUI", "%s, %X", mainRegister, upperMask);
				replacement[1] = AsmUtils.getFormattedLine(type.lastIns.getName(), "%s, %s, %X", mainRegister, mainRegister, lowerMask);
				break;
			case LIH:
				replacement[0] = AsmUtils.getFormattedLine("ADDIU", "%s, R0, %X", mainRegister, immediate);
				break;
			case LA:
				// ex:	LUI       A0, 8023
				//		ADDIU     A0, A0, A680
				// all registers always match
				replacement[0] = AsmUtils.getFormattedLine("LUI", "%s, %X", mainRegister, upperAdd);
				replacement[1] = AsmUtils.getFormattedLine(type.lastIns.getName(), "%s, %s, %X", mainRegister, mainRegister, lowerAdd);
				break;
			case LIF:
				// ex:  LUI       AT, 3F80
				//		ADDIU     AT, AT, A680
				//      MTC1      AT, F0
				replacement[0] = AsmUtils.getFormattedLine("LUI", "AT, %X", upperAdd);
				replacement[1] = AsmUtils.getFormattedLine("ORI", "AT, AT, %X", lowerAdd);
				replacement[2] = AsmUtils.getFormattedLine("MTC1", "AT, %s", mainRegister);
				break;
			case LIHF:
				// ex:  LUI       AT, 3F80
				//      MTC1      AT, F0
				replacement[0] = AsmUtils.getFormattedLine("LUI", "AT, %X", upperMask);
				replacement[1] = AsmUtils.getFormattedLine("MTC1", "AT, %s", mainRegister);
				break;
			/*
			case LID:
				// ex:  LUI       AT, 3F80
				//		ADDIU     AT, AT, A680
				//      MTC1      AT, F0
				//		CVT.D.W	  F4, F4
				replacement[0] = AsmUtils.getFormattedLine(type.opcode, "AT, %X",		"LUI", upper);
				replacement[1] = AsmUtils.getFormattedLine(type.opcode, "AT, AT, %X",	"ADDIU", lower);
				replacement[2] = AsmUtils.getFormattedLine(type.opcode, "AT, %s",		"MTC1", register);
				replacement[3] = AsmUtils.getFormattedLine(type.opcode, "%s, %s",		"CVT.D.W", register, register);
				break;
			 */

			/*
			case "LI":		// LI	A, amount
			{
			assertNoDelaySlot(line, delaySlot);
			String regName = line.getString(1);
			int amount = line.getInt(2);

			if(Short.MIN_VALUE < amount && amount < Short.MAX_VALUE) {
				newinstructions.add(line.createLine("ORI %s, %s, %X", regName, regName, amount));
			}
			else {
				int upper = (amount >>> 16);
				int lower = (amount & 0xFFFF);
				newinstructions.add(line.createLine("LUI %s, %X", regName, upper));
				newinstructions.add(line.createLine("ORI %s, %s, %X", regName, regName, lower));
			}
			continue;
			}
			case "LIF":		// LIF	F0, amount
			{
			assertNoDelaySlot(line, delaySlot);
			String regName = line.getString(1);
			float amount = line.getFloat(2);

			int bits = Float.floatToRawIntBits(amount);
			int upper = (bits >>> 16);
			int lower = (bits & 0xFFFF);

			newinstructions.add(line.createLine("LUI AT, %X", upper));
			if(lower != 0)
				newinstructions.add(line.createLine("ORI AT, AT, %X", lower));
			newinstructions.add(line.createLine("MTC1", "AT, %s", regName));

			continue;
			}
			 */

			case LAB:
			case LABU:
			case LAH:
			case LAHU:
			case LAW:
				// ex:	LUI       A0, 8024
				//		LHU       A0, 33B0 (A0)
				// all registers always match
				replacement[0] = AsmUtils.getFormattedLine("LUI", "%s, %X", mainRegister, upperAdd);
				replacement[1] = AsmUtils.getFormattedLine(type.lastIns.getName(), "%s, %X (%s)", mainRegister, lowerAdd, mainRegister);
				break;

			case SAB:
			case SAH:
			case SAW:
			case LAF:
			case LAD:
			case SAF:
				// ex:	LUI       AT, 8024
				//		LWC1      F0, 1B74 (AT)
				// always uses AT for the intermediary register
				replacement[0] = AsmUtils.getFormattedLine("LUI", "AT, %X", upperAdd);
				replacement[1] = AsmUtils.getFormattedLine(type.lastIns.getName(), "%s, %X (AT)", mainRegister, lowerAdd);
				break;

			case LTB:
			case LTBU:
			case LTH:
			case LTHU:
			case LTW:
				// ex:	LUI       V1, 8024
				//		ADDU      V1, V1, V0
				//		LBU       V1, 521C (V1)
				// except index, all registers always match
				String intermediary = mainRegister.equals(auxRegister) ? "AT" : mainRegister;
				replacement[0] = AsmUtils.getFormattedLine("LUI", "%s, %X", intermediary, upperAdd);
				replacement[1] = AsmUtils.getFormattedLine("ADDU", "%s, %s, %s", intermediary, intermediary, auxRegister);
				replacement[2] = AsmUtils.getFormattedLine(type.lastIns.getName(), "%s, %X (%s)", mainRegister, lowerAdd, intermediary);
				break;

			case STB:
			case STH:
			case STW:
			case LTF:
			case STF:
				// ex:	LUI       AT 8023
				//		ADDU      AT, AT, V0
				//		SWC1      F2, B210 (AT)
				// always uses AT for the intermediary register
				replacement[0] = AsmUtils.getFormattedLine("LUI", "AT, %X", upperAdd);
				replacement[1] = AsmUtils.getFormattedLine("ADDU", "AT, AT, %s", auxRegister);
				replacement[2] = AsmUtils.getFormattedLine(type.lastIns.getName(), "%s, %X (AT)", mainRegister, lowerAdd);
				break;
		}

		return replacement;
	}

	public static Line[] removeAll(Line[] lines)
	{
		List<Line> linesList = new ArrayList<>(lines.length);
		Collections.addAll(linesList, lines);

		linesList = removeAll(linesList);

		Line[] linesArray = new Line[linesList.size()];
		return linesList.toArray(linesArray);
	}

	private static void assertNoDelaySlot(Line line, boolean delaySlot)
	{
		if (delaySlot)
			throw new AssemblerException(line, "Pseudo-instruction cannot be in delay slot! " + line.str);
	}

	private static final Pattern PushPopOpcodePattern = Pattern.compile("(?i)(PUSH|POP|JPOP)(?:\\[([0-9A-F]+`?)\\])?");
	private static final Matcher PushPopOpcodeMatcher = PushPopOpcodePattern.matcher("");

	private static final Pattern PushPopLinePattern = Pattern.compile("(?i)(PUSH|POP|JPOP)(?:\\[([0-9A-F]+`?)\\])?\\s+(.+)");
	private static final Matcher PushPopLineMatcher = PushPopLinePattern.matcher("");

	/**
	 * Removes all pseudoinstrucions from a list of lines, replacing them with ordinary ASM.
	 */
	public static List<Line> removeAll(List<Line> instructionList)
	{
		Stack<Loop> loopStack = new Stack<>();
		int loopCounter = 0;

		for (Line line : instructionList) {
			line.gather();
			line.str = line.str.replaceAll("[()]", "");
			line.tokenize(",?\\s+");
		}

		Line[] lines = new Line[instructionList.size()];
		instructionList.toArray(lines);

		boolean delaySlot = false;
		List<Line> newinstructions = new LinkedList<>();
		for (int i = 0; i < lines.length; i++) {
			Line line = lines[i];

			PseudoInstruction pi = PseudoInstruction.createFromPseudoASM(lines, i);
			if (pi != null) {
				if (pi.type.pattern.length > 1)
					assertNoDelaySlot(line, delaySlot);
				delaySlot = false;

				String[] replacements = pi.generateASM();
				for (int j = 0; j < replacements.length; j++) {
					// reorder last branch instruction: move into PI asm
					if (pi.useDelaySlot && j == replacements.length - 1) {
						//XXX correct?
						//	String branch = instructionList.get(i + 1);
						//	newinstructions.add(branch);
						newinstructions.add(instructionList.get(i + 1));
						i += 2;
					}

					newinstructions.add(line.createLine(replacements[j]));
				}
			}
			else // (pi == null) --> check helper PIs
			{
				PushPopLineMatcher.reset(line.str);
				if (PushPopLineMatcher.matches()) {
					int stackDataSize = 0x10;
					int stackFrameSize;

					String requestedSpace = PushPopLineMatcher.group(2);
					if (requestedSpace != null) {
						try {
							stackDataSize = DataUtils.parseIntString(requestedSpace);
						}
						catch (InvalidInputException e) {
							throw new InputFileException(line, e);
						}

						if (stackDataSize % 4 != 0)
							throw new InputFileException(line, "Stack data size must be a multiple of 4 bytes: " + requestedSpace);
					}

					String[] args = PushPopLineMatcher.group(3).split("[,?\\s]+");
					int numArgs = args.length;

					// keep stack frame 8-byte aligned
					if (numArgs % 2 == 0)
						stackFrameSize = stackDataSize + 4 * numArgs;
					else
						stackFrameSize = stackDataSize + 4 * (numArgs + 1);

					switch (PushPopLineMatcher.group(1).toUpperCase()) {
						case "PUSH": {
							assertNoDelaySlot(line, delaySlot);
							delaySlot = false;

							if (numArgs < 1)
								throw new InputFileException(line, "Invalid PUSH: empty register list");

							newinstructions.add(line.createLine("ADDIU SP, SP, %4X", -stackFrameSize));

							for (int j = 0; j < numArgs; j++) {
								String regName = line.getString(j + 1);

								if ((regName.startsWith("F") || regName.startsWith("f")) && !regName.equalsIgnoreCase("FP"))
									newinstructions.add(line.createLine("SWC1 %s, %4X (SP)", regName, stackDataSize + 4 * j));
								else
									newinstructions.add(line.createLine("SW %s, %4X (SP)", regName, stackDataSize + 4 * j));
							}
							break;
						}
						case "POP": {
							assertNoDelaySlot(line, delaySlot);
							delaySlot = false;

							if (numArgs < 1)
								throw new InputFileException(line, "Invalid POP: empty register list");

							for (int j = 0; j < numArgs; j++) {
								String regName = line.getString(j + 1);

								if ((regName.startsWith("F") || regName.startsWith("f")) && !regName.equalsIgnoreCase("FP"))
									newinstructions.add(line.createLine("LWC1 %s, %4X (SP)", regName, stackDataSize + 4 * j));
								else
									newinstructions.add(line.createLine("LW %s, %4X (SP)", regName, stackDataSize + 4 * j));
							}

							newinstructions.add(line.createLine("ADDIU SP, SP, %4X", stackFrameSize));
							break;
						}
						case "JPOP": {
							assertNoDelaySlot(line, delaySlot);
							delaySlot = false;

							if (numArgs < 1)
								throw new InputFileException(line, "Invalid JPOP: empty register list");

							for (int j = 0; j < numArgs; j++) {
								String regName = line.getString(j + 1);
								if ((regName.startsWith("F") || regName.startsWith("f")) && !regName.equalsIgnoreCase("FP"))
									newinstructions.add(line.createLine("LWC1 %s, %4X (SP)", regName, stackDataSize + 4 * j));
								else
									newinstructions.add(line.createLine("LW %s, %4X (SP)", regName, stackDataSize + 4 * j));
							}

							newinstructions.add(line.createLine("JR RA"));
							newinstructions.add(line.createLine("ADDIU SP, SP, %4X", stackFrameSize));
							break;
						}
					}
					continue;
				}

				String opcode = line.getString(0);
				int numArgs = line.numTokens() - 1;

				switch (opcode) {
					case "LOOP": {
						assertNoDelaySlot(line, delaySlot);
						delaySlot = false;

						Loop loop = new Loop(line, String.format("autoloop_%X", loopCounter++));
						loopStack.push(loop);

						newinstructions.addAll(loop.getAsm());
						continue;
					}
					case "BREAKLOOP": {
						assertNoDelaySlot(line, delaySlot);
						delaySlot = false;

						if (loopStack.isEmpty())
							throw new InputFileException(line, "Invalid BREAKLOOP: no matching LOOP");
						Loop loop = loopStack.peek();

						newinstructions.add(line.createLine("BEQ R0, R0, ._%s_end", loop.getName()));
						newinstructions.add(line.createLine("NOP"));
						continue;
					}
					case "ENDLOOP": {
						assertNoDelaySlot(line, delaySlot);
						delaySlot = false;

						if (loopStack.isEmpty())
							throw new InputFileException(line, "Invalid ENDLOOP: no matching LOOP");
						Loop loop = loopStack.pop();

						newinstructions.add(line.createLine("BEQ R0, R0, ._%s_next", loop.getName()));
						newinstructions.add(line.createLine("NOP"));
						newinstructions.add(line.createLine("._%s_end", loop.getName()));
						continue;
					}
					case "SUBI": // SUBI	A, B, amount
					{
						assertNoDelaySlot(line, delaySlot);
						delaySlot = false;

						if (numArgs != 3)
							throw new InputFileException(line, "Invalid SUBI instruction");

						String regName1 = line.getString(1);
						String regName2 = line.getString(2);
						String amount = line.getString(3);

						newinstructions.add(line.createLine("ADDI AT, R0, %s", amount));
						newinstructions.add(line.createLine("SUB  %s, %s, AT", regName1, regName2));
						continue;
					}
					case "SUBIU": // SUBIU	A, B, amount
					{
						assertNoDelaySlot(line, delaySlot);
						delaySlot = false;

						if (numArgs != 3)
							throw new InputFileException(line, "Invalid SUBIU instruction");

						String regName1 = line.getString(1);
						String regName2 = line.getString(2);
						String amount = line.getString(3);

						newinstructions.add(line.createLine("ADDIU AT, R0, %s", amount));
						newinstructions.add(line.createLine("SUBU  %s, %s, AT", regName1, regName2));
						continue;
					}

					case "SBI": // SBI	value, A, offset
					{
						assertNoDelaySlot(line, delaySlot);
						delaySlot = false;

						if (numArgs != 3)
							throw new InputFileException(line, "Invalid SBI instruction");

						String amount = line.getString(1);
						String regName1 = line.getString(2);
						String offset = line.getString(3);

						newinstructions.add(line.createLine("ADDIU AT, R0, %s", amount));
						newinstructions.add(line.createLine("SB    AT, %s (%s)", regName1, offset));
						continue;
					}
					case "SHI": // SHI	value, A, offset
					{
						assertNoDelaySlot(line, delaySlot);
						delaySlot = false;

						if (numArgs != 3)
							throw new InputFileException(line, "Invalid SHI instruction");

						String amount = line.getString(1);
						String regName1 = line.getString(2);
						String offset = line.getString(3);

						newinstructions.add(line.createLine("ADDIU AT, R0, %s", amount));
						newinstructions.add(line.createLine("SH    AT, %s (%s)", regName1, offset));
						continue;
					}
					case "SWI": // SWI	value, A, offset
					{
						assertNoDelaySlot(line, delaySlot);
						delaySlot = false;

						if (numArgs != 3)
							throw new InputFileException(line, "Invalid SWI instruction");

						int amount = line.getInt(1);
						String regName1 = line.getString(2);
						String offset = line.getString(3);

						if (amount >= 0 && amount < 0x10000) {
							newinstructions.add(line.createLine("ADDIU AT, R0, %04X", amount));
							newinstructions.add(line.createLine("SW    AT, %s (%s)", regName1, offset));
						}
						else {
							newinstructions.add(line.createLine("LUI   AT, %04X", amount >>> 0x10));
							newinstructions.add(line.createLine("ORI   AT, AT, %04X", amount & 0xFFFF));
							newinstructions.add(line.createLine("SW    AT, %s (%s)", regName1, offset));
						}
						continue;
					}
					case "BLT": // BLT	A, B, offset
					case "BLTL": // BLT	A, B, offset
					{
						assertNoDelaySlot(line, delaySlot);
						delaySlot = true;

						if (numArgs != 3)
							throw new InputFileException(line, "Invalid " + opcode + " instruction");

						String regName1 = line.getString(1);
						String regName2 = line.getString(2);
						String offset = line.getString(3);

						newinstructions.add(line.createLine("SLT AT, %s, %s", regName1, regName2));
						if (opcode.length() == 3)
							newinstructions.add(line.createLine("BNE AT, R0, %s", offset));
						else
							newinstructions.add(line.createLine("BNEL AT, R0, %s", offset));
						continue;
					}
					case "BGT": // BGT	A, B, offset
					case "BGTL": // BGT	A, B, offset
					{
						assertNoDelaySlot(line, delaySlot);
						delaySlot = true;

						if (numArgs != 3)
							throw new InputFileException(line, "Invalid " + opcode + " instruction");

						String regName1 = line.getString(1);
						String regName2 = line.getString(2);
						String offset = line.getString(3);

						newinstructions.add(line.createLine("SLT AT, %s, %s", regName2, regName1));
						if (opcode.length() == 3)
							newinstructions.add(line.createLine("BNE AT, R0, %s", offset));
						else
							newinstructions.add(line.createLine("BNEL AT, R0, %s", offset));
						continue;
					}
					case "BLE": // BLE	A, B, offset
					case "BLEL": // BLE	A, B, offset
					{
						assertNoDelaySlot(line, delaySlot);
						delaySlot = true;

						if (numArgs != 3)
							throw new InputFileException(line, "Invalid " + opcode + " instruction");

						String regName1 = line.getString(1);
						String regName2 = line.getString(2);
						String offset = line.getString(3);

						newinstructions.add(line.createLine("SLT AT, %s, %s", regName2, regName1));
						if (opcode.length() == 3)
							newinstructions.add(line.createLine("BEQ AT, R0, %s", offset));
						else
							newinstructions.add(line.createLine("BEQL AT, R0, %s", offset));
						continue;
					}
					case "BGE": // A, B, offset
					case "BGEL": // A, B, offset
					{
						assertNoDelaySlot(line, delaySlot);
						delaySlot = true;

						if (numArgs != 3)
							throw new InputFileException(line, "Invalid " + opcode + " instruction");

						String regName1 = line.getString(1);
						String regName2 = line.getString(2);
						String offset = line.getString(3);

						newinstructions.add(line.createLine("SLT AT, %s, %s", regName1, regName2));
						if (opcode.length() == 3)
							newinstructions.add(line.createLine("BEQ AT, R0, %s", offset));
						else
							newinstructions.add(line.createLine("BEQL AT, R0, %s", offset));
						continue;
					}
					case "BEQI": // A, imm, offset
					case "BEQIL": // A, imm, offset
					{
						assertNoDelaySlot(line, delaySlot);
						delaySlot = true;

						if (numArgs != 3)
							throw new InputFileException(line, "Invalid " + opcode + " instruction");

						String regName1 = line.getString(1);
						String immediate = line.getString(2);
						String offset = line.getString(3);

						newinstructions.add(line.createLine("ADDIU AT, R0, %s", immediate));
						if (opcode.length() == 4)
							newinstructions.add(line.createLine("BEQ %s, AT, %s", regName1, offset));
						else
							newinstructions.add(line.createLine("BEQL %s, AT, %s", regName1, offset));
						continue;
					}
					case "BNEI": // A, imm, offset
					case "BNEIL": // A, imm, offset
					{
						assertNoDelaySlot(line, delaySlot);
						delaySlot = true;

						if (numArgs != 3)
							throw new InputFileException(line, "Invalid " + opcode + " instruction");

						String regName1 = line.getString(1);
						String immediate = line.getString(2);
						String offset = line.getString(3);

						newinstructions.add(line.createLine("ADDIU AT, R0, %s", immediate));
						if (opcode.length() == 4)
							newinstructions.add(line.createLine("BNE %s, AT, %s", regName1, offset));
						else
							newinstructions.add(line.createLine("BNEL %s, AT, %s", regName1, offset));
						continue;
					}
				} // endswitch

				// not a pseudoinstruction
				delaySlot = hasDelaySlot(opcode);
				newinstructions.add(instructionList.get(i));
				continue;
			}
		}

		if (!loopStack.isEmpty())
			throw new AssemblerException(loopStack.peek().startLine, "Encountered LOOP without ENDLOOP");

		return newinstructions;
	}

	public static ArrayList<String> addAll(List<String> instructionList)
	{
		return addAll(instructionList, null);
	}

	public static ArrayList<String> addAll(List<String> instructionList, BaseDataDecoder decoder)
	{
		TreeSet<PseudoInstruction> piSet = scanAll(instructionList);
		return addAll(instructionList, piSet, decoder);
	}

	public static TreeSet<PseudoInstruction> scanAll(List<String> instructionList)
	{
		String[][] tokens = AsmUtils.tokenize(instructionList);

		List<PatternMatch> matchList = PatternFinder.search(tokens);
		TreeSet<PseudoInstruction> piSet = new TreeSet<>(PseudoInstruction.LINE_ORDER_COMPARATOR);

		for (PatternMatch m : matchList) {
			PseudoInstruction pi = PseudoInstruction.createFromPattern(tokens, m);
			if (pi != null)
				piSet.add(pi);
		}

		return piSet;
	}

	private static ArrayList<String> addAll(List<String> instructionList, TreeSet<PseudoInstruction> piSet, BaseDataDecoder decoder)
	{
		PseudoInstruction currentPI = piSet.pollFirst();
		ArrayList<String> newInstructions = new ArrayList<>(instructionList.size());

		for (int i = 0; i < instructionList.size();) {
			if (currentPI == null || i != currentPI.firstLine) {
				newInstructions.add(instructionList.get(i));
				i++;
				continue;
			}

			int insCount = currentPI.type.pattern.length;
			String replacement = currentPI.generatePseudoASM(decoder);
			newInstructions.add(replacement);

			if (currentPI.useDelaySlot) {
				int branchLine = i + (insCount - 1);
				String branch = instructionList.get(branchLine);
				newInstructions.add(branch);
				newInstructions.add(PseudoInstruction.RESERVED);
				i++;
			}

			i += insCount;
			currentPI = piSet.pollFirst();
		}

		return newInstructions;
	}

	public static void main(String[] args) throws IOException
	{
		// dont use tabs for printing
		AsmUtils.tabWidth = 0;

		File dumpDir = new File("./analysis/funcdump/");
		for (File f : dumpDir.listFiles()) {
			if (f.getName().endsWith(".txt")) {
				System.out.println(f.getName());

				List<String> originals = new LinkedList<>();
				BufferedReader in = new BufferedReader(new FileReader(f));
				String line;
				while ((line = in.readLine()) != null) {
					if (line.isEmpty())
						continue;

					originals.add(line);
				}
				in.close();

				List<String> withPIs = addAll(originals);

				File out = new File("./analysis/funcnew/NEW_" + f.getName());
				FileUtils.touch(out);
				PrintWriter pw = IOUtils.getBufferedPrintWriter(out);
				for (String s : withPIs)
					pw.println(s);
				pw.close();

				List<Line> cycled = IOUtils.readPlainInputFile(out);
				for (Line l : cycled)
					l.tokenize("\\s+");

				cycled = removeAll(cycled);

				//	assert(cycled.size() == originals.size());
				for (int i = 0; i < originals.size(); i++) {
					System.out.printf("%-30s %-30s%n", originals.get(i), cycled.get(i).text);
					//		assert(cycled.get(i).text.equals(originals.get(i)));
				}
			}
		}
		System.out.println("Done. :)");
	}

	public static boolean hasDelaySlot(String op)
	{
		Instruction ins = MIPS.InstructionMap.get(op);
		if (ins == null)
			return false; // something like MOV.S

		switch (ins) {
			case BEQ:
			case BEQL:
			case BNE:
			case BNEL:
			case JALR:
			case BLEZ:
			case BGTZ:
			case BLEZL:
			case BGTZL:
			case BLTZ:
			case BGEZ:
			case BLTZL:
			case BGEZL:
			case BLTZAL:
			case BGEZAL:
			case BLTZALL:
			case BGEZALL:
			case JR:
			case J:
			case JAL:
			case BC1F:
			case BC1T:
			case BC1FL:
			case BC1TL:
				return true;
			default:
				return false;
		}
	}
}

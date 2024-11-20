package asm.pseudoinstruction;

import java.util.LinkedList;
import java.util.List;

import app.input.InputFileException;
import app.input.InvalidInputException;
import app.input.Line;
import asm.MIPS;
import game.shared.DataUtils;

public class Loop
{
	public final Line startLine;

	private final String name;
	private final String indexReg;

	private final boolean forLoop;

	private final CompareMode mode;

	private final Variable start;
	private final Variable end;
	private final Variable step;

	private static enum CompareMode
	{
		EQL, NEQ, GT, GTE, LT, LTE;
	}

	private static class Variable
	{
		public boolean useRegister;
		public int constAmount;
		public String regName;

		public Variable(Line sourceLine, String s)
		{
			useRegister = (MIPS.isCpuReg(s) || MIPS.isFpuReg(s));

			if (useRegister)
				regName = s;
			else {
				try {
					constAmount = DataUtils.parseIntString(s);
				}
				catch (InvalidInputException e) {
					throw new InputFileException(sourceLine, "Invalid immediate value: " + s);
				}

				if (Math.abs(constAmount) > 0x7FFF)
					throw new InputFileException(sourceLine, "Immediate value too large: " + s);
			}
		}

		@Override
		public String toString()
		{
			if (useRegister)
				return regName;
			else
				return String.format("%X", constAmount);
		}
	}

	private static String getADD(String destReg, Variable var, String srcReg)
	{
		if (var.useRegister)
			return String.format("DADDU %s, %s, %s", destReg, srcReg, var.regName);
		else
			return String.format("ADDIU %s, %s, %X", destReg, srcReg, var.constAmount);
	}

	private static String getSLT(String destReg, String reg, Variable var)
	{
		if (var.useRegister)
			return String.format("SLT  %s, %s, %s", destReg, reg, var.regName);
		else
			return String.format("SLTI %s, %s, %X", destReg, reg, var.constAmount);
	}

	public Loop(Line line, String name)
	{
		startLine = line;
		int numTokens = line.numTokens();

		// while  : LOOP S5 < S6
		// for (5): LOOP S5 = S6 S2
		// for (6): LOOP S5 = S6 T0 S2

		if (numTokens < 4)
			throw new InputFileException(line, "Invalid LOOP");

		this.name = name;
		indexReg = line.getString(1);

		if (!MIPS.isCpuReg(indexReg) && !MIPS.isFpuReg(indexReg))
			throw new InputFileException(line, "LOOP index is not valid register: " + indexReg);

		String operator = line.getString(2);
		forLoop = operator.equals("=");

		if (forLoop) {
			switch (numTokens) {
				case 5:
					start = new Variable(line, line.getString(3));
					step = new Variable(line, "1");
					end = new Variable(line, line.getString(4));
					break;
				case 6:
					start = new Variable(line, line.getString(3));
					step = new Variable(line, line.getString(4));
					;
					end = new Variable(line, line.getString(5));
					break;
				default:
					throw new InputFileException(line, "Invalid FOR LOOP");
			}

			mode = null;
		}
		else {
			if (numTokens > 4)
				throw new InputFileException(line, "Invalid LOOP");

			switch (operator) {
				case "==":
					mode = CompareMode.EQL;
					break;
				case "!=":
					mode = CompareMode.NEQ;
					break;
				case ">":
					mode = CompareMode.GT;
					break;
				case ">=":
					mode = CompareMode.GTE;
					break;
				case "<":
					mode = CompareMode.LT;
					break;
				case "<=":
					mode = CompareMode.LTE;
					break;
				default:
					throw new InputFileException(line, "Invalid LOOP comparator: " + operator);
			}

			start = null;
			step = null;
			end = new Variable(line, line.getString(3));
		}
	}

	// doesnt check for errors, just gives length oof valid LOOP statements, or 16 if invalid
	public static int getLength(Line line)
	{
		if (line.numTokens() != 4)
			return 16; // invalid line

		switch (line.getString(2)) {
			case "=":
				return 7; // for loop
			case ">":
			case "<=":
				return 4;
			case "==":
			case "!=":
			case "<":
			case ">=":
				return 3;
			default:
				return 16; // invalid
		}
	}

	public static int getLength(String[] tokens)
	{
		if (tokens.length != 4)
			return 16; // invalid line

		switch (tokens[2]) {
			case "=":
				return 7; // for loop
			case ">":
			case "<=":
				return 4;
			case "==":
			case "!=":
			case "<":
			case ">=":
				return 3;
			default:
				return 16; // invalid
		}
	}

	public String getName()
	{
		return name;
	}

	public List<Line> getAsm()
	{
		List<Line> lines = new LinkedList<>();
		if (forLoop) {
			// initialization
			lines.add(startLine.createLine(getADD(indexReg, start, "R0")));
			lines.add(startLine.createLine("BEQ R0, R0, ._%s_check", name));
			lines.add(startLine.createLine("NOP"));

			// increment
			lines.add(startLine.createLine("._%s_next", name));
			lines.add(startLine.createLine(getADD(indexReg, step, indexReg)));

			// condition
			lines.add(startLine.createLine("._%s_check", name));

			lines.add(startLine.createLine(getSLT("AT", indexReg, end)));
			lines.add(startLine.createLine("BEQ AT, R0, ._%s_end", name));
			lines.add(startLine.createLine("NOP"));
		}
		else {
			switch (mode) {
				case EQL:
					lines.add(startLine.createLine(getADD("AT", end, "R0")));
					lines.add(startLine.createLine("BEQ AT, %s, ._%s_end", indexReg, name));
					lines.add(startLine.createLine("NOP"));
					break;
				case NEQ:
					lines.add(startLine.createLine(getADD("AT", end, "R0")));
					lines.add(startLine.createLine("BNE AT, %s, ._%s_end", indexReg, name));
					lines.add(startLine.createLine("NOP"));
					break;
				case GT: //  index > var  --->  jump to END when index <= var
					lines.add(startLine.createLine(getADD("AT", end, "R0")));
					lines.add(startLine.createLine("SLT AT, AT, %s", indexReg));
					lines.add(startLine.createLine("BEQ AT, R0, ._%s_end", indexReg, name));
					lines.add(startLine.createLine("NOP"));
					break;
				case GTE: // index >= var  --->  jump to END when index < var
					lines.add(startLine.createLine(getSLT("AT", indexReg, end)));
					lines.add(startLine.createLine("BNE AT, R0, ._%s_end", indexReg, name));
					lines.add(startLine.createLine("NOP"));
					break;
				case LT: // index < var  --->  jump to END when index >= var
					lines.add(startLine.createLine(getSLT("AT", indexReg, end)));
					lines.add(startLine.createLine("BEQ AT, R0, ._%s_end", indexReg, name));
					lines.add(startLine.createLine("NOP"));
					break;
				case LTE: // index <= var  --->  jump to END when index > var
					lines.add(startLine.createLine(getADD("AT", end, "R0")));
					lines.add(startLine.createLine("SLT AT, AT, %s", indexReg));
					lines.add(startLine.createLine("BNE AT, R0, ._%s_end", indexReg, name));
					lines.add(startLine.createLine("NOP"));
					break;
			}
		}

		return lines;
	}
}

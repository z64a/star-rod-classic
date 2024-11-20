package asm;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import app.input.DummySource;
import app.input.InputFileException;
import app.input.Line;
import asm.pseudoinstruction.PseudoInstruction;
import patcher.RomPatcher;

public abstract class AsmUtils
{
	public static final String[][] tokenize(List<String> asmList)
	{
		String[][] tokens = new String[asmList.size()][];
		for (int i = 0; i < asmList.size(); i++)
			tokens[i] = asmList.get(i).replaceAll("[()]", "").split(",?\\s+");
		return tokens;
	}

	public static int tabWidth = 4;

	public static String getFormattedLine(String insName, String fmt, Object ... args)
	{
		if (tabWidth > 0) {
			int numTabs = 3 - insName.length() / tabWidth; // ((insName.length() + (tabWidth - 1)) & -tabWidth)/tabWidth;
			StringBuilder sb = new StringBuilder(insName);
			for (int i = 0; i < numTabs; i++)
				sb.append("\t");
			sb.append(String.format(fmt, args));
			return sb.toString();
		}
		else {
			return String.format("%-9s ", insName) + String.format(fmt, args);
		}
	}

	/*
	 * Finds all occurances of some sequence of instructions.
	 */
	public static final List<Integer> findSequence(String[][] tokens, String ... sequence)
	{
		int seqPos = 0;
		int seqEnd = sequence.length;

		List<Integer> matches = new ArrayList<>();

		for (int i = 0; i < tokens.length; i++) {
			if (tokens[i][0].equals(sequence[seqPos]))
				seqPos++;
			else
				seqPos = 0;

			if (seqPos == seqEnd) {
				matches.add(i - seqEnd + 1);
				seqPos = 0;
			}
		}

		return matches;
	}

	/**
	 * Forms an address from a LUI-ADDIU or LUI-LW instruction pair
	 * @param upper
	 * @param lower
	 * @return
	 */
	public static int makeAddress(int upper, int lower)
	{
		//	lower = (int)(short)lower;
		//	int dest = upper + lower;

		int dest = (upper << 16 | lower);
		if ((lower >>> 15) == 1)
			dest -= 0x10000;

		return dest;
	}

	public static void assembleAndWrite(RomPatcher rp, List<Line> lines)
	{
		assembleAndWrite(false, rp, lines);
	}

	public static void assembleAndWrite(boolean usePIs, RomPatcher rp, List<Line> lines)
	{
		for (Line line : lines)
			line.tokenize();

		if (usePIs)
			lines = PseudoInstruction.removeAll(lines);

		for (Line line : MIPS.assemble(lines)) {
			try {
				rp.writeInt((int) Long.parseLong(line.str, 16));
			}
			catch (NumberFormatException e) {
				throw new InputFileException(line, "Could not parse integer: %s", line.str);
			}
		}
	}

	public static void assembleAndWrite(String sourceName, int offset, RomPatcher rp, String ... strings)
	{
		rp.seek(sourceName, offset);
		assembleAndWrite(false, sourceName, rp, strings);
	}

	public static void assembleAndWrite(String sourceName, RomPatcher rp, String ... strings)
	{
		assembleAndWrite(false, sourceName, rp, strings);
	}

	public static void assembleAndWrite(boolean usePIs, String sourceName, RomPatcher rp, String ... strings)
	{
		DummySource src = new DummySource(sourceName);

		int i = 1;
		List<Line> lines = new LinkedList<>();
		for (String s : strings)
			lines.add(new Line(src, i++, s));

		assembleAndWrite(usePIs, rp, lines);
	}

	public static void assembleAndWrite(String sourceName, RomPatcher rp, List<String> strings)
	{
		assembleAndWrite(false, sourceName, rp, strings);
	}

	public static void assembleAndWrite(boolean usePIs, String sourceName, RomPatcher rp, List<String> strings)
	{
		DummySource src = new DummySource(sourceName);

		int i = 1;
		List<Line> lines = new LinkedList<>();
		for (String s : strings)
			lines.add(new Line(src, i++, s));

		assembleAndWrite(usePIs, rp, lines);
	}

	public static void assembleAndWrite(String sourceName, RomPatcher rp, String s)
	{
		assembleAndWrite(false, sourceName, rp, s);
	}

	public static void assembleAndWrite(boolean usePIs, String sourceName, RomPatcher rp, String s)
	{
		DummySource src = new DummySource(sourceName);
		List<Line> lines = new LinkedList<>();
		lines.add(new Line(src, 1, s));
		assembleAndWrite(usePIs, rp, lines);
	}
}

package asm.pseudoinstruction;

import asm.MIPS.Instruction;

public final class PatternMatch
{
	// type refers to the instruction sequence
	public static enum PIPattern
	{
		CLR (1), // clear
		CPY (1), // copy
		LIH (1), // load immediate to register (half)
		LIHF (2), // load immediate to float register (half)
		LIW (2), // load immediate to register (word)
		LIWF (3), // load immediate to float register (word)
		LSA (2), // load/store address
		LST (3); // load/store table

		public final int length;

		private PIPattern(int length)
		{
			this.length = length;
		}
	}

	public final PIPattern pattern;
	public final int line;
	public final boolean delaySlot;
	public final boolean halfSize;
	public final Instruction lastIns;

	public PatternMatch(PIPattern pattern, Instruction lastIns, int lastLine, boolean delaySlot)
	{
		this.pattern = pattern;
		this.lastIns = lastIns;
		this.delaySlot = delaySlot;
		this.halfSize = (pattern == PIPattern.LIH) || (pattern == PIPattern.LIHF);

		int diff = (lastLine - pattern.length);
		this.line = delaySlot ? diff : diff + 1;
	}

	@Override
	public String toString()
	{
		if (delaySlot)
			return pattern + ", line " + line + " (delayed)";
		else
			return pattern + ", line " + line;
	}
}

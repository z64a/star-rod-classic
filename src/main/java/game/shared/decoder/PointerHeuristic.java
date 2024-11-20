package game.shared.decoder;

import game.shared.struct.StructType;

public class PointerHeuristic
{
	public final int start;
	public final int end;
	public final int startPadding;
	public final int endPadding;

	public int structOffset = 0;
	public StructType structType;

	public PointerHeuristic(int startOffset, int endOffset, int startPadding, int endPadding)
	{
		this.start = startOffset;
		this.end = endOffset;
		this.startPadding = startPadding;
		this.endPadding = endPadding;
	}

	public int getOffset()
	{
		return start + structOffset;
	}

	public int getLength()
	{
		return end - start;
	}
}

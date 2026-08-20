package game.effects;

import java.nio.ByteBuffer;

public class EffectTableEntry
{
	public static final int SIZE = 0x18;

	public final int initAddr;
	public final int codeStart;
	public final int codeEnd;
	public final int codeDestAddr;
	public final int graphicsStart;
	public final int graphicsEnd;

	public EffectTableEntry(ByteBuffer fileBuffer)
	{
		initAddr = fileBuffer.getInt();

		codeStart = fileBuffer.getInt();
		codeEnd = fileBuffer.getInt();

		codeDestAddr = fileBuffer.getInt();

		graphicsStart = fileBuffer.getInt();
		graphicsEnd = fileBuffer.getInt();
	}

	public boolean hasCode()
	{
		return initAddr != 0 && codeEnd > codeStart;
	}

	public boolean hasGraphics()
	{
		return graphicsStart > 0 && graphicsEnd > graphicsStart;
	}
}

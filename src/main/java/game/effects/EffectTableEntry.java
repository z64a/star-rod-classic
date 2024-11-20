package game.effects;

import java.nio.ByteBuffer;

public class EffectTableEntry
{
	public final int initAddr;
	public final int codeStart;
	public final int codeEnd;
	public final int codeDestAddr;
	public final int graphicsStart;
	public final int graphicsEnd;

	public EffectTableEntry(ByteBuffer fileBuffer)
	{
		int addr; //XXX disassembler only handles 80-memory. oops.

		addr = fileBuffer.getInt();
		initAddr = addr; //(addr == 0) ? 0 : (addr & 0x0FFFFFF) | 0x80000000;

		codeStart = fileBuffer.getInt();
		codeEnd = fileBuffer.getInt();

		addr = fileBuffer.getInt();
		codeDestAddr = addr; // (addr == 0) ? 0 : (addr & 0x0FFFFFF) | 0x80000000;

		graphicsStart = fileBuffer.getInt();
		graphicsEnd = fileBuffer.getInt();
	}
}

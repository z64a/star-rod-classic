package game.world.action;

import java.nio.ByteBuffer;

public class ActionTableEntry
{
	public final int initAddr;
	public final int romStart;
	public final int romEnd;
	public final int flags;

	public ActionTableEntry(ByteBuffer fileBuffer)
	{
		initAddr = fileBuffer.getInt();
		romStart = fileBuffer.getInt();
		romEnd = fileBuffer.getInt();
		flags = fileBuffer.getInt();
	}
}

package patcher;

import java.io.IOException;
import java.io.RandomAccessFile;

import util.Logger;
import util.Priority;

public abstract class VariablePatcher
{
	// target instruction is at 0x00170DE0
	// we want to make the substitution: 24040001 -> 2404000X
	public static void setPoisonDamage(RandomAccessFile raf, int val) throws IOException
	{
		val &= 0xFF;
		raf.seek(0x00170DE0);
		raf.writeInt(0x24040000 | val);
		Logger.log("Poison damage changed to " + val + ".", Priority.IMPORTANT);
	}

	// target instruction is at 0x001A05A8
	// we want to make the substitution: 24040001 -> 2404000X
	public static void setSpikeDamage(RandomAccessFile raf, int val) throws IOException
	{
		val &= 0xFF;
		raf.seek(0x001A05A8);
		raf.writeInt(0x24040000 | val);
		Logger.log("Spike damage changed to " + val + ".", Priority.IMPORTANT);
	}

	// target instruction is at 0x001A04D0
	// we want to make the substitution: 24040001 -> 2404000X
	public static void setFireDamage(RandomAccessFile raf, int val) throws IOException
	{
		val &= 0xFF;
		raf.seek(0x001A04D0);
		raf.writeInt(0x24040000 | val);
		Logger.log("Fire damage changed to " + val + ".", Priority.IMPORTANT);
	}
}

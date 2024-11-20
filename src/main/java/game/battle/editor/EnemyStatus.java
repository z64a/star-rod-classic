package game.battle.editor;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class EnemyStatus
{
	private static enum StatusEffect
	{
		// @formatter:off
		Default		(0x2, 0x1F),
		Sleep		(0x6, 0x20),
		Poison		(0x9, 0x25),
		Frozen		(0x7, 0x22),
		Dizzy		(0x4, 0x24),
		Fear		(0x3, 0x23),
		Static		(0xB, 0x21),
		Paralyze	(0x5, 0x26),
		Shrink		(0xA, 0x27),
		Stop		(0x8, 0x29);
		// @formatter:on

		public final int chanceKey;
		public final int durationKey;

		private StatusEffect(int chanceKey, int durationKey)
		{
			this.chanceKey = chanceKey;
			this.durationKey = durationKey;
		}
	}

	// skipped:
	/*
	Normal			= 1
	Stone			= C
	Daze			= D		% partners only, turn mod is N/A
	End				= 0
	 */

	public static List<EnemyStatus> readList(ByteBuffer fileBuffer, int offset)
	{
		StatusEffect[] values = StatusEffect.values();

		List<EnemyStatus> list = new ArrayList<>();
		int key;

		while ((key = fileBuffer.getInt()) != 0) {
			for (StatusEffect eff : values) {
				//TODO
			}
		}

		return list;
	}

	/*
	while((key = fileBuffer.getInt()) != 0)
	{
		if(DataConstants.StatusType.has(key))
		{
			String keyName = DataConstants.StatusType.getConstantString(key);
			if(keyName.length() > 17)
				pw.printf("%-23s ", keyName);
			else
				pw.printf("%-17s ", keyName);
		}
		else
		{
			Logger.logWarning("Unknown status type found! " + key);
			pw.printf("%08X ", key);
		}
		pw.printf("%08X\n", fileBuffer.getInt());
	}
	pw.println(DataConstants.StatusType.getConstantString(0)); // .End
	 */
}

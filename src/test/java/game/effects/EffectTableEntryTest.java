package game.effects;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;

import org.junit.jupiter.api.Test;

public class EffectTableEntryTest
{
	@Test
	public void identifiesPopulatedCodeAndGraphicsRanges()
	{
		ByteBuffer buffer = ByteBuffer.allocate(EffectTableEntry.SIZE);
		buffer.putInt(0xE0002000);
		buffer.putInt(0x1000);
		buffer.putInt(0x1800);
		buffer.putInt(0xE0002000);
		buffer.putInt(0x2000);
		buffer.putInt(0x2800);
		buffer.flip();

		EffectTableEntry entry = new EffectTableEntry(buffer);
		assertTrue(entry.hasCode());
		assertTrue(entry.hasGraphics());
	}

	@Test
	public void rejectsEmptyRanges()
	{
		EffectTableEntry entry = new EffectTableEntry(ByteBuffer.allocate(EffectTableEntry.SIZE));
		assertFalse(entry.hasCode());
		assertFalse(entry.hasGraphics());
	}
}

package game.effects;

import java.io.IOException;
import java.nio.ByteBuffer;

import app.Environment;
import game.ROM.EOffset;
import game.shared.ProjectDatabase;
import game.shared.decoder.DumpMetadata;
import util.Logger;

public class EffectEditor
{
	public static final int SIZE_LIMIT = 0x2000;

	public static void main(String[] args) throws IOException
	{
		Environment.initialize();
		dumpEffects(Environment.getBaseRomBuffer());
		Environment.exit();
	}

	public static void dumpEffects(ByteBuffer fileBuffer) throws IOException
	{
		int effectTableBase = ProjectDatabase.rom.getOffset(EOffset.EFFECT_TABLE);

		for (int i = 0; i < 135; i++) {
			String baseName = null;
			int type = i << 16;

			for (int j = -1; j < 5; j++) {
				baseName = ProjectDatabase.EffectType.get(type | (j & 0xFFFF));
				if (baseName != null)
					break;
			}

			if (baseName != null)
				baseName = String.format("%02X %s", i,
					baseName.replaceAll(":", "_").replaceAll("\\W", ""));
			else
				baseName = String.format("%02X", i);

			fileBuffer.position(effectTableBase + i * 0x18);
			EffectTableEntry entry = new EffectTableEntry(fileBuffer);

			if (entry.initAddr == 0) {
				Logger.logf("Data missing for effect %X, skipping.", i);
				continue;
			}

			Logger.logf("Generating source files for effect: %s (baseAddr = %08X)", baseName, entry.codeDestAddr);

			DumpMetadata codeMdata = new DumpMetadata(baseName.replaceAll(":", "_"),
				entry.codeStart, entry.codeEnd,
				entry.codeDestAddr, entry.codeDestAddr + SIZE_LIMIT);

			new EffectDecoder(fileBuffer, codeMdata, entry);

			if (entry.graphicsStart > 0 && (entry.graphicsEnd - entry.graphicsStart) != 0) {
				// actually loaded to the heap, but internally all pointers use 0 -- hence segment 0
				DumpMetadata gfxMdata = new DumpMetadata(baseName,
					entry.graphicsStart, entry.graphicsEnd, 0x09000000,
					0x09000000 + (entry.graphicsEnd - entry.graphicsStart));

				new EffectGfxDecoder(fileBuffer, gfxMdata, entry);
			}
		}
	}
}

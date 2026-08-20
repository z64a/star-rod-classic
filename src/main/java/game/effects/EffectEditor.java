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
	public static final String SCRIPT_EXTENSION = ".escr";
	public static final String INDEX_EXTENSION = ".eidx";

	public static final int EFFECT_COUNT = 135;
	public static final int CODE_SIZE_LIMIT = 0x1000;
	public static final int CODE_ADDRESS_SPACE_SIZE = 0x2000;
	public static final int GRAPHICS_BASE = 0x09000000;
	public static final int GRAPHICS_ADDRESS_LIMIT = 0x0A000000;

	/**
	 * Retained for compatibility with tools which used the old name for the effect virtual address-space size.
	 */
	public static final int SIZE_LIMIT = CODE_ADDRESS_SPACE_SIZE;

	public static void main(String[] args) throws IOException
	{
		Environment.initialize();
		dumpEffects(Environment.getBaseRomBuffer());
		Environment.exit();
	}

	public static void dumpEffects(ByteBuffer fileBuffer) throws IOException
	{
		int effectTableBase = ProjectDatabase.rom.getOffset(EOffset.EFFECT_TABLE);

		for (int i = 0; i < EFFECT_COUNT; i++) {
			String baseName = getSourceName(i);

			fileBuffer.position(effectTableBase + i * EffectTableEntry.SIZE);
			EffectTableEntry entry = new EffectTableEntry(fileBuffer);

			if (entry.initAddr == 0) {
				Logger.logf("Data missing for effect %X, skipping.", i);
				continue;
			}

			Logger.logf("Generating source files for effect: %s (baseAddr = %08X)", baseName, entry.codeDestAddr);

			DumpMetadata codeMdata = new DumpMetadata(baseName.replaceAll(":", "_"),
				entry.codeStart, entry.codeEnd,
				entry.codeDestAddr, entry.codeDestAddr + CODE_ADDRESS_SPACE_SIZE);

			new EffectDecoder(fileBuffer, codeMdata, entry);

			if (entry.hasGraphics()) {
				// actually loaded to the heap, but internally all pointers use 0 -- hence segment 0
				DumpMetadata gfxMdata = new DumpMetadata(baseName,
					entry.graphicsStart, entry.graphicsEnd, GRAPHICS_BASE,
					GRAPHICS_BASE + (entry.graphicsEnd - entry.graphicsStart));

				new EffectGfxDecoder(fileBuffer, gfxMdata, entry);
			}
		}
	}

	public static String getSourceName(int id)
	{
		String baseName = null;
		int type = id << 16;

		for (int subtype = -1; subtype < 5; subtype++) {
			baseName = ProjectDatabase.EffectType.get(type | (subtype & 0xFFFF));
			if (baseName != null)
				break;
		}

		if (baseName != null)
			return String.format("%02X %s", id, baseName.replaceAll(":", "_").replaceAll("\\W", ""));

		return String.format("%02X", id);
	}
}

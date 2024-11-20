package game.battle.starpowers;

import static app.Directories.DUMP_STARS_RAW;
import static app.Directories.DUMP_STARS_SRC;
import static game.battle.BattleConstants.STARS_BASE;
import static game.battle.BattleConstants.STARS_RAM_LIMIT;
import static game.shared.StructTypes.UseScriptT;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import game.battle.BaseBattleDecoder;
import game.shared.decoder.Pointer.Origin;

public class StarPowerDecoder extends BaseBattleDecoder
{
	public StarPowerDecoder(ByteBuffer fileBuffer, String scriptName, int start, int end, int ptrMain) throws IOException
	{
		super();

		sourceName = scriptName;

		int startAddress = STARS_BASE;
		int endAddress = (end - start) + STARS_BASE;
		setAddressRange(startAddress, endAddress, STARS_RAM_LIMIT);
		setOffsetRange(start, end);

		findLocalPointers(fileBuffer);
		enqueueAsRoot(ptrMain, UseScriptT, Origin.DECODED).forceName("Script_UsePower");

		super.decode(fileBuffer);

		File rawFile = new File(DUMP_STARS_RAW + sourceName + ".bin");
		File scriptFile = new File(DUMP_STARS_SRC + sourceName + ".bscr");
		File indexFile = new File(DUMP_STARS_SRC + sourceName + ".bidx");

		printScriptFile(scriptFile, fileBuffer);
		printIndexFile(indexFile);
		writeRawFile(rawFile, fileBuffer);
	}
}

package game.battle.minigame;

import static app.Directories.DUMP_MINIGAME_RAW;
import static app.Directories.DUMP_MINIGAME_SRC;
import static game.battle.BattleConstants.ACTION_COMMANDS_BASE;
import static game.battle.BattleConstants.ACTION_COMMANDS_RAM_LIMIT;
import static game.shared.StructTypes.*;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import game.battle.BaseBattleDecoder;
import game.shared.decoder.Pointer.Origin;
import game.shared.decoder.PointerHeuristic;
import game.shared.struct.miniscript.HudElementScript;

public class MinigameDecoder extends BaseBattleDecoder
{
	public MinigameDecoder(ByteBuffer fileBuffer, String scriptName, int start, int end) throws IOException
	{
		super();

		sourceName = scriptName;

		int startAddress = ACTION_COMMANDS_BASE;
		int endAddress = (end - start) + ACTION_COMMANDS_BASE;
		setAddressRange(startAddress, endAddress, ACTION_COMMANDS_RAM_LIMIT);
		setOffsetRange(start, end);

		findLocalPointers(fileBuffer);
		enqueueAsRoot(ACTION_COMMANDS_BASE, FunctionT, Origin.DECODED);

		super.decode(fileBuffer);

		File rawFile = new File(DUMP_MINIGAME_RAW + sourceName + ".bin");
		File scriptFile = new File(DUMP_MINIGAME_SRC + sourceName + ".bscr");
		File indexFile = new File(DUMP_MINIGAME_SRC + sourceName + ".bidx");

		printScriptFile(scriptFile, fileBuffer);
		printIndexFile(indexFile);
		writeRawFile(rawFile, fileBuffer);
	}

	@Override
	protected int guessType(PointerHeuristic h, ByteBuffer fileBuffer)
	{
		int matches = super.guessType(h, fileBuffer);

		if (matches != 1 && HudElementScript.isHudElement(h, fileBuffer)) {
			h.structType = HudElementScriptT;
			matches++;
		}

		if (matches != 1)
			h.structType = UnknownT;

		return matches;
	}
}

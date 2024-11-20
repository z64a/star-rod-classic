package game.battle.minigame;

import static app.Directories.*;
import static game.battle.BattleConstants.ACTION_COMMANDS_RAM_LIMIT;

import java.io.File;
import java.io.IOException;

import app.input.FileSource;
import app.input.IOUtils;
import game.battle.formations.BattleSectionEncoder;
import patcher.IGlobalDatabase;

public class MinigameEncoder extends BattleSectionEncoder
{
	public MinigameEncoder(IGlobalDatabase db) throws IOException
	{
		super(db);
		setAddressLimit(ACTION_COMMANDS_RAM_LIMIT);
	}

	public void encode(String moveName) throws IOException
	{
		File patchFile = new File(MOD_MINIGAME_PATCH + moveName + ".bpat");
		File indexFile = new File(DUMP_MINIGAME_SRC + moveName + ".bidx");
		File rawFile = new File(DUMP_MINIGAME_RAW + moveName + ".bin");
		File outFile = new File(MOD_MINIGAME_TEMP + moveName + ".bin");
		File outIndexFile = new File(MOD_MINIGAME_TEMP + moveName + ".bidx");

		fileBuffer = IOUtils.getDirectBuffer(rawFile);
		setSource(new FileSource(patchFile));

		readIndexFile(indexFile); // reads Start/End/Padding/Missing, uses Start to find base
		readPatchFile(patchFile); // read all patches into patchedStructures
		digest();
		buildOverlay(outFile, outIndexFile);
	}
}

package game.battle.moves;

import static app.Directories.*;
import static game.battle.BattleConstants.MOVE_RAM_LIMIT;

import java.io.File;
import java.io.IOException;

import app.input.FileSource;
import app.input.IOUtils;
import game.battle.formations.BattleSectionEncoder;
import patcher.IGlobalDatabase;

public class MoveEncoder extends BattleSectionEncoder
{
	public MoveEncoder(IGlobalDatabase db) throws IOException
	{
		super(db);
		setAddressLimit(MOVE_RAM_LIMIT);
	}

	public void encode(String moveName) throws IOException
	{
		File patchFile = new File(MOD_MOVE_PATCH + moveName + ".bpat");
		File indexFile = new File(DUMP_MOVE_SRC + moveName + ".bidx");
		File rawFile = new File(DUMP_MOVE_RAW + moveName + ".bin");
		File outFile = new File(MOD_MOVE_TEMP + moveName + ".bin");
		File outIndexFile = new File(MOD_MOVE_TEMP + moveName + ".bidx");

		fileBuffer = IOUtils.getDirectBuffer(rawFile);
		setSource(new FileSource(patchFile));

		readIndexFile(indexFile); // reads Start/End/Padding/Missing, uses Start to find base
		readPatchFile(patchFile); // read all patches into patchedStructures
		digest();
		buildOverlay(outFile, outIndexFile);
	}
}

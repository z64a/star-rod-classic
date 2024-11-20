package game.battle.items;

import static app.Directories.*;
import static game.battle.BattleConstants.ITEM_RAM_LIMIT;

import java.io.File;
import java.io.IOException;

import app.input.FileSource;
import app.input.IOUtils;
import game.battle.formations.BattleSectionEncoder;
import patcher.IGlobalDatabase;

public class ItemEncoder extends BattleSectionEncoder
{
	public ItemEncoder(IGlobalDatabase db) throws IOException
	{
		super(db);
		setAddressLimit(ITEM_RAM_LIMIT);
	}

	public void encode(String moveName) throws IOException
	{
		File patchFile = new File(MOD_ITEM_PATCH + moveName + ".bpat");
		File indexFile = new File(DUMP_ITEM_SRC + moveName + ".bidx");
		File rawFile = new File(DUMP_ITEM_RAW + moveName + ".bin");
		File outFile = new File(MOD_ITEM_TEMP + moveName + ".bin");
		File outIndexFile = new File(MOD_ITEM_TEMP + moveName + ".bidx");

		fileBuffer = IOUtils.getDirectBuffer(rawFile);
		setSource(new FileSource(patchFile));

		readIndexFile(indexFile); // reads Start/End/Padding/Missing, uses Start to find base
		readPatchFile(patchFile); // read all patches into patchedStructures

		digest();
		buildOverlay(outFile, outIndexFile);
	}
}

package game.battle.formations;

import static app.Directories.*;
import static game.battle.BattleConstants.BATTLE_RAM_LIMIT;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import app.input.FileSource;
import app.input.IOUtils;
import app.input.Line;
import game.ROM.LibScope;
import game.battle.BattlePatcher.BattleConfig;
import game.shared.StructTypes;
import game.shared.encoder.BaseDataEncoder;
import patcher.IGlobalDatabase;
import util.Logger;

public class BattleSectionEncoder extends BaseDataEncoder
{
	public BattleSectionEncoder(IGlobalDatabase db)
	{
		super(StructTypes.battleTypes, LibScope.Battle, db, MOD_FORMA_IMPORT, true);
		setAddressLimit(BATTLE_RAM_LIMIT);
	}

	public void encode(BattleConfig cfg) throws IOException
	{
		File patchFile = new File(MOD_FORMA_PATCH + cfg.name + ".bpat");
		if (!patchFile.exists()) {
			Logger.logDetail("No patch file found for section " + cfg.name);
			return;
		}

		setSource(new FileSource(patchFile));

		File indexFile = new File(DUMP_FORMA_SRC + cfg.name + ".bidx");
		File rawFile = new File(DUMP_FORMA_RAW + cfg.name + ".bin");
		File outFile = new File(MOD_FORMA_TEMP + cfg.name + ".bin");
		File outIndexFile = new File(MOD_FORMA_TEMP + cfg.name + ".bidx");

		if (!rawFile.exists()) {
			fileBuffer = ByteBuffer.allocateDirect(0);
			setOverlayMemoryLocation(cfg.startAddress, BATTLE_RAM_LIMIT - cfg.startAddress);
		}
		else {
			fileBuffer = IOUtils.getDirectBuffer(rawFile);
			readIndexFile(indexFile); // reads Start/End/Padding/Missing, uses Start to find base
		}

		readPatchFile(patchFile); // read all patches into patchedStructures
		digest();
		buildOverlay(outFile, outIndexFile);
	}

	@Override
	protected void replaceExpression(Line line, String[] args, List<String> newTokenList)
	{}
}

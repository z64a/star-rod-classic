package game.battle.partners;

import static app.Directories.*;
import static game.battle.BattleConstants.*;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import app.input.FileSource;
import app.input.IOUtils;
import game.battle.formations.BattleSectionEncoder;
import patcher.IGlobalDatabase;

public class PartnerActorEncoder extends BattleSectionEncoder
{
	public PartnerActorEncoder(IGlobalDatabase db) throws IOException
	{
		super(db);
		setAddressLimit(ALLY_RAM_LIMIT);
	}

	public void encode(String partnerName) throws IOException
	{
		File patchFile = new File(MOD_ALLY_PATCH + partnerName + ".bpat");
		File indexFile = new File(DUMP_ALLY_SRC + partnerName + ".bidx");
		File rawFile = new File(DUMP_ALLY_RAW + partnerName + ".bin");
		File outFile = new File(MOD_ALLY_TEMP + partnerName + ".bin");
		File outIndexFile = new File(MOD_ALLY_TEMP + partnerName + ".bidx");

		setSource(new FileSource(patchFile));

		if (!rawFile.exists()) {
			fileBuffer = ByteBuffer.allocateDirect(0);
			setOverlayMemoryLocation(ALLY_BASE, ALLY_SIZE_LIMIT);
		}
		else {
			fileBuffer = IOUtils.getDirectBuffer(rawFile);
			readIndexFile(indexFile); // reads Start/End/Padding/Missing, uses Start to find base
		}

		readPatchFile(patchFile); // read all patches into patchedStructures
		digest();
		buildOverlay(outFile, outIndexFile);
	}
}

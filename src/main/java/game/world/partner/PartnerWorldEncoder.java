package game.world.partner;

import static app.Directories.*;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import app.input.FileSource;
import app.input.IOUtils;
import app.input.Line;
import game.RAM;
import game.ROM.LibScope;
import game.shared.StructTypes;
import game.shared.encoder.BaseDataEncoder;
import patcher.IGlobalDatabase;

public class PartnerWorldEncoder extends BaseDataEncoder
{
	public PartnerWorldEncoder(IGlobalDatabase db) throws IOException
	{
		super(StructTypes.mapTypes, LibScope.World, db, null, true);
		setAddressLimit(RAM.WORLD_PARTNER_LIMIT);
	}

	public void encode(PartnerConfig cfg) throws IOException
	{
		File patchFile = new File(MOD_ASSIST_PATCH + cfg.name + ".wpat");
		if (!patchFile.exists())
			return;

		setSource(new FileSource(patchFile));

		File indexFile = new File(DUMP_ASSIST_SRC + cfg.name + ".widx");
		File rawFile = new File(DUMP_ASSIST_RAW + cfg.name + ".bin");
		File outFile = new File(MOD_ASSIST_TEMP + cfg.name + ".bin");
		File outIndexFile = new File(MOD_ASSIST_TEMP + cfg.name + ".widx");

		if (!indexFile.exists()) {
			fileBuffer = ByteBuffer.allocateDirect(0);
			setOverlayMemoryLocation(RAM.WORLD_PARTNER_START, RAM.WORLD_PARTNER_MAX_SIZE);
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

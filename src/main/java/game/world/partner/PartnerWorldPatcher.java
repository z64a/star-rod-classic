package game.world.partner;

import static app.Directories.*;
import static game.world.partner.PartnerConfig.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import org.apache.commons.io.FileUtils;

import app.Directories;
import app.input.InputFileException;
import app.input.InvalidInputException;
import game.ROM.EOffset;
import game.shared.ProjectDatabase;
import game.shared.encoder.GlobalPatchManager;
import game.shared.struct.Struct;
import patcher.Patcher;
import patcher.Region;
import patcher.RomPatcher;
import util.CaseInsensitiveMap;
import util.Logger;

public class PartnerWorldPatcher
{
	private final Patcher patcher;
	private final RomPatcher rp;

	private final File xmlFile;
	private ArrayList<PartnerConfig> partners;

	public PartnerWorldPatcher(Patcher patcher) throws IOException
	{
		this.patcher = patcher;
		rp = patcher.getRomPatcher();
		xmlFile = new File(Directories.MOD_GLOBALS + "/" + Directories.FN_PARTNERS);
		partners = PartnerConfig.readXML(patcher, xmlFile);
	}

	public void patchData(Patcher patcher) throws IOException
	{
		FileUtils.forceMkdir(MOD_ASSIST_TEMP.toFile());
		FileUtils.cleanDirectory(MOD_ASSIST_TEMP.toFile());

		for (PartnerConfig cfg : partners) {
			File source;
			File index;
			File patch = new File(MOD_ASSIST_PATCH + cfg.name + ".wpat");

			Logger.log("Patching partner " + cfg.name);

			if (patch.exists()) {
				PartnerWorldEncoder encoder = new PartnerWorldEncoder(patcher);
				encoder.encode(cfg);

				index = new File(MOD_ASSIST_TEMP + cfg.name + ".widx");
				source = new File(MOD_ASSIST_TEMP + cfg.name + ".bin");
			}
			else {
				index = new File(DUMP_ASSIST_SRC + cfg.name + ".widx");
				source = new File(DUMP_ASSIST_RAW + cfg.name + ".bin");
			}

			HashMap<String, Struct> structMap = new HashMap<>();
			new PartnerWorldEncoder(patcher).loadIndexFile(structMap, index);

			try {
				cfg.tableEntry = new PartnerTableEntry(source, cfg, structMap);
			}
			catch (InvalidInputException e) {
				throw new InputFileException(xmlFile, e);
			}
		}
	}

	public void writeData(Patcher patcher) throws IOException
	{
		writeWorldData();

		for (int i = 0; i < partners.size(); i++) {
			PartnerConfig cfg = partners.get(i);
			rp.seek("Partner World Table", 0x9152C + i * 0x40);
			cfg.tableEntry.write(rp);

			rp.seek("Partner Anims", 0x9181C + i * 0x24);
			for (int k = 0; k < 9; k++)
				rp.writeInt(cfg.anims[k]);

			rp.seek("Partner Strings", 0x6A338 + i * 0x10);
			try {
				rp.writeInt(patcher.resolveStringID(cfg.abilityDescString));
				rp.writeInt(patcher.resolveStringID(cfg.battleDescString));
			}
			catch (InvalidInputException e) {
				throw new InputFileException(xmlFile, "Invalid string ID. " + e.getMessage());
			}
		}
	}

	private void writeWorldData() throws IOException
	{
		int currentPos = ProjectDatabase.rom.getOffset(EOffset.PARTNER_WORLD_START);
		int currentEnd = ProjectDatabase.rom.getOffset(EOffset.PARTNER_WORLD_END);

		for (PartnerConfig cfg : partners) {
			byte[] data = FileUtils.readFileToByteArray(cfg.tableEntry.binary);

			if (currentPos + data.length > currentEnd) {
				patcher.addEmptyRegion(new Region(currentPos, currentEnd));
				currentPos = rp.nextAlignedOffset();
				currentEnd = Integer.MAX_VALUE;
			}

			rp.seek("Partner World Data", currentPos);
			rp.write(data);
			Logger.log(String.format("Wrote %s to %X", cfg.tableEntry.binary.getName(), currentPos));

			cfg.tableEntry.romStart = currentPos;
			cfg.tableEntry.romEnd = currentPos + data.length;

			currentPos += data.length;
		}

		if (currentEnd < Integer.MAX_VALUE)
			patcher.addEmptyRegion(new Region(currentPos, currentEnd));
	}

	private static final int NUM_EXTENDED = 10;
	private static final int[] ORDER_EXTENDED = new int[] {
			1, 2, 3, 4, 5,
			9, 6, 7, 8, 10
	};

	public void allowExtraPartners(Patcher patcher, GlobalPatchManager gpm) throws IOException
	{
		/*
		8024F630 = ##[ANIMS]
		8024F6B0 = ##[PARTNERID]
		8024F6D0 = ##[BIOS]
		8024F6F0 = ##[FIRSTMOVES]
		8024F718 = ##[PORTRAITS]
		80270660 = ##[SPRITES]
		80270680 = ##[AVAILABLE]
		 */

		//NOTE: twink is NOT included, since he doesnt follow the typical movetable move layout
		if (partners.size() != (NUM_EXTENDED + 1))
			throw new InputFileException(xmlFile, "Expected to read " + NUM_EXTENDED + " partners, found " + partners.size());

		CaseInsensitiveMap<String> pointers = new CaseInsensitiveMap<>();
		rp.seek("Pause Partner Data", rp.nextAlignedOffset());

		// 8024F630 = ##[ANIMS]
		int menuAnimTable = rp.getCurrentOffset();

		for (int i = 0; i < NUM_EXTENDED; i++) {
			PartnerConfig cfg = partners.get(ORDER_EXTENDED[i] - 1);
			rp.writeInt(cfg.anims[ANIM_DEFAULT]);
			rp.writeInt(cfg.anims[ANIM_WALK]);
			rp.writeInt(cfg.anims[ANIM_SPEAK]);
			rp.writeInt(-1);
		}
		pointers.put("ANIMS", String.format("%08X", rp.toAddress(menuAnimTable)));

		// 8024F6B0 = ##[PARTNERID]
		int partnerIDs = rp.getCurrentOffset();
		for (int i = 0; i < NUM_EXTENDED; i++)
			rp.writeInt(ORDER_EXTENDED[i]);
		pointers.put("PARTNERID", String.format("%08X", rp.toAddress(partnerIDs)));

		// 8024F6D0 = ##[BIOS]
		int menuFullDescriptions = rp.getCurrentOffset();
		for (int i = 0; i < NUM_EXTENDED; i++) {
			PartnerConfig cfg = partners.get(ORDER_EXTENDED[i] - 1);
			try {
				rp.writeInt(patcher.resolveStringID(cfg.fullDescString));
			}
			catch (InvalidInputException e) {
				throw new InputFileException(xmlFile, "Invalid string ID. " + e.getMessage());
			}
		}
		pointers.put("BIOS", String.format("%08X", rp.toAddress(menuFullDescriptions)));

		// 8024F6F0 = ##[FIRSTMOVES]
		int firstMoves = rp.getCurrentOffset();
		rp.writeInt(0x83);
		rp.writeInt(0x89);
		rp.writeInt(0x8F);
		rp.writeInt(0x95);
		rp.writeInt(0x9B); // goompa
		rp.writeInt(0xB3);
		rp.writeInt(0xA1);
		rp.writeInt(0xA7);
		rp.writeInt(0xAD);
		rp.writeInt(0xB9); // goombaria
		pointers.put("FIRSTMOVES", String.format("%08X", rp.toAddress(firstMoves)));

		int[] portraitStrings = new int[NUM_EXTENDED];
		for (int i = 0; i < NUM_EXTENDED; i++) {
			PartnerConfig cfg = partners.get(i); // note: not indirected through ORDER_EXTENDED
			portraitStrings[i] = rp.toAddress(rp.getCurrentOffset());
			rp.write(cfg.portraitName.getBytes());
			rp.writeByte(0); // terminate string
			rp.padOut(4);
		}

		// 8024F718 = ##[PORTRAITS]
		int menuPortraitTable = rp.getCurrentOffset();
		for (int i = 0; i < NUM_EXTENDED; i++)
			rp.writeInt(portraitStrings[i]);
		pointers.put("PORTRAITS", String.format("%08X", rp.toAddress(menuPortraitTable)));

		// 80270660 = ##[SPRITES]
		int sprites = rp.getCurrentOffset();
		for (int i = 0; i < NUM_EXTENDED; i++)
			rp.writeInt(0);
		pointers.put("SPRITES", String.format("%08X", rp.toAddress(sprites)));

		// 80270680 = ##[AVAILABLE]
		int available = rp.getCurrentOffset();
		for (int i = 0; i < NUM_EXTENDED; i++)
			rp.writeInt(0);
		pointers.put("AVAILABLE", String.format("%08X", rp.toAddress(available)));

		gpm.readInternalPatch("ExtraPartners.patch", pointers);
	}

	public void patchPause(Patcher patcher)
	{
		// TODO Auto-generated method stub
	}
}

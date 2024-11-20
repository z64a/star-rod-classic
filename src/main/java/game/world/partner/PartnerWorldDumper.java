package game.world.partner;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import app.Directories;
import app.Environment;
import game.RAM;
import game.shared.decoder.DumpMetadata;

public class PartnerWorldDumper
{
	private static enum MiscPartnerData
	{
		// @formatter:off
		GOOMBARIO	(0x280006, "party_kurio"),
		KOOPER		(0x280013, "party_kameki"),
		BOMBETTE	(0x280020, "party_pinki"),
		PARAKARRY	(0x28002D, "party_pareta"),
		GOOMPA		(0x280006, "party_kurio"),
		WATT		(0x280047, "party_resa"),
		SUSHIE		(0x280054, "party_akari"),
		LAKILESTER	(0x280061, "party_opuku"),
		BOW			(0x28003A, "party_pokopi"),
		GOOMBARIA	(0x280006, "party_kurio"),
		TWINK		(0x280006, "party_kurio"); // peach_letter by default, but that crashes
		// @formatter:on

		private final int fullDesc;
		private final String portraitName;

		private MiscPartnerData(int fullDesc, String portraitName)
		{
			this.fullDesc = fullDesc;
			this.portraitName = portraitName;
		}
	}

	public static void main(String[] args) throws IOException
	{
		Environment.initialize();
		dumpPartners(Environment.getBaseRomBuffer());
		Environment.exit();
	}

	public static void dumpPartners(ByteBuffer fileBuffer) throws IOException
	{
		MiscPartnerData[] miscData = MiscPartnerData.values();
		List<PartnerConfig> partners = new ArrayList<>(11);
		for (int i = 0; i < 11; i++) {
			String baseName = String.format("%02X %s", i, PartnerConfig.DEFAULT_PARTNER_NAMES[i]);

			fileBuffer.position(0x9152C + i * 0x40);
			PartnerTableEntry entry = new PartnerTableEntry(fileBuffer);

			DumpMetadata metadata = new DumpMetadata(baseName,
				entry.romStart, entry.romEnd,
				RAM.WORLD_PARTNER_START, RAM.WORLD_PARTNER_LIMIT);

			byte[] bytes = new byte[metadata.size];
			fileBuffer.position(metadata.romStart);
			fileBuffer.get(bytes);

			PartnerWorldDecoder decoder = new PartnerWorldDecoder(fileBuffer, metadata, entry);
			PartnerConfig config = decoder.getPartnerConfig();

			fileBuffer.position(0x9181C + i * 0x24);
			for (int k = 0; k < 9; k++)
				config.anims[k] = fileBuffer.getInt();

			fileBuffer.position(0x6A338 + i * 0x10);
			config.fullDescString = String.format("%08X", miscData[i].fullDesc);
			config.abilityDescString = String.format("%08X", fileBuffer.getInt());
			config.battleDescString = String.format("%08X", fileBuffer.getInt());

			config.portraitName = miscData[i].portraitName;

			partners.add(config);
		}

		File xmlFile = new File(Directories.DUMP_GLOBALS + "/" + Directories.FN_PARTNERS);
		PartnerConfig.writeXML(partners, xmlFile);
	}
}

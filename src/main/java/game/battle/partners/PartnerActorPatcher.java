package game.battle.partners;

import static app.Directories.*;
import static game.battle.BattleConstants.ALLY_BASE;
import static game.shared.StructTypes.ActorT;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import app.Directories;
import app.input.IOUtils;
import app.input.InputFileException;
import game.battle.formations.BattleSectionEncoder;
import game.shared.struct.Struct;
import game.world.partner.PartnerConfig;
import patcher.Patcher;
import patcher.Region;
import patcher.RomPatcher;
import util.Logger;

public class PartnerActorPatcher
{
	private static class PartnerActorConfig
	{
		public File source;
		public int entryOffset;

		public int actorAddress;
	}

	private final Patcher patcher;
	private final RomPatcher rp;
	private List<PartnerActorConfig> configs;

	public PartnerActorPatcher(Patcher patcher)
	{
		this.patcher = patcher;
		rp = patcher.getRomPatcher();
	}

	public void patchPartnerData() throws IOException
	{
		FileUtils.forceMkdir(MOD_ALLY_TEMP.toFile());
		FileUtils.cleanDirectory(MOD_ALLY_TEMP.toFile());

		Collection<File> patchFiles = IOUtils.getFilesWithExtension(MOD_ALLY_PATCH, "bpat", true);

		for (File f : patchFiles) {
			Logger.log("Executing patch: " + f.getName());
			String name = FilenameUtils.removeExtension(f.getName());
			PartnerActorEncoder encoder = new PartnerActorEncoder(patcher);
			encoder.encode(name);
		}
	}

	public void generateConfigs() throws IOException
	{
		File xmlFile = new File(Directories.MOD_GLOBALS + "/" + Directories.FN_PARTNERS);
		configs = new LinkedList<>();

		for (PartnerConfig partner : PartnerConfig.readXML(patcher, xmlFile)) {
			PartnerActorConfig config = new PartnerActorConfig();

			File patchFile = new File(MOD_ALLY_PATCH + partner.name + ".bpat");
			File indexFile;

			if (patchFile.exists()) {
				indexFile = new File(MOD_ALLY_TEMP + partner.name + ".bidx");
				config.source = new File(MOD_ALLY_TEMP + partner.name + ".bin");
			}
			else {
				indexFile = new File(DUMP_ALLY_SRC + partner.name + ".bidx");
				config.source = new File(DUMP_ALLY_RAW + partner.name + ".bin");

				// goombaria, by default
				if (!config.source.exists()) {
					configs.add(null);
					continue;
				}
			}

			HashMap<String, Struct> structMap = new HashMap<>();
			BattleSectionEncoder tempEncoder = new BattleSectionEncoder(patcher);
			tempEncoder.loadIndexFile(structMap, indexFile);

			boolean foundActor = false;
			for (Struct str : structMap.values()) {
				if (str.isTypeOf(ActorT)) {
					if (foundActor)
						throw new InputFileException(indexFile, "Found multiple Actors in " + partner.name);

					config.actorAddress = str.originalAddress;
					foundActor = true;
				}
			}

			if (!foundActor)
				throw new InputFileException(indexFile, "Could not find Actor in " + partner.name);

			configs.add(config);
		}
	}

	public void writePartnerTable() throws IOException
	{
		rp.seek("Partner Battle Table", 0x1B2804);

		for (PartnerActorConfig cfg : configs) {
			if (cfg == null) // goombaria, by default
			{
				rp.writeInt(0);
				rp.writeInt(0);
				rp.writeInt(0);
				rp.writeInt(0);
				rp.writeInt(0);
			}
			else {
				cfg.entryOffset = rp.getCurrentOffset();
				rp.skip(8); // ROM offsets
				rp.writeInt(ALLY_BASE);
				rp.writeInt(cfg.actorAddress);
				rp.skip(4); // unknown, leave it alone
			}
		}

		Logger.log("Wrote partner script table.");
	}

	public void writePartnerData() throws IOException
	{
		HashMap<File, Region> sourceMap = new LinkedHashMap<>();

		for (PartnerActorConfig cfg : configs) {
			if (cfg == null)
				continue;

			if (!sourceMap.containsKey(cfg.source)) {
				byte[] data = FileUtils.readFileToByteArray(cfg.source);
				int start = patcher.getBattleDataPos(data.length);
				int end = start + data.length;

				sourceMap.put(cfg.source, new Region(start, end));

				rp.seek(FilenameUtils.getBaseName(cfg.source.getName()) + " Data", start);
				rp.write(data);
				Logger.log(String.format("Wrote %s to %X", cfg.source.getName(), start));
			}

			Region r = sourceMap.get(cfg.source);

			// update table
			rp.seek("Partner Battle Table", cfg.entryOffset);
			rp.writeInt((int) r.start);
			rp.writeInt((int) r.end);
		}
	}
}

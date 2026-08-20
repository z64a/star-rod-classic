package game.effects;

import static app.Directories.MOD_EFFECT_PATCH;
import static app.Directories.MOD_EFFECT_TEMP;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import app.Environment;
import app.input.IOUtils;
import app.input.InputFileException;
import game.ROM.EOffset;
import game.shared.ProjectDatabase;
import patcher.Patcher;
import patcher.RomPatcher;
import util.CaseInsensitiveMap;
import util.Logger;
import util.Priority;

public class EffectPatcher
{
	private static final class EffectAsset
	{
		public final int id;
		public final String name;
		public final EffectTableEntry entry;

		public EffectAsset(int id, String name, EffectTableEntry entry)
		{
			this.id = id;
			this.name = name;
			this.entry = entry;
		}
	}

	private static final class PatchTarget
	{
		public final EffectAsset asset;
		public final String sourceName;
		public final boolean graphics;
		public final List<File> patchFiles = new LinkedList<>();

		public File binary;
		public int mainAddress;

		public PatchTarget(EffectAsset asset, boolean graphics)
		{
			this.asset = asset;
			this.graphics = graphics;
			sourceName = graphics ? asset.name + "_Gfx" : asset.name;
		}
	}

	private final Patcher patcher;
	private final RomPatcher rp;
	private final List<PatchTarget> patchedTargets = new LinkedList<>();

	public EffectPatcher(Patcher patcher)
	{
		this.patcher = patcher;
		rp = patcher.getRomPatcher();
	}

	public boolean buildData() throws IOException
	{
		patchedTargets.clear();
		if (!MOD_EFFECT_PATCH.toFile().exists())
			return false;

		FileUtils.forceMkdir(MOD_EFFECT_TEMP.toFile());
		FileUtils.cleanDirectory(MOD_EFFECT_TEMP.toFile());

		List<File> patchFiles = new ArrayList<>(IOUtils.getFilesWithExtension(MOD_EFFECT_PATCH, EffectEncoder.PATCH_EXTENSION, true));
		patchFiles.sort(Comparator.comparing(File::getPath, String.CASE_INSENSITIVE_ORDER));
		if (patchFiles.isEmpty())
			return false;

		CaseInsensitiveMap<PatchTarget> targetMap = readPatchTargets();
		for (File patchFile : patchFiles) {
			String sourceName = FilenameUtils.removeExtension(patchFile.getName());
			PatchTarget target = targetMap.get(sourceName);
			if (target == null)
				throw new InputFileException(patchFile, "No dumped effect source is named '%s'.", sourceName);

			if (target.patchFiles.isEmpty())
				patchedTargets.add(target);
			target.patchFiles.add(patchFile);
		}

		Logger.log("Building effect data...", Priority.MILESTONE);
		for (PatchTarget target : patchedTargets) {
			Logger.log("Patching effect " + target.sourceName);
			EffectEncoder encoder = new EffectEncoder(patcher, target.asset.entry, target.graphics);
			encoder.encode(target.sourceName, target.patchFiles);
			target.binary = EffectEncoder.getOutputFile(target.sourceName);

			if (!target.graphics) {
				target.mainAddress = encoder.readMainAddress(target.sourceName);
				int mainOffset = target.mainAddress - target.asset.entry.codeDestAddr;
				if (mainOffset < 0 || mainOffset >= target.binary.length())
					throw new InputFileException(target.patchFiles.get(0),
						"Rebuilt $Function_Main at %08X is outside the effect code blob.", target.mainAddress);
			}
		}

		return true;
	}

	private CaseInsensitiveMap<PatchTarget> readPatchTargets() throws IOException
	{
		CaseInsensitiveMap<PatchTarget> targetMap = new CaseInsensitiveMap<>();
		ByteBuffer fileBuffer = Environment.getBaseRomBuffer().duplicate();
		int effectTableBase = ProjectDatabase.rom.getOffset(EOffset.EFFECT_TABLE);

		for (int id = 0; id < EffectEditor.EFFECT_COUNT; id++) {
			fileBuffer.position(effectTableBase + id * EffectTableEntry.SIZE);
			EffectTableEntry entry = new EffectTableEntry(fileBuffer);
			EffectAsset asset = new EffectAsset(id, EffectEditor.getSourceName(id), entry);

			if (entry.hasCode())
				targetMap.put(asset.name, new PatchTarget(asset, false));
			if (entry.hasGraphics())
				targetMap.put(asset.name + "_Gfx", new PatchTarget(asset, true));
		}

		return targetMap;
	}

	public void writeData() throws IOException
	{
		if (patchedTargets.isEmpty())
			return;

		Logger.log("Writing effect data...", Priority.MILESTONE);
		int effectTableBase = ProjectDatabase.rom.getOffset(EOffset.EFFECT_TABLE);

		for (PatchTarget target : patchedTargets) {
			byte[] data = FileUtils.readFileToByteArray(target.binary);
			int start = rp.nextAlignedOffset();
			int end = start + data.length;

			rp.seek("Effect " + target.sourceName + " Data", start);
			rp.write(data);

			int tableEntry = effectTableBase + target.asset.id * EffectTableEntry.SIZE;
			if (target.graphics) {
				rp.seek("Effect Graphics Table", tableEntry + 0x10);
				rp.writeInt(start);
				rp.writeInt(end);
			}
			else {
				rp.seek("Effect Code Table", tableEntry);
				rp.writeInt(target.mainAddress);
				rp.writeInt(start);
				rp.writeInt(end);
			}

			Logger.log(String.format("Wrote %s to %X", target.binary.getName(), start));
		}
	}
}

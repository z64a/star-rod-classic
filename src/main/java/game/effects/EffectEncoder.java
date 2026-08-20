package game.effects;

import static app.Directories.DUMP_EFFECT_RAW;
import static app.Directories.DUMP_EFFECT_SRC;
import static app.Directories.MOD_EFFECT_TEMP;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;

import app.input.FileSource;
import app.input.IOUtils;
import app.input.InputFileException;
import app.input.Line;
import asm.MIPS;
import game.ROM.LibScope;
import game.shared.StructTypes;
import game.shared.encoder.BaseDataEncoder;
import game.shared.struct.Struct;
import patcher.IGlobalDatabase;

public class EffectEncoder extends BaseDataEncoder
{
	public static final String PATCH_EXTENSION = "epat";

	private final EffectTableEntry entry;
	private final boolean graphics;

	public EffectEncoder(IGlobalDatabase db, EffectTableEntry entry, boolean graphics)
	{
		super(StructTypes.sharedTypes, LibScope.Effect, db, null, true);
		this.entry = entry;
		this.graphics = graphics;

		if (graphics)
			setAddressLimit(EffectEditor.GRAPHICS_ADDRESS_LIMIT);
		else
			setAddressLimit(entry.codeDestAddr + EffectEditor.CODE_SIZE_LIMIT);
	}

	public void encode(String sourceName, List<File> patchFiles) throws IOException
	{
		if (patchFiles.isEmpty())
			throw new IllegalArgumentException("Effect encoder requires at least one patch file.");

		File indexFile = getDumpIndexFile(sourceName);
		File rawFile = getDumpRawFile(sourceName);
		File outFile = getOutputFile(sourceName);
		File outIndexFile = getOutputIndexFile(sourceName);

		if (!indexFile.exists())
			throw new InputFileException(patchFiles.get(0), "Missing dumped effect index: %s. Dump assets with Visual Effects enabled.", indexFile.getName());
		if (!rawFile.exists())
			throw new InputFileException(patchFiles.get(0), "Missing dumped effect binary: %s. Dump assets with Visual Effects enabled.", rawFile.getName());

		fileBuffer = IOUtils.getDirectBuffer(rawFile);
		setSource(new FileSource(patchFiles.get(0)));

		try {
			MIPS.setSegment(graphics ? 0 : 0xE);
			readIndexFile(indexFile);
			for (File patchFile : patchFiles)
				readPatchFile(patchFile);

			digest();
			buildOverlay(outFile, outIndexFile);
		}
		finally {
			MIPS.resetSegment();
		}

		long sizeLimit = graphics ? EffectEditor.GRAPHICS_ADDRESS_LIMIT - EffectEditor.GRAPHICS_BASE : EffectEditor.CODE_SIZE_LIMIT;
		if (outFile.length() > sizeLimit)
			throw new InputFileException(patchFiles.get(0), "Rebuilt %s is %,X bytes; the maximum is %,X bytes.",
				graphics ? "effect graphics" : "effect code", outFile.length(), sizeLimit);

		validateLinkAddress(sourceName);
	}

	private void validateLinkAddress(String sourceName) throws IOException
	{
		HashMap<String, Struct> structMap = new HashMap<>();
		File indexFile = getOutputIndexFile(sourceName);
		loadIndexFile(structMap, indexFile);

		int expectedAddress = graphics ? EffectEditor.GRAPHICS_BASE : entry.codeDestAddr;
		Struct start = structMap.get("$Start");
		if (start == null || start.originalAddress != expectedAddress)
			throw new InputFileException(indexFile, "Effect %s was linked at the wrong virtual address; expected %08X.",
				graphics ? "graphics" : "code", expectedAddress);
	}

	public int readMainAddress(String sourceName) throws IOException
	{
		HashMap<String, Struct> structMap = new HashMap<>();
		File indexFile = getOutputIndexFile(sourceName);
		loadIndexFile(structMap, indexFile);

		Struct main = structMap.get("$Function_Main");
		if (main == null || !main.isTypeOf(StructTypes.FunctionT))
			throw new InputFileException(indexFile, "Could not find $Function_Main in rebuilt effect code.");

		return main.originalAddress;
	}

	public static File getDumpRawFile(String sourceName)
	{
		return new File(DUMP_EFFECT_RAW + sourceName + ".bin");
	}

	public static File getDumpIndexFile(String sourceName)
	{
		return new File(DUMP_EFFECT_SRC + sourceName + EffectEditor.INDEX_EXTENSION);
	}

	public static File getOutputFile(String sourceName)
	{
		return new File(MOD_EFFECT_TEMP + sourceName + ".bin");
	}

	public static File getOutputIndexFile(String sourceName)
	{
		return new File(MOD_EFFECT_TEMP + sourceName + EffectEditor.INDEX_EXTENSION);
	}

	@Override
	protected void replaceExpression(Line line, String[] args, List<String> newTokenList)
	{}
}

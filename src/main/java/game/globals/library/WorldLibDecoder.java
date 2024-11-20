package game.globals.library;

import static app.Directories.DUMP_LIB;
import static game.shared.StructTypes.*;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import app.Directories;
import game.shared.decoder.Pointer.Origin;
import game.shared.decoder.PointerHeuristic;
import game.shared.lib.LibEntry;
import game.shared.struct.miniscript.HudElementScript;
import game.world.BaseWorldDecoder;
import util.Logger;

public class WorldLibDecoder extends BaseWorldDecoder
{
	private File libHintFile = null;

	@Override
	protected File getHintFile()
	{
		return libHintFile;
	}

	public WorldLibDecoder(ByteBuffer fileBuffer, int startOffset, int endOffset, int baseAddress) throws IOException
	{
		new WorldLibDecoder(fileBuffer, startOffset, endOffset, baseAddress, String.format("%07X", startOffset));
	}

	public WorldLibDecoder(ByteBuffer fileBuffer, int startOffset, int endOffset, int baseAddress, String libName) throws IOException
	{
		this(fileBuffer, startOffset, endOffset, baseAddress, libName, null);
	}

	public WorldLibDecoder(ByteBuffer fileBuffer, int startOffset, int endOffset, int baseAddress, String libName, String hintName) throws IOException
	{
		super();

		Logger.log("Dumping Library: " + libName);

		sourceName = "ROM";
		annotateIndexInfo = true;

		doSanityChecks = false;

		if (hintName != null)
			libHintFile = new File(Directories.DATABASE_HINTS + hintName);

		setOffsetRange(startOffset, endOffset);

		int endAddress = baseAddress + (endOffset - startOffset);
		setAddressRange(baseAddress, endAddress, endAddress);

		for (LibEntry e : library) {
			if (isLocalAddress(e.address)) {
				switch (e.type) {
					case api:
					case asm:
						Logger.log("Dumping Function_" + e.name);
						enqueueAsRoot(e.address, FunctionT, Origin.DECODED, "Function_" + e.name);
						break;

					case script:
						Logger.log("Dumping Script_" + e.name);
						enqueueAsRoot(e.address, ScriptT, Origin.DECODED, "Script_" + e.name);
						break;

					case data:
						break;
				}
			}
		}

		findLocalPointers(fileBuffer);

		super.decode(fileBuffer);

		File rawFile = new File(DUMP_LIB + libName + ".bin");
		File scriptFile = new File(DUMP_LIB + libName + ".wscr");
		File indexFile = new File(DUMP_LIB + libName + ".widx");

		printScriptFile(scriptFile, fileBuffer);
		printIndexFile(indexFile);
		writeRawFile(rawFile, fileBuffer);
	}

	@Override
	public boolean shouldFunctionsRemoveJumps()
	{
		return true;
	}

	@Override
	protected int guessType(PointerHeuristic h, ByteBuffer fileBuffer)
	{
		int matches = super.guessType(h, fileBuffer);

		if (matches != 1 && HudElementScript.isHudElement(h, fileBuffer)) {
			h.structType = HudElementScriptT;
			matches++;
		}

		if (matches != 1)
			h.structType = UnknownT;

		return matches;
	}
}

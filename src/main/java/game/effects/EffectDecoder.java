package game.effects;

import static app.Directories.DUMP_EFFECT_RAW;
import static app.Directories.DUMP_EFFECT_SRC;
import static game.shared.StructTypes.*;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import asm.MIPS;
import game.ROM.LibScope;
import game.shared.ProjectDatabase;
import game.shared.decoder.BaseDataDecoder;
import game.shared.decoder.DumpMetadata;
import game.shared.decoder.Pointer;
import game.shared.decoder.Pointer.Origin;
import game.shared.struct.script.Script;
import game.shared.struct.script.Script.ScriptLine;

public class EffectDecoder extends BaseDataDecoder
{
	protected String sourceName;

	protected void useDumpMetadata(DumpMetadata metadata)
	{
		sourceName = metadata.sourceName;

		startOffset = metadata.romStart;
		endOffset = metadata.romEnd;

		setAddressRange(metadata.ramStart, metadata.ramEnd, metadata.ramLimit);
		setOffsetRange(metadata.romStart, metadata.romEnd);
	}

	@Override
	public String getSourceName()
	{
		return sourceName;
	}

	public EffectDecoder(ByteBuffer fileBuffer, DumpMetadata metadata, EffectTableEntry entry) throws IOException
	{
		super(LibScope.Common, FunctionT, ProjectDatabase.rom.getLibrary(LibScope.Common));

		try {
			MIPS.setSegment(0xE);

			useDumpMetadata(metadata);
			findLocalPointers(fileBuffer);
			enqueueAsRoot(entry.initAddr, FunctionT, Origin.DECODED, "Function_Init");
			super.decode(fileBuffer);

			File rawFile = new File(DUMP_EFFECT_RAW + sourceName + ".bin");
			File scriptFile = new File(DUMP_EFFECT_SRC + sourceName + ".wscr");
			File indexFile = new File(DUMP_EFFECT_SRC + sourceName + ".widx");

			printScriptFile(scriptFile, fileBuffer);
			printIndexFile(indexFile);
			writeRawFile(rawFile, fileBuffer);
		}
		finally {
			MIPS.resetSegment();
		}
	}

	@Override
	public void scanScript(Pointer ptr, ByteBuffer fileBuffer)
	{
		Script.scan(this, ptr, fileBuffer);
		int endPosition = fileBuffer.position();

		for (ScriptLine line : ptr.script) {
			switch (line.cmd) {
				case SET_INT: // impossible to tell what the local pointer is from this command
					if (isLocalAddress(line.args[1])) {
						//XXX necessary? shouldnt these be automatically found during the initial scan?
						Pointer childPtr = getPointer(line.args[1]);
						ptr.addUniqueChild(childPtr);
					}
					break;

				case SET_BUFFER:
					if (isLocalAddress(line.args[0])) {
						addPointer(line.args[0]);
						enqueueAsChild(ptr, line.args[0], IntTableT);
					}
					break;

				case SET_FBUFFER:
					if (isLocalAddress(line.args[0])) {
						addPointer(line.args[0]);
						enqueueAsChild(ptr, line.args[0], FloatTableT);
					}
					break;

				case EXEC1:
				case EXEC2:
				case EXEC_WAIT:
					if (isLocalAddress(line.args[0])) {
						enqueueAsChild(ptr, line.args[0], ScriptT);
					}
					break;

				case TRIGGER:
				case LOCK:
					// ignore for battles
					break;

				default:
					break;
			}
		}

		fileBuffer.position(endPosition);
	}
}

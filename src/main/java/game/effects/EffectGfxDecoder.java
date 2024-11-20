package game.effects;

import static app.Directories.DUMP_EFFECT_RAW;
import static app.Directories.DUMP_EFFECT_SRC;
import static game.shared.StructTypes.*;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import app.input.InvalidInputException;
import asm.MIPS;
import game.ROM.LibScope;
import game.shared.ProjectDatabase;
import game.shared.decoder.BaseDataDecoder;
import game.shared.decoder.DumpMetadata;
import game.shared.decoder.Pointer;
import game.shared.decoder.Pointer.Origin;
import game.shared.struct.f3dex2.DisplayList;
import game.shared.struct.script.Script;
import game.shared.struct.script.Script.ScriptLine;

public class EffectGfxDecoder extends BaseDataDecoder
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

	public EffectGfxDecoder(ByteBuffer fileBuffer, DumpMetadata metadata, EffectTableEntry entry) throws IOException
	{
		super(LibScope.Common, DisplayListT, ProjectDatabase.rom.getLibrary(LibScope.Common));
		try {
			MIPS.setSegment(0);
			useDumpMetadata(metadata);
			findLocalPointers(fileBuffer);

			int gfxCount = 0;
			int nextPos = endOffset - 4;
			while (nextPos >= startOffset) {
				fileBuffer.position(nextPos);
				int v = fileBuffer.getInt();
				nextPos = nextPos - 4;

				if (v == 0xDF000000) {
					fileBuffer.position(nextPos - 12);
					int A = fileBuffer.getInt();
					fileBuffer.getInt();
					if ((A >> 24) == 0 || A == 0xFFFFFFFF)
						break;

					Integer startPos = readBackward(fileBuffer, startOffset);
					if (startPos != null) {
						int addr = startAddress + (startPos - startOffset);
						//				System.out.printf("FOUND GFX AT %X%n", addr);
						tryEnqueueAsRoot(addr, DisplayListT, Origin.DECODED);
						nextPos = startPos - 8;
						gfxCount++;
					}
				}
			}

			if (gfxCount > 0) {
				super.decode(fileBuffer);

				File rawFile = new File(DUMP_EFFECT_RAW + sourceName + "_Gfx.bin");
				File scriptFile = new File(DUMP_EFFECT_SRC + sourceName + "_Gfx.wscr");
				File indexFile = new File(DUMP_EFFECT_SRC + sourceName + "_Gfx.widx");

				printScriptFile(scriptFile, fileBuffer);
				printIndexFile(indexFile);
				writeRawFile(rawFile, fileBuffer);

				/*
				List<EmbeddedImage> sortedImages = new ArrayList<>();
				for(EmbeddedImage embed : embeddedImages)
					sortedImages.add(embed);

				Collections.sort(sortedImages, (a, b) -> a.imgAddr - b.imgAddr);

				for(EmbeddedImage embed : sortedImages)
				{
					int imgOffset = 0;
					if(embed.imgAddr >= 0x09000000)
						imgOffset = startOffset + (embed.imgAddr - startAddress);

					int palOffset = 0;
					if(embed.palAddr >= 0x09000000)
						palOffset = startOffset + (embed.palAddr - startAddress);

					if(embed.tile.format.type == TileFormat.TYPE_CI)
					{
						System.out.printf("<Image offset=\"%X\" palette=\"%X\" flip=\"true\" fmt=\"%s\" w=\"%d\" h=\"%d\" name=\"effect/%s_%X_%X\"/>%n",
								imgOffset, palOffset,  embed.tile.format, embed.tile.width, embed.tile.height, sourceName.replaceAll("\\s+", "_"), imgOffset, palOffset);
					}
					else
					{
						System.out.printf("<Image offset=\"%X\" flip=\"true\" fmt=\"%s\" w=\"%d\" h=\"%d\" name=\"effect/%s_%X\"/>%n",
								imgOffset, embed.tile.format, embed.tile.width, embed.tile.height, sourceName.replaceAll("\\s+", "_"), imgOffset);
					}
				}
				 */
			}

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

	public static Integer readBackward(ByteBuffer buf, int limitPos)
	{
		//	System.out.printf("START FROM %X%n", buf.position());

		int curPos = buf.position();
		int nextPos = curPos;
		int lastValidPos = curPos;

		while (nextPos > limitPos) {
			curPos = nextPos;
			buf.position(nextPos);

			int A = buf.getInt();
			int B = buf.getInt();
			int opcode = (A >> 24) & 0xFF;
			DisplayList.CommandType type = DisplayList.getCommandForOpcode(opcode);

			//		System.out.printf("> %6X %s%n", curPos, type);

			if (type == null)
				return lastValidPos;

			try {

				switch (type) {
					case G_NOOP:
					case G_ENDDL:
						return lastValidPos;

					/*
					case G_BRANCH_Z:
					buf.position(curPos);
					curPos -= 8;

					int C = buf.getInt();
					int D = buf.getInt();

					type.create(A, B, C, D);
					break;
					 */

					case G_TEXRECT:
						buf.position(curPos - 16);
						int C = buf.getInt();
						int D = buf.getInt();
						int E = buf.getInt();
						int F = buf.getInt();
						type.create(A, B, C, D, E, F);

						nextPos = curPos - 24;
						lastValidPos = curPos - 16;
						break;

					case G_VTX:
						int vtxNum = (A >> 12) & 0xFF;
						int vtxOffset = (B & 0xFFFFFF);
						//		lastVtxPos = (0x10 * vtxNum) + vtxOffset + startOffset;

						//	case G_MODIFYVTX:
						//	case G_CULLDL:
						//	case G_BRANCH_Z:
					case G_TRI1:
					case G_TRI2:
					case G_QUAD:
						//	case G_DMA_IO:
					case G_TEXTURE:
						//	case G_POPMTX:
					case G_GEOMETRYMODE:
						//	case G_MTX:
						//	case G_MOVEWORD:
						//	case G_MOVEMEM:
						//	case G_LOAD_UCODE:
					case G_DL:
						//	case G_NOOP_RDP:
						//	case G_RDPHALF_1:
					case G_SetOtherMode_L:
					case G_SetOtherMode_H:
						//	case G_TEXRECTFLIP:
					case G_RDPLOADSYNC:
					case G_RDPPIPESYNC:
					case G_RDPTILESYNC:
					case G_RDPFULLSYNC:
						//	case G_SETKEYGB:
						//	case G_SETKEYR:
						//	case G_SETCONVERT:
						//	case G_SETSCISSOR:
						//	case G_SETPRIMDEPTH:
					case G_RDPSetOtherMode:
					case G_LOADTLUT:
						//	case G_RDPHALF_2:
					case G_SETTILESIZE:
						//	case G_LOADBLOCK:
					case G_LOADTILE:
					case G_SETTILE:
					case G_FILLRECT:
					case G_SETFILLCOLOR:
					case G_SETFOGCOLOR:
					case G_SETBLENDCOLOR:
					case G_SETPRIMCOLOR:
					case G_SETENVCOLOR:
					case G_SETCOMBINE:
					case G_SETIMG:
						//	case G_SETZIMG:
						//	case G_SETCIMG:
						type.create(A, B);
						lastValidPos = curPos;
						nextPos = curPos - 8;
						break;

					default:
						return lastValidPos;
				}
			}
			catch (InvalidInputException e) {
				//	Logger.printStackTrace(e);
				return lastValidPos;
			}
		}

		return null;
	}
}

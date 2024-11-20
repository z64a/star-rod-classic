package game.shared.struct.other;

import static game.shared.StructTypes.*;

import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.input.InputFileException;
import app.input.InvalidInputException;
import app.input.Line;
import game.shared.BaseStruct;
import game.shared.DataUtils;
import game.shared.decoder.BaseDataDecoder;
import game.shared.decoder.Pointer;
import game.shared.encoder.BaseDataEncoder;
import game.shared.encoder.Patch;

public class AnimatedModelNode extends BaseStruct
{
	public static final AnimatedModelNode instance = new AnimatedModelNode();

	private static final Pattern FieldPattern = Pattern.compile("@(\\w+)\\s+(.+)");
	private static final Matcher FieldMatcher = FieldPattern.matcher("");

	private AnimatedModelNode()
	{}

	@Override
	public void scan(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer)
	{
		int ptrDisplayList = fileBuffer.getInt();
		if (ptrDisplayList != 0)
			decoder.enqueueAsChild(ptr, ptrDisplayList, DisplayListT);

		fileBuffer.getShort();
		fileBuffer.getShort();
		fileBuffer.getShort();
		int z = fileBuffer.getShort();
		assert (z == 0);

		fileBuffer.getFloat();
		fileBuffer.getFloat();
		fileBuffer.getFloat();

		int ptrNext;
		int ptrChild;
		int ptrVertices;

		if ((ptrNext = fileBuffer.getInt()) != 0)
			decoder.enqueueAsChild(ptr, ptrNext, AnimatedModelNodeT);

		if ((ptrChild = fileBuffer.getInt()) != 0)
			decoder.enqueueAsChild(ptr, ptrChild, AnimatedModelNodeT);

		int vtxOffset = fileBuffer.getShort();
		z = fileBuffer.getShort();
		assert (z == 0);

		if ((ptrVertices = fileBuffer.getInt()) != 0)
			decoder.enqueueAsChild(ptr, ptrVertices, VertexTableT);

		int modelID = fileBuffer.getShort();
		z = fileBuffer.getShort();
		assert (z == 0);

		if (ptrChild != 0)
			assert (ptrVertices == 0 && (vtxOffset == 0 || vtxOffset == -1) && modelID == 0) : ptr.getPointerName();
		//	else
		//		assert(ptrDisplayList != 0 || ptrVertices != 0 || modelID > 0) : ptr.getPointerName(); // modelID can still be -1, see kmr_11 8024816C

		if (modelID > 0)
			assert (ptrChild == 0 && ptrDisplayList == 0 && ptrVertices == 0 && vtxOffset == -1);
	}

	@Override
	public void print(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer, PrintWriter pw)
	{
		int ptrDisplayList = fileBuffer.getInt();

		float rx = (float) (fileBuffer.getShort() * (180.0 / 32767.0));
		float ry = (float) (fileBuffer.getShort() * (180.0 / 32767.0));
		float rz = (float) (fileBuffer.getShort() * (180.0 / 32767.0));
		fileBuffer.getShort();

		float x = fileBuffer.getFloat();
		float y = fileBuffer.getFloat();
		float z = fileBuffer.getFloat();

		int ptrNext = fileBuffer.getInt();
		int ptrChild = fileBuffer.getInt();

		int vtxOffset = fileBuffer.getShort();
		fileBuffer.getShort();

		int ptrVertices = fileBuffer.getInt();

		int modelID = fileBuffer.getShort();
		fileBuffer.getShort();

		pw.printf("@Position   %f %f %f%n", x, y, z);
		pw.printf("@Rotation   %f %f %f%n", rx, ry, rz);

		// % children
		if (ptrChild != 0) {
			pw.print("@Child      ");
			decoder.printScriptWord(pw, ptrChild);
			pw.println();
		}

		if (ptrNext != 0) {
			pw.print("@Next       ");
			decoder.printScriptWord(pw, ptrNext);
			pw.println();
		}

		if (modelID > 0) {
			pw.printf("@ModelID    ");
			decoder.printModelID(ptr, pw, modelID - 1);
			pw.println();

			assert (ptrChild == 0 && ptrDisplayList == 0 && ptrVertices == 0 && vtxOffset == -1);
		}
		else {
			if (ptrDisplayList != 0) {
				pw.print("@DisplayList   ");
				decoder.printScriptWord(pw, ptrDisplayList);
				pw.println();
			}

			if (ptrVertices != 0 || vtxOffset != 0) {
				pw.print("@VtxTable   ");
				decoder.printScriptWord(pw, ptrVertices);
				pw.println();

				pw.printf("@VtxStart   %X%n", vtxOffset);
			}
		}
	}

	@Override
	public void replaceStructConstants(BaseDataEncoder encoder, Patch patch)
	{
		if (patch.startingPos != 0)
			throw new InputFileException(patch.sourceLine, "AnimatedModelNode patches cannot have offsets!");

		// create a buffer for our struct
		String[] words = new String[11];
		Arrays.fill(words, "00000000");

		for (Line line : patch.lines) {
			try {
				line.gather();
				FieldMatcher.reset(line.str);
				if (!FieldMatcher.matches())
					throw new InputFileException(line, "Invalid format for field in AnimatedModelNode!");

				String fieldName = FieldMatcher.group(1);
				String argList = FieldMatcher.group(2);
				String[] args = argList.trim().split("\\s+");

				switch (fieldName.toLowerCase()) {
					case "displaylist":
						if (args.length != 1)
							throw new InputFileException(line, "Invalid value for " + fieldName + " field in AnimatedModelNode!");
						words[0] = args[0];
						break;
					case "rotation":
						if (args.length != 3)
							throw new InputFileException(line, "Invalid value for " + fieldName + " field in AnimatedModelNode!");
						short rx = (short) Math.round(Float.parseFloat(args[0]) * (32767.0 / 180.0));
						short ry = (short) Math.round(Float.parseFloat(args[1]) * (32767.0 / 180.0));
						short rz = (short) Math.round(Float.parseFloat(args[2]) * (32767.0 / 180.0));
						words[1] = String.format("%04X%04X", rx, ry);
						words[2] = String.format("%04X0000", rz);
						break;
					case "position":
						if (args.length != 3)
							throw new InputFileException(line, "Invalid value for " + fieldName + " field in AnimatedModelNode!");
						float x = Float.parseFloat(args[0]);
						float y = Float.parseFloat(args[1]);
						float z = Float.parseFloat(args[2]);
						words[3] = String.format("%08X", Float.floatToIntBits(x));
						words[4] = String.format("%08X", Float.floatToIntBits(y));
						words[5] = String.format("%08X", Float.floatToIntBits(z));
						break;
					case "child":
						if (args.length != 1)
							throw new InputFileException(line, "Invalid value for " + fieldName + " field in AnimatedModelNode!");
						words[6] = args[0];
						break;
					case "next":
						if (args.length != 1)
							throw new InputFileException(line, "Invalid value for " + fieldName + " field in AnimatedModelNode!");
						words[7] = args[0];
						break;
					case "vtxstart":
						if (args.length != 1)
							throw new InputFileException(line, "Invalid value for " + fieldName + " field in AnimatedModelNode!");
						int vtxStart = DataUtils.parseIntString(args[0]) & 0xFFFF;
						words[8] = String.format("%04X0000", vtxStart);
						break;
					case "vtxtable":
						if (args.length != 1)
							throw new InputFileException(line, "Invalid value for " + fieldName + " field in AnimatedModelNode!");
						words[9] = args[0];
						break;
					case "modelid":
						if (args.length != 1)
							throw new InputFileException(line, "Invalid value for " + fieldName + " field in AnimatedModelNode!");
						int modelID = DataUtils.parseIntString(args[0]) & 0xFFFF;
						words[10] = String.format("%04X0000", modelID);
						break;
					default:
						throw new InputFileException(line, "Unknown field for AnimatedModelNode: " + fieldName);
				}
			}
			catch (NumberFormatException | InvalidInputException e) {
				throw new InputFileException(line, e);
			}
		}

		// completely replace patch
		patch.lines.clear();
		patch.lines.add(patch.sourceLine.createLine(words));
	}
}

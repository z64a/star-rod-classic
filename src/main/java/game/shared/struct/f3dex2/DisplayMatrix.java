package game.shared.struct.f3dex2;

import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import app.input.InputFileException;
import app.input.Line;
import game.shared.BaseStruct;
import game.shared.decoder.BaseDataDecoder;
import game.shared.decoder.Pointer;
import game.shared.encoder.BaseDataEncoder;
import game.shared.encoder.Patch;

public class DisplayMatrix extends BaseStruct
{
	public static final DisplayMatrix instance = new DisplayMatrix();

	@Override
	public void scan(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer)
	{
		fileBuffer.position(fileBuffer.position() + 0x40);
	}

	@Override
	public void print(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer, PrintWriter pw)
	{
		short[][] whole = new short[4][4];
		for (int i = 0; i < 4; i++)
			for (int j = 0; j < 4; j++)
				whole[j][i] = fileBuffer.getShort();

		short[][] frac = new short[4][4];
		for (int i = 0; i < 4; i++)
			for (int j = 0; j < 4; j++)
				frac[j][i] = fileBuffer.getShort();

		for (int i = 0; i < 4; i++) {
			for (int j = 0; j < 4; j++) {
				int v = (whole[i][j] << 16) | (frac[i][j] & 0x0000FFFF);
				pw.printf("%f ", v / 65536.0);
			}
			pw.println();
		}
	}

	@Override
	public void replaceStructConstants(BaseDataEncoder encoder, Patch patch)
	{
		if (patch.startingPos != 0)
			throw new InputFileException(patch.lines.get(0), "Cannot use offset with display list matrices");

		List<Float> floatList = new ArrayList<>(16);
		List<String> charList = new ArrayList<>(32);

		for (Line line : patch.lines) {
			for (int i = 0; i < line.numTokens(); i++)
				floatList.add(line.getFloat(i));
		}

		if (floatList.size() != 16)
			throw new InputFileException(patch.lines.get(0), "Display list matrices must be 4x4!");

		short[][] whole = new short[4][4];
		short[][] frac = new short[4][4];

		int k = 0;
		for (int i = 0; i < 4; i++)
			for (int j = 0; j < 4; j++) {
				double elem = floatList.get(k++); // corresponds to mat[i][j]
				int v = (int) (elem * 65536.0);
				whole[i][j] = (short) (v >> 16);
				frac[i][j] = (short) v;
			}

		for (int i = 0; i < 4; i++)
			for (int j = 0; j < 4; j++)
				charList.add(String.format("%04Xs", whole[j][i]));

		for (int i = 0; i < 4; i++)
			for (int j = 0; j < 4; j++)
				charList.add(String.format("%04Xs", frac[j][i]));

		String[] newTokens = new String[charList.size()];
		charList.toArray(newTokens);

		patch.lines.clear();
		patch.lines.add(patch.sourceLine.createLine(newTokens));
	}
}

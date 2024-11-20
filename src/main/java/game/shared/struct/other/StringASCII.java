package game.shared.struct.other;

import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import app.input.IOUtils;
import app.input.InputFileException;
import app.input.Line;
import game.shared.BaseStruct;
import game.shared.decoder.BaseDataDecoder;
import game.shared.decoder.Pointer;
import game.shared.encoder.BaseDataEncoder;
import game.shared.encoder.Patch;

public class StringASCII extends BaseStruct
{
	public static final StringASCII instance = new StringASCII();

	private StringASCII()
	{}

	@Override
	public void scan(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer)
	{
		int start = fileBuffer.position();
		while (fileBuffer.get() != (byte) 0)
			;
		fileBuffer.position((fileBuffer.position() + 3) & 0xFFFFFFFC);
		int end = fileBuffer.position();

		fileBuffer.position(start);
		ptr.text = IOUtils.readString(fileBuffer);
		fileBuffer.position(end);
	}

	@Override
	public void print(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer, PrintWriter pw)
	{
		pw.println("\"" + IOUtils.readString(fileBuffer, ptr.getSize()) + "\"");
	}

	@Override
	public void replaceStructConstants(BaseDataEncoder encoder, Patch patch)
	{
		List<String> charList = new ArrayList<>(64);

		Line first = patch.lines.get(0);
		if (first.str.charAt(0) != '"')
			throw new InputFileException(first, "ASCII contents must begin with \".");
		first.str = first.str.substring(1);

		Line last = patch.lines.get(patch.lines.size() - 1);
		if (last.str.charAt(last.str.length() - 1) != '"')
			throw new InputFileException(first, "ASCII contents must end with \".");
		last.str = last.str.substring(0, last.str.length() - 1);

		for (Line line : patch.lines)
			for (char c : line.str.toCharArray())
				charList.add(String.format("%02Xb", (int) c)); // to ASCII

		charList.add("00b");

		if (charList.size() % 4 != 0)
			for (int i = 0; i < 4 - (charList.size() % 4); i++)
				charList.add("00b");

		String[] newTokens = new String[charList.size()];
		charList.toArray(newTokens);

		patch.lines.clear();
		patch.lines.add(patch.sourceLine.createLine(newTokens));
	}
}

package game.shared.struct.other;

import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import app.input.Line;
import game.shared.BaseStruct;
import game.shared.decoder.BaseDataDecoder;
import game.shared.decoder.Pointer;
import game.shared.encoder.BaseDataEncoder;
import game.shared.encoder.Patch;
import util.japanese.JapaneseHelper;
import util.japanese.WanaKanaJava;

public class StringSJIS extends BaseStruct
{
	public static final StringSJIS instance = new StringSJIS();

	private StringSJIS()
	{}

	@Override
	public void scan(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer)
	{
		while (true) {
			byte bh = fileBuffer.get();

			boolean doubleByteCharacter = ((bh & 0xF0) == (byte) 0x80 || (bh & 0xF0) == (byte) 0x90 ||
				(bh & 0xF0) == (byte) 0xE0 || (bh & 0xF0) == (byte) 0xF0);

			// null terminator
			if (bh == 0)
				break;

			if (doubleByteCharacter)
				fileBuffer.get();
		}

		fileBuffer.position((fileBuffer.position() + 3) & 0xFFFFFFFC);
	}

	@Override
	public void print(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer, PrintWriter pw)
	{
		ByteBuffer bb = ByteBuffer.allocateDirect(ptr.getSize());
		byte b;
		while ((b = fileBuffer.get()) != 0)
			bb.put(b);
		bb.flip();

		String japanese = JapaneseHelper.convertSJIStoUTF8(bb);

		// cant read from the buffer again since its been transcoded to UTF
		WanaKanaJava wk = new WanaKanaJava();
		String romaji = wk.toRomaji(japanese);

		pw.println(japanese + " % " + romaji);
	}

	@Override
	public void replaceStructConstants(BaseDataEncoder encoder, Patch patch)
	{
		List<String> charList = new ArrayList<>(64);

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

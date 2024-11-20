package game.shared.struct.other;

import java.io.PrintWriter;
import java.nio.ByteBuffer;

import game.shared.BaseStruct;
import game.shared.decoder.BaseDataDecoder;
import game.shared.decoder.Pointer;
import game.shared.encoder.BaseDataEncoder;
import game.shared.encoder.Patch;
import game.string.StringDecoder;
import game.string.StringEncoder;
import util.Logger;

public class StringMarkup extends BaseStruct
{
	public static final StringMarkup instance = new StringMarkup();

	private StringMarkup()
	{}

	@Override
	public void scan(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer)
	{
		int start = fileBuffer.position();
		int i = 0;
		do {
			if (++i > 256) {
				Logger.logWarning("String at %08X does not terminate after 256 characters!");
				fileBuffer.position(fileBuffer.position() + 4);
			}

			if (!fileBuffer.hasRemaining()) {
				Logger.logWarning("String at %08X does not terminate by end of file!");
				return;
			}
		}
		while ((fileBuffer.get() != (byte) 0xFD));
		int end = fileBuffer.position();

		fileBuffer.position(start);
		byte[] bytes = new byte[end - start];
		fileBuffer.get(bytes);
		ptr.text = StringDecoder.toMarkup(bytes);
		if (ptr.text.endsWith("\r\n"))
			ptr.text = ptr.text.substring(0, ptr.text.length() - 2);
		else if (ptr.text.endsWith("\n"))
			ptr.text = ptr.text.substring(0, ptr.text.length() - 1);

		fileBuffer.position((end + 3) & -4); // align
	}

	@Override
	public void print(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer, PrintWriter pw)
	{
		pw.println(ptr.text);
	}

	@Override
	public void replaceStructConstants(BaseDataEncoder encoder, Patch patch)
	{
		/*
		ByteBuffer bb = StringEncoder.encodeLines(patch.lines);
		List<String> charList = new ArrayList<>(bb.capacity());

		while(bb.hasRemaining())
			charList.add(String.format("%02Xb", (int)bb.get()));

		if(charList.size() % 4 != 0)
			for(int i = 0; i < 4 - (charList.size() % 4); i++)
				charList.add("00b");

		String[] newTokens = new String[charList.size()];
		charList.toArray(newTokens);
		*/

		ByteBuffer textBuffer = StringEncoder.encodeLines(patch.lines);
		String[] bytes = new String[textBuffer.capacity()];
		for (int i = 0; i < bytes.length; i++)
			bytes[i] = String.format("%02Xb", textBuffer.get());

		patch.lines.clear();
		patch.lines.add(patch.sourceLine.createLine(bytes));
	}
}

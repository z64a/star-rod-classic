package util.japanese;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;

import app.Environment;
import util.SimpleProgressBarDialog;

public class JapaneseTextScanner
{
	public static void main(String args[]) throws IOException
	{
		Environment.initialize();

		RandomAccessFile raf = Environment.getBaseRomReader();
		Writer fout = new BufferedWriter(new OutputStreamWriter(new FileOutputStream("./SJIS.txt"), "UTF-16"));
		SimpleProgressBarDialog dialog = new SimpleProgressBarDialog("Japanese Text Scanner", "Scanning ROM...");

		boolean reading = false;
		ByteBuffer bb = ByteBuffer.allocate(32);

		while (raf.getFilePointer() < raf.length()) {
			byte bh = raf.readByte();
			byte bl = 0;

			boolean matchHiragana = (bh == (byte) 0x82);
			boolean matchKatakana = (bh == (byte) 0x83);
			boolean matchSpace = (bh == (byte) 0x20);
			boolean terminator = (bh == 0);

			if (matchHiragana || matchKatakana)
				bl = raf.readByte();

			boolean sjisCharacter = matchHiragana || matchKatakana || (reading && matchSpace);

			if (!reading)
				reading = sjisCharacter;

			if (reading) {
				// ignore unknown characters or buffer overruns
				if (bb.position() > 30 || (!sjisCharacter && !terminator)) {
					reading = false;
					bb.clear();
					continue;
				}

				// good input, keep reading
				if (!terminator) {
					if (matchSpace)
						bb.put(bh);
					else
						bb.put(bh).put(bl);
					continue;
				}

				// string is terminated

				// too short -- probable false positive
				if (bb.position() < 4) {
					reading = false;
					bb.clear();
					continue;
				}

				String offsetString = String.format("%08X : ", (int) raf.getFilePointer() - (bb.position() + 1));
				print(offsetString, bb, fout);

				reading = false;
				bb.clear();
			}

			dialog.setProgress((int) (100 * ((float) raf.getFilePointer() / raf.length())));
		}

		dialog.dismiss();
		fout.close();
		raf.close();
		Environment.exit();
	}

	public static void print(String s, ByteBuffer inBB, Writer fout) throws IOException
	{
		inBB.put((byte) 0x0A).flip(); // add new line and flip

		Charset sjis = Charset.forName("Shift-JIS");
		Charset utf8 = Charset.forName("UTF-16");

		CharBuffer decodedCB = sjis.decode(inBB);
		ByteBuffer outBB = utf8.encode(decodedCB);
		byte[] outArray = new byte[outBB.limit()];
		outBB.get(outArray);

		fout.write(s + new String(outArray, utf8));
	}
}

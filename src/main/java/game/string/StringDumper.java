package game.string;

import static app.Directories.DUMP_STRINGS_SRC;
import static game.string.StringConstants.NUM_STRING_SECTIONS;
import static game.string.StringConstants.STRING_SECTION_NAMES;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import app.Environment;
import app.config.Options;
import app.input.IOUtils;
import util.Logger;
import util.Priority;

public class StringDumper
{
	private static HashMap<Integer, PMString> stringsMap = new HashMap<>();

	public static void dumpAllStrings() throws IOException
	{
		List<List<ByteBuffer>> groupBuffers = new ArrayList<>(NUM_STRING_SECTIONS);
		RandomAccessFile raf = Environment.getBaseRomReader();

		// read offsets from all offset tables (sizes must be known a priori)

		boolean indentPrintedData = Environment.mainConfig.getBoolean(Options.IndentPrintedData);
		boolean newlineOpenBrace = Environment.mainConfig.getBoolean(Options.NewlineOpenBrace);

		int[] sectionOffsets = new int[NUM_STRING_SECTIONS];
		raf.seek(0x1B83000);
		for (int i = 0; i < NUM_STRING_SECTIONS; i++)
			sectionOffsets[i] = raf.readInt();

		for (int i = 0; i < NUM_STRING_SECTIONS; i++) {
			raf.seek(0x1B83000 + sectionOffsets[i]);

			List<Integer> stringOffsets = new ArrayList<>();

			int stringOffset;
			while ((stringOffset = raf.readInt()) != sectionOffsets[i])
				stringOffsets.add(stringOffset);
			stringOffsets.add(stringOffset);

			List<ByteBuffer> stringBuffers = new ArrayList<>();
			groupBuffers.add(stringBuffers);

			for (int j = 0; j < stringOffsets.size() - 1; j++) {
				int start = 0x1B83000 + stringOffsets.get(j);
				int next = 0x1B83000 + stringOffsets.get(j + 1);
				ByteBuffer buf = ByteBuffer.allocate(next - start);

				raf.seek(start);
				byte b;
				do {
					b = raf.readByte();
					buf.put(b);
				}
				while (b != (byte) 0xFD);

				buf.flip();
				stringBuffers.add(buf);
			}
		}

		raf.close();

		stringsMap.clear();

		// create output files
		for (int i = 0; i < groupBuffers.size(); i++) {
			String name = STRING_SECTION_NAMES[i];
			List<ByteBuffer> currentBuffers = groupBuffers.get(i);

			// TODO(0.5.0): .msg and #message
			PrintWriter out = IOUtils.getBufferedPrintWriter(DUMP_STRINGS_SRC + name + ".str");
			for (int j = 0; j < currentBuffers.size(); j++) {
				ByteBuffer buf = currentBuffers.get(j);
				byte[] bytes = new byte[buf.limit()];
				buf.get(bytes);

				// validate(bytes, i, j);

				String msg = StringDecoder.toMarkup(bytes);
				String[] lines = msg.split("\\r?\\n");

				out.printf("#string:%02X:%03X", i, j);
				if (newlineOpenBrace) {
					out.println();
					out.println("{");
				}
				else
					out.println(" {");

				for (String line : lines) {
					if (indentPrintedData)
						out.print("\t");
					out.println(line);
				}
				out.println("}");
				out.println();

				stringsMap.put(((i & 0xFFFF) << 16) | (j & 0xFFFF), new PMString(ByteBuffer.wrap(bytes), i, j));
				i &= 0xFFFF;
				j &= 0xFFFF;
				System.out.printf("### MSG_@%02X_%04X = 0x%06X,%n", i, j, (i << 16) | j);
			}
			out.close();

			Logger.log(String.format("Dumped strings from %07X: %s", 0x1B83000 + sectionOffsets[i], name), Priority.MILESTONE);
		}
	}

	public static void validate(byte[] bytes, int i, int j)
	{
		if (i == 0x1E && j == 0x3E)
			return; // problem case

		String msg = StringDecoder.toMarkup(bytes);
		System.out.println(msg);

		ByteBuffer check = StringEncoder.encode(msg);

		for (byte b : bytes)
			System.out.printf("%02X ", b);
		System.out.println();
		while (check.hasRemaining())
			System.out.printf("%02X ", check.get());
		System.out.println();
		check.rewind();

		assert (check.capacity() == bytes.length);

		int k = 0;
		while (check.hasRemaining())
			assert (check.get() == bytes[k++]) : k;
	}

	public static PMString getString(int id)
	{
		return stringsMap.get(id);
	}
}

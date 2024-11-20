package game.yay0;

import java.io.IOException;
import java.io.RandomAccessFile;

import app.Environment;
import app.input.IOUtils;

// NOTE: this misses 246 Yay0 blocks from the ROM
public class Yay0TableDumper
{
	public static void main(String args[]) throws IOException
	{
		Environment.initialize();
		dumpYay0();
		Environment.exit();
	}

	public static void dumpYay0() throws IOException
	{
		RandomAccessFile raf = Environment.getBaseRomReader();

		int prevOffset = 0;
		int prevLength = 0;

		raf.seek(0x1E40020);
		for (int i = 0; i < 1033; i++) // 1033 = 0x409
		{
			raf.seek(0x1E40020 + i * 0x1C);
			String name = IOUtils.readString(raf, 0x10);
			int offset = raf.readInt() + 0x1E40020;
			int compressedLength = raf.readInt();
			int decompressedLength = raf.readInt();

			if (i == 0)
				System.out.println(String.format("%4d %-16s %8X %8X %8X", i, name, offset, compressedLength, decompressedLength));
			else {
				int prevEnd = prevOffset + prevLength;

				if (prevEnd < offset)
					System.out.printf("HOLE:  %8X -- %8X\n", prevEnd, offset);

				System.out.println(String.format("%4d %-16s %8X %8X %8X", i, name, offset, compressedLength, decompressedLength));
			}

			byte[] dumpedBytes;
			byte[] writeBytes;

			raf.seek(offset);
			if (raf.readInt() == 0x59617930) // "Yay0"
			{
				int yay0length = raf.readInt();
				assert (yay0length == decompressedLength);

				dumpedBytes = new byte[compressedLength];
				raf.seek(offset);
				raf.read(dumpedBytes);
				writeBytes = Yay0Helper.decode(dumpedBytes);

			}
			else {
				// texture assets are not Yay0 compressed
				dumpedBytes = new byte[decompressedLength];

				raf.seek(offset);
				raf.read(dumpedBytes);
				writeBytes = dumpedBytes;
			}

			prevOffset = offset;
			prevLength = compressedLength;

			//		File out = new File(Directories.DUMP_YAY0_ENCODED + name);
			//		FileUtils.writeByteArrayToFile(out, writeBytes);
		}

		// while !name.equals("end_data")

		raf.close();
	}
}

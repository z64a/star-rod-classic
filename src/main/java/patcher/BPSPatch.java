package patcher;

import java.io.ByteArrayOutputStream;
import java.util.zip.CRC32;

/**
 * Creates patches in the Beat Patch System (BPS) format.
 */
public final class BPSPatch
{
	private static final int SOURCE_READ = 0;
	private static final int TARGET_READ = 1;
	private static final int TARGET_COPY = 3;

	private static final int MINIMUM_COPY_LENGTH = 4;

	private BPSPatch()
	{}

	public static byte[] create(byte[] source, byte[] target)
	{
		PatchOutputStream out = new PatchOutputStream();
		out.write('B');
		out.write('P');
		out.write('S');
		out.write('1');
		writeNumber(out, source.length);
		writeNumber(out, target.length);
		writeNumber(out, 0); // no metadata

		int outputPos = 0;
		long targetRelativeOffset = 0;

		while (outputPos < target.length) {
			int sourceReadLength = getSourceReadLength(source, target, outputPos);
			if (sourceReadLength >= MINIMUM_COPY_LENGTH) {
				writeAction(out, SOURCE_READ, sourceReadLength);
				outputPos += sourceReadLength;
				continue;
			}

			int targetCopyLength = getTargetCopyLength(target, outputPos);
			if (targetCopyLength >= MINIMUM_COPY_LENGTH) {
				writeAction(out, TARGET_COPY, targetCopyLength);
				int copyPos = outputPos - 1;
				writeSignedNumber(out, copyPos - targetRelativeOffset);
				targetRelativeOffset = copyPos + targetCopyLength;
				outputPos += targetCopyLength;
				continue;
			}

			int readStart = outputPos++;
			while (outputPos < target.length) {
				sourceReadLength = getSourceReadLength(source, target, outputPos);
				targetCopyLength = getTargetCopyLength(target, outputPos);
				if (sourceReadLength >= MINIMUM_COPY_LENGTH || targetCopyLength >= MINIMUM_COPY_LENGTH)
					break;
				outputPos++;
			}

			int readLength = outputPos - readStart;
			writeAction(out, TARGET_READ, readLength);
			out.write(target, readStart, readLength);
		}

		writeChecksum(out, getChecksum(source));
		writeChecksum(out, getChecksum(target));
		writeChecksum(out, out.getChecksum());
		return out.toByteArray();
	}

	private static int getSourceReadLength(byte[] source, byte[] target, int pos)
	{
		int end = Math.min(source.length, target.length);
		int start = pos;
		while (pos < end && source[pos] == target[pos])
			pos++;
		return pos - start;
	}

	private static int getTargetCopyLength(byte[] target, int pos)
	{
		if (pos == 0 || target[pos] != target[pos - 1])
			return 0;

		int start = pos;
		byte value = target[pos];
		while (pos < target.length && target[pos] == value)
			pos++;
		return pos - start;
	}

	private static void writeAction(ByteArrayOutputStream out, int action, int length)
	{
		writeNumber(out, ((long) (length - 1) << 2) | action);
	}

	private static void writeSignedNumber(ByteArrayOutputStream out, long value)
	{
		long encoded = Math.abs(value) << 1;
		if (value < 0)
			encoded |= 1;
		writeNumber(out, encoded);
	}

	private static void writeNumber(ByteArrayOutputStream out, long value)
	{
		while (true) {
			int next = (int) (value & 0x7F);
			value >>= 7;
			if (value == 0) {
				out.write(0x80 | next);
				return;
			}
			out.write(next);
			value--;
		}
	}

	private static long getChecksum(byte[] data)
	{
		CRC32 crc = new CRC32();
		crc.update(data);
		return crc.getValue();
	}

	private static void writeChecksum(ByteArrayOutputStream out, long checksum)
	{
		for (int i = 0; i < 4; i++) {
			out.write((int) checksum & 0xFF);
			checksum >>= 8;
		}
	}

	private static class PatchOutputStream extends ByteArrayOutputStream
	{
		private final CRC32 crc = new CRC32();

		@Override
		public void write(int value)
		{
			super.write(value);
			crc.update(value);
		}

		@Override
		public void write(byte[] data, int offset, int length)
		{
			super.write(data, offset, length);
			crc.update(data, offset, length);
		}

		private long getChecksum()
		{
			return crc.getValue();
		}
	}
}

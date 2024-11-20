package patcher;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import app.Environment;
import app.StarRodException;
import app.config.Options;
import app.input.IOUtils;

public class RomPatcher
{
	// only allow patching the ROM indirectly, both to speed up writes and to perform
	// error-checking to prevent conflicts

	public static final int ROM_BASE = 0x02800000;
	public static final int RAM_BASE = 0x80400000;

	private final File file;
	private final ByteBuffer fileBuffer;
	private ConflictTree conflicts = new ConflictTree();
	private String currentSource = null;
	private int lastPos = 0;

	public RomPatcher(int bufferSize, File sourceFile) throws IOException
	{
		this.file = sourceFile;
		fileBuffer = ByteBuffer.allocateDirect(bufferSize);
		conflicts = new ConflictTree();

		ByteBuffer sourceBuffer = IOUtils.getDirectBuffer(sourceFile);
		fileBuffer.put(sourceBuffer);
		lastPos = fileBuffer.position();
	}

	public void print()
	{
		for (Interval in : conflicts.values())
			System.out.printf("%08X %08X %s%n", in.start, in.end, in.source);
	}

	public String getSourceName()
	{
		return currentSource;
	}

	public int toAddress(int offset)
	{
		return RAM_BASE + (offset - ROM_BASE);
	}

	//TODO be careful with this!
	public ByteBuffer getBuffer()
	{
		return fileBuffer;
	}

	public void writeFile() throws IOException
	{
		fileBuffer.limit(nextAlignedOffset());
		IOUtils.writeBufferToFile(fileBuffer, file);
	}

	public void seek(String source, int offset)
	{
		currentSource = source;
		fileBuffer.position(offset);
	}

	public void skip(int numBytes)
	{
		fileBuffer.position(fileBuffer.position() + numBytes);
	}

	public void padOut(int size)
	{
		int start = fileBuffer.position();
		int end;
		switch (size) {
			case 2:
				end = (start + 1) & -2;
				break;
			case 4:
				end = (start + 3) & -4;
				break;
			case 8:
				end = (start + 7) & -8;
				break;
			case 16:
				end = (start + 15) & -16;
				break;
			default:
				throw new IllegalStateException("Unsupported padding size: " + size);
		}
		byte[] padding = new byte[end - start];
		if (padding.length > 0)
			write(padding);
	}

	public int getCurrentOffset()
	{
		return fileBuffer.position();
	}

	/**
	 * Write a long and invalidate the region for future writes.
	 */
	public void writeLong(long l)
	{
		invalidate(fileBuffer.position(), fileBuffer.position() + 8);
		fileBuffer.putLong(l);
		lastPos = Math.max(lastPos, fileBuffer.position());
	}

	/**
	 * Write a 8-byte zero without invalidating the region for future writes.
	 */
	public void clearLong()
	{
		fileBuffer.putLong(0);
		lastPos = Math.max(lastPos, fileBuffer.position());
	}

	/**
	 * Write an int and invalidate the region for future writes.
	 */
	public void writeInt(int v)
	{
		invalidate(fileBuffer.position(), fileBuffer.position() + 4);
		fileBuffer.putInt(v);
		lastPos = Math.max(lastPos, fileBuffer.position());
	}

	/**
	 * Write a 4-byte zero without invalidating the region for future writes.
	 */
	public void clearInt()
	{
		fileBuffer.putInt(0);
		lastPos = Math.max(lastPos, fileBuffer.position());
	}

	public void writeShort(int s)
	{
		writeShort((short) s);
	}

	/**
	 * Write a short and invalidate the region for future writes.
	 */
	public void writeShort(short s)
	{
		invalidate(fileBuffer.position(), fileBuffer.position() + 2);
		fileBuffer.putShort(s);
		lastPos = Math.max(lastPos, fileBuffer.position());
	}

	/**
	 * Write a 2-byte zero without invalidating the region for future writes.
	 */
	public void clearShort()
	{
		fileBuffer.putShort((short) 0);
		lastPos = Math.max(lastPos, fileBuffer.position());
	}

	public void writeMessage(String msg)
	{
		// always have one extra byte for \0 and pad to 4 bytes
		int len = (msg.length() + 4) & -4;
		write(msg.getBytes());
		int padding = len - msg.length();
		if (padding > 0)
			write(new byte[padding]);
	}

	public void writeByte(int b)
	{
		writeByte((byte) b);
	}

	/**
	 * Write a byte and invalidate the region for future writes.
	 */
	public void writeByte(byte b)
	{
		invalidate(fileBuffer.position(), fileBuffer.position() + 1);
		fileBuffer.put(b);
		lastPos = Math.max(lastPos, fileBuffer.position());
	}

	/**
	 * Write a 1-byte zero without invalidating the region for future writes.
	 */
	public void clearByte()
	{
		fileBuffer.put((byte) 0);
		lastPos = Math.max(lastPos, fileBuffer.position());
	}

	/**
	 * Write a double and invalidate the region for future writes.
	 */
	public void writeDouble(double d)
	{
		invalidate(fileBuffer.position(), fileBuffer.position() + 8);
		fileBuffer.putDouble(d);
		lastPos = Math.max(lastPos, fileBuffer.position());
	}

	/**
	 * Write a float and invalidate the region for future writes.
	 */
	public void writeFloat(float f)
	{
		invalidate(fileBuffer.position(), fileBuffer.position() + 4);
		fileBuffer.putFloat(f);
		lastPos = Math.max(lastPos, fileBuffer.position());
	}

	/**
	 * Write the contents of a ByteBuffer and invalidate the region for future writes.
	 */
	public void write(ByteBuffer bb)
	{
		bb.rewind();
		invalidate(fileBuffer.position(), fileBuffer.position() + bb.limit());
		fileBuffer.put(bb);
		lastPos = Math.max(lastPos, fileBuffer.position());
	}

	/**
	 * Write the contents of a ByteBuffer and invalidate the region for future writes.
	 */
	public void write(byte[] bytes)
	{
		if (bytes.length == 0)
			return;
		invalidate(fileBuffer.position(), fileBuffer.position() + bytes.length);
		fileBuffer.put(bytes);
		lastPos = Math.max(lastPos, fileBuffer.position());
	}

	public void write(byte[] bytes, int offset, int length)
	{
		if (bytes.length == 0)
			return;
		invalidate(fileBuffer.position(), fileBuffer.position() + length);
		fileBuffer.put(bytes, offset, length);
		lastPos = Math.max(lastPos, fileBuffer.position());
	}

	public void read(byte[] dst)
	{
		fileBuffer.get(dst);
	}

	public void read(byte[] dst, int offset)
	{
		int pos = fileBuffer.position();
		fileBuffer.get(dst);
		fileBuffer.position(pos);
	}

	public int readByte()
	{
		return fileBuffer.get();
	}

	public int readShort()
	{
		return fileBuffer.getShort();
	}

	public int readInt()
	{
		return fileBuffer.getInt();
	}

	public void clear(int start, int end)
	{
		Interval conflict = conflicts.getConflict(start, end);
		if (conflict != null)
			throw new StarRodException("%s cannot clear (%X,%X) due to conflict from %s at (%X,%X)",
				currentSource, start, end, conflict.source, conflict.start, conflict.end);

		byte[] zeros = new byte[end - start];
		fileBuffer.position(start);
		fileBuffer.put(zeros);
	}

	private void invalidate(int start, int end)
	{
		//	System.out.println("Invalidating " + new Region(start,end));

		Interval conflict = conflicts.add(currentSource, start, end);
		if (conflict != null) {
			String errorMessage = String.format(
				"%s cannot write %X bytes to (%X,%X) due to conflict from %s at (%X,%X)",
				currentSource, end - start, start, end, conflict.source, conflict.start, conflict.end);

			if (!Environment.project.config.getBoolean(Options.AllowWriteConflicts))
				//	Logger.logWarning(errorMessage);
				//else
				throw new StarRodException(errorMessage);
		}
	}

	public int tailReserveWithPadding(int bytes)
	{
		lastPos = nextAlignedOffset();
		lastPos += bytes;
		return lastPos;
	}

	public int nextAlignedOffset()
	{
		return (lastPos + 15) & -16;
	}

	public int nextAlignedOffset(int size)
	{
		// assumes power of two!
		// return (lastPos + (size - 1)) & -size;

		switch (size) {
			case 2:
				return (lastPos + 1) & -2;
			case 4:
				return (lastPos + 3) & -4;
			case 8:
				return (lastPos + 7) & -8;
			case 16:
				return (lastPos + 15) & -16;
			default:
				throw new IllegalStateException("Unsupported padding size: " + size);
		}
	}

	public int getEndOffset()
	{
		return lastPos;
	}
}

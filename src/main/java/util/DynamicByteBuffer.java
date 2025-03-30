package util;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class DynamicByteBuffer
{
	private ByteBuffer buffer;

	public DynamicByteBuffer(int initialCapacity)
	{
		buffer = ByteBuffer.allocateDirect(initialCapacity);
	}

	public DynamicByteBuffer()
	{
		this(128);
	}

	public void putByte(int v)
	{
		putByte((byte) v);
	}

	public void putByte(byte b)
	{
		ensureCapacity(1);
		buffer.put(b);
	}

	public void putShort(int v)
	{
		putShort((short) v);
	}

	public void putShort(short s)
	{
		ensureCapacity(Short.BYTES);
		buffer.putShort(s);
	}

	public void putInt(int v)
	{
		ensureCapacity(Integer.BYTES);
		buffer.putInt(v);
	}

	public void putFloat(float f)
	{
		ensureCapacity(Float.BYTES);
		buffer.putFloat(f);
	}

	public void putDouble(double d)
	{
		ensureCapacity(Double.BYTES);
		buffer.putDouble(d);
	}

	public void putBytes(byte[] bytes)
	{
		ensureCapacity(bytes.length);
		buffer.put(bytes);
	}

	public void put(ByteBuffer other)
	{
		ByteBuffer clone = other.duplicate();
		clone.rewind();

		int len = clone.remaining();

		ensureCapacity(len);
		buffer.put(clone);
	}

	public void putUTF8(String str, boolean nullTerminate)
	{
		if (str == null) {
			if (nullTerminate)
				putByte((byte) 0);
			return;
		}

		byte[] encoded = str.getBytes(StandardCharsets.UTF_8);
		putBytes(encoded);

		if (nullTerminate)
			putByte((byte) 0);
	}

	public void clear()
	{
		buffer.clear();
	}

	public int capacity()
	{
		return buffer.capacity();
	}

	public int position()
	{
		return buffer.position();
	}

	public void position(int newPosition)
	{
		if (newPosition > buffer.capacity()) {
			resizeBuffer(Math.round(newPosition * 2));
		}

		if (newPosition > buffer.limit()) {
			buffer.limit(newPosition);
		}

		buffer.position(newPosition);
	}

	public void skip(int numBytes)
	{
		position(buffer.position() + numBytes);
	}

	public void align(int padSize)
	{
		int curPos = buffer.position();
		int alignPos = curPos;

		if (curPos % padSize != 0) {
			alignPos += padSize - (curPos % padSize);
		}

		position(alignPos);
	}

	public int size()
	{
		return buffer.limit();
	}

	private void ensureCapacity(int additionalCapacity)
	{
		int requiredCapacity = buffer.position() + additionalCapacity;

		if (requiredCapacity > buffer.capacity()) {
			resizeBuffer(Math.max(buffer.capacity() * 2, requiredCapacity));
		}

		if (requiredCapacity > buffer.limit()) {
			buffer.limit(requiredCapacity);
		}
	}

	private void resizeBuffer(int newCapacity)
	{
		int currentPosition = buffer.position();
		int currentLimit = buffer.limit();

		ByteBuffer newBuffer = ByteBuffer.allocateDirect(newCapacity);

		buffer.rewind();
		newBuffer.put(buffer);

		buffer = newBuffer;
		buffer.position(currentPosition);
		buffer.limit(currentLimit);
	}

	public ByteBuffer getFixedBuffer()
	{
		return getFixedBuffer(1); // no padding
	}

	public ByteBuffer getFixedBuffer(int padSize)
	{
		int actualSize = buffer.limit();
		int finalSize = actualSize;

		if (actualSize % padSize != 0) {
			finalSize += padSize - (actualSize % padSize);
		}

		ByteBuffer outBuffer = ByteBuffer.allocateDirect(finalSize);

		int currentPosition = buffer.position();
		int currentLimit = buffer.limit();

		buffer.rewind();
		outBuffer.put(buffer);

		buffer.position(currentPosition);
		buffer.limit(currentLimit);

		outBuffer.position(0);
		outBuffer.limit(actualSize);

		return outBuffer;
	}
}

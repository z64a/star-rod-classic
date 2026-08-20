package input.patch;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;

import org.junit.jupiter.api.Test;

import game.shared.decoder.Pointer;
import game.shared.struct.other.StringMarkup;
import game.string.StringDecoder;
import game.string.StringEncoder;

public class PatchStringDecodingTest
{
	@Test
	public void pauseArgumentDecodesAsUnsigned() throws Exception
	{
		assertRoundTrip("[Pause 200][End]", "[Pause 200]");
	}

	@Test
	public void speedArgumentsDecodeAsUnsigned() throws Exception
	{
		assertRoundTrip("[Speed delay=200 chars=201][End]", "[Speed delay=200 chars=201]");
	}

	@Test
	public void volumePercentageRoundTripsEveryRawValue() throws Exception
	{
		for (int rawValue = 0; rawValue <= 0xFF; rawValue++) {
			byte[] original = bytes(StringEncoder.encode(String.format("[Volume %d][End]", rawValue)));
			String markup = StringDecoder.toMarkup(original);
			byte[] encoded = bytes(StringEncoder.encode(markup));

			assertTrue(markup.contains("[Volume percent="), "Decoded raw volume " + rawValue + ": " + markup);
			assertArrayEquals(original, encoded, "Raw volume " + rawValue + " did not round trip through " + markup);
		}
	}

	@Test
	public void stringScannerReadsPast256BytesWithoutSkipping() throws Exception
	{
		StringBuilder source = new StringBuilder();
		for (int i = 0; i < 257; i++)
			source.append('A');
		source.append("[End]");

		byte[] encoded = bytes(StringEncoder.encode(source.toString()));
		int alignedSize = (encoded.length + 3) & -4;
		ByteBuffer fileBuffer = ByteBuffer.allocate(alignedSize);
		fileBuffer.put(encoded);
		fileBuffer.rewind();

		Pointer ptr = new Pointer(0x80000000);
		StringMarkup.instance.scan(null, ptr, fileBuffer);

		assertEquals(source.toString(), ptr.text);
		assertEquals(alignedSize, fileBuffer.position());
	}

	private static void assertRoundTrip(String source, String expectedTag) throws Exception
	{
		byte[] original = bytes(StringEncoder.encode(source));
		String markup = StringDecoder.toMarkup(original);

		assertTrue(markup.contains(expectedTag), markup);
		assertArrayEquals(original, bytes(StringEncoder.encode(markup)));
	}

	private static byte[] bytes(ByteBuffer buffer)
	{
		byte[] bytes = new byte[buffer.remaining()];
		buffer.get(bytes);
		return bytes;
	}
}

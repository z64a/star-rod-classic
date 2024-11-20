package util.japanese;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;

public class JapaneseHelper
{
	public static String convertSJIStoUTF8(byte[] sjisBytes)
	{
		return convertSJIStoUTF8(ByteBuffer.wrap(sjisBytes));
	}

	public static String convertSJIStoUTF8(ByteBuffer sjisBuffer)
	{
		Charset sjis = Charset.forName("Shift-JIS");
		Charset utf8 = Charset.forName("UTF-8");

		CharBuffer decodedCB = sjis.decode(sjisBuffer);
		ByteBuffer outBB = utf8.encode(decodedCB);
		byte[] outArray = new byte[outBB.limit()];
		outBB.get(outArray);

		return new String(outArray, utf8);
	}

	public static String convertSJIStoUTF16(byte[] sjisBytes)
	{
		return convertSJIStoUTF16(ByteBuffer.wrap(sjisBytes));
	}

	public static String convertSJIStoUTF16(ByteBuffer sjisBuffer)
	{
		Charset sjis = Charset.forName("Shift-JIS");
		Charset utf16 = Charset.forName("UTF-16");

		CharBuffer decodedCB = sjis.decode(sjisBuffer);
		ByteBuffer outBB = utf16.encode(decodedCB);
		byte[] outArray = new byte[outBB.limit()];
		outBB.get(outArray);

		return new String(outArray, utf16);
	}

	public static String convertSJIStoRomaji(byte[] sjisBytes)
	{
		return convertSJIStoRomaji(ByteBuffer.wrap(sjisBytes));
	}

	public static String convertSJIStoRomaji(ByteBuffer sjisBuffer)
	{
		String japanese = convertSJIStoUTF8(sjisBuffer);
		WanaKanaJava wk = new WanaKanaJava();
		return wk.toRomaji(japanese);
	}
}

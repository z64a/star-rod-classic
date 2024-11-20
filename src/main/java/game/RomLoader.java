package game;

import java.io.File;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class RomLoader
{
	private static String errorMsg = "";

	public static enum ByteOrder
	{
		BIG,
		BYTESWAP,
		LITTLE,
		WORDSWAP
	}

	public static ByteOrder checkByteOrder(ByteBuffer bb)
	{
		bb.rewind();
		if (bb.remaining() < 0x1000) {
			errorMsg = "File is too small!";
			return null;
		}

		int endianCheck = bb.getInt();
		switch (endianCheck) {
			case 0x80371240:
				return ByteOrder.BIG; // z64 ABCD
			case 0x40123780:
				return ByteOrder.LITTLE; // n64 DCBA
			case 0x37804012:
				return ByteOrder.BYTESWAP; // v64 BADC
			case 0x12408037:
				return ByteOrder.WORDSWAP; // x64 CDAB

			default:
				errorMsg = "File is not a valid N64 ROM.";
				return null;
		}
	}

	public static void toNative(ByteBuffer bb, ByteOrder order)
	{
		errorMsg = "no error";
		bb.rewind();
		int pos = 0;
		switch (order) {
			case BIG:
				return;

			case LITTLE: // DCBA --> ABCD
				while (bb.remaining() > 4) {
					byte A = bb.get();
					byte B = bb.get();
					byte C = bb.get();
					byte D = bb.get();
					bb.position(pos);
					bb.put(D);
					bb.put(C);
					bb.put(B);
					bb.put(A);
					pos += 4;
				}
				return;

			case BYTESWAP: // BADC --> ABCD
				while (bb.remaining() > 4) {
					byte A = bb.get();
					byte B = bb.get();
					byte C = bb.get();
					byte D = bb.get();
					bb.position(pos);
					bb.put(B);
					bb.put(A);
					bb.put(D);
					bb.put(C);
					pos += 4;
				}
				return;

			case WORDSWAP: // CDAB --> ABCD
				while (bb.remaining() > 4) {
					byte A = bb.get();
					byte B = bb.get();
					byte C = bb.get();
					byte D = bb.get();
					bb.position(pos);
					bb.put(C);
					bb.put(D);
					bb.put(A);
					bb.put(B);
					pos += 4;
				}
				return;
		}
	}

	public static ROM tryLoadingROM(ByteBuffer bb, File databaseDir)
	{
		bb.rewind();
		if (bb.remaining() < 0x1000) {
			errorMsg = "File is too small!";
			return null;
		}

		int endianCheck = bb.getInt();
		if (endianCheck != 0x80371240) {
			errorMsg = "File is not in big endian byte order.";
			return null;
		}

		String MD5 = getMD5(bb);

		if (MD5.equals(ROM_US.MD5))
			return new ROM_US(databaseDir);
		else if (MD5.equals(ROM_JP.MD5))
			return new ROM_JP(databaseDir);
		else if (MD5.equals(ROM_PAL.MD5))
			return new ROM_PAL(databaseDir);
		else if (MD5.equals(ROM_CN.MD5))
			return new ROM_CN(databaseDir);

		errorMsg = "File MD5 does not match a valid ROM: " + MD5;
		return null;
	}

	private static String getMD5(ByteBuffer bb)
	{
		try {
			bb.rewind();
			MessageDigest md = MessageDigest.getInstance("MD5");

			md.update(bb);
			byte[] hashedBytes = md.digest();

			StringBuilder sb = new StringBuilder(2 * hashedBytes.length);
			for (byte b : hashedBytes)
				sb.append(String.format("%02X", b));
			return sb.toString();
		}
		catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

	public static String getError()
	{
		return errorMsg;
	}
}

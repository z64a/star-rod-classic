package game.shared;

import java.io.IOException;
import java.nio.ByteBuffer;

import app.input.InvalidInputException;
import patcher.RomPatcher;

public class DataUtils
{
	public static boolean isPointerFmt(String s)
	{
		return (s.length() > 1) && (s.charAt(0) == SyntaxConstants.POINTER_PREFIX);
	}

	public static boolean isConstantFmt(String s)
	{
		return (s.length() > 1) && (s.charAt(0) == SyntaxConstants.CONSTANT_PREFIX);
	}

	public static boolean isScriptVarFmt(String s)
	{
		return (s.length() > 1) && (s.charAt(0) == SyntaxConstants.SCRIPT_VAR_PREFIX);
	}

	public static boolean isInteger(String s)
	{
		try {
			if (s.endsWith("`") || s.endsWith("'"))
				Long.parseLong(s.substring(0, s.length() - 1));
			else
				Long.parseLong(s, 16);
		}
		catch (NumberFormatException e) {
			return false;
		}
		return true;
	}

	public static int parseIntString(String s) throws InvalidInputException
	{
		try {
			if (s.endsWith("`") || s.endsWith("'"))
				return (int) Long.parseLong(s.substring(0, s.length() - 1));
			else
				return (int) Long.parseLong(s, 16);
		}
		catch (NumberFormatException e) {
			throw new InvalidInputException(e);
		}
	}

	public static int getSize(String s)
	{
		char suffix = s.charAt(s.length() - 1);

		switch (suffix) {
			case 'b':
				return 1;
			case 's':
				return 2;
			case 'd':
				return 8;
			default:
				return 4;
		}
	}

	public static String combineTokens(String[] tokens)
	{
		return combineTokens(tokens, " ");
	}

	public static String combineTokens(String[] tokens, String delimiter)
	{
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < tokens.length - 1; i++)
			sb.append(tokens[i]).append(delimiter);
		sb.append(tokens[tokens.length - 1]);
		return sb.toString();
	}

	public static void addToBuffer(ByteBuffer buffer, String s) throws InvalidInputException
	{
		char suffix = s.charAt(s.length() - 1);
		String trimmed = s.substring(0, s.length() - 1);

		if (s.contains(".")) {
			switch (suffix) {
				case 's':
					buffer.putShort((short) (Float.parseFloat(trimmed) * 32767.0f));
					break;
				case 'd':
					buffer.putDouble(Double.parseDouble(trimmed));
					break;
				default:
					buffer.putFloat(Float.parseFloat(s));
					break;
			}
		}
		else {
			switch (suffix) {
				case 'b':
					buffer.put((byte) DataUtils.parseIntString(trimmed));
					break;
				case 's':
					buffer.putShort((short) DataUtils.parseIntString(trimmed));
					break;
				default:
					buffer.putInt(DataUtils.parseIntString(s));
					break;
			}
		}
	}

	public static void writeWord(RomPatcher rp, String s) throws IOException, InvalidInputException
	{
		char suffix = s.charAt(s.length() - 1);
		String trimmed = s.substring(0, s.length() - 1);

		if (s.contains(".")) {
			switch (suffix) {
				case 's':
					rp.writeShort((short) (Float.parseFloat(trimmed) * 32767.0f));
					break;
				case 'd':
					rp.writeDouble(Double.parseDouble(trimmed));
					break;
				default:
					rp.writeFloat(Float.parseFloat(s));
					break;
			}
		}
		else {
			switch (suffix) {
				case 'b':
					rp.writeByte(parseIntString(trimmed));
					break;
				case 's':
					rp.writeShort(parseIntString(trimmed));
					break;
				default:
					rp.writeInt(parseIntString(s));
			}
		}
	}
}

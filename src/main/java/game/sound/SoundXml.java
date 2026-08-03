package game.sound;

import org.w3c.dom.Element;

import util.xml.XmlKey;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public final class SoundXml
{
	private SoundXml()
	{}

	public static int readInt(XmlReader reader, Element element, XmlKey key, int min, int max)
	{
		reader.requiresAttribute(element, key);
		return checkRange(reader, key, reader.readInt(element, key), min, max);
	}

	public static int readInt(XmlReader reader, Element element, XmlKey key, int defaultValue, int min, int max)
	{
		if (!reader.hasAttribute(element, key))
			return defaultValue;
		return checkRange(reader, key, reader.readInt(element, key), min, max);
	}

	public static int readHex(XmlReader reader, Element element, XmlKey key, int min, int max)
	{
		reader.requiresAttribute(element, key);
		String text = reader.getAttribute(element, key);
		if (!text.matches("-?[0-9A-Fa-f]+"))
			reader.complain(key + " must be a hexadecimal value: " + text);

		long value = 0;
		try {
			value = Long.parseLong(text, 16);
		}
		catch (NumberFormatException e) {
			reader.complain("Invalid hexadecimal value for " + key + ": " + text);
		}

		if (value < min || value > max) {
			reader.complain(String.format("%s must be between %s and %s: %s", key, formatHex(min), formatHex(max), text));
		}
		return (int) value;
	}

	public static void addHex(XmlWriter writer, XmlTag tag, XmlKey key, int width, int value)
	{
		writer.addAttribute(tag, key, formatHex(width, value));
	}

	public static String formatHex(int width, int value)
	{
		if (width < 1)
			throw new IllegalArgumentException("Hexadecimal field width must be positive");
		String text = String.format("%0" + width + "X", Math.abs((long) value));
		return value < 0 ? "-" + text : text;
	}

	private static int checkRange(XmlReader reader, XmlKey key, int value, int min, int max)
	{
		if (value < min || value > max)
			reader.complain(key + " must be between " + min + " and " + max + ": " + value);
		return value;
	}

	private static String formatHex(int value)
	{
		String text = String.format("%X", Math.abs((long) value));
		return value < 0 ? "-" + text : text;
	}
}

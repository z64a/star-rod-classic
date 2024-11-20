package game.shared;

import java.io.PrintWriter;
import java.nio.ByteBuffer;

import game.shared.decoder.BaseDataDecoder;

public class StructField
{
	public final String name;
	public final String comment;
	public final int offset;
	public final int length;
	private final Style style;
	private final boolean decimal;

	public static enum Style
	{
		Bytes,
		Shorts,
		Ints
	}

	public StructField(String name, int offset, int length, Style style, boolean decimal)
	{
		this(name, offset, length, style, decimal, "");
	}

	public StructField(String name, int offset, int length, Style style, boolean decimal, String comment)
	{
		this.name = name;
		this.comment = comment;
		this.offset = offset;
		this.length = length;
		this.style = style;
		this.decimal = decimal;
	}

	public void print(BaseDataDecoder decoder, ByteBuffer bb, PrintWriter pw)
	{
		bb.position(offset);
		String format = decimal ? "d`" : "X";
		pw.printf("%-13s ", "[" + name + "]");

		switch (style) {
			case Bytes:
				for (int i = 0; i < length; i++)
					pw.printf("%3" + format + "b ", bb.get());
				break;
			case Shorts:
				for (int i = 0; i < length; i += 2)
					pw.printf("%3" + format + "s ", bb.getShort());
				break;
			case Ints:
				for (int i = 0; i < length; i += 4)
					pw.printf("%s ", decoder.getVariableName(bb.getInt()));
				break;
		}

		if (comment.isEmpty())
			pw.println();
		else
			pw.println("% " + comment);
	}
}

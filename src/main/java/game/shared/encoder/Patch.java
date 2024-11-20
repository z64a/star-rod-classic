package game.shared.encoder;

import java.util.ArrayList;
import java.util.List;

import app.input.InputFileException;
import app.input.InvalidInputException;
import app.input.Line;
import game.shared.DataUtils;
import game.shared.struct.Struct;

public class Patch
{
	public static final int UNBOUNDED = -1;

	public final Line sourceLine;
	// identifies this patch among other patches applied to the same struct; uniqueness not guaranteed
	public final String offsetIdentifier;
	public final List<Line> lines;

	public final Struct struct;
	public final int maxLength;

	public int startingPos;
	public int length = -1;

	public List<String> annotations = new ArrayList<>(0);

	/*
	public String name;
	public PatchType type;
	public LibScope scope = LibScope.US_Common;
	public boolean isNew = true;

	public int address = -1;

	public int fileOffset;
	public int endFileOffset; // used for @Fill
	public int reservedSize; // used for #reserve

	// for patches onto reserved space
	public GlobalPatch target = null;
	public int targetOffset = 0;

	public Subscription subscription = null;

	// used for offsets into functions
	public HashMap<String,Integer> labelMap = null;
	*/

	private Patch(Line sourceLine, String identifier, Struct struct, int startingPos, int maxLength)
	{
		this.sourceLine = sourceLine;
		this.struct = struct;
		this.offsetIdentifier = identifier;
		this.startingPos = startingPos;
		lines = new ArrayList<>();
		this.maxLength = maxLength;
	}

	public static Patch create(Line sourceLine, Struct struct)
	{
		return new Patch(sourceLine, "", struct, 0, UNBOUNDED);
	}

	public static Patch create(Line sourceLine, Struct struct, int baseOffset)
	{
		return new Patch(sourceLine, baseOffset + "", struct, baseOffset, UNBOUNDED);
	}

	public static Patch create(Line sourceLine, Struct struct, String offsetString)
	{
		try {
			return new Patch(sourceLine, offsetString, struct, struct.basePatchOffset + DataUtils.parseIntString(offsetString), -1); //XXX -1?
		}
		catch (InvalidInputException e) {
			throw new InputFileException(sourceLine, "Could not parse offset: " + offsetString);
		}
	}

	public static Patch createBounded(Line sourceLine, String identifier, Struct struct, int startingPos, int maxLength)
	{
		return new Patch(sourceLine, identifier, struct, struct.basePatchOffset + startingPos, maxLength);
	}

	public static Patch createGlobal(Line sourceLine, Struct struct, int baseOffset)
	{
		return new Patch(sourceLine, baseOffset + "", struct, baseOffset, UNBOUNDED);
	}

	public void print()
	{
		System.out.println(offsetIdentifier);
		for (Line line : lines)
			line.print();
		System.out.println();
	}
}

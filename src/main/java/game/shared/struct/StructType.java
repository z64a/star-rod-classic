package game.shared.struct;

import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.util.List;

import app.input.Line;
import game.shared.BaseStruct;
import game.shared.StructField;
import game.shared.StructTypes;
import game.shared.decoder.BaseDataDecoder;
import game.shared.decoder.IPrint;
import game.shared.decoder.IScan;
import game.shared.decoder.Pointer;
import game.shared.encoder.BaseDataEncoder;
import game.shared.encoder.IParseField;
import game.shared.encoder.IPatchLength;
import game.shared.encoder.IReplaceExpression;
import game.shared.encoder.IReplaceSpecial;
import game.shared.encoder.Patch;

/**
 * Defines a type of data structure as a loose collection of methods used
 * by the Encoder and Decoder objects. These are intended to be pseudo-
 * singletons. Only one should exist for each type.<BR>
 * <BR>
 * Shared types are enumerated in {@link StructTypes}. Map and battle
 * structs are enumerated similarly in their respective packages. The decoder
 * classes bind functional interfaces for decoding, while the encoder classes
 * bind functional interfaces for encoding. Default methods are provided for
 * all functional interfaces.<BR>
 * <BR>
 * If a StructType is derived from another, it will inherit the functional
 * interfaces of the super type unless they are explicitly overridden.
 */
public class StructType
{
	// option flags
	public static final int UNIQUE = 1;
	public static final int ARRAY = 2;

	private final String name;
	private final StructType parent;

	public IScan scanner = BaseStruct.instance;
	public IPrint printer = BaseStruct.instance;

	public IParseField parseOffset = BaseStruct.instance;
	public IReplaceExpression replaceExpression = BaseStruct.instance;
	public IReplaceSpecial replaceConstants = BaseStruct.instance;
	public IPatchLength patchLength = BaseStruct.instance;

	public final boolean hasKnownSize;
	public final boolean isUnique;
	public final boolean isArray;
	public final int sizeOf;

	public int count = 0;

	public StructType(TypeMap types, String name)
	{
		this(types, -1, name, 0);
	}

	public StructType(TypeMap types, int size, String name)
	{
		this(types, size, name, 0);
	}

	public StructType(TypeMap types, String name, int flags)
	{
		this(types, -1, name, flags);
	}

	public StructType(TypeMap types, int size, String name, int flags)
	{
		this.name = name;
		this.name.hashCode(); // cache hashcode for faster access
		this.sizeOf = size;

		parent = null;

		this.hasKnownSize = (size != -1);
		this.isUnique = (flags & UNIQUE) != 0;
		this.isArray = (flags & ARRAY) != 0;

		types.add(this);
	}

	public StructType(TypeMap types, String name, StructType parent)
	{
		this(types, -1, name, parent, 0);
	}

	public StructType(TypeMap types, int size, String name, StructType parent)
	{
		this(types, size, name, parent, 0);
	}

	public StructType(TypeMap types, String name, StructType parent, int flags)
	{
		this(types, -1, name, parent, flags);
	}

	public StructType(TypeMap types, int size, String name, StructType parent, int flags)
	{
		this.name = name;
		this.name.hashCode(); // cache hashcode for faster access
		this.sizeOf = size;

		if (parent.isUnique)
			throw new IllegalArgumentException("StructType " + name + " cannot extend unique parent " + parent);

		// use parent's implementation unless this are explicitly overridden
		this.parent = parent;
		scanner = null;
		printer = null;
		parseOffset = null;
		replaceExpression = null;
		replaceConstants = null;
		patchLength = null;

		this.hasKnownSize = (size != -1);
		this.isUnique = (flags & UNIQUE) != 0;
		this.isArray = (flags & ARRAY) != 0;

		types.add(this);
	}

	public void bind(BaseStruct type)
	{
		this.scanner = type;
		this.printer = type;
		this.parseOffset = type;
		this.replaceExpression = type;
		this.replaceConstants = type;
		this.patchLength = type;
	}

	@Override
	public String toString()
	{
		return name;
	}

	/**
	 * Decoding functional interfaces
	 */

	public void scan(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer)
	{
		if (parent != null && scanner == null)
			parent.scan(decoder, ptr, fileBuffer);
		else
			scanner.scan(decoder, ptr, fileBuffer);
	}

	public void print(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer, PrintWriter pw)
	{
		if (parent != null && printer == null)
			parent.print(decoder, ptr, fileBuffer, pw);
		else
			printer.print(decoder, ptr, fileBuffer, pw);
	}

	/**
	 * Encoding functional interfaces
	 */

	public StructField parseFieldOffset(BaseDataEncoder encoder, Struct struct, String offsetName)
	{
		if (parent != null && parseOffset == null)
			return parent.parseFieldOffset(encoder, struct, offsetName);
		else
			return parseOffset.parseFieldOffset(encoder, struct, offsetName);
	}

	/**
	 * Some structs, such as NPCs, have custom expressions.
	 */
	public void replaceExpression(Line sourceLine, BaseDataEncoder encoder, String[] args, List<String> newTokenList)
	{
		if (parent != null && parseOffset == null)
			parent.replaceExpression(sourceLine, encoder, args, newTokenList);
		else
			replaceExpression.replaceExpression(sourceLine, encoder, args, newTokenList);
	}

	/**
	 * Used to replace special, struct-specific keywords, constants, and characters.
	 * Exs: script keywords, converting ASCII strings to hex
	 */
	public void replaceSpecial(BaseDataEncoder encoder, Patch patch)
	{
		if (parent != null && parseOffset == null)
			parent.replaceSpecial(encoder, patch);
		else
			replaceConstants.replaceStructConstants(encoder, patch);
	}

	public int getPatchLength(BaseDataEncoder encoder, Patch patch)
	{
		if (parent != null && parseOffset == null)
			return parent.getPatchLength(encoder, patch);
		else
			return patchLength.getPatchLength(encoder, patch);
	}

	public boolean isTypeOf(StructType t)
	{
		if (this == t)
			return true;

		if (parent == null)
			return false;
		else
			return parent.isTypeOf(t);
	}

	public final Integer getSizeOf()
	{
		if (sizeOf <= 0)
			return null;
		return sizeOf;
	}

	public final boolean isValidSize(int proposed)
	{
		if (sizeOf < 0)
			return true;
		if (isArray)
			return (proposed >= sizeOf) && (proposed % sizeOf == 0);
		else
			return proposed == sizeOf;
	}
}

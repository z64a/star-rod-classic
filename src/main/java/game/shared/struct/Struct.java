package game.shared.struct;

import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeMap;

import app.input.Line;
import game.ROM.LibScope;
import game.shared.StructField;
import game.shared.encoder.BaseDataEncoder;
import game.shared.encoder.Patch;
import util.CaseInsensitiveMap;

/**
 * These objects are the basic building blocks used by the encoding process. Each represents
 * a data structure, either dumped from the ROM or declared in the patch file.<BR>
 * <BR>
 * Some structs have patches applied to them by the mod author. These are recorded in a list
 * of {@link Patch} objects, which operate on these structs to produce the final modded data.
 */
public class Struct
{
	/*
	public static enum Status
	{
		New,		// no original struct
		Existing,	// has an original size, but no patches
		Modified,	// has an original size and has been patched
		Replaced	// had an original size, overriden by patches
	//	Deleted		// removed
	}

	public Status status;
	*/

	public static enum StructGenus
	{
		// @formatter:off
		New,		// newly declared, either local or global, will be placed in relevant free memory
		Overlay,	// existing struct from an overlay -- has a fixed address in the overlay, maybe be relocated
		Fixed,		// existing struct from the rom -- has a fixed rom offset
		Hook		// special type of global having a fixed rom offset for the hook and a body to place in free memory
		// @formatter:on
	}

	public final StructGenus gens;

	public final String name;
	public final String namespace;
	public final StructType type;

	public boolean replaceExisting = false;
	public boolean hasFixedSize = false;

	public final int originalFileOffset;
	public final int originalAddress;
	public final int originalSize;

	public boolean deleted = false;
	public boolean forceDelete = false;

	// do the patches applied to this struct make it larger than its original size?
	public boolean extended = false;
	public boolean shrunken = false;

	public int finalFileOffset;
	public int finalAddress;
	public int finalSize;

	public boolean patched = false;
	public final List<Patch> patchList = new LinkedList<>();
	public ByteBuffer patchedBuffer;

	public int basePatchOffset = 0;

	public boolean validateScriptSyntax = true;

	// used for structs that change size and shape during compilation (ie, scripts with opcode packing)
	public TreeMap<Integer, Integer> offsetMorphMap = null;

	// used for fields in structs and offsets into functions
	public CaseInsensitiveMap<Integer> labelMap = null;

	// string literals
	public Line literalOrigin;

	// used with @fill patches
	public boolean fillMode = false;

	// globals
	public boolean exported;
	public final LibScope scope;

	public static final int NO_VALUE = -1;

	public static Struct createNew(StructType type, LibScope scope, String name, String namespace)
	{
		Struct s = new Struct(StructGenus.New, type, scope, name, namespace, NO_VALUE, NO_VALUE, NO_VALUE);

		if (type.hasKnownSize) {
			s.hasFixedSize = true;
			s.finalSize = type.sizeOf;
		}

		return s;
	}

	public static Struct createOverlay(StructType type, LibScope scope, String name, String namespace, int address, int offset, int size)
	{
		Struct s = new Struct(StructGenus.Overlay, type, scope, name, namespace, address, offset, size);

		s.finalFileOffset = s.originalFileOffset;
		s.finalAddress = s.originalAddress;
		s.finalSize = s.originalSize;

		if (type.hasKnownSize) {
			s.hasFixedSize = true;
			s.finalSize = type.sizeOf;
		}

		return s;
	}

	public static Struct createFixed(StructType type, LibScope scope, String name, int romOffset)
	{
		Struct s = new Struct(StructGenus.Fixed, type, scope, name, "", NO_VALUE, romOffset, NO_VALUE);

		s.finalFileOffset = s.originalFileOffset;
		s.finalAddress = s.originalAddress;
		s.finalSize = s.originalSize;

		if (type.hasKnownSize) {
			s.hasFixedSize = true;
			s.finalSize = type.sizeOf;
		}

		return s;
	}

	public static Struct createFixed(StructType type, LibScope scope, String name, int romOffset, int size)
	{
		Struct s = new Struct(StructGenus.Fixed, type, scope, name, "", NO_VALUE, romOffset, size);

		s.finalFileOffset = s.originalFileOffset;
		s.finalAddress = s.originalAddress;
		s.finalSize = s.originalSize;

		if (size != NO_VALUE) {
			s.hasFixedSize = true;
			s.finalSize = size;
		}
		else if (type.hasKnownSize) {
			s.hasFixedSize = true;
			s.finalSize = type.sizeOf;
		}

		return s;
	}

	public static Struct createHook(StructType type, LibScope scope, String name, int hookOffset)
	{
		Struct s = new Struct(StructGenus.Hook, type, scope, name, "", NO_VALUE, hookOffset, NO_VALUE);
		s.hasFixedSize = false;
		return s;
	}

	private Struct(StructGenus gens, StructType type, LibScope scope, String name, String namespace, int address, int offset, int size)
	{
		if (type == null)
			throw new IllegalStateException("Tried created struct with no type!");

		this.gens = gens;
		this.type = type;
		this.scope = scope;
		this.name = name;
		this.namespace = namespace;

		this.originalFileOffset = offset;
		this.originalAddress = address;
		this.originalSize = size;
	}

	/*
	 * Totally encapsulate the struct type to prevent subtle mistakes.
	 * All type comparisons should use the isTypeOf method.
	 */

	public StructField parseFieldOffset(BaseDataEncoder encoder, Struct struct, String offsetName)
	{
		return type.parseFieldOffset(encoder, struct, offsetName);
	}

	public void replaceSpecial(BaseDataEncoder encoder, Patch patch)
	{
		type.replaceSpecial(encoder, patch);
	}

	public void replaceExpression(Line line, BaseDataEncoder encoder, String[] args, List<String> newTokenList)
	{
		type.replaceExpression(line, encoder, args, newTokenList);
	}

	public int getPatchLength(BaseDataEncoder encoder, Patch patch)
	{
		return type.getPatchLength(encoder, patch);
	}

	public boolean isTypeOf(StructType t)
	{
		return type.isTypeOf(t);
	}

	public String getTypeName()
	{
		return type.toString();
	}

	public boolean isDumped()
	{
		return gens == StructGenus.Overlay;
	}
}

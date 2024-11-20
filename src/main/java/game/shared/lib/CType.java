package game.shared.lib;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FilenameUtils;

import app.Directories;
import app.Environment;
import app.StarRodException;
import app.input.IOUtils;
import app.input.InputFileException;
import app.input.InvalidInputException;
import game.shared.DataUtils;
import util.Logger;

public class CType
{
	// types: base(**...)([x][y]...)(***...)
	// read type information right to left, as follows:
	// int*			pointer to int
	// int[10]		array of 10 ints
	// int*[10]		array of 10 pointers to ints
	// int[10]*		pointer to an array of 10 ints
	// int*[10]*	pointer to an array of 10 pointers to ints
	// int*[3][3]*		...and so on.
	// int*[10]**[2]	...
	// int**[2][3]*[4]* ...

	public static void main(String[] args) throws IOException
	{
		Environment.initialize();

		loadTypes();

		/*
		getType("float[3][3]");
		getType("int*[3]");
		getType("int**");
		getType("int**");
		getType("int**");
		getType("int*");
		getType("double[3]**");
		getType("byte*[4][3]*");

		for(Entry<String,Type> e : typePool.entrySet())
		{
			Type t = e.getValue();
			System.out.printf("%-15s%-15s%-4X%s%n", e.getKey(), t.specifier, t.size, t.typeCategory);
		}
		 */

		Environment.exit();
	}

	public static void loadTypes()
	{
		typePool = new LinkedHashMap<>();
		baseTypes = new HashMap<>();
		Primitive.load();

		try {
			loadTypedefs();
			loadStructs();
		}
		catch (IOException e) {
			throw new StarRodException("Exception while loading struct type data: %n%s", e.getMessage());
		}
	}

	private static final Pattern TypeSpecifierPattern = Pattern.compile("\\w+(?:\\*|\\[[0-9A-Fa-f]+[`']?\\])*");
	private static final Matcher TypeSpecifierMatcher = TypeSpecifierPattern.matcher("");

	private static final Pattern ArrayTypePattern = Pattern.compile("(.+?)((?:\\[[0-9A-Fa-f]+[`']?\\]))$");
	private static final Matcher ArrayTypeMatcher = ArrayTypePattern.matcher("");

	private static final Pattern PointerTypePattern = Pattern.compile("(.+?)(\\*)$");
	private static final Matcher PointerTypeMatcher = PointerTypePattern.matcher("");

	// keeps types singleton based on specifier
	private static HashMap<String, CType> typePool;
	private static HashMap<String, CType> baseTypes;

	public final String specifier;
	public final CType baseType;
	public final CType parentType;
	private int size;

	public final TypeCategory typeCategory;
	public final BaseCategory baseCategory;

	public int arraySize;
	private CType childType = null;

	private final Primitive primitive;

	public final List<CField> fields = new LinkedList<>();
	private List<String> fieldStrings = new LinkedList<>();
	public String desc = "";

	public static class CField
	{
		public final CType type;
		public final int offset;
		public final String name;
		public final String desc;
		public final boolean undefined;

		private CField(CType type, int offset, String name)
		{
			this(type, offset, name, "", false);
		}

		private CField(CType type, int offset, String name, String desc, boolean undefined)
		{
			this.type = type;
			this.offset = offset;
			this.name = name;
			this.desc = desc;
			this.undefined = undefined;
		}
	}

	public static enum TypeCategory
	{
		Base,
		Array,
		Pointer
	}

	public static enum BaseCategory
	{
		Primitive,
		Struct,
		Static,
		Derived
	}

	public static enum Mathegory
	{
		// @formatter:off
		Integer		(true),
		Float		(true),
		Boolean		(false),
		None		(false);
		// @formatter:on

		public final boolean supportsArithmetic;

		private Mathegory(boolean arithmetic)
		{
			supportsArithmetic = arithmetic;
		}
	}

	public static enum Primitive
	{
		// @formatter:off
		Bool	(0, 4, "bool",		Mathegory.Boolean),
		Byte	(1, 1, "byte",		Mathegory.Integer),
		UByte	(2, 1, "ubyte",		Mathegory.Integer, true),
	//	Char	(1, 1, "char",		Mathegory.Signed),
	//	UChar	(1, 1, "uchar",		Mathegory.Unsigned),
		Short	(3, 2, "short",		Mathegory.Integer),
		UShort	(4, 2, "ushort",	Mathegory.Integer, true),
		Int		(5, 4, "int",		Mathegory.Integer),
		UInt	(6, 4, "uint",		Mathegory.Integer, true),
	//	Long	(7, 8, "long",		Mathegory.Signed),
	//	ULong	(8, 8, "ulong",		Mathegory.Unsigned),
	//	Fixed	(-1, 4, "fixed",	Mathegory.Fixed),
		Float	(-1, 4, "float",	Mathegory.Float),
		Double	(-1, 8, "double",	Mathegory.Float),
		Pointer	(-1, 4, "ptr",		Mathegory.None),
		Void	(-1, 4, "void",		Mathegory.None),
		Code	(-1, 4, "code",		Mathegory.None);
		// @formatter:on

		public final CType type;
		public final Mathegory mathType;
		public final boolean unsigned;
		public final int intConversionRank;

		private Primitive(int rank, int size, String name, Mathegory mathType)
		{
			this(rank, size, name, mathType, false);
		}

		private Primitive(int rank, int size, String name, Mathegory mathType, boolean unsigned)
		{
			type = new CType(name, this, size);
			this.mathType = mathType;
			this.unsigned = unsigned;
			intConversionRank = rank;
		}

		private static void load()
		{
			for (Primitive p : Primitive.values())
				registerNewBaseType(p.type);
		}
	}

	private static void loadTypedefs() throws IOException
	{
		File f = new File(Directories.DATABASE_STRUCTS + "typedefs.txt");
		for (String line : IOUtils.readFormattedTextFile(f, false)) {
			String[] entry = line.split("\\s+");

			if (entry.length != 2)
				throw new InputFileException(f, "Invalid typedef: " + line);

			if (typePool.containsKey(entry[0]))
				throw new InputFileException(f, "Duplicate name for typedef: " + entry[1]);

			CType t = getType(entry[1]);
			if (t == null)
				throw new InputFileException(f, "Unknown base type for typedef: %s %n%s", entry[1], line);

			registerNewTypedef(entry[0], t);
		}
	}

	private static void loadStructs() throws IOException
	{
		HashMap<CType, File> structTypes = new HashMap<>();

		// first pass, read all files
		for (File source : IOUtils.getFilesWithExtension(Directories.DATABASE_RAM_STRUCTS, "struct", true))
			loadStruct(structTypes, source);

		for (Entry<CType, File> entry : structTypes.entrySet()) {
			CType struct = entry.getKey();
			File source = entry.getValue();
			for (String line : struct.fieldStrings) {
				int fieldOffset;
				String[] tokens = line.trim().split("\\s*:\\s*");
				try {
					fieldOffset = DataUtils.parseIntString(tokens[0]);
				}
				catch (InvalidInputException e) {
					throw new InputFileException(source, "Invalid field offset: %s %n%s", tokens[0], line);
				}

				CType fieldType = getType(tokens[2]);
				if (fieldType == null)
					throw new InputFileException(source, "Unknown type %s for field: %s %n%s", tokens[2], tokens[1], line);

				struct.addField(fieldType, fieldOffset, tokens[1]);
			}
			struct.fieldStrings = new LinkedList<>();
		}
	}

	private static void loadStruct(HashMap<CType, File> structTypes, File source) throws IOException
	{
		String typeString = "";
		String sizeString = "";
		CType type = null;

		int readState = 0;

		for (String line : IOUtils.readFormattedTextFile(source, false)) {
			switch (readState) {
				case 0:
					if (line.startsWith("type:"))
						typeString = line.substring(5).trim();
					else if (line.startsWith("size:"))
						sizeString = line.substring(5).trim();
					else if (line.matches("fields:\\s+\\{"))
						readState = 2;
					else if (line.startsWith("fields:"))
						readState = 1;
					else
						throw new InputFileException(source, "Invalid line: %n%s", line);
					break;
				case 1:
					if (!line.equals("{"))
						throw new InputFileException(source, "Missing { for fields list: %n%s", line);
					readState = 2;
					break;
				case 2:
					if (type == null) {
						String typeName = FilenameUtils.getBaseName(source.getName());
						int size;
						try {
							size = DataUtils.parseIntString(sizeString);
						}
						catch (InvalidInputException e) {
							throw new InputFileException(source, "Invalid struct size: %s", sizeString);
						}
						BaseCategory category;
						switch (typeString) {
							case "ram":
								category = BaseCategory.Struct;
								break;
							case "rom":
								category = BaseCategory.Static;
								break;
							default:
								throw new InputFileException(source, "Invalid struct type: %s", typeString);
						}

						if (typePool.containsKey(typeName))
							throw new InputFileException(source, "Duplicate name for struct: " + typeName);

						type = new CType(typeName, category);
						type.size = size;
						structTypes.put(type, source);
						registerNewBaseType(type);
					}
					if (!line.equals("}"))
						type.fieldStrings.add(line);
					else
						readState = 3;
					break;
				case 3:
					throw new InputFileException(source, "Encountered non-empty line after fields list: %n%s", line);
			}
		}
	}

	public static void registerNewBaseType(CType t)
	{
		Logger.logDetail("Adding base type: " + t.specifier);
		typePool.put(t.specifier, t);
		baseTypes.put(t.specifier, t);
	}

	public static void registerNewDerivedType(CType t)
	{
		Logger.logDetail("Adding derived type: " + t.specifier);
		typePool.put(t.specifier, t);
	}

	public static void registerNewTypedef(String typedefName, CType t)
	{
		Logger.logDetail("Adding derived type: " + t.specifier);
		typePool.put(typedefName, t);
	}

	public static CType getType(String specifier) throws IllegalArgumentException
	{
		CType t = typePool.get(specifier);
		if (t != null)
			return t; // found it

		TypeSpecifierMatcher.reset(specifier);

		if (!TypeSpecifierMatcher.matches())
			return null; // invalid type

		// this is a new type specifier; check if its a derived type

		ArrayTypeMatcher.reset(specifier);
		if (ArrayTypeMatcher.matches()) {
			String suffix = ArrayTypeMatcher.group(2);
			CType parent = getType(ArrayTypeMatcher.group(1));
			if (parent == null)
				return null;
			t = new CType(parent, specifier, TypeCategory.Array, suffix);
		}

		PointerTypeMatcher.reset(specifier);
		if (PointerTypeMatcher.matches()) {
			String suffix = PointerTypeMatcher.group(2);
			CType parent = getType(PointerTypeMatcher.group(1));
			if (parent == null)
				return null;
			t = new CType(parent, specifier, TypeCategory.Pointer, suffix);
		}

		// it was a derived type, add it to the type pool
		if (t != null) {
			registerNewDerivedType(t);
			return t;
		}

		// unknown base type
		return null;
	}

	// for primitive types
	private CType(String specifier, Primitive prim, int size)
	{
		this.specifier = specifier;

		this.parentType = this;
		this.baseType = this;

		this.typeCategory = TypeCategory.Base;
		this.baseCategory = BaseCategory.Primitive;
		this.primitive = prim;

		this.size = size;
		this.arraySize = 0;
	}

	// for other base types
	public CType(String specifier, BaseCategory baseCategory)
	{
		this.specifier = specifier;

		this.parentType = this;
		this.baseType = this;

		this.typeCategory = TypeCategory.Base;
		this.baseCategory = baseCategory;
		this.primitive = null;

		this.arraySize = 0;
	}

	// for derived types
	private CType(CType parent, String specifier, TypeCategory category, String suffix)
	{
		if ((category == TypeCategory.Base))
			throw new IllegalStateException("Wrong constructed used for base type: " + specifier);

		this.specifier = specifier;

		this.parentType = parent;
		this.baseType = parent.baseType;

		this.typeCategory = category;
		this.baseCategory = BaseCategory.Derived;
		this.primitive = null;

		switch (category) {
			case Array:
				suffix = suffix.substring(1, suffix.length() - 1);
				try {
					arraySize = DataUtils.parseIntString(suffix);
				}
				catch (InvalidInputException e) {
					throw new IllegalArgumentException("Invalid array size: " + suffix);
				}
				size = arraySize * parentType.getSize();
				break;
			case Pointer:
				arraySize = 0;
				size = 4;
				break;
			default:
				throw new IllegalStateException("Missing constructor for category: " + category);
		}
	}

	private void addField(CType fieldType, int fieldOffset, String fieldName)
	{
		fields.add(new CField(fieldType, fieldOffset, fieldName));
	}

	public void appendField(CType fieldType, String fieldName)
	{
		appendField(fieldType, fieldName, "", false);
	}

	public void appendField(CType fieldType, String fieldName, String fieldDesc, boolean undefined)
	{
		fields.add(new CField(fieldType, size, fieldName, fieldDesc, undefined));
		size += fieldType.size;
	}

	public CType dereference() throws InvalidInputException
	{
		if ((typeCategory == TypeCategory.Base))
			throw new InvalidInputException("Can't dereference base type: " + specifier);
		return parentType;
	}

	public CType reference()
	{
		if (childType == null)
			childType = CType.getType(specifier + "*");

		return childType;
	}

	public boolean isPrimitive()
	{
		return primitive != null;
	}

	public Primitive getPrimitive()
	{
		return primitive;
	}

	public Mathegory getMathegory()
	{
		return (primitive == null) ? Mathegory.None : primitive.mathType;
	}

	public CField getField(String fieldName)
	{
		for (CField field : fields) {
			if (field.name.equals(fieldName))
				return field;
		}
		return null;
	}

	@Override
	public String toString()
	{
		return specifier;
	}

	@Override
	public int hashCode()
	{
		return specifier.hashCode();
	}

	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		CType other = (CType) obj;
		return specifier.equalsIgnoreCase(other.specifier);
	}

	public int getSize()
	{
		return size;
	}
}

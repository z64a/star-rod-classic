package game.shared.lib;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.input.InvalidInputException;
import asm.MIPS;
import game.ROM.LibScope;
import game.shared.DataUtils;
import game.shared.ProjectDatabase;
import game.shared.ProjectDatabase.ConstEnum;
import game.shared.SyntaxConstants;
import game.shared.lib.CType.TypeCategory;
import game.shared.struct.StructType;
import game.shared.struct.script.ScriptVariable;

public class LibEntry
{
	private static final Pattern AddressFieldPattern = Pattern.compile("(80[0-9A-Fa-f]{6})(?:, *([0-9A-Fa-f]{1,8}))?");
	private static final Matcher AddressFieldMatcher = AddressFieldPattern.matcher("");

	private static final Pattern ValidScrTypePattern = Pattern.compile("\\$\\w+");
	private static final Matcher ValidScrTypeMatcher = ValidScrTypePattern.matcher("");

	private static final Pattern StackStoragePattern = Pattern.compile("SP\\[([0-9A-Fa-f]+[`']?)\\]");
	private static final Matcher StackStorageMatcher = StackStoragePattern.matcher("");

	private static final Pattern ValidOptionPattern = Pattern.compile("(\\w+)=(#?[\\w\\[\\]*]+)");
	private static final Matcher ValidOptionMatcher = ValidOptionPattern.matcher("");

	public final LibScope scope;
	public final EntryType type;

	public final int address;
	public final int offset;

	public final String name;
	public final String line;

	private List<String> options = new LinkedList<>();
	private List<LibParamList> signatures = new LinkedList<>();
	private LibParamList defaultParams;
	private LibParamList returns;

	private LibEntry(String line, LibScope scope, EntryType type, String name, int address, int offset)
	{
		this.line = line;
		this.scope = scope;
		this.type = type;
		this.address = address;
		this.offset = offset;
		this.name = name;
	}

	public static String matchOption(String token)
	{
		if (!token.startsWith("{") || !token.endsWith("}") || token.length() < 3)
			return null;
		return token.substring(1, token.length() - 1);
	}

	public static String matchDescription(String token)
	{
		if (!token.startsWith("\"") || !token.endsWith("\"") || token.length() < 3)
			return null;
		return token.substring(1, token.length() - 1);
	}

	public static enum EntryType
	{
		asm,
		api,
		sig,
		script,
		lbl,
		data
	}

	public static enum ParamListType
	{
		Normal,
		Void,
		Unknown,
		Varargs
	}

	public static class LibParamList implements Iterable<LibParam>
	{
		public final ParamListType listType;
		private final List<LibParam> params;

		public LibParamList(LibraryFile sourceFile, List<String> field, boolean returns) throws InvalidInputException
		{
			if (field.size() == 1) {
				String s = field.get(0);

				if (s.equals("???")) {
					listType = ParamListType.Unknown;
					params = new ArrayList<>(0);
					return;
				}

				if (s.equalsIgnoreCase("varargs")) {
					listType = ParamListType.Varargs;
					params = new ArrayList<>(0);
					return;
				}

				if (s.equalsIgnoreCase("void")) {
					listType = ParamListType.Void;
					params = new ArrayList<>(0);
					return;
				}

				if (s.equalsIgnoreCase("none")) {
					listType = ParamListType.Normal;
					params = new ArrayList<>(0);
					return;
				}
			}

			listType = ParamListType.Normal;

			List<List<String>> paramList = separate(field, ",");
			params = new ArrayList<>(paramList.size());
			for (List<String> tokens : paramList)
				params.add(new LibParam(sourceFile.scope, tokens));
		}

		@Override
		public Iterator<LibParam> iterator()
		{
			return params.iterator();
		}

		public int getLength()
		{
			return params.size();
		}

		public LibParam getParamAt(int i)
		{
			return params.get(i);
		}
	}

	public static enum ParamCategory
	{
		// types + typedefs
		Enum,
		StaticStruct,
		MemoryStruct,

		// missing information
		Unknown,
		MissingStaticStruct,
		MissingMemoryStruct,

		// contextual
		StringID,
		ModelID,
		ColliderID,
		ZoneID,
		EntryID,

		// formatting
		Number
	}

	public static class LibParam
	{
		// type
		public final LibType typeInfo;

		// storage
		public final LibParamStorage storage;

		public final String name;
		public final boolean isRawParam;

		public final boolean isOutParam;
		public final LibType outTypeInfo;

		// metadata for dumping and patching
		public final String suffix;
		public final int arrayLen; // negative values like -N indicate 'get from Nth param value'
		public final boolean shouldPrintParam;
		public final boolean ignorable;
		public final int ignoreValue;

		public LibParam(LibScope scope, List<String> tokens) throws InvalidInputException
		{
			List<String> options = new LinkedList<>();
			String description = "";

			boolean isRawParam = false;
			boolean isOutParam = false;
			LibType outTypeInfo = null;

			String suffix = "";
			int arrayLen = 0;
			boolean shouldPrintParam = false;
			boolean ignorable = false;
			int ignoreValue = 0;

			Iterator<String> iter = tokens.iterator();
			while (iter.hasNext()) {
				String s = iter.next();

				String option = matchOption(s);
				if (option != null) {
					options.add(option);
					iter.remove();
					continue;
				}

				String desc = matchDescription(s);
				if (desc != null) {
					if (!description.isEmpty())
						throw new InvalidInputException("Cannot have multiple description strings for one parameter.");
					description = desc;
					iter.remove();
					continue;
				}
			}

			for (String s : options) {
				ValidOptionMatcher.reset(s);
				if (ValidOptionMatcher.matches()) {
					String value = ValidOptionMatcher.group(2);
					switch (ValidOptionMatcher.group(1)) {
						case "name":
							suffix = value;
							break;
						case "len":
							try {
								if (value.startsWith("#"))
									arrayLen = -DataUtils.parseIntString(value.substring(1));
								else
									arrayLen = DataUtils.parseIntString(value);
							}
							catch (NumberFormatException e) {
								throw new InvalidInputException("Invalid array length option: " + s);
							}
							break;
						case "ignore":
							try {
								ignorable = true;
								ignoreValue = DataUtils.parseIntString(value);
							}
							catch (NumberFormatException e) {
								throw new InvalidInputException("Invalid ignore option: " + s);
							}
							break;
						case "outType":
							outTypeInfo = resolveType(scope, value);
							if (outTypeInfo == null)
								throw new InvalidInputException("Invalid parameter out type: " + value);
							break;
						case "warning":
							break; // ignore it, this is for docs/editor only
						default:
							throw new InvalidInputException("Unknown option: " + s);
					}
				}
				else {
					switch (s) {
						case "out":
							isOutParam = true;
							break;
						case "raw":
							isRawParam = true;
							break;
						case "print":
							shouldPrintParam = true;
							break;
						default:
							throw new InvalidInputException("Unknown option: " + s);
					}
				}
			}

			if (outTypeInfo != null && !isOutParam)
				throw new InvalidInputException("{outType} given for parameter not marked as {out}.");

			String typeSpecifier;
			switch (tokens.size()) {
				case 1:
					storage = null;
					typeSpecifier = tokens.get(0);
					name = "";
					break;
				case 2:
					storage = null;
					typeSpecifier = tokens.get(0);
					name = tokens.get(1);
					break;
				case 3:
					storage = new LibParamStorage(tokens.get(0));
					typeSpecifier = tokens.get(1);
					name = tokens.get(2);
					break;
				default:
					throw new InvalidInputException("Parameter is invalid.");
			}

			if (!name.isEmpty() && !name.equals("???") && !name.matches("\\w+"))
				throw new InvalidInputException("Invalid parameter name: " + name);

			if (typeSpecifier.equals("???")) {
				typeInfo = new LibType(ParamCategory.Unknown);
			}
			else if (typeSpecifier.equals("dec")) {
				typeInfo = resolveType(scope, typeSpecifier);
			}
			else
				typeInfo = resolveType(scope, typeSpecifier);

			if (typeInfo == null)
				throw new InvalidInputException("Invalid parameter type: " + typeSpecifier);

			this.isRawParam = isRawParam;
			this.isOutParam = isOutParam;
			this.outTypeInfo = outTypeInfo;

			this.suffix = suffix;
			this.arrayLen = arrayLen;
			this.shouldPrintParam = shouldPrintParam;
			this.ignorable = ignorable;
			this.ignoreValue = ignoreValue;
		}
	}

	public static enum StorageType
	{
		Register,
		Stack,
		Variable
	}

	public static class LibParamStorage
	{
		public final StorageType type;
		public final String baseName;
		public final int offset;

		public LibParamStorage(String token) throws InvalidInputException
		{
			ScriptVariable varType = ScriptVariable.getTypeOf("*" + token);
			if (varType != null) {
				type = StorageType.Variable;
				baseName = varType.getTypeName();
				offset = ScriptVariable.getScriptVariableIndex("*" + token);
			}
			else {
				StackStorageMatcher.reset(token);
				if (StackStorageMatcher.matches()) {
					type = StorageType.Stack;
					baseName = "SP";
					try {
						offset = DataUtils.parseIntString(StackStorageMatcher.group(1));
					}
					catch (NumberFormatException e) {
						throw new InvalidInputException("Invalid stack storage: " + token);
					}

				}
				else if (MIPS.isCpuReg(token) || MIPS.isFpuReg(token)) {
					type = StorageType.Register;
					baseName = token;
					offset = 0;
				}
				else {
					throw new InvalidInputException("Invalid storage: " + token);
				}
			}
		}
	}

	public static class LibType
	{
		public final ParamCategory category;
		public final ConstEnum constType;
		public final StructType staticType;
		public final CType ctype;

		private LibType(ParamCategory category)
		{
			this.category = category;
			this.ctype = null;
			this.staticType = null;
			this.constType = null;
		}

		private LibType(ParamCategory category, CType type)
		{
			this.category = category;
			this.ctype = type;
			this.staticType = null;
			this.constType = null;
		}

		private LibType(ParamCategory category, StructType staticType)
		{
			this.category = category;
			this.ctype = null;
			this.staticType = staticType;
			this.constType = null;
		}

		private LibType(ParamCategory category, ConstEnum constType)
		{
			this.category = category;
			this.ctype = null;
			this.staticType = null;
			this.constType = constType;
		}

		@Override
		public String toString()
		{
			StringBuilder sb = new StringBuilder(category.name());
			switch (category) {
				case Enum:
					sb.append(constType.getNamespace());
					break;
				case MemoryStruct:
					sb.append(ctype.specifier);
					break;
				case StaticStruct:
					sb.append(staticType.toString());
					break;
				default:
					break;
			}
			return sb.toString();
		}
	}

	public boolean isFunction()
	{
		return type == EntryType.api || type == EntryType.asm;
	}

	public LibParamList getDefaultParams()
	{
		return defaultParams;
	}

	public LibParamList getMatchingParams(int nargs)
	{
		switch (type) {
			case asm:
			case api:
			case script:
				for (LibParamList list : signatures) {
					if (list.params.size() == nargs || list.listType == ParamListType.Varargs || list.listType == ParamListType.Unknown)
						return list;
				}
				break;
			default:
				throw new IllegalStateException();
		}

		return null;
	}

	public LibParamList getReturns()
	{
		return returns;
	}

	public static LibEntry parse(LibraryFile sourceFile, String line) throws InvalidInputException
	{
		List<String> tokens = tokenize(line);
		List<List<String>> fields = separate(tokens, ":");

		LibEntry entry = createEntry(sourceFile.scope, line, fields);

		switch (entry.type) {
			case asm:
				parseASM(sourceFile, entry, fields);
				break;
			case api:
				parseAPI(sourceFile, entry, fields);
				break;
			case sig:
				parseSIG(sourceFile, entry, fields);
				break;
			case script:
				parseSCR(sourceFile, entry, fields);
				break;
			case lbl:
				parseLBL(entry, fields);
				break;
			case data:
				parseDAT(entry, fields);
				break;
			default:
				throw new InvalidInputException("Unknown entry type: %s", tokens.get(0));
		}

		return entry;
	}

	private static LibEntry createEntry(LibScope scope, String line, List<List<String>> fields) throws InvalidInputException
	{
		if (fields.size() < 3)
			throw new InvalidInputException("Invalid number of fields for line.");

		List<String> typeField = fields.get(0);
		if (typeField.size() != 1)
			throw new InvalidInputException("Invalid entry type.");

		EntryType type;
		switch (typeField.get(0)) {
			case "asm":
				type = EntryType.asm;
				break;
			case "api":
				type = EntryType.api;
				break;
			case "sig":
				type = EntryType.sig;
				break;
			case "scr":
				type = EntryType.script;
				break;
			case "lbl":
				type = EntryType.lbl;
				break;
			case "dat":
				type = EntryType.data;
				break;
			default:
				throw new InvalidInputException("Invalid entry type.");
		}

		int nameFieldIndex = (type == EntryType.sig) ? 1 : 2;

		// check name field
		List<String> nameField = fields.get(nameFieldIndex);
		Iterator<String> iter = nameField.iterator();
		String description = "";

		// only option support for entry is "warning"
		// we do not need to load it since its an docs/editor annotation
		while (iter.hasNext()) {
			String s = iter.next();

			String option = matchOption(s);
			if (option != null) {
				iter.remove();
				continue;
			}

			String desc = matchDescription(s);
			if (desc != null) {
				if (!description.isEmpty())
					description += System.lineSeparator() + desc;
				else
					description = desc;
				iter.remove();
				continue;
			}
		}

		if (nameField.size() != 1)
			throw new InvalidInputException("Invalid entry name field.");

		String name = nameField.get(0);
		if (!name.matches("\\w+") && !name.equals("???"))
			throw new InvalidInputException("Invalid entry name: " + name);

		int address = -1;
		int offset = -1;
		if (type != EntryType.sig) {
			List<String> addressField = fields.get(1);
			if (addressField.size() < 1)
				throw new InvalidInputException("Invalid address field.");

			try {
				AddressFieldMatcher.reset(addressField.get(0));
				if (AddressFieldMatcher.matches()) {
					address = (int) Long.parseLong(AddressFieldMatcher.group(1), 16);
					if (addressField.size() >= 3)
						offset = (int) Long.parseLong(addressField.get(2), 16);
				}
				else {
					throw new InvalidInputException("Could not parse address field: " + addressField.get(0));
				}
			}
			catch (NumberFormatException e) {
				throw new InvalidInputException("Could not parse address field: " + addressField.get(0));
			}
		}

		return new LibEntry(line, scope, type, name, address, offset);
	}

	private static void parseFunctionLike(LibraryFile sourceFile, LibEntry entry, List<List<String>> fields) throws InvalidInputException
	{
		if (entry.isFunction() && fields.size() == 4) {
			List<String> sigFields = fields.get(3);

			if (sigFields.size() != 1 || !sigFields.get(0).startsWith("@"))
				throw new RuntimeException("Invalid entry " + entry.name + " has wrong number of fields: " + fields.size());

			String signame = sigFields.get(0).substring(1);
			LibEntry signature = null;
			for (LibEntry sig : sourceFile.signatures) {
				if (sig.name.equals(signame)) {
					signature = sig;
					break;
				}
			}

			if (signature != null) {
				entry.returns = signature.returns;
				LibParamList params = signature.getDefaultParams();
				entry.signatures.add(params);
				entry.defaultParams = params;
			}
			else {
				throw new RuntimeException("Function " + entry.name + " uses unknown signature: " + signame);
			}
			return;
		}

		if (fields.size() < 5)
			throw new InvalidInputException("Invalid entry has wrong number of fields (%d).", fields.size());

		entry.returns = new LibParamList(sourceFile, fields.get(3), true);

		for (int i = 4; i < fields.size(); i++) {
			LibParamList newList = new LibParamList(sourceFile, fields.get(i), false);
			for (LibParamList oldList : entry.signatures) {
				if (oldList.params.size() == newList.params.size())
					throw new InvalidInputException("Invalid entry: multiple parameter lists of equal length.", fields.size());

				if (oldList.listType == ParamListType.Varargs || newList.listType == ParamListType.Varargs)
					throw new InvalidInputException("Invalid entry: multiple parameter lists with one being varargs.", fields.size());

				if (oldList.listType == ParamListType.Unknown || newList.listType == ParamListType.Unknown)
					throw new InvalidInputException("Invalid entry: multiple parameter lists with one being unknown.", fields.size());
			}
			if (entry.signatures.size() == 0)
				entry.defaultParams = newList;
			entry.signatures.add(newList);
		}
	}

	private static void parseASM(LibraryFile sourceFile, LibEntry entry, List<List<String>> fields) throws InvalidInputException
	{
		parseFunctionLike(sourceFile, entry, fields);

		for (LibParamList paramList : entry.signatures) {
			for (LibParam param : paramList.params) {
				if ((param.storage != null) && (param.storage.type == StorageType.Variable)) // register and stack only
					throw new InvalidInputException("Invalid parameter storage: %s", param.storage);
			}
		}

		for (LibParam param : entry.returns.params) {
			if ((param.storage != null) && (param.storage.type == StorageType.Variable)) // register and stack only
				throw new InvalidInputException("Invalid return storage: %s", param.storage);
		}
	}

	private static void parseAPI(LibraryFile sourceFile, LibEntry entry, List<List<String>> fields) throws InvalidInputException
	{
		parseFunctionLike(sourceFile, entry, fields);

		for (LibParamList paramList : entry.signatures) {
			for (LibParam param : paramList.params) {
				if (param.storage != null) // no storage for api functions
					throw new InvalidInputException("Invalid parameter storage: %s", param.storage.baseName);
			}
		}

		for (LibParam param : entry.returns.params) {
			if ((param.storage == null) || (param.storage.type != StorageType.Variable)) // variable only
				throw new InvalidInputException("Invalid return storage: %s", param.storage);
		}
	}

	private static void parseSIG(LibraryFile sourceFile, LibEntry entry, List<List<String>> fields) throws InvalidInputException
	{
		if (fields.size() != 4)
			throw new RuntimeException("Invalid signature entry has wrong number of fields: " + fields.size());

		entry.returns = new LibParamList(sourceFile, fields.get(2), true);

		LibParamList params = new LibParamList(sourceFile, fields.get(3), false);
		entry.defaultParams = params;
		entry.signatures.add(params);

		sourceFile.signatures.add(entry);
	}

	private static void parseSCR(LibraryFile sourceFile, LibEntry entry, List<List<String>> fields) throws InvalidInputException
	{
		parseFunctionLike(sourceFile, entry, fields);

		for (LibParamList paramList : entry.signatures) {
			for (LibParam param : paramList.params) {
				if ((param.storage != null) && (param.storage.type != StorageType.Variable)) // variable only
					throw new InvalidInputException("Invalid parameter storage: %s", param.storage);
			}
		}

		for (LibParam param : entry.returns.params) {
			if ((param.storage != null) && (param.storage.type != StorageType.Variable)) // variable only
				throw new InvalidInputException("Invalid return storage: %s", param.storage);
		}
	}

	private static void parseDAT(LibEntry entry, List<List<String>> fields)
	{
		if (fields.size() != 4)
			throw new RuntimeException("Invalid data entry " + entry.name + " has wrong number of fields: " + fields.size());
		//TODO
	}

	private static void parseLBL(LibEntry entry, List<List<String>> fields)
	{
		if (fields.size() != 3)
			throw new RuntimeException("Invalid label entry " + entry.name + " has wrong number of fields: " + fields.size());
	}

	public static LibType resolveType(LibScope scope, String specifier)
	{
		if (specifier.charAt(0) == SyntaxConstants.POINTER_PREFIX) {
			ValidScrTypeMatcher.reset(specifier);
			if (!ValidScrTypeMatcher.matches())
				return null;

			StructType t = scope.typeMap.get(specifier.substring(1));
			if (t == null)
				return new LibType(ParamCategory.MissingStaticStruct);

			return new LibType(ParamCategory.StaticStruct, t);
		}

		// check enum types
		if (specifier.charAt(0) == '#') {
			ConstEnum constType = ProjectDatabase.getFromLibraryName(specifier.substring(1));
			return (constType == null) ? null : new LibType(ParamCategory.Enum, constType);
		}

		ParamCategory category;
		if (specifier.equalsIgnoreCase("dec"))
			category = ParamCategory.Number;
		else if (specifier.equalsIgnoreCase("stringID"))
			category = ParamCategory.StringID;
		else if (specifier.equalsIgnoreCase("modelID"))
			category = ParamCategory.ModelID;
		else if (specifier.equalsIgnoreCase("colliderID"))
			category = ParamCategory.ColliderID;
		else if (specifier.equalsIgnoreCase("zoneID"))
			category = ParamCategory.ZoneID;
		else if (specifier.equalsIgnoreCase("entryID"))
			category = ParamCategory.EntryID;
		else
			category = ParamCategory.MemoryStruct;

		CType t = CType.getType(specifier);
		if (t == null) {
			// pointers to undocumented structs allowed
			if (specifier.endsWith("*"))
				return new LibType(ParamCategory.MissingMemoryStruct);
			else
				return null;
		}

		// params can either be primitives or pointers to other types
		if (t.typeCategory == TypeCategory.Pointer || t.isPrimitive())
			return new LibType(category, t);
		else
			return null;
	}

	private static List<List<String>> separate(List<String> tokens, String delimiter)
	{
		List<List<String>> fields = new LinkedList<>();
		List<String> current = new LinkedList<>();
		for (String s : tokens) {
			if (s.equals(delimiter)) {
				fields.add(current);
				current = new LinkedList<>();
			}
			else {
				current.add(s);
			}
		}

		if (current.size() > 0)
			fields.add(current);

		return fields;
	}

	private enum ReadingState
	{
		Init,
		Token,
		Spacing,
		Annotation,
		Description
	}

	private static List<String> tokenize(String line) throws InvalidInputException
	{
		List<String> tokens = new ArrayList<>();
		StringBuilder sb = new StringBuilder();

		ReadingState state = ReadingState.Init;

		boolean escaping = false;
		for (char c : line.toCharArray()) {
			if (state == ReadingState.Description) {
				switch (c) {
					case '\\':
						if (escaping)
							sb.append(c);
						escaping = !escaping;
						break;
					case '"':
						sb.append(c);
						if (!escaping) {
							tokens.add(sb.toString());
							sb = new StringBuilder();
							state = ReadingState.Spacing;
						}
						escaping = false;
						break;
					default:
						if (escaping)
							throw new InvalidInputException("Invalid escape sequence '\\%c' for line: %n%s", c, line);
						sb.append(c);
				}
			}
			else if (state == ReadingState.Annotation) {
				switch (c) {
					case '\\':
						if (escaping)
							sb.append(c);
						escaping = !escaping;
						break;
					case '}':
						sb.append(c);
						if (!escaping) {
							tokens.add(sb.toString());
							sb = new StringBuilder();
							state = ReadingState.Spacing;
						}
						escaping = false;
						break;
					default:
						if (escaping)
							throw new InvalidInputException("Invalid escape sequence '\\%c' for line: %n%s", c, line);
						sb.append(c);
				}
			}
			else {
				switch (c) {
					case ' ':
					case '\t':
						switch (state) {
							case Token:
								if (sb.length() > 0) {
									tokens.add(sb.toString());
									sb = new StringBuilder();
								}
								state = ReadingState.Spacing;
							case Spacing:
							case Init:
								continue;

							case Description:
							case Annotation:
								throw new IllegalStateException();
						}

					case ':':
					case ',':
						switch (state) {
							case Init:
							case Token:
								if (sb.length() > 0) {
									tokens.add(sb.toString());
									sb = new StringBuilder();
								}
							case Spacing:
								tokens.add("" + c);
								continue;

							case Description:
							case Annotation:
								throw new IllegalStateException();
						}

					case '"':
						if (sb.length() > 0) {
							tokens.add(sb.toString());
							sb = new StringBuilder();
						}
						state = ReadingState.Description;
						sb.append(c);
						continue;

					case '{':
						if (sb.length() > 0) {
							tokens.add(sb.toString());
							sb = new StringBuilder();
						}
						state = ReadingState.Annotation;
						sb.append(c);
						continue;

					default:
						switch (state) {
							case Init:
							case Spacing:
								state = ReadingState.Token;
							case Token:
								sb.append(c);
								continue;
							case Description:
							case Annotation:
								throw new IllegalStateException();
						}
				}
			}
		}

		if (sb.length() > 0)
			tokens.add(sb.toString());

		return tokens;
	}
}

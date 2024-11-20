package game.shared;

import static app.Directories.*;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;

import javax.swing.JComboBox;

import org.apache.commons.io.FilenameUtils;
import org.w3c.dom.Element;

import app.Environment;
import app.StarRodClassic;
import app.input.IOUtils;
import app.input.InputFileException;
import app.input.InvalidInputException;
import game.ROM;
import game.ROM_US;
import game.globals.ItemRecord;
import game.globals.MoveRecord;
import game.globals.editor.GlobalsData;
import game.globals.editor.GlobalsRecord;
import game.map.shading.SpriteShadingData;
import game.map.shading.SpriteShadingEditor;
import game.shared.ProjectDatabase.ConstEnum.EnumPair;
import game.shared.lib.CType;
import game.shared.struct.script.ScriptVariable;
import game.string.StringConstKey;
import game.string.StringEncoder;
import game.texture.images.ImageDatabase;
import util.DualHashMap;
import util.xml.XmlWrapper.XmlReader;

public class ProjectDatabase
{
	public static void main(String[] args) throws InvalidInputException
	{
		Environment.initialize();
		ConstEnum e = constNameMap.get("DamageType");
		System.out.println(e.getFlagsString(0x10000030));

		System.out.printf("%X%n", getDebuffValue(new String[] { "DebuffType", "Dizzy", "2", "60`" }));

		Environment.exit();
	}

	public static ROM rom;

	public static ImageDatabase images;
	public static GlobalsData globalsData;

	public static DualHashMap<Integer, String> EffectType;

	private static HashMap<Integer, String> actorNameMap;
	private static HashMap<String, String> miscConstantsMap;

	// allows looking up constant types from either namespace name or library name
	private static HashMap<String, ConstEnum> constNameMap;
	private static HashMap<String, ConstEnum> constLibMap;

	// string constants
	private static HashMap<String, ByteBuffer> stringConstMap;

	public static ConstEnum SpriteType;
	public static ConstEnum AbilityType;
	public static ConstEnum StatusType;
	public static ConstEnum DebuffType;
	public static ConstEnum ElementType;
	public static ConstEnum EntityType;
	public static ConstEnum StoryType;
	public static ConstEnum LocationType;
	public static ConstEnum TriggerType;
	public static ConstEnum EventType;
	public static ConstEnum PhaseType;
	public static ConstEnum OutcomeType;
	public static ConstEnum HitResultType;
	public static ConstEnum DoorSoundsType;
	public static ConstEnum DoorSwingsType;
	public static ConstEnum NpcType;

	public static final String ITEM_NAMESPACE = "Item";
	public static final String MOVE_NAMESPACE = "Move";

	public static final String SHADING_NAMESPACE = "Shading";
	public static SpriteShadingData SpriteShading;

	private static VarNameDictionary dumpBytes;
	private static VarNameDictionary dumpFlags;

	private static VarNameDictionary gameBytes;
	private static VarNameDictionary gameFlags;

	private static VarNameDictionary modBytes;
	private static VarNameDictionary modFlags;

	private static List<String> modFlagList;

	private static boolean initialized = false;

	public static void initialize() throws IOException
	{
		initialize(false); // !RunContext.currentProject.isDecomp
	}

	public static void initialize(boolean hasProject) throws IOException
	{
		if (initialized)
			return;

		reload(hasProject);

		initialized = true;
	}

	public static void reload(boolean hasProject) throws IOException
	{
		constNameMap = new HashMap<>();
		constLibMap = new HashMap<>();
		String[] fileTypes = new String[] { "enum", "flags" };

		ConstEnum itemEnum = new ConstEnum("Item", "itemID", new File(DATABASE + "default_item_names.txt"));
		constNameMap.put(itemEnum.namespace, itemEnum);
		constLibMap.put(itemEnum.libName, itemEnum);

		ConstEnum moveEnum = new ConstEnum("Move", "moveID", new File(DATABASE + "default_move_names.txt"));
		constNameMap.put(moveEnum.namespace, moveEnum);
		constLibMap.put(moveEnum.libName, moveEnum);

		for (File f : IOUtils.getFilesWithExtension(DATABASE_TYPES, fileTypes, true)) {
			boolean flagsFile = FilenameUtils.getExtension(f.getName()).equals("flags");
			ConstEnum ce = new ConstEnum(f, flagsFile);
			constNameMap.put(ce.namespace, ce);
			constLibMap.put(ce.libName, ce);
		}

		if (hasProject && MOD_ENUMS.toFile().exists()) {
			for (File f : IOUtils.getFilesWithExtension(MOD_ENUMS, fileTypes, true)) {
				boolean flagsFile = FilenameUtils.getExtension(f.getName()).equals("flags");
				ConstEnum ce = new ConstEnum(f, flagsFile);

				if (!constNameMap.containsKey(ce.namespace))
					constNameMap.put(ce.namespace, ce);
				else
					constNameMap.get(ce.namespace).addOverrides(ce);

				if (!constLibMap.containsKey(ce.libName))
					constLibMap.put(ce.libName, ce);
			}
		}

		CType.loadTypes();

		actorNameMap = readDecode(DATABASE_TYPES + "actors.txt");
		miscConstantsMap = readEncode(DATABASE_TYPES + "misc.txt");

		SpriteType = constNameMap.get("Sprite");
		AbilityType = constNameMap.get("Ability");
		StatusType = constNameMap.get("Status");
		DebuffType = constNameMap.get("DebuffType");
		ElementType = constNameMap.get("Element");
		EntityType = constNameMap.get("Entity");
		StoryType = constNameMap.get("Story");
		LocationType = constNameMap.get("Location");
		NpcType = constNameMap.get("Npc");
		TriggerType = constNameMap.get("Trigger");
		EventType = constNameMap.get("Event");
		PhaseType = constNameMap.get("Phase");
		OutcomeType = constNameMap.get("Outcome");
		HitResultType = constNameMap.get("HitResult");

		DoorSoundsType = constNameMap.get("DoorSounds");
		DoorSwingsType = constNameMap.get("DoorSwing");

		EffectType = readEffectTypes(new File(DATABASE_TYPES + "effects.txt"));

		if (hasProject)
			SpriteShading = SpriteShadingEditor.loadModData();
		else
			SpriteShading = SpriteShadingEditor.loadDumpData();

		dumpBytes = new VarNameDictionary(ScriptVariable.GameByte);
		dumpFlags = new VarNameDictionary(ScriptVariable.GameFlag);

		dumpBytes.loadDictionary(new File(DATABASE + FN_GAME_BYTES));
		dumpFlags.loadDictionary(new File(DATABASE + FN_GAME_FLAGS));

		gameBytes = new VarNameDictionary(ScriptVariable.GameByte);
		gameFlags = new VarNameDictionary(ScriptVariable.GameFlag);

		modBytes = new VarNameDictionary(ScriptVariable.ModByte);
		modFlags = new VarNameDictionary(ScriptVariable.ModFlag);
		modFlagList = new ArrayList<>();

		if (hasProject && MOD_GLOBALS.toFile().exists())
			loadModGlobals();

		rom = new ROM_US(DATABASE.toFile());

		images = new ImageDatabase();
		if (hasProject)
			images.loadFromProject();
		else
			images.loadFromDatabase();

		stringConstMap = loadStringConstants();
	}

	public static String getActorName(int index)
	{
		return actorNameMap.get(index);
	}

	public static void loadGlobals(boolean fromProject)
	{
		globalsData = new GlobalsData();
		globalsData.loadDataStrict(fromProject);

		replaceEnum("Item", "itemID", globalsData.items);
		replaceEnum("Move", "moveID", globalsData.moves);
	}

	private static void replaceEnum(String namespace, String libName, Iterable<? extends GlobalsRecord> entries)
	{
		constNameMap.remove(namespace);
		constLibMap.remove(libName);

		LinkedHashMap<Integer, String> decodeMap = new LinkedHashMap<>();
		for (GlobalsRecord rec : entries)
			decodeMap.put(rec.listIndex, rec.getIdentifier());

		ConstEnum ce = new ConstEnum(namespace, libName, decodeMap);
		constNameMap.put(ce.namespace, ce);
		constLibMap.put(ce.libName, ce);
	}

	public static boolean hasItem(int index)
	{
		if (globalsData == null)
			return false;
		return index >= 0 && globalsData.items.size() > index;
	}

	public static String getItemName(int index)
	{
		if (globalsData == null)
			return null;
		if (!hasItem(index))
			return null;
		ItemRecord item = globalsData.items.get(index);
		return (item == null) ? null : item.getIdentifier();
	}

	public static Integer getItemID(String name)
	{
		if (globalsData == null)
			return null;
		ItemRecord item = globalsData.items.getElement(name);
		return (item == null) ? null : item.listIndex;
	}

	public static String getItemConstant(int itemID)
	{
		if (globalsData == null)
			return null;
		String name = getItemName(itemID);
		if (name == null)
			name = String.format("MISSING_%03X", itemID);
		return SyntaxConstants.CONSTANT_PREFIX + ITEM_NAMESPACE + SyntaxConstants.CONSTANT_SEPARATOR + name;
	}

	public static String[] getItemNames()
	{
		if (globalsData == null)
			return new String[] {};

		String[] names = new String[globalsData.items.size()];
		for (int i = 0; i < names.length; i++) {
			ItemRecord item = globalsData.items.get(i);
			names[i] = item.getIdentifier();
		}
		return names;
	}

	public static boolean hasMove(int index)
	{
		if (globalsData == null)
			return false;
		return index >= 0 && globalsData.moves.size() > index;
	}

	public static String getMoveName(int index)
	{
		if (globalsData == null)
			return null;
		if (!hasMove(index))
			return null;
		MoveRecord move = globalsData.moves.get(index);
		return (move == null) ? null : move.getIdentifier();
	}

	public static Integer getMoveID(String name)
	{
		if (globalsData == null)
			return null;
		MoveRecord move = globalsData.moves.getElement(name);
		return (move == null) ? null : move.listIndex;
	}

	// format: name = index
	private static HashMap<String, String> readEncode(String path) throws IOException
	{
		File f = new File(path);
		HashMap<String, String> map = new HashMap<>();
		try {
			List<String> lines = IOUtils.readFormattedTextFile(f);
			for (int i = 0; i < lines.size(); i++) {
				String line = lines.get(i);

				if (line.isEmpty())
					continue;

				String[] tokens = IOUtils.getKeyValuePair(f, line, i);

				map.put(tokens[0], tokens[1]);
			}

		}
		catch (IOException e) {
			e.printStackTrace();
			StarRodClassic.displayStackTrace(e);
			System.exit(-1);
		}
		return map;
	}

	// format: index = name
	private static HashMap<Integer, String> readDecode(String path) throws IOException
	{
		File f = new File(path);
		HashMap<Integer, String> map = new HashMap<>();
		try {
			List<String> lines = IOUtils.readFormattedTextFile(f);
			for (int i = 0; i < lines.size(); i++) {
				String line = lines.get(i);

				if (line.isEmpty())
					continue;

				String[] tokens = IOUtils.getKeyValuePair(f, line, i);
				int index = (int) Long.parseLong(tokens[0], 16);

				map.put(index, tokens[1]);
			}

		}
		catch (Exception e) {
			e.printStackTrace();
			StarRodClassic.displayStackTrace(e);
			System.exit(-1);
		}
		return map;
	}

	public static boolean has(String s)
	{
		if (miscConstantsMap.containsKey(s))
			return true;

		int radix = s.lastIndexOf(SyntaxConstants.CONSTANT_SEPARATOR);

		if (radix < 0)
			return false;

		String nameSpace = s.substring(0, radix);
		String name = s.substring(radix);

		if (nameSpace.isEmpty() || name.isEmpty())
			return false;

		ConstEnum constType = constNameMap.get(nameSpace);

		if (constType == null)
			return false;

		return constType.hasID(name);
	}

	public static String resolve(String s, boolean checkValidity) throws InvalidInputException
	{
		if (miscConstantsMap.containsKey(s))
			return miscConstantsMap.get(s);

		String namespace = null;
		String name = null;

		int radix = s.lastIndexOf(SyntaxConstants.CONSTANT_SEPARATOR);

		if (radix > 0) {
			namespace = s.substring(0, radix);
			name = s.substring(radix + 1);
		}

		if (radix < 0 || namespace.isEmpty() || name.isEmpty()) {
			if (checkValidity)
				throw new InvalidInputException("Could not resolve constant: " + SyntaxConstants.CONSTANT_PREFIX + s);
			else
				return null;
		}

		if (namespace.equals(ITEM_NAMESPACE)) {
			Integer itemID = getItemID(name);
			if (itemID == null)
				throw new InvalidInputException("Undefined item: " + name);
			return String.format("%08X", itemID);
		}

		if (namespace.equals(MOVE_NAMESPACE)) {
			Integer itemID = getMoveID(name);
			if (itemID == null)
				throw new InvalidInputException("Undefined move: " + name);
			return String.format("%08X", itemID);
		}

		if (namespace.equals(SHADING_NAMESPACE)) {
			Integer spriteShadingKey = SpriteShading.getShadingKey(name);
			if (spriteShadingKey == null)
				throw new InvalidInputException("Undefined sprite shading: " + name);
			return String.format("%08X", spriteShadingKey);
		}

		ConstEnum constType = constNameMap.get(namespace);

		if (constType == null)
			return null;

		String idName = constType.encodeMap.get(name);

		return idName;
	}

	public static String getDebuffString(int id)
	{
		int unk = (id >> 0x1C) & 0xF;
		int type = id & 0x0FFFF000;
		int duration = (id >> 0x8) & 0xF;
		int chance = id & 0xFF;

		String typeName = DebuffType.getName(type);
		if (typeName == null)
			typeName = String.format("%X", type);

		if (unk == 8)
			return String.format("%c%s:%s:%X:%d`", SyntaxConstants.EXPRESSION_PREFIX, DebuffType.getNamespace(),
				typeName, duration, chance);
		else
			return String.format("%c%s:%X|%s:%X:%d`", SyntaxConstants.EXPRESSION_PREFIX, DebuffType.getNamespace(),
				unk, typeName, duration, chance);
	}

	public static int getDebuffValue(String[] fields) throws InvalidInputException
	{
		if (fields.length != 4)
			throw new InvalidInputException(DebuffType.getNamespace() + " has invalid format.");

		String typeString;
		int msn;
		int duration = DataUtils.parseIntString(fields[2]);
		int chance = DataUtils.parseIntString(fields[3]);

		if (fields[1].contains("|")) {
			String[] typeFields = fields[1].split("|");
			if (typeFields.length != 2)
				throw new InvalidInputException(DebuffType.getNamespace() + " type has invalid format: " + fields[1]);

			msn = DataUtils.parseIntString(typeFields[0]);
			typeString = typeFields[1];
		}
		else {
			msn = 8;
			typeString = fields[1];
		}

		int type;
		if (DebuffType.hasID(typeString))
			type = DebuffType.getID(typeString);
		else
			type = DataUtils.parseIntString(fields[2]);

		return ((msn & 0xF) << 0x1C) | (type & 0x0FFFF000) | ((duration & 0xF) << 8) | (chance & 0xFF);
	}

	// each type of constant has its own namespace
	public static class ConstEnum
	{
		private final boolean flags;
		private final String namespace;
		private final String libName;
		private LinkedHashMap<Integer, String> decodeMap;
		private LinkedHashMap<String, String> encodeMap;

		private ConstEnum(String namespace, String libName, LinkedHashMap<Integer, String> entries)
		{
			this.flags = false;
			this.namespace = namespace;
			this.libName = libName;

			decodeMap = new LinkedHashMap<>();
			for (Entry<Integer, String> e : entries.entrySet())
				decodeMap.put(e.getKey(), e.getValue());

			encodeMap = getEncodingMap(decodeMap);
		}

		private ConstEnum(String namespace, String libName, File listFile) throws IOException
		{
			this.flags = false;
			this.namespace = namespace;
			this.libName = libName;
			decodeMap = new LinkedHashMap<>();

			List<String> lines = IOUtils.readFormattedTextFile(listFile);
			for (int i = 0; i < lines.size(); i++) {
				String line = lines.get(i);

				if (line.isEmpty())
					continue;

				String[] tokens = IOUtils.getKeyValuePair(listFile, line, i);
				decodeMap.put(Integer.parseInt(tokens[0], 16), tokens[1]);
			}

			encodeMap = getEncodingMap(decodeMap);
		}

		public ConstEnum(File f, boolean flags) throws IOException
		{
			this.flags = flags;
			List<String> lines = IOUtils.readFormattedTextFile(f);

			if (lines.size() < 3)
				throw new IOException(f.getName() + " is missing header lines.");

			namespace = lines.get(0);
			libName = lines.get(1);
			boolean reversed = Boolean.parseBoolean(lines.get(2));

			decodeMap = new LinkedHashMap<>();
			encodeMap = new LinkedHashMap<>();

			for (int i = 3; i < lines.size(); i++) {
				String line = lines.get(i);

				if (line.isEmpty())
					continue;

				if (reversed) {
					String[] tokens = IOUtils.getKeyValuePair(f, line, i);
					encodeMap.put(tokens[0], tokens[1]);
				}
				else {
					String[] tokens = IOUtils.getKeyValuePair(f, line, i);
					int index = (int) Long.parseLong(tokens[0], 16);
					decodeMap.put(index, tokens[1]);
				}
			}

			if (reversed)
				decodeMap = getDecodingMap(encodeMap);
			else
				encodeMap = getEncodingMap(decodeMap);
		}

		public void addOverrides(ConstEnum ce)
		{
			if (!namespace.equals(ce.namespace))
				throw new IllegalStateException("Tried merging enums with conflicting namespaces! " + namespace);

			if (!libName.equals(ce.libName))
				throw new IllegalStateException("Tried merging enums with conflicting library names! " + libName);

			encodeMap.putAll(ce.encodeMap);
			decodeMap.putAll(ce.decodeMap);
		}

		public String getNamespace()
		{
			return namespace;
		}

		public boolean isFlags()
		{
			return flags;
		}

		public boolean has(int id)
		{
			return (flags && id == 0) || decodeMap.containsKey(id);
		}

		public String getName(int id)
		{
			return decodeMap.get(id);
		}

		public void printSet()
		{
			System.out.println(namespace);
			for (Entry<Integer, String> e : decodeMap.entrySet())
				System.out.printf("%08X %s%n", e.getKey(), e.getValue());
			System.out.println();
		}

		public String getConstantString(int id)
		{
			if (flags)
				return getFlagsString(id);

			if (decodeMap.containsKey(id))
				return SyntaxConstants.CONSTANT_PREFIX + namespace + SyntaxConstants.CONSTANT_SEPARATOR + decodeMap.get(id);

			return String.format("%08X", id);
		}

		private String getFlagsString(int bits)
		{
			StringBuilder sb = new StringBuilder();
			sb.append(SyntaxConstants.EXPRESSION_PREFIX).append("Flags:").append(namespace).append(":");

			if (bits == 0) {
				if (decodeMap.containsKey(0))
					sb.append(decodeMap.get(0)); // allow enums to override 0
				else
					sb.append("0");

				return sb.toString();
			}

			int added = 0;
			for (Entry<Integer, String> e : decodeMap.entrySet()) {
				int flag = e.getKey();
				if ((bits & flag) != flag)
					continue;

				if (added++ != 0)
					sb.append("|");
				sb.append(e.getValue());

				bits &= ~flag;
			}

			if (bits != 0)
				sb.append(":").append(String.format("%X", bits));

			return sb.toString();
		}

		public Integer getFlagsValue(String flagString)
		{
			int bits = 0;
			String[] tokens = flagString.contains("|") ? flagString.split("\\|") : new String[] { flagString };
			for (String token : tokens) {
				Integer v = getID(token);
				if (v == null)
					return null;

				bits |= v;
			}
			return bits;
		}

		public boolean hasID(String name)
		{
			return encodeMap.containsKey(name);
		}

		public Integer getID(String name)
		{
			String idName = encodeMap.get(name);
			if (idName == null)
				return null;

			return (int) Long.parseLong(idName, 16);
		}

		public int getNumDefined()
		{
			assert (decodeMap.size() == encodeMap.size());
			return decodeMap.size();
		}

		public int getMinDefinedValue()
		{
			int v = Integer.MAX_VALUE;

			for (int i : decodeMap.keySet()) {
				if (i < v)
					v = i;
			}

			return v;
		}

		public int getMaxDefinedValue()
		{
			int v = Integer.MIN_VALUE;

			for (int i : decodeMap.keySet()) {
				if (i > v)
					v = i;
			}

			return v;
		}

		public List<EnumPair> getDecoding()
		{
			List<EnumPair> list = new ArrayList<>(decodeMap.size());
			for (Entry<Integer, String> e : decodeMap.entrySet())
				list.add(new EnumPair(e.getKey(), e.getValue()));
			Collections.sort(list);
			return list;
		}

		public String[] getValues()
		{
			String[] array = new String[decodeMap.size()];
			int i = 0;
			for (Entry<Integer, String> e : decodeMap.entrySet())
				array[i++] = e.getValue();
			Arrays.sort(array);
			return array;
		}

		public static class EnumPair implements Comparable<EnumPair>
		{
			public final int key;
			public final String value;

			private EnumPair(int key, String value)
			{
				this.key = key;
				this.value = value;
			}

			@Override
			public int compareTo(EnumPair other)
			{
				return key - other.key;
			}

			@Override
			public String toString()
			{
				return value;
			}
		}
	}

	public static class EnumComboBox extends JComboBox<EnumPair>
	{
		public EnumComboBox(ConstEnum e)
		{
			for (EnumPair pair : e.getDecoding()) {
				super.addItem(pair);
			}
		}

		public int getSelectedValue()
		{
			EnumPair selected = (EnumPair) getSelectedItem();
			return selected != null ? selected.key : 0;
		}

		public void selectByValue(int v)
		{
			for (int i = 0; i < getItemCount(); i++) {
				EnumPair pair = getItemAt(i);
				if (pair.key == v) {
					setSelectedItem(pair);
					return;
				}
			}
			super.addItem(new EnumPair(v, String.format("UNDEFINED %02X", v)));
		}
	}

	private static LinkedHashMap<Integer, String> getDecodingMap(LinkedHashMap<String, String> encodingMap)
	{
		LinkedHashMap<Integer, String> decodingMap = new LinkedHashMap<>();

		for (Entry<String, String> e : encodingMap.entrySet())
			decodingMap.put((int) Long.parseLong(e.getValue(), 16), e.getKey());

		return decodingMap;
	}

	private static LinkedHashMap<String, String> getEncodingMap(LinkedHashMap<Integer, String> decodingMap)
	{
		LinkedHashMap<String, String> encodingMap = new LinkedHashMap<>();

		for (Entry<Integer, String> e : decodingMap.entrySet())
			encodingMap.put(e.getValue(), String.format("%08X", e.getKey()));

		return encodingMap;
	}

	private static DualHashMap<Integer, String> readEffectTypes(File byteFile) throws IOException
	{
		DualHashMap<Integer, String> effectMap = new DualHashMap<>();
		List<String> lines = IOUtils.readFormattedTextFile(byteFile);

		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i);

			if (line.isEmpty() || !line.contains("="))
				continue;

			String[] tokens = IOUtils.getKeyValuePair(byteFile, line, i);

			int effectKey = Integer.parseInt(tokens[0], 16);
			effectMap.add(effectKey, tokens[1]);
		}

		return effectMap;
	}

	private static HashMap<String, ByteBuffer> loadStringConstants()
	{
		HashMap<String, ByteBuffer> constMap = new HashMap<>();
		if (Environment.project.isDecomp)
			return constMap;

		File f = new File(MOD_STRINGS + FN_STRING_CONSTANTS);
		if (!f.exists())
			return constMap;

		XmlReader xmr = new XmlReader(f);
		Element rootElem = xmr.getRootElement();
		for (Element elem : xmr.getTags(rootElem, StringConstKey.TAG_CONSTANT)) {
			xmr.requiresAttribute(elem, StringConstKey.ATTR_NAME);
			xmr.requiresAttribute(elem, StringConstKey.ATTR_VALUE);
			String key = xmr.getAttribute(elem, StringConstKey.ATTR_NAME);
			String text = xmr.getAttribute(elem, StringConstKey.ATTR_VALUE);
			if (!key.matches("\\w+"))
				xmr.complain("Invalid name for string constant: " + key);
			try {
				ByteBuffer bb = StringEncoder.encodeVar(text, true);
				constMap.put(key, bb);
			}
			catch (InputFileException e) {
				xmr.complain("Invalid name for string constant: " + key);
			}
		}
		return constMap;
	}

	public static boolean hasLibraryName(String libName)
	{
		return constLibMap.containsKey(libName);
	}

	public static ConstEnum getFromLibraryName(String libName)
	{
		return constLibMap.get(libName);
	}

	public static boolean hasNamespace(String namespace)
	{
		return constNameMap.containsKey(namespace);
	}

	public static ConstEnum getFromNamespace(String namespace)
	{
		return constNameMap.get(namespace);
	}

	public static void loadModGlobals() throws IOException
	{
		gameBytes.loadDictionary(new File(MOD_GLOBALS + FN_GAME_BYTES));
		gameFlags.loadDictionary(new File(MOD_GLOBALS + FN_GAME_FLAGS));

		modBytes.loadDictionary(new File(MOD_GLOBALS + FN_MOD_BYTES));
		modFlags.loadDictionary(new File(MOD_GLOBALS + FN_MOD_FLAGS));

		modFlagList = modFlags.getNameList();
	}

	public static void clearModGlobals() throws IOException
	{
		gameBytes.clear();
		gameFlags.clear();
		modBytes.clear();
		modFlags.clear();
		modFlagList.clear();
	}

	/*
	private static void readVariableDictionary(ScriptVariable type, DualHashMap<Integer, String> nameMap, File byteFile) throws IOException
	{
		nameMap.clear();

		if(!byteFile.exists())
			return;

		List<String> lines = IOUtils.readFormattedTextFile(byteFile);
		HashSet<Integer> indexSet = new HashSet<>();

		for(int i = 0; i < lines.size(); i++)
		{
			String line = lines.get(i);

			if(line.isEmpty() || !line.contains("="))
				continue;

			String[] tokens = IOUtils.getKeyValuePair(byteFile, line, i);
			String newName = SCRIPT_VAR_PREFIX + tokens[1];
			int offset = Integer.parseInt(tokens[0], 16);

			int max = type.getMaxIndex();
			if(offset < 0 || offset >= max)
				throw new InputFileException(byteFile, i, "%s index is out of range (0 to %X): %n%s", type.getTypeName(), max, line);

			if(indexSet.contains(offset))
				throw new InputFileException(byteFile, i, "Duplicate %s index: %n%s", type.getTypeName(), line);
			indexSet.add(offset);

			if(nameMap.containsInverse(newName))
				throw new InputFileException(byteFile, i, "Duplicate %s name: %n%s", type.getTypeName(), line);

			nameMap.add(offset, newName);
		}
	}
	 */

	/*
	private static void readGameBytes(DualHashMap<Integer, String> nameMap, File byteFile) throws IOException
	{
		List<String> lines = IOUtils.readTextFile(byteFile);
		HashSet<Integer> indexSet = new HashSet<>();
		nameMap.clear();

		for(int i = 0; i < lines.size(); i++)
		{
			String line = lines.get(i);

			if(line.isEmpty() || !line.contains("="))
				continue;

			String[] tokens = IOUtils.getKeyValuePair(byteFile, line, i);
			String newName = SCRIPT_VAR_PREFIX + tokens[1];
			int offset = Integer.parseInt(tokens[0], 16);

			int max = ScriptVariable.GameByte.getMaxValue();
			if(offset < 0 || offset >= max)
				throw new InputFileException(byteFile, i, "Saved byte index is out of range (0 to %X): %s", max, line);

			if(indexSet.contains(offset))
				throw new InputFileException(byteFile, i, "Duplicate saved byte index: ", line);
			indexSet.add(offset);

			if(nameMap.containsInverse(newName))
				throw new InputFileException(byteFile, i, "Duplicate saved byte name: " + line);

			nameMap.add(offset, newName);
		}
	}

	private static void readGameFlags(DualHashMap<Integer, String> nameMap, File flagFile) throws IOException
	{
		List<String> lines = IOUtils.readTextFile(flagFile);
		HashSet<Integer> indexSet = new HashSet<>();
		nameMap.clear();

		for(int i = 0; i < lines.size(); i++)
		{
			String line = lines.get(i);

			if(line.isEmpty() || !line.contains("="))
				continue;

			String[] tokens = IOUtils.getKeyValuePair(flagFile, line, i);
			String newName = SCRIPT_VAR_PREFIX + tokens[1];
			int offset = Integer.parseInt(tokens[0], 16);

			int max = ScriptVariable.GameFlag.getMaxValue();
			if(offset < 0 || offset >= max)
				throw new InputFileException(flagFile, i, "Saved flag index is out of range (0 to %X): %s", max, line);

			if(indexSet.contains(offset))
				throw new InputFileException(flagFile, i, "Duplicate saved flag index: ", line);
			indexSet.add(offset);

			if(nameMap.containsInverse(newName))
				throw new InputFileException(flagFile, i, "Saved flag may not share name with byte: " + line);

			if(nameMap.containsInverse(newName))
				throw new InputFileException(flagFile, i, "Duplicate saved flag name: " + line);

			nameMap.add(offset, newName);
		}
	}

	private static void readModItems(File file) throws IOException
	{
		List<String> lines = IOUtils.readTextFile(file);
		modItemMap.clear();

		for(int i = 0; i < lines.size(); i++)
		{
			String line = lines.get(i);

			if(line.isEmpty())
				continue;

			String[] tokens = IOUtils.getKeyValuePair(file, line, i);

			int index = (int)Long.parseLong(tokens[0], 16);
			modItemMap.put(tokens[1], String.format("%08X", index));
		}
	}
	 */

	public static String getDefaultGameByte(int index)
	{
		return dumpBytes.getName(index);
	}

	public static String getDefaultGameFlag(int index)
	{
		return dumpFlags.getName(index);
	}

	public static Integer getGameByte(String name)
	{
		return gameBytes.getIndex(name);
	}

	public static String getGameByte(int index)
	{
		return gameBytes.getName(index);
	}

	public static boolean isGameByteUnused(int index)
	{
		return !gameBytes.hasSemanticName(index);
	}

	public static Integer getGameFlag(String name)
	{
		return gameFlags.getIndex(name);
	}

	public static String getGameFlag(int index)
	{
		return gameFlags.getName(index);
	}

	public static boolean isGameFlagUnused(int index)
	{
		return !gameFlags.hasSemanticName(index);
	}

	public static Integer getModByte(String name)
	{
		return modBytes.getIndex(name);
	}

	public static String getModByte(int index)
	{
		return modBytes.getName(index);
	}

	public static Integer getModFlag(String name)
	{
		return modFlags.getIndex(name);
	}

	public static String getModFlag(int index)
	{
		return modFlags.getName(index);
	}

	public static List<String> getModFlagList()
	{
		return modFlagList;
	}

	public static ByteBuffer getStringConstant(String name)
	{
		return stringConstMap.get(name);
	}
}

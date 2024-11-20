package game.shared.decoder;

import static game.shared.StructTypes.NpcT;
import static game.shared.StructTypes.UnknownT;

import java.util.ArrayList;

import game.map.marker.Marker;
import game.shared.struct.StructType;
import game.shared.struct.script.Script.ScriptLine;
import game.world.entity.EntityInfo.EntityType;
import util.identity.IdentityHashSet;

public class Pointer
{
	private StructType type;
	public boolean typeSetByHint = false;

	// size and length of data structure
	public final int address;

	private int size = 0;
	private boolean sizeSetByHint = false;

	public int newlineHint = 0;

	private String descriptor = "";
	private String importName = ""; // used to name structs in auto-generated import files for NPCs and enemies

	private String overrideName = "";

	public String text = ""; // valid for ASCII after scanning

	// information about how this pointer was identified
	public enum Origin
	{
		UNIDENTIFIED, DECODED, HINT, HEURISTIC
	}

	public Origin origin = Origin.UNIDENTIFIED;
	public boolean root = false;

	// maintain a dependency graph of data structures
	public final IdentityHashSet<Pointer> children;
	public final IdentityHashSet<Pointer> parents;

	public final IdentityHashSet<Pointer> ancestors;

	// special fields for structs holding or belonging to arrays
	public int listLength = -1;
	public int listIndex = -1;

	// special fields used for scripts
	public ArrayList<ScriptLine> script = null;
	public Integer[] scriptExecArgs = null;
	public ArrayList<ScriptLine> bindLines = null; // set for scripts called via Bind

	public String mapName = "";

	// special fields used by specific struct types or instances
	// refactor these to use 'properties' if we start having too many of them
	public int npcBattleID = -1;
	public String foliageName = "";
	public int foliageCollider = -1;
	public int ptrBombPos = 0;

	public boolean isAPI; // for functions

	// for scripts assigned to an entity
	public EntityType assignedToType = null;
	public Marker assignedToMarker = null;

	/*
	private ArrayList<Property> properties = new ArrayList<>();

	// many special case fields
	public static enum PropertyType
	{
		NpcBattleID			(-1),
		FoliageColliderID	(-1),
		FoliageBombPos		(0);

		private final Object defaultVal;

		private PropertyType(Object defaultVal)
		{
			this.defaultVal = defaultVal;
		}
	}

	public class Property
	{
		private final PropertyType type;
		private Object obj;

		private Property(PropertyType type, Object obj)
		{
			this.type = type;
			this.obj = obj;
		}

		public Object getValue()
		{
			return obj;
		}
	}

	public Object setProperty(PropertyType type, Object obj)
	{
		Property prop = null;
		for(Property p : properties)
		{
			if(p.type == type)
			{
				prop = p;
				break;
			}
		}

		Object prev = null;
		if(prop != null)
		{
			prev = prop.getValue();
			prop.obj = obj;
		}
		else
		{
			prop = new Property(type, obj);
			properties.add(prop);
		}
		return prev;
	}

	public Object getProperty(PropertyType type)
	{
		for(Property p : properties)
		{
			if(p.type == type)
				return p.obj;
		}
		return type.defaultVal;
	}

	public boolean hasProperty(PropertyType type)
	{
		for(Property p : properties)
		{
			if(p.type == type)
				return true;
		}
		return false;
	}
	*/

	public boolean hasKnownSize()
	{
		return type.hasKnownSize || size != 0;
	}

	public int getSize()
	{
		return size;
	}

	public boolean setSize(int newSize)
	{
		if (sizeSetByHint)
			return false;

		size = newSize;
		return true;
	}

	public Pointer(int address)
	{
		this.address = address;
		parents = new IdentityHashSet<>();
		children = new IdentityHashSet<>();

		ancestors = new IdentityHashSet<>();
		type = UnknownT;
	}

	public Pointer(Hint h)
	{
		this(h.address);
		useHints(h);
	}

	public void useHints(Hint h)
	{
		if (h.hasType()) {
			type = h.getTypeSuggestion();
			typeSetByHint = true;
		}

		if (h.hasLength()) {
			size = h.getLengthSuggestion();
			sizeSetByHint = true;
		}

		if (h.hasWordsPerRow())
			newlineHint = h.getWordsPerRowSuggestion();

		if (h.hasName())
			forceName(h.getNameSuggestion());
	}

	public void addUniqueChild(Pointer child)
	{
		children.add(child);
		child.parents.add(this);
	}

	public boolean isTypeUnknown()
	{
		return type == UnknownT;
	}

	public StructType getType()
	{
		return type;
	}

	public void setType(StructType type)
	{
		setType(type, false);
	}

	public void setType(StructType type, boolean overrideHints)
	{
		if (!overrideHints && origin == Origin.HINT && type != UnknownT)
			return; // type was fixed by hint

		this.type = type;
	}

	public void setImportAffix(String suffix)
	{
		//	assert(this.importName.isEmpty());
		this.importName = suffix;
	}

	public String getDescriptor()
	{
		return descriptor;
	}

	public void setDescriptor(String descriptor)
	{
		this.descriptor = descriptor;
	}

	public void forceName(String name)
	{
		overrideName = name;
	}

	public boolean nameWasOverriden()
	{
		return !overrideName.isEmpty();
	}

	private String getNpcSuffix()
	{
		return (listIndex == 0) ? "" : String.format("_%03X", listIndex * 0x1F0);
	}

	private String getBaseName()
	{
		// special case for NPCs
		if (type.isTypeOf(NpcT)) {
			Pointer parent = parents.iterator().next();
			return parent.getBaseName() + getNpcSuffix();
		}

		// special case for indexed battle formations
		if (listIndex >= 0)
			return String.format("_%02X", listIndex);

		// no need to print address for unique structs
		if (type.isUnique) {
			if (descriptor.isEmpty())
				return "";
			else
				return "_" + descriptor;
		}
		else {
			if (descriptor.isEmpty())
				return String.format("_%08X", address);
			else
				return "_" + descriptor + String.format("_%08X", address);
		}
	}

	// Used for import pointer names and import file names:
	// Suffix_(Descriptor_Address|OverrideName)
	public String getImportName()
	{
		if (type.isTypeOf(NpcT)) {
			Pointer parent = parents.iterator().next();
			return parent.getImportName() + getNpcSuffix();
		}

		if (!overrideName.isEmpty())
			return String.format(importName + "_" + overrideName);

		return String.format(importName + getBaseName());
	}

	// Used for pointer names in source files:
	// $Type_(Descriptor_Address|OverrideName)_Suffix
	// $Formation_Index_Suffix == $Type_(Index|OverrideName)_Suffix
	// $End[XX]
	public String getPointerName()
	{
		// unique types always have a reliable name, no descriptor or suffix allowed
		if (type.isUnique)
			return "$" + type.toString() + getBaseName();

		// special case for NPCs
		if (type.isTypeOf(NpcT)) {
			Pointer parent = parents.iterator().next();
			String typePrefix = parent.type.toString();
			String importSuffix = parent.importName.isEmpty() ? "" : "_" + parent.importName;
			return "$" + typePrefix + parent.getBaseName() + getNpcSuffix() + importSuffix;
		}

		String typePrefix = (type.isTypeOf(UnknownT)) ? "???" : type.toString();
		String importSuffix = importName.isEmpty() ? "" : "_" + importName;

		if (!overrideName.isEmpty())
			return "$" + overrideName + importSuffix;

		return "$" + typePrefix + getBaseName() + importSuffix;
	}
}

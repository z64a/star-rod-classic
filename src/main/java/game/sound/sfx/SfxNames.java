package game.sound.sfx;

import static game.sound.sfx.SfxXmlKey.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import util.xml.XmlWrapper.XmlReader;

public final class SfxNames
{
	public static final String RESOURCE_SOUNDS = "/audio/Sounds.xml";

	private static final Pattern SOUND_ID = Pattern.compile("[0-9A-F]{4}");
	private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

	private final Map<Integer, String> names = new LinkedHashMap<>();
	private final Set<Integer> unusedIDs = new HashSet<>();
	private final Set<Integer> emptyIDs = new HashSet<>();
	private final Map<Integer, String> descriptions = new LinkedHashMap<>();
	private final Map<Integer, List<String>> tags = new LinkedHashMap<>();

	public String get(int id)
	{
		return names.get(id);
	}

	public boolean isUnused(int id)
	{
		return unusedIDs.contains(id);
	}

	public boolean isEmpty(int id)
	{
		return emptyIDs.contains(id);
	}

	public String getDescription(int id)
	{
		return descriptions.getOrDefault(id, "");
	}

	public List<String> getTags(int id)
	{
		return tags.getOrDefault(id, List.of());
	}

	public Iterable<Map.Entry<Integer, String>> entries()
	{
		return names.entrySet();
	}

	public boolean contains(int id)
	{
		return names.containsKey(id);
	}

	public boolean hasAuthoritativeName(int id)
	{
		String name = get(id);
		return name != null && !isGeneratedName(id, name);
	}

	public boolean hasGeneratedName(int id)
	{
		String name = get(id);
		return name != null && isGeneratedName(id, name);
	}

	public boolean shouldMaterializeEmpty(int id)
	{
		return hasAuthoritativeName(id) || isEmpty(id);
	}

	public boolean hasEmptyName(int id)
	{
		return isEmpty(id);
	}

	public String preferredName(int id)
	{
		String name = get(id);
		return name == null ? nameMissing(id) : name;
	}

	public static String nameMissing(int id)
	{
		return String.format("Unk_%04X", id);
	}

	public static String unusedName(int id)
	{
		return String.format("Unused_%04X", id);
	}

	public static String emptyName(int id)
	{
		return String.format("Empty_%04X", id);
	}

	public static String invalidName(int id)
	{
		return String.format("Invalid_%04X", id);
	}

	private static boolean isGeneratedName(int id, String name)
	{
		return nameMissing(id).equals(name)
			|| unusedName(id).equals(name)
			|| emptyName(id).equals(name)
			|| invalidName(id).equals(name);
	}

	void add(int id, String name)
	{
		if (isRawSoundID(id))
			names.put(id, name);
	}

	void setMetadata(int id, boolean unused, boolean empty, String desc, List<String> idTags)
	{
		if (unused)
			unusedIDs.add(id);
		if (empty)
			emptyIDs.add(id);
		if (desc != null && !desc.isEmpty())
			descriptions.put(id, desc);
		if (idTags != null && !idTags.isEmpty())
			tags.put(id, List.copyOf(idTags));
	}

	public static SfxNames loadBundled() throws IOException
	{
		InputStream stream = SfxNames.class.getResourceAsStream(RESOURCE_SOUNDS);
		if (stream == null)
			throw new IOException("Missing bundled " + RESOURCE_SOUNDS);

		try (InputStream input = stream) {
			return read(new XmlReader(input, RESOURCE_SOUNDS));
		}
	}

	public static SfxNames load(Path path) throws IOException
	{
		return read(new XmlReader(path.toFile()));
	}

	private static SfxNames read(XmlReader xmr)
	{
		Element root = xmr.getRootElement();
		if (!root.getTagName().equals(TAG_SOUNDS.toString()))
			xmr.complain("Expected root tag: " + TAG_SOUNDS);
		for (Node child = root.getFirstChild(); child != null; child = child.getNextSibling()) {
			if (child instanceof Element element && !element.getTagName().equals(TAG_SOUND.toString()))
				xmr.complain("Unknown element in " + TAG_SOUNDS + ": " + element.getTagName());
		}

		SfxNames result = new SfxNames();
		Set<Integer> ids = new HashSet<>();
		Set<String> identifiers = new HashSet<>();
		for (Element element : xmr.getTags(root, TAG_SOUND)) {
			for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
				if (child instanceof Element childElement)
					xmr.complain("Sound defaults cannot contain child element: " + childElement.getTagName());
			}
			xmr.requiresAttribute(element, ATTR_ID);
			xmr.requiresAttribute(element, ATTR_NAME);

			String idText = xmr.getAttribute(element, ATTR_ID);
			if (!SOUND_ID.matcher(idText).matches())
				xmr.complain("Sound ID must be four uppercase hexadecimal digits: " + idText);
			int id = xmr.readHex(element, ATTR_ID);
			if (!isRawSoundID(id))
				xmr.complain("Sound ID is outside 0001-03FF and 2001-2140: " + idText);
			if (!ids.add(id))
				xmr.complain("Duplicate sound ID: " + idText);

			String name = readIdentifier(xmr, element);
			if (!identifiers.add(name))
				xmr.complain("Duplicate sound name: " + name);
			result.add(id, name);

			boolean unused = readTrueFlag(xmr, element, ATTR_UNUSED);
			boolean empty = readTrueFlag(xmr, element, ATTR_EMPTY);
			if (unused && empty)
				xmr.complain(String.format("Sound %04X cannot be both unused and empty", id));
			String desc = xmr.hasAttribute(element, ATTR_DESC)
				? xmr.getAttribute(element, ATTR_DESC)
				: "";
			List<String> idTags = List.of();
			if (xmr.hasAttribute(element, ATTR_TAGS)) {
				idTags = xmr.readStringList(element, ATTR_TAGS);
				for (String tag : idTags) {
					if (tag.isBlank())
						xmr.complain(String.format("Sound %04X has a blank tag", id));
				}
				if (new LinkedHashSet<>(idTags).size() != idTags.size())
					xmr.complain(String.format("Sound %04X has duplicate tags", id));
			}
			result.setMetadata(id, unused, empty, desc, idTags);
		}
		return result;
	}

	private static String readIdentifier(XmlReader xmr, Element element)
	{
		String name = xmr.getAttribute(element, ATTR_NAME);
		if (!IDENTIFIER.matcher(name).matches())
			xmr.complain("Invalid sound name: " + name);
		return name;
	}

	private static boolean readTrueFlag(XmlReader xmr, Element element, SfxXmlKey key)
	{
		if (!xmr.hasAttribute(element, key))
			return false;
		if (!xmr.readBoolean(element, key, false))
			xmr.complain(key + ", when present, must be true");
		return true;
	}

	public static boolean isRawSoundID(int id)
	{
		return (id >= 0x0001 && id <= 0x03FF) || (id >= 0x2001 && id <= 0x2140);
	}
}

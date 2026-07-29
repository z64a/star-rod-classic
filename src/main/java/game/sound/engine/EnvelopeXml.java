package game.sound.engine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import util.xml.XmlKey;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public final class EnvelopeXml
{
	private enum Key implements XmlKey
	{
		// @formatter:off
		TAG_POINT      ("Point"),
		TAG_SET_SCALE  ("SetScale"),
		TAG_ADD_SCALE  ("AddScale"),
		TAG_START_LOOP ("StartLoop"),
		TAG_END_LOOP   ("EndLoop"),
		TAG_END        ("End"),
		ATTR_DURATION  ("duration"),
		ATTR_VALUE     ("value"),
		ATTR_COUNT     ("count");
		// @formatter:on

		private final String key;

		Key(String key)
		{
			this.key = key;
		}

		@Override
		public String toString()
		{
			return key;
		}

		private static Key forTag(String name)
		{
			for (Key key : values()) {
				if (key.name().startsWith("TAG_") && key.key.equals(name))
					return key;
			}
			return null;
		}
	}

	private EnvelopeXml()
	{}

	public static List<EnvelopeCommand> readCommands(XmlReader reader, Element parent)
	{
		List<EnvelopeCommand> commands = new ArrayList<>();
		for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
			if (!(child instanceof Element element)) {
				if ((child.getNodeType() == Node.TEXT_NODE || child.getNodeType() == Node.CDATA_SECTION_NODE)
					&& !child.getTextContent().isBlank())
					reader.complain("Text content is not allowed in " + parent.getTagName());
				continue;
			}

			Key key = Key.forTag(element.getTagName());
			if (key == null) {
				reader.complain("Unknown envelope command: " + element.getTagName());
				continue;
			}

			EnvelopeCommand command;
			switch (key) {
				case TAG_POINT:
					checkAttributes(reader, element, Key.ATTR_DURATION, Key.ATTR_VALUE);
					command = new EnvelopeCommand(EnvelopeOp.POINT,
						readInt(reader, element, Key.ATTR_VALUE, 0, EnvelopeProgram.MAX_VOLUME));
					reader.requiresAttribute(element, Key.ATTR_DURATION);
					try {
						command.durationIndex = EnvelopeTimes.indexForToken(reader.getAttribute(element, Key.ATTR_DURATION));
					}
					catch (IllegalArgumentException e) {
						reader.complain(e.getMessage());
					}
					break;
				case TAG_SET_SCALE:
					checkAttributes(reader, element, Key.ATTR_VALUE);
					command = new EnvelopeCommand(EnvelopeOp.SET_SCALE,
						readInt(reader, element, Key.ATTR_VALUE, 0, EnvelopeProgram.MAX_SCALE));
					break;
				case TAG_ADD_SCALE:
					checkAttributes(reader, element, Key.ATTR_VALUE);
					command = new EnvelopeCommand(EnvelopeOp.ADD_SCALE,
						readInt(reader, element, Key.ATTR_VALUE, -128, 127));
					break;
				case TAG_START_LOOP:
					checkAttributes(reader, element, Key.ATTR_COUNT);
					command = new EnvelopeCommand(EnvelopeOp.START_LOOP,
						readInt(reader, element, Key.ATTR_COUNT, 0, 255));
					break;
				case TAG_END_LOOP:
					checkAttributes(reader, element);
					command = new EnvelopeCommand(EnvelopeOp.END_LOOP);
					break;
				case TAG_END:
					checkAttributes(reader, element);
					command = new EnvelopeCommand(EnvelopeOp.END);
					break;
				default:
					reader.complain("Unknown envelope command: " + element.getTagName());
					continue;
			}

			if (element.hasChildNodes()) {
				for (Node content = element.getFirstChild(); content != null; content = content.getNextSibling()) {
					if (content instanceof Element || !content.getTextContent().isBlank())
						reader.complain(element.getTagName() + " must be empty");
				}
			}
			commands.add(command);
		}

		try {
			EnvelopeProgram.validate(commands);
		}
		catch (IllegalArgumentException e) {
			reader.complain(e.getMessage());
		}
		return commands;
	}

	public static void writeCommands(XmlWriter writer, List<EnvelopeCommand> commands)
	{
		EnvelopeProgram.validate(commands);
		for (EnvelopeCommand command : commands) {
			XmlTag tag;
			switch (command.op) {
				case POINT:
					tag = writer.createTag(Key.TAG_POINT, true);
					writer.addAttribute(tag, Key.ATTR_DURATION, EnvelopeTimes.tokenForIndex(command.durationIndex));
					writer.addInt(tag, Key.ATTR_VALUE, command.value);
					break;
				case SET_SCALE:
					tag = writer.createTag(Key.TAG_SET_SCALE, true);
					writer.addInt(tag, Key.ATTR_VALUE, command.value);
					break;
				case ADD_SCALE:
					tag = writer.createTag(Key.TAG_ADD_SCALE, true);
					writer.addInt(tag, Key.ATTR_VALUE, command.value);
					break;
				case START_LOOP:
					tag = writer.createTag(Key.TAG_START_LOOP, true);
					writer.addInt(tag, Key.ATTR_COUNT, command.value);
					break;
				case END_LOOP:
					tag = writer.createTag(Key.TAG_END_LOOP, true);
					break;
				case END:
					tag = writer.createTag(Key.TAG_END, true);
					break;
				default:
					throw new IllegalArgumentException("Unknown envelope operation: " + command.op);
			}
			writer.printTag(tag);
		}
	}

	private static int readInt(XmlReader reader, Element element, Key key, int min, int max)
	{
		reader.requiresAttribute(element, key);
		int value = reader.readInt(element, key);
		if (value < min || value > max)
			reader.complain(key + " must be between " + min + " and " + max + ": " + value);
		return value;
	}

	private static void checkAttributes(XmlReader reader, Element element, Key ... allowed)
	{
		Set<String> names = new HashSet<>();
		for (Key key : allowed)
			names.add(key.toString());

		NamedNodeMap attributes = element.getAttributes();
		for (int i = 0; i < attributes.getLength(); i++) {
			String name = attributes.item(i).getNodeName();
			if (!names.contains(name))
				reader.complain("Unknown attribute for " + element.getTagName() + ": " + name);
		}
	}
}

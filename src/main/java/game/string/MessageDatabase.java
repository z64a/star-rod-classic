package game.string;

import static app.Directories.MOD_STRINGS_PATCH;
import static app.Directories.MOD_STRINGS_SRC;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

import app.StarRodException;
import app.input.IOUtils;
import game.string.editor.io.StringResource;
import util.IterableListModel;
import util.Logger;

/**
 * Index of project messages that already have fixed numeric IDs.
 */
public final class MessageDatabase
{
	private static final Pattern GROUP_INDEX_PATTERN = Pattern.compile("[0-9A-Fa-f]{1,4}-[0-9A-Fa-f]{1,4}");
	private static final Pattern ID_PATTERN = Pattern.compile("[0-9A-Fa-f]{1,8}");

	private final IterableListModel<PMString> messages = new IterableListModel<>();
	private final Map<String, PMString> messagesByName = new HashMap<>();
	private final Map<Integer, PMString> messagesByID = new HashMap<>();

	public void load()
	{
		messages.removeAllElements();
		messagesByName.clear();
		messagesByID.clear();

		Map<Integer, PMString> indexedMessages = new TreeMap<>();
		Map<String, Integer> indexedNames = new HashMap<>();
		try {
			loadMessages(IOUtils.getFilesWithExtension(MOD_STRINGS_SRC, new String[] { "str", "msg" }, true), indexedMessages, indexedNames);
			loadMessages(IOUtils.getFilesWithExtension(MOD_STRINGS_PATCH, new String[] { "str", "msg" }, true), indexedMessages, indexedNames);
		}
		catch (IOException e) {
			throw new StarRodException("Exception while loading project messages! %n%s", e.getMessage());
		}

		for (PMString message : indexedMessages.values()) {
			messages.addElement(message);
			messagesByID.put(message.getID(), message);
			messagesByName.put(message.getIDName(), message);
			if (message.hasName())
				messagesByName.put(message.name, message);
		}

		Logger.logf("Loaded %d indexed messages", messages.getSize());
	}

	private void loadMessages(Collection<File> messageFiles, Map<Integer, PMString> indexedMessages, Map<String, Integer> indexedNames)
	{
		for (File file : messageFiles) {
			StringResource resource = new StringResource(file);
			for (PMString message : resource.strings) {
				if (!message.indexed || message.autoAssign)
					continue;

				int id = message.getID();
				PMString previousAtID = indexedMessages.put(id, message);
				if (previousAtID != null && previousAtID.hasName() && !previousAtID.name.equals(message.name))
					indexedNames.remove(previousAtID.name, id);

				if (message.hasName()) {
					Integer previousID = indexedNames.put(message.name, id);
					if (previousID != null && previousID != id) {
						PMString previousByName = indexedMessages.get(previousID);
						if (previousByName != null && message.name.equals(previousByName.name))
							indexedMessages.remove(previousID);
					}
				}
			}
		}
	}

	public IterableListModel<PMString> getMessages()
	{
		return messages;
	}

	public PMString getMessage(String identifier)
	{
		if (identifier == null || identifier.isEmpty())
			return null;

		PMString message = messagesByName.get(identifier);
		if (message != null)
			return message;

		if (GROUP_INDEX_PATTERN.matcher(identifier).matches()) {
			String[] parts = identifier.split("-");
			int group = Integer.parseInt(parts[0], 16);
			int index = Integer.parseInt(parts[1], 16);
			return messagesByID.get((group << 16) | (index & 0xFFFF));
		}

		if (ID_PATTERN.matcher(identifier).matches())
			return messagesByID.get((int) Long.parseLong(identifier, 16));

		return null;
	}

	public Integer getMessageID(String identifier)
	{
		if (identifier == null || identifier.isEmpty())
			return 0;

		PMString message = getMessage(identifier);
		return message == null ? null : message.getID();
	}
}

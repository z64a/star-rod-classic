package game.string;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import app.AssetManager;
import app.input.InputFileException;
import game.string.editor.io.StringResource;
import patcher.RomPatcher;
import util.Logger;

public class StringPatcher
{
	private final ArrayList<StringSection> sections;

	private final HashMap<String, PMString> namedStringMap;

	public StringPatcher()
	{
		sections = new ArrayList<>();
		namedStringMap = new HashMap<>();
	}

	public void readAllStrings() throws IOException
	{
		for (StringResource asset : AssetManager.getStringAssets()) {
			Logger.log("Reading strings from: " + asset.file.getName());
			readStringAsset(asset);
		}

		for (StringSection section : sections)
			section.prepareForWriting();
	}

	private void readStringAsset(StringResource asset) throws IOException
	{
		List<PMString> stringList = StringEncoder.parseStrings(asset);

		for (PMString string : stringList) {
			if (string.section > 0xFF || string.section < 0)
				throw new IOException("Invalid string section in file " + asset.file.getName());

			if (string.index > 0xFFFF)
				throw new IOException("Invalid string index in file " + asset.file.getName());

			if (string.hasName())
				namedStringMap.put(string.name, string);

			if (string.parseException != null)
				throw new InputFileException(string.parseException);

			getSection(string.section).addString(string);
		}
	}

	/**
	 * Gets a string section by its index, adding new string sections if required.
	 */
	private StringSection getSection(int index)
	{
		for (int i = 0; i <= index; i++) {
			if (i >= sections.size()) {
				sections.add(new StringSection(i));
			}
		}

		return sections.get(index);
	}

	public void writeStrings(RomPatcher patcher) throws IOException
	{
		int sectionTableSize = 4 * sections.size();
		int allTableSize = sectionTableSize;

		// read all string sections from input files
		for (StringSection section : sections) {
			allTableSize += section.getOffsetTableSize();
		}

		int limit = 0x1C84D30;
		patcher.clear(0x1B83000, 0x1C84D30);

		patcher.seek("Strings", 0x1B83000 + allTableSize);
		for (StringSection section : sections) {
			if (patcher.getCurrentOffset() + section.getStringSize() > limit) {
				// only occurs once, after space is exhausted
				//	patcher.clearAndInvalidate(patcher.getCurrentOffset(), limit);

				patcher.seek("Strings", patcher.nextAlignedOffset());
				limit = Integer.MAX_VALUE;
			}

			section.writeStrings(patcher);
		}

		ArrayList<Integer> sectionOffsets = new ArrayList<>(sections.size());
		patcher.seek("String Offsets", 0x1B83000 + sectionTableSize);

		for (StringSection section : sections) {
			Logger.logf("Writing string section %02X to %X (%d strings)",
				section.section, patcher.getCurrentOffset(), section.getNumStrings());
			sectionOffsets.add(patcher.getCurrentOffset() - 0x01B83000);
			section.writeStringOffsetTable(patcher);
		}

		patcher.seek("String Section Offsets", 0x1B83000);
		for (Integer offset : sectionOffsets)
			patcher.writeInt(offset);
	}

	public boolean namedStringExists(String name)
	{
		return namedStringMap.containsKey(name);
	}

	public PMString getNamedString(String name)
	{
		return namedStringMap.get(name);
	}

	public HashMap<String, Integer> getStringIDMap()
	{
		HashMap<String, Integer> stringIDMap = new HashMap<>();

		for (Entry<String, PMString> e : namedStringMap.entrySet())
			stringIDMap.put(e.getKey(), e.getValue().getID());

		return stringIDMap;
	}
}

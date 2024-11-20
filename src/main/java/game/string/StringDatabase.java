package game.string;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import app.AssetManager;
import app.input.InputFileException;
import game.string.editor.io.StringResource;
import util.Logger;

public class StringDatabase
{
	private final ArrayList<StringSection> sections = new ArrayList<>();

	private final HashMap<Integer, PMString> idLookup = new HashMap<>();
	private final HashMap<String, PMString> nameLookup = new HashMap<>();

	public void readAllStrings() throws IOException
	{
		for (StringResource asset : AssetManager.getStringAssets()) {
			Logger.log("Reading strings from: " + asset.file.getName());
			readStringAsset(asset);
		}

		for (StringSection section : sections) {
			section.prepareForWriting();

			for (PMString str : section.stringTable)
				if (str != null)
					idLookup.put(str.getID(), str);
		}
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
				nameLookup.put(string.name, string);

			if (string.parseException != null)
				throw new InputFileException(string.parseException);

			getSection(string.section).addString(string);
		}
	}

	private StringSection getSection(int index)
	{
		for (int i = 0; i <= index; i++) {
			if (i >= sections.size()) {
				sections.add(new StringSection(i));
			}
		}

		return sections.get(index);
	}

	public PMString getString(int i)
	{
		return idLookup.get(i);
	}
}

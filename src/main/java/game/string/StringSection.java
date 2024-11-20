package game.string;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;
import java.util.TreeMap;

import patcher.RomPatcher;

public class StringSection
{
	public final int section;
	private final TreeMap<Integer, PMString> strings;
	private final Queue<PMString> autoIndexedStrings;
	public int greatestID; //XXX
	private int totalLength;

	public PMString[] stringTable; //XXX
	private int[] offsetTable;

	private boolean writeReady = false;

	public StringSection(int section)
	{
		this.section = section;
		greatestID = -1;
		totalLength = 0;

		strings = new TreeMap<>();
		autoIndexedStrings = new LinkedList<>();
	}

	public void addString(PMString string)
	{
		assert (string.section == section);
		assert (!writeReady);

		if (string.autoAssign)
			autoIndexedStrings.add(string);
		else
			strings.put(string.index, string); // TODO: if replacing an existing string, error if they are defined within the same file

		totalLength += string.bytes.length;
		if (!string.autoAssign && string.index > greatestID)
			greatestID = string.index;
	}

	public int getNumStrings()
	{
		return greatestID + 1;
	}

	public void prepareForWriting()
	{
		int emptyPositions = (greatestID + 1) - strings.size();
		if (autoIndexedStrings.size() > emptyPositions)
			greatestID += autoIndexedStrings.size() - emptyPositions;

		stringTable = new PMString[getNumStrings()];
		offsetTable = new int[getNumStrings() + 1];

		for (int i = 0; i <= greatestID; i++) {
			if (strings.containsKey(i)) {
				stringTable[i] = strings.get(i);
			}
			else if (!autoIndexedStrings.isEmpty()) {
				PMString string = autoIndexedStrings.poll();
				string.index = i;
				stringTable[i] = string;
			}
		}

		assert (autoIndexedStrings.size() == 0);
		writeReady = true;
	}

	public void writeStrings(RomPatcher rp) throws IOException
	{
		assert (writeReady);

		/* write string bytes */
		for (int i = 0; i < stringTable.length; i++) {
			offsetTable[i] = rp.getCurrentOffset() - 0x1B83000;
			if (stringTable[i] != null)
				rp.write(stringTable[i].bytes);
			else
				rp.writeInt(0x253232FD);
		}
		// get the final offset to indicate end of last string
		offsetTable[stringTable.length] = rp.getCurrentOffset() - 0x1B83000;
	}

	public int getStringSize()
	{
		assert (writeReady);

		return totalLength + 4 * (getNumStrings() - strings.size());
	}

	public void writeStringOffsetTable(RomPatcher rp) throws IOException
	{
		assert (writeReady);

		for (int i = 0; i < offsetTable.length; i++)
			rp.writeInt(offsetTable[i]);
	}

	public int getOffsetTableSize()
	{
		assert (writeReady);

		return offsetTable.length * 4;
	}
}

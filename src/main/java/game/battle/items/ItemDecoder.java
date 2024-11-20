package game.battle.items;

import static app.Directories.DUMP_ITEM_RAW;
import static app.Directories.DUMP_ITEM_SRC;
import static game.battle.BattleConstants.ITEM_BASE;
import static game.battle.BattleConstants.ITEM_RAM_LIMIT;
import static game.shared.StructTypes.ScriptT;
import static game.shared.StructTypes.UseScriptT;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import game.battle.BaseBattleDecoder;
import game.battle.moves.MoveDecoder.MoveSectionData;
import game.shared.decoder.Pointer.Origin;

public class ItemDecoder extends BaseBattleDecoder
{
	public ItemDecoder(ByteBuffer fileBuffer, MoveSectionData data) throws IOException
	{
		super();

		sourceName = data.name;

		int endAddress = (data.end - data.start) + ITEM_BASE;
		setAddressRange(ITEM_BASE, endAddress, ITEM_RAM_LIMIT);
		setOffsetRange(data.start, data.end);

		findLocalPointers(fileBuffer);

		for (int i = 0; i < data.mainAddressList.size(); i++)
			enqueueAsRoot(data.mainAddressList.get(i), UseScriptT, Origin.DECODED, data.mainNameList.get(i));

		super.decode(fileBuffer);

		File rawFile = new File(DUMP_ITEM_RAW + sourceName + ".bin");
		File scriptFile = new File(DUMP_ITEM_SRC + sourceName + ".bscr");
		File indexFile = new File(DUMP_ITEM_SRC + sourceName + ".bidx");

		printScriptFile(scriptFile, fileBuffer);
		printIndexFile(indexFile);
		writeRawFile(rawFile, fileBuffer);
	}

	public ItemDecoder(ByteBuffer fileBuffer, String scriptName, int start, int end, int main, int ... scriptHints) throws IOException
	{
		super();

		sourceName = scriptName;

		int endAddress = (end - start) + ITEM_BASE;
		setAddressRange(ITEM_BASE, endAddress, ITEM_RAM_LIMIT);
		setOffsetRange(start, end);

		findLocalPointers(fileBuffer);
		enqueueAsRoot(main, UseScriptT, Origin.DECODED).forceName("Script_UseItem");

		for (int i : scriptHints)
			enqueueAsRoot(i, ScriptT, Origin.HINT);

		super.decode(fileBuffer);

		File rawFile = new File(DUMP_ITEM_RAW + sourceName + ".bin");
		File scriptFile = new File(DUMP_ITEM_SRC + sourceName + ".bscr");
		File indexFile = new File(DUMP_ITEM_SRC + sourceName + ".bidx");

		printScriptFile(scriptFile, fileBuffer);
		printIndexFile(indexFile);
		writeRawFile(rawFile, fileBuffer);
	}
}

package game.battle.moves;

import static app.Directories.DUMP_MOVE_RAW;
import static app.Directories.DUMP_MOVE_SRC;
import static game.battle.BattleConstants.MOVE_BASE;
import static game.battle.BattleConstants.MOVE_RAM_LIMIT;
import static game.shared.StructTypes.UseScriptT;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

import game.battle.BaseBattleDecoder;
import game.shared.decoder.Pointer.Origin;

public class MoveDecoder extends BaseBattleDecoder
{
	public static final class MoveSectionData
	{
		public final String name;
		public final int start;
		public final int end;

		public final List<Integer> mainAddressList;
		public final List<String> mainNameList;

		public MoveSectionData(String name, int start, int end)
		{
			this.name = name;
			this.start = start;
			this.end = end;
			mainAddressList = new LinkedList<>();
			mainNameList = new LinkedList<>();
		}
	}

	public MoveDecoder(ByteBuffer fileBuffer, MoveSectionData scriptData) throws IOException
	{
		super();

		startOffset = scriptData.start;
		endOffset = scriptData.end;
		sourceName = scriptData.name;

		int startAddress = MOVE_BASE;
		int endAddress = (endOffset - startOffset) + MOVE_BASE;
		setAddressRange(startAddress, endAddress, MOVE_RAM_LIMIT);
		setOffsetRange(scriptData.start, scriptData.end);

		findLocalPointers(fileBuffer);
		for (int i = 0; i < scriptData.mainAddressList.size(); i++) {
			int addr = scriptData.mainAddressList.get(i);
			String name = scriptData.mainNameList.get(i);
			enqueueAsRoot(addr, UseScriptT, Origin.DECODED).forceName(name);
		}

		super.decode(fileBuffer);

		File rawFile = new File(DUMP_MOVE_RAW + sourceName + ".bin");
		File scriptFile = new File(DUMP_MOVE_SRC + sourceName + ".bscr");
		File indexFile = new File(DUMP_MOVE_SRC + sourceName + ".bidx");

		printScriptFile(scriptFile, fileBuffer);
		printIndexFile(indexFile);
		writeRawFile(rawFile, fileBuffer);
	}
}

package game.world.action;

import static app.Directories.DUMP_ACTION_RAW;
import static app.Directories.DUMP_ACTION_SRC;
import static game.shared.StructTypes.FunctionT;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import game.shared.decoder.DumpMetadata;
import game.shared.decoder.Pointer.Origin;
import game.world.BaseWorldDecoder;

public class ActionDecoder extends BaseWorldDecoder
{
	public static final class ActionSectionData
	{
		public final String name;
		public final int start;
		public final int end;

		public final ArrayList<Integer> mainAddressList;
		public final ArrayList<String> mainNameList;

		public ActionSectionData(String name, int start, int end)
		{
			this.name = name;
			this.start = start;
			this.end = end;
			mainAddressList = new ArrayList<>();
			mainNameList = new ArrayList<>();
		}
	}

	public ActionDecoder(ByteBuffer fileBuffer, DumpMetadata metadata, ActionSectionData entry) throws IOException
	{
		super();

		useDumpMetadata(metadata);

		findLocalPointers(fileBuffer);

		for (int i = 0; i < entry.mainAddressList.size(); i++)
			enqueueAsRoot(entry.mainAddressList.get(i), FunctionT, Origin.DECODED, entry.mainNameList.get(i));

		super.decode(fileBuffer);

		File rawFile = new File(DUMP_ACTION_RAW + sourceName + ".bin");
		File scriptFile = new File(DUMP_ACTION_SRC + sourceName + ".wscr");
		File indexFile = new File(DUMP_ACTION_SRC + sourceName + ".widx");

		printScriptFile(scriptFile, fileBuffer);
		printIndexFile(indexFile);
		writeRawFile(rawFile, fileBuffer);
	}
}

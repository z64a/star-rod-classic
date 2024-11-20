package game.battle.partners;

import static app.Directories.DUMP_ALLY_RAW;
import static app.Directories.DUMP_ALLY_SRC;
import static game.battle.BattleConstants.ALLY_BASE;
import static game.battle.BattleConstants.ALLY_RAM_LIMIT;
import static game.shared.StructTypes.ActorT;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import game.battle.BaseBattleDecoder;
import game.shared.decoder.Pointer;
import game.shared.decoder.Pointer.Origin;

public class PartnerActorDecoder extends BaseBattleDecoder
{
	public PartnerActorDecoder(ByteBuffer fileBuffer, String scriptName, int start, int end, int ptrActor) throws IOException
	{
		super();

		sourceName = scriptName;

		int startAddress = ALLY_BASE;
		int endAddress = (end - start) + ALLY_BASE;
		setAddressRange(startAddress, endAddress, ALLY_RAM_LIMIT);
		setOffsetRange(start, end);

		findLocalPointers(fileBuffer);
		Pointer ptr = enqueueAsRoot(ptrActor, ActorT, Origin.DECODED);
		ptr.forceName(scriptName.substring(3));

		super.decode(fileBuffer);

		File rawFile = new File(DUMP_ALLY_RAW + sourceName + ".bin");
		File scriptFile = new File(DUMP_ALLY_SRC + sourceName + ".bscr");
		File indexFile = new File(DUMP_ALLY_SRC + sourceName + ".bidx");

		printScriptFile(scriptFile, fileBuffer);
		printIndexFile(indexFile);
		writeRawFile(rawFile, fileBuffer);
	}
}

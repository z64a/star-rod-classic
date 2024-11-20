package game.map.struct.npc;

import static game.shared.StructTypes.NpcGroupT;

import java.io.PrintWriter;
import java.nio.ByteBuffer;

import game.shared.BaseStruct;
import game.shared.decoder.BaseDataDecoder;
import game.shared.decoder.Pointer;
import reports.BattleMapTracker;

public class NpcGroupList extends BaseStruct
{
	public static final NpcGroupList instance = new NpcGroupList();

	private NpcGroupList()
	{}

	@Override
	public void scan(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer)
	{
		int numNPCs;
		while ((numNPCs = fileBuffer.getInt()) != 0) {
			int ptrNPCs = fileBuffer.getInt();
			int battleID = fileBuffer.getInt();

			Pointer groupInfo = decoder.enqueueAsChild(ptr, ptrNPCs, NpcGroupT);
			groupInfo.listLength = numNPCs;
			groupInfo.npcBattleID = battleID;
			// groupInfo.setProperty(PropertyType.NpcBattleID, battleID);
			BattleMapTracker.add(decoder.getSourceName(), battleID);
		}
		fileBuffer.getInt();
		fileBuffer.getInt();
	}

	@Override
	public void print(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer, PrintWriter pw)
	{
		decoder.printHex(ptr, fileBuffer, pw, 3);
	}
}

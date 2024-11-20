package game.map.struct.npc;

import static game.shared.StructTypes.ScriptT;

import java.nio.ByteBuffer;

import game.shared.BaseStruct;
import game.shared.decoder.BaseDataDecoder;
import game.shared.decoder.Pointer;

public class NpcSettings extends BaseStruct
{
	public static final NpcSettings instance = new NpcSettings();

	private NpcSettings()
	{}

	@Override
	public void scan(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer)
	{
		fileBuffer.getInt(); // animation?
		fileBuffer.getInt(); // size?

		int ptrScript;

		if ((ptrScript = fileBuffer.getInt()) != 0) // init/spawn script?
			decoder.enqueueAsChild(ptr, ptrScript, ScriptT);

		if ((ptrScript = fileBuffer.getInt()) != 0)
			decoder.enqueueAsChild(ptr, ptrScript, ScriptT);

		// called AI script, usually calls an updateMovement function? func_8004A47C
		// used in some other circumstances to trigger cutscenes (trd_10)
		if ((ptrScript = fileBuffer.getInt()) != 0) {
			Pointer scriptInfo = decoder.enqueueAsChild(ptr, ptrScript, ScriptT);
			scriptInfo.setDescriptor("NpcAI");

			// all npcs with battle IDs have AI data
			// for(Pointer parentInfo : ptr.ancestors)
			//	assert(parentInfo.npcBattleID != -1); // but friendly NPCs use battle ID of 0...
		}

		if ((ptrScript = fileBuffer.getInt()) != 0) {
			if (decoder.tryEnqueueAsChild(ptr, ptrScript, ScriptT) == null)
				assert (ptrScript == 0x80077F70); // default script
		}

		if ((ptrScript = fileBuffer.getInt()) != 0)
			decoder.enqueueAsChild(ptr, ptrScript, ScriptT);

		if ((ptrScript = fileBuffer.getInt()) != 0) {
			if (decoder.tryEnqueueAsChild(ptr, ptrScript, ScriptT) == null)
				assert (ptrScript == 0x8007809C); // default script
		}

		fileBuffer.getInt();
		fileBuffer.getInt();
		fileBuffer.getInt();
	}
}

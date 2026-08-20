package game.map.struct.npc;

import static game.shared.StructTypes.ScriptT;

import java.nio.ByteBuffer;

import game.map.marker.Marker;
import game.map.marker.Marker.MarkerType;
import game.map.marker.NpcComponent;
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
		int defaultAnimation = fileBuffer.getInt();
		short height = fileBuffer.getShort();
		short radius = fileBuffer.getShort();

		int callbacks = 0;
		scanScript(decoder, ptr, fileBuffer.getInt(), "OtherAI", 0);
		if (scanScript(decoder, ptr, fileBuffer.getInt(), "Interact", 0))
			callbacks |= NpcComponent.CALLBACK_INTERACT;
		if (scanScript(decoder, ptr, fileBuffer.getInt(), "NpcAI", 0))
			callbacks |= NpcComponent.CALLBACK_AI;
		if (scanScript(decoder, ptr, fileBuffer.getInt(), "Hit", 0x80077F70))
			callbacks |= NpcComponent.CALLBACK_HIT;
		if (scanScript(decoder, ptr, fileBuffer.getInt(), "Aux", 0))
			callbacks |= NpcComponent.CALLBACK_AUX;
		if (scanScript(decoder, ptr, fileBuffer.getInt(), "Defeat", 0x8007809C))
			callbacks |= NpcComponent.CALLBACK_DEFEAT;

		int enemyFlags = fileBuffer.getInt();
		fileBuffer.getInt(); // copied to Enemy.unk_B8
		short level = fileBuffer.getShort();
		int actionFlags = Short.toUnsignedInt(fileBuffer.getShort());

		for (Pointer parent : ptr.parents) {
			Marker marker = parent.associatedMarker;
			if (marker == null || marker.getType() != MarkerType.NPC)
				continue;

			NpcComponent npc = marker.npcComponent;
			if (defaultAnimation != 0) {
				npc.setDefaultAnimation(defaultAnimation & 0xFF);
				npc.inferAnimationOverrides();
			}
			npc.height.set((int) height);
			npc.radius.set((int) radius);
			npc.level.set((int) level);
			npc.actionFlags.set(actionFlags);
			npc.enemyFlags.set(npc.enemyFlags.get() | enemyFlags);
			npc.callbackFlags.set(npc.callbackFlags.get() | callbacks);
		}
	}

	private boolean scanScript(BaseDataDecoder decoder, Pointer parent, int address, String descriptor, int defaultAddress)
	{
		if (address == 0)
			return false;

		Pointer script = decoder.tryEnqueueAsChild(parent, address, ScriptT);
		if (script != null) {
			script.setDescriptor(descriptor);
			return true;
		}

		assert (address == defaultAddress);
		return false;
	}
}

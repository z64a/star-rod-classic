package game.map.scripts;

import static game.map.marker.NpcComponent.CALLBACK_AI;
import static game.map.marker.NpcComponent.CALLBACK_AUX;
import static game.map.marker.NpcComponent.CALLBACK_DEFEAT;
import static game.map.marker.NpcComponent.CALLBACK_HIT;
import static game.map.marker.NpcComponent.CALLBACK_IDLE;
import static game.map.marker.NpcComponent.CALLBACK_INIT;
import static game.map.marker.NpcComponent.CALLBACK_INTERACT;
import static game.map.marker.NpcComponent.CALLBACK_MASK;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import app.input.InvalidInputException;
import game.map.marker.Marker;
import game.map.marker.Marker.MarkerType;
import game.map.marker.NpcComponent;
import game.shared.ProjectDatabase;

public final class NpcGenerator
{
	public static final String GROUP_LIST_NAME = "$NpcGroupList_Generated";

	private static final String GROUP_NAME = "$NpcGroup_Generated";

	private final ScriptGenerator generator;
	private final List<String> body = new ArrayList<>();
	private final List<String> callbacks = new ArrayList<>();

	public NpcGenerator(ScriptGenerator generator, List<Marker> npcs) throws InvalidInputException
	{
		this.generator = generator;
		if (npcs.isEmpty())
			return;

		validateNpcs(npcs);
		addGroupList(npcs);
		addNpcData(npcs);
		for (Marker npc : npcs)
			addNpcSupport(npc);
	}

	public List<String> getLines()
	{
		return body;
	}

	public List<String> getCallbacks()
	{
		return callbacks;
	}

	private void validateNpcs(List<Marker> npcs) throws InvalidInputException
	{
		Set<String> names = new HashSet<>();
		for (Marker npc : npcs) {
			String name = npc.getName();
			generator.validateObject(name, "NPC marker", MarkerType.NPC, name);
			if (!names.add(name))
				throw new InvalidInputException("Generated NPC name is not unique: " + name);
			if (name.matches(".*\\s+.*"))
				throw new InvalidInputException("Generated NPC name contains whitespace: " + name);

			NpcComponent data = npc.npcComponent;
			validateSignedShort(name, "height", data.height.get());
			validateSignedShort(name, "radius", data.radius.get());
			validateSignedShort(name, "level", data.level.get());
			if ((data.actionFlags.get() & ~0xFFFF) != 0)
				throw new InvalidInputException("NPC %s has action flags outside the 16-bit range: %X", name, data.actionFlags.get());
			if ((data.callbackFlags.get() & ~CALLBACK_MASK) != 0)
				throw new InvalidInputException("NPC %s has unknown callback flags: %X", name, data.callbackFlags.get());

			getTattleMessageID(npc);
		}
	}

	private void validateSignedShort(String npcName, String fieldName, int value) throws InvalidInputException
	{
		if (value < Short.MIN_VALUE || value > Short.MAX_VALUE)
			throw new InvalidInputException("NPC %s has %s outside the signed 16-bit range: %d", npcName, fieldName, value);
	}

	private void addGroupList(List<Marker> npcs)
	{
		body.add("#new:NpcGroupList " + GROUP_LIST_NAME);
		body.add("{");
		body.add(String.format("\t%d` %s 00000000", npcs.size(), GROUP_NAME));
		body.add("\t00000000 00000000 00000000");
		body.add("}");
		body.add("");
	}

	private void addNpcData(List<Marker> npcs) throws InvalidInputException
	{
		body.add("#new:NpcGroup " + GROUP_NAME);
		body.add("{");

		for (int i = 0; i < npcs.size(); i++) {
			Marker npc = npcs.get(i);
			NpcComponent data = npc.npcComponent;
			String name = npc.getName();

			generator.defineLines.add(String.format("#define .NpcID:%s %d`", name, i));

			String initScript = data.needsInitScript() ? getInitScriptName(name) : "00000000";
			body.add(String.format("\t.NpcID:%s %s ~Vec3f:%s", name, getSettingsName(name), name));
			body.add(String.format("\t%08X %s 00000000 00000000 %08X", data.enemyFlags.get(), initScript, Math.round((float) npc.yaw.getAngle())));
			body.add("\t~NoDrops");
			body.add(String.format("\t~Movement:%s", name));
			body.add(String.format("\t~AnimationTable:%s", name));
			body.add(String.format("\t00000000 00000000 00000000 %08X", getTattleMessageID(npc)));

			if (i + 1 < npcs.size())
				body.add("");
		}

		body.add("}");
		body.add("");
	}

	private void addNpcSupport(Marker npc)
	{
		NpcComponent data = npc.npcComponent;
		String name = npc.getName();

		body.add("#new:NpcSettings " + getSettingsName(name));
		body.add("{");
		body.add(String.format("\t%08X %04Xs %04Xs", data.getDefaultAnimationID(), data.height.get() & 0xFFFF, data.radius.get() & 0xFFFF));
		body.add("\t00000000 00000000 00000000 00000000 00000000 00000000");
		body.add(String.format("\t00000000 00000000 %04Xs %04Xs", data.level.get() & 0xFFFF, data.actionFlags.get() & 0xFFFF));
		body.add("}");
		body.add("");

		if (!data.needsInitScript())
			return;

		body.add("#new:Script " + getInitScriptName(name));
		body.add("{");
		if (data.hasCallback(CALLBACK_INTERACT))
			body.add(String.format("\tCall  BindNpcInteract   ( .Npc:Self %s )", getCallbackName("Interact", name)));
		if (data.hasCallback(CALLBACK_IDLE))
			body.add(String.format("\tCall  BindNpcIdle       ( .Npc:Self %s )", getCallbackName("Idle", name)));
		if (data.hasCallback(CALLBACK_AI))
			body.add(String.format("\tCall  BindNpcAI         ( .Npc:Self %s )", getCallbackName("AI", name)));
		if (data.hasCallback(CALLBACK_HIT))
			body.add(String.format("\tCall  BindNpcHit        ( .Npc:Self %s )", getCallbackName("Hit", name)));
		if (data.hasCallback(CALLBACK_DEFEAT))
			body.add(String.format("\tCall  BindNpcDefeat     ( .Npc:Self %s )", getCallbackName("Defeat", name)));
		if (data.hasCallback(CALLBACK_AUX))
			body.add(String.format("\tCall  BindNpcAux        ( .Npc:Self %s )", getCallbackName("Aux", name)));
		if (data.hasCallback(CALLBACK_INIT))
			body.add("\tExecWait  " + getCallbackName("Init", name));
		body.add("\tReturn");
		body.add("\tEnd");
		body.add("}");
		body.add("");

		if (data.hasCallback(CALLBACK_INIT))
			addEmptyCallback("Init", name);
		if (data.hasCallback(CALLBACK_INTERACT))
			addEmptyCallback("Interact", name);
		if (data.hasCallback(CALLBACK_IDLE))
			addEmptyCallback("Idle", name);
		if (data.hasCallback(CALLBACK_AI))
			addEmptyCallback("AI", name);
		if (data.hasCallback(CALLBACK_HIT))
			addEmptyCallback("Hit", name);
		if (data.hasCallback(CALLBACK_DEFEAT))
			addEmptyCallback("Defeat", name);
		if (data.hasCallback(CALLBACK_AUX))
			addEmptyCallback("Aux", name);
	}

	private int getTattleMessageID(Marker npc) throws InvalidInputException
	{
		String identifier = npc.npcComponent.tattleMessage.get();
		if (identifier == null || identifier.isEmpty())
			return 0;

		Integer messageID = ProjectDatabase.messages == null ? null : ProjectDatabase.messages.getMessageID(identifier);
		if (messageID == null)
			throw new InvalidInputException("NPC %s has an invalid fixed-ID tattle message: %s", npc.getName(), identifier);
		return messageID;
	}

	private void addEmptyCallback(String type, String npcName)
	{
		callbacks.add("#new:Script " + getCallbackName(type, npcName));
		callbacks.add("{");
		callbacks.add("\tReturn");
		callbacks.add("\tEnd");
		callbacks.add("}");
		callbacks.add("");
	}

	private static String getSettingsName(String npcName)
	{
		return "$NpcSettings_" + npcName;
	}

	private static String getInitScriptName(String npcName)
	{
		return "$Script_NpcSetup_" + npcName;
	}

	private static String getCallbackName(String type, String npcName)
	{
		return "$Script_Npc" + type + "_" + npcName;
	}
}

package game.map.struct.npc;

import static game.shared.StructTypes.*;

import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.util.List;

import app.Environment;
import app.config.Options;
import app.input.Line;
import game.map.marker.Marker;
import game.map.marker.Marker.MarkerType;
import game.map.marker.NpcComponent.MoveType;
import game.map.patching.MapDecoder;
import game.shared.BaseStruct;
import game.shared.ProjectDatabase;
import game.shared.StructField;
import game.shared.decoder.BaseDataDecoder;
import game.shared.decoder.Pointer;
import game.shared.encoder.BaseDataEncoder;
import game.shared.struct.Struct;

public class NpcGroup extends BaseStruct
{
	public static final NpcGroup instance = new NpcGroup();

	private NpcGroup()
	{}

	@Override
	public void scan(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer)
	{
		MapDecoder mapDecoder = decoder instanceof MapDecoder ? (MapDecoder) decoder : null;

		for (int i = 0; i < ptr.listLength; i++) {
			int pos = fileBuffer.position();
			int npcAddress = decoder.toAddress(fileBuffer.position());
			Pointer npc = new Pointer(npcAddress);
			npc.setType(NpcT, true);
			npc.listIndex = i;
			npc.origin = ptr.origin;
			ptr.addUniqueChild(npc);

			if (mapDecoder != null)
				mapDecoder.addNpc(npc);

			int npcID = fileBuffer.getInt(); // npcID
			decoder.tryEnqueueAsChild(npc, fileBuffer.getInt(), NpcSettingsT);
			float x = fileBuffer.getFloat(); // X
			float y = fileBuffer.getFloat(); // Y
			float z = fileBuffer.getFloat(); // Z
			fileBuffer.getInt(); // flags
			int initScript = fileBuffer.getInt();
			Pointer initPtr = decoder.tryEnqueueAsChild(npc, initScript, ScriptT);
			if (initPtr != null)
				initPtr.setDescriptor("Init");

			int w1 = fileBuffer.getInt();
			fileBuffer.getInt(); // unknown flags, ex: 100, 10601, ...
			int angle = fileBuffer.getInt();

			assert (w1 == 0 || w1 == 1);
			assert (angle >= 0 && angle < 360);

			String tempName = String.format("NPC_%08X", npcAddress);
			Marker npcMarker = new Marker(tempName, MarkerType.NPC, x, y, z, angle);
			if (mapDecoder != null)
				mapDecoder.addMarker(npcMarker);

			byte dropFlag = fileBuffer.get();
			byte dropChance = fileBuffer.get();

			assert (dropFlag == 0 || dropFlag == (byte) 0x80);

			if (dropFlag == 0) {
				assert (dropChance == 0);
				for (int s = 0; s < 91; s++)
					assert (fileBuffer.getShort() == 0);
			}
			else {
				short[][] itemDrops = new short[8][3];
				for (int j = 0; j < 8; j++) {
					itemDrops[j][0] = fileBuffer.getShort(); // item ID
					itemDrops[j][1] = fileBuffer.getShort(); // weight
					itemDrops[j][2] = fileBuffer.getShort();
				}

				short[][] heartDrops = new short[8][4];
				for (int j = 0; j < 8; j++) {
					heartDrops[j][0] = fileBuffer.getShort(); // hp cutoff (% of max, reduced float)
					heartDrops[j][1] = fileBuffer.getShort(); // % chance to drop (reduced float)
					heartDrops[j][2] = fileBuffer.getShort(); // drop attempts
					heartDrops[j][3] = fileBuffer.getShort(); // % chance per attempt (reduced float)
				}

				short[][] flowerDrops = new short[8][4];
				for (int j = 0; j < 8; j++) {
					flowerDrops[j][0] = fileBuffer.getShort(); // fp cutoff (% of max, reduced float)
					flowerDrops[j][1] = fileBuffer.getShort(); // % chance to drop (reduced float)
					flowerDrops[j][2] = fileBuffer.getShort(); // drop attempts
					flowerDrops[j][3] = fileBuffer.getShort(); // % chance per attempt (reduced float)
				}

				short coinsMin = fileBuffer.getShort();
				short coinsMax = fileBuffer.getShort();
				short coins3 = fileBuffer.getShort();

				checkItemDrops(ptr, dropChance, itemDrops);
				checkHeartDrops(heartDrops);
				checkFlowerDrops(flowerDrops);

				assert (coinsMin <= coinsMax);
				assert (coins3 == 0);
			}

			// 0xE0 to 0x1A0 -- Movement Data
			int[] moveData = new int[48];
			for (int j = 0; j < 48; j++)
				moveData[j] = fileBuffer.getInt();

			MoveType movementType = Npc.getMovementType(moveData, npcAddress, decoder.getSourceName());
			npcMarker.npcComponent.loadTerritoryData(movementType, moveData);

			checkMoveData(moveData, movementType);

			// 0x1A0 to 0x1E0 -- Animations
			int anim0 = fileBuffer.getInt();
			int spriteID = anim0 >> 16;

			npcMarker.npcComponent.setSpriteID(anim0 >> 16);
			npcMarker.npcComponent.setPaletteID((anim0 >> 8) & 0xFF);
			npcMarker.npcComponent.setAnimation(0, anim0 & 0xFF);

			String spriteName = ProjectDatabase.SpriteType.getConstantString(spriteID);
			if (spriteName != null && spriteID != 0 && mapDecoder != null && Environment.mainConfig.getBoolean(Options.GenerateNpcIDs)) {
				String uniqueName = mapDecoder.makeUniqueNpcName(spriteName.substring(spriteName.indexOf(":") + 1), npcID); // trim .Sprite:
				if (uniqueName != null) {
					//	ptr.forceName("NPC_" + uniqueName);
					npcMarker.setName("NPC_" + uniqueName);
				}
			}

			for (int j = 1; j < 16; j++)
				npcMarker.npcComponent.setAnimation(j, fileBuffer.getInt() & 0xFF);

			int w2 = fileBuffer.getInt();
			int w3 = fileBuffer.getInt(); // omo_09 $NpcGroup_802493BC [0] has this == 1

			assert (w2 == 0 || w2 == 1 || w2 == 2 || w2 == 3 || w2 == 9) : String.format("%X NPC_%08X", w2, npcAddress);
			assert (w3 == 0 || w3 == 1) : String.format("%X NPC_%08X", w3, npcAddress);

			decoder.tryEnqueueAsChild(npc, fileBuffer.getInt(), ExtraAnimationListT);
			fileBuffer.getInt(); // tattle string ID
		}
	}

	@Override
	public void print(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer, PrintWriter pw)
	{
		int initialPos = fileBuffer.position();
		for (int i = 0; i < ptr.listLength; i++) {
			if (i > 0)
				pw.printf("%% %n%% %s[%X]%n", ptr.getPointerName(), 0x1F0 * i);

			fileBuffer.position(initialPos + 0x1F0 * i);
			if (decoder instanceof MapDecoder)
				Npc.printNpcData((MapDecoder) decoder, fileBuffer, pw, false);
			else
				decoder.printHex(fileBuffer, pw, 8, 0x1F0);
		}
	}

	@Override
	public void replaceExpression(Line line, BaseDataEncoder encoder, String[] tokens, List<String> newTokens)
	{
		Npc.instance.replaceExpression(line, encoder, tokens, newTokens);
	}

	@Override
	public StructField parseFieldOffset(BaseDataEncoder encoder, Struct struct, String offsetName)
	{
		return Npc.instance.parseFieldOffset(encoder, struct, offsetName);
	}

	private static void checkItemDrops(Pointer ptr, int dropChance, short[][] itemDrops)
	{
		boolean empty = (dropChance == 0);
		for (int j = 0; j < 8; j++) {
			if (empty)
				assert (itemDrops[j][0] == 0);

			if (itemDrops[j][0] == 0 || (j == 0 && itemDrops[j][0] == 100)) {
				assert (itemDrops[j][1] == 0);
				empty = true;
			}
			assert (itemDrops[j][2] == 0) : ptr.getPointerName() + " item drop " + j + " has unknown short = " + itemDrops[j][2];
		}
	}

	private static void checkHeartDrops(short[][] heartDrops)
	{
		boolean empty = false;
		for (int j = 0; j < 8; j++) {
			if (empty || heartDrops[j][0] == 0) {
				assert (heartDrops[j][1] == 0);
				assert (heartDrops[j][2] == 0);
				assert (heartDrops[j][3] == 0);
				empty = true;
			}

			if (j == 0 && heartDrops[j][0] == 0x7FFF) {
				assert (heartDrops[j][1] == 0);
				assert (heartDrops[j][2] == 0);
				assert (heartDrops[j][3] == 0);
				empty = true;
			}

			assert (onlyNeedsOneDecimal(heartDrops[j][0]));
			assert (onlyNeedsOneDecimal(heartDrops[j][1]));
			assert (onlyNeedsOneDecimal(heartDrops[j][3]));
		}
	}

	private static void checkFlowerDrops(short[][] flowerDrops)
	{
		boolean empty = false;
		for (int j = 0; j < 8; j++) {
			if (empty || flowerDrops[j][0] == 0) {
				assert (flowerDrops[j][1] == 0);
				assert (flowerDrops[j][2] == 0);
				assert (flowerDrops[j][3] == 0);
				empty = true;
			}

			if (j == 0 && flowerDrops[j][0] == 0x7FFF) {
				assert (flowerDrops[j][1] == 0);
				assert (flowerDrops[j][2] == 0);
				assert (flowerDrops[j][3] == 0);
				empty = true;
			}

			assert (onlyNeedsOneDecimal(flowerDrops[j][0]));
			assert (onlyNeedsOneDecimal(flowerDrops[j][1]));
			assert (onlyNeedsOneDecimal(flowerDrops[j][3]));
		}
	}

	private static boolean onlyNeedsOneDecimal(short s)
	{
		float a = s / 32767.0f;
		float trunc = Math.round(a * 10) / 10.0f;
		return Math.abs(a - trunc) < 1e-4;
	}

	private static void checkMoveData(int[] moveData, MoveType type)
	{
		switch (type) {
			case Wander:
				assert (moveData[6] == 0 || moveData[6] == 1);
				assert (moveData[12] == 0 || moveData[12] == 1);
				assert (moveData[13] == 0 || moveData[13] == 1);

				for (int k = 14; k < 48; k++)
					assert (moveData[k] == 0);
				break;

			case Patrol:
				int num = moveData[0];
				for (int k = 3 * num + 1; k < 31; k++)
					assert (moveData[k] == 0);

				for (int k = 39; k < 48; k++)
					assert (moveData[k] == 0);
				break;
			case Stationary:

				for (int k = 0; k < 48; k++)
					assert (moveData[k] == 0);
				break;
		}
	}
}

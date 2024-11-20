package game.map.struct.npc;

import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;

import app.StarRodException;
import app.input.InputFileException;
import app.input.InvalidInputException;
import app.input.Line;
import game.map.MapIndex;
import game.map.marker.Marker;
import game.map.marker.Marker.MarkerType;
import game.map.marker.NpcComponent.MoveType;
import game.map.patching.MapDecoder;
import game.shared.BaseStruct;
import game.shared.DataUtils;
import game.shared.ProjectDatabase;
import game.shared.StructField;
import game.shared.StructField.Style;
import game.shared.SyntaxConstants;
import game.shared.decoder.BaseDataDecoder;
import game.shared.decoder.Pointer;
import game.shared.encoder.BaseDataEncoder;
import game.shared.struct.Struct;

public class Npc extends BaseStruct
{
	public static final Npc instance = new Npc();

	public static PrintWriter npcPrinter = null;
	public static PrintWriter enemyPrinter = null;

	public static final int MOVE_NONE = 0;
	public static final int MOVE_WANDER = 1;
	public static final int MOVE_PATROL = 2;

	public static final int ANIM_TABLE_SIZE = 16;

	private Npc()
	{}

	@Override
	public void scan(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer)
	{
		throw new IllegalStateException("NPC structs should never be scanned individually! They exist only to track dependency.");
	}

	public static MoveType getMovementType(int[] moveData, int npcAddress, String mapName)
	{
		if (moveData[5] == 0xFFFF8001 || (npcAddress == 0x8024DBA8 && mapName.equals("nok_01")))
			return MoveType.Wander;
		else if (moveData[31] == 0xFFFF8001)
			return MoveType.Patrol;
		else
			return MoveType.Stationary;
	}

	@Override
	public void print(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer, PrintWriter pw)
	{
		assert (ptr.parents.size() == 1);

		if (!(decoder instanceof MapDecoder))
			throw new StarRodException("Invalid expression: no map is associated with source");

		printNpcData((MapDecoder) decoder, fileBuffer, pw, true);
	}

	public static void printNpcData(MapDecoder decoder, ByteBuffer fileBuffer, PrintWriter pw, boolean exportMode)
	{
		int npcAddress = decoder.toAddress(fileBuffer.position());
		int npcID = fileBuffer.getInt();
		String npcName = decoder.getNpcName(npcID);

		String markerName;
		if (npcName == null) {
			markerName = String.format("NPC_%08X", npcAddress);
			fileBuffer.position(fileBuffer.position() - 4);
			decoder.printHex(fileBuffer, pw, 8, 1); // ID
		}
		else {
			markerName = "NPC_" + npcName;
			pw.print(MapDecoder.NPC_ID_NAMESPACE + npcName + " ");
		}

		//	if(!exportMode && !markerName.equals(String.format("NPC_%08X", npcAddress)))
		//		System.out.printf("### %s\tNPC_%08X\t%s%n", decoder.getSourceName(), npcAddress, markerName);

		decoder.printHex(fileBuffer, pw, 8, 1); // ExtraNpcData pointer

		int x = (int) fileBuffer.getFloat();
		int y = (int) fileBuffer.getFloat();
		int z = (int) fileBuffer.getFloat();

		if (exportMode)
			pw.println("00000000 00000000 00000000 ");
		else
			pw.printf("%cVec3f:%s %% %d %d %d%n", SyntaxConstants.EXPRESSION_PREFIX, markerName, x, y, z);

		int flags = fileBuffer.getInt();
		fileBuffer.position(fileBuffer.position() - 4);

		decoder.printHex(fileBuffer, pw, 8, 5); // unknown
		pw.println();

		byte dropFlag = fileBuffer.get();
		byte dropChance = fileBuffer.get();

		short[][] itemDrops = new short[8][3];
		for (int j = 0; j < 8; j++) {
			itemDrops[j][0] = fileBuffer.getShort(); // item ID
			itemDrops[j][1] = fileBuffer.getShort(); // weight
			itemDrops[j][2] = fileBuffer.getShort(); // flag + 715 to set on spawn

			//	if(itemDrops[j][2] != 0)
			//		DebugPrinter.println("%s %X", markerName, itemDrops[j][2]);

			assert (itemDrops[j][2] == 0);
		}

		short[][] heartDrops = new short[8][4];
		for (int j = 0; j < 8; j++) {
			heartDrops[j][0] = fileBuffer.getShort(); // hp cutoff (% of max, reduced float)
			heartDrops[j][1] = fileBuffer.getShort(); // % chance to drop (reduced float)
			heartDrops[j][2] = fileBuffer.getShort(); // drop attempts
			heartDrops[j][3] = fileBuffer.getShort(); //
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
		fileBuffer.getShort();

		if (dropFlag == 0) {
			pw.printf("%cNoDrops", SyntaxConstants.EXPRESSION_PREFIX);
		}
		else {
			boolean noItems = (dropChance == 0);
			//	boolean noHP = (heartDrops[0][0] == 0x7FFF);
			//	boolean noFP = (flowerDrops[0][0] == 0x7FFF);
			boolean noCoins = (coinsMin == 0 && coinsMax == 0);

			PresetHP hpPreset = PresetHP.getMatch(heartDrops);
			PresetFP fpPreset = PresetFP.getMatch(flowerDrops);

			if (noItems && noCoins && hpPreset == PresetHP.NONE && fpPreset == PresetFP.NONE)
				pw.printf("%cNoDrops%n",
					SyntaxConstants.EXPRESSION_PREFIX, SyntaxConstants.EXPRESSION_PREFIX,
					SyntaxConstants.EXPRESSION_PREFIX, SyntaxConstants.EXPRESSION_PREFIX);
			else {
				if (noItems)
					pw.printf("%cNoItems ", SyntaxConstants.EXPRESSION_PREFIX);
				else {
					pw.printf("%cItems:%d", SyntaxConstants.EXPRESSION_PREFIX, dropChance);
					int itemCount = 0;
					for (int j = 0; j < 8; j++) {
						if (itemDrops[j][0] == 0)
							break;

						String itemName = ProjectDatabase.getItemName(itemDrops[j][0]);
						pw.printf(":%s:%X", itemName, itemDrops[j][1]);
						itemCount++;
					}

					if (itemCount > 1)
						pw.println();
					else
						pw.print(" ");
				}

				pw.printf("%cHP:%s:%X ", SyntaxConstants.EXPRESSION_PREFIX, hpPreset.name, heartDrops[0][2]);
				pw.printf("%cFP:%s:%X ", SyntaxConstants.EXPRESSION_PREFIX, fpPreset.name, flowerDrops[0][2]);

				if (noCoins)
					pw.printf("%cNoCoinBonus%n", SyntaxConstants.EXPRESSION_PREFIX);
				else
					pw.printf("%cCoinBonus:%X:%X%n", SyntaxConstants.EXPRESSION_PREFIX, coinsMin, coinsMax);
			}
		}

		int[] moveData = new int[48];
		for (int j = 0; j < 48; j++)
			moveData[j] = fileBuffer.getInt();

		MoveType movementType = Npc.getMovementType(moveData, npcAddress, decoder.getSourceName());

		if (exportMode) {
			switch (movementType) {
				case Wander:
					moveData[0] -= x;
					moveData[1] -= y;
					moveData[2] -= z;

					moveData[7] -= x;
					moveData[8] -= y;
					moveData[9] -= z;

					decoder.printHex(moveData, pw, 8);
					break;

				case Patrol:
					for (int i = 0; i < 3 * moveData[0]; i += 3) {
						moveData[i + 1] -= x;
						moveData[i + 2] -= y;
						moveData[i + 3] -= z;
					}

					moveData[32] -= x;
					moveData[33] -= y;
					moveData[34] -= z;

					decoder.printHex(moveData, pw, 8);
					break;

				case Stationary:
					decoder.printHex(moveData, pw, 8);
					break;
			}
		}
		else
			pw.printf("%cMovement:%s%n", SyntaxConstants.EXPRESSION_PREFIX, markerName);

		/*
		The animations table is comprised of 16 animation values (no pointers or other data).
		Some or all can be null (00000000). The spriteIDs and paletteIDs will always match.
		 */

		int spriteID = fileBuffer.getInt() >> 16;
		String spriteName = ProjectDatabase.SpriteType.getConstantString(spriteID);
		fileBuffer.position(fileBuffer.position() + 4 * 15); // skip

		pw.printf("%cAnimationTable:%s", SyntaxConstants.EXPRESSION_PREFIX, markerName);
		if (spriteName != null && !spriteName.isEmpty())
			pw.println(" % " + spriteName);
		else
			pw.println();

		decoder.printHex(fileBuffer, pw, 8, 3);
		int tattleID = fileBuffer.getInt();
		if (tattleID != 0)
			pw.printf("%08X %s", tattleID, decoder.getStringComment(tattleID));
		else
			pw.printf("%08X %% no tattle string", tattleID);
		pw.println();
	}

	@Override
	public void replaceExpression(Line line, BaseDataEncoder encoder, String[] tokens, List<String> newTokens)
	{
		switch (tokens[0].toLowerCase()) {
			case "nodrops": {
				// 0xB8 blank bytes
				for (int i = 0; i < 0xB8; i += 4)
					newTokens.add("00000000");
			}
				break;

			case "noitems": {
				// 50 blank bytes
				for (int i = 0; i < 48; i += 4)
					newTokens.add("00000000");
				newTokens.add("0000s");
			}
				break;

			case "items": {
				int totalChance;
				try {
					totalChance = DataUtils.parseIntString(tokens[1]);
				}
				catch (InvalidInputException e) {
					throw new InputFileException(line, "Could not parse item drop chance: %s", tokens[1]);
				}

				if (tokens.length < 2 || tokens.length % 2 != 0 || totalChance > 100 || totalChance < 0)
					throw new InputFileException(line, "Invalid item drop expression on line: %n%s", line.trimmedInput());

				newTokens.add(String.format("80%02Xs", totalChance));

				int numItems = (tokens.length / 2) - 1;
				for (int i = 0; i < 8; i++) {
					if (i < numItems) {
						String itemIDString = tokens[2 * i + 2]; // allow item IDs for custom item support
						Integer itemID = ProjectDatabase.getItemID(tokens[2 * i + 2]);
						if (itemID != null)
							itemIDString = String.format("%08X", itemID);

						int weight;
						try {
							weight = DataUtils.parseIntString(tokens[2 * i + 3]);
						}
						catch (InvalidInputException e) {
							throw new InputFileException(line, "Could not parse item drop weight: %s", tokens[2 * i + 3]);
						}

						newTokens.add(itemIDString + "s");
						newTokens.add(String.format("%04Xs", weight));
						newTokens.add("0000s");
					}
					else {
						newTokens.add("0000s");
						newTokens.add("0000s");
						newTokens.add("0000s");
					}
				}
			}
				break;

			case "nohp":
			case "nofp": {
				// 64 blank bytes
				for (int i = 0; i < 64; i += 4)
					newTokens.add("00000000");
			}
				break;

			case "hp":
			case "fp": {
				if (tokens.length == 2) {
					if (!tokens[1].equals("None"))
						throw new InputFileException(line, "Invalid %s drop expression on line: %n%s", tokens[0], line.trimmedInput());

					// 8 blank bytes
					newTokens.add("00000000");
					newTokens.add("00000000");
				}
				else if (tokens.length == 3) {
					int dropCount;
					try {
						dropCount = DataUtils.parseIntString(tokens[2]);
					}
					catch (InvalidInputException e) {
						throw new InputFileException(line, "Could not parse %s drop count: %s", tokens[0], tokens[2]);
					}
					int[][] table = null;
					if (tokens[0].equals("HP")) {
						for (PresetHP preset : PresetHP.values()) {
							if (preset.name.equals(tokens[1]))
								table = preset.table;
						}
					}
					else {
						for (PresetFP preset : PresetFP.values()) {
							if (preset.name.equals(tokens[1]))
								table = preset.table;
						}
					}
					if (table == null)
						throw new InputFileException(line, "Invalid %s preset: %s", tokens[0], tokens[1]);
					for (int i = 0; i < 8; i++) {
						newTokens.add(String.format("%04Xs", table[i][0]));
						newTokens.add(String.format("%04Xs", table[i][1]));
						newTokens.add(table[i][2] == 0 ? "0000s" : String.format("%04Xs", dropCount));
						newTokens.add(String.format("%04Xs", table[i][3]));
					}
				}
				else {
					if (tokens.length != 5)
						throw new InputFileException(line, "Invalid %s drop expression on line: %n%s", tokens[0], line.trimmedInput());

					int cutoff = Integer.parseInt(tokens[1]);
					int baseChance = Integer.parseInt(tokens[2]);
					int attempts = Integer.parseInt(tokens[3]);
					int attemptChance = Integer.parseInt(tokens[4]);

					newTokens.add(String.format("%04Xs", Math.round(cutoff * 327.67f)));
					newTokens.add(String.format("%04Xs", Math.round(baseChance * 327.67f)));
					newTokens.add(String.format("%04Xs", attempts));
					newTokens.add(String.format("%04Xs", Math.round(attemptChance * 327.67f)));
				}
			}
				break;

			case "nocoinbonus": {
				// 6 blank bytes
				newTokens.add("0000s");
				newTokens.add("0000s");
				newTokens.add("0000s");
			}
				break;

			case "coinbonus": {
				if (tokens.length != 3)
					throw new InputFileException(line, "Invalid coin bonus expression on line: %n%s", line.trimmedInput());

				int min, max;
				try {
					min = DataUtils.parseIntString(tokens[1]);
					max = DataUtils.parseIntString(tokens[2]);
				}
				catch (InvalidInputException e) {
					throw new InputFileException(line, "Could not parse coin bonus amount: %s or %s", tokens[1], tokens[2]);
				}

				newTokens.add(String.format("%04Xs", min));
				newTokens.add(String.format("%04Xs", max));
				newTokens.add("0000s");
			}
				break;

			case "movement": {
				MapIndex index = encoder.getCurrentMap();
				if (index == null)
					throw new InputFileException(line, "Invalid Movement expression: no map is associated with source");

				if (tokens.length != 2)
					throw new InputFileException(line, "Invalid Movement expression on line: %n%s", line.trimmedInput());

				Marker m = index.getMarker(tokens[1]);

				if (m == null)
					throw new InputFileException(line, "Cannot find marker: " + tokens[1]);

				if (m.getType() != MarkerType.NPC)
					throw new InputFileException(line, tokens[1] + " is not an NPC marker");

				if (m.npcComponent == null)
					throw new InputFileException(line, tokens[1] + " does not have any movement data");

				for (int v : m.npcComponent.getTerritoryData())
					newTokens.add(String.format("%08X", v));
			}
				break;

			case "nomovement": {
				for (int i = 0; i < 48; i++)
					newTokens.add("00000000");
			}
				break;

			case "animationtable": {
				if (tokens.length != 2)
					throw new InputFileException(line, "Invalid AnimationTable expression on line: %n%s", line.trimmedInput());

				MapIndex index = encoder.getCurrentMap();
				if (index == null)
					throw new InputFileException(line, "Invalid AnimationTable expression: no map is associated with source");

				Marker m = index.getMarker(tokens[1]);
				if (m == null)
					throw new InputFileException(line, "Cannot find marker: " + tokens[1]);

				if (m.getType() != MarkerType.NPC)
					throw new InputFileException(line, tokens[1] + " is not an NPC marker");

				for (int i = 0; i < ANIM_TABLE_SIZE; i++) {
					int v = m.npcComponent.getAnimation(i);
					newTokens.add(String.format("%08X",
						((m.npcComponent.getSpriteID() & 0xFF) << 16) |
							((m.npcComponent.getPaletteID() & 0xFF) << 8) |
							(v & 0xFF)));
				}
			}
				break;
		}
	}

	private static final HashMap<String, StructField> fields = new HashMap<>();

	private static void addField(StructField a)
	{
		fields.put("[" + a.name + "]", a);
	}

	private static final StructField NpcIDA = new StructField("ID", 0x0, 4, Style.Ints, false);
	private static final StructField SpawnPosA = new StructField("SpawnPos", 0x8, 12, Style.Shorts, false);
	private static final StructField FlagsA = new StructField("Flags", 0x14, 4, Style.Ints, false);
	private static final StructField ItemDropsA = new StructField("ItemDrops", 0x28, 50, Style.Shorts, false);
	private static final StructField HeartDropsA = new StructField("HeartDrops", 0x5A, 64, Style.Shorts, false);
	private static final StructField FlowerDropsA = new StructField("FlowerDrops", 0x9A, 64, Style.Shorts, false);
	private static final StructField MoveDataA = new StructField("MoveData", 0xE0, 192, Style.Ints, false);
	private static final StructField AnimationsA = new StructField("Animations", 0x1A0, 64, Style.Ints, false);

	static {
		addField(NpcIDA);
		addField(FlagsA);
		addField(SpawnPosA);
		addField(ItemDropsA);
		addField(HeartDropsA);
		addField(FlowerDropsA);
		addField(MoveDataA);
		addField(AnimationsA);
	}

	@Override
	public StructField parseFieldOffset(BaseDataEncoder encoder, Struct struct, String offsetName)
	{
		return fields.get(offsetName);
	}

	// definitions for some standard drop tables

	private static boolean matches(int[][] preset, short[][] table)
	{
		for (int i = 0; i < 8; i++) {
			if (table[i][0] != (short) preset[i][0])
				return false;
			if (table[i][1] != (short) preset[i][1])
				return false;
			if (table[i][3] != (short) preset[i][3])
				return false;
		}
		return true;
	}

	private static enum PresetHP
	{
		NONE ("None", HP_NONE),
		STANDARD ("Standard", HP_STANDARD),
		GENEROUS ("Generous", HP_GENEROUS), // used throughout isk
		GENEROUS_WHEN_LOW ("GenerousWhenLow", HP_GENEROUS_WHEN_LOW); // used for some early areas (some kmr maps and nok_12)

		private final String name;
		private final int[][] table;

		private PresetHP(String name, int[][] table)
		{
			this.name = name;
			this.table = table;
		}

		public static PresetHP getMatch(short[][] table)
		{
			for (PresetHP preset : values()) {
				if (matches(preset.table, table))
					return preset;
			}
			return null;
		}
	}

	private static enum PresetFP
	{
		NONE ("None", FP_NONE),
		STANDARD ("Standard", FP_STANDARD),
		GENEROUS_WHEN_LOW ("GenerousWhenLow", FP_GENEROUS_WHEN_LOW), // used for some early areas (some kmr maps and nok_12)
		SLIGHLY_REDUCED ("SlightlyReduced", FP_SLIGHLY_REDUCED); // fuzzies in nok_03 only

		private final String name;
		private final int[][] table;

		private PresetFP(String name, int[][] table)
		{
			this.name = name;
			this.table = table;
		}

		public static PresetFP getMatch(short[][] table)
		{
			for (PresetFP preset : values()) {
				if (matches(preset.table, table))
					return preset;
			}
			return null;
		}
	}

	private static final int[][] HP_NONE = {
			{ 0x7FFF, 0x0, 0xFFFF, 0x0 },
			{ 0x0, 0x0, 0x0, 0x0 },
			{ 0x0, 0x0, 0x0, 0x0 },
			{ 0x0, 0x0, 0x0, 0x0 },
			{ 0x0, 0x0, 0x0, 0x0 },
			{ 0x0, 0x0, 0x0, 0x0 },
			{ 0x0, 0x0, 0x0, 0x0 },
			{ 0x0, 0x0, 0x0, 0x0 } };

	private static final int[][] HP_STANDARD = {
			{ 0x1999, 0x5998, 0xFFFF, 0x3FFF },
			{ 0x2666, 0x4CCC, 0xFFFF, 0x3FFF },
			{ 0x3FFF, 0x3FFF, 0xFFFF, 0x3332 },
			{ 0x6665, 0x3332, 0xFFFF, 0x3332 },
			{ 0x7FFF, 0x2666, 0xFFFF, 0x2666 },
			{ 0x0, 0x0, 0x0, 0x0 },
			{ 0x0, 0x0, 0x0, 0x0 },
			{ 0x0, 0x0, 0x0, 0x0 } };

	private static final int[][] HP_GENEROUS = {
			{ 0x1999, 0x6665, 0xFFFF, 0x3FFF },
			{ 0x2666, 0x5998, 0xFFFF, 0x3FFF },
			{ 0x3FFF, 0x4CCC, 0xFFFF, 0x3332 },
			{ 0x6665, 0x3FFF, 0xFFFF, 0x3332 },
			{ 0x7FFF, 0x3332, 0xFFFF, 0x2666 },
			{ 0x0, 0x0, 0x0, 0x0 },
			{ 0x0, 0x0, 0x0, 0x0 },
			{ 0x0, 0x0, 0x0, 0x0 } };

	private static final int[][] HP_GENEROUS_WHEN_LOW = {
			{ 0x1999, 0x6665, 0xFFFF, 0x4CCC },
			{ 0x2666, 0x5998, 0xFFFF, 0x3FFF },
			{ 0x3FFF, 0x4CCC, 0xFFFF, 0x3FFF },
			{ 0x6665, 0x3FFF, 0xFFFF, 0x3332 },
			{ 0x7FFF, 0x2666, 0xFFFF, 0x2666 },
			{ 0x0, 0x0, 0x0, 0x0 },
			{ 0x0, 0x0, 0x0, 0x0 },
			{ 0x0, 0x0, 0x0, 0x0 } };

	private static final int[][] FP_NONE = {
			{ 0x7FFF, 0x0, 0xFFFF, 0x0 },
			{ 0x0, 0x0, 0x0, 0x0 },
			{ 0x0, 0x0, 0x0, 0x0 },
			{ 0x0, 0x0, 0x0, 0x0 },
			{ 0x0, 0x0, 0x0, 0x0 },
			{ 0x0, 0x0, 0x0, 0x0 },
			{ 0x0, 0x0, 0x0, 0x0 },
			{ 0x0, 0x0, 0x0, 0x0 } };

	private static final int[][] FP_STANDARD = {
			{ 0x1999, 0x3FFF, 0xFFFF, 0x3332 },
			{ 0x2666, 0x3332, 0xFFFF, 0x3332 },
			{ 0x3FFF, 0x3332, 0xFFFF, 0x3332 },
			{ 0x6665, 0x3332, 0xFFFF, 0x3332 },
			{ 0x7FFF, 0x2666, 0xFFFF, 0x3332 },
			{ 0x0, 0x0, 0x0, 0x0 },
			{ 0x0, 0x0, 0x0, 0x0 },
			{ 0x0, 0x0, 0x0, 0x0 } };

	private static final int[][] FP_GENEROUS_WHEN_LOW = {
			{ 0x1999, 0x5998, 0xFFFF, 0x3FFF },
			{ 0x2666, 0x4CCC, 0xFFFF, 0x3FFF },
			{ 0x3FFF, 0x3FFF, 0xFFFF, 0x3332 },
			{ 0x6665, 0x3332, 0xFFFF, 0x3332 },
			{ 0x7FFF, 0x2666, 0xFFFF, 0x3332 },
			{ 0x0, 0x0, 0x0, 0x0 },
			{ 0x0, 0x0, 0x0, 0x0 },
			{ 0x0, 0x0, 0x0, 0x0 } };

	private static final int[][] FP_SLIGHLY_REDUCED = {
			{ 0x1999, 0x3332, 0xFFFF, 0x3332 },
			{ 0x2666, 0x3332, 0xFFFF, 0x3332 },
			{ 0x3FFF, 0x3332, 0xFFFF, 0x3332 },
			{ 0x6665, 0x3332, 0xFFFF, 0x3332 },
			{ 0x7FFF, 0x2666, 0xFFFF, 0x3332 },
			{ 0x0, 0x0, 0x0, 0x0 },
			{ 0x0, 0x0, 0x0, 0x0 },
			{ 0x0, 0x0, 0x0, 0x0 } };

	/*
		~HP:Standard:N	% standard drop table for up to N hearts with N=2,3,4,5
		HP:20:70:2:50 HP:30:60:2:50 HP:50:50:2:40 HP:80:40:2:40 HP:100:30:2:30 HP:None HP:None HP:None
		HP:20:70:3:50 HP:30:60:3:50 HP:50:50:3:40 HP:80:40:3:40 HP:100:30:3:30 HP:None HP:None HP:None
		HP:20:70:4:50 HP:30:60:4:50 HP:50:50:4:40 HP:80:40:4:40 HP:100:30:4:30 HP:None HP:None HP:None
		HP:20:70:5:50 HP:30:60:5:50 HP:50:50:5:40 HP:80:40:5:40 HP:100:30:5:30 HP:None HP:None HP:None

		~HP:Generous:N % N=2 only
		HP:20:80:2:50 HP:30:70:2:50 HP:50:60:2:40 HP:80:50:2:40 HP:100:40:2:30 HP:None HP:None HP:None
		HP:20:80:3:50 HP:30:70:3:50 HP:50:60:3:40 HP:80:50:3:40 HP:100:40:3:30 HP:None HP:None HP:None
		      10            10            10            10             10

		~HP:GenerousWhenLow:N % N=2 only
		HP:20:80:2:60 HP:30:70:2:50 HP:50:60:2:50 HP:80:50:2:40 HP:100:30:2:30 HP:None HP:None HP:None
		      10   10       10            10            10

		~FP:Standard:N	% standard drop table for up to N hearts with N=2,3,4,5,6
		FP:20:50:2:40 FP:30:40:2:40 FP:50:40:2:40 FP:80:40:2:40 FP:100:30:2:40 FP:None FP:None FP:None
		FP:20:50:3:40 FP:30:40:3:40 FP:50:40:3:40 FP:80:40:3:40 FP:100:30:3:40 FP:None FP:None FP:None
		FP:20:50:4:40 FP:30:40:4:40 FP:50:40:4:40 FP:80:40:4:40 FP:100:30:4:40 FP:None FP:None FP:None
		FP:20:50:5:40 FP:30:40:5:40 FP:50:40:5:40 FP:80:40:5:40 FP:100:30:5:40 FP:None FP:None FP:None
		FP:20:50:6:40 FP:30:40:6:40 FP:50:40:6:40 FP:80:40:6:40 FP:100:30:6:40 FP:None FP:None FP:None

		~FP:GenerousWhenLow:N % N=2 only
		FP:20:70:2:50 FP:30:60:2:50 FP:50:50:2:40 FP:80:40:2:40 FP:100:30:2:40 FP:None FP:None FP:None
		      20   10       20   10       10

		~FP:SlightlyReduced:N
		FP:20:40:2:40 FP:30:40:2:40 FP:50:40:2:40 FP:80:40:2:40 FP:100:30:2:40 FP:None FP:None FP:None
		     -10
	 */
}

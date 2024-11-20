package game.battle.struct;

import static game.shared.StructTypes.*;

import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.util.HashMap;

import game.shared.BaseStruct;
import game.shared.ProjectDatabase;
import game.shared.StructField;
import game.shared.StructField.Style;
import game.shared.decoder.BaseDataDecoder;
import game.shared.decoder.Pointer;
import game.shared.encoder.BaseDataEncoder;
import game.shared.struct.Struct;

public class Actor extends BaseStruct
{
	public static final Actor instance = new Actor();

	private Actor()
	{}

	public static final String[] nameIDs = new String[0xD4];

	@Override
	public void scan(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer)
	{
		int start = fileBuffer.position();
		fileBuffer.position(start + 5);

		int nameIndex = (fileBuffer.get() & 0xFF);
		ptr.setDescriptor(ProjectDatabase.getActorName(nameIndex));

		if (nameIDs[nameIndex] == null)
			nameIDs[nameIndex] = decoder.getSourceName();

		fileBuffer.position(start + 0x8);
		int numStates = fileBuffer.getShort();
		fileBuffer.getShort(); // always zero

		int ptrStateTable = fileBuffer.getInt();
		int ptrAIScript = fileBuffer.getInt();
		int ptrStatusTable = fileBuffer.getInt();

		if (ptrStateTable != 0) {
			Pointer stateTableInfo = decoder.enqueueAsChild(ptr, ptrStateTable, PartsTableT);
			stateTableInfo.listLength = numStates;
			stateTableInfo.ancestors.add(ptr);
		}

		if (ptrAIScript != 0) {
			Pointer aiScriptInfo = decoder.enqueueAsChild(ptr, ptrAIScript, ScriptT);
			aiScriptInfo.setDescriptor("Init");
			aiScriptInfo.ancestors.add(ptr);
		}

		if (ptrStatusTable != 0) // violated by unused version of the master in area 09
		{
			Pointer statusTableInfo = decoder.enqueueAsChild(ptr, ptrStatusTable, StatusTableT);
			statusTableInfo.ancestors.add(ptr);
		}

		fileBuffer.position(start + 0x28);
	}

	@Override
	public void print(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer, PrintWriter pw)
	{
		byte[] enemyBytes = new byte[0x28];
		fileBuffer.get(enemyBytes);
		ByteBuffer bb = ByteBuffer.wrap(enemyBytes);

		pw.println("% stats");
		IndexA.print(decoder, bb, pw);
		LevelA.print(decoder, bb, pw);
		MaxHPA.print(decoder, bb, pw);
		CoinsA.print(decoder, bb, pw);
		FlagsA.print(decoder, bb, pw);
		StatusTableA.print(decoder, bb, pw);
		pw.println("% ai");
		PartsCountA.print(decoder, bb, pw);
		PartsTableA.print(decoder, bb, pw);
		ScriptA.print(decoder, bb, pw);
		pw.println("% move effectiveness");
		EscapeA.print(decoder, bb, pw);
		ItemA.print(decoder, bb, pw);
		AirLiftA.print(decoder, bb, pw);
		HurricaneA.print(decoder, bb, pw);
		UpAndAwayA.print(decoder, bb, pw);
		PowerBounceA.print(decoder, bb, pw);
		SpinSmashA.print(decoder, bb, pw);
		pw.println("% ui positions");
		SizeA.print(decoder, bb, pw);
		HealthBarA.print(decoder, bb, pw);
		StatusTurnA.print(decoder, bb, pw);
		StatusIconA.print(decoder, bb, pw);
	}

	private static final HashMap<String, StructField> fields = new HashMap<>();

	private static void addField(StructField a)
	{
		fields.put("[" + a.name + "]", a);
	}

	private static final StructField FlagsA = new StructField("Flags", 0x00, 4, Style.Ints, false);
	private static final StructField IndexA = new StructField("Index", 0x05, 1, Style.Bytes, false);
	private static final StructField LevelA = new StructField("Level", 0x06, 1, Style.Bytes, true);
	private static final StructField MaxHPA = new StructField("MaxHP", 0x07, 1, Style.Bytes, true);
	private static final StructField PartsCountA = new StructField("PartsCount", 0x08, 2, Style.Shorts, true);
	private static final StructField PartsTableA = new StructField("PartsTable", 0x0C, 4, Style.Ints, false);
	private static final StructField ScriptA = new StructField("Script", 0x10, 4, Style.Ints, false);
	private static final StructField StatusTableA = new StructField("StatusTable", 0x14, 4, Style.Ints, false);
	private static final StructField EscapeA = new StructField("Escape", 0x18, 1, Style.Bytes, true);
	private static final StructField AirLiftA = new StructField("AirLift", 0x19, 1, Style.Bytes, true);
	private static final StructField HurricaneA = new StructField("Hurricane", 0x1A, 1, Style.Bytes, true, "Bow's \"Spook\" as well");
	private static final StructField ItemA = new StructField("Item", 0x1B, 1, Style.Bytes, true);
	private static final StructField UpAndAwayA = new StructField("UpAndAway", 0x1C, 1, Style.Bytes, true);
	private static final StructField SpinSmashA = new StructField("SpinSmash", 0x1D, 1, Style.Bytes, true, "weight (0-4)");
	private static final StructField PowerBounceA = new StructField("PowerBounce", 0x1E, 1, Style.Bytes, true);
	private static final StructField CoinsA = new StructField("Coins", 0x1F, 1, Style.Bytes, true);
	private static final StructField SizeA = new StructField("Size", 0x20, 2, Style.Bytes, true, "width height");
	private static final StructField HealthBarA = new StructField("HealthBar", 0x22, 2, Style.Bytes, true, "dx dy");
	private static final StructField StatusTurnA = new StructField("StatusTurn", 0x24, 2, Style.Bytes, true, "dx dy");
	private static final StructField StatusIconA = new StructField("StatusIcon", 0x26, 2, Style.Bytes, true, "dx dy");

	static {
		addField(FlagsA);
		addField(IndexA);
		addField(LevelA);
		addField(MaxHPA);
		addField(PartsCountA);
		addField(PartsTableA);
		addField(ScriptA);
		addField(StatusTableA);
		addField(EscapeA);
		addField(AirLiftA);
		addField(HurricaneA);
		addField(ItemA);
		addField(UpAndAwayA);
		addField(SpinSmashA);
		addField(PowerBounceA);
		addField(CoinsA);
		addField(SizeA);
		addField(HealthBarA);
		addField(StatusTurnA);
		addField(StatusIconA);
	}

	@Override
	public StructField parseFieldOffset(BaseDataEncoder encoder, Struct struct, String offsetName)
	{
		return fields.get(offsetName);
	}
}

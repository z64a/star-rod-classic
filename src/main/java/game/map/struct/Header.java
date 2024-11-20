package game.map.struct;

import static game.shared.StructTypes.*;

import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.util.HashMap;

import game.map.patching.MapDecoder;
import game.shared.BaseStruct;
import game.shared.StructField;
import game.shared.StructField.Style;
import game.shared.decoder.BaseDataDecoder;
import game.shared.decoder.Pointer;
import game.shared.encoder.BaseDataEncoder;
import game.shared.struct.Struct;

public class Header extends BaseStruct
{
	public static final Header instance = new Header();

	private Header()
	{}

	@Override
	public void scan(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer)
	{
		int z;
		z = fileBuffer.getInt();
		assert (z == 0);
		z = fileBuffer.getInt();
		assert (z == 0);
		z = fileBuffer.getInt();
		assert (z == 0);
		z = fileBuffer.getInt();
		assert (z == 0);

		int ptrMainScript = fileBuffer.getInt();
		int ptrEntryList = fileBuffer.getInt();
		int entryListLength = fileBuffer.getInt();
		fileBuffer.getInt();

		z = fileBuffer.getInt();
		assert (z == 0);
		z = fileBuffer.getInt();
		assert (z == 0);
		z = fileBuffer.getInt();
		assert (z == 0);
		z = fileBuffer.getInt();
		assert (z == 0);

		z = fileBuffer.getInt();
		assert (z == 0);
		z = fileBuffer.getInt();
		assert (z == 0);
		int ptrBackground = fileBuffer.getInt();
		int tattleID = fileBuffer.getInt();

		//	assert(ptrBackground == 0x80200000) : String.format("%08X", ptrBackground);

		Pointer mainScript = decoder.enqueueAsChild(ptr, ptrMainScript, MainScriptT);
		if (decoder instanceof MapDecoder)
			((MapDecoder) decoder).mainScript = mainScript;

		decoder.enqueueAsChild(ptr, ptrEntryList, EntryListT).listLength = entryListLength;
		decoder.tryEnqueueAsChild(ptr, tattleID, GetTattleFunctionT);
	}

	@Override
	public void print(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer, PrintWriter pw)
	{
		//decoder.printHex(ptr, fileBuffer, pw, 4);

		byte[] enemyBytes = new byte[0x40];
		fileBuffer.get(enemyBytes);
		ByteBuffer bb = ByteBuffer.wrap(enemyBytes);

		MainScript.print(decoder, bb, pw);
		EntryList.print(decoder, bb, pw);
		EntryCount.print(decoder, bb, pw);
		Background.print(decoder, bb, pw);
		MapTattle.print(decoder, bb, pw);
	}

	private static final HashMap<String, StructField> fields = new HashMap<>();

	private static void addField(StructField a)
	{
		fields.put("[" + a.name + "]", a);
	}

	public static final StructField MainScript = new StructField("MainScript", 0x10, 4, Style.Ints, false);
	public static final StructField EntryList = new StructField("EntryList", 0x14, 4, Style.Ints, false);
	public static final StructField EntryCount = new StructField("EntryCount", 0x18, 4, Style.Ints, false);
	public static final StructField Background = new StructField("Background", 0x38, 4, Style.Ints, false);
	public static final StructField MapTattle = new StructField("MapTattle", 0x3C, 4, Style.Ints, false);

	static {
		addField(MainScript);
		addField(EntryList);
		addField(EntryCount);
		addField(Background);
		addField(MapTattle);
	}

	@Override
	public StructField parseFieldOffset(BaseDataEncoder encoder, Struct struct, String offsetName)
	{
		return fields.get(offsetName);
	}
}

package game.battle.struct;

import static game.shared.StructTypes.*;

import java.io.PrintWriter;
import java.nio.ByteBuffer;

import app.input.IOUtils;
import game.shared.BaseStruct;
import game.shared.decoder.BaseDataDecoder;
import game.shared.decoder.Pointer;

public class Stage extends BaseStruct
{
	public static final Stage instance = new Stage();

	private Stage()
	{}

	@Override
	public void scan(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer)
	{
		int ptrTexName = fileBuffer.getInt();
		if (ptrTexName != 0)
			decoder.enqueueAsChild(ptr, ptrTexName, AsciiT);

		int ptrShapeName = fileBuffer.getInt();
		if (ptrShapeName != 0)
			decoder.enqueueAsChild(ptr, ptrShapeName, AsciiT);

		int ptrHitName = fileBuffer.getInt();
		if (ptrHitName != 0)
			decoder.enqueueAsChild(ptr, ptrHitName, AsciiT);

		int pos = fileBuffer.position();
		fileBuffer.position(decoder.toOffset(ptrShapeName));
		String mapName = IOUtils.readString(fileBuffer);
		fileBuffer.position(pos);

		assert (mapName.endsWith("_shape"));
		mapName = mapName.substring(0, mapName.length() - 6);

		// executed before screen fades from black
		Pointer beforeScript = decoder.enqueueAsChild(ptr, fileBuffer.getInt(), ScriptT);
		beforeScript.setDescriptor("BeforeBattle");
		beforeScript.mapName = mapName;

		// executed after screen fades to black
		Pointer afterScript = decoder.enqueueAsChild(ptr, fileBuffer.getInt(), ScriptT);
		afterScript.setDescriptor("AfterBattle");
		afterScript.mapName = mapName;

		int ptrBackgroundName = fileBuffer.getInt();
		if (ptrBackgroundName != 0)
			decoder.enqueueAsChild(ptr, ptrBackgroundName, AsciiT);

		int ptrForegroundList = fileBuffer.getInt();
		if (ptrForegroundList != 0)
			decoder.enqueueAsChild(ptr, ptrForegroundList, ForegroundListT).mapName = mapName;

		int encounterSize = fileBuffer.getInt();
		int ptrSpecialFormation = fileBuffer.getInt();

		if (encounterSize > 0) {
			// used for whacka (09) and slot machine parts (10, 12, 27)
			Pointer specialInfo = decoder.enqueueAsChild(ptr, ptrSpecialFormation, SpecialFormationT);
			specialInfo.listLength = encounterSize;
		}

		// unknown flags, only used for whacka (09) = 00000200
		fileBuffer.getInt();
	}

	@Override
	public void print(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer, PrintWriter pw)
	{
		int v = fileBuffer.getInt();
		if (v != 0) {
			decoder.printWord(pw, v);
			pw.println(" % " + decoder.getPointer(v).text);
		}
		else {
			pw.println("00000000 ");
		}

		v = fileBuffer.getInt();
		if (v != 0) {
			decoder.printWord(pw, v);
			pw.println(" % " + decoder.getPointer(v).text);
		}
		else {
			pw.println("00000000 ");
		}

		v = fileBuffer.getInt();
		if (v != 0) {
			decoder.printWord(pw, v);
			pw.println(" % " + decoder.getPointer(v).text);
		}
		else {
			pw.println("00000000 ");
		}

		decoder.printWord(pw, fileBuffer.getInt());
		pw.println();

		decoder.printWord(pw, fileBuffer.getInt());
		pw.println();

		v = fileBuffer.getInt();
		if (v != 0) {
			decoder.printWord(pw, v);
			pw.println(" % " + decoder.getPointer(v).text);
		}
		else {
			pw.println("00000000 ");
		}

		decoder.printWord(pw, fileBuffer.getInt());
		pw.println();

		decoder.printWord(pw, fileBuffer.getInt());
		pw.println();

		decoder.printWord(pw, fileBuffer.getInt());
		pw.println();

		decoder.printWord(pw, fileBuffer.getInt());
		pw.println();
	}
}

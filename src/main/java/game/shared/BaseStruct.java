package game.shared;

import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.util.List;

import app.input.Line;
import app.input.Token;
import game.shared.decoder.BaseDataDecoder;
import game.shared.decoder.IPrint;
import game.shared.decoder.IScan;
import game.shared.decoder.Pointer;
import game.shared.encoder.BaseDataEncoder;
import game.shared.encoder.IParseField;
import game.shared.encoder.IPatchLength;
import game.shared.encoder.IReplaceExpression;
import game.shared.encoder.IReplaceSpecial;
import game.shared.encoder.Patch;
import game.shared.struct.Struct;

/**
 * To add a new struct:
 * (1) Create a class extending either MapStruct or BattleStruct which contains the scan/print/etc code.
 * (2) Add a static StructType instance for the new struct in StructTypes
 */
public class BaseStruct implements IPrint, IScan, IParseField, IReplaceExpression, IReplaceSpecial, IPatchLength
{
	public static final BaseStruct instance = new BaseStruct();

	@Override
	public void scan(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer)
	{}

	@Override
	public void print(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer, PrintWriter pw)
	{
		decoder.printHex(ptr, fileBuffer, pw);
	}

	@Override
	public StructField parseFieldOffset(BaseDataEncoder encoder, Struct struct, String offsetName)
	{
		return null;
	}

	@Override
	public void replaceExpression(Line line, BaseDataEncoder encoder, String[] args, List<String> newTokenList)
	{}

	@Override
	public void replaceStructConstants(BaseDataEncoder encoder, Patch patch)
	{}

	@Override
	public int getPatchLength(BaseDataEncoder encoder, Patch patch)
	{
		int length = 0;

		for (Line line : patch.lines) {
			for (Token t : line.tokens) {
				if (DataUtils.isPointerFmt(t.str) || DataUtils.isScriptVarFmt(t.str))
					length += 4;
				else
					length += DataUtils.getSize(t.str);
			}
		}

		return length;
	}

	/*
	// maintain a set of instances for each struct class
	private static HashMap<Class<? extends BaseStruct>, BaseStruct> instanceMap;

	protected static void init()
	{
		instanceMap = new HashMap<>();
	}

	@SuppressWarnings("unchecked")
	public static <T extends BaseStruct> T get(Class<T> cls)
	{
		BaseStruct struct = instanceMap.get(cls);
		if(struct == null)
		{
			try {
				struct = cls.getDeclaredConstructor().newInstance();
				instanceMap.put(cls, struct);
			}
			catch (Exception  e) {
				throw new StarRodException("Failed to create struct instance %s %n%s", cls.getSimpleName(), e.getMessage());
			}
		}

		return (T)struct;
	}
	*/
}

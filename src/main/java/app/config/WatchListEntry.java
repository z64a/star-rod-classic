package app.config;

import java.nio.ByteBuffer;

import app.StarRodException;
import app.input.InvalidInputException;
import game.shared.SyntaxConstants;
import game.shared.struct.script.ScriptVariable;

public class WatchListEntry
{
	public static enum Category
	{
		Memory,
		Variable,
		Clear
	}

	public static enum MemoryType
	{
		// @formatter:off
		Byte	("1", 1),
		Short	("2", 2),
		Word	("4", 4),
		Float	("F", 0x104),
		Double	("D", 0x108);
		// @formatter:on

		public final String code;
		public final int len;

		private MemoryType(String code, int len)
		{
			this.code = code;
			this.len = len;
		}
	}

	public Category category = Category.Memory;
	public MemoryType memType = MemoryType.Byte;

	public String name;
	public int addr;

	private int varRef;

	public WatchListEntry()
	{}

	public WatchListEntry(Config cfg, Options opt)
	{
		this(cfg.getString(opt));
	}

	public WatchListEntry(String s)
	{
		String[] fields = s.split(",");
		if (fields.length == 1) {
			category = Category.Variable;
			name = fields[0]; // assume this is var
			//XXX parseVarName();
		}
		else if (fields.length == 3) {
			category = Category.Memory;
			name = fields[2];

			try {
				addr = (int) Long.parseLong(fields[0], 16);

				if (fields[1].equalsIgnoreCase("F"))
					memType = MemoryType.Float;
				else if (fields[1].equalsIgnoreCase("D"))
					memType = MemoryType.Double;
				else {
					int len = Integer.parseInt(fields[1], 16);
					if (len == 1)
						memType = MemoryType.Byte;
					else if (len == 2)
						memType = MemoryType.Short;
					else if (len == 4)
						memType = MemoryType.Word;
					else
						throw new StarRodException("Invalid memory address for debug watchlist in mod.cfg: " + s);
				}

			}
			catch (NumberFormatException e) {
				throw new StarRodException("Invalid memory address for debug watchlist in mod.cfg: " + s);
			}
		}
		else
			throw new StarRodException("Invalid value for debug watchlist in mod.cfg: " + s);
	}

	private int parseVarName()
	{
		int len;
		int varRef;
		ScriptVariable varType;

		try {
			varRef = (int) Long.parseLong(ScriptVariable.parseScriptVariable(SyntaxConstants.SCRIPT_VAR_PREFIX + name), 16);
		}
		catch (InvalidInputException | NumberFormatException e1) {
			throw new StarRodException("Invalid variable name for debug watchlist in mod.cfg: " + name);
		}

		varType = ScriptVariable.getType(varRef);
		if (varType == null)
			throw new StarRodException("Invalid variable type for debug watchlist in mod.cfg: " + name);

		// get printing length for variable type
		switch (varType) {
			case MapFlag:
			case AreaFlag:
			case GameFlag:
			case ModFlag:
				len = 0;
				break;

			case AreaByte:
			case GameByte:
			case ModByte:
				len = 1;
				break;

			case MapVar:
				len = 4;
				break;

			default:
				throw new StarRodException("Forbidden variable type for debug watchlist in mod.cfg: " + name);
		}

		return len;
	}

	public String getText()
	{
		switch (category) {
			case Memory:
				return String.format("%X,%s,%s", addr, memType.code, name);

			case Variable:
				parseVarName();
				return name;

			case Clear:
				return "";
		}

		throw new StarRodException("Invalid watch list category: " + category);
	}

	public void put(ByteBuffer bb)
	{
		int len = 1;
		int ref = 0;

		switch (category) {
			case Memory:
				len = memType.len;
				ref = addr;
				break;

			case Variable:
				len = parseVarName();
				ref = varRef;
				break;

			case Clear:
				return;
		}

		if (name.length() > 15)
			name = name.substring(0, 15);

		bb.putShort((short) category.ordinal());
		bb.putShort((short) len);
		bb.putInt(ref);
		bb.put(name.getBytes());
	}
}

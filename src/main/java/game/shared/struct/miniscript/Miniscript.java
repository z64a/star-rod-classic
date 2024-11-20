package game.shared.struct.miniscript;

import static game.shared.StructTypes.FunctionT;

import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.StarRodException;
import app.input.InputFileException;
import app.input.InvalidInputException;
import app.input.Line;
import game.shared.BaseStruct;
import game.shared.ProjectDatabase;
import game.shared.ProjectDatabase.ConstEnum;
import game.shared.decoder.BaseDataDecoder;
import game.shared.decoder.Pointer;
import game.shared.encoder.BaseDataEncoder;
import game.shared.encoder.Patch;
import game.shared.struct.StructType;
import patcher.RomPatcher;
import util.ArrayIterator;
import util.CaseInsensitiveMap;

public abstract class Miniscript extends BaseStruct
{
	private static final Pattern LinePattern = Pattern.compile("\\s*(\\S+)\\s*(?:\\(([^\\)]+)\\))?\\s*");
	private static final Matcher LineMatcher = LinePattern.matcher("");

	public final String scriptName;
	private final int scriptFlags;
	private final int opsize;

	public HashMap<Integer, MiniCommand> cmdDecodeMap; //TEMP for 0.5!
	private CaseInsensitiveMap<MiniCommand> cmdTypeMap;
	private final int minPrintWidth;

	private String lineComment = "";

	public Miniscript(String name, CommandBuilder ... types)
	{
		this(name, 0, types);
	}

	public Miniscript(String name, int flags, CommandBuilder ... types)
	{
		scriptName = name;
		scriptFlags = flags;

		if ((scriptFlags & U8_OPCODES) != 0)
			opsize = 8;
		else if ((scriptFlags & U16_OPCODES) != 0)
			opsize = 16;
		else
			opsize = 32;

		cmdDecodeMap = new HashMap<>();
		cmdTypeMap = new CaseInsensitiveMap<>();
		for (CommandBuilder builder : types) {
			MiniCommand type = builder.build();
			cmdDecodeMap.put(type.opcode, type);
			cmdTypeMap.put(type.name, type);
		}

		Arrays.sort(types, (a, b) -> b.name.length() - a.name.length());
		int sample = (int) (types.length * 0.33);
		minPrintWidth = ((types[sample].name.length() + 1) + 3) & -4;
	}

	public int getLength(RomPatcher rp)
	{
		int startPos = rp.getCurrentOffset();
		boolean done = false;
		while (!done) {
			int opcode;
			switch (opsize) {
				case 8:
					opcode = rp.readByte() & 0xFF;
					break;
				case 16:
					opcode = rp.readShort() & 0xFFFF;
					break;
				default:
					opcode = rp.readInt();
					break;
			}

			MiniCommand cmd = cmdDecodeMap.get(opcode);
			if (cmd == null)
				throw new StarRodException("%s -- could not read opcode for %s: %08X", rp.getSourceName(), scriptName, opcode);
			for (int i = 0; i < cmd.argc; i++)
				cmd.args[i].consume(rp);
			if (cmd.scanCallback != null)
				cmd.scanCallback.accept(this);
			done = cmd.hasFlag(ENDS);
		}
		int endPos = rp.getCurrentOffset();
		return endPos - startPos;
	}

	@Override
	public void scan(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer)
	{
		boolean done = false;
		while (!done) {
			int opcode;
			switch (opsize) {
				case 8:
					opcode = fileBuffer.get() & 0xFF;
					break;
				case 16:
					opcode = fileBuffer.getShort() & 0xFFFF;
					break;
				default:
					opcode = fileBuffer.getInt();
					break;
			}

			MiniCommand cmd = cmdDecodeMap.get(opcode);
			if (cmd == null)
				throw new StarRodException("%s -- could not read opcode for %s: %08X", ptr.getPointerName(), scriptName, opcode);
			for (int i = 0; i < cmd.argc; i++)
				cmd.args[i].scan(this, decoder, ptr, fileBuffer);
			if (cmd.scanCallback != null)
				cmd.scanCallback.accept(this);
			done = cmd.hasFlag(ENDS);
		}
	}

	@Override
	public void print(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer, PrintWriter pw)
	{
		boolean done = false;
		int tabDepth = 1;

		while (!done) {
			int opcode;
			switch (opsize) {
				case 8:
					opcode = fileBuffer.get() & 0xFF;
					break;
				case 16:
					opcode = fileBuffer.getShort() & 0xFFFF;
					break;
				default:
					opcode = fileBuffer.getInt();
					break;
			}

			MiniCommand cmd = cmdDecodeMap.get(opcode);
			if (cmd == null)
				throw new StarRodException("%s -- could not read opcode for %s: %08X", ptr.getPointerName(), scriptName, opcode);

			if (cmd.hasFlag(INDENT_LESS_NOW))
				tabDepth = Math.max(1, tabDepth - 1);

			for (int i = 0; i < tabDepth; i++)
				pw.print("\t");

			if (cmd.hasFlag(INDENT_MORE_AFTER))
				tabDepth = Math.max(1, tabDepth + 1);

			int printWidth = Math.max(minPrintWidth, ((cmd.name.length() + 1) + 3) & -4);
			pw.printf("%-" + printWidth + "s", cmd.name);

			if (cmd.argc > 0 || cmd.hasFlag(VARARGS))
				pw.printf("( ");

			for (int i = 0; i < cmd.argc; i++)
				cmd.args[i].print(this, decoder, ptr, fileBuffer, pw);

			if (cmd.argc > 0 || cmd.hasFlag(VARARGS))
				pw.printf(")");

			if (!lineComment.isEmpty())
				pw.print(" % " + lineComment);
			lineComment = "";

			pw.println();
			done = cmd.hasFlag(ENDS);
		}
	}

	@Override
	public void replaceStructConstants(BaseDataEncoder encoder, Patch patch)
	{
		for (Line line : patch.lines) {
			line.gather();
			line.replace(parseCommand(encoder, line));
		}
	}

	public String[] parseCommand(BaseDataEncoder encoder, Line line)
	{
		try {
			return parseCommand(encoder, line.str);
		}
		catch (InvalidInputException e) {
			throw new InputFileException(line, e);
		}
	}

	public String[] parseCommand(BaseDataEncoder encoder, String line) throws InvalidInputException
	{
		LineMatcher.reset(line);
		if (!LineMatcher.matches())
			throw new InvalidInputException("%s line invalid format: %n%s", scriptName, line);

		String cmdName = LineMatcher.group(1);
		String argList = LineMatcher.group(2);

		MiniCommand cmdType = cmdTypeMap.get(cmdName);
		String[] args = new String[0];

		if (cmdType == null)
			throw new InvalidInputException("Unknown command %s for %s: %n%s", cmdName, scriptName, line);

		if (cmdType.hasFlag(VARARGS)) {
			if (argList == null)
				throw new InvalidInputException("Command %s requires an arg list: %n%s", cmdName, line);

			args = argList.trim().split("\\s+");
		}
		else if (cmdType.argc > 0) {
			if (argList == null)
				throw new InvalidInputException("Command %s requires %d args: %n%s", cmdName, cmdType.argc, line);

			args = argList.trim().split("\\s+");
		}
		else {
			if (argList != null && !argList.trim().isEmpty())
				throw new InvalidInputException("Command %s cannot have args: %n%s", cmdName, line);
		}

		Iterator<String> iter = new ArrayIterator<>(args);
		List<String> tokens = new ArrayList<>(args.length + 1);
		tokens.add(String.format("%08X", cmdType.opcode));

		for (MiniCommandArg arg : cmdType.args) {
			try {
				arg.parse(this, encoder, iter, tokens);
			}
			catch (NotEnoughArgsException e) {
				throw new InvalidInputException("Not enough args for %s: %n%s", cmdType.name, line);
			}
		}

		if (iter.hasNext())
			throw new InvalidInputException("Too many args for %s: %n%s", cmdType.name, line);

		String[] tokenArray = new String[tokens.size()];
		tokens.toArray(tokenArray);
		return tokenArray;
	}

	public void appendLineComment(String comment)
	{
		lineComment += comment;
	}

	protected static final int BLOCKS = 1 << 0;
	protected static final int EXITS = 1 << 1;
	protected static final int ENDS = 1 << 2;
	protected static final int INDENT_MORE_AFTER = 1 << 3;
	protected static final int INDENT_LESS_NOW = 1 << 4;
	protected static final int VARARGS = 1 << 5;
	protected static final int U8_OPCODES = 1 << 6;
	protected static final int U16_OPCODES = 1 << 7;

	public static final class CommandBuilder
	{
		private final String name;
		private final int opcode;
		private int flags;
		private MiniCommandArg[] args = new MiniCommandArg[0];
		private Consumer<Object> scanCallback = null;

		public CommandBuilder(int opcode, String name)
		{
			this.name = name;
			this.opcode = opcode;
		}

		public CommandBuilder(int opcode, String name, int flags)
		{
			this(opcode, name);
			this.flags = flags;
		}

		public CommandBuilder(int opcode, String name, MiniCommandArg ... args)
		{
			this(opcode, name);
			this.args = args;
		}

		public CommandBuilder(int opcode, String name, int flags, MiniCommandArg ... args)
		{
			this(opcode, name);
			this.flags = flags;
			this.args = args;
		}

		public CommandBuilder setFlags(int flags)
		{
			this.flags = flags;
			return this;
		}

		public CommandBuilder setFlags(int ... flags)
		{
			for (int f : flags)
				this.flags |= f;
			return this;
		}

		public CommandBuilder setArgs(MiniCommandArg ... args)
		{
			this.args = args;
			return this;
		}

		public CommandBuilder setScanCallback(Consumer<Object> scanCallback)
		{
			this.scanCallback = scanCallback;
			return this;
		}

		public MiniCommand build()
		{
			return new MiniCommand(opcode, name, flags, args, scanCallback);
		}
	}

	public static final class MiniCommand
	{
		public final String name;
		public final int opcode;
		public final int flags;
		public final int argc;
		public final MiniCommandArg[] args;
		public final Consumer<Object> scanCallback;

		private MiniCommand(int opcode, String name, int flags, MiniCommandArg[] args, Consumer<Object> scanCallback)
		{
			this.name = name;
			this.opcode = opcode;
			this.flags = flags;
			this.argc = args.length;
			this.args = args;
			this.scanCallback = scanCallback;
		}

		public boolean hasFlag(int flag)
		{
			return (flags & flag) != 0;
		}
	}

	public static abstract class MiniCommandArg
	{
		public abstract void scan(Miniscript context, BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer);

		public abstract void print(Miniscript context, BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer, PrintWriter pw);

		public abstract void consume(RomPatcher rp);

		public void parse(Miniscript context, BaseDataEncoder encoder, Iterator<String> in, List<String> out)
			throws NotEnoughArgsException, InvalidInputException
		{
			if (!in.hasNext())
				throw new NotEnoughArgsException();
			out.add(in.next());
		}
	}

	public static class HexArg extends MiniCommandArg
	{
		private final int size;

		public HexArg()
		{
			this(32);
		}

		public HexArg(int size)
		{
			this.size = size;
			if (size != 8 && size != 16 && size != 32)
				throw new IllegalArgumentException("Invalid hex arg has size " + size);
		}

		@Override
		public void consume(RomPatcher rp)
		{
			switch (size) {
				case 8:
					rp.readByte();
					break;
				case 16:
					rp.readShort();
					break;
				default:
					rp.readInt();
					break;
			}
		}

		@Override
		public void scan(Miniscript context, BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer)
		{
			switch (size) {
				case 8:
					fileBuffer.get();
					break;
				case 16:
					fileBuffer.getShort();
					break;
				case 32:
					fileBuffer.getInt();
					break;
			}
		}

		@Override
		public void print(Miniscript context, BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer, PrintWriter pw)
		{
			switch (size) {
				case 8:
					pw.printf("%02Xb ", fileBuffer.get());
					break;
				case 16:
					pw.printf("%04Xs ", fileBuffer.getShort());
					break;
				case 32:
					decoder.printScriptWord(pw, fileBuffer.getInt());
					break;
			}
		}
	}

	public static class ShortHexArg extends MiniCommandArg
	{
		private final int size;

		public ShortHexArg()
		{
			this(32);
		}

		public ShortHexArg(int size)
		{
			this.size = size;
			if (size != 8 && size != 16 && size != 32)
				throw new IllegalArgumentException("Invalid hex arg has size " + size);
		}

		@Override
		public void consume(RomPatcher rp)
		{
			switch (size) {
				case 8:
					rp.readByte();
					break;
				case 16:
					rp.readShort();
					break;
				default:
					rp.readInt();
					break;
			}
		}

		@Override
		public void scan(Miniscript context, BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer)
		{
			switch (size) {
				case 8:
					fileBuffer.get();
					break;
				case 16:
					fileBuffer.getShort();
					break;
				case 32:
					fileBuffer.getInt();
					break;
			}
		}

		@Override
		public void print(Miniscript context, BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer, PrintWriter pw)
		{
			switch (size) {
				case 8:
					pw.printf("%Xb ", fileBuffer.get());
					break;
				case 16:
					pw.printf("%Xs ", fileBuffer.getShort());
					break;
				case 32:
					pw.printf("%X ", fileBuffer.getInt());
					break;
			}
		}
	}

	public static class DecArg extends MiniCommandArg
	{
		private final boolean usePadding;
		private final int size;

		public DecArg()
		{
			this(false, 32);
		}

		public DecArg(boolean usePadding)
		{
			this(usePadding, 32);
		}

		public DecArg(int size)
		{
			this(false, size);
		}

		public DecArg(boolean usePadding, int size)
		{
			this.usePadding = usePadding;
			this.size = size;
			if (size != 8 && size != 16 && size != 32)
				throw new IllegalArgumentException("Invalid dec arg has size " + size);
		}

		@Override
		public void consume(RomPatcher rp)
		{
			switch (size) {
				case 8:
					rp.readByte();
					break;
				case 16:
					rp.readShort();
					break;
				default:
					rp.readInt();
					break;
			}
		}

		@Override
		public void scan(Miniscript context, BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer)
		{
			switch (size) {
				case 8:
					fileBuffer.get();
					break;
				case 16:
					fileBuffer.getShort();
					break;
				case 32:
					fileBuffer.getInt();
					break;
			}
		}

		@Override
		public void print(Miniscript context, BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer, PrintWriter pw)
		{
			if (usePadding) {
				switch (size) {
					case 8:
						pw.printf("%2d`b ", fileBuffer.get());
						break;
					case 16:
						pw.printf("%4d`s ", fileBuffer.getShort());
						break;
					case 32:
						pw.printf("%6d` ", fileBuffer.getInt());
						break;
				}
			}
			else {
				switch (size) {
					case 8:
						pw.printf("%d`b ", fileBuffer.get());
						break;
					case 16:
						pw.printf("%d`s ", fileBuffer.getShort());
						break;
					case 32:
						pw.printf("%d` ", fileBuffer.getInt());
						break;
				}
			}
		}
	}

	public static class FloatArg extends MiniCommandArg
	{
		public FloatArg()
		{}

		@Override
		public void consume(RomPatcher rp)
		{
			rp.readInt();
		}

		@Override
		public void scan(Miniscript context, BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer)
		{
			fileBuffer.getFloat();
		}

		@Override
		public void print(Miniscript context, BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer, PrintWriter pw)
		{
			pw.print(fileBuffer.getFloat());
		}
	}

	public static class EnumArg extends MiniCommandArg
	{
		public final String namespace;

		public EnumArg(String namespace)
		{
			this.namespace = namespace;
		}

		@Override
		public void consume(RomPatcher rp)
		{
			rp.readInt();
		}

		@Override
		public void scan(Miniscript context, BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer)
		{
			fileBuffer.getInt();
		}

		@Override
		public void print(Miniscript context, BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer, PrintWriter pw)
		{
			ConstEnum enumType = ProjectDatabase.getFromNamespace(namespace);
			if (enumType == null)
				throw new StarRodException("Unknown enum: %s", namespace);
			decoder.printEnum(pw, enumType, fileBuffer.getInt());
		}
	}

	public static class PointerArg extends MiniCommandArg
	{
		public final StructType ptrType;

		public PointerArg(StructType ptrType)
		{
			this.ptrType = ptrType;
		}

		@Override
		public void consume(RomPatcher rp)
		{
			rp.readInt();
		}

		@Override
		public void scan(Miniscript context, BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer)
		{
			int addr = fileBuffer.getInt();
			if (addr != 0)
				decoder.tryEnqueueAsChild(ptr, addr, ptrType);
		}

		@Override
		public void print(Miniscript context, BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer, PrintWriter pw)
		{
			int addr = fileBuffer.getInt();
			if (ptrType.isTypeOf(FunctionT))
				decoder.printFunctionName(pw, addr);
			else
				decoder.printScriptWord(pw, addr);
		}
	}
}

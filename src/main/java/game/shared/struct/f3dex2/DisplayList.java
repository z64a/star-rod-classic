package game.shared.struct.f3dex2;

import static game.shared.StructTypes.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.Environment;
import app.Resource;
import app.Resource.ResourceType;
import app.StarRodException;
import app.input.DummySource;
import app.input.IOUtils;
import app.input.InputFileException;
import app.input.InvalidInputException;
import app.input.Line;
import app.input.Token;
import game.shared.BaseStruct;
import game.shared.DataUtils;
import game.shared.decoder.BaseDataDecoder;
import game.shared.decoder.Pointer;
import game.shared.struct.f3dex2.commands.BranchZ;
import game.shared.struct.f3dex2.commands.CullDL;
import game.shared.struct.f3dex2.commands.FillRect;
import game.shared.struct.f3dex2.commands.GeometryMode;
import game.shared.struct.f3dex2.commands.LoadBlock;
import game.shared.struct.f3dex2.commands.LoadTLUT;
import game.shared.struct.f3dex2.commands.LoadTile;
import game.shared.struct.f3dex2.commands.LoadUCode;
import game.shared.struct.f3dex2.commands.LoadVertex;
import game.shared.struct.f3dex2.commands.ModifyVertex;
import game.shared.struct.f3dex2.commands.MoveMem;
import game.shared.struct.f3dex2.commands.MoveWord;
import game.shared.struct.f3dex2.commands.NewDL;
import game.shared.struct.f3dex2.commands.PopMatrix;
import game.shared.struct.f3dex2.commands.Quad;
import game.shared.struct.f3dex2.commands.SetBuffer;
import game.shared.struct.f3dex2.commands.SetColor;
import game.shared.struct.f3dex2.commands.SetCombine;
import game.shared.struct.f3dex2.commands.SetConvert;
import game.shared.struct.f3dex2.commands.SetFillColor;
import game.shared.struct.f3dex2.commands.SetImg;
import game.shared.struct.f3dex2.commands.SetKeyGB;
import game.shared.struct.f3dex2.commands.SetKeyR;
import game.shared.struct.f3dex2.commands.SetOtherModeH;
import game.shared.struct.f3dex2.commands.SetOtherModeL;
import game.shared.struct.f3dex2.commands.SetPrimColor;
import game.shared.struct.f3dex2.commands.SetPrimDepth;
import game.shared.struct.f3dex2.commands.SetScissor;
import game.shared.struct.f3dex2.commands.SetTile;
import game.shared.struct.f3dex2.commands.SetTileSize;
import game.shared.struct.f3dex2.commands.TexRect;
import game.shared.struct.f3dex2.commands.Texture;
import game.shared.struct.f3dex2.commands.Tri1;
import game.shared.struct.f3dex2.commands.Tri2;
import game.shared.struct.f3dex2.commands.UseMatrix;
import game.texture.TileFormat;

public class DisplayList extends BaseStruct
{
	public static final DisplayList instance = new DisplayList();

	private DisplayList()
	{}

	public static void main(String[] args) throws IOException, InvalidInputException
	{
		Environment.initialize();
		ByteBuffer bb = Environment.getBaseRomBuffer();
		//print(null, bb, 0x161E70);
		//	print(bb, 0xC09578);
		//	print(bb, 0x52540);
		//	print(bb, 0x525E8);

		//	testLists(bb);

		printLine(0xe3000a11, 0x18ac30);
		printLine(0xE3000A11, 0x00082CF0);

		printCmd("G_SetOtherMode_H (G_MDSFT_TEXTFILT, G_TF_POINT)");

		Environment.exit();
	}

	public static CommandType getCommandForOpcode(int opcode)
	{
		return decodeMap.get(opcode);
	}

	public static void printCmd(String line) throws InvalidInputException
	{
		BaseF3DEX2 cmd = parse(line);
		for (int i : cmd.assemble())
			System.out.printf("%08X ", i);
		System.out.println();
	}

	public static void printLine(int gfx0, int gfx1) throws InvalidInputException
	{
		int opcode = (gfx0 >> 24) & 0xFF;
		CommandType type = decodeMap.get(opcode);
		if (type == null)
			throw new IllegalStateException(String.format("Invalid display command opcode: %X", opcode));
		if (type.size != 2)
			throw new IllegalStateException(String.format("Invalid size for display command %s: %d", type.name(), type.size));
		System.out.println(type.create(gfx0, gfx1).getString(null));
	}

	public static enum CommandType
	{
		// @formatter:off
		G_NOOP				(0x00, NoArg.class),
		G_VTX				(0x01, LoadVertex.class),
		G_MODIFYVTX			(0x02, ModifyVertex.class),
		G_CULLDL			(0x03, CullDL.class),
		G_BRANCH_Z			(0x04, 4, BranchZ.class),
		G_TRI1				(0x05, Tri1.class),
		G_TRI2				(0x06, Tri2.class),
		G_QUAD				(0x07, Quad.class),
		G_DMA_IO			(0xD6), // no binding
		G_TEXTURE			(0xD7, Texture.class),
		G_POPMTX			(0xD8, PopMatrix.class),
		G_GEOMETRYMODE		(0xD9, GeometryMode.class),
		G_MTX				(0xDA, UseMatrix.class),
		G_MOVEWORD			(0xDB, MoveWord.class),
		G_MOVEMEM			(0xDC, MoveMem.class),
		G_LOAD_UCODE		(0xDD, 4, LoadUCode.class),
		G_DL				(0xDE, NewDL.class),
		G_ENDDL				(0xDF, NoArg.class),
		G_NOOP_RDP			(0xE0, NoArg.class),
		G_RDPHALF_1			(0xE1), // should not be seen by user
		G_SetOtherMode_L	(0xE2, SetOtherModeL.class),
		G_SetOtherMode_H	(0xE3, SetOtherModeH.class),
		G_TEXRECT			(0xE4, 6, TexRect.class),
		G_TEXRECTFLIP		(0xE5, 6, TexRect.class),
		G_RDPLOADSYNC		(0xE6, NoArg.class),
		G_RDPPIPESYNC		(0xE7, NoArg.class),
		G_RDPTILESYNC		(0xE8, NoArg.class),
		G_RDPFULLSYNC		(0xE9, NoArg.class),
		G_SETKEYGB			(0xEA, SetKeyGB.class),
		G_SETKEYR			(0xEB, SetKeyR.class),
		G_SETCONVERT		(0xEC, SetConvert.class),
		G_SETSCISSOR		(0xED, SetScissor.class),
		G_SETPRIMDEPTH		(0xEE, SetPrimDepth.class),
		G_RDPSetOtherMode	(0xEF), // no binding
		G_LOADTLUT			(0xF0, LoadTLUT.class),
		G_RDPHALF_2			(0xF1), // should not be seen by user
		G_SETTILESIZE		(0xF2, SetTileSize.class),
		G_LOADBLOCK			(0xF3, LoadBlock.class),
		G_LOADTILE			(0xF4, LoadTile.class),
		G_SETTILE			(0xF5, SetTile.class),
		G_FILLRECT			(0xF6, FillRect.class),
		G_SETFILLCOLOR		(0xF7, SetFillColor.class),
		G_SETFOGCOLOR		(0xF8, SetColor.class),
		G_SETBLENDCOLOR		(0xF9, SetColor.class),
		G_SETPRIMCOLOR		(0xFA, SetPrimColor.class),
		G_SETENVCOLOR		(0xFB, SetColor.class),
		G_SETCOMBINE		(0xFC, SetCombine.class),
		G_SETIMG			(0xFD, SetImg.class),
		G_SETZIMG			(0xFE, SetBuffer.class),
		G_SETCIMG			(0xFF, SetBuffer.class);
		// @formatter:on

		public final int opcode;
		public final String opName;
		public final boolean noArgs;

		public final int size; // how many words does this command consist of after its been compiled?

		//	private final Constructor<? extends BaseF3DEX2> emptyConstructor;
		private final Constructor<? extends BaseF3DEX2> abConstructor;
		private final Constructor<? extends BaseF3DEX2> listConstructor;

		private CommandType(int opcode)
		{
			this(opcode, 2);
		}

		private CommandType(int opcode, int words)
		{
			this(opcode, words, BaseF3DEX2.class);
		}

		private CommandType(int opcode, Class<? extends BaseF3DEX2> command)
		{
			this(opcode, 2, command);
		}

		private CommandType(int opcode, int words, Class<? extends BaseF3DEX2> command)
		{
			this.opcode = opcode;
			this.opName = name();
			this.size = words;
			this.noArgs = (command == NoArg.class);

			try {
				//		emptyConstructor = command.getConstructor(CommandType.class);
				abConstructor = command.getConstructor(CommandType.class, Integer[].class); //Integer.TYPE, Integer.TYPE );
				listConstructor = command.getConstructor(CommandType.class, String[].class);
			}
			catch (Exception e) {
				throw new IllegalStateException(e.getClass() + " : " + e.getMessage());
			}
		}

		/*
		public BaseF3DEX2 create()
		{
			try {
				return emptyConstructor.newInstance(this);
			} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
				throw new IllegalStateException(e.getMessage());
			}
		}
		 */

		public BaseF3DEX2 create(Integer ... args) throws InvalidInputException
		{
			try {
				return abConstructor.newInstance(this, args);
			}
			catch (InstantiationException | IllegalAccessException | IllegalArgumentException e) {
				// programming error
				IllegalStateException illegalState = new IllegalStateException(e.getMessage());
				illegalState.setStackTrace(e.getStackTrace());
				throw illegalState;
			}
			catch (InvocationTargetException e) {
				// user error
				Throwable cause = e.getCause();
				if (cause.getClass().isAssignableFrom(InvalidInputException.class))
					throw new InvalidInputException(cause);
				// programming error
				IllegalStateException illegalState = new IllegalStateException(cause.getMessage());
				illegalState.setStackTrace(cause.getStackTrace());
				throw illegalState;
			}
		}

		public BaseF3DEX2 create(String ... list) throws InvalidInputException
		{
			try {
				return listConstructor.newInstance(this, list);
			}
			catch (InstantiationException | IllegalAccessException | IllegalArgumentException e) {
				// programming error
				IllegalStateException illegalState = new IllegalStateException(e.getMessage());
				illegalState.setStackTrace(e.getStackTrace());
				throw illegalState;
			}
			catch (InvocationTargetException e) {
				// user error
				Throwable cause = e.getCause();
				if (cause.getClass().isAssignableFrom(InvalidInputException.class))
					throw new InvalidInputException(cause);
				// programming error
				IllegalStateException illegalState = new IllegalStateException(cause.getMessage());
				illegalState.setStackTrace(cause.getStackTrace());
				throw illegalState;
			}
		}
	}

	private static HashMap<Integer, CommandType> decodeMap = new HashMap<>();
	private static HashMap<String, CommandType> encodeMap = new HashMap<>();
	static {
		decodeMap = new HashMap<>();
		encodeMap = new HashMap<>();
		for (CommandType cmd : CommandType.values()) {
			decodeMap.put(cmd.opcode, cmd);
			encodeMap.put(cmd.opName.toUpperCase(), cmd);
		}
	}

	public static final Pattern NoArgPattern = Pattern.compile("([\\w_]+)\\s*(?:\\(\\s*\\)|\\[\\s*\\])?");
	public static final Pattern RoughPattern = Pattern.compile("([\\w_]+)\\s*\\[([0-9A-Fa-f]+),\\s*([0-9A-Fa-f]+)\\]");
	public static final Pattern FancyPattern = Pattern.compile("([\\w_]+)\\s*\\((.+)\\)");

	public static BaseF3DEX2 parse(String s) throws InvalidInputException
	{
		Matcher m = NoArgPattern.matcher(s);
		if (m.matches()) {
			CommandType cmd = encodeMap.get(m.group(1).toUpperCase());
			if (cmd == null)
				throw new InvalidInputException("Unknown F3DEX2 command: " + m.group(1));
			if (!cmd.noArgs)
				throw new InvalidInputException(m.group(1) + " is missing a parameter list.");

			return cmd.create(0, 0);
		}

		m = RoughPattern.matcher(s);
		if (m.matches()) {
			CommandType cmd = encodeMap.get(m.group(1).toUpperCase());
			if (cmd == null)
				throw new InvalidInputException("Unknown F3DEX2 command: " + m.group(1));
			if (cmd.noArgs)
				throw new InvalidInputException(m.group(1) + " should not have parameters!");

			int A = (int) Long.parseLong(m.group(2), 16);
			int B = (int) Long.parseLong(m.group(3), 16);
			return cmd.create(A, B);
		}

		m = FancyPattern.matcher(s);
		if (m.matches()) {
			CommandType cmd = encodeMap.get(m.group(1).toUpperCase());
			if (cmd == null)
				throw new InvalidInputException("Unknown F3DEX2 command: " + m.group(1));
			if (cmd.noArgs)
				throw new InvalidInputException(m.group(1) + " should not have parameters!");

			String[] args = m.group(2).trim().split("\\s*,\\s*");
			for (String t : args) {
				if (t.isEmpty())
					throw new InvalidInputException("Empty argument for command: " + m.group(1));
			}
			return cmd.create(args);
		}

		throw new InvalidInputException("Could not parse command: " + s); //TODO((s.length() < 50) ? s : (s.substring(0,50) + " ...")));
	}

	// we ASSUME each display list can (potentially) set up a single texture
	@Override
	public void scan(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer)
	{
		List<BaseF3DEX2> commands = readList(fileBuffer, fileBuffer.position());

		TileFormat fmt = null;
		int ptrPrevImage = 0;
		int ptrLastImage = 0;
		int ptrPal = 0;
		int width = 0;
		int height = 0;

		// setimg sets last img pointer

		for (BaseF3DEX2 cmd : commands) {
			switch (cmd.type) {
				case G_DL:
					NewDL newDL = (NewDL) cmd;
					decoder.tryEnqueueAsChild(ptr, newDL.addr, DisplayListT);
					break;

				case G_MTX:
					UseMatrix useMtx = (UseMatrix) cmd;
					decoder.tryEnqueueAsChild(ptr, useMtx.addr, DisplayMatrixT);
					break;

				case G_VTX:
					LoadVertex loadVertex = (LoadVertex) cmd;
					Pointer child = decoder.tryEnqueueAsChild(ptr, loadVertex.addr, VertexTableT);
					if (child != null)
						child.listLength = loadVertex.num;
					break;

				case G_SETIMG:
					SetImg setImage = (SetImg) cmd;
					ptrPrevImage = ptrLastImage;
					ptrLastImage = setImage.addr;
					break;

				case G_LOADTLUT:
					assert (ptrLastImage != 0);
					ptrPal = ptrLastImage;
					if (fmt != null)
						decoder.addImage(ptr, fileBuffer, fmt, ptrPrevImage, ptrLastImage, width, height);
					break;

				case G_SETTILE:
					SetTile setTile = (SetTile) cmd;
					if (setTile.descriptor == 0) // G_TX_RENDERTILE
						fmt = setTile.fmt;
					break;

				case G_SETTILESIZE:
					SetTileSize setSize = (SetTileSize) cmd;
					//	assert(setSize.startS == 0);
					//	assert(setSize.startT == 0);
					width = setSize.W;
					height = setSize.H;
					if (fmt != null)
						decoder.addImage(ptr, fileBuffer, fmt, ptrLastImage, ptrPal, width, height);
				default:
			}
		}
	}

	// re-orders E1/F1 to always be after the relevant opcode
	public static List<BaseF3DEX2> readList(ByteBuffer buf, int offset)
	{
		buf.position(offset);

		List<BaseF3DEX2> commandList = new ArrayList<>();
		CommandType type = null;

		int[] args = new int[6];

		do {
			int readPos = buf.position();
			int A = buf.getInt();
			int B = buf.getInt();
			int opcode = (A >> 24) & 0xFF;

			switch (opcode) {
				case 0xE4:
				case 0xE5:
					args[0] = A;
					args[1] = B;
					args[2] = buf.getInt();
					args[3] = buf.getInt();
					args[4] = buf.getInt();
					args[5] = buf.getInt();
					break;

				case 0xE1:
					args[0] = buf.getInt();
					args[1] = buf.getInt();
					args[2] = A;
					args[3] = B;

					opcode = (args[0] >> 24) & 0xFF;
					if (opcode != 0x04 && opcode != 0xDD)
						throw new StarRodException("Unexpected display command at %X: %08X", buf.position() - 8, args[0]);
					break;

				default:
					args[0] = A;
					args[1] = B;
					break;
			}

			type = decodeMap.get(opcode);
			BaseF3DEX2 cmd = null;
			try {
				switch (type.size) {
					case 2:
						cmd = type.create(args[0], args[1]);
						break;
					case 4:
						cmd = type.create(args[0], args[1], args[2], args[3]);
						break;
					case 6:
						cmd = type.create(args[0], args[1], args[2], args[3], args[4], args[5]);
						break;
					default:
						throw new IllegalStateException(String.format("Invalid size for display command %s: %d", type.name(), type.size));
				}
			}
			catch (InvalidInputException e) {
				StarRodException sre = new StarRodException("Invalid display list at %X: %n%s", readPos, e.getMessage());
				sre.setStackTrace(e.getStackTrace());
				throw sre;
			}
			commandList.add(cmd);
		}
		while (type != CommandType.G_ENDDL);

		return commandList;
	}

	@Override
	public void print(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer, PrintWriter pw)
	{
		CommandType type = null;
		do {
			int A = fileBuffer.getInt();
			int B = fileBuffer.getInt();
			int opcode = (A >> 24) & 0xFF;
			type = decodeMap.get(opcode);

			BaseF3DEX2 cmd = null;
			try {
				cmd = type.create(A, B);
			}
			catch (InvalidInputException e) {
				StarRodException sre = new StarRodException(
					String.format("%08X from %s:%n ", ptr.address, decoder.getSourceName()) + e.getMessage());
				sre.setStackTrace(e.getStackTrace());
				throw new StarRodException(e);
			}

			pw.println(cmd.getString(decoder));
		}
		while (type != CommandType.G_ENDDL);
	}

	public static int getPatchLength(List<Line> lines)
	{
		int length = 0;
		for (Line line : lines) {
			String[] tokens = line.str.split("[\\s\\(\\)\\[\\]]+");
			CommandType type = encodeMap.get(tokens[0].toUpperCase());
			if (type == null)
				throw new InputFileException(line, "Unknown F3DEX2 command: " + tokens[0]);
			length += type.size;
		}
		return length * 4;
	}

	public static void encode(List<Line> lines)
	{
		List<Line> newLines = new ArrayList<>((int) Math.ceil(lines.size() * 1.2f));

		for (Line line : lines) {
			try {
				line.gather();
				BaseF3DEX2 cmd = parse(line.str);
				int[] words = cmd.assemble();
				String[] tokens = new String[words.length];
				for (int i = 0; i < words.length; i++)
					tokens[i] = String.format("%08X", words[i]);
				newLines.add(line.createLine(tokens));
			}
			catch (InvalidInputException e) {
				throw new InputFileException(line, e);
			}
		}

		lines.clear();
		lines.addAll(newLines);
	}

	private static void testLists(ByteBuffer fileBuffer) throws IOException, InvalidInputException
	{
		InputStream is = Resource.getStream(ResourceType.Basic, "DisplayListOffsets.txt");
		List<String> entries = IOUtils.readFormattedTextStream(is, false);

		int i = 0;
		for (String entry : entries) {
			String[] tokens = entry.split("\\s+");
			int offset = (int) Long.parseLong(tokens[0], 16);

			DummySource src = new DummySource(String.format("ROM-%06X", offset));

			List<BaseF3DEX2> displayList = readList(fileBuffer, offset);
			List<Line> lines = new ArrayList<>(displayList.size());

			int j = 1;
			for (BaseF3DEX2 cmd : displayList) {
				// clean to remove comments, reduce extra whitespace, and join split lines
				String s = cmd.getString(null)
					.replace("\r", "")
					.replace("...\n", "")
					.replaceAll("\\s+", " ")
					.replaceAll("\\s+%.+$", "");
				Line newLine = new Line(src, j++, s);
				lines.add(newLine);
				newLine.tokenize();
			}

			int len = DisplayList.getPatchLength(lines);

			ByteBuffer bb = ByteBuffer.allocateDirect(len);

			System.out.printf("%d : %X%n", ++i, offset);
			for (Line line : lines)
				System.out.println(line.str);
			System.out.println();
			System.out.flush();

			DisplayList.encode(lines);

			for (Line line : lines) {
				line.tokenize();
				for (Token t : line.tokens)
					bb.putInt(DataUtils.parseIntString(t.str));
			}

			bb.flip();
			fileBuffer.position(offset);
			for (int k = 0; k < len; k += 4) {
				int original = fileBuffer.getInt();
				int cycled = bb.getInt();
				if (cycled != original)
					throw new InvalidInputException("%07X: %08X --> %08X", fileBuffer.position() - 4, original, cycled);
			}
		}
	}

	private static void printLists(ByteBuffer fileBuffer) throws IOException
	{
		InputStream is = Resource.getStream(ResourceType.Basic, "DisplayListOffsets.txt");
		List<String> lines = IOUtils.readFormattedTextStream(is, false);

		int i = 0;
		for (String line : lines) {
			String[] tokens = line.split("\\s+");
			int offset = (int) Long.parseLong(tokens[0], 16);

			System.out.printf("%d : %X%n", ++i, offset);
			print(fileBuffer, offset);
			System.out.println();
			System.out.flush();
		}
	}

	private static void print(ByteBuffer buf, int offset)
	{
		buf.position(offset);
		CommandType type = null;
		do {
			int A = buf.getInt();
			int B = buf.getInt();
			int opcode = (A >> 24) & 0xFF;
			type = decodeMap.get(opcode);

			BaseF3DEX2 cmd = null;
			try {
				cmd = type.create(A, B);
			}
			catch (InvalidInputException e) {
				e.printStackTrace();
			}
			System.out.println(cmd.getString(null));
		}
		while (type != CommandType.G_ENDDL);
	}

	/*
	private static void findLists(ByteBuffer fileBuffer)
	{
		fileBuffer.position(fileBuffer.capacity() - 4);

		int pos = fileBuffer.capacity() - 8;
		int count = 0;

		ArrayList<Integer> starts = new ArrayList<>(2000);
		ArrayList<Integer> ends = new ArrayList<>(2000);
		boolean reading = false;

		while(pos > 0)
		{
			fileBuffer.position(pos);
			long h = fileBuffer.getLong();

			if(!reading)
			{
				if(h == 0xDF00000000000000L)
				{
					//System.out.printf("%5d : %08X%n",  ++count, pos);
					ends.add(pos + 8);
					reading = true;
				}
			}
			else
			{
				if(h == 0xDF00000000000000L)
				{
					starts.add(pos + 8);
					ends.add(pos + 8);
					//System.out.printf("%5d : %08X%n",  ++count, pos);
				}

				boolean valid = true;
				int opcode  = (int)(h >> 56) & 0xFF;
				CommandType type = decodeMap.get(opcode);

				if(type == null)
					valid = false;

				switch(opcode)
				{
				case 0x00:
				case 0xFF:
					valid = false;
					break;

				case 0x05:
					valid = ((h & 0xFFFFFFFFL) == 0);
					break;
				}

				if(!valid)
				{
					starts.add(pos + 8);
					reading = false;
				}
			}

			pos -= 8;
		}

		if(starts.size() != ends.size())
			throw new IllegalStateException();

		Collections.reverse(starts);
		Collections.reverse(ends);

		int i = 0;
		for(Integer start : starts)
		{
			System.out.printf("%07X%n", start);
		//	System.out.printf("%d : %X%n", ++i, start);
		//	print(fileBuffer, start);
		//	System.out.println();
		}
	}
	 */
}

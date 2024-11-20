package game.shared.encoder;

import static app.Directories.MOD_RESOURCE;
import static game.shared.StructTypes.*;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.apache.commons.io.FileUtils;

import app.Directories;
import app.Environment;
import app.StarRodException;
import app.config.Config;
import app.config.Options;
import app.input.AbstractSource;
import app.input.DummySource;
import app.input.IOUtils;
import app.input.InputFileException;
import app.input.InvalidInputException;
import app.input.Line;
import app.input.PatchFileParser;
import app.input.PatchFileParser.PatchUnit;
import app.input.StreamSource;
import app.input.Token;
import asm.MIPS;
import asm.pseudoinstruction.PseudoInstruction;
import game.ROM;
import game.ROM.LibScope;
import game.map.MapIndex;
import game.map.marker.Marker;
import game.shared.DataUtils;
import game.shared.ProjectDatabase;
import game.shared.ProjectDatabase.ConstEnum;
import game.shared.StructField;
import game.shared.SyntaxConstants;
import game.shared.lib.LibEntry;
import game.shared.struct.Struct;
import game.shared.struct.StructType;
import game.shared.struct.TypeMap;
import game.shared.struct.f3dex2.DisplayList;
import game.shared.struct.function.Function;
import game.shared.struct.script.Script;
import game.shared.struct.script.ScriptVariable;
import game.shared.struct.script.inline.ConstantDatabase;
import game.shared.struct.script.inline.InlineCompiler;
import game.texture.Tile;
import game.texture.TileFormat;
import game.texture.images.HudElementRecord;
import game.texture.images.ImageDatabase.EncodedImageAsset;
import patcher.IGlobalDatabase;
import patcher.Region;
import patcher.RomPatcher;
import patcher.SubscriptionManager;
import util.CaseInsensitiveMap;
import util.Logger;

public abstract class BaseDataEncoder implements ConstantDatabase
{
	static {
		FunctionT.patchLength = (en, pt) -> {
			return Function.getPatchLength(pt.lines);
		};
		DisplayListT.patchLength = (en, pt) -> {
			return DisplayList.getPatchLength(pt.lines);
		};

		ScriptT.replaceConstants = (en, pt) -> {
			Script.encode(en, ProjectDatabase.rom.getLibrary(pt.struct.scope), pt.lines);

			boolean shouldPack = Environment.project.config.getBoolean(Options.PackScriptOpcodes);
			boolean canPack = pt.struct != null && (!pt.struct.isDumped() || pt.struct.replaceExisting);
			if (shouldPack && canPack) {
				if (pt.struct.offsetMorphMap == null)
					pt.struct.offsetMorphMap = new TreeMap<>();
				Script.packOpcodeLength(pt.lines, pt.struct.offsetMorphMap);
			}
		};
	}

	private static final char NAMESPACE_SEPERATOR = ':';

	public final boolean overlayMode;

	private AbstractSource primarySource;

	private final IGlobalDatabase globalsDatabase;
	protected ByteBuffer fileBuffer;
	private String currentNamespace = "";

	private final TypeMap typeMap;
	private final Directories importDirectory;

	private LibScope defaultScope;

	// Struct pointer symbols (ex %SomeStruct) must eventually be converted to pointers.
	// The structMap associates names with data structures. All named data structures are
	// in the struct map, even those that are declared or deleted.
	private final HashMap<String, Struct> declaredStructs;
	private final HashMap<String, Struct> virtualStructsMap;

	private final HashMap<String, Struct> stringLiteralMap;
	private final HashMap<String, Line> stringLiteralOriginMap;

	// Organize original structs into the address space they occupy.
	private final TreeMap<Integer, Struct> originalOffsetTree;

	private Struct start = null;
	private Struct end = null;
	private int baseAddress = -1;
	private int addressLimit = -1;

	private final List<Struct> patchedStructures;
	private final HashMap<String, String> constantMap;

	private final List<Region> reservedRegions;

	private final List<Region> deletedRegions;
	private final List<Region> paddingRegions;
	private final List<Region> missingRegions;

	private static final String ValidPointerName = "\\$[\\w?-]+";
	private static final String ValidConstantName = "\\.[\\w?:-]+";

	private static final Pattern ValidPointerPattern = Pattern.compile(ValidPointerName);
	private static final Pattern ValidConstPattern = Pattern.compile(ValidConstantName);

	// pre-compile common regex expressions
	private static final Pattern WhitespacePattern = Pattern.compile("\\s+");
	private static final Pattern LineOffsetPattern = Pattern.compile("^\\s*[0-9A-Fa-f]+:\\s*");

	private static final Pattern PatchOffsetPattern = Pattern.compile("\\s*\\[([^\\[\\]\\s]+)\\]\\s*(.+)?");

	// matches+captures name[offset]
	private static final Pattern ExpressionNameOffsetPatten = Pattern.compile("~([^\\[\\s]+)(?:\\[(\\S+)\\])?"); // offset group is optional
	private static final Pattern ScriptVarNameOffsetPatten = Pattern.compile("(\\*\\w+?)\\[(\\S+)\\]");
	private static final Pattern PointerNameOffsetPatten = Pattern.compile("(" + ValidPointerName + ")\\[(\\S+)\\]");
	private static final Pattern ConstNameOffsetPatten = Pattern.compile("(" + ValidConstantName + ")\\[(\\S+)\\]");

	private static final Pattern CastConstExpressionPatten = Pattern.compile("(?:byte|short):(" + ValidConstantName + ")", Pattern.CASE_INSENSITIVE);

	private static final Pattern FunctionIgnorePattern = Pattern.compile("[(),]");
	private static final Pattern ScriptInlineExpPattern = Pattern.compile("(?:\\h*\\[[0-9A-F]+\\]\\h)?\\h*SETF?\\h+\\S+?\\h+=.*");
	private static final Pattern ScriptIgnorePattern = Pattern.compile("[()]");

	protected BaseDataEncoder(TypeMap typeMap, LibScope scope, IGlobalDatabase db, Directories importDirectory, boolean overlayMode)
	{
		this.typeMap = typeMap;
		this.globalsDatabase = db;
		this.importDirectory = importDirectory;
		this.overlayMode = overlayMode;
		this.defaultScope = scope;

		declaredStructs = new HashMap<>();
		virtualStructsMap = new HashMap<>();
		patchedStructures = new ArrayList<>();
		originalOffsetTree = new TreeMap<>();

		constantMap = new HashMap<>();

		deletedRegions = new LinkedList<>();
		paddingRegions = new LinkedList<>();
		missingRegions = new LinkedList<>();
		reservedRegions = new LinkedList<>();

		stringLiteralMap = new HashMap<>();
		stringLiteralOriginMap = new HashMap<>();

		importDirectory = null;
	}

	protected final void setSource(AbstractSource source)
	{
		this.primarySource = source;
	}

	private String appendNamespace(String name)
	{
		if (currentNamespace.isEmpty())
			return name;
		else
			return name.substring(0, 1) + currentNamespace + NAMESPACE_SEPERATOR + name.substring(1);
	}

	/**
	 * Creates a new Struct declared (explictly or implicitly) in the patch file
	 * @return new Struct or null if type was invalid
	 */
	private final Struct declareStruct(String typeName, LibScope scope, String name)
	{
		StructType type = typeMap.get(typeName);
		if (type == null)
			return null;
		else
			return Struct.createNew(type, scope, appendNamespace(name), currentNamespace);
	}

	/**
	 * Creates a Struct to represent an entry from an index file
	 * @return new Struct or null if type was invalid
	 */
	private final Struct makeStruct(String typeName, String name, int address, int offset, int size)
	{
		StructType type = typeMap.get(typeName);
		if (type == null)
			return null;

		return Struct.createOverlay(type, defaultScope, appendNamespace(name), currentNamespace, address, offset, size);
	}

	private final int toOverlayOffset(int address)
	{
		assert (overlayMode);
		assert (baseAddress != 0);

		long mask32 = 0xFFFFFFFFL;
		return (int) ((address & mask32) - (baseAddress & mask32));
	}

	private final int toOverlayAddress(int offset)
	{
		assert (overlayMode);
		assert (baseAddress != 0);
		return offset + baseAddress;
	}

	protected final void setAddressLimit(int limit)
	{
		addressLimit = limit;
	}

	private boolean digested = false;
	private boolean built = false;

	protected final void digest()
	{
		if (digested)
			throw new InputFileException(primarySource, "Encoder for has already invoked digest!");
		digested = true;

		Config cfg = Environment.project.config;

		if (cfg.getBoolean(Options.ClearJapaneseStrings))
			removeJapaneseStrings(declaredStructs.values());

		// clean up input
		validateStructs();
		replaceAllSymbols();
		createStringLiteralStructs();

		// determine sizes and positioning
		calculatePatchLengths();
		calculatePatchPositions();
		calculateStructLengths();

		// determine where each patched data structure will be placed in memory
		if (overlayMode)
			determineLocalPlacement();
		else
			determineGlobalPlacement();
	}

	protected final void buildOverlay(File outFile, File outIndexFile) throws IOException
	{
		assert (overlayMode);
		if (!digested)
			throw new InputFileException(primarySource, "Encoder for tried to build without digest!");
		if (built)
			throw new InputFileException(primarySource, "Encoder for has already been built!");
		built = true;

		// prepare for patching
		findFunctionLabels(patchedStructures);
		replacePointerNames(patchedStructures);
		assembleFunctions(patchedStructures);
		assembleDisplayLists(patchedStructures);

		// write the patches
		ByteBuffer patchedBuffer = prepareBuffer();
		fixPointersInFunctions(patchedBuffer);
		fixPointers(patchedBuffer);

		applyPatches(patchedBuffer);

		Config cfg = Environment.project.config;
		if (cfg.getBoolean(Options.CheckScriptSyntax))
			checkPatchedScriptSyntax(patchedBuffer, cfg.getBoolean(Options.PackScriptOpcodes));

		writeBinaryOutput(patchedBuffer, outFile);
		printIndexFile(outIndexFile);
	}

	protected final void buildGlobals()
	{
		// prepare for patching
		findFunctionLabels(patchedStructures);
		replacePointerNames(patchedStructures);
		assembleFunctions(patchedStructures);
		assembleDisplayLists(patchedStructures);
	}

	protected final void writeROMPatches(RomPatcher rp) throws IOException
	{
		assert (!overlayMode);

		for (Struct str : patchedStructures) {
			if (str.fillMode) {
				str.patchedBuffer = ByteBuffer.allocateDirect(str.finalSize);
				applyFillPattern(str);

				rp.seek(str.name, str.finalFileOffset);
				rp.write(str.patchedBuffer);
				continue;
			}

			switch (str.gens) {
				case Fixed:
					for (Patch patch : str.patchList) {
						int romOffset = str.finalFileOffset + str.basePatchOffset + patch.startingPos;
						Logger.logf("Writing direct patch to %08X", romOffset);
						rp.seek(str.name, romOffset);

						for (Line line : patch.lines)
							for (Token t : line.tokens) {
								try {
									DataUtils.writeWord(rp, t.str);
								}
								catch (InvalidInputException e) {
									throw new InputFileException(t, "Invalid token when writing data: " + t.str);
								}
							}
					}
					break;
				case Hook:
				case New:
					// done in second pass
					break;
				case Overlay:
					throw new InputFileException(primarySource, "Source contains an invalid overlay struct: " + str.name);
			}
		}
	}

	protected final void addNewStructs(RomPatcher rp) throws IOException
	{
		assert (!overlayMode);

		for (Struct str : declaredStructs.values()) {
			switch (str.gens) {
				case Fixed:
					// already done in first pass
					break;
				case Hook:
					Logger.logf("Writing hook for %X to %08X", str.originalFileOffset, str.finalAddress);

					rp.seek(str.name + " (hook)", str.originalFileOffset);
					rp.writeInt(MIPS.getJumpIns(str.finalAddress));
					rp.writeInt(0); // NOP delay slot

				case New: // INTENTIONAL FALLTHROUGH FOR HOOK BODY
					for (Patch patch : str.patchList) {
						rp.seek(str.name, str.finalFileOffset + str.basePatchOffset + patch.startingPos);

						for (Line line : patch.lines)
							for (Token t : line.tokens) {
								try {
									DataUtils.writeWord(rp, t.str);
								}
								catch (InvalidInputException e) {
									throw new InputFileException(t, "Invalid token when writing data: " + t.str);
								}
							}
					}
					break;

				case Overlay:
					throw new InputFileException(primarySource, "Patch contains an invalid overlay struct: " + str.name);
			}
		}
	}

	public final void loadIndexFile(HashMap<String, Struct> nameMap, File f) throws IOException
	{
		try (BufferedReader in = new BufferedReader(new FileReader(f))) {
			String line;

			boolean foundStart = false;
			boolean foundEnd = false;

			while ((line = in.readLine()) != null) {
				line = WhitespacePattern.matcher(line).replaceAll("");
				if (line.isEmpty())
					continue;

				String[] tokens = line.split(SyntaxConstants.INDEX_FILE_SEPARATOR);

				if (line.startsWith("Padding"))
					continue;

				if (line.startsWith("Missing"))
					continue;

				String name = tokens[0];
				String type = tokens[1];
				int offset = (int) Long.parseLong(tokens[2], 16);
				int address = (int) Long.parseLong(tokens[3], 16);
				int size = (int) Long.parseLong(tokens[4], 16);

				Struct struct = makeStruct(type, name, address, offset, size);
				if (struct == null)
					throw new InputFileException(f, "Invalid declaration, no such type: %s", type);

				nameMap.put(struct.name, struct);

				if (struct.name.equals("$Start")) {
					if (foundStart)
						throw new InputFileException(f, "Found duplicate $Start in index file!");

					baseAddress = address;
					foundStart = true;
				}

				if (struct.name.equals("$End")) {
					if (foundEnd)
						throw new InputFileException(f, "Found duplicate $End in index file!");
					foundEnd = true;
				}
			}

			if (!foundStart)
				throw new InputFileException(f, "Could not read $Start from index file!");

			if (!foundEnd)
				throw new InputFileException(f, "Could not read $End from index file!");
		}
		catch (Exception e) {
			throw new InputFileException(f, e.getMessage());
		}
	}

	protected final void setOverlayMemoryLocation(int startAddress, int sizeLimit) throws IOException
	{
		assert (declaredStructs.isEmpty());
		assert (originalOffsetTree.isEmpty());

		if (sizeLimit <= 0)
			throw new StarRodException("Tried to create invalid overlay: %X bytes at %08X", sizeLimit, startAddress);

		baseAddress = startAddress;
		addressLimit = baseAddress + sizeLimit;

		start = Struct.createOverlay(UnknownT, defaultScope, "$Start", "", startAddress, 0, 0);
		end = Struct.createOverlay(UnknownT, defaultScope, "$End", "", startAddress, 0, sizeLimit);
		declaredStructs.put("$Start", start);
		declaredStructs.put("$End", end);
	}

	protected final void readIndexFile(File f) throws IOException
	{
		assert (declaredStructs.isEmpty());
		assert (originalOffsetTree.isEmpty());
		//	assert(addressLimit != -1);

		try (BufferedReader in = new BufferedReader(new FileReader(f))) {
			String line;

			while ((line = in.readLine()) != null) {
				line = line.trim();
				if (line.isEmpty())
					continue;

				String[] tokens = line.split(SyntaxConstants.INDEX_FILE_SEPARATOR);

				if (line.startsWith("Padding")) {
					int start = (int) Long.parseLong(tokens[1], 16);
					int end = (int) Long.parseLong(tokens[2], 16);
					paddingRegions.add(new Region(start, end));
					originalOffsetTree.put(start, null);
					continue;
				}

				if (line.startsWith("Missing")) {
					int start = (int) Long.parseLong(tokens[1], 16);
					int end = (int) Long.parseLong(tokens[2], 16);
					missingRegions.add(new Region(start, end));
					originalOffsetTree.put(start, null);
					continue;
				}

				String name = tokens[0];
				String type = tokens[1];
				int offset = (int) Long.parseLong(tokens[2], 16);
				int address = (int) Long.parseLong(tokens[3], 16);
				int size = (int) Long.parseLong(tokens[4], 16);

				// no namespace for index files, these are root
				Struct struct = makeStruct(type, name, address, offset, size);
				if (struct == null)
					throw new InputFileException(f, "Invalid declaration, no such type: %s", type);

				declaredStructs.put(struct.name, struct);
				originalOffsetTree.put(offset, struct);

				if (struct.name.equals("$Start")) {
					if (start != null)
						throw new InputFileException(f, "Found duplicate $Start in index file!");
					start = struct;
					baseAddress = address;
				}

				if (struct.name.equals("$End")) {
					if (end != null)
						throw new InputFileException(f, "Found duplicate $End in index file!");
					end = struct;
				}
			}

			if (start == null)
				throw new InputFileException(f, "Could not read $Start from index file!");

			if (end == null)
				throw new InputFileException(f, "Could not read $End from index file!");
		}
	}

	private static final Comparator<Struct> FINAL_LOCATION_COMPARATOR = (a, b) -> a.finalFileOffset - b.finalFileOffset;

	private final void printIndexFile(File f) throws IOException
	{
		PrintWriter pw = IOUtils.getBufferedPrintWriter(f);

		List<Struct> structList = new ArrayList<>(declaredStructs.values());
		Collections.sort(structList, FINAL_LOCATION_COMPARATOR);

		for (Struct str : structList) {
			if (str.deleted)
				continue;

			pw.printf("%s%s%s%s%X%s%X%s%X", str.name,
				SyntaxConstants.INDEX_FILE_SEPARATOR, str.getTypeName(),
				SyntaxConstants.INDEX_FILE_SEPARATOR, str.finalFileOffset,
				SyntaxConstants.INDEX_FILE_SEPARATOR, str.finalAddress,
				SyntaxConstants.INDEX_FILE_SEPARATOR, str.finalSize);

			pw.println();
		}

		for (Region padding : paddingRegions)
			pw.printf("Padding%s%X%s%X\n", SyntaxConstants.INDEX_FILE_SEPARATOR, padding.start, SyntaxConstants.INDEX_FILE_SEPARATOR, padding.end);

		for (Region missing : missingRegions)
			pw.printf("Missing%s%X%s%X\n", SyntaxConstants.INDEX_FILE_SEPARATOR, missing.start, SyntaxConstants.INDEX_FILE_SEPARATOR, missing.end);

		pw.close();
	}

	protected final void readPatchStream(InputStream in, String name) throws IOException
	{
		List<Line> lines = IOUtils.readPlainInputStream(new StreamSource(name), in);
		readPatchSource(lines, new CaseInsensitiveMap<>());
	}

	protected final void readPatchStream(InputStream in, String name, CaseInsensitiveMap<String> rules) throws IOException
	{
		List<Line> lines = IOUtils.readPlainInputStream(new StreamSource(name), in);
		readPatchSource(lines, rules);
	}

	protected final void readPatchFile(File f) throws IOException
	{
		readPatchFile(f, new CaseInsensitiveMap<>());
	}

	private final void readPatchFile(File f, CaseInsensitiveMap<String> rules) throws IOException
	{
		Logger.log("Reading patch file: " + IOUtils.getRelativePath(Environment.project.getDirectory(), f));

		List<Line> lines = IOUtils.readPlainInputFile(f);
		readPatchSource(lines, new CaseInsensitiveMap<>());
	}

	private final void readPatchSource(List<Line> lines, CaseInsensitiveMap<String> rules) throws IOException
	{
		List<PatchUnit> units = PatchFileParser.parse(lines, rules);

		for (PatchUnit unit : units) {
			String declareLine = unit.declaration.str.toLowerCase();
			if (declareLine.startsWith("@"))
				continue; // handled in third pass

			if (declareLine.startsWith("#")) {
				if (DefinePattern.matcher(declareLine).matches())
					parseConstant(unit);
				else if (declareLine.matches("#alias\\s+.+"))
					parseAlias(unit);
				else if (declareLine.matches("#import\\s+.+"))
					; // handled in second pass
				else if (declareLine.matches("#delete\\s+.+"))
					parseDelete(unit);
				else if (declareLine.matches("#reserve\\s+.+"))
					parseReserve(unit);
				else if (declareLine.startsWith("#new:") || declareLine.startsWith("#export:"))
					; // handled in third pass
				else if (declareLine.matches("#string\\s+.+") || declareLine.matches("#message\\s+.+"))
					; // handled in third pass
				else if (declareLine.matches("#export\\s+\\$\\S+"))
					; // handled in third pass
				else
					throw new InputFileException(unit.declaration, "Could not parse directive: %n%s", declareLine);
			}
			else
				throw new InputFileException(unit.declaration, "Expected directive or patch: %n%s", declareLine);
		}

		for (PatchUnit unit : units) {
			if (unit.parsed)
				continue;
			String declareLine = unit.declaration.str.toLowerCase();
			if (declareLine.matches("#import\\s+.+"))
				parseImport(unit);
		}

		for (PatchUnit unit : units) {
			if (unit.parsed)
				continue;
			String declareLine = unit.declaration.str.toLowerCase();
			if (declareLine.startsWith("@fill"))
				parseFill(unit);
			else if (declareLine.startsWith("@hook"))
				parseHook(unit);
			else if (declareLine.startsWith("@subscribe"))
				parseSubscribe(unit);
			else if (declareLine.startsWith("@"))
				parsePatch(unit);
			else if (StringPattern.matcher(declareLine).matches())
				parseString(unit);
			else if (declareLine.startsWith("#new:") || declareLine.startsWith("#export:"))
				parseNew(unit);
		}

		for (PatchUnit unit : units) {
			if (unit.parsed)
				continue;
			String declareLine = unit.declaration.str.toLowerCase();
			if (declareLine.matches("#export\\s+\\$\\S+"))
				parsePointerExport(unit);
		}

		for (Struct struct : patchedStructures) {
			cleanup(struct);
		}
	}

	private final void cleanup(Struct struct)
	{
		// join lines ending in "..."
		for (Patch patch : struct.patchList) {
			boolean joining = false;
			Line joinLine = null;

			Iterator<Line> iter = patch.lines.iterator();
			while (iter.hasNext()) {
				Line line = iter.next();

				if (line.str.endsWith("...")) {
					// remove ...
					line.str = line.str.substring(0, line.str.length() - 3);

					// continue join
					if (joining) {
						joinLine.str = joinLine.str + " " + line.str;
						iter.remove();
					}
					else {
						joinLine = line;
						joining = true;
					}

					if (!iter.hasNext())
						throw new InputFileException(line, "Patch does not have a following line for continuation.");
				}
				else {
					if (joining) {
						joinLine.str = joinLine.str + " " + line.str;
						iter.remove();
					}

					joinLine = null;
					joining = false;
				}
			}

			if (struct.isTypeOf(FunctionT)) {
				for (Line line : patch.lines) {
					line.str = LineOffsetPattern.matcher(line.str).replaceAll("");
					line.str = FunctionIgnorePattern.matcher(line.str).replaceAll(" ");
				}
			}
			else if (struct.isTypeOf(ScriptT)) {
				for (Line line : patch.lines) {
					line.str = LineOffsetPattern.matcher(line.str).replaceAll("");
					line.str = line.str.replaceAll("([)(])", " $1 "); // add spaces around parens
					if (!ScriptInlineExpPattern.matcher(line.str.toUpperCase()).matches())
						line.str = ScriptIgnorePattern.matcher(line.str).replaceAll(" ");
				}
			}

			// remove empty lines and tokenize
			iter = patch.lines.iterator();
			while (iter.hasNext()) {
				Line line = iter.next();
				if (line.str.isEmpty())
					iter.remove();
				else if (!line.tokenized)
					line.tokenize();
			}
		}
	}

	private final void removeJapaneseStrings(Collection<Struct> structs)
	{
		for (Struct str : structs) {
			if (str.isTypeOf(SjisT) && str.isDumped()) {
				str.forceDelete = true;
				str.deleted = true;
				deletedRegions.add(new Region(str.originalFileOffset, str.originalFileOffset + str.originalSize));
			}
		}
	}

	private final void addPatch(Struct struct, Patch patch)
	{
		struct.patchList.add(patch);

		if (!patchedStructures.contains(struct))
			patchedStructures.add(struct);

		struct.patched = true;
	}

	private final Patch parseStructOffset(Line line, Struct struct, String offsetName)
	{
		Patch patch;
		try {
			StructField field = struct.parseFieldOffset(this, struct, "[" + offsetName + "]");
			if (field != null)
				patch = Patch.createBounded(line, offsetName, struct, field.offset, field.length);
			else
				patch = Patch.create(line, struct, offsetName);
		}
		catch (IllegalArgumentException e) {
			throw new InputFileException(line, "Unknown offset for struct %s: %n%s", struct.name, offsetName);
		}

		if (struct.isDumped() && patch.startingPos > struct.originalSize)
			Logger.logfWarning("Patch offset %s[%s] exceeds size of data structure (%X)!",
				struct.name, offsetName, struct.originalSize);

		return patch;
	}

	private static final Pattern FillRangePattern = Pattern.compile("(?i)@fill\\s+([0-9A-F]+)\\s+([0-9A-F]+)\\s*");

	// @fill 80231000 80238000
	// @fill 1E2000 1F8000
	protected void parseFill(PatchUnit unit)
	{
		if (overlayMode)
			throw new InputFileException(unit.declaration, "Fill may only be used in global patches.");

		Matcher fillMatcher = FillRangePattern.matcher(unit.declaration.str);
		if (!fillMatcher.matches())
			throw new InputFileException(unit.declaration, "Invalid fill declaration: %n%s", unit.declaration.trimmedInput());

		if (unit.body.isEmpty())
			throw new InputFileException(unit.declaration, "Fill has no body.");

		int start = (int) Long.parseLong(fillMatcher.group(1), 16);
		int end = (int) Long.parseLong(fillMatcher.group(2), 16);

		if (end <= start)
			throw new InputFileException(unit.declaration, "Fill region must have a positive size: %n%s", unit.declaration.trimmedInput());

		int min = overlayMode ? baseAddress : 0;
		int max = overlayMode ? addressLimit : RomPatcher.ROM_BASE;
		int length = end - start;

		if (start < min || end > max)
			throw new InputFileException(unit.declaration, "Fill region is out of range (%X-%X): %n%s", baseAddress, addressLimit,
				unit.declaration.trimmedInput());

		String structName = SyntaxConstants.POINTER_PREFIX + String.format("FillRegion_%s_%X_%X", primarySource.getName(), start, end);

		if (declaredStructs.containsKey(structName))
			throw new InputFileException(unit.declaration, "Struct already declared: %s", structName);

		Struct struct;
		if (overlayMode)
			struct = Struct.createOverlay(DataTableT, defaultScope, structName, currentNamespace, start, toOverlayOffset(start), length);
		else
			struct = Struct.createFixed(DataTableT, defaultScope, structName, start, length);
		struct.fillMode = true;

		declaredStructs.put(struct.name, struct);

		Patch currentPatch = Patch.create(unit.declaration, struct);
		addPatch(struct, currentPatch);

		for (Line line : unit.body) {
			Matcher m = PatchOffsetPattern.matcher(line.str);
			if (m.matches())
				throw new InputFileException(unit.declaration, "Cannot have offsets in the body of a fill!");
			currentPatch.lines.add(line);
		}

		unit.parsed = true;
	}

	// general format:
	// @Hook:Scope location[offset] ~annotation
	private static final Pattern HookPattern = Pattern.compile(
		"(?i)@Hook"
			+ "(?::(\\w+))?" // group 1: scope (optional)
			+ "\\s+([0-9A-F]+|\\$[\\w?]+)" // group 2: location -- rom offset OR pointer name
			+ "(?:\\s*\\[(.+)\\])?" // group 3: offset (optional)
			+ "(?:\\s+~([\\w:]+))?" // group 4: annotations (optional)
			+ "\\s*");

	/**
	 * Hooks are global patches to virtual function structs.
	 */
	protected void parseHook(PatchUnit unit)
	{
		if (overlayMode)
			throw new InputFileException(unit.declaration, "Hook may only be used in global patches.");

		Matcher hookMatcher = HookPattern.matcher(unit.declaration.str);
		if (!hookMatcher.matches())
			throw new InputFileException(unit.declaration, "Invalid hook declaration: %n%s", unit.declaration.trimmedInput());

		if (unit.body.isEmpty())
			throw new InputFileException(unit.declaration, "Hook has no body.");

		String hookScope = hookMatcher.group(1);
		String hookLocation = hookMatcher.group(2);
		String hookOffset = hookMatcher.group(3);
		String hookAnnotations = hookMatcher.group(4);

		if (hookScope == null)
			hookScope = "";
		if (hookLocation == null)
			hookLocation = "";
		if (hookOffset == null)
			hookOffset = "";
		if (hookAnnotations == null)
			hookAnnotations = "";

		String[] annotations = hookAnnotations.split(":");

		LibScope scope = parseScope(unit.declaration, hookScope);
		int offset = getOffsetForGlobalTarget(unit.declaration, ProjectDatabase.rom, scope, hookLocation);

		if (!hookOffset.isEmpty()) {
			try {
				int baseOffset = ConstMath.parse(this, null, hookOffset);
				if (baseOffset < 0)
					throw new InvalidInputException("%s has illegal size: -%X", hookOffset, -baseOffset);
				offset += baseOffset;
			}
			catch (InvalidInputException e) {
				throw new InputFileException(unit.declaration, e);
			}
		}

		String structName = SyntaxConstants.POINTER_PREFIX + String.format("HookBody_%s_%06X", primarySource.getName(), offset);

		if (declaredStructs.containsKey(structName))
			throw new InputFileException(unit.declaration, "Struct already declared: %s", structName);

		Struct struct = Struct.createHook(FunctionT, scope, appendNamespace(structName), offset);
		declaredStructs.put(struct.name, struct);
		Logger.logf("Creating hook body for: %s", hookLocation);

		Patch currentPatch = Patch.create(unit.declaration, struct);
		addPatch(struct, currentPatch);

		Collections.addAll(currentPatch.annotations, annotations);

		for (Line line : unit.body) {
			Matcher m = PatchOffsetPattern.matcher(line.str);
			if (m.matches())
				throw new InputFileException(unit.declaration, "Cannot have offsets in the body of a hook!");
			currentPatch.lines.add(line);
		}

		unit.parsed = true;
	}

	// @Subscribe:Primary:Name ~annotation
	private static final Pattern SubscribePattern = Pattern.compile(
		"(?i)@subscribe"
			+ "(?::(\\w+))?" // group 1: priority (optional)
			+ ":([0-9A-F]+|\\w+)" // group 2: subscription name
			+ "(?:\\s+~([\\w:]+))?" // group 3: annotations (optional)
			+ "\\s*");

	protected void parseSubscribe(PatchUnit unit)
	{
		if (overlayMode)
			throw new InputFileException(unit.declaration, "Subscriptions may only be used in global patches.");

		Matcher subMatcher = SubscribePattern.matcher(unit.declaration.str);
		if (!subMatcher.matches())
			throw new InputFileException(unit.declaration, "Invalid subscription: %n%s", unit.declaration.trimmedInput());

		if (unit.body.isEmpty())
			throw new InputFileException(unit.declaration, "Subscription has no body.");

		String subPriority = subMatcher.group(1);
		String subName = subMatcher.group(2);
		String subAnnotations = subMatcher.group(3);

		if (subPriority == null)
			subPriority = "";
		if (subName == null)
			subName = "";
		if (subAnnotations == null)
			subAnnotations = "";

		String[] annotations = subAnnotations.split(":");

		String structName = SyntaxConstants.POINTER_PREFIX + String.format("Subscription_%s_%s_%X",
			primarySource.getName(), subName, System.nanoTime());

		if (declaredStructs.containsKey(structName))
			throw new InputFileException(unit.declaration, "Struct already declared: %s", structName);

		Struct struct = Struct.createNew(FunctionT, defaultScope, structName, currentNamespace);
		declaredStructs.put(struct.name, struct);
		Logger.logf("Creating subscription for: %s", subName);

		boolean valid;
		if (!subPriority.isEmpty()) {
			try {
				valid = SubscriptionManager.subscribe(struct, subName, DataUtils.parseIntString(subPriority));
			}
			catch (InvalidInputException e) {
				throw new InputFileException(unit.declaration, "Could not parse subscription priority: " + subPriority);
			}
		}
		else
			valid = SubscriptionManager.subscribe(struct, subName);

		if (!valid)
			throw new InputFileException(unit.declaration, "No subscription available for " + subName);

		Patch currentPatch = Patch.create(unit.declaration, struct);
		addPatch(struct, currentPatch);

		Collections.addAll(currentPatch.annotations, annotations);

		for (Line line : unit.body) {
			Matcher m = PatchOffsetPattern.matcher(line.str);
			if (m.matches())
				throw new InputFileException(unit.declaration, "Cannot have offsets in the body of a hook!");
			currentPatch.lines.add(line);
		}

		unit.parsed = true;
	}

	// local patches:
	// @ $Pointer [baseOffset] ~annotationA:annotationB {}

	// global patches:
	// @Data XXXX
	// @Function XXXX
	// @DisplayList XXXX
	// @Script:Global XXXX
	// @Script:Map XXXX
	// @Script:Battle XXXX

	// @Hook XXXX
	// @Subscribe:PP:XXXX
	// @Fill XXXX YYYY

	// general format:
	// @Type:Scope location ~annotation
	// location can be either...
	// ...a raw rom offset + relative offset: XXXX[Y]
	// ...a global pointer + relative offset: $P[Y]
	private static final Pattern PatchPattern = Pattern.compile(
		"(?i)@(\\w+)?" // group 1: type (optional for local, required for global)
			+ "(?::(\\w+))?" // group 2: scope (optional -- global only)
			+ "\\s+([0-9A-F]+|\\$[\\w?]+)" // group 3: location -- rom offset OR pointer name
			+ "(?:\\s*\\[(.+)\\])?" // group 4: offset (optional)
			+ "(?:\\s+~([\\w:]+))?" // group 5: annotations (optional)
			+ "\\s*");
	// one line: (?i)@(\w+)?(?::(\w+))?\s+([0-9A-F]+|\$[\w?]+)(?:\s*\[(.+)\])?(?:\s+~([\w:]+))?\s*

	private final void parsePatch(PatchUnit unit)
	{
		Matcher patchMatcher = PatchPattern.matcher(unit.declaration.str);
		if (!patchMatcher.matches())
			throw new InputFileException(unit.declaration, "Invalid patch declaration: %n%s", unit.declaration.trimmedInput());

		if (unit.body.isEmpty())
			throw new InputFileException(unit.declaration, "Patch declaration has no body.");

		String patchType = patchMatcher.group(1);
		String patchScope = patchMatcher.group(2);
		String patchLocation = patchMatcher.group(3);
		String patchOffset = patchMatcher.group(4);
		String patchAnnotations = patchMatcher.group(5);

		if (patchType == null)
			patchType = "";
		if (patchScope == null)
			patchScope = "";
		if (patchLocation == null)
			patchLocation = "";
		if (patchOffset == null)
			patchOffset = "";
		if (patchAnnotations == null)
			patchAnnotations = "";

		String[] annotations = patchAnnotations.split(":");

		Struct target;
		if (overlayMode)
			target = getStructforLocalPatch(unit, patchType, patchScope, patchLocation);
		else
			target = getStructforGlobalPatch(unit, patchType, patchScope, patchLocation);

		parsePatchBody(unit, target, patchOffset, annotations);
		unit.parsed = true;
	}

	private final Struct getStructforLocalPatch(PatchUnit unit, String structType, String patchScope, String patchLocation)
	{
		if (!patchLocation.startsWith("$"))
			throw new InputFileException(unit.declaration, "Invalid struct name: %s (must begin with $)", patchLocation);

		Struct struct = declaredStructs.get(patchLocation);
		if (struct == null)
			throw new InputFileException(unit.declaration, "Trying to patch unrecognized struct name: %s", patchLocation);

		boolean stringPatch = structType.equalsIgnoreCase("string") || structType.equalsIgnoreCase("message");

		if (struct.isTypeOf(StringT) && !stringPatch)
			throw new InputFileException(unit.declaration, "Message patches must begin with @Message");

		if (!struct.isTypeOf(StringT) && stringPatch)
			throw new InputFileException(unit.declaration, "Patch type %s does not match type %s", structType, struct.getTypeName());

		if (!structType.isEmpty() && !stringPatch)
			throw new InputFileException(unit.declaration, "Overlay patch files may only specify type as 'Message': %s", structType);

		if (!patchScope.isEmpty())
			throw new InputFileException(unit.declaration, "Overlay patch files may not specify context: %s", patchScope);

		return struct;
	}

	private final Struct getStructforGlobalPatch(PatchUnit unit, String structType, String patchScope, String patchLocation)
	{
		if (structType.isEmpty())
			throw new InputFileException(unit.declaration, "Global patch files must specify a type: %n%s", unit.declaration.trimmedInput());

		LibScope scope = parseScope(unit.declaration, patchScope);

		// does struct exist locally?
		Struct localStruct = declaredStructs.get(patchLocation);
		if (localStruct != null) {
			if (localStruct.scope != scope)
				throw new InputFileException(unit.declaration, "Scope of patch does not match struct: %s vs %s", scope, localStruct.scope);
			return localStruct;
		}

		// is struct a known global?
		Struct globalStruct = virtualStructsMap.get(patchLocation);
		if (globalStruct != null) {
			if (globalStruct.scope != scope)
				throw new InputFileException(unit.declaration, "Scope of patch does not match struct: %s vs %s", scope, globalStruct.scope);
			return globalStruct;
		}

		// create new global
		int offset = getOffsetForGlobalTarget(unit.declaration, ProjectDatabase.rom, scope, patchLocation);

		// create virtual struct
		StructType type = typeMap.get(structType); // globals assume SCOPE = all here!
		if (type == null)
			throw new InputFileException(unit.declaration, "Invalid declaration, no such type: %s", structType);

		Struct s = Struct.createFixed(type, scope, patchLocation, offset);
		virtualStructsMap.put(patchLocation, s);

		return s;
	}

	private final int getOffsetForGlobalTarget(Line sourceLine, ROM rom, LibScope scope, String target)
	{
		int structOffset;

		if (target.startsWith("$")) {
			LibEntry entry = ProjectDatabase.rom.getLibrary(scope).get(target.substring(1));
			if (entry == null)
				throw new InputFileException(sourceLine, "Could not resolve pointer: " + target);
			if (entry.offset == -1)
				throw new InputFileException(sourceLine, target + " does not have a ROM location defined in the library.");
			structOffset = entry.offset;
		}
		else if (target.matches("[0-9A-Fa-f]+")) // @Function 74C00 etc
		{
			try {
				structOffset = (int) Long.parseLong(target, 16);
				if ((structOffset & 0xFF000000) == 0x80000000) {
					Integer lookup = rom.getOffset(scope, structOffset);
					if (lookup == null)
						throw new InputFileException(sourceLine, "Could not determine offset for address %08X in %s", structOffset, scope);
					structOffset = lookup;
				}
				Logger.logf("Global patch for %s will be written to %X", target, structOffset);
			}
			catch (NumberFormatException e) {
				throw new InputFileException(sourceLine, "NumberFormatException caused by: %s %nExpected a numeric value.", target);
			}
		}
		else
			throw new InputFileException(sourceLine, "Could not parse patch target: %s", target);

		return structOffset;
	}

	private final void parsePatchBody(PatchUnit unit, Struct struct, String patchOffset, String[] annotations)
	{
		Patch currentPatch;

		if (patchOffset.isEmpty()) {
			currentPatch = Patch.create(unit.declaration, struct);
			struct.replaceExisting = true;
		}
		else {
			try {
				int baseOffset = ConstMath.parse(this, null, patchOffset);
				if (baseOffset < 0)
					throw new InvalidInputException("%s has illegal size: -%X", patchOffset, -baseOffset);
				currentPatch = Patch.create(unit.declaration, struct, baseOffset);
				if (struct.isDumped() && currentPatch.startingPos > struct.originalSize)
					Logger.logfWarning("Patch offset %s[%d] exceeds size of data structure (%X)!", struct.name, baseOffset, struct.originalSize);
				struct.replaceExisting = false;
			}
			catch (InvalidInputException e) {
				throw new InputFileException(unit.declaration, e);
			}
		}

		Collections.addAll(currentPatch.annotations, annotations);

		if (currentPatch.startingPos != 0)
			Logger.logf("Reading patch for %s (%X)", struct.name, currentPatch.startingPos);
		else
			Logger.logf("Reading patch for %s", struct.name);

		addPatch(struct, currentPatch);

		if (struct.isTypeOf(StringT)) {
			// ignore all struct offsets
			currentPatch.lines.addAll(unit.body);
		}
		else {
			for (Line line : unit.body) {
				Matcher offsetMatcher = PatchOffsetPattern.matcher(line.str);

				if (offsetMatcher.matches()) {
					struct.replaceExisting = false;
					currentPatch = parseStructOffset(line, struct, offsetMatcher.group(1));

					Collections.addAll(currentPatch.annotations, annotations);

					addPatch(struct, currentPatch);

					line.str = offsetMatcher.group(2);
				}

				if (line.str != null)
					currentPatch.lines.add(line);
			}
		}

		// annotations which apply to whole struct
		for (String s : annotations) {
			if (s.equalsIgnoreCase("IgnoreInvalidScriptSyntax"))
				struct.validateScriptSyntax = false;
		}

		unit.parsed = true;
	}

	// #new:Data $Name
	// #new:Function $Name
	// #new:Script:Global $Name
	// #new:Script:Map $Name
	// #new:Script:Battle $Name
	private static final Pattern NewPattern = Pattern.compile(
		"(?i)#(new|export)" // group 1: access (new | export)
			+ ":(\\w+)" // group 2: type
			+ "(?::(\\w+))?" // group 3: scope (optional)
			+ "\\s+(\\S+)" // group 4: identifier
			+ "(?:\\s+~(\\S+))?" // group 5: annotations (optional)
			+ "\\s*");

	private final void parseNew(PatchUnit unit)
	{
		Matcher newMatcher = NewPattern.matcher(unit.declaration.str);
		if (!newMatcher.matches())
			throw new InputFileException(unit.declaration, "Invalid declaration: %n%s", unit.declaration.trimmedInput());

		if (unit.body.isEmpty())
			throw new InputFileException(unit.declaration, "Struct declaration has no body.");

		boolean exporting = newMatcher.group(1) != null && newMatcher.group(1).equalsIgnoreCase("export");
		String structType = newMatcher.group(2);
		String structScope = newMatcher.group(3);
		String structName = newMatcher.group(4);

		LibScope scope = parseScope(unit.declaration, structScope);

		if (overlayMode && exporting)
			throw new InputFileException(unit.declaration, "Only global patches may export struct pointers.");

		if (overlayMode && structScope != null)
			throw new InputFileException(unit.declaration, "Only global patches may specify struct scope.");

		if (structType.equalsIgnoreCase("String") || structType.equalsIgnoreCase("Message"))
			throw new InputFileException(unit.declaration, "Invalid message declaration: %n%s", unit.declaration.trimmedInput());

		if (!DataUtils.isPointerFmt(structName))
			throw new InputFileException(unit.declaration, "Invalid struct name: %s (must begin with %c)", structName, SyntaxConstants.POINTER_PREFIX);

		if (!ValidPointerPattern.matcher(structName).matches())
			throw new InputFileException(unit.declaration, "Invalid struct name: %s (contains illegal character)", structName);

		if (declaredStructs.containsKey(structName))
			throw new InputFileException(unit.declaration, "Struct already declared: %s", structName);

		Struct struct = declareStruct(structType, scope, structName);
		if (struct == null)
			throw new InputFileException(unit.declaration, "Invalid declaration, no such type: %s", structType);

		declaredStructs.put(struct.name, struct);
		Logger.logf("Creating struct: %s", struct.name);

		if (exporting) {
			globalsDatabase.setGlobalPointer(struct.name, -1);
			struct.exported = true;
		}

		Patch currentPatch = Patch.create(unit.declaration, struct);
		addPatch(struct, currentPatch);

		for (Line line : unit.body) {
			Matcher m = PatchOffsetPattern.matcher(line.str);
			if (m.matches()) {
				currentPatch = parseStructOffset(line, struct, m.group(1));
				addPatch(struct, currentPatch);
				line.str = m.group(2);
			}
			currentPatch.lines.add(line);
		}

		unit.parsed = true;
	}

	private static final Pattern StringPattern = Pattern.compile("(?i)#(?:(new|export):)?(?:string|message)\\s+(\\S+)\\s*");

	// #string $Pointer {}
	private final void parseString(PatchUnit unit)
	{
		Matcher stringMatcher = StringPattern.matcher(unit.declaration.str);
		if (!stringMatcher.matches())
			throw new InputFileException(unit.declaration, "Invalid message definition: %n%s", unit.declaration.trimmedInput());

		if (unit.body.isEmpty())
			throw new InputFileException(unit.declaration, "Message declaration has no body.");

		boolean exporting = stringMatcher.group(1) != null && stringMatcher.group(1).equalsIgnoreCase("export");
		String stringName = stringMatcher.group(2);

		if (overlayMode && exporting)
			throw new InputFileException(unit.declaration, "Only global patches may export messages: %s", stringName);

		if (!DataUtils.isPointerFmt(stringName))
			throw new InputFileException(unit.declaration, "Invalid message name: %s (must begin with %c)", stringName, SyntaxConstants.POINTER_PREFIX);

		if (!ValidPointerPattern.matcher(stringName).matches())
			throw new InputFileException(unit.declaration, "Invalid message name: %s (contains illegal character)", stringName);

		if (declaredStructs.containsKey(stringName))
			throw new InputFileException(unit.declaration, "Invalid message name, %s is already in use.", stringName);

		Struct struct = declareStruct("Message", defaultScope, stringName);
		assert (struct != null) : unit.declaration.str;
		declaredStructs.put(struct.name, struct);

		if (exporting) {
			globalsDatabase.setGlobalPointer(struct.name, -1);
			struct.exported = true;
		}

		Patch patch = Patch.create(unit.declaration, struct);
		addPatch(struct, patch);

		patch.lines.addAll(unit.body);

		unit.parsed = true;
	}

	private static final Pattern DefinePattern = Pattern.compile("(?i)#(define|export)\\s+(\\.\\S+)\\s+(\\S+)\\s*");

	// #define .Name Value
	// #export .Name Value
	private final void parseConstant(PatchUnit unit)
	{
		Matcher defineMatcher = DefinePattern.matcher(unit.declaration.str);
		if (!defineMatcher.matches())
			throw new InputFileException(unit.declaration, "Invalid constant definition: %n%s", unit.declaration.trimmedInput());

		if (!unit.body.isEmpty())
			throw new InputFileException(unit.declaration, "Define cannot have a body.");

		boolean exporting = defineMatcher.group(1) != null && defineMatcher.group(1).equalsIgnoreCase("export");
		String constName = defineMatcher.group(2);
		String constValue = defineMatcher.group(3);

		if (overlayMode && exporting)
			throw new InputFileException(unit.declaration, "Only global patches may export constants: %s", constName);

		if (!DataUtils.isConstantFmt(constName))
			throw new InputFileException(unit.declaration, "Invalid constant name: %s (must begin with %c)", constName, SyntaxConstants.CONSTANT_PREFIX);

		if (!ValidConstPattern.matcher(constName).matches())
			throw new InputFileException(unit.declaration, "Invalid constant name: %s (contains illegal character)", constName);

		if (ProjectDatabase.has(constName))
			throw new InputFileException(unit.declaration, "Constant already defined in database: %s", constName);

		if (constantMap.containsKey(constName))
			throw new InputFileException(unit.declaration, "Constant already defined locally: %s", constName);

		if (globalsDatabase.hasGlobalConstant(constName))
			throw new InputFileException(unit.declaration, "Constant already defined globally: %s", constName);

		if (DataUtils.isConstantFmt(constValue)) {
			try {
				constValue = resolveConstant(constValue, true);
			}
			catch (InvalidInputException e) {
				throw new InputFileException(unit.declaration, e);
			}
			if (constValue == null)
				throw new InputFileException(unit.declaration, "Invalid constant definition: %s (cannot be another constant)", constValue);
		}

		constantMap.put(appendNamespace(constName), constValue);

		if (exporting)
			globalsDatabase.setGlobalConstant(constName, constValue);

		unit.parsed = true;
	}

	// #export $Name
	private static final Pattern ExportPattern = Pattern.compile("(?i)#export\\s+(\\$\\S+)");

	private final void parsePointerExport(PatchUnit unit)
	{
		if (overlayMode)
			throw new InputFileException(unit.declaration, "Only global patches may export struct pointers.");

		Matcher exportMatcher = ExportPattern.matcher(unit.declaration.str);
		if (!exportMatcher.matches())
			throw new InputFileException(unit.declaration, "Invalid pointer export: %n%s", unit.declaration.trimmedInput());

		String structName = exportMatcher.group(1);

		if (!DataUtils.isPointerFmt(structName))
			throw new InputFileException(unit.declaration, "Invalid struct name: %s (must begin with %c)", structName, SyntaxConstants.POINTER_PREFIX);

		if (!ValidPointerPattern.matcher(structName).matches())
			throw new InputFileException(unit.declaration, "Invalid struct name: %s (contains illegal character)", structName);

		if (!declaredStructs.containsKey(structName))
			throw new InputFileException(unit.declaration, "Struct is not defined: %s", structName);

		Struct struct = declaredStructs.get(structName);

		globalsDatabase.setGlobalPointer(struct.name, -1);
		struct.exported = true;

		unit.parsed = true;
	}

	private static final Pattern AliasPattern = Pattern.compile("(?i)#alias\\s+(\\S+)\\s+(\\S+)\\s*");

	// #alias $ComplicatedStructName $SimpleName
	private final void parseAlias(PatchUnit unit)
	{
		Matcher aliasMatcher = AliasPattern.matcher(unit.declaration.str);
		if (!aliasMatcher.matches())
			throw new InputFileException(unit.declaration, "Invalid alias: %n%s", unit.declaration.trimmedInput());

		if (!unit.body.isEmpty())
			throw new InputFileException(unit.declaration, "Alias cannot have a body.");

		String originalName = aliasMatcher.group(1);
		String aliasName = aliasMatcher.group(2);

		if (!ValidPointerPattern.matcher(originalName).matches())
			throw new InputFileException(unit.declaration, "Invalid pointer name: %s (contains illegal character)", originalName);

		if (!ValidPointerPattern.matcher(aliasName).matches())
			throw new InputFileException(unit.declaration, "Invalid pointer name: %s (contains illegal character)", aliasName);

		if (!declaredStructs.containsKey(originalName))
			throw new InputFileException(unit.declaration, "Alias refers to unknown struct: %s", originalName);

		if (declaredStructs.containsKey(aliasName))
			throw new InputFileException(unit.declaration, "Alias name already in use: %s", aliasName);

		declaredStructs.put(aliasName, declaredStructs.get(originalName));

		unit.parsed = true;
	}

	private static final Pattern KeyValuePairPattern = Pattern.compile("([\\w:]+)\\s*=\\s*(\\S+)");
	private static final Pattern ImportPattern = Pattern.compile("(?i)#import\\s+(\\S+)(?:\\s+(\\S+))?\\s*");

	// #import MyFile.bpat Namespace
	private final void parseImport(PatchUnit unit)
	{
		if (!overlayMode)
			throw new InputFileException(unit.declaration, "Import is not available in global patches.");

		if (importDirectory == null)
			throw new InputFileException(unit.declaration, "Import directory is not set!");

		Matcher importMatcher = ImportPattern.matcher(unit.declaration.str);
		if (!importMatcher.matches())
			throw new InputFileException(unit.declaration, "Invalid import: %n%s", unit.declaration.trimmedInput());

		String filename = importMatcher.group(1);
		String importNamespace = importMatcher.group(2);
		if (importNamespace == null) // importNamespace
			importNamespace = "";

		if (!importNamespace.isEmpty() && !importNamespace.matches("[\\w\\?]+"))
			throw new InputFileException(unit.declaration, "Invalid namespace: %s (contains illegal character)", importNamespace);

		File f = new File(importDirectory + "/" + filename);

		if (!f.exists())
			throw new InputFileException(unit.declaration, "Import file does not exist: %s", filename);

		CaseInsensitiveMap<String> rules = new CaseInsensitiveMap<>();
		if (!unit.body.isEmpty()) {
			for (Line line : unit.body) {
				Matcher m = KeyValuePairPattern.matcher(line.str);
				if (!m.matches())
					throw new InputFileException(line, "Invalid key=value pair for import: %s", line.str);
				String key = m.group(1);
				String value = m.group(2);

				if (DataUtils.isConstantFmt(value)) {
					try {
						value = resolveConstant(value, true);
					}
					catch (InvalidInputException e) {
						throw new InputFileException(line, e);
					}
				}

				rules.put(key, value);
			}
		}

		String prevNamespace = currentNamespace;
		try {
			currentNamespace = importNamespace;
			readPatchFile(f, rules);
		}
		catch (IOException e) {
			throw new InputFileException(f, e);
		}
		currentNamespace = prevNamespace;

		unit.parsed = true;
	}

	private static final Pattern DeletePattern = Pattern.compile("(?i)#delete\\s+(" + ValidPointerName + ")\\s*");

	// #delete $StructName
	private final void parseDelete(PatchUnit unit)
	{
		if (!overlayMode)
			throw new InputFileException(unit.declaration, "Delete is not available in global patches.");

		if (!currentNamespace.isEmpty())
			throw new InputFileException(unit.declaration, "Delete cannot be used within imports: %n%s", unit.declaration.trimmedInput());

		if (!unit.body.isEmpty())
			throw new InputFileException(unit.declaration, "Delete cannot have a body.");

		Matcher deleteMatcher = DeletePattern.matcher(unit.declaration.str);
		if (!deleteMatcher.matches())
			throw new InputFileException(unit.declaration, "Invalid delete: %n%s", unit.declaration.trimmedInput());

		String structName = deleteMatcher.group(1);

		if (structName.equalsIgnoreCase("$Start") || structName.equalsIgnoreCase("$End"))
			throw new InputFileException(unit.declaration, "Cannot delete protected struct: %n%s", unit.declaration.trimmedInput());

		Struct struct = declaredStructs.get(structName);
		if (struct == null)
			throw new InputFileException(unit.declaration, "Cannot delete unrecognized struct: %n%s", unit.declaration.trimmedInput());

		// delete it
		deletedRegions.add(new Region(struct.originalFileOffset, struct.originalFileOffset + struct.originalSize));
		struct.deleted = true;

		unit.parsed = true;
	}

	private static final Pattern ReserveRangePattern = Pattern.compile("(?i)#reserve\\s+([0-9A-F]{8})\\s+([0-9A-F]{8})\\s*");
	private static final Pattern ReserveSizePattern = Pattern.compile("(?i)#reserve\\s+([0-9A-F]+`?)\\s+(\\$\\w+)\\s*");

	// #reserve 80231000 80238000
	// #reserve XXXX $Name
	private final void parseReserve(PatchUnit unit)
	{
		if (!unit.body.isEmpty())
			throw new InputFileException(unit.declaration, "Reserve cannot have a body.");

		String line = unit.declaration.str.trim();
		Matcher rangeMatcher = ReserveRangePattern.matcher(line);
		Matcher sizeMatcher = ReserveSizePattern.matcher(line);

		if (rangeMatcher.matches()) {
			if (!overlayMode)
				throw new InputFileException(unit.declaration, "Global patches may not reserve explicit memory regions: %n%s", unit.declaration.trimmedInput());

			int start = (int) Long.parseLong(rangeMatcher.group(1), 16);
			int end = (int) Long.parseLong(rangeMatcher.group(2), 16);

			if (end <= start)
				throw new InputFileException(unit.declaration, "Reserved region must have a positive size: %n%s", unit.declaration.trimmedInput());

			if (start < baseAddress || end > addressLimit)
				throw new InputFileException(unit.declaration, "Reserved region is out of address range (%X-%X): %n%s", baseAddress, addressLimit,
					unit.declaration.trimmedInput());

			Region reserved = new Region(toOverlayOffset(start), toOverlayOffset(end));
			for (Region r : reservedRegions)
				if (Region.overlaps(reserved, r))
					throw new InputFileException(unit.declaration, "Reserved region (%X-%X) conflicts with other reservation", baseAddress, addressLimit);
			reservedRegions.add(reserved);
		}
		else if (sizeMatcher.matches()) {
			Struct struct = declareStruct("DataTable", defaultScope, sizeMatcher.group(2));
			if (struct == null)
				throw new InputFileException(unit.declaration, "Invalid declaration, no such type: DataTable");

			declaredStructs.put(struct.name, struct);
			try {
				struct.finalSize = DataUtils.parseIntString(sizeMatcher.group(1));
				struct.hasFixedSize = true;
			}
			catch (InvalidInputException e) {
				throw new InputFileException(unit.declaration, "Could not parse reservation size: %n%s", unit.declaration.trimmedInput());
			}

			Logger.logf("Creating struct: %s", struct.name);
		}
		else
			throw new InputFileException(unit.declaration, "Invalid reservation: %n%s", unit.declaration.trimmedInput());

		unit.parsed = true;
	}

	private final LibScope parseScope(Line source, String scopeName)
	{
		LibScope scope = defaultScope;
		if (scopeName != null && !scopeName.isEmpty()) {
			switch (scopeName.toLowerCase()) {
				case "global":
					break;
				case "map":
				case "world":
					scope = LibScope.World;
					break;
				case "battle":
					scope = LibScope.Battle;
					break;
				case "pause":
					scope = LibScope.Pause;
					break;
				case "mainmenu":
				case "filemenu":
				case "files":
					scope = LibScope.MainMenu;
					break;
				default:
					throw new InputFileException(source, "Unknown context for patch: %s", scopeName);
			}
		}
		return scope;
	}

	private final void validateStructs()
	{
		// cull empty patches
		for (Struct s : patchedStructures) {
			Iterator<Patch> iter = s.patchList.iterator();
			while (iter.hasNext()) {
				Patch patch = iter.next();
				if (patch.lines.isEmpty())
					iter.remove();

				for (Line line : patch.lines)
					assert (line.numTokens() > 0);
			}
		}

		// check for deletion conflicts
		for (Struct s : patchedStructures) {
			if (s.deleted)
				throw new InputFileException(primarySource, "Cannot apply patch to deleted struct: " + s.name);
		}
	}

	// Replaces symbols in patches with their numeric values. This includes:
	// (1) constants such as .ItemSuperShroom
	// (2) script variables such as *VAR[0]
	// (3) expressions such as ~Model:o211
	// (4) script keywords such as IF, END, etc
	private final void replaceAllSymbols()
	{
		for (Struct struct : patchedStructures) {
			for (Patch patch : struct.patchList) {
				if (!struct.isTypeOf(StringT) && !struct.isTypeOf(AsciiT)) {
					if (struct.isTypeOf(FunctionT))
						MIPS.removeVarNames(patch.lines);

					replacePatchSymbols(struct, patch);
					struct.replaceSpecial(this, patch);
					replaceScriptVars(struct, patch); // do these in a separate pass
				}
				else
					struct.replaceSpecial(this, patch);
			}
		}
	}

	private final void createStringLiteralStructs()
	{
		for (Entry<String, Struct> e : stringLiteralMap.entrySet()) {
			String stringName = e.getKey();
			Struct stringStruct = e.getValue();
			Line origin = stringLiteralOriginMap.get(stringName);

			Patch patch = Patch.create(origin, stringStruct);
			patch.lines.add(origin.createLine(e.getKey()));
			stringStruct.patchList.add(patch);
			stringStruct.replaceSpecial(this, patch);

			patchedStructures.add(stringStruct);
			stringStruct.patched = true;
		}
	}

	private final void replacePatchSymbols(Struct struct, Patch patch)
	{
		boolean isScript = struct.isTypeOf(ScriptT);

		for (Line line : patch.lines) {
			if (isScript && InlineCompiler.matchesPattern(line))
				continue; // inline compiler will handle these

			List<String> replacement = new LinkedList<>();

			for (Token t : line.tokens) {
				String s = t.str;

				if (s.length() < 2) {
					replacement.add(s);
					continue;
				}

				char prefix = s.charAt(0);

				if (prefix == SyntaxConstants.CONSTANT_PREFIX) {
					// value of a constant may itself be an expression, e.g. #define .Const ~Expr
					String constValue = resolveConstant(line, struct, s);
					if (!constValue.isEmpty() && constValue.charAt(0) == SyntaxConstants.EXPRESSION_PREFIX) {
						replacement.addAll(replaceExpression(line, struct, constValue));
					}
					else {
						replacement.add(constValue);
					}
				}
				else if (prefix == SyntaxConstants.EXPRESSION_PREFIX) {
					replacement.addAll(replaceExpression(line, struct, s));
				}
				else if (prefix == SyntaxConstants.STRING_DELIMITER) {
					replacement.add(getStringLiteralPointer(line, s));
				}
				else if (prefix == SyntaxConstants.POINTER_PREFIX) {
					replacement.add(appendNamespaceToPointer(line, struct, s));
				}
				else {
					replacement.add(s);
				}
			}

			line.replace(replacement);
		}
	}

	@Override
	public boolean hasConstant(String name)
	{
		try {
			return (resolveConstant(name, false) != null);
		}
		catch (InvalidInputException e) {
			return false;
		}
	}

	@Override
	public Integer getConstantValue(String name)
	{
		try {
			return (int) Long.parseLong(resolveConstant(name, false), 16);
		}
		catch (InvalidInputException e) {
			return null;
		}
	}

	private final void replaceScriptVars(Struct struct, Patch patch)
	{
		for (Line line : patch.lines) {
			List<String> replacement = new LinkedList<>();

			for (Token t : line.tokens) {
				String s = t.str;

				if (s.length() < 2 || s.charAt(0) != SyntaxConstants.SCRIPT_VAR_PREFIX) {
					replacement.add(s);
					continue;
				}

				// allow constants in var offsets, ie *Var[.ConstantName]
				Matcher m = ScriptVarNameOffsetPatten.matcher(s);
				if (m.matches()) {
					if (!m.group(1).equalsIgnoreCase(SyntaxConstants.SCRIPT_VAR_PREFIX + ScriptVariable.FixedReal.getTypeName())) {
						try {
							int offset = ConstMath.parse(this, null, m.group(2));
							s = String.format("%s[%X]", m.group(1), offset);
						}
						catch (InvalidInputException e) {
							throw new InputFileException(line, e);
						}
					}
				}

				try {
					replacement.add(ScriptVariable.parseScriptVariable(s));
				}
				catch (InvalidInputException e) {
					throw new InputFileException(line, e.getMessage());
				}
			}

			line.replace(replacement);
		}
	}

	private String appendNamespaceToPointer(Line line, Struct struct, String s)
	{
		if (globalsDatabase.hasGlobalPointer(s))
			return s; // skip namespacing if pointer is a global/exported

		if (!struct.namespace.isEmpty())
			s = SyntaxConstants.POINTER_PREFIX + struct.namespace + NAMESPACE_SEPERATOR + s.substring(1);
		return s;
	}

	private final String getStringLiteralPointer(Line line, String token)
	{
		int len = token.length();
		if (len < 3 || token.charAt(len - 1) != SyntaxConstants.STRING_DELIMITER)
			throw new InputFileException(line, "Invalid string: " + token);

		String s = token;

		if (stringLiteralMap.containsKey(s))
			return stringLiteralMap.get(s).name;

		String name = SyntaxConstants.POINTER_PREFIX + "ASCII_" + s + "_Literal";
		Struct struct = Struct.createNew(AsciiT, defaultScope, appendNamespace(name), currentNamespace);

		declaredStructs.put(struct.name, struct);
		stringLiteralMap.put(s, struct);
		stringLiteralOriginMap.put(s, line);

		return name;
	}

	private final String resolveConstant(Line line, Struct struct, String constName)
	{
		try {
			return resolveConstant(constName, !struct.isTypeOf(FunctionT));
		}
		catch (InvalidInputException e) {
			throw new InputFileException(line, e);
		}
	}

	public final String resolveConstant(String constName, boolean checkValidity) throws InvalidInputException
	{
		String resolved = null;

		int offset = 0;
		Matcher m = ConstNameOffsetPatten.matcher(constName);
		if (m.matches()) {
			constName = m.group(1);
			String offsetName = m.group(2);

			if (DataUtils.isConstantFmt(offsetName))
				offsetName = resolveConstant(offsetName, checkValidity);

			offset = DataUtils.parseIntString(offsetName);
		}

		if (constantMap.containsKey(constName))
			resolved = constantMap.get(constName);
		else if (globalsDatabase.hasGlobalConstant(constName))
			resolved = globalsDatabase.getGlobalConstant(constName);
		else
			resolved = ProjectDatabase.resolve(constName.substring(1), checkValidity);

		if (resolved != null && offset != 0) {
			int base = DataUtils.parseIntString(resolved);
			resolved = String.format("%08X", base + offset);
		}

		if (resolved != null)
			return resolved;

		if (!checkValidity)
			return constName;

		try {
			Float.parseFloat(constName);
			return constName;
		}
		catch (NumberFormatException e) {
			throw new InvalidInputException("Could not resolve constant: " + constName);
		}
	}

	// generic expression has the form ~CAST:EXPR[OFFSET]
	// both offset and cast may only be applied if the base EXPR is single-word
	// offset is applied *before* cast
	// cast may be used to cast constants, but CANNOT handle scoped constants: ~CAST:.ConstA:ConstB
	private final List<String> replaceExpression(Line line, Struct struct, String exp)
	{
		Matcher m = ExpressionNameOffsetPatten.matcher(exp);
		if (!m.matches())
			throw new InputFileException(line, "Invalid expression: " + exp);
		exp = m.group(1);

		// determine offset value
		int offset = 0;
		if (m.group(2) != null) {
			try {
				offset = ConstMath.parse(this, null, m.group(2));
			}
			catch (InvalidInputException e) {
				throw new InputFileException(line, e);
			}
		}

		List<String> newTokens = new LinkedList<>();
		String[] exprFields = exp.split(":");

		// check for cast expression
		int castSize = 0;
		if (exprFields[0].equalsIgnoreCase("byte"))
			castSize = 1;
		if (exprFields[0].equalsIgnoreCase("short"))
			castSize = 2;
		if (castSize > 0) {
			m = CastConstExpressionPatten.matcher(exp);
			if (m.matches()) {
				newTokens.add(resolveConstant(line, struct, m.group(1)));
			}
			else {
				if (exprFields.length < 2)
					throw new InputFileException(line, "Invalid cast expression: " + exp);

				String[] castedFields = Arrays.copyOfRange(exprFields, 1, exprFields.length);
				newTokens = replaceExpression(line, struct, exp, castedFields);
			}
		}
		else {
			newTokens = replaceExpression(line, struct, exp, exprFields);
		}

		// add offset
		if (offset != 0) {
			if (newTokens.size() > 1)
				throw new InputFileException(line, "Cant apply offset to multi-word expression: " + exp);

			try {
				int v = DataUtils.parseIntString(newTokens.get(0));
				newTokens.set(0, String.format("%08X", v + offset));
			}
			catch (InvalidInputException e) {
				throw new InputFileException(line, e);
			}
		}

		// apply cast
		if (castSize > 0) {
			if (newTokens.size() > 1)
				throw new InputFileException(line, "Cant apply cast to multi-word expression: " + exp);

			try {
				int v = (int) Long.parseLong(newTokens.get(0), 16);
				if (castSize == 1) {
					if (v < -0x80 || v > 0xFF)
						throw new InputFileException(line, "Value too large to store as byte: %d %n%s", v, exp);
					newTokens.set(0, String.format("%02Xb", v));
				}
				else if (castSize == 2) {
					if (v < -0x8000 || v > 0xFFFF)
						throw new InputFileException(line, "Value too large to store as short: %d %n%s", v, exp);
					newTokens.set(0, String.format("%04Xs", v));
				}
			}
			catch (NumberFormatException e) {
				throw new InputFileException(line, e);
			}
		}

		return newTokens;
	}

	private final List<String> replaceExpression(Line line, Struct struct, String exp, String[] exprFields)
	{
		List<String> newTokens = new LinkedList<>();

		switch (exprFields[0].toLowerCase()) {
			case "string":
				if (globalsDatabase.hasStringName(exprFields[1]))
					newTokens.add(String.format("%08X", globalsDatabase.getStringFromName(exprFields[1])));
				else
					throw new InputFileException(line, "No such string: " + exprFields[1]);
				break;

			case "index":
				if (exprFields.length != 2 || exprFields[1] == null || !DataUtils.isScriptVarFmt(exprFields[1]))
					throw new InputFileException(line, "Invalid index expression: " + exp);

				int index = 0;
				try {
					index = ScriptVariable.getScriptVariableIndex(exprFields[1]);
				}
				catch (InvalidInputException e) {
					throw new InputFileException(line, e.getMessage());
				}

				if (index > 0)
					newTokens.add(String.format("%08X", index));
				else
					throw new InputFileException(line, "No such string: " + exprFields[1]);
				break;

			case "sizeof":
				if (exprFields.length != 2)
					throw new InputFileException(line, "Invalid sizeof expression: " + exp);
				StructType structType = typeMap.get(exprFields[1]);
				if (structType == null)
					throw new InputFileException(line, "Could not resolve type: " + exprFields[1]);
				Integer size = structType.getSizeOf();
				if (size == null || size <= 0)
					throw new InputFileException(line, "Size for " + exprFields[1] + " is not available.");
				newTokens.add(String.format("%08X", size));
				break;

			case "func":
				LibEntry entry = ProjectDatabase.rom.getLibrary(struct.scope).get(exprFields[1]);
				if (entry == null)
					throw new InputFileException(line, "Function is not defined: " + exprFields[1]);
				if (!entry.isFunction())
					throw new InputFileException(line, "%s is defined as %s, not as a function.", exprFields[1], entry.type);
				newTokens.add(String.format("%08X", entry.address));
				break;

			case "flags": {
				if (exprFields.length > 4 || exprFields.length < 3)
					throw new InputFileException(line, "Invalid flags expression: " + exp);

				ConstEnum flagsEnum = ProjectDatabase.getFromNamespace(exprFields[1]);
				if (flagsEnum == null)
					throw new InputFileException(line, "No such flags type: " + exp);

				if (!flagsEnum.isFlags())
					throw new InputFileException(line, "Enum type is not flags: " + exp);

				int bits = 0;
				try {
					if (exprFields.length == 4)
						bits = (int) Long.parseLong(exprFields[3], 16);
				}
				catch (NumberFormatException e) {
					throw new InputFileException(line, "Enum literal field could not be parsed: " + exp);
				}

				Integer value = flagsEnum.getFlagsValue(exprFields[2]);
				if (value == null) {
					if (exprFields.length == 4)
						throw new InputFileException(line, "No such flag name for %s: %s", exprFields[1], exprFields[2]);
					try {
						value = (int) Long.parseLong(exprFields[2], 16);
					}
					catch (NumberFormatException e) {
						throw new InputFileException(line, "Flags enum could not be parsed: " + exp);
					}
				}

				bits |= value;
				newTokens.add(String.format("%08X", bits));
			}
				break;

			case "debufftype":
				try {
					newTokens.add(String.format("%08X", ProjectDatabase.getDebuffValue(exprFields)));
				}
				catch (InvalidInputException e) {
					throw new InputFileException(line, e.getMessage());
				}
				break;

			case "fx":
				String effectName;
				if (exprFields.length == 2)
					effectName = exprFields[1];
				else if (exprFields.length == 3)
					effectName = exprFields[1] + ":" + exprFields[2];
				else
					throw new InputFileException(line, "No definition for effect: " + exp);

				if (!ProjectDatabase.EffectType.containsInverse(effectName))
					throw new InputFileException(line, "No definition for effect: " + effectName);

				int effect = ProjectDatabase.EffectType.getInverse(effectName);
				int type = (effect >> 16) & 0xFFFF;
				int subtype = effect & 0xFFFF;

				newTokens.add(String.format("%08X", type));
				if (subtype != 0xFFFF)
					newTokens.add(String.format("%08X", subtype));
				break;

			case "tilefmt":
			case "tileformat": {
				if (exprFields.length != 2)
					throw new InputFileException(line, "Invalid tile format expression: " + exp);
				TileFormat fmt = TileFormat.getFormat(exprFields[1]);
				if (fmt == null)
					throw new InputFileException(line, "Invalid tile format: %s %nExpected one of: %s", exprFields[1], TileFormat.validFormats);
				newTokens.add(String.format("%08X", fmt.type));
			}
				break;

			case "tiledepth": {
				if (exprFields.length != 2)
					throw new InputFileException(line, "Invalid tile format expression: " + exp);
				TileFormat fmt = TileFormat.getFormat(exprFields[1]);
				if (fmt == null)
					throw new InputFileException(line, "Invalid tile format: %s %nExpected one of: %s", exprFields[1], TileFormat.validFormats);
				newTokens.add(String.format("%08X", fmt.depth));
			}
				break;

			case "binaryfile": {
				if (exprFields.length != 2 || exprFields[1] == null)
					throw new InputFileException(line, "Invalid binary file expression: " + exp);

				File f = new File(MOD_RESOURCE + exprFields[1]);

				if (!f.exists())
					throw new InputFileException(line, "File does not exist in " + MOD_RESOURCE + ": " + f.getName());

				try {
					byte[] fileBytes = FileUtils.readFileToByteArray(f);
					for (byte b : fileBytes)
						newTokens.add(String.format("%02Xb", b));
				}
				catch (IOException e) {
					throw new InputFileException(line, "IOException while trying to read binary file: %s%n%s", f.getName(), e.getMessage());
				}
			}
				break;

			// {HudElem:name}
			case "hudelem":
			case "hudelement":
			case "hudscript": {
				if (exprFields.length != 2 || exprFields[1] == null)
					throw new InputFileException(line, "Invalid image asset expression: " + exp);

				HudElementRecord elem = ProjectDatabase.globalsData.getHudElement(exprFields[1]);
				if (elem == null)
					throw new InputFileException(line, "HudElement not found: " + exprFields[2]);

				newTokens.add(String.format("%08X", elem.finalAddress));
			}
				break;

			// {ImgAsset:Img:name}
			case "imgasset": {
				if (exprFields.length != 3 || exprFields[1] == null)
					throw new InputFileException(line, "Invalid image asset expression: " + exp);

				EncodedImageAsset asset = ProjectDatabase.images.getImage(exprFields[2]);
				if (asset == null)
					throw new InputFileException(line, "Image asset not found: " + exprFields[2]);

				switch (exprFields[1].toLowerCase()) {
					case "imgaddr":
						newTokens.add(String.format("%08X", asset.outImgAddress));
						break;
					case "paladdr":
						newTokens.add(String.format("%08X", asset.outPalAddress));
						break;
					case "imgoffset":
						newTokens.add(String.format("%08X", asset.outImgOffset));
						break;
					case "paloffset":
						newTokens.add(String.format("%08X", asset.outPalOffset));
						break;
					default:
						throw new InputFileException(line, "Image asset property: " + exprFields[1]);
				}
			}
				break;

			// {RasterFile:format:filename}
			case "rasterfile": {
				if (exprFields.length != 3 || exprFields[1] == null)
					throw new InputFileException(line, "Invalid raster file expression: " + exp);

				File f = new File(MOD_RESOURCE + exprFields[2]);

				if (!f.exists())
					throw new InputFileException(line, "File does not exist in " + MOD_RESOURCE + ": " + f.getName());

				TileFormat fmt = TileFormat.getFormat(exprFields[1]);

				if (fmt == null)
					throw new InputFileException(line, "Invalid image format for raster: " + exprFields[1]);

				try {
					Tile img = Tile.load(f, fmt);
					byte[] rasterBytes = img.getRasterBytes();
					for (byte b : rasterBytes)
						newTokens.add(String.format("%02Xb", b));
				}
				catch (IOException e) {
					throw new InputFileException(line, "IOException while trying to read raster file: %s%n%s", f.getName(), e.getMessage());
				}
			}
				break;

			// {PaletteFile:format:filename}
			case "palettefile": {
				if (exprFields.length != 3 || exprFields[1] == null)
					throw new InputFileException(line, "Invalid palette file expression: " + exp);

				File f = new File(MOD_RESOURCE + exprFields[2]);

				if (!f.exists())
					throw new InputFileException(line, "File does not exist in " + MOD_RESOURCE + ": " + f.getName());

				TileFormat fmt = TileFormat.getFormat(exprFields[1]);

				if (fmt == null)
					throw new InputFileException(line, "Invalid image format for palette: " + exprFields[1]);

				try {
					Tile img = Tile.load(f, fmt);
					byte[] paletteBytes = img.palette.getPaletteBytes();
					for (byte b : paletteBytes)
						newTokens.add(String.format("%02Xb", b));
				}
				catch (IOException e) {
					throw new InputFileException(line, "IOException while trying to read palette file: %s%n%s", f.getName(), e.getMessage());
				}
			}
				break;

			// {ImageW:filename}
			case "imagew":
			case "imageh": {
				if (exprFields.length != 2 || exprFields[1] == null)
					throw new InputFileException(line, "Invalid image size expression: " + exp);

				File f = new File(MOD_RESOURCE + exprFields[1]);

				if (!f.exists())
					throw new InputFileException(line, "File does not exist in " + MOD_RESOURCE + ": " + f.getName());

				try {
					BufferedImage bimg = ImageIO.read(f);
					if (exprFields[0].toLowerCase().endsWith("w"))
						newTokens.add(String.format("%X", bimg.getWidth()));
					else
						newTokens.add(String.format("%X", bimg.getHeight()));
				}
				catch (IOException e) {
					throw new InputFileException(line, "IOException while trying to read image file: %s%n%s", f.getName(), e.getMessage());
				}
			}
				break;

			case "anim": {
				if (exprFields.length != 2 && exprFields.length != 3)
					throw new InputFileException(line, "Invalid anim: " + exp);

				String palName = (exprFields.length == 4) ? exprFields[3] : "";
				int id = globalsDatabase.getNpcAnimID(exprFields[1], exprFields[2], palName);
				if (id == -1)
					throw new InputFileException(line, "Invalid NPC sprite name: " + exprFields[1]);
				if (id == -2)
					throw new InputFileException(line, "Invalid animation name for NPC sprite " + exprFields[1] + ": " + exprFields[2]);
				if (id == -3)
					throw new InputFileException(line, "Invalid palette name for NPC sprite " + exprFields[1] + ": " + exprFields[3]);
				newTokens.add(String.format("%08X", id));
			}
				break;

			case "playeranim": {
				if (exprFields.length != 2 && exprFields.length != 3)
					throw new InputFileException(line, "Invalid player anim: " + exp);

				String palName = (exprFields.length == 3) ? exprFields[3] : "";
				int id = globalsDatabase.getPlayerAnimID(exprFields[1], exprFields[2], palName);
				if (id == -1)
					throw new InputFileException(line, "Invalid player sprite name: " + exprFields[1]);
				if (id == -2)
					throw new InputFileException(line, "Invalid animation name for player sprite " + exprFields[1] + ": " + exprFields[2]);
				if (id == -3)
					throw new InputFileException(line, "Invalid palette name for player sprite " + exprFields[1] + ": " + exprFields[3]);
				newTokens.add(String.format("%08X", id));
			}
				break;

			case "vec2d":
			case "vec2f":
			case "vecxzd":
			case "vecxzf":
			case "vec3d":
			case "vec3f":
			case "vec4d":
			case "vec4f":
			case "posxd":
			case "posxf":
			case "posyd":
			case "posyf":
			case "poszd":
			case "poszf":
			case "angle":
			case "anglef":
			case "entry":
			case "entryshort":
			case "model":
			case "modelshort":
			case "collider":
			case "collidershort":
			case "zone":
			case "zoneshort":
				replaceMapExpression(line, exprFields, newTokens);
				break;

			default:
				replaceExpression(line, exprFields, newTokens);
				if (newTokens.isEmpty())
					struct.replaceExpression(line, this, exprFields, newTokens);
		}

		if (newTokens.isEmpty())
			throw new InputFileException(line, "Unknown expression: " + exp);

		return newTokens;
	}

	protected void replaceMapExpression(Line line, String[] args, List<String> newTokenList)
	{
		switch (args[0].toLowerCase()) {
			case "vec2d":
			case "vec2f":
			case "vecxzd":
			case "vecxzf": {
				MapIndex index = getMapChecked(line, args, 2);
				String name = args[args.length - 1];
				Marker m = index.getMarker(name);
				if (m == null)
					throw new InputFileException(line, index.getMapName() + " does not have marker: " + name);
				m.putVectorXZ(newTokenList, args[0].endsWith("f"));
			}
				break;

			case "vec3d":
			case "vec3f": {
				MapIndex index = getMapChecked(line, args, 2);
				String name = args[args.length - 1];
				Marker m = index.getMarker(name);
				if (m == null)
					throw new InputFileException(line, index.getMapName() + " does not have marker: " + name);
				m.putVector(newTokenList, args[0].endsWith("f"));
			}
				break;

			case "vec4d":
			case "vec4f": {
				MapIndex index = getMapChecked(line, args, 2);
				String name = args[args.length - 1];
				Marker m = index.getMarker(name);
				if (m == null)
					throw new InputFileException(line, index.getMapName() + " does not have marker: " + name);
				boolean useFloat = args[0].endsWith("f");
				m.putVector(newTokenList, useFloat);
				m.putAngle(newTokenList, useFloat);
			}
				break;

			case "posxd":
			case "posxf": {
				MapIndex index = getMapChecked(line, args, 2);
				String name = args[args.length - 1];
				Marker m = index.getMarker(name);
				if (m == null)
					throw new InputFileException(line, index.getMapName() + " does not have marker: " + name);
				m.putCoord(newTokenList, 0, args[0].endsWith("f"));
			}
				break;

			case "posyd":
			case "posyf": {
				MapIndex index = getMapChecked(line, args, 2);
				String name = args[args.length - 1];
				Marker m = index.getMarker(name);
				if (m == null)
					throw new InputFileException(line, index.getMapName() + " does not have marker: " + name);
				m.putCoord(newTokenList, 1, args[0].endsWith("f"));
			}
				break;

			case "poszd":
			case "poszf": {
				MapIndex index = getMapChecked(line, args, 2);
				String name = args[args.length - 1];
				Marker m = index.getMarker(name);
				if (m == null)
					throw new InputFileException(line, index.getMapName() + " does not have marker: " + name);
				m.putCoord(newTokenList, 2, args[0].endsWith("f"));
			}
				break;

			case "angle":
			case "anglef": {
				MapIndex index = getMapChecked(line, args, 2);
				String name = args[args.length - 1];
				Marker m = index.getMarker(name);
				if (m == null)
					throw new InputFileException(line, index.getMapName() + " does not have marker: " + name);
				m.putAngle(newTokenList, args[0].endsWith("f"));
			}
				break;

			case "entry":
			case "entryshort": {
				MapIndex index = getMapChecked(line, args, 2);
				String name = args[args.length - 1];
				Marker m = index.getMarker(name);
				if (m == null)
					throw new InputFileException(line, index.getMapName() + " does not have entry: " + name);
				if (args[0].endsWith("Short"))
					newTokenList.add(String.format("%04Xs", m.entryID));
				else
					newTokenList.add(String.format("%08X", m.entryID));
			}
				break;

			case "model":
			case "modelshort": {
				MapIndex index = getMapChecked(line, args, 2);
				String name = args[args.length - 1];
				int id = index.getModelID(name);
				if (id < 0)
					throw new InputFileException(line, index.getMapName() + " does not have model: " + name);
				if (args[0].endsWith("Short"))
					newTokenList.add(String.format("%04Xs", id));
				else
					newTokenList.add(String.format("%08X", id));
			}
				break;

			case "collider":
			case "collidershort": {
				MapIndex index = getMapChecked(line, args, 2);
				String name = args[args.length - 1];
				int id = index.getColliderID(name);
				if (id < 0)
					throw new InputFileException(line, index.getMapName() + " does not have collider: " + name);
				if (args[0].endsWith("Short"))
					newTokenList.add(String.format("%04Xs", id));
				else
					newTokenList.add(String.format("%08X", id));
			}
				break;

			case "zone":
			case "zoneshort": {
				MapIndex index = getMapChecked(line, args, 2);
				String name = args[args.length - 1];
				int id = index.getZoneID(name);
				if (id < 0)
					throw new InputFileException(line, index.getMapName() + " does not have zone: " + name);
				if (args[0].endsWith("Short"))
					newTokenList.add(String.format("%04Xs", id));
				else
					newTokenList.add(String.format("%08X", id));
			}
				break;
		}
	}

	private MapIndex getMapChecked(Line line, String[] args, int len)
	{
		MapIndex index = null;
		if (args.length == (len + 1)) {
			index = globalsDatabase.getMapIndex(args[1]);
			if (index == null)
				throw new InputFileException(line, "Could not find map: " + args[1]);
		}
		else if (args.length == len) {
			index = getCurrentMap();
			if (index == null) {
				if (primarySource != null)
					throw new InputFileException(line, "No map is bound for " + primarySource.getName());
				else
					throw new InputFileException(line, "No map is bound for " + getClass().getCanonicalName());
			}
		}
		else
			throw new InputFileException(line, "Incorrect args for " + args[0]);

		assert (index != null);
		return index;
	}

	public MapIndex getCurrentMap()
	{
		return null;
	}

	/**
	 * Checks expressions only available in map/battle scripts.
	 * If no match is found, nothing should be added to newTokenList.
	 */
	protected abstract void replaceExpression(Line line, String[] args, List<String> newTokenList);

	private final void calculatePatchLengths()
	{
		for (Struct s : patchedStructures) {
			for (Patch patch : s.patchList) {
				patch.length = s.getPatchLength(this, patch);

				// fixed length patches
				if (patch.maxLength > 0 && patch.maxLength != patch.length) {
					throw new InputFileException(patch.sourceLine,
						"Patch length mismatch (found %X, expected %X) for %s %s",
						patch.length, patch.maxLength, s.name, patch.offsetIdentifier);
				}
			}
		}
	}

	private final void calculatePatchPositions()
	{
		for (Struct s : patchedStructures) {
			if (s.fillMode)
				continue;

			for (Patch patch : s.patchList) {
				if (s.offsetMorphMap != null) {
					Entry<Integer, Integer> nearest = s.offsetMorphMap.floorEntry(patch.startingPos);
					patch.startingPos = nearest.getValue() + (patch.startingPos - nearest.getKey());
				}
			}
		}
	}

	private final void calculateStructLengths()
	{
		for (Struct s : patchedStructures) {
			if (s.fillMode)
				continue;

			if (s.hasFixedSize && !s.type.isArray) {
				for (Patch patch : s.patchList) {
					int patchEnd = patch.startingPos + patch.length;
					if (patchEnd > s.finalSize)
						throw new InputFileException(patch.sourceLine,
							"Patch from %X to %X exceeds fixed size %X for struct %s",
							patch.startingPos, patchEnd, s.finalSize, s.name);
				}
			}
			else {
				int fullSize = s.replaceExisting ? 0 : s.originalSize;

				for (Patch patch : s.patchList) {
					int patchEnd = patch.startingPos + patch.length;
					if (patchEnd > fullSize)
						fullSize = patchEnd;
				}

				fullSize = (fullSize + 3) & 0xFFFFFFFC; // zero pad to ensure 4 byte alignment

				if (s.replaceExisting && fullSize < s.originalSize) {
					s.shrunken = true;
					s.finalSize = fullSize;
				}

				if (fullSize > s.originalSize) {
					s.extended = true;
					s.finalSize = fullSize;
				}
			}
		}
	}

	// ASCENDING order
	private static final Comparator<Region> REGION_SIZE_COMPARATOR = (a, b) -> {
		if (a.length() == b.length())
			return a.compareTo(b);

		return (int) (a.length() - b.length());
	};

	// DESCENDING order
	private static final Comparator<Struct> STRUCT_SIZE_COMPARATOR = (a, b) -> b.finalSize - a.finalSize;

	// sets finalLocation for all structs in structMap
	// structs may be placed in deleted regions, or appended to the end of the section
	private final void determineLocalPlacement()
	{
		assert (overlayMode);
		// determine which structs need to be relocated
		List<Struct> relocatedStructList = new LinkedList<>();
		List<Region> freeRegions = new LinkedList<>();
		for (Struct struct : declaredStructs.values()) {
			// declared structs do not have an orignal location
			if (!struct.isDumped()) {
				relocatedStructList.add(struct);
				continue;
			}

			// extended structs need to be moved, leaving free space behind
			if (struct.extended) {
				relocatedStructList.add(struct);
				freeRegions.add(new Region(struct.originalFileOffset, struct.originalFileOffset + struct.originalSize));
				continue;
			}

			// shrunken structs leave free space at the end
			if (struct.shrunken) {
				int start = struct.originalFileOffset;
				freeRegions.add(new Region(start + struct.finalSize, start + struct.originalSize));
			}

			// all other structs can stay where they are
			struct.finalFileOffset = struct.originalFileOffset;
			struct.finalAddress = struct.originalAddress;
		}

		LinkedList<Region> emptyRegions = getEmptyRegions(freeRegions);

		if (relocatedStructList.isEmpty()) {
			int currentEnd = (int) emptyRegions.getLast().start;
			currentEnd = (currentEnd + 15) & -16; // 0x10 pad files

			end.finalFileOffset = currentEnd;
			end.finalAddress = toOverlayAddress(end.finalFileOffset);
			return;
		}

		Collections.sort(relocatedStructList, STRUCT_SIZE_COMPARATOR);

		Collections.sort(emptyRegions, REGION_SIZE_COMPARATOR);
		Iterator<Region> iter = emptyRegions.iterator();

		/*
		// ignore all empty regions smaller than the smallest struct
		int minStructSize = relocatedStructList.get(relocatedStructList.size() - 1).finalSize;
		while(iter.hasNext())
		{
			Region r = iter.next();
			if((r.length() < 0x10) || (r.length() < minStructSize))
			{
				iter.remove();
			}
		}
		 */

		// starting with the largest struct, find the smallest place where it can fit
		// if data is appended to the end, keep track of the terminal data offset
		for (Struct struct : relocatedStructList) {
			int alignReq = struct.isTypeOf(ConstDoubleT) ? 8 : 4;

			boolean foundFit = false;
			iter = emptyRegions.iterator();

			while (iter.hasNext() && !foundFit) {
				Region r = iter.next();

				int alignedStart = ((int) r.start + (alignReq - 1)) & -alignReq;
				int alignedEnd = (int) r.end & -alignReq;
				int length = alignedEnd - alignedStart;

				if (struct.finalSize <= length) {
					int structEnd = alignedStart + struct.finalSize;
					struct.finalFileOffset = alignedStart;
					struct.finalAddress = toOverlayAddress(struct.finalFileOffset);
					iter.remove();

					if (r.start < alignedStart)
						emptyRegions.add(new Region(r.start, alignedStart));

					if (structEnd < r.end)
						emptyRegions.add(new Region(structEnd, r.end));

					foundFit = true;
				}
			}

			if (!foundFit)
				throw new InputFileException(primarySource, "%s %n%s", "Ran out of room to place new data structures!", struct.name);

			Logger.logf("%s will be placed at %08X (length = %X bytes)", struct.name, struct.finalAddress, struct.finalSize);
		}

		Collections.sort(emptyRegions);

		int currentEnd = (int) emptyRegions.getLast().start;
		currentEnd = (currentEnd + 15) & -16; // 0x10 pad files

		end.finalFileOffset = currentEnd;
		end.finalAddress = toOverlayAddress(end.finalFileOffset);
		end.finalSize = toOverlayOffset(addressLimit) - currentEnd;
		return;
	}

	private final LinkedList<Region> getEmptyRegions(List<Region> relocatedRegionList)
	{
		List<Region> emptyRegions = new LinkedList<>();
		emptyRegions.addAll(relocatedRegionList);
		emptyRegions.addAll(deletedRegions);
		emptyRegions.addAll(paddingRegions);
		emptyRegions.add(new Region(end.originalFileOffset, toOverlayOffset(addressLimit)));
		Collections.sort(emptyRegions);

		LinkedList<Region> mergedRegions = new LinkedList<>();
		if (emptyRegions.size() == 0)
			return mergedRegions;

		// ensure no two empty regions overlap
		for (int i = 1; i < emptyRegions.size(); i++) {
			Region a = emptyRegions.get(i - 1);
			Region b = emptyRegions.get(i);
			if (Region.overlap(a, b) > 0) {
				throw new InputFileException(primarySource,
					"Found overlapping empty regions: %X to %X and %X to %X",
					baseAddress + a.start,
					baseAddress + a.end,
					baseAddress + b.start,
					baseAddress + b.end);
			}
		}

		Region current = emptyRegions.get(0);
		for (int i = 1; i < emptyRegions.size(); i++) {
			Region r = emptyRegions.get(i);

			if (Region.adjacent(current, r))
				current = Region.merge(current, r);
			else {
				mergedRegions.add(current);
				current = r;
			}
		}
		mergedRegions.add(current);

		Logger.logf("Merged %d regions to %d", emptyRegions.size(), mergedRegions.size());

		if (reservedRegions.isEmpty())
			return mergedRegions;

		// remove reserved regions from empty space listing

		Collections.sort(reservedRegions);

		for (Region r : reservedRegions) {
			ListIterator<Region> iter = mergedRegions.listIterator();

			while (iter.hasNext()) {
				Region m = iter.next();

				if (m.length() == 0 || !Region.overlaps(r, m))
					continue;

				if (m.start <= r.start && m.end >= r.end) //
				{
					iter.add(new Region(r.end, m.end));
					m.end = r.start;
				}
				else if (r.start <= m.start && r.end >= m.end)
					m.end = m.start; // make size = 0
				else if (r.start <= m.start)
					m.start = r.end;
				else
					m.end = r.start;
			}
		}

		LinkedList<Region> finalRegions = new LinkedList<>();
		for (Region r : mergedRegions) {
			if (r.length() > 0)
				finalRegions.add(r);
		}

		return finalRegions;
	}

	private final void determineGlobalPlacement()
	{
		assert (!overlayMode);

		RomPatcher rp = globalsDatabase.getRomPatcher();

		for (Struct str : declaredStructs.values()) {
			if (str.fillMode)
				continue;

			int startOffset = rp.nextAlignedOffset();
			str.finalFileOffset = startOffset;
			str.finalAddress = rp.toAddress(startOffset);
			rp.tailReserveWithPadding(str.finalSize);

			if (str.exported)
				globalsDatabase.setGlobalPointer(str.name, str.finalAddress);

			Logger.logf("%s will be placed at %08X (%X) (length = %X bytes)", str.name, str.finalAddress, str.finalFileOffset, str.finalSize);
		}
	}

	private final void replacePointerNames(Iterable<Struct> structs)
	{
		for (Struct str : structs) {
			for (Patch patch : str.patchList) {
				for (Line line : patch.lines) {
					for (Token t : line.tokens) {
						if (DataUtils.isPointerFmt(t.str))
							t.str = getAddress(line, t.str);
					}
				}
			}
		}
	}

	/**
	 * Must be called before linking local structs
	 */
	private final void findFunctionLabels(Iterable<Struct> structs)
	{
		for (Struct str : structs) {
			if (!str.isTypeOf(FunctionT))
				continue;

			for (Patch patch : str.patchList) {
				List<Line> dummyInstructions = new LinkedList<>();

				for (Line line : patch.lines) {
					StringBuilder sb = new StringBuilder();
					for (Token t : line.tokens) {
						if (DataUtils.isPointerFmt(t.str))
							sb.append(0x80ABCDEF); // dummy pointer
						else
							sb.append(t.str + " ");
					}

					Line newLine = line.createLine(sb.toString());
					newLine.tokenize();
					dummyInstructions.add(newLine);
				}

				dummyInstructions = PseudoInstruction.removeAll(dummyInstructions);
				str.labelMap = MIPS.getLabelMap(dummyInstructions);
			}
		}
	}

	/**
	 * Assemble all function patches
	 */
	private final void assembleFunctions(Iterable<Struct> structs)
	{
		for (Struct str : structs) {
			if (!str.isTypeOf(FunctionT))
				continue;

			for (Patch patch : str.patchList) {
				List<Line> cleaned = PseudoInstruction.removeAll(patch.lines);
				List<Line> assembled = MIPS.assemble(cleaned);
				for (Line line : assembled)
					line.tokenize();

				patch.lines.clear();
				patch.lines.addAll(assembled);
			}
		}
	}

	private final void assembleDisplayLists(Iterable<Struct> structs)
	{
		for (Struct str : structs) {
			if (!str.isTypeOf(DisplayListT))
				continue;

			for (Patch patch : str.patchList)
				DisplayList.encode(patch.lines);
		}
	}

	private final ByteBuffer prepareBuffer()
	{
		ByteBuffer patchedBuffer = ByteBuffer.allocateDirect(end.finalFileOffset);
		byte[] originalBytes = new byte[fileBuffer.limit()];

		fileBuffer.position(0);
		fileBuffer.get(originalBytes);
		if (patchedBuffer.limit() < originalBytes.length)
			patchedBuffer.put(originalBytes, 0, patchedBuffer.limit());
		else
			patchedBuffer.put(originalBytes);

		return patchedBuffer;
	}

	/**
	 * Relocated structures may be referenced by UNPATCHED functions, with the pointers
	 * to those structures split among two or more instructions (ex: LUI 8024, ADDIU 0320).
	 * This method "digs out" those pointers and fixes them.
	 * Each function in the structMap is disassembled and examined for local pointers,
	 * which are then replaced and reassembled as necessary.
	 */
	private final void fixPointersInFunctions(ByteBuffer patchedBuffer)
	{
		for (Struct str : declaredStructs.values()) {
			if (!str.isDumped() || str.deleted)
				continue;

			if (str.isTypeOf(FunctionT)) {
				// get original function from buffer
				patchedBuffer.position(str.originalFileOffset);
				List<String> oldAsmList = new ArrayList<>();

				for (int i = 0; i < str.originalSize; i += 4)
					oldAsmList.add(MIPS.disassemble(patchedBuffer.getInt()));

				// add PIs to 'dig' pointers out
				oldAsmList = PseudoInstruction.addAll(oldAsmList);

				// replace pointers, convert to lines, remove PIs
				DummySource src = new DummySource(str.name + "_Fixup");
				List<Line> fixedAsmList = new ArrayList<>();
				int lineNum = 1;

				for (String ins : oldAsmList) {
					ins = FunctionIgnorePattern.matcher(ins).replaceAll(" ");
					String[] tokens = ins.split("\\s+");
					StringBuilder sb = new StringBuilder();
					for (String s : tokens) {
						try {
							int v = (int) Long.parseLong(s, 16);
							if (v >= baseAddress && v < addressLimit)
								s = String.format("%08X", fixPointer(v));
						}
						catch (NumberFormatException e) {}

						sb.append(s + " ");
					}

					Line newLine = new Line(src, lineNum++, sb.toString());
					newLine.tokenize();
					fixedAsmList.add(newLine);
				}
				fixedAsmList = PseudoInstruction.removeAll(fixedAsmList);

				// write fixed function back to buffer
				patchedBuffer.position(str.originalFileOffset);
				for (Line line : MIPS.assemble(fixedAsmList)) {
					try {
						patchedBuffer.putInt((int) Long.parseLong(line.str, 16));
					}
					catch (NumberFormatException e) {
						throw new InputFileException(line, "Could not parse integer: %s", line.str);
					}
				}
			}
		}
	}

	/**
	 * Iterates through a ByteBuffer looking for pointers to relocated structures,
	 * replacing them with new addresses.
	 */
	private final void fixPointers(ByteBuffer bb)
	{
		// check for missed pointers in the original data
		bb.position(0);
		while (bb.hasRemaining()) {
			int v = bb.getInt();
			if (v >= baseAddress && v < addressLimit) {
				int newAddr = fixPointer(v);
				bb.position(bb.position() - 4);
				bb.putInt(newAddr);
			}
		}
	}

	/**
	 * Checks whether a pointer has been relocated and find the new location.
	 * @param addr - original pointer address
	 * @return the updated pointer for relocated structs, or the original pointer for others
	 */
	private final int fixPointer(int addr)
	{
		if (addr < baseAddress || addr >= addressLimit)
			return addr;

		int offset = toOverlayOffset(addr);

		// pointer to reserved region, leave it alone!
		for (Region r : reservedRegions) {
			if (r.contains(offset))
				return addr;
		}

		Entry<Integer, Struct> entry = originalOffsetTree.floorEntry(offset);
		Struct struct = entry.getValue();

		// pointer to unknown area, don't touch it!
		if (struct == null)
			return addr;

		// struct has not moved, pointer into it does not need to be changed
		if (struct.originalAddress == struct.finalAddress)
			return addr;

		int structOffset = addr - struct.originalAddress;
		int newAddr = struct.finalAddress + structOffset;

		if (offset != 0)
			Logger.logf("Changing pointer %08X to %08X (%s[%X])", addr, newAddr, struct.name, structOffset);
		else
			Logger.logf("Changing pointer %08X to %08X (%s)", addr, newAddr, struct.name);

		return newAddr;
	}

	private final void applyPatches(ByteBuffer patchedBuffer)
	{
		// copy data from patchBuffer and apply patches
		for (Struct str : patchedStructures) {
			str.patchedBuffer = ByteBuffer.allocateDirect(str.finalSize);

			if (str.fillMode) {
				applyFillPattern(str);
				continue;
			}

			// edit existing struct
			if (str.isDumped() && !str.replaceExisting) {
				byte[] structBytes = new byte[str.originalSize];
				patchedBuffer.position(str.originalFileOffset);
				patchedBuffer.get(structBytes, 0, str.originalSize);
				str.patchedBuffer.put(structBytes);
			}

			for (Patch patch : str.patchList) {
				str.patchedBuffer.position(patch.startingPos);

				for (Line line : patch.lines) {
					for (int i = 0; i < line.numTokens(); i++) {
						try {
							DataUtils.addToBuffer(str.patchedBuffer, line.getString(i));
						}
						catch (InvalidInputException e) {
							throw new InputFileException(line, "Unable to parse token \'%s\' from patch %s %n%s",
								line.getString(i), str.name, line.trimmedInput());
						}
					}
				}
			}
		}

		// zero out all deleted regions -- note: some may already be truncated or exist beyond the new overlay end position!
		for (Region r : deletedRegions) {
			if (patchedBuffer.capacity() < r.start)
				continue;
			patchedBuffer.position((int) r.start);
			int endPos = Math.min((int) r.end, patchedBuffer.capacity());
			byte[] zeros = new byte[endPos - (int) r.start];
			patchedBuffer.put(zeros);
		}

		// write new data to patchBuffer
		for (Struct str : patchedStructures) {
			str.patchedBuffer.rewind();
			patchedBuffer.position(str.finalFileOffset);
			patchedBuffer.put(str.patchedBuffer);
		}
	}

	private void applyFillPattern(Struct struct)
	{
		Patch patch = struct.patchList.get(0);

		int fillPatternLength = 0;
		for (Line line : patch.lines)
			for (Token t : line.tokens) {
				fillPatternLength += DataUtils.getSize(t.str);
			}

		ByteBuffer bb = ByteBuffer.allocateDirect(fillPatternLength);
		for (Line line : patch.lines)
			for (Token t : line.tokens) {
				try {
					DataUtils.addToBuffer(bb, t.str);
				}
				catch (InvalidInputException e) {
					throw new InputFileException(t, "Invalid token when writing fill: " + t.str);
				}
			}

		struct.patchedBuffer.position(0);
		while (struct.patchedBuffer.hasRemaining()) {
			if (!bb.hasRemaining())
				bb.rewind();

			struct.patchedBuffer.put(bb.get());
		}
	}

	private final void checkPatchedScriptSyntax(ByteBuffer patchedBuffer, boolean usingPackedOpcodes)
	{
		for (Struct str : patchedStructures) {
			if (!str.isTypeOf(ScriptT))
				continue;

			if (str.validateScriptSyntax) {
				try {
					Script.checkSyntax(this, patchedBuffer, str, usingPackedOpcodes);
				}
				catch (InvalidInputException e) {
					throw new InputFileException(primarySource, e.getMessage());
				}
			}
		}
	}

	private final void writeBinaryOutput(ByteBuffer patchedBuffer, File outFile) throws IOException
	{
		byte[] outBytes = new byte[patchedBuffer.limit()];
		patchedBuffer.rewind();
		patchedBuffer.get(outBytes);
		FileUtils.writeByteArrayToFile(outFile, outBytes);
	}

	private final String getAddress(Line line, String structName)
	{
		int address;

		int offset = 0;
		Matcher m = PointerNameOffsetPatten.matcher(structName);
		if (m.matches()) {
			structName = m.group(1);
			String offsetName = m.group(2);

			CaseInsensitiveMap<Integer> fieldMap = null;
			Struct struct = declaredStructs.get(structName);
			if (struct != null && struct.labelMap != null)
				fieldMap = struct.labelMap;

			try {
				offset = ConstMath.parse(this, fieldMap, offsetName);
			}
			catch (InvalidInputException e) {
				throw new InputFileException(line, e);
			}
		}

		if (globalsDatabase.hasGlobalPointer(structName)) {
			address = globalsDatabase.getGlobalPointerAddress(structName) + offset;
		}
		else {
			Struct struct = declaredStructs.get(structName);

			if (struct == null)
				throw new InputFileException(line, "Unrecognized structure name during finalize: " + structName);

			if (struct.deleted) {
				if (struct.forceDelete) {
					Logger.logWarning("Overwriting pointer to deleted struct " + struct.name);
					address = 0;
				}
				else
					throw new InputFileException(line, "Found deleted structure name during finalize: " + structName);
			}
			else {
				address = struct.finalAddress + offset;
			}
		}

		return String.format("%08X", address);
	}

	public Integer getSizeOf(String typeName)
	{
		StructType type = typeMap.get(typeName);
		if (type == null)
			return null;
		return type.getSizeOf();
	}
}

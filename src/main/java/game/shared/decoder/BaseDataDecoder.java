package game.shared.decoder;

import static app.Directories.DATABASE_HINTS;
import static game.shared.StructTypes.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import app.Environment;
import app.StarRodException;
import app.config.Options;
import app.input.IOUtils;
import app.input.InputFileException;
import game.ROM.LibScope;
import game.shared.ProjectDatabase;
import game.shared.ProjectDatabase.ConstEnum;
import game.shared.SyntaxConstants;
import game.shared.decoder.Pointer.Origin;
import game.shared.lib.CType.Primitive;
import game.shared.lib.LibEntry;
import game.shared.lib.LibEntry.EntryType;
import game.shared.lib.LibEntry.LibParam;
import game.shared.lib.LibEntry.LibParamList;
import game.shared.lib.LibEntry.LibType;
import game.shared.lib.Library;
import game.shared.struct.StructType;
import game.shared.struct.function.Function;
import game.shared.struct.function.FunctionScanResults;
import game.shared.struct.function.JumpTable;
import game.shared.struct.function.JumpTarget;
import game.shared.struct.script.Script;
import game.shared.struct.script.Script.ScriptLine;
import game.string.PMString;
import game.string.StringDumper;
import game.shared.struct.script.ScriptVariable;
import game.texture.Tile;
import game.texture.TileFormat;
import patcher.Region;
import util.Logger;

public abstract class BaseDataDecoder
{
	/**
	 * static struct definitions
	 */
	static {
		UnknownT.scanner = (decoder, ptr, fileBuf) -> {
			throw new IllegalArgumentException("Unable to scan unknown data structure.");
		};
		UnknownT.printer = (decoder, ptr, fileBuf, pw) -> {
			decoder.printHex(ptr, fileBuf, pw, 8);
		};

		ScriptT.scanner = (decoder, ptr, fileBuf) -> {
			decoder.scanScript(ptr, fileBuf);
		};
		ScriptT.printer = (decoder, ptr, fileBuf, pw) -> {
			Script.print(decoder, ptr, fileBuf, pw);
		};

		FunctionT.scanner = (decoder, ptr, fileBuf) -> {
			decoder.scanFunction(ptr, fileBuf);
		};
		FunctionT.printer = (decoder, ptr, fileBuf, pw) -> {
			Function.print(decoder, ptr, decoder.jumpTargetMap, decoder.jumpTableTargetMap, decoder.library, fileBuf, pw);
		};

		JumpTableT.scanner = (decoder, ptr, fileBuf) -> {
			decoder.scanJumpTable(ptr, fileBuf);
		};
		JumpTableT.printer = (decoder, ptr, fileBuf, pw) -> {
			decoder.printHex(ptr, fileBuf, pw, 4);
		};

		// These just use default scanning/printing implementation.
		/*
		ByteTableT.scanner = (decoder,ptr,fileBuf) -> {};
		ByteTableT.printer = (decoder,ptr,fileBuf,pw) -> { decoder.printHex(ptr, fileBuf, pw, 8); };

		ShortTableT.scanner = (decoder,ptr,fileBuf) -> {};
		ShortTableT.printer = (decoder,ptr,fileBuf,pw) -> { decoder.printHex(ptr, fileBuf, pw, 8); };

		IntTableT.scanner = (decoder,ptr,fileBuf) -> {};
		IntTableT.printer =	(decoder,ptr,fileBuf,pw) -> { decoder.printHex(ptr, fileBuf, pw, 8); };
		 */

		FloatTableT.scanner = (decoder, ptr, fileBuf) -> {};
		FloatTableT.printer = (decoder, ptr, fileBuf, pw) -> {
			int length = (ptr.getSize() / 4);
			for (int i = 0; i < length;) {
				float f = fileBuf.getFloat();
				if ((++i % 8) == 0 || i >= length)
					pw.println(f);
				else
					pw.printf("%-8s ", f + "");
			}
		};

		StringTableT.scanner = (decoder, ptr, fileBuf) -> {
			if (!ptr.hasKnownSize())
				throw new StarRodException("Can't scan string table of unknown length!");

			int num = ptr.getSize() / 4;
			for (int i = 0; i < num; i++) {
				int v = fileBuf.getInt();
				if (decoder.isLocalAddress(v))
					decoder.tryEnqueueAsChild(ptr, v, StringT);
			}
		};
		StringTableT.printer = (decoder, ptr, fileBuf, pw) -> {
			int length = (ptr.getSize() / 4);
			for (int i = 0; i < length; i++) {
				int v = fileBuf.getInt();
				decoder.printScriptWord(pw, v);
				pw.println(decoder.getStringComment(v));
			}
		};

		DataTableT.printer = (decoder, ptr, fileBuf, pw) -> {
			decoder.printHex(ptr, fileBuf, pw, 8, true);
		};

		ConstDoubleT.scanner = (decoder, ptr, fileBuf) -> {
			fileBuf.getDouble();
		};
		ConstDoubleT.printer = (decoder, ptr, fileBuf, pw) -> {
			pw.printf("%fd%n", fileBuf.getDouble());
		};
	}

	/**
	 * instance variables
	 */

	protected boolean doSanityChecks = true;

	protected int startAddress = -1;
	protected int endAddress = -1;
	protected int addressLimit = -1;

	protected int startOffset = -1;
	protected int endOffset = -1;

	protected boolean annotateIndexInfo = false;

	protected final Library library;
	protected final TreeMap<Integer, Pointer> localPointerMap;

	// keep track of which pointers have yet to be scanned, and which already have been
	protected final LinkedList<Integer> pointerQueue;
	protected final HashSet<Integer> finishedPointers;

	private File hintFile;
	private TreeMap<Integer, Hint> hintMap;

	// required for function structs
	protected final TreeMap<Integer, JumpTarget> jumpTargetMap;
	protected final TreeMap<Integer, JumpTarget> jumpTableTargetMap;

	protected boolean dumpEmbeddedImages = true;
	protected final HashSet<EmbeddedImage> embeddedImages;

	private final List<Region> paddingRegions;
	private final List<Region> missingRegions;

	// track success rate for output
	public int unknownPointers = 0;
	public int missingSections = 0;

	private final LibScope scope;

	// the is the struct type that can act as the ancestor of various scripts/functions/etc.
	// usually Actors for battle data and NPCs for world data
	private final StructType ancestorType;

	public final boolean printRequiredBy;
	public final boolean printLineOffsets;
	public final boolean useTabIndents;
	public final boolean useTabSpacing;
	public final boolean newlineOpenBrace;
	public final boolean indentPrintedData;

	protected BaseDataDecoder(LibScope scope, StructType ancestorType, Library library)
	{
		this.scope = scope;
		this.ancestorType = ancestorType;
		this.library = library;

		// clear type counters
		for (StructType s : scope.typeMap)
			s.count = 0;

		localPointerMap = new TreeMap<>();
		pointerQueue = new LinkedList<>();
		finishedPointers = new HashSet<>();

		jumpTargetMap = new TreeMap<>();
		jumpTableTargetMap = new TreeMap<>();

		embeddedImages = new HashSet<>();

		paddingRegions = new LinkedList<>();
		missingRegions = new LinkedList<>();

		printRequiredBy = Environment.mainConfig.getBoolean(Options.PrintRequiredBy);
		printLineOffsets = Environment.mainConfig.getBoolean(Options.PrintLineOffsets);
		useTabIndents = Environment.mainConfig.getBoolean(Options.UseTabIndents);
		useTabSpacing = Environment.mainConfig.getBoolean(Options.UseTabSpacing);
		newlineOpenBrace = Environment.mainConfig.getBoolean(Options.NewlineOpenBrace);
		indentPrintedData = Environment.mainConfig.getBoolean(Options.IndentPrintedData);
	}

	public Library getLibrary()
	{
		return library;
	}

	public LibScope getScope()
	{
		return scope;
	}

	/**
	 * Iterates through fileBuffer from startOffset to endOffset,
	 * adding local addresses to the list of known pointers
	 * @param fileBuffer
	 */
	protected void findLocalPointers(ByteBuffer fileBuffer)
	{
		fileBuffer.position(startOffset);
		while (fileBuffer.position() < endOffset) {
			int v = fileBuffer.getInt();
			if (isLocalAddress(v))
				addPointer(v);
		}
	}

	protected void decode(ByteBuffer fileBuffer) throws IOException
	{
		readHints();
		for (Hint h : hintMap.values())
			enqueueAsRoot(h);

		scanPointerQueue(fileBuffer);

		tryHeuristics(fileBuffer);

		growGreedyStructs();
		mergeAdjacentStructs(VertexTableT);
		purgeSubordinatePointers();

		// aggressive function search finding division between code/data
		for (int i = 0; i < 3; i++) {
			int lastFunctionAddr = Integer.MIN_VALUE;
			int firstNonFunctionAddr = Integer.MAX_VALUE;

			List<OccupiedRegion> regions = getSpacePartition();
			for (OccupiedRegion r : regions) {
				if (r.ptr != null && r.ptr.getType().isTypeOf(FunctionT)) {
					if (r.start >= lastFunctionAddr)
						lastFunctionAddr = (int) r.start;
				}
				else if (r.ptr == null || r.ptr.getType() != UnknownT) {
					if (r.start < firstNonFunctionAddr)
						firstNonFunctionAddr = (int) r.start;
				}
			}

			if (lastFunctionAddr > firstNonFunctionAddr)
				break;

			for (OccupiedRegion r : regions) {
				if (r.start > lastFunctionAddr)
					break;

				if (r.ptr == null || r.ptr.getType() == UnknownT) {
					fileBuffer.position(toOffset((int) r.start));
					while (fileBuffer.hasRemaining() && fileBuffer.getInt() == 0)
						;
					enqueueAsRoot(toAddress(fileBuffer.position() - 4), FunctionT, Origin.HEURISTIC);
				}
			}

			scanPointerQueue(fileBuffer);

			growGreedyStructs();
			purgeSubordinatePointers();
		}
	}

	public abstract void scanScript(Pointer ptr, ByteBuffer fileBuffer);

	protected void setAddressRange(int start, int end, int limit)
	{
		startAddress = start;
		endAddress = end;
		addressLimit = limit;
		assert (end >= start) : String.format("Data end %08X comes before start %08X", end, start);
		assert (limit >= end) : String.format("Data end %08X exceeds limit %08X", end, limit);
	}

	protected void setOffsetRange(int start, int end)
	{
		startOffset = start;
		endOffset = end;
		assert (end >= start);
	}

	public int getStartAddress()
	{
		return startAddress;
	}

	// returns the 'source' -- usually a map name or battle section
	public abstract String getSourceName();

	// convert data offset to an address
	public final int toAddress(int offset)
	{
		return startAddress + (offset - startOffset);
	}

	// convert address to data offset
	public final int toOffset(int address)
	{
		return startOffset + (address - startAddress);
	}

	// does a given address fall within the source data section?
	public final boolean isLocalAddress(int address)
	{
		return (address >= startAddress && address < endAddress);
	}

	// returns the pointer object associated with an address, or null if none exists
	public final Pointer getPointer(int address)
	{
		return localPointerMap.get(address);
	}

	/**
	 * Should only be used to add 'entry point' structures to the queue, not during the
	 * recursive struct search. Only enqueueAsChild should invoke this during that time.
	 * @param address
	 * @param type
	 */
	private final void enqueuePointer(int address, StructType type)
	{
		Pointer ptr = getPointer(address);

		// The same pointer may be submitted multiple times to the pointer queue in any order,
		// with or without a type given. Therefore, (1) we must allow previously-scanned unknown
		// structs to be re-scanned if a concrete struct type is specified and (2) only set the
		// type if it is known, preventing UnknownT from overwriting a concrete struct type.

		if (finishedPointers.contains(address)) {
			// don't override anything set by a hint
			if (ptr.typeSetByHint)
				return;

			// already scanned this pointer as some struct
			if (ptr.getType() != UnknownT)
				return;

			// don't bother scanning unknown struct as unknown again
			if (type == UnknownT)
				return;
		}

		if (type == UnknownT)
			pointerQueue.addFirst(address);
		else {
			pointerQueue.addLast(address);
			ptr.setType(type);
		}
	}

	/**
	 * Adds a known root struct to the recursive search queue iff address is local.
	 */
	protected final void tryEnqueueAsRoot(int address, StructType type, Origin origin)
	{
		if (isLocalAddress(address))
			enqueueAsRoot(address, type, origin);
	}

	/**
	 * Adds a known root struct to the recursive search queue iff address is local.
	 */
	protected final void tryEnqueueAsRoot(int address, StructType type, Origin origin, String forcedName)
	{
		if (isLocalAddress(address))
			enqueueAsRoot(address, type, origin).forceName(forcedName);
	}

	/**
	 * Adds a known root struct to the recursive search queue.
	 * Assumes address is local.
	 */
	protected final Pointer enqueueAsRoot(int address, StructType type, Origin origin)
	{
		Pointer ptr = addPointer(address);
		ptr.origin = origin;
		ptr.root = true;
		enqueuePointer(address, type);

		return ptr;
	}

	protected final Pointer enqueueAsRoot(Hint h)
	{
		Pointer ptr;

		if (localPointerMap.containsKey(h.address)) {
			ptr = localPointerMap.get(h.address);
		}
		else {
			ptr = new Pointer(h);

			if (h.hasType()) {
				ptr.origin = Origin.HINT;
				ptr.root = true;
			}

			localPointerMap.put(h.address, ptr);
		}

		ptr.useHints(h);
		enqueuePointer(h.address, ptr.getType());

		return ptr;
	}

	/**
	 * Adds a known root struct to the recursive search queue.
	 * Assumes address is local.
	 */
	protected final Pointer enqueueAsRoot(int address, StructType type, Origin origin, String forcedName)
	{
		Pointer ptr = addPointer(address);
		ptr.origin = origin;
		ptr.root = true;
		ptr.forceName(forcedName);
		enqueuePointer(address, type);

		return ptr;
	}

	public final Pointer tryEnqueueAsChild(Pointer parent, int address, StructType child, String name)
	{
		Pointer ptr = tryEnqueueAsChild(parent, address, child);
		if (ptr != null)
			ptr.setDescriptor(name);
		return ptr;
	}

	public final Pointer tryEnqueueAsChild(Pointer parent, int address, StructType child)
	{
		if (isLocalAddress(address))
			return enqueueAsChild(parent, address, child);
		else
			return null;
	}

	public final Pointer enqueueAsChild(Pointer parent, int address, StructType child, String name)
	{
		Pointer ptr = enqueueAsChild(parent, address, child);
		if (ptr != null)
			ptr.setDescriptor(name);
		return ptr;
	}

	public final Pointer enqueueAsChild(Pointer parent, int address, StructType child)
	{
		Pointer ptr = getPointer(address);
		parent.addUniqueChild(ptr);
		enqueuePointer(address, child);

		ptr.mapName = parent.mapName;

		updateStructHierarchy(parent, ptr);

		switch (ptr.origin) {
			case UNIDENTIFIED:
				// propagate origin for unidentified pointers
				ptr.origin = parent.origin;
				break;

			case HINT:
				// notify about unnecessary hints
				if (ptr.root)
					Logger.logf("Structure at %08X may not need to be in hint file.", ptr.address);
				break;

			default:
				// do nothing
		}

		return ptr;
	}

	public final boolean addImage(Pointer parent, ByteBuffer fileBuffer, TileFormat fmt, int raster, int palette, int width, int height)
	{
		if (!isLocalAddress(raster))
			return false;

		if (fmt == null) {
			Logger.logfWarning("Image at %08X has unknown format!", raster);
			return false;
		}

		Tile tile = new Tile(fmt, height, width);

		Pointer img = tryEnqueueAsChild(parent, raster, IntTableT);
		img.setSize(fmt.getNumBytes(width, height));

		Pointer pal = null;

		if (fmt.type == TileFormat.TYPE_CI) {
			if (!isLocalAddress(palette))
				return false;

			pal = tryEnqueueAsChild(parent, palette, IntTableT);
			pal.setSize((fmt == TileFormat.CI_4) ? 0x20 : 0x200);
		}

		embeddedImages.add(new EmbeddedImage(tile, raster, palette));
		return true;
	}

	protected Pointer addPointer(int address)
	{
		if (!localPointerMap.containsKey(address)) {
			Pointer ptr = new Pointer(address);
			localPointerMap.put(address, ptr);
		}
		return localPointerMap.get(address);
	}

	private final void updateStructHierarchy(Pointer parent, Pointer child)
	{
		if (parent.getType() == ancestorType)
			child.ancestors.add(parent);

		if (!parent.ancestors.isEmpty()) {
			for (Pointer ancestorPtr : parent.ancestors)
				child.ancestors.add(ancestorPtr);
		}
	}

	protected final void scanPointerQueue(ByteBuffer fileBuffer) throws IOException
	{
		while (!pointerQueue.isEmpty()) {
			int pointerAddr = pointerQueue.poll();

			if (!finishedPointers.contains(pointerAddr)) // prevent infinite recursion
			{
				Pointer ptr = getPointer(pointerAddr);

				// this can happen in functions a pointer is the target of a jump table
				// entry and also a branch target.
				if (ptr == null) {
					if (jumpTableTargetMap.containsKey(pointerAddr))
						finishedPointers.add(pointerAddr);
					else
						Logger.logfWarning("Decoder pointer %08X = NULL, not found in jump table targets!", pointerAddr);
					continue;
				}

				if (ptr.isTypeUnknown())
					continue;

				int start = toOffset(pointerAddr);
				fileBuffer.position(start);

				finishedPointers.add(pointerAddr);
				scanPointer(fileBuffer, ptr);

				int end = fileBuffer.position();
				ptr.setSize(end - start);
			}
		}
	}

	protected void scanPointer(ByteBuffer fileBuffer, Pointer ptr)
	{
		StructType type = ptr.getType();

		type.count++;

		if (type.isUnique && type.count > 1)
			throw new RuntimeException("Found multiple copies of unique struct: " + ptr.getPointerName());

		type.scan(this, ptr, fileBuffer);
	}

	protected ArrayList<String[]> scanFunction(Pointer ptr, ByteBuffer fileBuffer)
	{
		FunctionScanResults results = Function.scan(this, fileBuffer, ptr.address);
		int endPosition = fileBuffer.position();

		for (int addr : results.unknownChildPointers) {
			// could be expanded to do any type inference, limited to functions only for now
			boolean isFunction = false;

			fileBuffer.position(toOffset(addr));
			if (fileBuffer.remaining() >= 8) {
				int A = fileBuffer.getInt();
				int B = fileBuffer.getInt();
				isFunction = ((A & 0xFFFF8000) == 0x27BD8000) || (A == 0x03E00008 && B == 0x00000000);
			}

			addPointer(addr);
			enqueueAsChild(ptr, addr, isFunction ? FunctionT : UnknownT);
		}

		for (int addr : results.localFunctionCalls) {
			addPointer(addr);
			enqueueAsChild(ptr, addr, FunctionT);
		}

		for (JumpTable table : results.jumpTables) {
			addPointer(table.baseAddress);
			enqueueAsChild(ptr, table.baseAddress, JumpTableT).listLength = table.numEntries;
		}

		for (JumpTarget target : results.branchTargets) {
			jumpTargetMap.put(target.targetAddr, target);
		}

		for (JumpTarget target : results.jumpTableTargets) {
			if (localPointerMap.containsKey(target.targetAddr))
				localPointerMap.remove(target.targetAddr);

			jumpTableTargetMap.put(target.targetAddr, target);
		}

		for (int addr : results.byteTables) {
			addPointer(addr);
			enqueueAsChild(ptr, addr, ByteTableT);
		}

		for (int addr : results.shortTables) {
			addPointer(addr);
			enqueueAsChild(ptr, addr, ShortTableT);
		}

		for (int addr : results.intTables) {
			addPointer(addr);
			enqueueAsChild(ptr, addr, IntTableT);
		}

		for (int addr : results.floatTables) {
			addPointer(addr);
			enqueueAsChild(ptr, addr, FloatTableT);
		}

		for (int addr : results.constDoubles) {
			addPointer(addr);
			enqueueAsChild(ptr, addr, ConstDoubleT);
		}

		fileBuffer.position(endPosition);
		return results.code;
	}

	// add a reference to some data referenced by a function
	// does not add if the reference points inside an existing struct
	private void tryAddingFunctionData(Pointer parent, int addr, StructType type)
	{
		Entry<Integer, Pointer> outer = localPointerMap.floorEntry(addr);
		if (outer != null) {
			Pointer ptr = outer.getValue();
			if (ptr.hasKnownSize() && (addr - ptr.address) < ptr.getSize())
				return;
		}

		addPointer(addr);
		enqueueAsChild(parent, addr, type);
	}

	protected void scanJumpTable(Pointer ptr, ByteBuffer fileBuffer)
	{
		for (int i = 0; i < ptr.listLength; i++) {
			fileBuffer.getInt();
		}
	}

	public boolean shouldFunctionsRemoveJumps()
	{
		return true;
	}

	protected final ArrayList<Integer> getSortedLocalPointerList()
	{
		ArrayList<Integer> pointerList = new ArrayList<>(localPointerMap.size());
		for (Entry<Integer, Pointer> e : localPointerMap.entrySet()) {
			pointerList.add(e.getKey());
		}

		return pointerList;
	}

	protected class OccupiedRegion extends Region
	{
		public final Pointer ptr;

		public OccupiedRegion(int start, int end, Pointer ptr)
		{
			super(start, end);
			this.ptr = ptr;
		}
	}

	protected final List<OccupiedRegion> getSpacePartition()
	{
		List<OccupiedRegion> regions = new LinkedList<>();
		int currentStart, currentEnd;
		int previousEnd = startAddress;
		Pointer prev = null;

		for (Entry<Integer, Pointer> e : localPointerMap.entrySet()) {
			Pointer ptr = e.getValue();
			currentStart = e.getKey();
			currentEnd = currentStart + ptr.getSize();

			if (doSanityChecks) {
				assert (currentStart >= previousEnd) : String.format("(%s) %s (start = %X) conflicts with %s (end = %X)",
					getSourceName(), ptr.getPointerName(), currentStart,
					prev == null ? null : prev.getPointerName(), previousEnd);
			}

			if (currentStart != previousEnd)
				regions.add(new OccupiedRegion(previousEnd, currentStart, null));

			regions.add(new OccupiedRegion(currentStart, currentEnd, ptr));
			previousEnd = currentEnd;
			prev = ptr;
		}

		if (previousEnd != endAddress)
			regions.add(new OccupiedRegion(previousEnd, endAddress, null));

		return regions;
	}

	protected final void printSpacePartition()
	{
		System.out.println("Space Partition for " + getSourceName());
		for (OccupiedRegion r : getSpacePartition()) {
			if (r.ptr == null)
				System.out.printf("> From %X to %X -- %s%n", r.start, r.end, "missing");
			else
				System.out.printf("> From %X to %X -- %s%n", r.start, r.end, r.ptr.getPointerName());
		}
	}

	/*
	protected final void enactHintSuggestions(ByteBuffer fileBuffer) throws IOException
	{
		for(Hint h : hintMap.values())
		{
			if(h.hasType())
			{
				StructType hintType = h.getTypeSuggestion();
				if(finishedPointers.contains(h.address))
				{
					Pointer ptr = getPointer(h.address);
					if(ptr.type != UnknownT && ptr.type == hintType)
					{
						Logger.logf("Struct %08X from hint file has already been identified as %s",
								h.address, ptr.getPointerName());
						continue;
					}
					finishedPointers.remove(h.address);
				}
				enqueueAsRoot(h.address, hintType, Origin.HINT);
			}
		}

		scanPointerQueue(fileBuffer);
		TreeSet<String> usedNames = new TreeSet<String>();

		for(Pointer ptr : localPointerMap.values())
		{
			usedNames.add(ptr.getPointerName());
		}

		for(Hint h : hintMap.values())
		{
			if(h.hasLength())
			{
				if(!finishedPointers.contains(h.address))
					throw new InputFileException(hintFile, getSourceName() + " has unknown struct referenced by size hint: %08X", h.address);

				Pointer ptr = localPointerMap.get(h.address);
				ptr.length = h.getLengthSuggestion();
			}

			if(h.hasWordsPerRow())
			{
				if(!finishedPointers.contains(h.address))
					throw new InputFileException(hintFile, getSourceName() + " has unknown struct referenced by newline hint: %08X", h.address);

				Pointer ptr = localPointerMap.get(h.address);
				ptr.newlineHint = h.getWordsPerRowSuggestion();
			}

			if(h.hasName())
			{
				if(!finishedPointers.contains(h.address))
					throw new InputFileException(hintFile, getSourceName() + " has unknown struct referenced by name hint: %08X", h.address);

				Pointer ptr = localPointerMap.get(h.address);
				String name = h.getNameSuggestion();
				if(usedNames.contains(name))
					throw new InputFileException(hintFile, getSourceName() + " has duplicate name suggested by hint: %08X", h.address);
				ptr.forceName(h.getNameSuggestion());
			}
		}
	}
	 */

	private static final Pattern TrailingWhitespacePattern = Pattern.compile("(.*?)\\s+$");
	private static final Matcher TrailingWhitespaceMatcher = TrailingWhitespacePattern.matcher("");

	protected void printPreamble(PrintWriter pw)
	{} // optional for subclasses

	protected void printScriptFile(File f, ByteBuffer fileBuffer) throws IOException
	{
		FileUtils.touch(f);
		PrintWriter pw = IOUtils.getBufferedPrintWriter(f);
		fileBuffer.position(startOffset);

		pw.println("% Script File: " + f.getName());
		pw.printf("%% Decoded from: %X to %X (%s)%n", startOffset, endOffset, getSourceName());
		pw.println();

		printPreamble(pw);

		for (OccupiedRegion r : getSpacePartition()) {
			if (r.ptr == null) {
				printMissing(fileBuffer, pw, (int) r.start, (int) r.end);
				continue;
			}

			// print annotations
			if (printRequiredBy && !r.ptr.ancestors.isEmpty()) {
				pw.print("% Required by: ");
				for (Pointer ancestorPtr : r.ptr.ancestors)
					pw.print(ancestorPtr.getPointerName() + " ");
				pw.println();
			}
			if (r.ptr.origin != Origin.DECODED && r.ptr.origin != Origin.UNIDENTIFIED)
				pw.println("% Origin: " + r.ptr.origin);

			if (annotateIndexInfo)
				pw.printf("%% %08X --> %08X%n", toOffset(r.ptr.address), r.ptr.address);

			// print struct
			pw.print("#new:" + r.ptr.getType().toString() + " " + r.ptr.getPointerName());
			if (newlineOpenBrace)
				pw.println();
			else
				pw.print(" ");
			pw.println("{");
			printPointer(r.ptr, fileBuffer, (int) r.start, pw);
			pw.println("}");
			pw.println();

			// book keeping
			if (r.ptr.isTypeUnknown())
				unknownPointers++;
		}

		pw.close();

		// post-process step to remove trailing whitespace and add tabs before data
		List<String> lines = IOUtils.readPlainTextFile(f);
		pw = IOUtils.getBufferedPrintWriter(f);
		boolean withinStruct = false;
		for (String line : lines) {
			// account for open brace not on its own line
			if (line.contains("{")) {
				withinStruct = true;
				pw.println(line);
				continue;
			}
			else if (line.equals("}")) {
				withinStruct = false;
				pw.println(line);
				continue;
			}

			if (indentPrintedData) {
				if (withinStruct && !line.isEmpty() && line.charAt(0) != ' ' && line.charAt(0) != '\t')
					pw.print(getTabString());
			}

			TrailingWhitespaceMatcher.reset(line);
			if (!TrailingWhitespaceMatcher.matches()) {
				pw.println(line);
				continue;
			}

			pw.println(TrailingWhitespaceMatcher.group(1));
		}
		pw.close();

		if (dumpEmbeddedImages && embeddedImages.size() > 0) {
			String baseName = FilenameUtils.getBaseName(f.getName());
			File path = f.getParentFile();

			for (EmbeddedImage embed : embeddedImages) {
				String filename;

				if (embed.tile.format.type == TileFormat.TYPE_CI) {
					filename = String.format("%s/%s_%08X_%08X", path, baseName, embed.imgAddr, embed.palAddr);

					//	filename = String.format("%s/%s_%06X_%06X", path,
					//			embed.tile.format,
					//			startOffset + (embed.imgAddr - startAddress),
					//			startOffset + (embed.palAddr - startAddress));

					fileBuffer.position(toOffset(embed.imgAddr));
					embed.tile.readImage(fileBuffer, false);

					fileBuffer.position(toOffset(embed.palAddr));
					embed.tile.readPalette(fileBuffer);
				}
				else {
					filename = String.format("%s/%s_%08X", path, baseName, embed.imgAddr);

					//	filename = String.format("%s/%s_%06X", path, embed.tile.format,
					//			startOffset + (embed.imgAddr - startAddress));

					fileBuffer.position(toOffset(embed.imgAddr));
					embed.tile.readImage(fileBuffer, false);
				}

				FileUtils.touch(new File(filename + ".png"));
				embed.tile.savePNG(filename);
			}
		}
	}

	protected void printPointer(Pointer ptr, ByteBuffer fileBuffer, int startAddress, PrintWriter pw)
	{
		fileBuffer.position(toOffset(startAddress));
		ptr.getType().print(this, ptr, fileBuffer, pw);
	}

	public final void printFunctionName(PrintWriter pw, int addr)
	{
		if (!isLocalAddress(addr)) {
			LibEntry e = library.get(addr);
			if (e != null) {
				pw.print("~Func:" + e.name + " ");
				return;
			}
		}

		printScriptWord(pw, addr);
	}

	public final void printFunctionCall(Pointer ptr, PrintWriter pw, ScriptLine line, int lineAddress, int alignedSize)
	{
		String name;
		boolean local = false;

		int addr = line.args[0];
		if (isLocalAddress(addr)) {
			name = getVariableName(addr);
			local = true;
		}
		else {
			LibEntry e = library.get(addr);
			if (e != null) {
				if (e.type != EntryType.api)
					throw new StarRodException("API function " + e.name + " address registered as " + e.type + " in library!");
				name = e.name;
			}
			else
				name = String.format("%08X", line.args[0]);
		}

		if (line.args.length > 1) {
			if (useTabSpacing) {
				pw.print(name);
				int numTabs = ((alignedSize - name.length()) + 3 & -4) >> 2;
				for (int i = 0; i < numTabs; i++)
					pw.print("\t");
			}
			else {
				if (alignedSize < 1)
					alignedSize = 1;
				pw.printf("%-" + alignedSize + "s", name);
			}
		}
		else // no arg functions just have a single space
			pw.print(name + " ");

		// print args
		if (local) {
			pw.print("( ");
			for (int i = 1; i < line.args.length; i++)
				printScriptWord(pw, ptr, line.types[i], line.args[i]);
			pw.print(")");
		}
		else {
			pw.printf("( ");
			String comment = printFunctionArgs(ptr, pw, line, lineAddress);
			pw.print(comment.isEmpty() ? ")" : ") " + comment);
		}
	}

	public final void printScriptExec(Pointer ptr, PrintWriter pw, ScriptLine line, int lineAddress)
	{
		int addr = line.args[0];
		if (isLocalAddress(addr)) {
			pw.print(getVariableName(addr) + " ");
			for (int i = 1; i < line.args.length; i++)
				printScriptWord(pw, ptr, line.types[i], line.args[i]);
		}
		else {
			LibEntry e = library.get(addr);
			if (e != null) {
				if (e.type != EntryType.script)
					throw new StarRodException("Script address registered as " + e.type + " in library!");
				pw.printf("%s ", e.name);
			}
			else
				printScriptWord(pw, ptr, line.types[0], line.args[0]);

			for (int i = 1; i < line.args.length; i++)
				printScriptWord(pw, ptr, line.types[i], line.args[i]);
		}
	}

	public String printFunctionArgs(Pointer ptr, PrintWriter pw, ScriptLine line, int lineAddress)
	{
		LibEntry entry = library.get(line.args[0]);
		if (entry == null) {
			for (int i = 1; i < line.args.length; i++)
				printScriptWord(pw, ptr, line.types[i], line.args[i]);
			return "";
		}

		int nargs = line.args.length - 1;
		LibParamList params = entry.getMatchingParams(nargs);

		// only wildcard arguments exist
		if (params == null) {
			for (int i = 1; i < line.args.length; i++)
				printScriptWord(pw, ptr, line.types[i], line.args[i]);
			return "";
		}

		String comment = "";

		int i = 1;
		for (LibParam param : params) {
			if (param.shouldPrintParam)
				comment = getStringComment(line.args[i]);

			printScriptWord(pw, ptr, line.types[i], line.args[i]);
			i++;
		}

		return comment;
	}

	public final String getStringComment(int id)
	{
		if ((id & 0xFFFFFFF0) == 0xFE363C80)
			return "% variable string ID";

		if (isLocalAddress(id)) {
			Pointer ptr = getPointer(id);
			if (ptr != null && (ptr.getType() == AsciiT || ptr.getType() == StringT)) {
				String s = ptr.text.replace("%", "\\%");
				if (s.length() > 50)
					return "% " + s.substring(0, 50) + " ...";
				else
					return "% " + s;
			}
		}

		PMString pms = StringDumper.getString(id);
		if (pms != null) {
			String s = pms.toString().replace("\r", "").replace("\n", "\\");
			if (s.length() > 50)
				return "% " + s.substring(0, 50) + " ...";
			else
				return "% " + s;
		}

		return String.format("%% Invalid string ID! %08X", id);
	}

	/**
	 * Returns the variable name associated with a particular address.
	 */
	public String getVariableName(int address)
	{
		if (address >= startAddress && address < addressLimit) {
			if (address < endAddress) {
				Entry<Integer, Pointer> e = localPointerMap.floorEntry(address);
				Pointer ptr = e.getValue();
				int offset = address - ptr.address;
				assert (offset <= ptr.getSize());

				if (offset == 0)
					return ptr.getPointerName();
				else
					return String.format("%s[%X]", ptr.getPointerName(), offset);
			}
			else {
				int offset = address - endAddress;
				return String.format("$End[%X]", offset);
			}
		}

		return String.format("%08X", address);
	}

	public void printNumber(Pointer ptr, PrintWriter pw, int id)
	{
		if (ScriptVariable.isScriptVariable(id))
			pw.print(ScriptVariable.getScriptVariable(id) + " ");
		else
			pw.printf(" %d` ", id);
	}

	public void printModelID(Pointer ptr, PrintWriter pw, int id)
	{
		printScriptWord(pw, id);
	}

	public void printColliderID(Pointer ptr, PrintWriter pw, int id)
	{
		printScriptWord(pw, id);
	}

	public void printZoneID(Pointer ptr, PrintWriter pw, int id)
	{
		printScriptWord(pw, id);
	}

	public void printEntryID(Pointer ptr, PrintWriter pw, int id)
	{
		printScriptWord(pw, id);
	}

	public void printEnum(PrintWriter pw, ConstEnum type, int id)
	{
		if (type == ProjectDatabase.DebuffType)
			printStatus(pw, type, id);
		else if (type == ProjectDatabase.NpcType)
			printNpcID(pw, type, id);
		else if ((!ScriptVariable.isScriptVariable(id) && type.isFlags()) || type.has(id))
			pw.print(type.getConstantString(id) + " ");
		else
			printScriptWord(pw, id);
	}

	public void printStatus(PrintWriter pw, ConstEnum enumType, int id)
	{
		if (id == 0 || ScriptVariable.isScriptVariable(id))
			printScriptWord(pw, id);
		else
			pw.printf(ProjectDatabase.getDebuffString(id) + " ");
	}

	public void printNpcID(PrintWriter pw, ConstEnum enumType, int id)
	{
		if (ScriptVariable.isScriptVariable(id))
			printScriptWord(pw, id);
		else if (enumType.has(id))
			pw.print(enumType.getConstantString(id) + " ");
		else
			printScriptWord(pw, id);
	}

	public void printBoolean(PrintWriter pw, int id)
	{
		if (id == 0)
			pw.print(SyntaxConstants.CONSTANT_PREFIX + "False ");
		else if (id == 1)
			pw.print(SyntaxConstants.CONSTANT_PREFIX + "True ");
		else
			printScriptWord(pw, id);
	}

	/**
	 * Prints a single word in raw hex. Local pointers are replaced by variable names.
	 */
	public final void printWord(PrintWriter pw, int v)
	{
		pw.printf("%s ", getVariableName(v));
	}

	/**
	 * Similar to printWord(), but handles script variables like *VAR[X] as well.
	 */
	public final void printScriptWord(PrintWriter pw, int v)
	{
		if (ScriptVariable.isScriptVariable(v))
			pw.print(ScriptVariable.getScriptVariable(v) + " ");
		else
			printWord(pw, v);
	}

	public final void printScriptWord(PrintWriter pw, Pointer ptr, LibType type, int value)
	{
		if (type == null) {
			printScriptWord(pw, value);
			return;
		}

		switch (type.category) {
			case Number:
				printNumber(ptr, pw, value);
				break;
			case ModelID:
				printModelID(ptr, pw, value);
				break;
			case ColliderID:
				printColliderID(ptr, pw, value);
				break;
			case ZoneID:
				printZoneID(ptr, pw, value);
				break;
			case EntryID:
				printEntryID(ptr, pw, value);
				break;
			case Enum:
				printEnum(pw, type.constType, value);
				break;

			case MemoryStruct:
				if (type.ctype == Primitive.Bool.type)
					printBoolean(pw, value);
				else
					printScriptWord(pw, value);
				break;

			case Unknown:
			case MissingMemoryStruct:
			case MissingStaticStruct:
				printScriptWord(pw, value);
				break;

			case StringID:
			case StaticStruct:
				printScriptWord(pw, value);
				break;
		}
	}

	public final String getScriptWord(int v)
	{
		if (ScriptVariable.isScriptVariable(v))
			return ScriptVariable.getScriptVariable(v);
		else
			return getVariableName(v);
	}

	protected final void printFloatTable(Pointer ptr, ByteBuffer fileBuffer, PrintWriter pw)
	{
		int length = ptr.getSize() / 4;
		int wordsPerRow = 8;

		for (int i = 0; i < length;) {
			pw.printf("%f ", fileBuffer.getFloat());

			if ((++i % wordsPerRow) == 0)
				pw.println("");
		}

		if (length % wordsPerRow != 0)
			pw.println("");
	}

	/**
	 * Prints an abstract struct in raw hex, adding a new line after every 8 words.
	 * Local pointers are replaced by variable names.
	 */
	public final void printHex(Pointer ptr, ByteBuffer fileBuffer, PrintWriter pw)
	{
		printHex(ptr, fileBuffer, pw, 8);
	}

	/**
	 * Prints an abstract struct in raw hex with a specified number of words before a line feed.
	 * Local pointers are replaced by variable names.
	 */
	public final void printHex(Pointer ptr, ByteBuffer fileBuffer, PrintWriter pw, int wordsPerRow)
	{
		printHex(ptr, fileBuffer, pw, wordsPerRow, false);
	}

	public final void printHex(Pointer ptr, ByteBuffer fileBuffer, PrintWriter pw, int wordsPerRow, boolean useVariableNames)
	{
		int length = ptr.getSize() / 4;

		if (ptr.newlineHint != 0)
			wordsPerRow = ptr.newlineHint;

		printHex(fileBuffer, pw, wordsPerRow, length, useVariableNames);

		if (length % wordsPerRow != 0)
			pw.println("");
	}

	/**
	 * Prints an abstract struct in raw hex with a specified number of words before a line feed.
	 * Local pointers are replaced by variable names.
	 */
	public final void printHex(ByteBuffer fileBuffer, PrintWriter pw, int wordsPerRow, int length)
	{
		printHex(fileBuffer, pw, wordsPerRow, length, false);
	}

	public final void printHex(ByteBuffer fileBuffer, PrintWriter pw, int wordsPerRow, int length, boolean useVariableNames)
	{
		for (int i = 0; i < length;) {
			int v = fileBuffer.getInt();
			if (useVariableNames)
				printScriptWord(pw, v);
			else
				printWord(pw, v);

			if ((++i % wordsPerRow) == 0)
				pw.println("");
		}
	}

	public final void printHex(int[] data, PrintWriter pw, int wordsPerRow)
	{
		printHex(data, pw, wordsPerRow, false);
	}

	public final void printHex(int[] data, PrintWriter pw, int wordsPerRow, boolean useVariableNames)
	{
		for (int i = 0; i < data.length;) {
			if (useVariableNames)
				printScriptWord(pw, data[i]);
			else
				printWord(pw, data[i]);

			if ((++i % wordsPerRow) == 0)
				pw.println("");
		}
	}

	/**
	 * Prints a region of the filebuffer than is not claimed by any known struct. If the region is
	 * completely blank, it is labeled as "padding". Otherwise, the region is considered "missing".
	 */
	protected final void printMissing(ByteBuffer fileBuffer, PrintWriter pw, int start, int end)
	{
		if (start != end) {
			boolean nonzero = false;

			fileBuffer.position(toOffset(start));
			for (int j = start; j < end; j += 4) {
				if (fileBuffer.getInt() != 0)
					nonzero = true;
			}

			if (nonzero) {
				missingSections++;
				pw.printf("MISSING: %08X to %08X (%08X to %08X)%n", start, end, toOffset(start), toOffset(end));
				missingRegions.add(new Region(toOffset(start - startOffset), toOffset(end - startOffset)));
			}
			else {
				pw.printf("PADDING: %08X to %08X (%08X to %08X)%n", start, end, toOffset(start), toOffset(end));
				paddingRegions.add(new Region(toOffset(start - startOffset), toOffset(end - startOffset)));
			}

			fileBuffer.position(toOffset(start));
			int num = 0;
			for (int j = start; j < end; j += 4) {
				pw.printf("%08X ", fileBuffer.getInt());
				if (++num % 8 == 0)
					pw.println("");
			}
			if (num % 8 != 0)
				pw.println("");
			pw.println("");
		}
	}

	protected final void printIndexFile(File f) throws IOException
	{
		PrintWriter pw = IOUtils.getBufferedPrintWriter(f);

		for (Entry<Integer, Pointer> e : localPointerMap.entrySet()) {
			Pointer ptr = e.getValue();

			StringBuilder sb = new StringBuilder(ptr.getPointerName());
			sb.append(SyntaxConstants.INDEX_FILE_SEPARATOR).append(ptr.getType());
			sb.append(SyntaxConstants.INDEX_FILE_SEPARATOR).append(String.format("%X", ptr.address - startAddress));
			sb.append(SyntaxConstants.INDEX_FILE_SEPARATOR).append(String.format("%08X", ptr.address));
			sb.append(SyntaxConstants.INDEX_FILE_SEPARATOR).append(String.format("%X", ptr.getSize()));
			if (ptr.getType().isTypeOf(FunctionT))
				sb.append(SyntaxConstants.INDEX_FILE_SEPARATOR).append(ptr.isAPI);

			pw.println(sb.toString());
		}

		int sectionLength = endOffset - startOffset;

		pw.printf("$Start%s%s%s%X%s%X%s%X%n",
			SyntaxConstants.INDEX_FILE_SEPARATOR, UnknownT,
			SyntaxConstants.INDEX_FILE_SEPARATOR, 0,
			SyntaxConstants.INDEX_FILE_SEPARATOR, startAddress,
			SyntaxConstants.INDEX_FILE_SEPARATOR, sectionLength);

		pw.printf("$End%s%s%s%X%s%X%s%X%n",
			SyntaxConstants.INDEX_FILE_SEPARATOR, UnknownT,
			SyntaxConstants.INDEX_FILE_SEPARATOR, sectionLength,
			SyntaxConstants.INDEX_FILE_SEPARATOR, endAddress,
			SyntaxConstants.INDEX_FILE_SEPARATOR, addressLimit - endAddress);

		for (Region padding : paddingRegions)
			pw.printf("Padding%s%X%s%X%n", SyntaxConstants.INDEX_FILE_SEPARATOR, padding.start, SyntaxConstants.INDEX_FILE_SEPARATOR, padding.end);

		for (Region missing : missingRegions)
			pw.printf("Missing%s%X%s%X%n", SyntaxConstants.INDEX_FILE_SEPARATOR, missing.start, SyntaxConstants.INDEX_FILE_SEPARATOR, missing.end);

		pw.close();
	}

	protected void readHints() throws IOException
	{
		TreeSet<String> usedNames = new TreeSet<>();
		hintMap = new TreeMap<>();
		int hintsAdded = 0;

		hintFile = getHintFile();
		if (hintFile != null && hintFile.exists()) {
			List<String> lines = IOUtils.readFormattedTextFile(hintFile, false);
			for (String line : lines) {
				String[] tokens = line.split("\\s+");
				if (tokens.length != 3)
					throw new InputFileException(hintFile, "Improper directive in hint file: " + line);

				int address = (int) Long.parseLong(tokens[1], 16);

				Hint h = null;
				if (hintMap.containsKey(address))
					h = hintMap.get(address);
				else
					h = new Hint(address);

				switch (tokens[0]) {
					case "add":
						StructType type = scope.typeMap.get(tokens[2]);
						if (type == null)
							throw new InputFileException(hintFile, "Unknown type in hint file: " + line);
						h.setTypeSuggestion(type);
						break;

					case "size":
						int size = (int) Long.parseLong(tokens[2], 16);
						h.setLengthSuggestion(size);
						break;

					case "name":
						String name;
						if (tokens[2].startsWith("$")) {
							name = tokens[2].substring(1);
							if (name.isEmpty())
								throw new InputFileException(hintFile, "Improper name directive in hint file: " + line);
						}
						else {
							name = tokens[2];
						}
						h.setNameSuggestion(name);
						if (usedNames.contains(name))
							throw new InputFileException(hintFile, getSourceName() + " has duplicate name suggested by hint: %08X", h.address);
						usedNames.add(name);
						break;

					case "newline":
						int wordsPerRow = (int) Long.parseLong(tokens[2], 16);
						h.setWordsPerRowSuggestion(wordsPerRow);
						break;

					default:
						throw new InputFileException(hintFile, "Unknown directive in hint file: " + tokens[0] + " (expected add|name)");
				}

				if (!hintMap.containsKey(address))
					hintMap.put(address, h);
				hintsAdded++;
			}

			Logger.logf("Added %d hints.", hintsAdded);
		}
	}

	protected File getHintFile()
	{
		return new File(DATABASE_HINTS + getSourceName() + ".hint");
	}

	// certain types of structs are greedy -- we don't know how large they are supposed to be
	protected void growGreedyStructs()
	{
		for (Entry<Integer, Pointer> e : localPointerMap.entrySet()) {
			Pointer ptr = e.getValue();
			int currentAddress = e.getKey();

			if (currentAddress >= endAddress)
				break;

			if (!ptr.hasKnownSize()) {
				Entry<Integer, Pointer> nextEntry = localPointerMap.higherEntry(currentAddress);
				if (nextEntry != null) {
					ptr.setSize(nextEntry.getKey() - currentAddress);
				}
				else {
					ptr.setSize(endAddress - currentAddress);
				}
			}
		}
	}

	protected void mergeAdjacentStructs(StructType type)
	{
		List<Pointer> pointers = new ArrayList<>();

		for (Entry<Integer, Pointer> e : localPointerMap.entrySet()) {
			Pointer ptr = e.getValue();
			if (ptr.getType() == type)
				pointers.add(ptr);
		}

		if (pointers.size() < 2)
			return;

		pointers.sort((p1, p2) -> p1.address - p2.address);

		Pointer current = pointers.get(0);
		for (int i = 1; i < pointers.size(); i++) {
			Pointer ptr = pointers.get(i);
			if (current.address + current.getSize() == ptr.address) {
				current.setSize(current.getSize() + ptr.getSize());
				localPointerMap.remove(ptr.address);
			}
			else {
				// start from the new pointer
				current = ptr;
			}
		}
	}

	// remove structs contained within others
	protected void purgeSubordinatePointers()
	{
		Pointer prevPtr = null;
		int previousEnd = startAddress;
		int currentStart, currentEnd;

		Iterator<Entry<Integer, Pointer>> iter = localPointerMap.entrySet().iterator();
		while (iter.hasNext()) {
			Entry<Integer, Pointer> e = iter.next();
			Pointer ptr = e.getValue();
			currentStart = e.getKey();
			currentEnd = currentStart + ptr.getSize();

			// skip the first struct, it can't possibly be a substruct
			if (prevPtr == null) {
				prevPtr = ptr;
				previousEnd = currentEnd;
				continue;
			}

			if (currentEnd <= previousEnd) {
				iter.remove();
			}
			else {
				prevPtr = ptr;
				previousEnd = currentEnd;
			}
		}
	}

	/**
	 * Looks for pointers that were missed by the scan system.<BR>
	 * It does this by searching the file buffer for regions containing nonzero
	 * data that are not claimed by any struct. The data in these regions is
	 * compared with expected formats for a variety of data structures. If there
	 * is a unique match, the type of its {@link PointerHeuristic} is set and it
	 * is added to the list.
	 *
	 * @param fileBuffer
	 * @return list of PointerHeuristic objects for likely structs
	 */
	private final void tryHeuristics(ByteBuffer fileBuffer) throws IOException
	{
		List<PointerHeuristic> initialHeuristics = findHeuristicCandidates(fileBuffer);
		int structuresFound = 0;

		// first check for ancestors?
		for (PointerHeuristic ph : initialHeuristics) {
			guessType(ph, fileBuffer);
			if (ph.structType == ancestorType) {
				int addr = toAddress(ph.getOffset());
				finishedPointers.remove(addr);
				enqueueAsRoot(addr, ancestorType, Origin.HEURISTIC);
			}
		}

		do {
			// scan the newly discovered data structures
			structuresFound += pointerQueue.size();
			scanPointerQueue(fileBuffer);

			// look for more
			initialHeuristics = findHeuristicCandidates(fileBuffer);
			for (PointerHeuristic ph : initialHeuristics) {
				guessType(ph, fileBuffer);
				if ((ph.structType == UnknownT) || (ph.structType == ancestorType))
					continue;

				int addr = toAddress(ph.getOffset());
				finishedPointers.remove(addr); //XXX necessary?
				enqueueAsRoot(addr, ph.structType, Origin.HEURISTIC);
			}
		}
		while (pointerQueue.size() > 0);

		structuresFound += pointerQueue.size();
		scanPointerQueue(fileBuffer);

		if (structuresFound > 0)
			Logger.logf("Found %d structures through heuristics.", structuresFound);
	}

	/**
	 * Looks for potential data structures that were missed by the scan system.
	 * These are identified by regions of the file buffer with nonzero data that
	 * are not claimed by any struct.
	 *
	 * @param fileBuffer
	 * @return list of possible structs
	 */
	private final List<PointerHeuristic> findHeuristicCandidates(ByteBuffer fileBuffer)
	{
		List<PointerHeuristic> heuristicsList = new ArrayList<>();
		ArrayList<Integer> pointerList = getSortedLocalPointerList();

		if (pointerList.isEmpty())
			return heuristicsList;

		int currentAddress;
		int prevEndAddress = startAddress;

		for (int i = 0; i < pointerList.size(); i++) {
			currentAddress = pointerList.get(i);
			fileBuffer.position(toOffset(currentAddress));

			// found some region that doesn't belong to any identifed struct
			if (currentAddress != prevEndAddress) {
				int startNonzero = Integer.MAX_VALUE;
				int lastNonzero = Integer.MIN_VALUE;

				fileBuffer.position(toOffset(prevEndAddress));
				for (int j = prevEndAddress; j < currentAddress; j += 4) {
					if (fileBuffer.getInt() != 0) {
						if (startNonzero == Integer.MAX_VALUE)
							startNonzero = j;
						lastNonzero = j;
					}
				}

				// the region is not just padding
				if (startNonzero != Integer.MAX_VALUE) {
					int startPadding = startNonzero - prevEndAddress;
					int endPadding = currentAddress - (lastNonzero + 4);
					heuristicsList.add(new PointerHeuristic(
						toOffset(startNonzero), toOffset(lastNonzero + 4),
						startPadding, endPadding));
				}
			}

			// skip to next
			Pointer ptr = getPointer(currentAddress);
			fileBuffer.position(toOffset(currentAddress) + ptr.getSize());
			prevEndAddress = toAddress(fileBuffer.position());
		}

		return heuristicsList;
	}

	protected int guessType(PointerHeuristic h, ByteBuffer fileBuffer)
	{
		int matches = 0;

		if (Function.isFunction(h, fileBuffer)) {
			h.structType = FunctionT;
			matches++;
		}

		if (Script.isScript(h, fileBuffer)) {
			h.structType = ScriptT;
			matches++;
		}

		if (isASCII(h, fileBuffer)) {
			h.structType = AsciiT;
			matches++;
		}

		if (isDisplayList(h, fileBuffer)) {
			h.structType = DisplayListT;
			matches++;
		}

		if (matches != 1)
			h.structType = UnknownT;
		if (matches > 1)
			Logger.logf("Data at %X matches multiple struct heuristics.", h.start);

		return matches;
	}

	private static final boolean isASCII(PointerHeuristic h, ByteBuffer fileBuffer)
	{
		fileBuffer.position(h.start);
		int maxLength = h.endPadding + (h.end - h.start);

		if (maxLength > 64)
			return false;

		String s = IOUtils.readString(fileBuffer, maxLength);

		if (s.contains("_")) {
			// may just be enough to look for _ character, but this is the most accurate method
			if (s.matches("[a-z0-9_]+"))
				return true;
		}

		return false;
	}

	private static final boolean isDisplayList(PointerHeuristic h, ByteBuffer fileBuffer)
	{
		// the last word from END_DL would be considered padding
		if (h.endPadding < 4)
			return false;
		int end = h.end + 4;

		// need at least RDP_PIPE_SYNC, END_DL
		int length = end - h.start;
		if (length % 8 != 0)
			return false;
		if (length < 16)
			return false;

		// check for RDP_PIPE_SYNC
		fileBuffer.position(h.start);
		if (fileBuffer.getInt() != 0xE7000000)
			return false;
		if (fileBuffer.getInt() != 0x00000000)
			return false;

		// check for END_DL
		fileBuffer.position(end - 8);
		if (fileBuffer.getInt() != 0xDF000000)
			return false;
		if (fileBuffer.getInt() != 0x00000000)
			return false;

		return true;
	}

	protected final void writeRawFile(File f, ByteBuffer fileBuffer) throws IOException
	{
		byte[] sectionBytes = new byte[endOffset - startOffset];
		fileBuffer.position(startOffset);
		fileBuffer.get(sectionBytes);

		FileUtils.touch(f);
		FileOutputStream fos = new FileOutputStream(f);
		FileChannel out = fos.getChannel();
		out.write(ByteBuffer.wrap(sectionBytes));
		fos.close();
	}

	public String getTabString()
	{
		if (useTabIndents)
			return "\t";
		else
			return "    ";
	}
}

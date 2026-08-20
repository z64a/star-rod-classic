package patcher;

import static app.Directories.MOD_OUT;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;

import app.input.IOUtils;
import game.ROM.LibScope;
import game.shared.ProjectDatabase;
import game.shared.lib.LibEntry;
import util.Logger;

/** Collects final build addresses for the external human-readable symbol map. */
public class BuildSymbolMap
{
	public static final String OUTPUT_NAME = "symbol_map.txt";

	private static final int EMBEDDED_ENTRY_SIZE = 12;
	private static final int MAX_EMBEDDED_NAME_LENGTH = 32;
	private static final int MAX_INFERRED_FUNCTION_SIZE = 0x1000;
	private static final int MIN_EXECUTABLE_ADDRESS = 0x80000400;
	private static final int MAX_EXECUTABLE_ADDRESS = 0x80800000;

	private static final Comparator<Symbol> ADDRESS_COMPARATOR = (a, b) -> {
		int result = Integer.compareUnsigned(a.address, b.address);
		if (result != 0)
			return result;
		result = a.scope.compareTo(b.scope);
		if (result != 0)
			return result;
		result = a.source.compareToIgnoreCase(b.source);
		if (result != 0)
			return result;
		return a.name.compareToIgnoreCase(b.name);
	};

	private final List<Symbol> symbols = new ArrayList<>();

	public BuildSymbolMap()
	{
		for (LibScope scope : LibScope.values()) {
			if (scope == LibScope.None)
				continue;

			for (LibEntry entry : ProjectDatabase.rom.getLibrary(scope)) {
				if (entry.scope != scope)
					continue;

				String type;
				switch (entry.type) {
					case asm:
					case api:
						type = "Function";
						break;
					case script:
						type = "Script";
						break;
					default:
						continue;
				}

				symbols.add(new Symbol(entry.address, 0, entry.name, type, scope, "-", "engine"));
			}
		}
	}

	public void add(int address, int size, String name, String type, LibScope scope, String source, boolean overlay)
	{
		if (address == -1 || size <= 0)
			return;
		symbols.add(new Symbol(address, size, name, type, scope, source, overlay ? "overlay" : "global"));
	}

	public void write() throws IOException
	{
		List<Symbol> sorted = new ArrayList<>(symbols);
		Collections.sort(sorted, ADDRESS_COMPARATOR);
		writeTextFile(sorted);
	}

	/** Writes the compact always-resident function table used by named crash traces. */
	public void writeEmbeddedFunctionTable(RomPatcher rp, int infoAddress)
	{
		List<EmbeddedSymbol> embedded = getEmbeddedSymbols();
		int tableOffset = rp.nextAlignedOffset();
		int nameOffset = tableOffset + embedded.size() * EMBEDDED_ENTRY_SIZE;

		for (EmbeddedSymbol symbol : embedded) {
			symbol.nameAddress = rp.toAddress(nameOffset);
			nameOffset += symbol.name.length + 1;
		}

		rp.seek("Crash Function Symbol Table", tableOffset);
		for (EmbeddedSymbol symbol : embedded) {
			rp.writeInt(symbol.address);
			rp.writeInt(symbol.endAddress);
			rp.writeInt(symbol.nameAddress);
		}
		for (EmbeddedSymbol symbol : embedded) {
			rp.write(symbol.name);
			rp.writeByte(0);
		}
		rp.padOut(4);

		int infoOffset = RomPatcher.ROM_BASE + (infoAddress - RomPatcher.RAM_BASE);
		rp.seek("Crash Function Symbol Info", infoOffset);
		rp.writeInt(rp.toAddress(tableOffset));
		rp.writeInt(embedded.size());

		Logger.logf("Embedded %d always-resident function names for crash traces (%X bytes).", embedded.size(), nameOffset - tableOffset);
	}

	private List<EmbeddedSymbol> getEmbeddedSymbols()
	{
		TreeMap<Integer, Symbol> resident = new TreeMap<>(Integer::compareUnsigned);
		for (Symbol symbol : symbols) {
			if (!symbol.type.equals("Function"))
				continue;
			if (!symbol.origin.equals("global") && (!symbol.origin.equals("engine") || symbol.scope != LibScope.Common))
				continue;
			if (Integer.compareUnsigned(symbol.address, MIN_EXECUTABLE_ADDRESS) < 0
				|| Integer.compareUnsigned(symbol.address, MAX_EXECUTABLE_ADDRESS) >= 0)
				continue;
			resident.put(symbol.address, symbol);
		}

		List<Symbol> sorted = new ArrayList<>(resident.values());
		List<EmbeddedSymbol> embedded = new ArrayList<>(sorted.size());
		for (int i = 0; i < sorted.size(); i++) {
			Symbol symbol = sorted.get(i);
			long startAddress = Integer.toUnsignedLong(symbol.address);
			long endAddress;
			if (symbol.size > 0) {
				endAddress = startAddress + symbol.size;
			}
			else {
				endAddress = startAddress + MAX_INFERRED_FUNCTION_SIZE;
				if (i + 1 < sorted.size())
					endAddress = Math.min(endAddress, Integer.toUnsignedLong(sorted.get(i + 1).address));
			}
			endAddress = Math.min(endAddress, Integer.toUnsignedLong(MAX_EXECUTABLE_ADDRESS));
			if (endAddress <= startAddress)
				continue;

			String name = getEmbeddedName(symbol.name);
			embedded.add(new EmbeddedSymbol(symbol.address, (int) endAddress, name.getBytes(StandardCharsets.US_ASCII)));
		}
		return embedded;
	}

	private static String getEmbeddedName(String name)
	{
		if (name.startsWith("$"))
			name = name.substring(1);

		StringBuilder cleaned = new StringBuilder(name.length());
		for (int i = 0; i < name.length(); i++) {
			char chr = name.charAt(i);
			cleaned.append(chr >= 0x20 && chr <= 0x7E ? chr : '?');
		}

		if (cleaned.length() <= MAX_EMBEDDED_NAME_LENGTH)
			return cleaned.toString();
		return cleaned.substring(0, 20) + ".." + cleaned.substring(cleaned.length() - 10);
	}

	private void writeTextFile(List<Symbol> sorted) throws IOException
	{
		File output = new File(MOD_OUT + OUTPUT_NAME);
		try (PrintWriter pw = IOUtils.getBufferedPrintWriter(output)) {
			pw.println("# Star Rod build symbol map");
			pw.println("# address  end       type      scope     origin    source                          name");
			for (Symbol symbol : sorted) {
				String end = symbol.size > 0 ? String.format("%08X", symbol.address + symbol.size) : "--------";
				pw.printf("%08X  %s  %-8s  %-8s  %-8s  %-30s  %s%n", symbol.address, end, symbol.type, symbol.scope, symbol.origin, symbol.source,
					symbol.name);
			}
		}
	}

	private static class Symbol
	{
		private final int address;
		private final int size;
		private final String name;
		private final String type;
		private final LibScope scope;
		private final String source;
		private final String origin;

		private Symbol(int address, int size, String name, String type, LibScope scope, String source, String origin)
		{
			this.address = address;
			this.size = size;
			this.name = name;
			this.type = type;
			this.scope = scope;
			this.source = source == null || source.isEmpty() ? "-" : source;
			this.origin = origin;
		}
	}

	private static class EmbeddedSymbol
	{
		private final int address;
		private final int endAddress;
		private final byte[] name;
		private int nameAddress;

		private EmbeddedSymbol(int address, int endAddress, byte[] name)
		{
			this.address = address;
			this.endAddress = endAddress;
			this.name = name;
		}
	}
}

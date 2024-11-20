package game.shared.lib;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeMap;

import app.input.InvalidInputException;
import game.ROM.LibScope;
import game.shared.lib.LibEntry.EntryType;
import game.shared.struct.StructType;

public class Library implements Iterable<LibEntry>
{
	private TreeMap<Integer, LibEntry> addrMap;
	private TreeMap<String, LibEntry> nameMap;
	public List<LibEntry> signatures = new LinkedList<>();
	private LibScope scope;

	public LibEntry get(int addr)
	{
		return addrMap.get(addr);
	}

	public LibEntry get(String name)
	{
		return nameMap.get(name);
	}

	public Library(LibScope scope) throws IOException
	{
		addrMap = new TreeMap<>();
		nameMap = new TreeMap<>();
		this.scope = scope;
	}

	protected StructType getType(String name)
	{
		StructType type = scope.typeMap.get(name);

		if (type == null)
			throw new RuntimeException("Function library references unknown type: " + name);

		return type;
	}

	@Override
	public Iterator<LibEntry> iterator()
	{
		return addrMap.values().iterator();
	}

	public void addEntries(LibraryFile lib, boolean allowOverride) throws InvalidInputException
	{
		for (LibEntry e : lib) {
			if (e.type == EntryType.sig || e.name.equals("???"))
				continue;

			if (addrMap.containsKey(e.address) && (!allowOverride || !addrMap.get(e.address).name.equals(e.name)))
				throw new InvalidInputException(
					"Duplicate address in library: %n%X from %s has already been defined!",
					e.address, lib.source.getName());

			if (nameMap.containsKey(e.name) && (!allowOverride || nameMap.get(e.name).address != e.address))
				throw new InvalidInputException(
					"Duplicate name in library: %n%s from %s has already been defined!",
					e.name, lib.source.getName());

			addrMap.put(e.address, e);
			nameMap.put(e.name, e);
		}

		signatures.addAll(lib.signatures);
	}
}

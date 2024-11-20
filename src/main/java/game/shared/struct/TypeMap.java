package game.shared.struct;

import java.util.Iterator;

import util.CaseInsensitiveMap;

public class TypeMap implements Iterable<StructType>
{
	private CaseInsensitiveMap<StructType> nameMap;

	public TypeMap()
	{
		nameMap = new CaseInsensitiveMap<>();
	}

	public void put(String name, StructType s)
	{
		nameMap.put(name, s);
	}

	public void add(StructType s)
	{
		nameMap.put(s.toString(), s);
	}

	public void add(TypeMap types)
	{
		nameMap.putAll(types.nameMap);
	}

	public StructType get(String name)
	{
		return nameMap.get(name);
	}

	@Override
	public Iterator<StructType> iterator()
	{
		return nameMap.values().iterator();
	}
}

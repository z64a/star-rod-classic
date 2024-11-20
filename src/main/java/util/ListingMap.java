package util;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

public class ListingMap<S, R>
{
	private LinkedHashMap<S, List<R>> map;

	public ListingMap()
	{
		map = new LinkedHashMap<>();
	}

	public void clear()
	{
		map.clear();
	}

	public void add(S key, R obj)
	{
		if (map.containsKey(key)) {
			List<R> list = map.get(key);
			list.add(obj);
		}
		else {
			List<R> list = new LinkedList<>();
			map.put(key, list);
			list.add(obj);
		}
	}

	public List<R> getList(S key)
	{
		return map.get(key);
	}

	public Set<Entry<S, List<R>>> entrySet()
	{
		return map.entrySet();
	}
}

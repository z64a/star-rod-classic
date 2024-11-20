package util;

import java.util.TreeMap;

// represent a list with holes in it
public class IncompleteList<T>
{
	private TreeMap<Integer, T> map;

	public IncompleteList()
	{
		map = new TreeMap<>();
	}

	public void set(int index, T item)
	{
		map.put(index, item);
	}

	public T remove(int index)
	{
		return map.remove(index);
	}

	public T get(int index)
	{
		return map.get(index);
	}

	public Integer indexBefore(int index)
	{
		return map.floorKey(index);
	}

	public Integer indexAfter(int index)
	{
		return map.ceilingKey(index);
	}
}

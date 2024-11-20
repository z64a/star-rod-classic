package patcher;

import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiFunction;

public class ConflictTree extends TreeMap<Integer, Interval>
{
	public Interval getConflict(int start, int end)
	{
		if (containsKey(start))
			return get(start); // conflict

		java.util.Map.Entry<Integer, Interval> prev = lowerEntry(start);
		java.util.Map.Entry<Integer, Interval> next = higherEntry(start);

		if (prev != null && prev.getValue().end > start)
			return prev.getValue(); // conflict with prev

		if (next != null && next.getValue().start < end)
			return next.getValue(); // conflict with next

		return null;
	}

	public Interval add(String source, int start, int end)
	{
		if (containsKey(start))
			return get(start); // conflict

		java.util.Map.Entry<Integer, Interval> prevKV = lowerEntry(start);
		java.util.Map.Entry<Integer, Interval> nextKV = higherEntry(start);
		Interval prev = (prevKV == null) ? null : prevKV.getValue();
		Interval next = (nextKV == null) ? null : nextKV.getValue();

		boolean mergedPrev = false;
		boolean mergedNext = false;

		if (prev != null) {
			if (prev.end > start)
				return prev; // conflict with prev

			if (prev.end == start && prev.source.equals(source)) {
				prev.end = end;
				start = prev.start; // necessary for when new data merges with both prev and next
				mergedPrev = true;
				//	System.out.printf("MERGED WITH PREV: %X,%X%n", prev.start, prev.end);
			}
		}

		if (next != null) {
			if (next.start < end)
				return next; // conflict with next

			if (next.start == end && next.source.equals(source)) {
				super.remove(next.start);
				super.put(start, new Interval(source, start, next.end));

				mergedNext = true;
				super.remove(next.start);

				//	System.out.printf("MERGED WITH NEXT: %X,%X%n", next.start, next.end);
			}
		}

		if (!mergedPrev && !mergedNext)
			super.put(start, new Interval(source, start, end));

		return null;
	}

	@Override
	public Interval put(Integer key, Interval value)
	{
		//	Object oldValue = super.put(key, value);
		//	return oldValue;
		throw new UnsupportedOperationException();
	}

	@Override
	public void putAll(Map<? extends Integer, ? extends Interval> map)
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public Interval remove(Object key)
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public Interval replace(Integer key, Interval value)
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean replace(Integer key, Interval oldValue, Interval newValue)
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public void replaceAll(BiFunction<? super Integer, ? super Interval, ? extends Interval> function)
	{
		throw new UnsupportedOperationException();
	}
}

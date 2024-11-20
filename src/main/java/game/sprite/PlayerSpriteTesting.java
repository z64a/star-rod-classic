package game.sprite;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Map.Entry;
import java.util.TreeMap;

import app.Environment;
import util.CountingMap;

public class PlayerSpriteTesting
{
	public static void main(String args[]) throws IOException
	{
		Environment.initialize();
		test();
		Environment.exit();
	}

	private static void test() throws IOException
	{
		ByteBuffer bb = Environment.getBaseRomBuffer();
		bb.position(0x1943068);

		TreeMap<Integer, Interval> xyMap = new TreeMap<>();
		CountingMap<Integer> counts = new CountingMap<>();

		for (int i = 0; i < 0x2CD; i++) {
			int v = bb.getInt();
			int size = (v >>> 20) << 4;
			int offset = v & 0xFFFFF;

			System.out.printf("%08X %3X %5X %08X%n", v, size, offset, 0x1943020 + offset);

			Interval xy = new Interval(offset, size);

			counts.add(offset);
			xyMap.put(offset, xy);
		}

		ArrayList<Interval> intervals = new ArrayList<>(xyMap.values());

		for (int i = 0; i < intervals.size(); i++) {
			Interval a = intervals.get(i);
			for (int j = (i + 1); j < intervals.size(); j++) {
				Interval b = intervals.get(j);
				if (a.overlaps(b))
					System.out.println("OVERLAP: " + a + " & " + b);
			}
		}

		Interval prev = null;
		for (int i = 0; i < intervals.size(); i++) {
			Interval a = intervals.get(i);
			System.out.printf("%s %X%n", a, 0x1943020 + a.start);

			if (prev != null && prev.end != a.start)
				System.out.printf("GAP: %X%n", a.start - prev.end);

			prev = a;
		}

		for (Entry<Integer, Integer> e : counts.entrySet()) {
			if (e.getValue() > 1)
				System.out.printf("%X %d%n", e.getKey(), e.getValue());
		}
	}

	private static class Interval implements Comparable<Interval>
	{
		final int start;
		final int length;
		final int end;

		public Interval(int start, int length)
		{
			this.start = start;
			this.length = length;
			this.end = start + length;
		}

		public boolean overlaps(Interval other)
		{
			return Math.max(end, other.end) - Math.min(start, other.start) < (length + other.length);
		}

		@Override
		public int compareTo(Interval other)
		{
			return other.start - start;
		}

		@Override
		public String toString()
		{
			return String.format("%4X - %4X (%X)", start, end, length);
		}
	}
}

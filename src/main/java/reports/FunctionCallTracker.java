package reports;

import java.io.PrintWriter;
import java.util.Map;
import java.util.TreeMap;

import app.StarRodException;
import game.shared.lib.LibEntry;
import game.shared.lib.Library;

public class FunctionCallTracker
{
	private static TreeMap<Long, Integer> calls = new TreeMap<>();

	public static void clear()
	{
		calls.clear();
	}

	public static void addCall(int address)
	{
		long unsignedAddress = address & 0xFFFFFFFFL;

		if (calls.containsKey(unsignedAddress)) {
			int oldCount = calls.get(unsignedAddress);
			calls.put(unsignedAddress, oldCount + 1);
		}
		else {
			calls.put(unsignedAddress, 1);
		}
	}

	public static void printCalls(Library library, PrintWriter out)
	{
		int identified = 0;
		int identifiedCount = 0;
		int totalCount = 0;

		for (Map.Entry<Long, Integer> entry : calls.entrySet()) {
			Integer address = (int) (long) (entry.getKey());
			Integer count = entry.getValue();

			LibEntry e = library.get(address);

			if (e != null) {
				if (!e.isFunction())
					throw new StarRodException("Library entry for %08X is %s, expected function.", e.address, e.type);
				out.printf("%08X  %-4d  %s\r\n", address, count, e.name);
				identified++;
				identifiedCount += count;
			}
			else
				out.printf("%08X  %-4d  \r\n", address, count);

			totalCount += count;
		}

		out.println();
		out.printf("Identified %d of %d functions (%5.2f%%)\r\n",
			identified, calls.size(), (100.0f * identified) / calls.size());
		out.printf("Identified %d of %d function calls (%5.2f%%)\r\n",
			identifiedCount, totalCount, (100.0f * identifiedCount) / totalCount);

		out.close();
	}

	public static void printCalls(Library library)
	{
		printCalls(library, new PrintWriter(System.out));
	}

	public static void printCalls(PrintWriter out)
	{
		for (Map.Entry<Long, Integer> entry : calls.entrySet()) {
			Long address = entry.getKey();
			Integer count = entry.getValue();
			out.printf("%08X  %-6d\r\n", address, count);
		}

		out.close();
	}

	public static void printCalls()
	{
		printCalls(new PrintWriter(System.out));
	}
}

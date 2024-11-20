package reports;

import java.io.PrintWriter;
import java.util.Map;
import java.util.TreeMap;

import game.shared.ProjectDatabase;

public class EffectTypeTracker
{
	private static TreeMap<Integer, Integer> effectCount = new TreeMap<>();
	private static TreeMap<Integer, String> effectSource = new TreeMap<>();

	public static void clear()
	{
		effectCount.clear();
		effectSource.clear();
	}

	public static void addEffect(int effect, String source)
	{
		if (effectCount.containsKey(effect)) {
			int oldCount = effectCount.get(effect);
			effectCount.put(effect, oldCount + 1);
			effectSource.put(effect, source);
		}
		else {
			effectCount.put(effect, 1);
			effectSource.put(effect, source);
		}
	}

	public static void printEffects(PrintWriter out)
	{
		for (Map.Entry<Integer, Integer> entry : effectCount.entrySet()) {
			Integer effect = entry.getKey();
			Integer count = entry.getValue();
			String source = effectSource.get(effect);

			if (ProjectDatabase.EffectType.contains(effect))
				out.printf("** %08X  %-3d  %s\r\n", effect, count, source);
			else
				out.printf("   %08X  %-3d  %s\r\n", effect, count, source);
		}
		out.close();
	}
}

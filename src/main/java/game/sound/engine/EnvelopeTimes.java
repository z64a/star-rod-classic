package game.sound.engine;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class EnvelopeTimes
{
	public static final int COUNT = 95;

	private static final int[] FRAME_COUNTS = {
			10434, 9565, 8695, 7826, 6956, 6086, 5217, 4782,
			4347, 3913, 3478, 3304, 3130, 2956, 2782, 2608,
			2434, 2260, 2086, 1913, 1739, 1565, 1391, 1217,
			1043, 869, 782, 695, 608, 521, 478, 434,
			391, 347, 330, 313, 295, 278, 260, 243,
			226, 208, 191, 173, 165, 156, 147, 139,
			130, 121, 113, 104, 95, 86, 78, 69,
			65, 60, 56, 52, 50, 48, 46, 45,
			43, 41, 40, 38, 36, 34, 33, 31,
			29, 27, 26, 24, 22, 20, 19, 17,
			16, 14, 12, 11, 10, 9, 8, 7,
			6, 5, 4, 3, 2, 1, 0
	};

	private static final String[] TOKENS = {
			// 0-29
			"60s", "55s", "50s", "45s", "40s", "35s", "30s", "27.5s", "25s", "22.5s",
			"20s", "19s", "18s", "17s", "16s", "15s", "14s", "13s", "12s", "11s",
			"10s", "9s", "8s", "7s", "6s", "5s", "4.5s", "4s", "3.5s", "3s",

			// 30-59
			"2750ms", "2500ms", "2250ms", "2s", "1900ms", "1800ms", "1700ms", "1600ms", "1500ms", "1400ms",
			"1300ms", "1200ms", "1100ms", "1s", "950ms", "900ms", "850ms", "800ms", "750ms", "700ms",
			"650ms", "600ms", "550ms", "500ms", "450ms", "400ms", "375ms", "350ms", "325ms", "300ms",

			// 60-79
			"290ms", "280ms", "270ms", "260ms", "250ms", "240ms", "230ms", "220ms", "210ms", "200ms",
			"190ms", "180ms", "170ms", "160ms", "150ms", "140ms", "130ms", "120ms", "110ms", "100ms",

			// 80-94. The engine has no 15- or 13-unit entries.
			"16units", "14units", "12units", "11units", "10units", "9units", "8units", "7units",
			"6units", "5units", "4units", "3units", "2units", "1unit", "0"
	};

	private static final List<String> TOKEN_LIST = List.of(TOKENS);
	private static final Map<String, Integer> TOKEN_INDICES;

	static {
		if (FRAME_COUNTS.length != COUNT)
			throw new ExceptionInInitializerError("Expected " + COUNT + " envelope intervals, found " + FRAME_COUNTS.length);
		if (TOKENS.length != COUNT)
			throw new ExceptionInInitializerError("Expected " + COUNT + " envelope duration tokens, found " + TOKENS.length);

		Map<String, Integer> indices = new LinkedHashMap<>();
		for (int i = 0; i < TOKENS.length; i++) {
			Integer previous = indices.put(TOKENS[i], i);
			if (previous != null)
				throw new ExceptionInInitializerError("Duplicate envelope duration token: " + TOKENS[i]);
		}
		// Accept plural spelling while always writing "1unit".
		indices.put("1units", 93);
		TOKEN_INDICES = Collections.unmodifiableMap(indices);
	}

	private EnvelopeTimes()
	{}

	public static int framesForIndex(int index)
	{
		if (index < 0 || index >= COUNT)
			throw new IllegalArgumentException("Envelope duration index must be between 0 and " + (COUNT - 1) + ": " + index);
		return FRAME_COUNTS[index];
	}

	public static int[] frameCounts()
	{
		return FRAME_COUNTS.clone();
	}

	public static String tokenForIndex(int index)
	{
		if (index < 0 || index >= COUNT)
			throw new IllegalArgumentException("Envelope duration index must be between 0 and " + (COUNT - 1) + ": " + index);
		return TOKENS[index];
	}

	public static int indexForToken(String token)
	{
		if (token == null)
			throw new IllegalArgumentException("Envelope duration token cannot be null");

		Integer index = TOKEN_INDICES.get(token);
		if (index == null)
			throw new IllegalArgumentException("Unknown envelope duration token: " + token);
		return index;
	}

	public static List<String> tokens()
	{
		return TOKEN_LIST;
	}
}

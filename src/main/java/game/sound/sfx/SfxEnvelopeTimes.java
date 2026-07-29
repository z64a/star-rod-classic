package game.sound.sfx;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SfxEnvelopeTimes
{
	public static final int COUNT = 95;

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
		if (TOKENS.length != COUNT)
			throw new ExceptionInInitializerError("Expected " + COUNT + " envelope duration tokens, found " + TOKENS.length);

		Map<String, Integer> indices = new LinkedHashMap<>();
		for (int i = 0; i < TOKENS.length; i++) {
			Integer previous = indices.put(TOKENS[i], i);
			if (previous != null)
				throw new ExceptionInInitializerError("Duplicate envelope duration token: " + TOKENS[i]);
		}
		// Accept plural spelling while always writing "1unit"
		indices.put("1units", 93);
		TOKEN_INDICES = Collections.unmodifiableMap(indices);
	}

	private SfxEnvelopeTimes()
	{}

	public static String tokenForIndex(int index)
	{
		if (index < 0 || index >= COUNT)
			throw new SfxFormatException("Envelope duration index must be between 0 and " + (COUNT - 1) + ": " + index);
		return TOKENS[index];
	}

	public static int indexForToken(String token)
	{
		if (token == null)
			throw new SfxFormatException("Envelope duration token cannot be null");

		Integer index = TOKEN_INDICES.get(token);
		if (index == null)
			throw new SfxFormatException("Unknown envelope duration token: " + token);
		return index;
	}

	public static List<String> tokens()
	{
		return TOKEN_LIST;
	}
}

package game.sound.sfx;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

public final class SfxVanillaUsage
{
	private static final Pattern TABLE_ROW = Pattern.compile("[0-9A-Fa-f]{1,4}");
	private static final Set<Integer> UNUSED_IDS = loadUnusedIDs();

	private SfxVanillaUsage()
	{}

	public static boolean isUnused(int id)
	{
		return UNUSED_IDS.contains(id);
	}

	private static Set<Integer> loadUnusedIDs()
	{
		InputStream stream = SfxVanillaUsage.class.getResourceAsStream("/sfx_unused.txt");
		if (stream == null)
			throw new IllegalStateException("Missing bundled sfx_unused.txt");

		Set<Integer> ids = new HashSet<>();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
			for (String line; (line = reader.readLine()) != null;) {
				String value = line.trim();
				if (value.isEmpty() || value.startsWith("#"))
					continue;
				if (!TABLE_ROW.matcher(value).matches())
					throw new IllegalStateException("Invalid sfx_unused.txt row: " + line);
				int id = Integer.parseInt(value, 16);
				if (!SfxNames.isRawSoundID(id))
					throw new IllegalStateException("Invalid vanilla-unused sound ID: " + value);
				if (!ids.add(id))
					throw new IllegalStateException("Duplicate vanilla-unused sound ID: " + value);
			}
		}
		catch (IOException e) {
			throw new IllegalStateException("Could not read bundled sfx_unused.txt", e);
		}
		return Set.copyOf(ids);
	}
}

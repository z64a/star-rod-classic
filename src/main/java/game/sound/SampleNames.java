package game.sound;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.StarRodException;

public final class SampleNames
{
	private static final Pattern TABLE_ROW = Pattern.compile(
		"^\\s*([A-Z0-9]{4}_[0-9A-F]{2}(?:_Loop)?)\\s+([A-Za-z0-9_]+)\\s*$");

	private final Map<String, String> names = new LinkedHashMap<>();
	private final Map<String, String> rawNames = new HashMap<>();

	private SampleNames()
	{}

	public String get(String rawName)
	{
		String name = names.get(rawName);
		if (name == null)
			throw new StarRodException("Missing bundled sample name for %s", rawName);
		return name;
	}

	public static SampleNames loadBundled() throws IOException
	{
		InputStream stream = SampleNames.class.getResourceAsStream("/samples.txt");
		if (stream == null)
			throw new IOException("Missing bundled samples.txt");

		SampleNames result = new SampleNames();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
			for (String line; (line = reader.readLine()) != null;) {
				Matcher matcher = TABLE_ROW.matcher(line);
				if (!matcher.matches())
					continue;

				String rawName = matcher.group(1);
				String name = matcher.group(2);
				if (result.names.put(rawName, name) != null)
					throw new IOException("Duplicate raw sample name: " + rawName);
				if (result.rawNames.put(name, rawName) != null)
					throw new IOException("Duplicate named sample: " + name);
			}
		}
		return result;
	}
}

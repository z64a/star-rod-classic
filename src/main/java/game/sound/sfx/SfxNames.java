package game.sound.sfx;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SfxNames
{
	private static final Pattern TABLE_ROW = Pattern.compile("^\\s*([0-9A-Fa-f]+)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*$");
	private static final Pattern ENUM_ROW = Pattern.compile("^\\s*([A-Z_][A-Z0-9_]*)\\s*=\\s*([^,]+),");

	private final Map<Integer, List<String>> names = new LinkedHashMap<>();

	public List<String> get(int id)
	{
		return names.getOrDefault(id, List.of());
	}

	public Iterable<Map.Entry<Integer, List<String>>> entries()
	{
		return names.entrySet();
	}

	public boolean hasAuthoritativeName(int id)
	{
		List<String> idNames = get(id);
		return !idNames.isEmpty() && !isGeneratedName(id, idNames.get(0));
	}

	public boolean hasGeneratedName(int id)
	{
		List<String> idNames = get(id);
		return idNames.size() == 1 && isGeneratedName(id, idNames.get(0));
	}

	public boolean shouldMaterializeEmpty(int id)
	{
		List<String> idNames = get(id);
		return hasAuthoritativeName(id)
			|| idNames.size() == 1 && emptyName(id).equals(idNames.get(0));
	}

	public String preferredName(int id)
	{
		List<String> idNames = get(id);
		return idNames.isEmpty() ? placeholder(id) : idNames.get(0);
	}

	public static String placeholder(int id)
	{
		return String.format("SOUND_UNK_%04X", id);
	}

	public static String emptyName(int id)
	{
		return String.format("SOUND_EMPTY_%04X", id);
	}

	public static String unusedName(int id)
	{
		return String.format("SOUND_UNUSED_%04X", id);
	}

	public static String invalidName(int id)
	{
		return String.format("SOUND_INVALID_%04X", id);
	}

	private static boolean isGeneratedName(int id, String name)
	{
		return placeholder(id).equals(name)
			|| emptyName(id).equals(name)
			|| unusedName(id).equals(name)
			|| invalidName(id).equals(name);
	}

	private void add(int id, String name)
	{
		if (isRawSoundID(id))
			names.computeIfAbsent(id, ignored -> new ArrayList<>()).add(name);
	}

	public static SfxNames loadBundled() throws IOException
	{
		InputStream stream = SfxNames.class.getResourceAsStream("/sfx.txt");
		if (stream == null)
			throw new IOException("Missing bundled sfx.txt");

		try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
			return readTable(reader);
		}
	}

	public static SfxNames load(Path path) throws IOException
	{
		List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
		if (lines.stream().anyMatch(line -> line.contains("enum SoundIDs")))
			return readCEnum(lines);
		return readTable(lines);
	}

	private static SfxNames readTable(BufferedReader reader) throws IOException
	{
		List<String> lines = new ArrayList<>();
		for (String line; (line = reader.readLine()) != null;)
			lines.add(line);
		return readTable(lines);
	}

	private static SfxNames readTable(List<String> lines)
	{
		SfxNames result = new SfxNames();
		for (String line : lines) {
			Matcher matcher = TABLE_ROW.matcher(line);
			if (matcher.matches())
				result.add(Integer.parseInt(matcher.group(1), 16), matcher.group(2));
		}
		return result;
	}

	private static SfxNames readCEnum(List<String> lines)
	{
		SfxNames result = new SfxNames();
		Map<String, Integer> values = new LinkedHashMap<>();
		values.put("SOUND_ID_UNK", 0x2000);
		boolean inEnum = false;

		for (String line : lines) {
			if (!inEnum) {
				inEnum = line.contains("enum SoundIDs");
				continue;
			}
			if (line.contains("};"))
				break;

			Matcher matcher = ENUM_ROW.matcher(line);
			if (!matcher.find())
				continue;

			String name = matcher.group(1);
			int value = 0;
			boolean valid = true;
			for (String token : matcher.group(2).trim().split("\\|")) {
				token = token.trim();
				Integer known = values.get(token);
				try {
					value |= known != null ? known : Integer.decode(token);
				}
				catch (NumberFormatException e) {
					valid = false;
					break;
				}
			}
			if (valid) {
				values.put(name, value);
				result.add(value, name);
			}
		}
		return result;
	}

	public static boolean isRawSoundID(int id)
	{
		return (id >= 0x0001 && id <= 0x0400) || (id >= 0x2001 && id <= 0x2140);
	}
}

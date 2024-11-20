package app.helper;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.Environment;
import app.StarRodException;
import app.input.IOUtils;
import game.shared.ProjectDatabase;
import game.shared.lib.LibEntry;

public class DecompDiffer
{
	private static final Pattern LinePattern = Pattern.compile("(\\S+)\\s*=\\s*0x(\\S+); // ?(.+)?");
	private static final Matcher LineMatcher = LinePattern.matcher("");

	private static final Pattern InfoPattern = Pattern.compile("(\\S+):(\\S+)");
	private static final Matcher InfoMatcher = InfoPattern.matcher("");

	public static void main(String[] args) throws IOException
	{
		if (args.length != 1)
			throw new StarRodException("Expected arg: symbols filename");

		File f = new File(args[0]);
		if (!f.exists())
			throw new StarRodException("Could not find symbols file: " + args[0]);

		HashMap<Integer, String> decompOffsetMap = new HashMap<>();

		Environment.initialize();

		for (String line : IOUtils.readPlainTextFile(f)) {
			// D_80234810_6BBA10 = 0x80234810; // type:data rom:0x6BBA10

			LineMatcher.reset(line);

			if (!LineMatcher.matches()) {
				System.out.println("CAN'T PARSE LINE: " + line);
				continue;
			}

			String name = LineMatcher.group(1);
			String addrStr = LineMatcher.group(2);
			String comment = LineMatcher.group(3);

			if (comment != null) {
				String type = null;
				int romOffset = -1;

				String[] tokens = comment.trim().split("\\s+");
				for (String s : tokens) {
					if (s.equals("!"))
						continue;

					InfoMatcher.reset(s);
					if (!InfoMatcher.matches())
						System.out.println("CAN'T PARSE COMMENT: " + line);

					String key = InfoMatcher.group(1);
					String val = InfoMatcher.group(2);

					switch (key.toLowerCase()) {
						case "rom":
							romOffset = Integer.parseInt(val.substring(2), 16);
							decompOffsetMap.put(romOffset, name);
							break;
						case "type":
							type = val;
							break;
						case "size":
						case "struct":
						case "dead":
							break;
						default:
					}
				}

				if (romOffset >= 0) {
					LibEntry e = ProjectDatabase.rom.libOffsetMap.get(romOffset);
					if (e != null && !e.name.equals(name)) {
						System.out.printf("%08X %08X %s --> %s%n", e.offset, e.address, name, e.name);
					}
				}
			}
		}

		for (Entry<Integer, LibEntry> entry : ProjectDatabase.rom.libOffsetMap.entrySet()) {
			int key = entry.getKey();
			LibEntry e = entry.getValue();
			if (!e.isFunction())
				continue;
			if (!decompOffsetMap.containsKey(key))
				System.out.printf("%08X %08X %s --> %s%n", e.offset, e.address, "MISSING", e.name);
		}

		Environment.exit();
	}
}

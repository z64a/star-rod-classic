package input.patch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import app.input.IOUtils;
import app.input.Line;
import app.input.PatchFileParser;
import app.input.PatchFileParser.PatchUnit;
import app.input.StreamSource;
import util.CaseInsensitiveMap;

public final class PatchFileParserTestSupport
{
	private static final String ResourceRoot = "/input/patch/";

	private PatchFileParserTestSupport()
	{}

	public static List<PatchUnit> parse(String resource) throws IOException
	{
		return parse(resource, new CaseInsensitiveMap<>());
	}

	public static List<PatchUnit> parse(String resource, CaseInsensitiveMap<String> rules) throws IOException
	{
		InputStream input = PatchFileParserTestSupport.class.getResourceAsStream(ResourceRoot + resource);
		assertNotNull(input, "Missing test resource: " + resource);

		try (InputStream stream = input) {
			List<Line> lines = IOUtils.readPlainInputStream(new StreamSource(resource), stream);
			return PatchFileParser.parse(lines, rules);
		}
	}

	public static PatchUnit parseUnit(String resource) throws IOException
	{
		return parseUnit(resource, new CaseInsensitiveMap<>());
	}

	public static PatchUnit parseUnit(String resource, CaseInsensitiveMap<String> rules) throws IOException
	{
		List<PatchUnit> units = parse(resource, rules);
		assertEquals(1, units.size(), "Expected one patch unit in " + resource);
		return units.get(0);
	}

	public static List<String> body(PatchUnit unit)
	{
		List<String> body = new ArrayList<>(unit.body.size());
		for (Line line : unit.body)
			body.add(line.str);
		return body;
	}
}

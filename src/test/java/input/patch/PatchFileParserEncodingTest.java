package input.patch;

import static input.patch.PatchFileParserTestSupport.body;
import static input.patch.PatchFileParserTestSupport.parseUnit;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import app.input.PatchFileParser.PatchUnit;

public class PatchFileParserEncodingTest
{
	@Test
	public void readsUtf8PatchFileWithoutLosingCharacters() throws Exception
	{
		PatchUnit unit = parseUnit("encoding/utf8.patch");

		assertEquals("encoding/utf8.patch", unit.source.getName());
		assertEquals(List.of("\"Star Rod \u30B9\u30BF\u30FC\u30ED\u30C3\u30C9\""), body(unit));
	}
}

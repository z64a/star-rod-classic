package input.patch;

import static input.patch.PatchFileParserTestSupport.body;
import static input.patch.PatchFileParserTestSupport.parseUnit;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.util.List;

import org.junit.jupiter.api.Test;

import app.input.IOUtils;
import app.input.InputFileException;
import app.input.PatchFileParser.PatchUnit;
import app.input.StreamSource;

public class PatchFileParserEncodingTest
{
	@Test
	public void readsUtf8PatchFileWithoutLosingCharacters() throws Exception
	{
		PatchUnit unit = parseUnit("encoding/utf8.patch");

		assertEquals("encoding/utf8.patch", unit.source.getName());
		assertEquals(List.of("\"Star Rod \u30B9\u30BF\u30FC\u30ED\u30C3\u30C9\""), body(unit));
	}

	@Test
	public void acceptsUtf8BomBeforeFirstDeclaration() throws Exception
	{
		PatchUnit unit = parseUnit("encoding/utf8-bom.patch");

		assertEquals("#new:Data $Bom", unit.declaration.str);
		assertEquals(List.of("BODY"), body(unit));
	}

	@Test
	public void rejectsMalformedUtf8()
	{
		byte[] malformedPatch = {
			'#', 'n', 'e', 'w', ':', 'D', 'a', 't', 'a', ' ', '$', 'B', 'a', 'd', ' ', '{', '\n',
			(byte) 0xC3, 0x28, '\n', '}'
		};

		assertThrows(InputFileException.class, () -> IOUtils.readPlainInputStream(
			new StreamSource("malformed-utf8.patch"), new ByteArrayInputStream(malformedPatch)));
	}
}

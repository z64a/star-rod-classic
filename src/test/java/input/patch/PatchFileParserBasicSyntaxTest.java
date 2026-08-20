package input.patch;

import static input.patch.PatchFileParserTestSupport.body;
import static input.patch.PatchFileParserTestSupport.parse;
import static input.patch.PatchFileParserTestSupport.parseUnit;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import app.input.InputFileException;
import app.input.PatchFileParser.PatchUnit;

public class PatchFileParserBasicSyntaxTest
{
	@Test
	public void parsesSeparateAndInlineBlocks() throws Exception
	{
		List<PatchUnit> units = parse("basic/block-layouts.patch");

		assertEquals(2, units.size());

		PatchUnit separate = units.get(0);
		assertEquals("#new:Data $Separate", separate.declaration.str);
		assertEquals(List.of("Alpha Beta"), body(separate));
		assertEquals(1, separate.startLineNum);
		assertEquals(4, separate.endLineNum);

		PatchUnit inline = units.get(1);
		assertEquals("@Script $Inline", inline.declaration.str);
		assertEquals(List.of("Gamma Delta"), body(inline));
		assertEquals(5, inline.startLineNum);
		assertEquals(5, inline.endLineNum);
		assertFalse(inline.parsedAsString);
	}

	@Test
	public void preservesNestedBracesAsBodyLines() throws Exception
	{
		PatchUnit unit = parseUnit("basic/nested-braces.patch");

		assertEquals(List.of("Outer", "{", "Inner", "{", "Value", "}", "Tail", "}", "After"), body(unit));
	}

	@Test
	public void rejectsBlockWithoutClosingBrace()
	{
		InputFileException exception = assertThrows(InputFileException.class,
			() -> parse("basic/missing-close-brace.patch"));

		assertEquals("Missing } -- brace was not closed by end of file!", exception.getMessage());
		assertEquals("basic/missing-close-brace.patch [Line 3]", exception.getOrigin());
	}

	@Test
	public void rejectsClosingBraceWithoutDeclaration()
	{
		InputFileException exception = assertThrows(InputFileException.class,
			() -> parse("basic/closing-brace-without-declaration.patch"));

		assertEquals("Closing brace encountered without declaration.", exception.getMessage());
		assertEquals("basic/closing-brace-without-declaration.patch [Line 1]", exception.getOrigin());
	}
}

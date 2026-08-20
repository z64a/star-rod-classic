package input.patch;

import static input.patch.PatchFileParserTestSupport.body;
import static input.patch.PatchFileParserTestSupport.parse;
import static input.patch.PatchFileParserTestSupport.parseUnit;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import app.input.InputFileException;
import app.input.PatchFileParser.PatchUnit;

public class PatchFileParserStringTest
{
	@Test
	public void recognizesStringAndMessageDeclarationForms() throws Exception
	{
		List<PatchUnit> units = parse("strings/declaration-forms.patch");

		assertEquals(3, units.size());
		assertEquals("#string $Classic", units.get(0).declaration.str);
		assertEquals("#new:String $New", units.get(1).declaration.str);
		assertEquals("#export:Message $Exported", units.get(2).declaration.str);
		for (PatchUnit unit : units)
			assertTrue(unit.parsedAsString);
	}

	@Test
	public void preservesEmptyLinesAndWhitespaceInStringBodies() throws Exception
	{
		PatchUnit unit = parseUnit("strings/whitespace.patch");

		assertEquals(List.of("  Hello", "", "World  [End]"), body(unit));
	}

	@Test
	public void reportsUnclosedQuotedLiteralAtItsOpeningLine()
	{
		InputFileException exception = assertThrows(InputFileException.class,
			() -> parse("strings/unclosed-quoted-literal.patch"));

		assertEquals("Missing \" -- string was not closed by end of file!", exception.getMessage());
		assertEquals("strings/unclosed-quoted-literal.patch [Line 2]", exception.getOrigin());
	}
}

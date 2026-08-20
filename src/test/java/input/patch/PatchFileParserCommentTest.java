package input.patch;

import static input.patch.PatchFileParserTestSupport.body;
import static input.patch.PatchFileParserTestSupport.parse;
import static input.patch.PatchFileParserTestSupport.parseUnit;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import app.input.InputFileException;
import app.input.PatchFileParser.PatchUnit;

public class PatchFileParserCommentTest
{
	@Test
	public void removesPatchCommentsAndPreservesLiteralCommentCharacters() throws Exception
	{
		PatchUnit unit = parseUnit("comments/comments.patch");

		assertEquals(List.of(
			"Alpha",
			"Beta Gamma",
			"Delta % literal",
			"\"100% /% literal %/\"",
			"A /* untouched */ B",
			"A // untouched"), body(unit));
	}

	@Test
	public void reportsUnclosedCommentAtItsOpeningLine()
	{
		InputFileException exception = assertThrows(InputFileException.class,
			() -> parse("comments/unclosed-comment.patch"));

		assertEquals("Missing %/ -- comment was not closed by end of file!", exception.getMessage());
		assertEquals("comments/unclosed-comment.patch [Line 2]", exception.getOrigin());
	}
}

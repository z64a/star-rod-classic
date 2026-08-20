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
import util.CaseInsensitiveMap;

public class PatchFileParserConditionTest
{
	@Test
	public void ifSelectsFirstMatchingBranch() throws Exception
	{
		CaseInsensitiveMap<String> rules = new CaseInsensitiveMap<>();
		rules.put("Mode", "A");

		PatchUnit unit = parseUnit("conditions/branches.patch", rules);

		assertEquals(List.of("MODE_A"), body(unit));
	}

	@Test
	public void elseIfSelectsLaterMatchingBranch() throws Exception
	{
		CaseInsensitiveMap<String> rules = new CaseInsensitiveMap<>();
		rules.put("Mode", "B");

		PatchUnit unit = parseUnit("conditions/branches.patch", rules);

		assertEquals(List.of("MODE_B"), body(unit));
	}

	@Test
	public void elseHandlesUnmatchedConditions() throws Exception
	{
		CaseInsensitiveMap<String> rules = new CaseInsensitiveMap<>();
		rules.put("Mode", "C");

		PatchUnit unit = parseUnit("conditions/branches.patch", rules);

		assertEquals(List.of("OTHER"), body(unit));
	}

	@Test
	public void elseIfNotSelectsNonmatchingValue() throws Exception
	{
		CaseInsensitiveMap<String> rules = new CaseInsensitiveMap<>();
		rules.put("Mode", "C");

		PatchUnit unit = parseUnit("conditions/elseifnot.patch", rules);

		assertEquals(List.of("NOT_B"), body(unit));
	}

	@Test
	public void elseIfNotDoesNotOverrideEarlierMatch() throws Exception
	{
		CaseInsensitiveMap<String> rules = new CaseInsensitiveMap<>();
		rules.put("Mode", "A");

		PatchUnit unit = parseUnit("conditions/elseifnot.patch", rules);

		assertEquals(List.of("MODE_A"), body(unit));
	}

	@Test
	public void nestedConditionDoesNotLeakFromSkippedParent() throws Exception
	{
		CaseInsensitiveMap<String> rules = new CaseInsensitiveMap<>();
		rules.put("Outer", "No");
		rules.put("Inner", "Yes");

		PatchUnit unit = parseUnit("conditions/nested.patch", rules);

		assertEquals(List.of("NOT_OUTER", "TAIL"), body(unit));
	}

	@Test
	public void nestedConditionsRemainActiveWhenParentsMatch() throws Exception
	{
		CaseInsensitiveMap<String> rules = new CaseInsensitiveMap<>();
		rules.put("Outer", "Yes");
		rules.put("Inner", "Yes");

		PatchUnit unit = parseUnit("conditions/nested.patch", rules);

		assertEquals(List.of("OUTER", "INNER", "OUTER_TAIL", "TAIL"), body(unit));
	}

	@Test
	public void valueDirectivesReplaceNamesAndBodyText() throws Exception
	{
		CaseInsensitiveMap<String> rules = new CaseInsensitiveMap<>();
		rules.put("Name", "Generated");
		rules.put("Left", "cost$5");
		rules.put("Words", "Alpha Beta");

		PatchUnit unit = parseUnit("conditions/value-replacements.patch", rules);

		assertEquals("#new:Data $Generated", unit.declaration.str);
		assertEquals(List.of("cost$5-value", "Before Alpha Beta After"), body(unit));
	}

	@Test
	public void undefinedValueInInactiveBranchIsIgnored() throws Exception
	{
		PatchUnit unit = parseUnit("conditions/inactive-value.patch");

		assertEquals(List.of("KEPT"), body(unit));
	}

	@Test
	public void rejectsElseWithoutIf()
	{
		InputFileException exception = assertThrows(InputFileException.class,
			() -> parse("conditions/else-without-if.patch"));

		assertEquals("Else found without initial If: ELSE", exception.getMessage());
		assertEquals("conditions/else-without-if.patch [Line 2]", exception.getOrigin());
	}

	@Test
	public void rejectsElseIfAfterElse()
	{
		CaseInsensitiveMap<String> rules = new CaseInsensitiveMap<>();
		rules.put("Mode", "C");

		InputFileException exception = assertThrows(InputFileException.class,
			() -> parse("conditions/elseif-after-else.patch", rules));

		assertEquals("ElseIf found after Else: ELSEIF:Mode:B", exception.getMessage());
		assertEquals("conditions/elseif-after-else.patch [Line 6]", exception.getOrigin());
	}

	@Test
	public void rejectsUnclosedIfAtItsOpeningLine()
	{
		InputFileException exception = assertThrows(InputFileException.class,
			() -> parse("conditions/unclosed-if.patch"));

		assertEquals("If directive is not closed: IF:Enabled", exception.getMessage());
		assertEquals("conditions/unclosed-if.patch [Line 2]", exception.getOrigin());
	}
}

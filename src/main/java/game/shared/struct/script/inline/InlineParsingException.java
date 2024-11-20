package game.shared.struct.script.inline;

import app.input.Line;
import game.shared.encoder.ScriptParsingException;

public class InlineParsingException extends ScriptParsingException
{
	public InlineParsingException(Line line, String msg)
	{
		super(line, String.format("Error with inline script expression! %n%s %n%s", msg, line.trimmedInput()));
	}

	public InlineParsingException(Line line, String fmt, Object ... args)
	{
		super(line, String.format("Error with inline script expression! %n%s %n%s", String.format(fmt, args), line.trimmedInput()));
	}
}

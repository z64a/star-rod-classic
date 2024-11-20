package game.shared.encoder;

import app.input.InputFileException;
import app.input.Line;

public class ScriptParsingException extends InputFileException
{
	public ScriptParsingException(Line line, String msg)
	{
		super(line, msg);
	}

	public ScriptParsingException(Line line, String format, Object ... args)
	{
		super(line, format, args);
	}
}

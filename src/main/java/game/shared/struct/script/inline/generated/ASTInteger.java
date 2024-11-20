package game.shared.struct.script.inline.generated;

import app.input.InvalidInputException;
import game.shared.DataUtils;
import game.shared.struct.script.inline.ValueType;

public class ASTInteger extends ASTNode
{
	public ASTInteger()
	{
		super(InlineParserTreeConstants.JJTINTEGER);
	}

	public ASTInteger(int id)
	{
		super(id);
	}

	public ASTInteger(InlineParser p, int id)
	{
		super(p, id);
	}

	public void setValue(int value)
	{
		intValue = value;
		isConst = true;
		type = ValueType.INT;
	}

	public void setValue(String value)
	{
		try {
			setValue(DataUtils.parseIntString(value));
		}
		catch (InvalidInputException e) {
			throw new IllegalStateException(e.getMessage());
		}
	}

	@Override
	public String toString()
	{
		return super.toString() + ": " + intValue + "`";
	}

	@Override
	public Object jjtAccept(InlineParserVisitor visitor, Object data)
	{
		return visitor.visit(this, data);
	}
}

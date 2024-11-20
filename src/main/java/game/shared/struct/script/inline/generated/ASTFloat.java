package game.shared.struct.script.inline.generated;

import game.shared.struct.script.inline.ValueType;

public class ASTFloat extends ASTNode
{
	public ASTFloat()
	{
		super(InlineParserTreeConstants.JJTFLOAT);
	}

	public ASTFloat(int id)
	{
		super(id);
	}

	public ASTFloat(InlineParser p, int id)
	{
		super(p, id);
	}

	public void setValue(float value)
	{
		floatValue = value;
		isConst = true;
		type = ValueType.FLOAT;
	}

	public void setValue(String value)
	{
		setValue(Float.parseFloat(value));
	}

	@Override
	public String toString()
	{
		return super.toString() + ": " + floatValue;
	}

	@Override
	public Object jjtAccept(InlineParserVisitor visitor, Object data)
	{
		return visitor.visit(this, data);
	}
}

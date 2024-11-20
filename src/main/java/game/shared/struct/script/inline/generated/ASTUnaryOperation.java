package game.shared.struct.script.inline.generated;

import game.shared.struct.script.inline.UnaryOperator;
import game.shared.struct.script.inline.ValueType;

public class ASTUnaryOperation extends ASTNode
{
	private UnaryOperator operator;

	public ASTUnaryOperation(int id)
	{
		super(id);
	}

	public ASTUnaryOperation(InlineParser p, int id)
	{
		super(p, id);
	}

	public void setOperator(String s)
	{
		this.operator = UnaryOperator.getOperator(s);

		if (operator == UnaryOperator.INT)
			type = ValueType.INT;
		else if (operator == UnaryOperator.FLT)
			type = ValueType.FLOAT;
	}

	public UnaryOperator getOperator()
	{
		return operator;
	}

	@Override
	public String toString()
	{
		return super.toString() + ": " + operator.toString();
	}

	@Override
	public Object jjtAccept(InlineParserVisitor visitor, Object data)
	{
		return visitor.visit(this, data);
	}
}

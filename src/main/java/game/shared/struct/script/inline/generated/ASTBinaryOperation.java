package game.shared.struct.script.inline.generated;

import game.shared.struct.script.inline.BinaryOperator;

public class ASTBinaryOperation extends ASTNode
{
	private BinaryOperator operator;

	public ASTBinaryOperation()
	{
		this(InlineParserTreeConstants.JJTBINARYOPERATION);
	}

	public ASTBinaryOperation(int id)
	{
		super(id);
	}

	public ASTBinaryOperation(InlineParser p, int id)
	{
		super(p, id);
	}

	public void setOperator(String s)
	{
		this.operator = BinaryOperator.getOperator(s);
	}

	public void setOperator(BinaryOperator operator)
	{
		this.operator = operator;
	}

	public BinaryOperator getOperator()
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

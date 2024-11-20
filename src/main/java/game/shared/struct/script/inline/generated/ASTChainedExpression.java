package game.shared.struct.script.inline.generated;

import java.util.LinkedList;
import java.util.List;

import game.shared.struct.script.inline.BinaryOperator;

public class ASTChainedExpression extends ASTNode
{
	private List<BinaryOperator> operators = new LinkedList<>();

	public ASTChainedExpression(int id)
	{
		super(id);
	}

	public ASTChainedExpression(InlineParser p, int id)
	{
		super(p, id);
	}

	public void addOperator(String s)
	{
		operators.add(BinaryOperator.getOperator(s));
	}

	public BinaryOperator getOperator(int i)
	{
		return operators.get(i);
	}

	@Override
	public String toString()
	{
		StringBuilder sb = new StringBuilder(super.toString());
		sb.append(":");

		for (BinaryOperator op : operators)
			sb.append(" ").append(op.toString());

		return sb.toString();
	}

	@Override
	public Object jjtAccept(InlineParserVisitor visitor, Object data)
	{
		return visitor.visit(this, data);
	}
}

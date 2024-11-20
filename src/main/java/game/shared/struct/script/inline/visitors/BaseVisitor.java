package game.shared.struct.script.inline.visitors;

import game.shared.struct.script.inline.generated.ASTAssign;
import game.shared.struct.script.inline.generated.ASTBinaryOperation;
import game.shared.struct.script.inline.generated.ASTChainedExpression;
import game.shared.struct.script.inline.generated.ASTConstant;
import game.shared.struct.script.inline.generated.ASTFloat;
import game.shared.struct.script.inline.generated.ASTInteger;
import game.shared.struct.script.inline.generated.ASTLine;
import game.shared.struct.script.inline.generated.ASTOffset;
import game.shared.struct.script.inline.generated.ASTPointer;
import game.shared.struct.script.inline.generated.ASTUnaryOperation;
import game.shared.struct.script.inline.generated.ASTVariable;
import game.shared.struct.script.inline.generated.InlineParserVisitor;
import game.shared.struct.script.inline.generated.SimpleNode;

public abstract class BaseVisitor implements InlineParserVisitor
{
	@Override
	public Object visit(SimpleNode node, Object data)
	{
		return node.childrenAccept(this, data);
	}

	@Override
	public Object visit(ASTLine node, Object data)
	{
		return visit((SimpleNode) node, data);
	}

	public Object visit(ASTAssign node, Object data)
	{
		return visit((SimpleNode) node, data);
	}

	@Override
	public Object visit(ASTChainedExpression node, Object data)
	{
		return visit((SimpleNode) node, data);
	}

	@Override
	public Object visit(ASTUnaryOperation node, Object data)
	{
		return visit((SimpleNode) node, data);
	}

	@Override
	public Object visit(ASTBinaryOperation node, Object data)
	{
		return visit((SimpleNode) node, data);
	}

	public Object visit(ASTOffset node, Object data)
	{
		return visit((SimpleNode) node, data);
	}

	@Override
	public Object visit(ASTVariable node, Object data)
	{
		return visit((SimpleNode) node, data);
	}

	@Override
	public Object visit(ASTConstant node, Object data)
	{
		return visit((SimpleNode) node, data);
	}

	@Override
	public Object visit(ASTPointer node, Object data)
	{
		return visit((SimpleNode) node, data);
	}

	@Override
	public Object visit(ASTInteger node, Object data)
	{
		return visit((SimpleNode) node, data);
	}

	@Override
	public Object visit(ASTFloat node, Object data)
	{
		return visit((SimpleNode) node, data);
	}
}

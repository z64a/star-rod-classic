package game.shared.struct.script.inline.generated;

import game.shared.struct.script.inline.ValueType;

public class ASTAssign extends ASTNode
{
	private String outVar;

	public ASTAssign(boolean assignAsFloat, String outVariable, ASTLine root)
	{
		super(-1);

		setVarName(outVariable);
		type = assignAsFloat ? ValueType.FLOAT : ValueType.INT;

		ASTNode child = (ASTNode) root.jjtGetChild(0);
		jjtAddChild(child, 0);
		child.jjtSetParent(this);
	}

	public void setVarName(String name)
	{
		this.outVar = name;
	}

	public String getVarName()
	{
		return outVar;
	}

	@Override
	public String toString()
	{
		return "Assign " + ((type == ValueType.FLOAT) ? "(float) " : "(int) ") + outVar;
	}

	@Override
	public Object jjtAccept(InlineParserVisitor visitor, Object data)
	{
		return visitor.visit(this, data);
	}
}

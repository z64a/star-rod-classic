package game.shared.struct.script.inline.generated;

import game.shared.struct.script.inline.ConstantDatabase;
import game.shared.struct.script.inline.ValueType;
import game.shared.struct.script.inline.visitors.VisitorException;

public class ASTConstant extends ASTNode
{
	private String name;

	public ASTConstant(int id)
	{
		super(id);
	}

	public ASTConstant(InlineParser p, int id)
	{
		super(p, id);
	}

	public void setName(String name)
	{
		this.name = name;
	}

	public void assignValue(ConstantDatabase db)
	{
		if (!db.hasConstant(name))
			throw new VisitorException("Unknown constant: " + name);

		intValue = db.getConstantValue(name);
		isConst = true;
		type = ValueType.INT;
	}

	@Override
	public String toString()
	{
		return super.toString() + ": " + name;
	}

	@Override
	public Object jjtAccept(InlineParserVisitor visitor, Object data)
	{
		return visitor.visit(this, data);
	}
}

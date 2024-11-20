package game.shared.struct.script.inline.generated;

public class ASTVariable extends ASTNode
{
	private String name;

	public ASTVariable()
	{
		super(InlineParserTreeConstants.JJTVARIABLE);
	}

	public ASTVariable(int id)
	{
		super(id);
	}

	public ASTVariable(InlineParser p, int id)
	{
		super(p, id);
	}

	public void setName(String name)
	{
		this.name = name;
	}

	public String getName()
	{
		return name;
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

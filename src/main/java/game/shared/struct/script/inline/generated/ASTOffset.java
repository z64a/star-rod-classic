package game.shared.struct.script.inline.generated;

public class ASTOffset extends ASTNode
{
	public ASTOffset(int id)
	{
		super(id);
	}

	public ASTOffset(InlineParser p, int id)
	{
		super(p, id);
	}

	@Override
	public Object jjtAccept(InlineParserVisitor visitor, Object data)
	{
		return visitor.visit(this, data);
	}
}

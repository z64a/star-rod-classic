package game.shared.struct.script.inline.generated;

public class ASTPointer extends ASTNode
{
	private String name;
	private int offset;

	public ASTPointer(int id)
	{
		super(id);
	}

	public ASTPointer(InlineParser p, int id)
	{
		super(p, id);
	}

	public void setName(String name)
	{
		this.name = name;
	}

	public void addOffset(int offset)
	{
		this.offset += offset;
	}

	public String getName()
	{
		if (offset == 0)
			return name;
		else
			return String.format("%s[%X]", name, offset);
	}

	/*
	public void assignValue(LocalDatabase db)
	{
		if(!db.hasPointer(name))
			throw new VisitorException("Unknown pointer: " + name);

		intValue = db.getPointerAddress(name);
		setType(ValueType.CONST_INT);
	}
	*/

	@Override
	public String toString()
	{
		return super.toString() + ": " + getName();
	}

	@Override
	public Object jjtAccept(InlineParserVisitor visitor, Object data)
	{
		return visitor.visit(this, data);
	}
}

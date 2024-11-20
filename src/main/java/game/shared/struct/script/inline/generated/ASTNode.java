package game.shared.struct.script.inline.generated;

import game.shared.struct.script.inline.ValueType;

public abstract class ASTNode extends SimpleNode
{
	public ValueType type = ValueType.UNK;
	public boolean isConst;

	public int intValue;
	public float floatValue;

	public ASTNode(int i)
	{
		super(i);
	}

	public ASTNode(InlineParser p, int i)
	{
		super(p, i);
	}

	public boolean isZero()
	{
		if (!isConst)
			return false;

		switch (type) {
			case INT:
				return intValue == 0;
			case FLOAT:
				return floatValue == 0.0f;
			case UNK:
			case DC:
		}
		return false;
	}

	public boolean isOne()
	{
		if (!isConst)
			return false;

		switch (type) {
			case INT:
				return intValue == 1;
			case FLOAT:
				return floatValue == 1.0f;
			case UNK:
			case DC:
		}
		return false;
	}

	@Override
	public String toString()
	{
		switch (type) {
			case INT:
				if (isConst)
					return String.format("(d) [%d`] %s", intValue, super.toString());
				else
					return "(d) " + super.toString();
			case FLOAT:
				if (isConst)
					return String.format("(f) [%f] %s", floatValue, super.toString());
				else
					return "(f) " + super.toString();
			case UNK:
				return "(?) " + super.toString();
			case DC:
				return "(X) " + super.toString();
		}

		return "INVALID " + type;
	}
}

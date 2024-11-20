package game.shared.struct.script.inline;

public interface ConstantDatabase
{
	public boolean hasConstant(String name);

	public Integer getConstantValue(String name);
}

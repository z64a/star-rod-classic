package game.sprite.editor.animators;

public enum ParentType
{
	// @formatter:off
	Component	("Component"),
	s8000		("8000"),
	s8201		("8201"),
	s8202		("8202");
	// @formatter:on

	private final String name;

	private ParentType(String name)
	{
		this.name = name;
	}

	@Override
	public String toString()
	{
		return name;
	}
}

package game.shared.struct.miniscript;

public class ItemEntityScript extends Miniscript
{
	public static final ItemEntityScript instance = new ItemEntityScript();

	private ItemEntityScript()
	{
		// @formatter:off
		super("ItemEntityScript",
			new CommandBuilder(0, "End",			ENDS),
			new CommandBuilder(1, IconImageArg.SET_ICON_IMG_CMD_NAME, new DecArg(false), new IconImageArg()),
			new CommandBuilder(2, "Restart",		INDENT_LESS_NOW),
			new CommandBuilder(3, "Loop",			INDENT_MORE_AFTER),
			new CommandBuilder(4, "RandomRestart",	INDENT_LESS_NOW | INDENT_MORE_AFTER, new DecArg(false), new DecArg(false))
			);
		// @formatter:on
	}
}

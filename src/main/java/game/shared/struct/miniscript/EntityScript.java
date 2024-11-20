package game.shared.struct.miniscript;

import static game.shared.StructTypes.FunctionT;

public class EntityScript extends Miniscript
{
	public static final EntityScript instance = new EntityScript();

	/*
	0   End             ()                              breaks
	1   Jump            ( int* newReadPos )
	2   Call            ( code* func (entity*) )
	3   SetCallback     ( ???, code* callback )         breaks
	4   Goto            ( int index (0-2 inclusive) )
	5   Label           ( int index (0-2 inclusive) )
	6   Cmd06           ()      ????    sets entity->flags 1000000
	7   SetFlags        ( int bits )
	8   ClearFlags      ( int bits )
	9   PlaySound       ( int soundID )
	*/

	private EntityScript()
	{
		// @formatter:off
		super("EntityScript",
			new CommandBuilder(0, "End",			ENDS),
			new CommandBuilder(1, "Jump",			new HexArg()), //TODO PointerArg(EntityScriptT)),
			new CommandBuilder(2, "Call",			new PointerArg(FunctionT)),
			new CommandBuilder(3, "SetHandler",		new HexArg(), new PointerArg(FunctionT)),
			new CommandBuilder(4, "Goto",			ENDS, new HexArg()),
			new CommandBuilder(5, "Label",			new HexArg()),
			new CommandBuilder(6, "RestartBoundScript"),
			new CommandBuilder(7, "SetFlags",		new HexArg()),
			new CommandBuilder(8, "ClearFlags",		new HexArg()),
			new CommandBuilder(9, "PlaySound",		new EnumArg("Sound")));
		// @formatter:on
	}
}

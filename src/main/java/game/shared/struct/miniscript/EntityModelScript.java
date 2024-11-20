package game.shared.struct.miniscript;

import static game.shared.StructTypes.DisplayListT;

public class EntityModelScript extends Miniscript
{
	public static final EntityModelScript instance = new EntityModelScript();

	/*
	0	Free			( )						frees current entity model
	1	Draw			( holdTime, Gfx* )		ENDS
	2	Restart			( )
	3	Jump			( pointer)
	4	SetRenderMode	( renderMode )
	5	SetFlags		( bits )
	6	ClearFlags		( bits )
	7	AppendGfx		( holdTime, Gfx[4] )	ENDS
	*/

	private EntityModelScript()
	{
		// @formatter:off
		super("EntityModelScript",
			new CommandBuilder(0, "Delete"),
			new CommandBuilder(1, "SetDisplayList",	BLOCKS, new DecArg(false), new PointerArg(DisplayListT)),
			new CommandBuilder(2, "Restart",		ENDS),
			new CommandBuilder(3, "Jump",			new HexArg()), // new PointerArg(EntityModelCommandListT)),
			new CommandBuilder(4, "SetRenderMode",	new EnumArg("RenderMode")),
			new CommandBuilder(5, "SetFlags",		new HexArg()),
			new CommandBuilder(6, "ClearFlags",		new HexArg()),
			new CommandBuilder(7, "AppendGfx",		BLOCKS, new HexArg(), new HexArg(), new HexArg(), new HexArg())
			);
		// @formatter:on
	}
}

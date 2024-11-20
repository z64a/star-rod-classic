package game.shared.encoder;

import java.util.List;

import app.input.AbstractSource;
import app.input.Line;
import game.ROM.LibScope;
import game.shared.StructTypes;
import patcher.IGlobalDatabase;

public class GlobalEncoder extends BaseDataEncoder
{
	protected GlobalEncoder(IGlobalDatabase db, AbstractSource source)
	{
		super(StructTypes.allTypes, LibScope.Common, db, null, false);
		setSource(source);
	}

	@Override
	protected void replaceExpression(Line line, String[] args, List<String> newTokenList)
	{
		super.replaceMapExpression(line, args, newTokenList);
	}
}

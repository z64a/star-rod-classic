package game.shared.encoder;

import java.util.List;

import app.input.InvalidInputException;
import app.input.Line;
import game.ROM.LibScope;
import game.map.MapIndex;
import game.shared.StructTypes;
import patcher.DefaultGlobals;
import patcher.IGlobalDatabase;
import patcher.RomPatcher;

public class DummyDataEncoder extends BaseDataEncoder
{
	public DummyDataEncoder()
	{
		super(StructTypes.mapTypes, LibScope.None, new DummyGlobalsDatabase(), null, true);
	}

	@Override
	protected void replaceExpression(Line line, String[] args, List<String> newTokenList)
	{}

	private static class DummyGlobalsDatabase implements IGlobalDatabase
	{
		@Override
		public void setGlobalPointer(DefaultGlobals global, int addr)
		{}

		@Override
		public void setGlobalPointer(String name, int addr)
		{}

		@Override
		public boolean hasGlobalPointer(String name)
		{
			return false;
		}

		@Override
		public int getGlobalPointerAddress(String name)
		{
			return 0;
		}

		@Override
		public void setGlobalConstant(DefaultGlobals global, String value)
		{}

		@Override
		public void setGlobalConstant(String name, String value)
		{}

		@Override
		public boolean hasGlobalConstant(String name)
		{
			return false;
		}

		@Override
		public String getGlobalConstant(String name)
		{
			return name;
		}

		@Override
		public int resolveStringID(String s) throws InvalidInputException
		{
			return 0;
		}

		@Override
		public boolean hasStringName(String name)
		{
			return false;
		}

		@Override
		public int getStringFromName(String name)
		{
			return 0;
		}

		@Override
		public boolean hasMapIndex(String name)
		{
			return false;
		}

		@Override
		public MapIndex getMapIndex(String name)
		{
			return null;
		}

		@Override
		public int getNpcAnimID(String spriteName, String animName, String palName)
		{
			return 0;
		}

		@Override
		public int getPlayerAnimID(String spriteName, String animName, String palName)
		{
			return 0;
		}

		@Override
		public RomPatcher getRomPatcher()
		{
			return null;
		}

	}
}

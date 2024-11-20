package patcher;

import app.input.InvalidInputException;
import game.map.MapIndex;

// maybe just get rid of this interface and let classes BaseDataEncoder take Patcher as argument
public interface IGlobalDatabase
{
	public void setGlobalPointer(DefaultGlobals global, int addr);

	public void setGlobalPointer(String name, int addr);

	public boolean hasGlobalPointer(String name);

	public int getGlobalPointerAddress(String name);

	public void setGlobalConstant(DefaultGlobals global, String value);

	public void setGlobalConstant(String name, String value);

	public boolean hasGlobalConstant(String name);

	public String getGlobalConstant(String name);

	public int resolveStringID(String s) throws InvalidInputException;

	public boolean hasStringName(String name);

	public int getStringFromName(String name);

	public boolean hasMapIndex(String name);

	public MapIndex getMapIndex(String name);

	public int getNpcAnimID(String spriteName, String animName, String palName);

	public int getPlayerAnimID(String spriteName, String animName, String palName);

	public RomPatcher getRomPatcher(); //TODO awkward
}

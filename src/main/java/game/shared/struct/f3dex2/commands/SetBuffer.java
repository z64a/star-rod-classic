package game.shared.struct.f3dex2.commands;

import app.input.InvalidInputException;
import game.shared.DataUtils;
import game.shared.decoder.BaseDataDecoder;
import game.shared.struct.f3dex2.BaseF3DEX2;
import game.shared.struct.f3dex2.DisplayList.CommandType;

public class SetBuffer extends BaseF3DEX2
{
	int addr;

	public SetBuffer(CommandType cmd, Integer ... args) throws InvalidInputException
	{
		super(cmd, args, 2);

		if ((args[0] & 0x00FFFFFF) != 0)
			throw new InvalidInputException("Invalid %s command: %08X", getName(), args[0]);

		addr = args[1];
	}

	public SetBuffer(CommandType cmd, String ... params) throws InvalidInputException
	{
		super(cmd, params, 1);
		addr = DataUtils.parseIntString(params[0]);
	}

	@Override
	public int[] assemble()
	{
		int[] encoded = new int[2];
		encoded[0] = opField;
		encoded[1] = addr;
		return encoded;
	}

	@Override
	public String getString(BaseDataDecoder decoder)
	{
		if (decoder.isLocalAddress(addr))
			return String.format("%-16s (%s)", getName(), decoder.getPointer(addr).getPointerName());
		else
			return String.format("%-16s (%08X)", getName(), addr);
	}
}

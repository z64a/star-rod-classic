package game.shared.struct.f3dex2.commands;

import app.input.InvalidInputException;
import game.shared.DataUtils;
import game.shared.decoder.BaseDataDecoder;
import game.shared.struct.f3dex2.BaseF3DEX2;
import game.shared.struct.f3dex2.DisplayList.CommandType;

//	ED [xx x][y yy] 0[m] [vv v][w ww]

public class SetScissor extends BaseF3DEX2
{
	int mode, X, Y, V, W;

	String[] modeOpt = {
			"G_SC_NON_INTERLACE",
			null, // 1
			"G_SC_EVEN_INTERLACE",
			"G_SC_ODD_INTERLACE"
	};

	public SetScissor(CommandType cmd, Integer ... args) throws InvalidInputException
	{
		super(cmd, args, 2);

		if ((args[1] & 0xFC000000) != 0)
			throw new InvalidInputException("Invalid %s command: %08X", getName(), args[1]);

		X = (args[0] >> 12) & 0xFFF;
		Y = args[0] & 0xFFF;

		V = (args[1] >> 12) & 0xFFF;
		W = args[1] & 0xFFF;

		mode = (args[1] >> 24) & 3;
	}

	public SetScissor(CommandType cmd, String ... params) throws InvalidInputException
	{
		super(cmd, params, 5);

		mode = -1;
		for (int i = 0; i < modeOpt.length; i++) {
			if (modeOpt[i] != null && modeOpt[i].equalsIgnoreCase(params[0])) {
				mode = i;
				break;
			}
		}
		if (mode < 0)
			mode = DataUtils.parseIntString(params[0]);

		X = DataUtils.parseIntString(params[1]);
		Y = DataUtils.parseIntString(params[2]);
		V = DataUtils.parseIntString(params[3]);
		W = DataUtils.parseIntString(params[4]);

		if (mode < 0 || mode > 3)
			throw new InvalidInputException("%s mode is out of range (0-3): %d", getName(), mode);
		if (X < 0 || X > 0xFFF || Y < 0 || Y > 0xFFF || V < 0 || V > 0xFFF || W < 0 || W > 0xFFF)
			throw new InvalidInputException("%s coordinates must fit in unsigned 12-bit fields", getName());
	}

	@Override
	public int[] assemble()
	{
		int[] encoded = new int[2];
		encoded[0] = opField;

		encoded[0] |= (X & 0xFFF) << 12;
		encoded[0] |= (Y & 0xFFF);

		encoded[1] |= (mode & 3) << 24;
		encoded[1] |= (V & 0xFFF) << 12;
		encoded[1] |= (W & 0xFFF);

		return encoded;
	}

	@Override
	public String getString(BaseDataDecoder decoder)
	{
		String modeName = null;
		if (mode >= 0 && mode < 4 && mode != 1)
			modeName = modeOpt[mode];
		else
			modeName = String.format("%X", mode);

		return String.format("%-16s (%s, %d`, %d`, %d`, %d`)", getName(), modeName, X, Y, V, W);
	}
}

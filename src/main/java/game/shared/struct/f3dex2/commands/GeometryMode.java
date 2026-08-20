package game.shared.struct.f3dex2.commands;

import app.input.InvalidInputException;
import game.shared.DataUtils;
import game.shared.decoder.BaseDataDecoder;
import game.shared.struct.f3dex2.BaseF3DEX2;
import game.shared.struct.f3dex2.DisplayList.CommandType;

/*
	Enables and disables certain geometry parameters (ex. lighting, front-/backface culling, Z-buffer).

	D9 [CC CC CC] [SS SS SS SS]
 */

public class GeometryMode extends BaseF3DEX2
{
	public int clearFlags = 0;
	public int setFlags = 0;

	private static enum Flag
	{
		// @formatter:off
		G_ZBUFFER				(0),
		G_SHADE					(2),
		G_CULL_FRONT			(9),
		G_CULL_BACK				(10),
		G_FOG					(16),
		G_LIGHTING				(17),
		G_TEXTURE_GEN			(18),
		G_TEXTURE_GEN_LINEAR	(19),
		G_LOD					(20),
		G_SHADING_SMOOTH		(21),
		G_CLIPPING				(23);
		// @formatter:on

		private final int mask;

		private Flag(int shift)
		{
			mask = 1 << shift;
		}

		@Override
		public String toString()
		{
			return name();
		}
	}

	public GeometryMode(CommandType cmd, Integer ... args) throws InvalidInputException
	{
		super(cmd, args, 2);

		clearFlags = ~(args[0] | 0xFF000000) & 0xFFFFFF;
		setFlags = args[1];
	}

	public GeometryMode(CommandType cmd, String ... params) throws InvalidInputException
	{
		super(cmd, params, -1);

		if (params.length < 1)
			throw new InvalidInputException("%s requires at least one parameter", getName());

		boolean setting;
		if (params[0].equalsIgnoreCase("CLEAR"))
			setting = false;
		else if (params[0].equalsIgnoreCase("SET"))
			setting = true;
		else
			throw new InvalidInputException("%s mode must be 'Set' or 'Clear', read %s", getName(), params[0]);

		for (int i = 1; i < params.length; i++) {
			if (params[i].equalsIgnoreCase("SET")) {
				if (setting)
					throw new InvalidInputException("%s includes an unexpected 'Set'", getName());
				setting = true;
				continue;
			}

			if (params[i].equalsIgnoreCase("CLEAR"))
				throw new InvalidInputException("%s may only clear flags before setting flags", getName());

			if (params[i].equalsIgnoreCase("ALL")) {
				if (setting)
					throw new InvalidInputException("%s may only use 'ALL' with 'Clear'", getName());
				clearFlags = 0xFFFFFF;
				continue;
			}

			int mask = -1;
			for (Flag flag : Flag.values()) {
				if (flag.toString().equalsIgnoreCase(params[i])) {
					mask = flag.mask;
					break;
				}
			}

			if (mask < 0)
				mask = DataUtils.parseIntString(params[i]);

			if (setting)
				setFlags |= mask;
			else
				clearFlags |= mask;
		}
	}

	@Override
	public int[] assemble()
	{
		int[] encoded = new int[2];
		encoded[0] = opField | (~clearFlags & 0xFFFFFF);
		encoded[1] = setFlags;

		return encoded;
	}

	@Override
	public String getString(BaseDataDecoder decoder)
	{
		StringBuilder sb = new StringBuilder(String.format("%-16s (", getName()));

		if (clearFlags != 0) {
			sb.append("Clear");
			if (clearFlags == 0xFFFFFF)
				sb.append(", ALL");
			else
				appendFlags(sb, clearFlags);
		}

		if (setFlags != 0 || clearFlags == 0) {
			if (clearFlags != 0)
				sb.append(", ");
			sb.append("Set");
			appendFlags(sb, setFlags);
		}

		sb.append(")");

		return sb.toString();
	}

	private static void appendFlags(StringBuilder sb, int flags)
	{
		int bits = flags;
		for (Flag flag : Flag.values()) {
			if ((flag.mask & bits) != 0) {
				sb.append(", ");
				sb.append(flag.toString());
				bits ^= flag.mask;
			}
		}

		if (bits != 0)
			sb.append(String.format(", %X", bits));
	}
}

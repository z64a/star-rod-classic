package game.map.shape;

import java.io.Serializable;

public class Property implements Serializable
{
	private static final long serialVersionUID = 1L;

	public final String[] value = new String[3];

	public Property(int v1, int v2, int v3)
	{
		value[0] = String.format("%08X", v1);
		value[1] = String.format("%08X", v2);
		value[2] = String.format("%08X", v3);
	}
}

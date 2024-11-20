package game.shared.encoder;

import game.shared.StructField;
import game.shared.struct.Struct;

@FunctionalInterface
public interface IParseField
{
	public StructField parseFieldOffset(BaseDataEncoder encoder, Struct struct, String offsetName);
}

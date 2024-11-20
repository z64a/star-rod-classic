package game.shared.encoder;

@FunctionalInterface
public interface IPatchLength
{
	// Some types need to calculate patch lengths in special ways
	public int getPatchLength(BaseDataEncoder encoder, Patch patch);
}

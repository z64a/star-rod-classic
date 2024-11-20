package game.shared.encoder;

@FunctionalInterface
public interface IReplaceSpecial
{
	public void replaceStructConstants(BaseDataEncoder encoder, Patch patch);
}

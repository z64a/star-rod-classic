package game.shared.decoder;

public final class DumpMetadata
{
	public final String sourceName;

	public final int romStart;
	public final int romEnd;

	public final int ramStart;
	public final int ramLimit;

	public final int ramEnd;
	public final int size;

	public DumpMetadata(String sourceName, int romStart, int romEnd, int ramStart, int ramLimit)
	{
		this.sourceName = sourceName;

		this.romStart = romStart;
		this.romEnd = romEnd;

		this.ramStart = ramStart;
		this.ramLimit = ramLimit;

		size = (romEnd - romStart);
		ramEnd = ramStart + size;
	}
}

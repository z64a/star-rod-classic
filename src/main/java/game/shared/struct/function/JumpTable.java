package game.shared.struct.function;

public class JumpTable implements Comparable<JumpTable>
{
	public final int baseAddress;
	public final int numEntries;

	public JumpTable(int tableAddress, int numEntries)
	{
		this.baseAddress = tableAddress;
		this.numEntries = numEntries;
	}

	@Override
	public int compareTo(JumpTable other)
	{
		return baseAddress - other.baseAddress;
	}

	@Override
	public int hashCode()
	{
		return baseAddress;
	}

	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		JumpTable other = (JumpTable) obj;
		if (baseAddress != other.baseAddress)
			return false;
		return true;
	}
}

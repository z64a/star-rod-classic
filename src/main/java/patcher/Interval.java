package patcher;

public class Interval
{
	public final String source;
	public int start;
	public int end;

	public Interval(String source, int start, int end)
	{
		this.source = source;
		this.start = start;
		this.end = end;
	}
}

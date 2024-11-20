package game.shared.struct.script;

import java.util.ArrayList;
import java.util.List;

public class ScriptFindings
{
	public final List<Integer> functionCalls = new ArrayList<>(64);

	public final List<Integer> intBuffers = new ArrayList<>();
	public final List<Integer> floatBuffers = new ArrayList<>();

	public final List<Integer> scriptExecs = new ArrayList<>(64);
	public final List<Integer> triggers = new ArrayList<>(64);
	public final List<Integer> unknownChildPointers = new ArrayList<>(32);
}

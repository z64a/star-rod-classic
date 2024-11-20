package game.shared.struct.function;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

public class FunctionScanResults
{
	public ArrayList<String[]> code = new ArrayList<>();

	public final List<Integer> localFunctionCalls = new ArrayList<>();
	public final List<Integer> libraryFunctionCalls = new ArrayList<>();
	public final List<Integer> constDoubles = new ArrayList<>();
	public final List<Integer> byteTables = new ArrayList<>();
	public final List<Integer> shortTables = new ArrayList<>();
	public final List<Integer> intTables = new ArrayList<>();
	public final List<Integer> floatTables = new ArrayList<>();
	public final List<Integer> unknownChildPointers = new ArrayList<>();

	public final TreeSet<JumpTarget> branchTargets = new TreeSet<>();

	public final TreeSet<JumpTable> jumpTables = new TreeSet<>();
	public final TreeSet<Integer> jumpTableAddresses = new TreeSet<>();
	public final TreeSet<JumpTarget> jumpTableTargets = new TreeSet<>();
}

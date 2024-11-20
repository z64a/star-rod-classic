package game.shared;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.input.IOUtils;
import app.input.InputFileException;
import game.shared.struct.script.ScriptVariable;

public class VarNameDictionary
{
	private static final Pattern EntryPattern = Pattern.compile("([0-9A-Fa-f]+)\\s*=\\s*(\\S+)(?:\\s*=\\s*(\\S+))?(?:\\s*%(.+))?");
	private static final Matcher EntryMatcher = EntryPattern.matcher("");

	public static class VarName
	{
		private final int index;
		private String defaultName = null;
		private String semanticName = null;
		public String comment = "";
		private boolean usedByVanilla;

		private VarName(ScriptVariable type, int index)
		{
			//	this.defaultName = String.format("%c%s[%X]", DataConstants.SCRIPT_VAR_PREFIX, type.getTypeName(), index);
			this.index = index;
		}
	}

	private final ScriptVariable varType;
	private VarName[] varTable;
	private HashMap<String, VarName> nameLookup;

	public VarNameDictionary(ScriptVariable type)
	{
		varType = type;
		clear();
	}

	public void clear()
	{
		varTable = new VarName[varType.getMaxIndex()];
		for (int i = 0; i < varTable.length; i++)
			varTable[i] = new VarName(varType, i);

		nameLookup = new HashMap<>();
		nameLookup.clear();
	}

	public void loadDictionary(File definitionFile) throws IOException
	{
		loadDictionary(definitionFile, true);
	}

	public void loadDictionary(File definitionFile, boolean stripComments) throws IOException
	{
		clear();

		if (!definitionFile.exists())
			return;

		List<String> lines;
		if (stripComments)
			lines = IOUtils.readFormattedTextFile(definitionFile);
		else
			lines = IOUtils.readPlainTextFile(definitionFile);

		HashSet<Integer> indexSet = new HashSet<>();

		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i);

			line = line.trim();
			if (line.startsWith("%"))
				continue;

			if (line.isEmpty())
				continue;

			EntryMatcher.reset(line);
			if (!EntryMatcher.matches())
				throw new InputFileException(definitionFile, i, "Invalid database entry: %n%s", line);

			int index = Integer.parseInt(EntryMatcher.group(1), 16);

			int max = varType.getMaxIndex();
			if (index < 0 || index >= max)
				throw new InputFileException(definitionFile, i, "%s index is out of range (0 to %X): %n%s", varType.getTypeName(), max, line);

			if (indexSet.contains(index))
				throw new InputFileException(definitionFile, i, "Duplicate %s index in file: %n%s", varType.getTypeName(), line);
			indexSet.add(index);

			boolean unused = false;

			String defaultName = SyntaxConstants.SCRIPT_VAR_PREFIX + EntryMatcher.group(2);
			if (addName(definitionFile, line, index, defaultName))
				varTable[index].defaultName = defaultName;

			String assignedName = EntryMatcher.group(3);
			if (assignedName != null) {
				if (assignedName.equalsIgnoreCase("unused")) {
					assignedName = null;
					unused = true;
				}
				else {
					assignedName = SyntaxConstants.SCRIPT_VAR_PREFIX + assignedName;
				}
			}
			if (addName(definitionFile, line, index, assignedName))
				varTable[index].semanticName = assignedName;

			if (!unused)
				varTable[index].usedByVanilla = true;

			String comment = EntryMatcher.group(4);
			if (comment != null && !comment.isEmpty())
				varTable[index].comment = comment;
		}
	}

	private boolean addName(File input, String line, int index, String name)
	{
		if (name == null || name.isEmpty())
			return false;

		if (!ScriptVariable.isValidName(name))
			throw new InputFileException(input, index,
				"Invalid name for variable: %s %n%s",
				name, line);

		VarName existing = nameLookup.get(name);
		if (existing != null && existing.index != index)
			throw new InputFileException(input, index,
				"Duplicate %s name used for indices %X and %X: %n%s",
				varType.getTypeName(), existing.index, index, line);

		nameLookup.put(name, varTable[index]);
		return true;
	}

	public ArrayList<String> getNameList()
	{
		ArrayList<String> names = new ArrayList<>();

		for (VarName var : varTable) {
			if (var.semanticName != null)
				names.add(var.semanticName);
		}

		for (VarName var : varTable) {
			if (var.defaultName != null)
				names.add(var.defaultName);
		}

		return names;
	}

	public String getName(int index)
	{
		if (index < 0 || index >= varType.getMaxIndex())
			return null;

		if (varTable[index].semanticName != null)
			return varTable[index].semanticName;
		else
			return varTable[index].defaultName;
	}

	public String getDefaultName(int index)
	{
		if (index < 0 || index >= varType.getMaxIndex())
			return null;

		return varTable[index].defaultName;
	}

	public String getComment(int index)
	{
		if (index < 0 || index >= varType.getMaxIndex())
			return null;

		return varTable[index].comment;
	}

	public Integer getIndex(String name)
	{
		VarName var = nameLookup.get(name);
		if (var == null)
			return null;
		else
			return var.index;
	}

	public boolean hasSemanticName(int index)
	{
		if (index < 0 || index >= varType.getMaxIndex())
			return false;

		return (varTable[index].semanticName != null);
	}

	public void print()
	{
		int unused = 0;
		for (VarName var : varTable) {
			if (!var.usedByVanilla)
				unused++;
		}

		System.out.printf("Loaded %d vars with %d names (%d unused)%n", varTable.length, nameLookup.size(), unused);
		for (int i = 0; i < varTable.length; i++) {
			VarName var = varTable[i];
			String assignedName = var.semanticName;
			if (assignedName == null)
				assignedName = var.usedByVanilla ? "" : "unused";
			else
				assignedName = assignedName.substring(1);
			System.out.printf("%3X : %-13s : %s%n", i, var.defaultName.substring(1), assignedName);
		}
	}
}

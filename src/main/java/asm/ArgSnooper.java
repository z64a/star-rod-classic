package asm;

import java.util.ArrayList;

public class ArgSnooper
{
	public static void main(String[] args)
	{
		ArrayList<String[]> code = new ArrayList<>();

		code.add(new String[] { "ADDIU", "A0", "R0", "FF" });
		code.add(new String[] { "CLEAR", "A1" });
		code.add(new String[] { "COPY", "A2", "A0" });
		code.add(new String[] { "JAL", "XYZ" });
		code.add(new String[] { "LA", "A3", "$Pointer" });

		new ArgSnooper(code, 3);
	}

	public String[] val = new String[4];

	public ArgSnooper(ArrayList<String[]> code, int jalLineNum)
	{
		if (code.size() > jalLineNum + 1)
			check(code.get(jalLineNum + 1));

		for (int i = jalLineNum - 1; i >= 0; i--) {
			if (i > 0) {
				String[] prevLine = code.get(i - 1);

				if (prevLine[0].equals("JAL") || prevLine[0].startsWith("B"))
					break; // we're in a delay slot
			}

			check(code.get(i));
		}

		for (int i = 0; i < val.length; i++) {
			if ("R0".equals(val[i]))
				val[i] = "0";
		}

		// System.out.printf("Found ARGS: %s %s %s %s%n", val[0], val[1], val[2], val[3]);
	}

	private void check(String[] line)
	{
		if (line[0].startsWith("."))
			return;

		/*
		for(String s : line)
			System.out.print(s + " ");
		System.out.println();
		*/

		switch (line[0]) {
			case "ADDIU":
				if (line[2].equals("R0"))
					assign(line[1], line[3], false);
				break;
			/*
			case "DADDU":
			if(line[2].equals("R0"))
				assign(line[1], line[3], false);
			else if(line[3].equals("R0"))
				assign(line[1], line[2], false);
			break;
			*/
			case "LA":
			case "LI":
			case "COPY":
				assign(line[1], line[2], false);
				break;

			case "CLEAR":
				assign(line[1], "0", false);
				break;
		}
	}

	private boolean assign(String regName, String value, boolean replace)
	{
		int index = getIndex(regName);
		if (index < 0)
			return false;

		if (!replace && val[index] != null)
			return false;

		val[index] = value;

		for (int i = 0; i < val.length; i++) {
			if (index == i)
				continue;

			if (regName.equals(val[i]))
				assign(getName(i), value, true);
		}

		return true;
	}

	private int getIndex(String regName)
	{
		switch (regName) {
			case "A0":
				return 0;
			case "A1":
				return 1;
			case "A2":
				return 2;
			case "A3":
				return 3;
		}

		return -1;
	}

	private String getName(int index)
	{
		switch (index) {
			case 0:
				return "A0";
			case 1:
				return "A1";
			case 2:
				return "A2";
			case 3:
				return "A3";
		}

		return null;
	}
}

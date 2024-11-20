package app.helper;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;

import app.Directories;
import app.Environment;
import app.Resource;
import app.Resource.ResourceType;
import app.input.IOUtils;
import app.input.InputFileException;
import app.input.InvalidInputException;
import app.input.Line;
import game.shared.lib.CType;
import game.shared.lib.CType.BaseCategory;
import game.shared.lib.CType.CField;
import util.Logger;

public class HeaderReader
{
	public static void main(String[] args) throws InvalidInputException
	{
		Environment.initialize();
		List<Line> lines = Resource.getLines(ResourceType.Basic, "global44.h");
		new HeaderReader(lines);
		Environment.exit();
	}

	//	private static final Pattern ArrayPattern = Pattern.compile("(\\S+)\\[(\\d+|0x[\\dA-Fa-f)]+)\\]");
	private static final Pattern ArrayPattern = Pattern.compile("([^ \\[]+)\\[([\\]\\[\\dxA-Fa-f]+)]");
	private static final Pattern UnnamedFieldPattern = Pattern.compile("field_0x[0-9A-Fa-f]+");

	private enum ReadState
	{
		Root,
		Struct,
		Enum // assume enums from ghidra are 1 byte :/
	}

	private ArrayList<CType> structList = new ArrayList<>();

	public HeaderReader(List<Line> lines)
	{
		// special SRC names for these
		CType.registerNewTypedef("pointer", CType.getType("ptr"));
		CType.registerNewTypedef("void*", CType.getType("code"));

		readLines(lines);

		for (CType struct : structList) {
			List<String> structFileLines = getStructFile(struct);
			File outFile = new File(Directories.DATABASE_WIP_STRUCTS + struct.specifier + ".struct");

			try {
				FileUtils.touch(outFile);
			}
			catch (IOException e) {
				Logger.printStackTrace(e);
			}

			try (PrintWriter pw = IOUtils.getBufferedPrintWriter(outFile)) {
				for (String line : structFileLines)
					pw.println(line);
			}
			catch (FileNotFoundException e) {
				Logger.printStackTrace(e);
			}
		}

		//System.out.println("TYPEDEFS:");
		//for(Entry<String,String> e : typeMap.entrySet())
		//	System.out.printf("%-20s --> %-10s :: %s%n", e.getKey(), e.getValue(), CType.getType(e.getValue()));
	}

	private static List<String> getStructFile(CType struct)
	{
		List<String> out = new ArrayList<>(struct.fields.size() + 5);
		out.add("% " + struct);
		if (!struct.desc.isEmpty())
			out.add("% " + struct.desc);
		out.add("type: ram");
		out.add(String.format("size: %X", struct.getSize()));
		out.add("fields:");
		out.add("{");
		int pos = 0;
		for (CField field : struct.fields) {
			String offsetString = String.format("%X", field.offset);
			if (!field.name.isEmpty() && !field.undefined) //!UnnamedFieldPattern.matcher(field.name).matches())
			{
				CType outType = field.type;
				if (outType == CType.getType("uchar"))
					outType = CType.getType("ubyte");

				if (pos != field.offset)
					out.add(String.format("%%\t%3s : UNK %X", String.format("%X", pos), field.offset - pos));

				if (field.desc.isEmpty())
					out.add(String.format("\t%3s : %-18s : %s", offsetString, field.name, outType));
				else
					out.add(String.format("\t%3s : %-18s : %-10s %% %s", offsetString, field.name, outType, field.desc));

				pos = field.offset + outType.getSize();
			}
		}
		if (pos != struct.getSize())
			out.add(String.format("%%\t%3s : UNK %X", String.format("%X", pos), struct.getSize() - pos));
		out.add("}");
		out.add("");

		return out;
	}

	private void readLines(List<Line> lines)
	{
		ReadState state = ReadState.Root;
		CType currentStruct = null;

		// special SRC names for these
		//	typeMap.put("pointer", "ptr");
		//	typeMap.put("void*", "code");

		for (Line line : lines) {
			String text = line.str;
			if (text.isEmpty())
				continue;

			text = text.replaceAll("\\*\\/", "");
			text = text.replaceAll("\\/\\*", "#");

			String comment = "";
			if (text.contains("#")) {
				comment = text.substring(text.indexOf("#") + 1).trim();
				text = text.substring(0, text.indexOf("#")).trim();
			}

			// remove excess WS
			text = text.replaceAll("\\s+", " ");

			// remove pointless chars
			text = text.replaceAll("[;,]", "");

			// force all pointers into a standard form
			text = text.replaceAll("\\* (?=\\*)", "*");
			text = text.replaceAll("(\\*+)(\\S+)", "$2$1");
			text = text.replaceAll("(\\S+) (\\*+) ?", "$1$2 ");
			text = text.trim();

			if (text.isEmpty())
				continue;

			String[] tokens = text.split("\\s+");

			//TODO handle "typedef struct { } name;" / "typedef enum { } name;"

			switch (state) {
				case Root:
					switch (tokens[0]) {
						case "typedef":
							if (tokens[1].equals("enum")) {
								state = ReadState.Enum;
								typedefEnum(line, tokens);
							}
							else if (tokens[1].equals("struct"))
								typedefStruct(line, tokens);
							else
								typedef(line, tokens);
							break;
						case "struct":
							state = ReadState.Struct;
							assert (tokens.length == 3 && tokens[2].equals("{")) : text;
							currentStruct = CType.getType(tokens[1]);
							structList.add(currentStruct);
							if (!comment.isEmpty())
								currentStruct.desc = comment;
							break;
						default:
							throw new InputFileException(line, text);
					}
					break;

				case Struct:
					if (text.startsWith("}")) {
						state = ReadState.Root;
						currentStruct = null;
					}
					else {
						readStructField(line, tokens, comment, currentStruct);
					}
					break;

				case Enum:
					// just completely ignore these
					if (text.startsWith("}"))
						state = ReadState.Root;
					break;
			}
		}
	}

	private void readStructField(Line line, String[] tokens, String comment, CType currentStruct)
	{
		System.out.printf("%-5d : %s%n", line.lineNum, line.str);

		String typeName;
		String fieldName;
		if (tokens[0].equals("struct") || tokens[0].equals("enum")) {
			assert (tokens.length == 3);
			typeName = tokens[1];
			fieldName = tokens[2];
		}
		else {
			assert (tokens.length == 2);
			typeName = tokens[0];
			fieldName = tokens[1];
		}

		Matcher arrayMatcher = ArrayPattern.matcher(fieldName);
		if (arrayMatcher.matches()) {
			fieldName = arrayMatcher.group(1);
			String[] contents = arrayMatcher.group(2).split("\\]\\[");
			StringBuilder sb = new StringBuilder();
			for (String s : contents) {
				sb.append("[");
				if (s.startsWith("0x"))
					sb.append(s.substring(2));
				else
					sb.append(s).append("`");
				sb.append("]");
			}

			typeName = typeName + sb.toString();
		}

		CType fieldType = CType.getType(typeName);
		currentStruct.appendField(fieldType, fieldName, comment, typeName.matches("undefined\\d?"));
	}

	private void typedefStruct(Line line, String[] tokens)
	{
		if (tokens[tokens.length - 1].equals("{"))
			throw new InputFileException(line, "\"typedef struct ... {\" is not yet supported.");

		//typedef struct static_sprite_component static_sprite_component,
		//typedef struct static_sprite_component* static_sprite_animation;

		if (tokens[2].endsWith("*"))
			CType.registerNewTypedef(tokens[3], CType.getType(tokens[2]));
		else
			CType.registerNewBaseType(new CType(tokens[3], BaseCategory.Struct));

		/*
		for(int i = 3; i < tokens.length; i++)
		{
			if(tokens[i].endsWith("*"))
				typeMap.put(tokens[i], tokens[2] + "*");
			else
				typeMap.put(tokens[i], tokens[2]);
		}
		*/
	}

	private void typedefEnum(Line line, String[] tokens)
	{
		assert (tokens.length == 4 && tokens[3].equals("{")) : line;
		String typeName = tokens[2];

		CType.registerNewTypedef(typeName, CType.getType("uchar"));
	}

	private void typedef(Line line, String[] tokens)
	{
		String typeName = tokens[tokens.length - 1];
		String arrayName = "";

		Matcher arrayMatcher = ArrayPattern.matcher(typeName);
		if (arrayMatcher.matches()) {
			typeName = arrayMatcher.group(1);
			String[] contents = arrayMatcher.group(2).split("\\]\\[");
			StringBuilder sb = new StringBuilder();
			for (String s : contents) {
				sb.append("[");
				if (s.startsWith("0x"))
					sb.append(s.substring(2));
				else
					sb.append(s).append("`");
				sb.append("]");
			}
			arrayName = sb.toString();
		}

		boolean unsigned = tokens[1].equals("unsigned");
		StringBuilder sb = new StringBuilder();

		if (tokens[unsigned ? 2 : 1].equals("long"))
			return; // skip all like "typedef long long    longlong"

		for (int i = unsigned ? 2 : 1; i < tokens.length - 1; i++)
			sb.append(tokens[i]);

		String specifier = sb.toString();
		if (specifier.equals("longlong") || specifier.equals("longlongint"))
			specifier = "long";
		else if (specifier.equals("long") || specifier.equals("longint"))
			specifier = "int";

		if (unsigned)
			specifier = "u" + specifier;

		specifier += arrayName;

		CType.registerNewTypedef(typeName, CType.getType(specifier));
		System.out.println(typeName + " --> " + specifier);
	}
}

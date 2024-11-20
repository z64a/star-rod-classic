package app.update;

import static app.Directories.*;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;

import app.Directories;
import app.Environment;
import app.input.IOUtils;
import app.input.InvalidInputException;
import game.shared.VarNameDictionary;
import game.shared.struct.script.ScriptVariable;
import util.Logger;

// 800DBD70
public class MapVarNameUpdater
{
	private static final Pattern VarPattern = Pattern.compile("(\\*[\\w?:]+)");
	private static final Matcher VarMatcher = VarPattern.matcher("");

	public static void main(String[] args)
	{
		Environment.initialize();
		try {
			run();
		}
		catch (IOException | InvalidInputException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		Environment.exit();
	}

	public static void run() throws IOException, InvalidInputException
	{
		Environment.initialize();
		HashMap<String, String> renameMap = new HashMap<>();

		VarNameDictionary flags = new VarNameDictionary(ScriptVariable.GameFlag);
		flags.loadDictionary(new File(Directories.DATABASE + FN_GAME_FLAGS), false);

		VarNameDictionary modFlags = new VarNameDictionary(ScriptVariable.GameFlag);
		modFlags.loadDictionary(new File(Directories.MOD_GLOBALS + FN_GAME_FLAGS), false);

		FileUtils.copyFile(
			new File(Directories.MOD_GLOBALS + FN_GAME_FLAGS),
			new File(Directories.MOD_GLOBALS + FN_GAME_FLAGS + ".old"));

		PrintWriter newFlags = IOUtils.getBufferedPrintWriter(new File(Directories.MOD_GLOBALS + FN_GAME_FLAGS));
		newFlags.println("% index X for GameFlag[X] = engine name = semantic name");
		newFlags.println();

		for (int i = 0; i < ScriptVariable.GameFlag.getMaxIndex(); i++) {
			String defaultName = flags.getDefaultName(i);
			String semanticName = flags.getName(i);
			String modName = modFlags.getName(i);
			boolean modNameIsDefault = modName.matches("GameFlag\\[[0-9A-Fa-f]+\\]");

			if (semanticName != null && !semanticName.equals(defaultName) && (modName.equals(defaultName) || modNameIsDefault))
				renameMap.put(defaultName, semanticName);

			StringBuilder sb = new StringBuilder();
			sb.append(String.format("%03X", i));
			sb.append(" = ");
			sb.append(flags.getDefaultName(i).substring(1));
			if (!modName.equals(defaultName)) {
				sb.append(" = ");
				sb.append(modName.substring(1));
			}
			else {
				if (semanticName.equals(defaultName)) {
					sb.append(" = ");
					sb.append("unused");
				}
				else {
					sb.append(" = ");
					sb.append(semanticName.substring(1));
				}
			}

			String comment = flags.getComment(i);
			if (comment != null && !comment.isEmpty()) {
				sb.append(" %");
				sb.append(comment);
			}
			newFlags.println(sb.toString());
		}

		newFlags.close();

		VarNameDictionary bytes = new VarNameDictionary(ScriptVariable.GameByte);
		bytes.loadDictionary(new File(Directories.DATABASE + FN_GAME_BYTES), false);

		VarNameDictionary modBytes = new VarNameDictionary(ScriptVariable.GameByte);
		modBytes.loadDictionary(new File(Directories.MOD_GLOBALS + FN_GAME_BYTES), false);

		FileUtils.copyFile(
			new File(Directories.MOD_GLOBALS + FN_GAME_BYTES),
			new File(Directories.MOD_GLOBALS + FN_GAME_BYTES + ".old"));

		PrintWriter newBytes = IOUtils.getBufferedPrintWriter(new File(Directories.MOD_GLOBALS + FN_GAME_BYTES));
		newBytes.println("% index X for GameByte[X] = engine name = semantic name");
		newBytes.println();

		for (int i = 0; i < ScriptVariable.GameByte.getMaxIndex(); i++) {
			String defaultName = bytes.getDefaultName(i);
			String semanticName = bytes.getName(i);
			String modName = modBytes.getName(i);
			boolean modNameIsDefault = modName.matches("GameByte\\[[0-9A-Fa-f]+\\]");

			if (semanticName != null && !semanticName.equals(defaultName) && (modName.equals(defaultName) || modNameIsDefault))
				renameMap.put(defaultName, semanticName);

			StringBuilder sb = new StringBuilder();
			sb.append(String.format("%03X", i));
			sb.append(" = ");
			sb.append(bytes.getDefaultName(i).substring(1));
			if (!modName.equals(defaultName)) {
				sb.append(" = ");
				sb.append(modName.substring(1));
			}
			else {
				if (semanticName.equals(defaultName)) {
					sb.append(" = ");
					sb.append("unused");
				}
				else {
					sb.append(" = ");
					sb.append(semanticName.substring(1));
				}
			}

			String comment = bytes.getComment(i);
			if (comment != null && !comment.isEmpty()) {
				sb.append(" %");
				sb.append(comment);
			}
			newBytes.println(sb.toString());
		}

		newBytes.close();

		if (!renameMap.isEmpty()) {
			for (File f : IOUtils.getFilesWithExtension(MOD_MAP_PATCH, "mpat", true))
				updatePatchFile(f, renameMap);

			for (File f : IOUtils.getFilesWithExtension(MOD_BATTLE, "bpat", true))
				updatePatchFile(f, renameMap);

			for (File f : IOUtils.getFilesWithExtension(MOD_PATCH, "patch", true))
				updatePatchFile(f, renameMap);
		}
	}

	private static void updatePatchFile(File sourceFile, HashMap<String, String> renameMap) throws IOException
	{
		int count = 0;

		List<String> linesIn = IOUtils.readPlainTextFile(sourceFile);
		List<String> linesOut = new ArrayList<>(linesIn.size());

		for (String line : linesIn) {
			StringBuffer sb = new StringBuffer(line.length());

			boolean found = false;

			VarMatcher.reset(line);
			while (VarMatcher.find()) {
				String varName = VarMatcher.group(1);
				if (renameMap.containsKey(varName)) {
					VarMatcher.appendReplacement(sb, Matcher.quoteReplacement(renameMap.get(varName)));
					count++;
					found = true;
				}
				else
					VarMatcher.appendReplacement(sb, Matcher.quoteReplacement(varName));

			}
			VarMatcher.appendTail(sb);

			if (found) {
				System.out.println("> " + line);
				System.out.println("< " + sb.toString());
			}

			linesOut.add(sb.toString());
		}

		if (count != 0) {
			sourceFile.delete();
			PrintWriter pw = IOUtils.getBufferedPrintWriter(sourceFile.getAbsolutePath());
			for (String line : linesOut)
				pw.println(line);
			pw.close();

			Logger.log("Replaced " + count + " var names in " + sourceFile.getName());
		}
	}
}

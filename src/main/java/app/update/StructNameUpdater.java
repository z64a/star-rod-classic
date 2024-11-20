package app.update;

import static app.Directories.*;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FilenameUtils;

import app.Directories;
import app.Environment;
import app.input.IOUtils;
import app.input.InputFileException;
import game.shared.SyntaxConstants;
import util.Logger;

public class StructNameUpdater
{
	public static void main(String[] args) throws IOException
	{
		Environment.initialize();
		updateAll();
		Environment.exit();
	}

	public static void updateAll() throws IOException
	{
		updateDir("midx", "mpat", DUMP_MAP_SRC, MOD_MAP_SRC, MOD_MAP_PATCH);
		updateDir("bidx", "bpat", DUMP_FORMA_SRC, MOD_FORMA_SRC, MOD_FORMA_PATCH);
		updateDir("bidx", "bpat", DUMP_ITEM_SRC, MOD_ITEM_SRC, MOD_ITEM_PATCH);
		updateDir("bidx", "bpat", DUMP_MOVE_SRC, MOD_MOVE_SRC, MOD_MOVE_PATCH);
		updateDir("bidx", "bpat", DUMP_ALLY_SRC, MOD_ALLY_SRC, MOD_ALLY_PATCH);
	}

	private static void updateDir(String extIdx, String extPatch,
		Directories dumpDir, Directories modDir, Directories patchDir) throws IOException
	{
		for (File f : IOUtils.getFilesWithExtension(dumpDir, extIdx, true)) {
			try {
				new StructNameUpdater(f, modDir, patchDir, extPatch);
			}
			catch (IOException e) {
				Logger.printStackTrace(e);
			}
		}
	}

	private StructNameUpdater(File dumpIdx, Directories modDir, Directories patchDir, String extPatch) throws IOException
	{
		String name = FilenameUtils.removeExtension(dumpIdx.getName());
		File copyIdx = new File(modDir + dumpIdx.getName());

		File[] patches = IOUtils.getFileWithin(patchDir, name + "." + extPatch, true);
		if (patches.length < 1)
			return;

		if (!copyIdx.exists())
			return;

		HashMap<Integer, String> dumpMap;
		HashMap<Integer, String> copyMap;

		try {
			dumpMap = loadIndexFile(dumpIdx);
		}
		catch (IOException e) {
			throw new InputFileException(dumpIdx, e);
		}

		try {
			copyMap = loadIndexFile(copyIdx);
		}
		catch (IOException e) {
			throw new InputFileException(copyIdx, e);
		}

		HashMap<String, String> renameMap = new HashMap<>();

		for (Entry<Integer, String> e : dumpMap.entrySet()) {
			String copyName = copyMap.get(e.getKey());
			if (copyName == null || copyName.equals(e.getValue()))
				continue;

			renameMap.put(copyName, e.getValue());
		}

		if (renameMap.isEmpty())
			return;

		Logger.logf("Found %d renamed structures in %s", renameMap.size(), name);

		for (File f : patches)
			updatePatchFile(f, renameMap);
	}

	private static final Pattern PointerPattern = Pattern.compile("(\\$\\S+)");

	private void updatePatchFile(File sourceFile, HashMap<String, String> renameMap) throws IOException
	{
		int count = 0;

		List<String> linesIn = IOUtils.readPlainTextFile(sourceFile);
		List<String> linesOut = new ArrayList<>(linesIn.size());

		Matcher findMatcher = PointerPattern.matcher("");
		for (String line : linesIn) {
			StringBuffer sb = new StringBuffer(line.length());

			boolean found = false;

			findMatcher.reset(line);
			while (findMatcher.find()) {
				String pointerName = findMatcher.group(1);
				if (renameMap.containsKey(pointerName)) {
					findMatcher.appendReplacement(sb, Matcher.quoteReplacement(renameMap.get(pointerName)));
					count++;
					found = true;
				}
				else
					findMatcher.appendReplacement(sb, Matcher.quoteReplacement(pointerName));

			}
			findMatcher.appendTail(sb);

			if (found) {
				//	System.out.println("> " + line);
				//	System.out.println("< " + sb.toString());
			}

			linesOut.add(sb.toString());
		}

		if (count != 0) {
			sourceFile.delete();
			PrintWriter pw = IOUtils.getBufferedPrintWriter(sourceFile.getAbsolutePath());
			for (String line : linesOut)
				pw.println(line);
			pw.close();

			Logger.log("Replaced " + count + " pointer names in " + sourceFile.getName());

		}
	}

	private static final Pattern WhitespacePattern = Pattern.compile("\\s+");

	private final HashMap<Integer, String> loadIndexFile(File f) throws IOException
	{
		HashMap<Integer, String> nameMap = new HashMap<>();
		List<String> lines = IOUtils.readPlainTextFile(f);

		for (String line : lines) {
			line = WhitespacePattern.matcher(line).replaceAll("");
			if (line.isEmpty())
				continue;

			String[] tokens = line.split(SyntaxConstants.INDEX_FILE_SEPARATOR);

			if (line.startsWith("Padding"))
				continue;

			if (line.startsWith("Missing"))
				continue;

			String name = tokens[0];
			int offset = (int) Long.parseLong(tokens[2], 16);

			if (name.equals("$Start") || name.equals("$End"))
				continue;

			nameMap.put(offset, name);
		}

		return nameMap;
	}
}

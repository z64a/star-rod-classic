package app.update;

import static app.Directories.*;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.Directories;
import app.input.IOUtils;
import util.Logger;

public class GeneralPatchUpdater
{
	public static void updateAll(HashMap<String, String> renameMap) throws IOException
	{
		// create Matcher and replacement string for each regex and literal replacement in the renameMap
		ArrayList<Entry<Matcher, String>> rules = new ArrayList<>();

		for (var entry : renameMap.entrySet()) {
			Matcher matcher = Pattern.compile(entry.getKey()).matcher("");
			rules.add(new SimpleImmutableEntry<>(matcher, Matcher.quoteReplacement(entry.getValue())));
		}

		new GeneralPatchUpdater(MOD_PATCH, "patch", rules);
		new GeneralPatchUpdater(MOD_MAP, "mpat", rules);
		new GeneralPatchUpdater(MOD_BATTLE, "bpat", rules);
	}

	private GeneralPatchUpdater(Directories patchDir, String extPatch, Iterable<Entry<Matcher, String>> rules) throws IOException
	{
		for (File f : IOUtils.getFilesWithExtension(patchDir, extPatch, true))
			updatePatchFile(f, rules);
	}

	private void updatePatchFile(File sourceFile, Iterable<Entry<Matcher, String>> rules) throws IOException
	{
		Logger.log("Updating patch file: " + sourceFile.getName());

		List<String> linesIn = IOUtils.readPlainTextFile(sourceFile);
		List<String> linesOut = new ArrayList<>(linesIn.size());

		for (String line : linesIn) {
			for (var rule : rules) {
				Matcher matcher = rule.getKey();
				matcher.reset(line);
				line = matcher.replaceAll(rule.getValue());
			}
			linesOut.add(line);
		}

		sourceFile.delete();
		PrintWriter pw = IOUtils.getBufferedPrintWriter(sourceFile.getAbsolutePath());
		for (String line : linesOut)
			pw.println(line);
		pw.close();
	}
}

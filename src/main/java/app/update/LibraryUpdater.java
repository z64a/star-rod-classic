package app.update;

import static app.Directories.*;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.input.IOUtils;
import game.ROM;
import game.ROM.LibScope;
import game.ROM_US;
import game.shared.lib.LibEntry;
import game.shared.lib.Library;
import util.Logger;

public class LibraryUpdater
{
	private static final Pattern FuncExpPattern = Pattern.compile("(?i)~Func:(\\w+)");
	private static final Pattern CallPattern = Pattern.compile("(?i)(Call\\s+)(\\w+)(\\s*\\([^(]+\\))");

	private HashMap<LibScope, HashMap<String, UpdatedName>> libMap = new HashMap<>();

	public static class UpdatedName
	{
		public final String oldName;
		public final String newName;
		public final int addr;
		public final boolean defined;
		public final boolean changed;

		public UpdatedName(int addr, String oldName, String newName)
		{
			this.oldName = oldName;
			this.newName = newName;
			this.addr = addr;
			defined = true;
			changed = !newName.equals(oldName);
		}

		public UpdatedName(int addr, String oldName)
		{
			this.oldName = oldName;
			this.newName = null;
			this.addr = addr;
			defined = false;
			changed = true;
		}
	}

	private int loadNameMaps(File oldLibDirectory) throws IOException
	{
		ROM oldLibraries = new ROM_US(oldLibDirectory);
		ROM newLibraries = new ROM_US(DATABASE.toFile());

		int totalChanged = 0;

		for (LibScope scope : LibScope.values()) {
			HashMap<String, UpdatedName> scopeMap = new HashMap<>();
			libMap.put(scope, scopeMap);
			int changed = loadNameMap(oldLibraries.getLibrary(scope), newLibraries.getLibrary(scope), scopeMap);
			if (changed > 0)
				Logger.logf("Found %d altered " + scope.name() + " function names.", changed);
			totalChanged += changed;
		}

		return totalChanged;
	}

	private static int loadNameMap(Library oldLib, Library newLib, HashMap<String, UpdatedName> map) throws IOException
	{
		int changed = 0;
		map.clear();

		HashSet<Integer> addressSet = new HashSet<>();

		for (LibEntry oldEntry : oldLib) {
			LibEntry newEntry = newLib.get(oldEntry.address);

			UpdatedName newName;
			if (newEntry == null) {
				newName = new UpdatedName(oldEntry.address, oldEntry.name); // removed entry
			}
			else {
				newName = new UpdatedName(oldEntry.address, oldEntry.name, newEntry.name); // renamed entry
			}

			if (newName.changed)
				changed++;

			map.put(oldEntry.name, newName);
			addressSet.add(oldEntry.address);
		}

		for (LibEntry e : newLib) {
			if (addressSet.contains(e.address))
				continue;

			String oldName = String.format("%08X", e.address);
			UpdatedName newName = new UpdatedName(e.address, oldName, e.name); // new entry
			map.put(oldName, newName);
			changed++;
		}

		return changed;
	}

	public LibraryUpdater(File oldLibDirectory) throws IOException
	{
		Logger.log("Running LibraryUpdater...");

		int numChanged = loadNameMaps(oldLibDirectory);
		if (numChanged == 0) {
			Logger.log("No changes detected in libraries.");
			return;
		}

		checkedFunctions = 0;
		updatedFunctions = 0;

		for (File f : IOUtils.getFilesWithExtension(MOD_MAP, "mpat", true))
			updatePatchFile(f, libMap.get(LibScope.World));

		for (File f : IOUtils.getFilesWithExtension(MOD_MAP, "mscr", true))
			updatePatchFile(f, libMap.get(LibScope.World));

		for (File f : IOUtils.getFilesWithExtension(MOD_BATTLE, "bpat", true))
			updatePatchFile(f, libMap.get(LibScope.Battle));

		for (File f : IOUtils.getFilesWithExtension(MOD_BATTLE, "bscr", true))
			updatePatchFile(f, libMap.get(LibScope.Battle));

		for (File f : IOUtils.getFilesWithExtension(MOD_PATCH, "patch", true))
			updatePatchFile(f, libMap.get(LibScope.Common));

		Logger.logf("Done! Updated %d of %d function references.", updatedFunctions, checkedFunctions);
	}

	private void updatePatchFile(File sourceFile, HashMap<String, UpdatedName> nameMap) throws IOException
	{
		List<String> linesOld = IOUtils.readPlainTextFile(sourceFile);
		List<String> linesNew = new ArrayList<>(linesOld.size());

		int changedinFile = updateFunctions(sourceFile.getName(), linesOld, linesNew, nameMap);

		if (changedinFile > 0) {
			Logger.log("Replacing " + changedinFile + " functions in " + sourceFile.getName());

			sourceFile.delete();
			PrintWriter pw = IOUtils.getBufferedPrintWriter(sourceFile);

			for (String line : linesNew)
				pw.println(line);

			pw.close();
		}
	}

	private int checkedFunctions;
	private int updatedFunctions;

	private int updateFunctions(String filename, List<String> linesIn, List<String> linesOut, HashMap<String, UpdatedName> nameMap)
	{
		int initialCount = updatedFunctions;
		linesOut.clear();

		for (String line : linesIn) {
			StringBuffer sb = new StringBuffer(line.length());

			Matcher expMatcher = FuncExpPattern.matcher(line);
			while (expMatcher.find()) {
				String replacement;
				String oldName = expMatcher.group(1);

				UpdatedName entry = nameMap.get(oldName);
				if (entry == null) {
					replacement = "~Func:" + oldName;
					Logger.logfDetail("[%s] Found undocumented function: %s", filename, oldName);
				}
				else if (!entry.defined) {
					replacement = String.format("%08X", entry.addr);
					updatedFunctions++;
					Logger.logfWarning("[%s] Found removed function: %s", filename, oldName);
				}
				else if (!entry.changed) {
					replacement = "~Func:" + oldName;
				}
				else {
					replacement = "~Func:" + entry.newName;
					updatedFunctions++;
					Logger.logf("[%s] Replacing function %s --> %s", filename, oldName, entry.newName);
				}

				expMatcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
				checkedFunctions++;
			}
			expMatcher.appendTail(sb);

			String s = sb.toString();
			sb = new StringBuffer(s.length());

			Matcher callMatcher = CallPattern.matcher(s);
			while (callMatcher.find()) {
				String newName;
				String oldName = callMatcher.group(2);

				UpdatedName entry = nameMap.get(oldName);
				if (entry == null) {
					newName = oldName;
					//		Logger.logfWarning("[%s] Found undocumented function: %s", filename, oldName);
				}
				else if (!entry.defined) {
					newName = String.format("%08X", entry.addr);
					updatedFunctions++;
					Logger.logfWarning("[%s] Found removed function: %s", filename, oldName);
				}
				else if (!entry.changed) {
					newName = oldName;
				}
				else {
					newName = entry.newName;
					updatedFunctions++;
					Logger.logf("[%s] Replacing function %s --> %s", filename, oldName, newName);
				}

				String replacement = callMatcher.group(1) + newName + callMatcher.group(3);
				callMatcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
				checkedFunctions++;
			}
			callMatcher.appendTail(sb);

			linesOut.add(sb.toString());
		}

		return updatedFunctions - initialCount;
	}
}

package app.helper;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;

import app.Directories;
import app.Environment;
import app.input.IOUtils;
import app.input.InvalidInputException;
import game.ROM;
import game.ROM.LibScope;
import game.ROM_JP;

public class LibraryFixer
{
	public static void main(String[] args) throws IOException, InvalidInputException
	{
		Environment.initialize();

		ROM romJP = new ROM_JP(Directories.DATABASE.toFile());

		/*
		updateLib("common", LibScope.US_Common);
		updateLib("common_data", LibScope.US_Common);
		updateLib("world", LibScope.US_World);
		updateLib("battle", LibScope.US_Battle);
		updateLib("pause_menu", LibScope.US_Pause);
		updateLib("files_menu", LibScope.US_MainMenu);
		*/

		updateLib("common_jp", romJP, LibScope.Common);

		Environment.exit();
	}

	private static final Pattern EntryLinePattern = Pattern.compile("(\\s*(?:asm|api|scr)\\s*:\\s*)(.+?)(\\s*:.+)");
	private static final Matcher EntryLineMatcher = EntryLinePattern.matcher("");

	private static final Pattern DataLinePattern = Pattern.compile("(\\s*(?:dat|lbl)\\s*:\\s*)(.+?)(\\s*:.+)");
	private static final Matcher DataLineMatcher = DataLinePattern.matcher("");

	private static void updateLib(String filename, ROM rom, LibScope scope) throws IOException, InvalidInputException
	{
		File lib = new File(Directories.DATABASE + filename + ".lib");
		FileUtils.copyFile(lib, new File(Directories.DATABASE + filename + ".backup"));

		List<String> lines = IOUtils.readPlainTextFile(lib);
		PrintWriter pw = IOUtils.getBufferedPrintWriter(lib);
		for (String line : lines) {
			EntryLineMatcher.reset(line);
			if (!EntryLineMatcher.matches()) {
				DataLineMatcher.reset(line);
				if (DataLineMatcher.matches()) {
					int addr = (int) Long.parseLong(DataLineMatcher.group(2), 16);
					pw.print(DataLineMatcher.group(1));
					pw.printf("%08X", addr);
					pw.print(DataLineMatcher.group(3));
					pw.println();
					continue;
				}

				pw.println(line);
				continue;
			}

			String locationField = EntryLineMatcher.group(2);
			if (locationField.contains(",")) {
				pw.println(line);
				continue;
			}

			int addr = (int) Long.parseLong(locationField, 16);
			Integer offset = rom.getOffset(scope, addr);

			if (offset == null)
				throw new InvalidInputException("%X is out of scope for %s!", addr, filename);

			pw.print(EntryLineMatcher.group(1));
			pw.printf("%08X, %06X", addr, offset);
			pw.print(EntryLineMatcher.group(3));
			pw.println();
		}
		pw.close();
	}
}

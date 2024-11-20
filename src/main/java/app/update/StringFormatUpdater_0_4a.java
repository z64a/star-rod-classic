package app.update;

import static app.Directories.*;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;

import app.Environment;
import app.input.IOUtils;
import app.input.Line;
import app.input.PatchFileParser;
import app.input.PatchFileParser.PatchUnit;
import util.Logger;

public class StringFormatUpdater_0_4a
{
	public static void main(String[] args) throws IOException
	{
		Environment.initialize();
		new StringFormatUpdater_0_4a();
		Environment.exit();
	}

	public StringFormatUpdater_0_4a() throws IOException
	{
		File stringsVersion = new File(MOD_STRINGS + "version");
		if (stringsVersion.exists()) {
			ByteBuffer bb = IOUtils.getDirectBuffer(stringsVersion);
			int version = bb.getInt();
			if (version == 3) {
				Logger.logWarning("Strings have already been updated for 0.4! Skipping string update.");
				return;
			}
		}

		if (!MOD_STRINGS_SRC.toFile().exists()) {
			File tempDir = new File(TEMP + "/strings/");
			FileUtils.copyDirectory(MOD_STRINGS.toFile(), tempDir);
			FileUtils.cleanDirectory(MOD_STRINGS.toFile());
			FileUtils.copyDirectory(tempDir, MOD_STRINGS_PATCH.toFile());
			FileUtils.copyDirectory(DUMP_STRINGS_SRC.toFile(), MOD_STRINGS_SRC.toFile());
			FileUtils.deleteDirectory(tempDir);
		}

		int fileCount = 0;
		int stringCount = 0;

		for (File f : IOUtils.getFilesWithExtension(MOD_STRINGS_PATCH, "str", true)) {
			stringCount += updateStrings(f, true);
			fileCount++;
		}

		for (File f : IOUtils.getFilesWithExtension(MOD_MAP, "mpat", true)) {
			stringCount += updateStrings(f, true);
			fileCount++;
		}

		for (File f : IOUtils.getFilesWithExtension(MOD_BATTLE, "bpat", true)) {
			stringCount += updateStrings(f, true);
			fileCount++;
		}

		for (File f : IOUtils.getFilesWithExtension(MOD_PATCH, "patch", true)) {
			stringCount += updateStrings(f, true);
			fileCount++;
		}

		Logger.log(stringCount + " strings replaced in " + fileCount + " files.");

		ByteBuffer bb = ByteBuffer.allocateDirect(4);
		bb.putInt(3);
		IOUtils.writeBufferToFile(bb, stringsVersion);
	}

	private int updateStrings(File sourceFile, boolean overwriteSource) throws IOException
	{
		List<Line> linesIn = IOUtils.readPlainInputFile(sourceFile);
		List<String> linesOut = new ArrayList<>((int) (linesIn.size() * 1.2));

		List<PatchUnit> units = PatchFileParser.parse(linesIn);

		int stringCount = 0;
		int currentLine = 0;

		for (PatchUnit unit : units) {
			if (unit.parsedAsString) {
				if (!unit.body.isEmpty()) {
					int startLine = (unit.body.get(0).lineNum) - 1;
					int endLine = (unit.body.get(unit.body.size() - 1).lineNum) - 1;

					while (currentLine < startLine)
						linesOut.add(linesIn.get(currentLine++).str);

					List<String> tempLines = new ArrayList<>();

					StringBuilder appender = null;
					int i = 0;

					for (Line line : unit.body) {
						if (line.str.endsWith("[...]")) {
							if (appender == null)
								appender = new StringBuilder();
							appender.append(line.str.substring(0, line.str.length() - 5));
						}
						else {
							if (appender != null)
								appender.append(line.str.replaceFirst("^\\t+", ""));
							else
								appender = new StringBuilder(line.str);

							if (i < unit.body.size() - 1)
								appender.append("[BR]");

							tempLines.add(appender.toString());
							appender = null;
						}
						i++;
					}

					if (appender != null)
						tempLines.add(appender.toString());

					linesOut.addAll(tempLines);

					currentLine = endLine + 1;
					stringCount++;
				}
			}
		}

		while (currentLine < linesIn.size())
			linesOut.add(linesIn.get(currentLine++).str);

		if (overwriteSource) {
			sourceFile.delete();
			PrintWriter pw = IOUtils.getBufferedPrintWriter(sourceFile);

			for (String line : linesOut)
				pw.println(line);

			pw.close();
		}

		return stringCount;
	}
}

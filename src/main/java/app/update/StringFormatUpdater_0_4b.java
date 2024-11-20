package app.update;

import static app.Directories.*;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import app.Environment;
import app.input.IOUtils;
import app.input.InputFileException;
import app.input.Line;
import app.input.PatchFileParser;
import app.input.PatchFileParser.PatchUnit;
import game.string.StringDecoder;
import game.string.StringEncoder;
import util.Logger;

public class StringFormatUpdater_0_4b
{
	public static void main(String[] args) throws IOException
	{
		Environment.initialize();
		new StringFormatUpdater_0_4b();
		Environment.exit();
	}

	public StringFormatUpdater_0_4b() throws IOException
	{
		File stringsVersion = new File(MOD_STRINGS + "version");
		if (stringsVersion.exists()) {
			ByteBuffer bb = IOUtils.getDirectBuffer(stringsVersion);
			int version = bb.getInt();
			if (version == 4) {
				Logger.logWarning("Strings have already been updated for 0.4! Skipping string update.");
				return;
			}
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
		bb.putInt(4);
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

					StringBuilder sb = new StringBuilder();

					for (Line line : unit.body) {
						String s = line.str;
						s = s.replaceAll("(?i)\\[Func_04\\]", "[Yield]");
						s = s.replaceAll("(?i)\\[Kerning", "[CharWidth");
						s = s.replaceAll("(?i)\\[SetPrintPos", "[SetPosX");
						s = s.replaceAll("(?i)\\[SetPrintY", "[SetPosY");
						s = s.replaceAll("(?i)\\[Indent", "[Right");
						s = s.replaceAll("(?i)\\[Image1", "[InlineImage");
						s = s.replaceAll("(?i)\\[Sprite", "[AnimSprite");
						s = s.replaceAll("(?i)\\[Func_1A", "[AnimDelay");
						s = s.replaceAll("(?i)\\[Func_1B", "[AnimLoop");
						s = s.replaceAll("(?i)\\[Func_1C", "[AnimDone");
						s = s.replaceAll("(?i)\\[Item", "[ItemIcon");
						s = s.replaceAll("(?i)\\[Image7", "[Image");
						s = s.replaceAll("(?i)\\[StartAnim\\]", "[SavePos]");
						s = s.replaceAll("(?i)\\[EndAnim\\]", "[RestorePos]");
						s = s.replaceAll("(?i)\\[PushColor\\]", "[SaveColor]");
						s = s.replaceAll("(?i)\\[PopColor\\]", "[RestoreColor]");
						s = s.replaceAll("(?i)\\[SpeechSound:00", "[Voice Normal");
						s = s.replaceAll("(?i)\\[SpeechSound:01", "[Voice Bowser");
						s = s.replaceAll("(?i)\\[SpeechSound:02", "[Voice Spirit");
						s = s.replaceAll("(?i)\\[Func_2B\\]", "[EnableCDownNext]");
						s = s.replaceAll("(?i)\\[Func_29", "[CenterX");

						s = s.replaceAll("(?i)\\[WAIT\\]", "[Wait]");
						s = s.replaceAll("(?i)\\[NEXT\\]", "[Next]");
						s = s.replaceAll("(?i)\\[END\\]", "[End]");

						s = s.replaceAll("(?i)\\[(A|B|START|L|R|Z|C-UP|C-DOWN|C-LEFT|C-RIGHT)\\]", "[~$1]");

						s = s.replaceAll("(?i)FX:Noise", "FX:NoiseOutline");
						s = s.replaceAll("(?i)FX:FadedNoise", "FX:Static");
						s = s.replaceAll("(?i)FX:FadedJitter", "FX:Blur");
						s = s.replaceAll("(?i)FX:Faded", "FX:DitherFade");
						s = s.replaceAll("(?i)FX:Jitter", "FX:Shake");

						s = s.replaceAll("(?i)FX:WavyB", "FX:GlobalWave");
						s = s.replaceAll("(?i)FX:Wavy", "FX:Wave");
						s = s.replaceAll("(?i)FX:RainbowB", "FX:GlobalRainbow");
						s = s.replaceAll("(?i)FX:Shrinking", "FX:PrintRising");
						s = s.replaceAll("(?i)FX:Growing", "FX:PrintGrowing");

						s = s.replaceAll("(?i)\\[StartFX:", "[");
						s = s.replaceAll("(?i)\\[EndFX:", "[/");

						sb.append(s).append(System.lineSeparator());
						tempLines.add(s);
					}

					try {
						ByteBuffer bb = StringEncoder.encode(sb.toString());
						byte[] bytes = new byte[bb.capacity()];
						bb.get(bytes);
						String markup = StringDecoder.toMarkup(bytes);
						if (markup.endsWith(System.lineSeparator()))
							markup = markup.substring(0, markup.length() - System.lineSeparator().length());
						linesOut.add(markup);
					}
					catch (InputFileException e) {
						Logger.printStackTrace(e);
						linesOut.addAll(tempLines);
					}

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

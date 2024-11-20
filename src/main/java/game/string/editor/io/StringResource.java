package game.string.editor.io;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FilenameUtils;

import app.input.IOUtils;
import app.input.InputFileException;
import app.input.Line;
import game.string.PMString;
import game.string.StringEncoder;
import util.Logger;

public class StringResource
{
	public final File file;
	public List<PMString> strings = null;

	public long lastModified;

	public final boolean usePatchFormat;

	public boolean modified;

	public boolean isValid;

	public StringResource(File file)
	{
		this.file = file;
		switch (FilenameUtils.getExtension(file.getName())) {
			case "str":
			case "msg":
				usePatchFormat = false;
				break;
			default:
				usePatchFormat = true;
				break;
		}

		load();
		isValid = true;
	}

	public void reload()
	{
		if (strings != null && !strings.isEmpty()) {
			for (PMString msg : strings)
				msg.invalidate();
		}

		load();
	}

	private void load()
	{
		try {
			strings = StringEncoder.parseStrings(this);
		}
		catch (IOException e) {
			throw new InputFileException(file, e.getMessage());
		}

		/*
		for (PMString string : strings)
		{
			if(!string.indexed)
				continue;

			if(string.section > 0xFF || string.section < 0)
				string.error = true;

			if(string.index > 0xFFFF)
				string.error = true;
		}
		*/

		lastModified = file.lastModified();
	}

	public void setModified()
	{
		modified = true;
	}

	public void saveChanges() throws IOException
	{
		if (!file.exists() || file.lastModified() != lastModified) {
			Logger.logError("Could not save changes to " + file.getName());
			return;
		}

		List<Line> linesIn = IOUtils.readPlainInputFile(file);
		List<String> linesOut = new ArrayList<>((int) (linesIn.size() * 1.2));
		int currentLine = 0;

		//TODO ensure strings are sorted
		for (PMString msg : strings) {
			if (msg.startLineNum <= 0)
				continue;

			while (currentLine < msg.startLineNum - 1)
				linesOut.add(linesIn.get(currentLine++).str);

			msg.startLineNum = linesOut.size() + 1;
			writeString(msg, linesOut);
			currentLine = msg.endLineNum;
			msg.endLineNum = linesOut.size();
		}

		while (currentLine < linesIn.size())
			linesOut.add(linesIn.get(currentLine++).str);

		for (PMString msg : strings) {
			if (msg.startLineNum > 0)
				continue;

			msg.startLineNum = linesOut.size() + 1;
			writeString(msg, linesOut);
			msg.endLineNum = linesOut.size();

			linesOut.add("");
		}

		file.delete();
		PrintWriter pw = IOUtils.getBufferedPrintWriter(file);
		for (String line : linesOut)
			pw.println(line);
		pw.close();

		lastModified = file.lastModified();
		modified = false;
		for (PMString msg : strings)
			msg.modified = false;
	}

	private void writeString(PMString msg, List<String> linesOut)
	{
		StringBuilder sb = new StringBuilder();

		if (usePatchFormat) {
			if (msg.unit != null)
				sb.append(msg.unit.declaration.str.trim().split("\\s+")[0]);
			else
				sb.append("#new:String");

			sb.append(" $");
			sb.append(msg.name);
		}
		else {
			if (msg.indexed) {
				if (msg.autoAssign)
					sb.append(String.format("#message:%02X:(%s)", msg.section, msg.name));
				else
					sb.append(String.format("#message:%02X:%03X", msg.section, msg.index));
			}
			else
				sb.append(String.format("#message:%02X:(%s)", msg.section, msg.name));
		}

		linesOut.add(sb.toString());
		linesOut.add("{");
		msg.sanitize();
		String[] lines = msg.getMarkup().split("\r?\n");
		for (String line : lines)
			linesOut.add(msg.leadingTabs + line);
		linesOut.add("}");
	}
}

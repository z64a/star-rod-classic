package game.string;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.input.InputFileException;
import app.input.Line;
import app.input.PatchFileParser.PatchUnit;
import game.globals.editor.GlobalsListable;
import game.string.StringConstants.ControlCharacter;
import game.string.editor.io.StringResource;

public class PMString implements Externalizable, GlobalsListable
{
	public final StringResource source;
	public final PatchUnit unit;

	public int startLineNum = -1;
	public int endLineNum = -1;
	public String leadingTabs = "";

	public InputFileException parseException;
	public String errorMessage = "";

	private boolean hasValidSource;
	public boolean modified;
	public boolean editorShouldSync;

	private String markup;
	private String previewText;
	public byte[] bytes;

	public boolean indexed = false;
	public boolean autoAssign = false;

	public int section;
	public int index;
	public String name;

	@Override
	public void writeExternal(ObjectOutput out) throws IOException
	{
		out.writeBoolean(indexed);
		out.writeBoolean(autoAssign);
		out.writeInt(section);
		out.writeInt(index);
		out.writeUTF(name);
		out.writeUTF(previewText);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException
	{
		indexed = in.readBoolean();
		autoAssign = in.readBoolean();
		section = in.readInt();
		index = in.readInt();
		name = in.readUTF();
		previewText = in.readUTF();
	}

	// dumping from binary
	public PMString(ByteBuffer bb, int section, int index)
	{
		this.source = null;
		this.unit = null;

		this.indexed = true;
		this.autoAssign = false;

		this.section = section;
		this.index = index;

		bytes = new byte[bb.remaining()];
		bb.get(bytes);
		setMarkup(StringDecoder.toMarkup(bytes));
	}

	// loading from patch
	public PMString(StringResource res, PatchUnit unit, String name)
	{
		this.source = res;
		this.unit = unit;
		startLineNum = unit.startLineNum;
		endLineNum = unit.endLineNum;
		hasValidSource = true;

		this.indexed = false;
		this.autoAssign = false;

		this.section = -1;
		this.index = -1;
		this.name = name;

		loadLines(unit.body);
		tryCompile(unit.body);
	}

	// loading from text
	public PMString(StringResource res, PatchUnit unit, int section, int index, String name)
	{
		this.source = res;
		this.unit = unit;
		startLineNum = unit.startLineNum;
		endLineNum = unit.endLineNum;
		hasValidSource = true;

		this.indexed = true;
		this.autoAssign = (index == 0xFFFF);

		this.section = section;
		this.index = index;
		this.name = name;

		loadLines(unit.body);
		tryCompile(unit.body);
	}

	// created in editor
	public PMString(StringResource res)
	{
		this.source = res;
		this.unit = null;
		hasValidSource = true;

		if (res.usePatchFormat) {
			indexed = false;
		}
		else {
			indexed = true;
			autoAssign = true;
			section = 0x2F;
		}

		name = "NewString";
		markup = "[END]";
		previewText = "";
	}

	private static final Pattern TabStartPattern = Pattern.compile("^(\t+).+");
	private static final Matcher TabStartMatcher = TabStartPattern.matcher("");

	private void loadLines(List<Line> lines)
	{
		// preserve tab indents
		if (lines.size() > 0 && lines.get(0).str.startsWith("\t")) {
			String firstLine = lines.get(0).str;
			TabStartMatcher.reset(firstLine);
			if (TabStartMatcher.matches())
				leadingTabs = TabStartMatcher.group(1);
		}

		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i).str;
			sb.append(line.replaceAll("[\t\r]", ""));
			sb.append("\n");
		}
		setMarkup(sb.toString());
	}

	public void setMarkup(String s)
	{
		markup = s;
		previewText = markup.replaceAll("(?i)\\[" + ControlCharacter.ENDL + "\\]", " ")
			.replaceAll("\\\\", "") // remove escape sequences
			.replaceAll("\\[[^]]+\\]", "") // remove other tags
			.replaceAll("\\s+", " ") // squash contiguous spaces into one
			.trim();
	}

	public String getMarkup()
	{
		return markup;
	}

	public void sanitize()
	{
		String unescapedControlChar = "[\\s\\S]*(?<!\\\\)[{}%][\\s\\S]*";
		String invalidEscapedChar = "[\\s\\S]*\\\\[^{}%\\[\\]][\\s\\S]*";
		if (markup.matches(unescapedControlChar) || markup.matches(invalidEscapedChar)) {
			editorShouldSync = true;
			String fixed = markup.replaceAll("(?<!\\\\)([{}%])", "\\\\$1");
			fixed = fixed.replaceAll("\\\\*([^{}%\\[\\]])", "$1");
			setMarkup(fixed);
		}
	}

	private void tryCompile(List<Line> lines)
	{
		try {
			ByteBuffer bb = StringEncoder.encodeLines(lines);
			bytes = new byte[bb.remaining()];
			bb.get(bytes);
			parseException = null;
		}
		catch (InputFileException e) {
			parseException = e;
			errorMessage = parseException.getMessage();
			bytes = null;
		}
	}

	public boolean hasID()
	{
		return section >= 0;
	}

	public int getID()
	{
		return ((section & 0xFFFF) << 16) | (index & 0xFFFF);
	}

	public String getIDName()
	{
		if (indexed) {
			if (autoAssign)
				return String.format("%02X-auto", section);
			else
				return String.format("%02X-%03X", section, index);
		}
		else
			return "embed";
	}

	@Override
	public String toString()
	{
		return previewText;
	}

	public String getIdentifier()
	{
		if (hasName())
			return name;
		else if (!autoAssign)
			return String.format("%02X-%03X", section, index);
		return null; //!
	}

	public boolean hasName()
	{
		return name != null && !name.isBlank();
	}

	public void setModified()
	{
		assert (source != null);
		modified = true;
		source.setModified();
	}

	public boolean isModified()
	{
		return modified;
	}

	public void invalidate()
	{
		hasValidSource = false;
	}

	public boolean hasValidSource()
	{
		return source.isValid && hasValidSource;
	}

	public void setErrorMessage(String string)
	{
		if (string == null)
			errorMessage = "";
		else
			errorMessage = string;
	}

	public String getErrorMessage()
	{
		return errorMessage;
	}

	public boolean hasError()
	{
		return !errorMessage.isEmpty();
	}

	@Override
	public String getFilterableString()
	{
		String needle;
		if (hasName())
			needle = name.replaceAll("_", " ");
		else
			needle = getIDName();

		needle += " " + toString();

		return needle;
	}

	@Override
	public boolean canDeleteFromList()
	{
		return false;
	}

	@Override
	public int getIndex()
	{
		return -1;
	}
}

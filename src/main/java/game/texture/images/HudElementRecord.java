package game.texture.images;

import static app.Directories.EXT_HUD_SCRIPT;
import static app.Directories.MOD_HUD_SCRIPTS;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;

import app.input.AbstractSource;
import app.input.DummySource;
import app.input.IOUtils;
import app.input.Line;
import game.globals.editor.GlobalsRecord;
import game.shared.ProjectDatabase;
import util.Logger;

public class HudElementRecord extends GlobalsRecord
{
	public static final String DEFAULT_SCRIPT = "SetVisible"
		+ "\nSetTileSize ( .IconSize:32x32 )"
		+ "\nLoop"
		+ "\n\tSetIcon     ( 60` ~ImageIcon:IMAGE_NAME )"
		+ "\nRestart"
		+ "\nEnd";

	public String identifier;
	public int offset = -1;
	public int itemIndex = -1;

	private String body = "";
	public String previewImageName;

	public List<Line> lines = null;
	public ByteBuffer out = null;
	public int finalAddress = -1;

	public HudElementRecord()
	{}

	public HudElementRecord(String name, int offset)
	{
		this.identifier = name;
		this.offset = offset;
	}

	@Override
	public String getFilterableString()
	{
		return identifier;
	}

	@Override
	public boolean canDeleteFromList()
	{
		return true;
	}

	@Override
	public String getIdentifier()
	{
		return identifier;
	}

	@Override
	public void setIdentifier(String newValue)
	{
		identifier = newValue;
	}

	@Override
	public String toString()
	{
		return identifier;
	}

	public String load() throws IOException
	{
		body = "";

		File f = new File(MOD_HUD_SCRIPTS + identifier + EXT_HUD_SCRIPT);
		if (f.exists()) {
			for (String line : IOUtils.readPlainTextFile(f))
				body = body + line + "\n";
		}

		scanScriptForPreviewImage();
		return f.getName();
	}

	public String save() throws IOException
	{
		File f = new File(MOD_HUD_SCRIPTS + identifier + EXT_HUD_SCRIPT);
		FileUtils.touch(f);

		try (PrintWriter pw = IOUtils.getBufferedPrintWriter(f)) {
			for (String line : body.split("\r?\n"))
				pw.println(line);
		}

		return f.getName();
	}

	public void buildAutoScript(String imageAssetName, int sizeW, int sizeH)
	{
		StringBuilder sb = new StringBuilder();
		sb.append("SetVisible");
		if (ProjectDatabase.getFromNamespace("IconSize").hasID(sizeW + "x" + sizeH))
			sb.append("\nSetTileSize ( .IconSize:" + sizeW + "x" + sizeH + " )");
		else
			sb.append("\nSetCustomSize ( " + sizeW + "` " + sizeH + "` )");
		sb.append("\nLoop");
		sb.append("\n\tSetIcon     ( 60` ~ImageIcon:").append(imageAssetName).append(" )");
		sb.append("\nRestart");
		sb.append("\nEnd");

		String text = sb.toString();
		setBody(text);

		AbstractSource src = new DummySource("HudElemScript" + identifier);
		int line = 0;
		lines = new ArrayList<>();
		for (String s : text.split("\r?\n"))
			lines.add(new Line(src, line++, s.trim()));
	}

	public void setBody(String text)
	{
		body = text;
		scanScriptForPreviewImage();
	}

	public String getBody()
	{
		return body;
	}

	private static final Pattern ImgPattern = Pattern.compile(".+\\s~Image(Icon|CI|RGBA):(\\S+)\\s.+");
	private static final Matcher ImgMatcher = ImgPattern.matcher("");

	public void scanScriptForPreviewImage()
	{
		previewImageName = "";

		InputStream targetStream = org.apache.commons.io.IOUtils.toInputStream(body, StandardCharsets.UTF_8);
		try {
			for (String line : IOUtils.readFormattedTextStream(targetStream)) {
				ImgMatcher.reset(line);
				if (ImgMatcher.matches()) {
					previewImageName = ImgMatcher.group(2);
					return;
				}
			}
		}
		catch (IOException e) {
			Logger.printStackTrace(e);
		}
	}
}

package game.texture.images;

import static app.Directories.MOD_IMG_ASSETS;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;

import game.globals.editor.GlobalsRecord;
import game.texture.Tile;
import game.texture.TileFormat;

public class ImageRecord extends GlobalsRecord
{
	public boolean hasRaster = true;
	public int palCount = 1;

	// set when the database is loaded
	public boolean fixedPos;
	public int imgOffset = -1;
	public int palOffset = -1;
	public int imgAddress = -1;
	public int palAddress = -1;

	public TileFormat fmt;
	public int sizeW;
	public int sizeH;
	public boolean flip = false;

	public String identifier;

	public String[] source;
	public Tile[] tile;
	public BufferedImage[] fullsize;
	public ImageIcon[] preview;
	public ImageIcon[] smallPreview;

	@Override
	public String getFilterableString()
	{
		return identifier;
	}

	@Override
	public boolean canDeleteFromList()
	{
		return !fixedPos;
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

	private static final Pattern SuffixedImagePattern = Pattern.compile("(\\S+)_(alt\\d*)");
	private static final Matcher SuffixedImageMatcher = SuffixedImagePattern.matcher("");

	public static class ImageReference
	{
		public final String name;
		public final int index;

		private ImageReference(String name, int index)
		{
			this.name = name;
			this.index = index;
		}
	}

	public static ImageReference parseImageName(String name)
	{
		if (name == null || name.isBlank())
			return null;

		SuffixedImageMatcher.reset(name);

		String baseName = name;
		int index = 0;

		if (SuffixedImageMatcher.matches()) {
			baseName = SuffixedImageMatcher.group(1);
			if (SuffixedImageMatcher.group(2).equalsIgnoreCase("alt"))
				index = 1;
			else
				index = Integer.parseInt(SuffixedImageMatcher.group(2).substring(3));
		}

		return new ImageReference(baseName, index);
	}

	public void loadTiles() throws IOException
	{
		tile = new Tile[palCount];
		source = new String[palCount];

		loadTile(0, identifier);

		File altFile = new File(MOD_IMG_ASSETS + identifier + "_alt.png");
		if (palCount == 2 && altFile.exists()) {
			loadTile(1, identifier + "_alt");
			return;
		}

		for (int i = 1; i < palCount; i++)
			loadTile(i, identifier + "_alt" + i);
	}

	private void loadTile(int index, String srcName) throws IOException
	{
		File srcFile = new File(MOD_IMG_ASSETS + srcName + ".png");
		tile[index] = Tile.load(srcFile, fmt);
		source[index] = srcName;
	}

	public void loadPreviews() throws IOException
	{
		loadPreviews(-1);
	}

	public void loadPreviews(int maxSize) throws IOException
	{
		fullsize = new BufferedImage[palCount];
		preview = new ImageIcon[palCount];
		smallPreview = new ImageIcon[palCount];
		source = new String[palCount];

		loadPreview(maxSize, 0, identifier);

		File altFile = new File(MOD_IMG_ASSETS + identifier + "_alt.png");
		if (palCount == 2 && altFile.exists()) {
			loadPreview(maxSize, 1, identifier + "_alt");
			return;
		}

		for (int i = 1; i < palCount; i++)
			loadPreview(maxSize, i, identifier + "_alt" + i);
	}

	private void loadPreview(int maxSize, int index, String srcName) throws IOException
	{
		File srcFile = new File(MOD_IMG_ASSETS + srcName + ".png");
		source[index] = srcName;
		fullsize[index] = null;
		preview[index] = null;
		smallPreview[index] = null;

		if (srcFile.exists()) {
			BufferedImage bimg = ImageIO.read(srcFile);
			fullsize[index] = bimg;

			if (maxSize > 0 && (bimg.getWidth() > maxSize || bimg.getHeight() > maxSize))
				preview[index] = new ImageIcon(resizeImage(bimg, maxSize));
			else
				preview[index] = new ImageIcon(bimg);

			if (bimg.getWidth() > 16 || bimg.getHeight() > 16)
				smallPreview[index] = new ImageIcon(resizeImage(bimg, 16));
			else
				smallPreview[index] = new ImageIcon(bimg);
		}
	}

	private static BufferedImage resizeImage(BufferedImage src, int targetSize)
	{
		if (targetSize <= 0) {
			return src; //this can't be resized
		}
		int targetWidth = targetSize;
		int targetHeight = targetSize;
		float ratio = ((float) src.getHeight() / (float) src.getWidth());
		if (ratio <= 1) { //square or landscape-oriented image
			targetHeight = (int) Math.ceil(targetWidth * ratio);
		}
		else { //portrait image
			targetWidth = Math.round(targetHeight / ratio);
		}
		BufferedImage bi = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2d = bi.createGraphics();
		g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g2d.drawImage(src, 0, 0, targetWidth, targetHeight, null);
		g2d.dispose();
		return bi;
	}
}

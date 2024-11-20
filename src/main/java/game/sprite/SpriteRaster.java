package game.sprite;

import java.awt.Image;
import java.awt.image.BufferedImage;

import javax.swing.ImageIcon;

import org.apache.commons.io.FilenameUtils;

import game.sprite.PlayerSpriteConverter.RasterTableEntry;
import game.sprite.SpriteLoader.Indexable;
import game.texture.ImageConverter;
import game.texture.Tile;

public class SpriteRaster implements Indexable<Tile>
{
	// normal sprites
	public Tile img;
	public String filename;
	public SpritePalette defaultPal;

	// player backsprites special case
	public boolean isSpecial;
	public int specialWidth;
	public int specialHeight;

	// player duming
	public transient RasterTableEntry tableEntry;

	// editor fields
	protected transient int listIndex;
	public transient String name = "";
	public transient boolean imported;

	public transient BufferedImage previewImg;
	public transient ImageIcon icon;

	public transient int atlasRow, atlasX, atlasY;
	public transient boolean selected;
	public transient boolean highlighted;
	public transient boolean deleted;

	// deserialization only
	public transient int palIndex;

	public SpriteRaster()
	{}

	public SpriteRaster(SpriteRaster original)
	{
		img = new Tile(original.img);
		isSpecial = original.isSpecial;
		specialWidth = original.specialWidth;
		specialHeight = original.specialHeight;
		name = original.name;

		defaultPal = original.defaultPal;
		loadEditorImages();
	}

	@Override
	public String toString()
	{
		return name.isEmpty() ? FilenameUtils.removeExtension(filename) : name;
	}

	@Override
	public Tile getObject()
	{
		return img;
	}

	@Override
	public int getIndex()
	{
		return listIndex;
	}

	public void loadEditorImages()
	{
		previewImg = ImageConverter.getIndexedImage(img, defaultPal.pal);
		icon = new ImageIcon(getScaledToFit(previewImg, 80));
	}

	private static Image getScaledToFit(BufferedImage in, int maxSize)
	{
		if (in.getHeight() > in.getWidth()) {
			if (in.getHeight() > maxSize) {
				return in.getScaledInstance(
					(int) (in.getWidth() * (maxSize / (float) in.getHeight())), maxSize, java.awt.Image.SCALE_SMOOTH);
			}
		}
		else {
			if (in.getWidth() > maxSize) {
				return in.getScaledInstance(maxSize,
					(int) (in.getHeight() * (maxSize / (float) in.getWidth())), java.awt.Image.SCALE_SMOOTH);
			}
		}

		return in.getScaledInstance(in.getWidth(), in.getHeight(), java.awt.Image.SCALE_DEFAULT);
	}
}

package game.sprite;

import org.apache.commons.io.FilenameUtils;

import game.sprite.SpriteLoader.Indexable;
import game.texture.Palette;

public final class SpritePalette implements Indexable<SpritePalette>
{
	public Palette pal;
	public String filename;

	// editor fields
	protected transient int listIndex;
	public transient String name = "";
	public transient boolean dirty; // needs reupload to GPU
	public transient boolean modified;
	public transient boolean deleted;

	// build fields
	public transient int writeOffset;
	public transient boolean dumped;

	public SpritePalette(Palette pal)
	{
		this.pal = pal;
	}

	public SpritePalette(SpritePalette original)
	{
		pal = new Palette(original.pal);
		name = original.name;
	}

	@Override
	public String toString()
	{
		return name.isEmpty() ? FilenameUtils.removeExtension(filename) : name;
	}

	@Override
	public SpritePalette getObject()
	{
		return this;
	}

	@Override
	public int getIndex()
	{
		return listIndex;
	}
}

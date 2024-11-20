package game.shared.decoder;

import game.texture.Tile;

public class EmbeddedImage
{
	public final Tile tile;
	public final int imgAddr;
	public final int palAddr;

	public EmbeddedImage(Tile tile, int img, int pal)
	{
		this.tile = tile;
		this.imgAddr = img;
		this.palAddr = pal;
	}

	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
			return true;

		if (obj == null)
			return false;

		if (getClass() != obj.getClass())
			return false;

		EmbeddedImage other = (EmbeddedImage) obj;

		if (imgAddr != other.imgAddr)
			return false;

		if (palAddr != other.palAddr)
			return false;

		return true;
	}

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = 1;
		result = prime * result + imgAddr;
		result = prime * result + palAddr;
		return result;
	}
}

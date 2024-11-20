package shared;

import java.awt.Image;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;

import app.Directories;
import app.Resource;
import app.Resource.ResourceType;

public class Globals
{
	public static final int MOD_HEADER_IDENTIFIER = 0x504D5352;
	public static final int MOD_HEADER_IDENTIFIER_BS = 0x4D505253;

	public static ImageIcon ICON_DEFAULT = loadIconResource(ResourceType.Icon, "icon.png");
	public static ImageIcon ICON_ERROR = null;

	public static void reloadIcons()
	{
		ICON_DEFAULT = loadIconFile(Directories.DUMP_IMG_ASSETS + "item/battle/ShootingStar.png");
		if (ICON_DEFAULT == null)
			ICON_DEFAULT = loadIconResource(ResourceType.Icon, "icon.png");
		ICON_ERROR = loadIconFile(Directories.DUMP_SPR_NPC_SRC + "3B/Raster_1A.png");
	}

	public static final Image getDefaultIconImage()
	{
		return (ICON_DEFAULT == null) ? null : ICON_DEFAULT.getImage();
	}

	public static final Image getErrorIconImage()
	{
		return (ICON_DEFAULT == null) ? null : ICON_DEFAULT.getImage();
	}

	private static ImageIcon loadIconFile(String fileName)
	{
		File imageFile = new File(fileName);
		if (!imageFile.exists()) {
			System.err.println("Unable to find image " + fileName);
			return null;
		}

		try {
			return new ImageIcon(ImageIO.read(imageFile));
		}
		catch (IOException e) {
			System.err.println("Exception while loading image " + fileName);
			return null;
		}
	}

	private static ImageIcon loadIconResource(ResourceType type, String resourceName)
	{
		try {
			return new ImageIcon(ImageIO.read(Resource.getStream(type, resourceName)));
		}
		catch (IOException e) {
			System.err.println("Exception while loading image " + resourceName);
			return null;
		}
	}
}

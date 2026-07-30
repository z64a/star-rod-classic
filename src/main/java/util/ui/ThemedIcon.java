package util.ui;

import java.io.IOException;
import java.io.InputStream;

import javax.swing.UIManager;

import com.formdev.flatlaf.extras.FlatSVGIcon;

import app.Resource;
import app.Resource.ResourceType;
import util.Logger;

public abstract class ThemedIcon
{
	private static FlatSVGIcon getIcon(String name)
	{
		try (InputStream is = Resource.getStream(ResourceType.Icon, name + ".svg")) {
			FlatSVGIcon icon = new FlatSVGIcon(is);
			icon.setColorFilter(new FlatSVGIcon.ColorFilter(
				color -> UIManager.getColor("Label.foreground")));
			return icon;
		}
		catch (IOException e) {
			Logger.logError(e.getMessage());
			return null;
		}
	}

	public static final FlatSVGIcon REWIND_24 = getIcon("rewind_24");
	public static final FlatSVGIcon STOP_24 = getIcon("stop_24");
	public static final FlatSVGIcon PLAY_24 = getIcon("play_24");
	public static final FlatSVGIcon PAUSE_24 = getIcon("pause_24");
	public static final FlatSVGIcon VOLUME_UP_24 = getIcon("volume_up_24");
	public static final FlatSVGIcon VOLUME_OFF_24 = getIcon("volume_off_24");

	public static final FlatSVGIcon VOLUME_UP_16 = VOLUME_UP_24.derive(16, 16);
	public static final FlatSVGIcon VOLUME_OFF_16 = VOLUME_OFF_24.derive(16, 16);
}

package game.fold;

import java.awt.event.KeyEvent;

import common.CameraInput;
import common.KeyboardInputConfig;

final class FoldKeyConfig extends KeyboardInputConfig
{
	FoldKeyConfig()
	{
		addDefault(FoldInput.RESET_CAMERA, KeyEvent.VK_SPACE);
		addDefault(FoldInput.PREVIOUS_ANIMATION, KeyEvent.VK_UP);
		addDefault(FoldInput.NEXT_ANIMATION, KeyEvent.VK_DOWN);
		addDefault(CameraInput.PAN_UP, KeyEvent.VK_W);
		addDefault(CameraInput.PAN_DOWN, KeyEvent.VK_S);
		addDefault(CameraInput.PAN_LEFT, KeyEvent.VK_A);
		addDefault(CameraInput.PAN_RIGHT, KeyEvent.VK_D);
		finishDefaults();
	}
}

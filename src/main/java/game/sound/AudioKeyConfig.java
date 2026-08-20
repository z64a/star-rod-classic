package game.sound;

import java.awt.event.KeyEvent;

import common.CameraInput;
import common.KeyboardInputConfig;

final class AudioKeyConfig extends KeyboardInputConfig
{
	AudioKeyConfig()
	{
		addDefault(AudioInput.RESET_CAMERA, KeyEvent.VK_SPACE);
		addDefault(AudioInput.TOGGLE_GRID, KeyEvent.VK_G);
		addDefault(AudioInput.TOGGLE_BACKGROUND, KeyEvent.VK_B);
		addDefault(CameraInput.PAN_UP, KeyEvent.VK_W);
		addDefault(CameraInput.PAN_DOWN, KeyEvent.VK_S);
		addDefault(CameraInput.PAN_LEFT, KeyEvent.VK_A);
		addDefault(CameraInput.PAN_RIGHT, KeyEvent.VK_D);
		finishDefaults();
	}
}

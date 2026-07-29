package game.sprite.editor;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import common.CameraInput;
import common.KeyboardInputConfig;

final class SpriteKeyConfig extends KeyboardInputConfig
{
	SpriteKeyConfig()
	{
		addDefault(SpriteInput.RESET_CAMERA, KeyEvent.VK_SPACE);
		addDefault(SpriteInput.DELETE_SELECTED, KeyEvent.VK_DELETE);
		addDefault(SpriteInput.TOGGLE_COMPONENT, KeyEvent.VK_H);
		addDefault(SpriteInput.RESET_PLAYBACK, KeyEvent.VK_HOME, InputEvent.ALT_DOWN_MASK);
		addDefault(SpriteInput.PLAY, KeyEvent.VK_UP, InputEvent.ALT_DOWN_MASK);
		addDefault(SpriteInput.STOP, KeyEvent.VK_DOWN, InputEvent.ALT_DOWN_MASK);
		addDefault(SpriteInput.PREVIOUS_FRAME, KeyEvent.VK_LEFT, InputEvent.ALT_DOWN_MASK);
		addDefault(SpriteInput.NEXT_FRAME, KeyEvent.VK_RIGHT, InputEvent.ALT_DOWN_MASK);
		addDefault(CameraInput.PAN_UP, KeyEvent.VK_W);
		addDefault(CameraInput.PAN_DOWN, KeyEvent.VK_S);
		addDefault(CameraInput.PAN_LEFT, KeyEvent.VK_A);
		addDefault(CameraInput.PAN_RIGHT, KeyEvent.VK_D);

		makeGlobal(SpriteInput.RESET_PLAYBACK);
		makeGlobal(SpriteInput.PLAY);
		makeGlobal(SpriteInput.STOP);
		makeGlobal(SpriteInput.PREVIOUS_FRAME);
		makeGlobal(SpriteInput.NEXT_FRAME);
		finishDefaults();
	}
}

package game.string.editor;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import common.CameraInput;
import common.KeyboardInputConfig;

final class StringKeyConfig extends KeyboardInputConfig
{
	StringKeyConfig()
	{
		addDefault(StringInput.RESET_CAMERA, KeyEvent.VK_SPACE);
		addDefault(StringInput.UNDO, KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK);
		addDefault(StringInput.REDO, KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK);
		addDefault(CameraInput.PAN_UP, KeyEvent.VK_UP);
		addDefault(CameraInput.PAN_DOWN, KeyEvent.VK_DOWN);
		addDefault(CameraInput.PAN_LEFT, KeyEvent.VK_LEFT);
		addDefault(CameraInput.PAN_RIGHT, KeyEvent.VK_RIGHT);

		makeGlobal(StringInput.UNDO);
		makeGlobal(StringInput.REDO);
		finishDefaults();
	}
}

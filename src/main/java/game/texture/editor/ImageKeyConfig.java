package game.texture.editor;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import common.CameraInput;
import common.KeyboardInputConfig;

final class ImageKeyConfig extends KeyboardInputConfig
{
	ImageKeyConfig()
	{
		addDefault(ImageInput.RESET_CAMERA, KeyEvent.VK_SPACE);
		addDefault(ImageInput.UNDO, KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK);
		addDefault(ImageInput.REDO, KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK);
		addDefault(ImageInput.FILL_SELECTION, KeyEvent.VK_F);
		addDefault(ImageInput.FILL_SELECTED_AREA, KeyEvent.VK_F, InputEvent.SHIFT_DOWN_MASK);
		addDefault(ImageInput.FILL_DESELECTED_AREA, KeyEvent.VK_F, InputEvent.ALT_DOWN_MASK);
		addDefault(ImageInput.CLEAR_SELECTION, KeyEvent.VK_K, InputEvent.CTRL_DOWN_MASK);
		addDefault(ImageInput.TOGGLE_GRID, KeyEvent.VK_G);
		addDefault(ImageInput.TOGGLE_BACKGROUND, KeyEvent.VK_B);
		addDefault(CameraInput.PAN_UP, KeyEvent.VK_W);
		addDefault(CameraInput.PAN_DOWN, KeyEvent.VK_S);
		addDefault(CameraInput.PAN_LEFT, KeyEvent.VK_A);
		addDefault(CameraInput.PAN_RIGHT, KeyEvent.VK_D);

		makeGlobal(ImageInput.UNDO);
		makeGlobal(ImageInput.REDO);
		finishDefaults();
	}
}

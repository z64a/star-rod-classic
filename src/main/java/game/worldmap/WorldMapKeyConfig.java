package game.worldmap;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import common.CameraInput;
import common.KeyboardInputConfig;

final class WorldMapKeyConfig extends KeyboardInputConfig
{
	WorldMapKeyConfig()
	{
		addDefault(WorldMapInput.RESET_CAMERA, KeyEvent.VK_SPACE);
		addDefault(WorldMapInput.UNDO, KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK);
		addDefault(WorldMapInput.REDO, KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK);
		addDefault(WorldMapInput.TOGGLE_GRID, KeyEvent.VK_G);
		addDefault(WorldMapInput.TOGGLE_PATHS, KeyEvent.VK_N);
		addDefault(WorldMapInput.TOGGLE_MARKERS, KeyEvent.VK_M);
		addDefault(WorldMapInput.TOGGLE_BACKGROUND, KeyEvent.VK_B);
		addDefault(CameraInput.PAN_UP, KeyEvent.VK_W);
		addDefault(CameraInput.PAN_DOWN, KeyEvent.VK_S);
		addDefault(CameraInput.PAN_LEFT, KeyEvent.VK_A);
		addDefault(CameraInput.PAN_RIGHT, KeyEvent.VK_D);

		makeGlobal(WorldMapInput.UNDO);
		makeGlobal(WorldMapInput.REDO);
		finishDefaults();
	}
}

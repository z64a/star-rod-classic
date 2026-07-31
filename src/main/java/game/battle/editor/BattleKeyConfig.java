package game.battle.editor;

import java.awt.event.KeyEvent;

import common.KeyboardInputConfig;

final class BattleKeyConfig extends KeyboardInputConfig
{
	BattleKeyConfig()
	{
		addDefault(BattleInput.RESET_CAMERA, KeyEvent.VK_SPACE);
		finishDefaults();
	}
}

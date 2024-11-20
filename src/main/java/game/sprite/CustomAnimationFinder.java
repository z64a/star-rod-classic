package game.sprite;

import static app.Directories.DUMP_SPR_NPC_SRC;

import java.io.File;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import app.Environment;
import game.sprite.SpriteLoader.SpriteSet;

public class CustomAnimationFinder
{
	public static void main(String[] args) throws IOException, NoSuchAlgorithmException
	{
		Environment.initialize();
		findCustom();
		Environment.exit();
	}

	public static void findCustom() throws IOException
	{
		for (int i = 1; i <= 0xE9; i++) {
			String spriteName = String.format("%02X", i);
			File spriteDir = new File(DUMP_SPR_NPC_SRC + String.format("%02X/", i));
			File newXml = new File(spriteDir.getPath() + "/SpriteSheet.xml");

			File oldDir = new File("S:/Dropbox/pmpm/mod/sprite/npc/src/" + spriteName);
			File oldXml = new File(oldDir.getPath() + "/SpriteSheet.xml");

			Sprite sprNew = Sprite.read(newXml, SpriteSet.Npc);
			Sprite sprOld = Sprite.read(oldXml, SpriteSet.Npc);

			if (sprNew.animations.size() != sprOld.animations.size())
				System.out.printf("[%s] Animation count differs: %d vs %d%n", spriteName, sprNew.animations.size(), sprOld.animations.size());
			if (sprNew.palettes.size() != sprOld.palettes.size())
				System.out.printf("[%s] Palette count differs: %d vs %d%n", spriteName, sprNew.palettes.size(), sprOld.palettes.size());
			if (sprNew.rasters.size() != sprOld.rasters.size())
				System.out.printf("[%s] Raster count differs: %d vs %d%n", spriteName, sprNew.rasters.size(), sprOld.rasters.size());

			for (int j = 0; j < sprNew.animations.size(); j++) {
				SpriteAnimation oldAnim = sprOld.animations.get(j);
				SpriteAnimation newAnim = sprNew.animations.get(j);

				assert (oldAnim.components.size() == newAnim.components.size());

				for (int k = 0; k < newAnim.components.size(); k++) {
					SpriteComponent oldComp = oldAnim.components.get(k);
					SpriteComponent newComp = newAnim.components.get(k);

					List<Short> oldCommands = oldComp.animator.getCommandList();
					List<Short> newCommands = newComp.animator.getCommandList();

					assert (oldCommands.size() == newCommands.size());
					for (int l = 0; l < newCommands.size(); l++) {
						short s1 = oldCommands.get(l);
						short s2 = newCommands.get(l);
						assert (s1 == s2) : String.format("[%s] %X %X", spriteName, s1, s2);
					}
				}
			}
		}
	}
}

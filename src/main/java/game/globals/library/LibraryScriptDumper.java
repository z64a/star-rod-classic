package game.globals.library;

import java.io.IOException;
import java.nio.ByteBuffer;

import app.Environment;

public abstract class LibraryScriptDumper
{
	public static void main(String[] args) throws IOException
	{
		Environment.initialize();
		dumpAll();
		Environment.exit();
	}

	public static void dumpAll() throws IOException
	{
		// boot dmacopy (in load order)

		//	new WorldLibDecoder(fileBuffer, 0x316F30, 0x317020, 0x802B2000);
		//	003169F0 00316A70 80200000
		//	00316A70 00316C00 80200080
		//	new WorldLibDecoder (fileBuffer, 0x0FEE30, 0x102610, 0x802DBD40);
		//	new WorldLibDecoder (fileBuffer, 0x0759B0, 0x0A5DD0, 0x800DC500);
		//	new WorldLibDecoder (fileBuffer, 0x0E79B0, 0x0FEE30, 0x802C3000);
		//	new WorldLibDecoder (fileBuffer, 0x102610, 0x10CC10, 0x802E0D90);
		//	new WorldLibDecoder (fileBuffer, 0x0A5DD0, 0x0E79B0, 0x8010F6D0);
		//	new WorldLibDecoder (fileBuffer, 0x10CC10, 0x10F1B0, 0x802EB3D0);
		//	new WorldLibDecoder (fileBuffer, 0x10F1B0, 0x1142B0, 0x802EE8D0);
		//	new WorldLibDecoder (fileBuffer, 001144B0, 001149B0, 802F4560);
		//	00325AD0 00326410 E0200000
		//	0010F1B0 001142B0 802EE8D0
		//	001144B0 001149B0 802F4560
		//	00325AD0 00326410 E0200000
		//	001FE1B0 002191B0 (heap)

		// start battle dmacopy (in load order)

		//	00316F30 00317020 802B2000
		//	00316D90 00316F30 802AE000
		//	0016C8E0 001CC310 8023E000		// battle lib
		//	... battle map .... [8023E5CC]
		//	00385640 003863B0 E0082000
		//	003863B0 003889D0 (heap)
		//	00328110 00328EA0 E000C000

		// end battle dmacopy

		//  collectively, these fill
		//	00E4B2E0 00E4E7F0 80264AE0
		//	00E3DDB0 00E3E260 80264630
		//	00E3B870 00E3C320 80263B80
		//	00E657A0 00E67120 80262200
		//	00E67120 00E68580 80260DA0
		//	007E0E80 007E73A0 80280000
		//	... map stuff ...

		// load battle dmacopy 0x73DA0  00759B0
		//	80070000 4B400

		ByteBuffer fileBuffer = Environment.getBaseRomBuffer();

		new WorldLibDecoder(fileBuffer, 0x1000, 0x0759B0, 0x80025C00, "001000 (80025C00) System", "lib_system.hint"); // system, this is all that gets loaded at once
		new WorldLibDecoder(fileBuffer, 0x0759B0, 0x0A5DD0, 0x800DC500, "0759B0 (800DC500)", "lib_DC500.hint"); // boot
		new WorldLibDecoder(fileBuffer, 0x0A5DD0, 0x0E79B0, 0x8010F6D0, "0A5DD0 (8010F6D0) Rendering", "lib_render.hint"); // boot
		new WorldLibDecoder(fileBuffer, 0x0E79B0, 0x0FEE30, 0x802C3000, "0E79B0 (802C3000) ScriptInterpreter"); // boot
		new WorldLibDecoder(fileBuffer, 0x0FEE30, 0x102610, 0x802DBD40, "0FEE30 (802DBD40) SpriteLib", "lib_sprite.hint"); // boot

		new WorldLibDecoder(fileBuffer, 0x102610, 0x10CC10, 0x802E0D90, "102610 (802E0D90) Common Entity Data", "lib_entity.hint"); // boot
		new WorldLibDecoder(fileBuffer, 0x3251D0, 0x325AD0, 0x802C0000, "World UseItem", "lib_world_useitem.hint");

		new WorldLibDecoder(fileBuffer, 0x10CC10, 0x10F1B0, 0x802EB3D0, "10CC10 (802EB3D0)"); // boot
		new WorldLibDecoder(fileBuffer, 0x10F1B0, 0x1142B0, 0x802EE8D0, "10F1B0 (802EE8D0)");
		new WorldLibDecoder(fileBuffer, 0x1144B0, 0x1149B0, 0x802F4560, "1144B0 (802F4560)");
		new WorldLibDecoder(fileBuffer, 0x1144B0, 0x1149B0, 0x802F4560, "1144B0 (802F4560)");

		new WorldLibDecoder(fileBuffer, 0xE225B0, 0xE23260, 0x802B7000, "E225B0 (802B7000) ISpy", "lib_ispy.hint");

		new WorldLibDecoder(fileBuffer, 0xE2B530, 0xE2D730, 0x802BAE00, "E2B530 (802BAE00) Entity Overlay Default", "lib_entity_default.hint");
		new WorldLibDecoder(fileBuffer, 0xE2D730, 0xE2F750, 0x802BAE00, "E2D730 (802BAE00) Entity Overlay jan+iwa", "lib_entity_janiwa.hint");
		new WorldLibDecoder(fileBuffer, 0xE2F750, 0xE31530, 0x802BAE00, "E2F750 (802BAE00) Entity Overlay sbk+omo", "lib_entity_sbkomo.hint");

		new BattleLibDecoder(fileBuffer, 0x415D90, 0x4219F0, 0x802A1000, "415D90 (802A1000) BattleMenu", "lib_battle_menu.hint");

		//	0x10F1B0, 0x1142B0, 0x802EE8D0
		//	001144B0 001149B0 802F4560
		new WorldLibDecoder(fileBuffer, 0x7E0E80, 0x7E73A0, 0x80280000, "7E0E80 (80280000) WorldLib");
		new BattleLibDecoder(fileBuffer, 0x16C8E0, 0x1CC310, 0x8023E000, "16C8E0 (8023E000) BattleLib", "lib_battle.hint");

		new PauseLibDecoder(fileBuffer, 0x131340, 0x135EE0, 0x8023E000, "131340 (8023E000) PauseIcons", "lib_pause_icons.hint");
		new PauseLibDecoder(fileBuffer, 0x135EE0, 0x163400, 0x80242BA0, "135EE0 (80242BA0) PauseLib", "lib_pause.hint");
		new MainMenuLibDecoder(fileBuffer, 0x163400, 0x16C8E0, 0x80242BA0, "163400 (80242BA0) MainMenu"); // main menu stuff
	}
}

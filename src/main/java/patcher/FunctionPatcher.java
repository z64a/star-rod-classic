package patcher;

import static patcher.Patcher.RAM_BASE;

import java.io.IOException;

import app.Environment;
import app.StarRodException;
import app.config.Config;
import app.config.Options;
import app.input.DummyInputFile;
import asm.AsmUtils;
import asm.MIPS;
import game.RAM;
import game.shared.encoder.GlobalPatchManager;
import game.string.StringEncoder;
import util.Logger;
import util.Priority;

public class FunctionPatcher
{
	// func_80025C60 is called while the game is initializing
	// it comes right after an empty space at 80025C40, so well just move the whole thing up
	// and set up a DmaCopy for our global code in the formerly empty space.
	public static void hookBoot(Patcher p, Config cfg, RomPatcher rp, int size) throws IOException
	{
		int endOfAvailableMemory = p.getGlobalPointerAddress(DefaultGlobals.WORLD_HEAP.toString());
		if (!cfg.getBoolean(Options.IncreaseHeapSizes))
			endOfAvailableMemory = 0x80800000;

		Logger.log(String.format("In-game RAM usage: %X bytes (%.02f%% of available)", size,
			(100.0 * size) / (endOfAvailableMemory - RAM_BASE)), Priority.IMPORTANT);

		if (Patcher.RAM_BASE + size > endOfAvailableMemory)
			throw new RuntimeException("Mod Compile Failed! \nRan out of in-game memory for new patches!");

		// change JAL address that jumps to this function
		// 000013DC <-> 80025FDC
		rp.seek("Boot Hook", 0x13DC);
		rp.writeInt(0x0C009710);

		// move the whole function up
		rp.seek("Boot Hook", 0x1040);
		int[] func_80025C60 = {
				0x27BDFFE8, 0x3C05B3FF, 0x34A50014, 0xAFB00010,
				0x3C10800A, 0x2610A638, 0xAFBF0014, 0x8E040000,
				0x0C018358, 0x0000302D, 0x3C05B3FF, 0x34A50004,
				0x8E040000, 0x0C018358, 0x0000302D, 0x3C05B3FF,
				0x3C064953, 0x8E040000, 0x0C018358, 0x34C63634
		};
		for (int i : func_80025C60) {
			rp.writeInt(i);
		}
		AsmUtils.assembleAndWrite("HookBootDma", rp, new String[] {
				String.format("LUI	A0, %X", romBeginBootCode >> 16),
				String.format("ORI	A0, A0, %X", romBeginBootCode & 0xFFFF),
				String.format("LUI	A1, %X", romEndBootCode >> 16),
				String.format("ORI	A1, A1, %X", romEndBootCode & 0xFFFF),
				String.format("LUI	A2, %X", RAM.WORLD_MAP_DATA_START >> 16),
				String.format("ORI	A2, A2, %X", RAM.WORLD_MAP_DATA_START & 0xFFFF),
				"JAL	8002973C", // dmacopy
				"NOP",
				String.format("J %X", RAM.WORLD_MAP_DATA_START + 0x200),
				"NOP",
				// make sure to pop registers before jumping back!
				"JR		RA",
				"ADDIU	SP, SP, 18"
		});

		rp.seek("Boot Hook", romHookDmaArgs);
		AsmUtils.assembleAndWrite("HookBootDmaArgs", rp, new String[] {
				String.format("LUI	A0, %X", Patcher.ROM_BASE >> 16),
				String.format("ORI	A0, A0, %X", Patcher.ROM_BASE & 0xFFFF),
				String.format("LUI	A1, %X", (Patcher.ROM_BASE + size) >> 16),
				String.format("ORI	A1, A1, %X", (Patcher.ROM_BASE + size) & 0xFFFF),
		});

		Logger.log("Boot process hooked.", Priority.IMPORTANT);
	}

	private static int romBeginBootCode = 0;
	private static int romEndBootCode = 0;
	private static int romHookDmaArgs = 0;

	public static void addBootCode(RomPatcher rp) throws IOException
	{
		romBeginBootCode = rp.nextAlignedOffset();
		rp.seek("Boot Code", romBeginBootCode);

		String[] lines = {
				//		"0123456789abcdefghijklkjihgfedcba9876543210",
				"",
				"    ---------------------------------",
				"        EXPANSION PACK NOT FOUND!",
				"    ---------------------------------",
				"",
				"Mods created with Star Rod require 8MB RAM.",
				"Insert your N64 expansion pack and reset.",
				"",
				"If using an emulator: increase the memory",
				"to 8MB in your settings and restart it."
		};

		int[] lineAddress = new int[10];

		for (int i = 0; i < 10; i++) {
			lineAddress[i] = RAM.WORLD_MAP_DATA_START + (rp.getCurrentOffset() - romBeginBootCode);
			rp.write(lines[i].getBytes());
			rp.writeByte(0);
			while ((rp.getCurrentOffset() & 0x3) != 0)
				rp.writeByte(0);
		}

		/*
		 * LUI		A2, XXXX
		 * ADDIU	A2, A2, YYYY
		 *
		 * 8002C4B8 AT ...
		 * 8002C4E0 A0 ...
		 * 8002C508 A3 ...
		 * 8002C530 T2 ...
		 * 8002C558 T5 ...
		 * 8002C580 S0 ...
		 * 8002C5A8 S3 ...
		 * 8002C5D0 S6 ...
		 * 8002C5F8 T9 ...
		 * 8002C618 S8 ...
		 */

		int[] lineArgs = {
				0x8002C4B8, 0x8002C4E0, 0x8002C508, 0x8002C530, 0x8002C558,
				0x8002C580, 0x8002C5A8, 0x8002C5D0, 0x8002C5F8, 0x8002C618
		};

		rp.seek("Boot Code", romBeginBootCode + 0x200);

		// check 80000318: 00800000 = 8MB, 00400000 = 4MB
		// if expansion pack is missing, go into an idle loop

		DummyInputFile bootCode = new DummyInputFile("AddBootCode");

		bootCode.add("LUI	A0, 8000");
		bootCode.add("LH 	A0, 318 (A0)");
		bootCode.add("ORI	A1, R0, 0080");
		bootCode.add("BEQL   A0, A1, .Has8MB");
		bootCode.add("NOP");

		// clear existing exception handler format strings at 80098034
		bootCode.add("LUI A1, 8009");
		bootCode.add("ORI A1, A1, 8034");
		bootCode.add("ADDIU A2, R0, 1A0");

		bootCode.add(".ClearFormatStrings");
		bootCode.add("SW 	R0, 0 (A1)");
		bootCode.add("ADDIU	A1, A1, 4");
		bootCode.add("ADDIU	A2, A2, -4");
		bootCode.add("BNE    A2, R0, .ClearFormatStrings");
		bootCode.add("NOP");

		// replace references to format strings with our own
		for (int i = 0; i < 10; i++) {
			bootCode.add(String.format("LUI	A0, %X", lineArgs[i] >> 16));
			bootCode.add(String.format("ORI	A0, A0, %X", lineArgs[i] & 0xFFFF));
			bootCode.add(String.format("ADDIU A1, R0, %X", lineAddress[i] >> 16));
			bootCode.add(String.format("ADDIU A2, R0, %X", lineAddress[i] & 0xFFFF));

			bootCode.add(String.format("SH	A1, 2 (A0)"));
			bootCode.add(String.format("SH	A2, 6 (A0)"));
		}

		// trigger an exception, or jump directly to handler if it fails, or idle forever if both fail
		bootCode.add("LUI	A0, C000");
		bootCode.add("LWR    A1, 9200 (A3)");
		bootCode.add("NOP");
		bootCode.add("J		80000000");
		bootCode.add("NOP");
		bootCode.add(".IdleForever");
		bootCode.add("BEQ	R0, R0, .IdleForever");
		bootCode.add("NOP");
		bootCode.add(".Has8MB");

		AsmUtils.assembleAndWrite(rp, bootCode.lines);

		// leave a space for the DmaArgs, we'll set them up later in hookBoot
		// after all the global data is actually written to the ROM
		romHookDmaArgs = rp.getCurrentOffset();
		rp.skip(16);

		// copy the global data and jump back to the hook
		AsmUtils.assembleAndWrite("BootCodeHook", rp, new String[] {
				String.format("LUI	A2, %X", Patcher.RAM_BASE >> 16),
				String.format("ORI	A2, A2, %X", Patcher.RAM_BASE & 0xFFFF),
				"JAL	8002973C", // dmacopy
				"NOP",
				"LW		RA, 14 (SP)",
				"J		80025CB8", //TODO was 80025FE4, check this!
				"LW		S0, 10 (SP)"
		});

		romEndBootCode = rp.getCurrentOffset();

		Logger.log(String.format("Boot code written from 0x%08X to 0x%08X",
			romBeginBootCode, romEndBootCode), Priority.IMPORTANT);
	}

	public static void showVersionInfo(RomPatcher rp, int offset) throws IOException
	{
		Logger.log(String.format("Version information will be stored at 0x%08X",
			Patcher.toAddress(offset)), Priority.IMPORTANT);
		rp.seek("Version Info", offset);

		int starRodStringAddress = Patcher.toAddress(rp.getCurrentOffset());
		rp.write(StringEncoder.encodeString("Star Rod v" + Environment.getVersionString(), true));
		rp.padOut(4);

		int modStringAddress = Patcher.toAddress(rp.getCurrentOffset());
		String modName = Environment.project.config.getString(Options.ModVersionString);
		rp.write(StringEncoder.encodeString(modName, true));
		rp.padOut(4);

		int fpPrintVersionString = Patcher.toAddress(rp.getCurrentOffset());
		AsmUtils.assembleAndWrite("ShowVersionInfo", rp, new String[] {
				String.format("LUI	A0, %04X    ", (starRodStringAddress) >>> 16),
				String.format("ORI	A0, A0, %04X", (starRodStringAddress & 0x0000FFFF)),
				"ORI 	A1, R0, C8", // pos X
				"SUB	A2, R0, S3", // animation position offset (goes from -22 to 25)
				"ADDI 	A2, A2, F5", // pos Y (220 + 25)
				"ORI 	A3, R0, FF", // opacity
				"SW 	R0, 10 (SP)",
				"JAL	8024997C", // DrawStringB()
				"SW 	R0, 14 (SP)",

				String.format("LUI	A0, %04X    ", (modStringAddress) >>> 16),
				String.format("ORI	A0, A0, %04X", (modStringAddress & 0x0000FFFF)),
				"ORI 	A1, R0, 08", // pos X
				"SUB	A2, R0, S3", // animation position offset (goes from -22 to 25)
				"ADDI 	A2, A2, F5", // pos Y (220 + 25)
				"ORI 	A3, R0, FF", // opacity
				"SW 	R0, 10 (SP)",
				"JAL	8024997C", // DrawStringB()
				"SW 	R0, 14 (SP)",

				"LW		RA, 28 (SP)",
				"J		80244CD8",
				"LW		S3, 24 (SP)"
		});

		//TODO really odd hook to use
		// 80244CD0 <-> 165530
		rp.seek("Print Version Hook", 0x165530);
		rp.writeInt(MIPS.getJumpIns(fpPrintVersionString));
		rp.writeInt(0); // NOP
	}

	public static void modifyHeaps(Patcher p, Config cfg, GlobalPatchManager gpm, RomPatcher rp) throws IOException
	{
		int worldHeapSize = 0x54000;
		int collisionHeapSize = 0x18000;
		int spriteHeapSize = 0x40000;
		int battleHeapSize = 0x25800;
		int audioHeapSize = 0x56000;

		int mapHeapBase = 0x802FB800;
		int collisionHeapBase = 0x80268000;
		int spriteHeapBase = 0x8034F800;
		int battleHeapBase = 0x803DA800;
		int audioHeapBase = 0x801AA000;

		if (cfg.getBoolean(Options.IncreaseHeapSizes)) {
			worldHeapSize = cfg.getHex(Options.HeapSizeWorld);
			collisionHeapSize = cfg.getHex(Options.HeapSizeCollision);
			spriteHeapSize = cfg.getHex(Options.HeapSizeSprite);
			battleHeapSize = cfg.getHex(Options.HeapSizeBattle);
			audioHeapSize = cfg.getHex(Options.HeapSizeAudio);

			if ((worldHeapSize & 0x7FF) != 0)
				throw new StarRodException("Invalid world heap size: %X %nSize must be multiple of 800.", worldHeapSize);

			if ((collisionHeapSize & 0x7FF) != 0)
				throw new StarRodException("Invalid collision heap size: %X %nSize must be multiple of 800.", collisionHeapSize);

			if ((battleHeapSize & 0x7FF) != 0)
				throw new StarRodException("Invalid battle heap size: %X %nSize must be multiple of 800.", battleHeapSize);

			if ((spriteHeapSize & 0xFFFF) != 0)
				throw new StarRodException("Invalid sprite heap size: %X %nSize must be multiple of 10000.", spriteHeapSize);

			if ((audioHeapSize & 0x7FF) != 0)
				throw new StarRodException("Invalid audio heap size: %X %nSize must be multiple of 800.", audioHeapSize);

			if (worldHeapSize < 0x54000)
				throw new StarRodException("Invalid world heap size: %X %nSize cannot be smaller than default: 54000.", worldHeapSize);

			if (collisionHeapSize < 18000)
				throw new StarRodException("Invalid world heap size: %X %nSize cannot be smaller than default: 18000.", collisionHeapSize);

			if (battleHeapSize < 0x25800)
				throw new StarRodException("Invalid world heap size: %X %nSize cannot be smaller than default: 25800.", battleHeapSize);

			if (spriteHeapSize < 0x40000)
				throw new StarRodException("Invalid world heap size: %X %nSize cannot be smaller than default: 40000.", spriteHeapSize);

			if (audioHeapSize < 0x56000)
				throw new StarRodException("Invalid audio heap size: %X %nSize cannot be smaller than default: 56000.", audioHeapSize);

			battleHeapBase = 0x80800000 - battleHeapSize;
			spriteHeapBase = battleHeapBase - spriteHeapSize;
			collisionHeapBase = spriteHeapBase - collisionHeapSize;
			mapHeapBase = collisionHeapBase - worldHeapSize;

			if (audioHeapSize > 0x56000) {
				audioHeapBase = mapHeapBase - audioHeapSize;
			}

			gpm.readInternalPatch("EnlargeHeaps.patch",
				String.format("%s=%08X", "AudioBase", audioHeapBase),
				String.format("%s=%08X", "AudioSize", audioHeapSize),
				String.format("%s=%08X", "WorldBase", mapHeapBase),
				String.format("%s=%08X", "WorldSize", worldHeapSize),
				String.format("%s=%08X", "CollisionBase", collisionHeapBase),
				String.format("%s=%08X", "CollisionSize", collisionHeapSize),
				String.format("%s=%08X", "BattleBase", battleHeapBase),
				String.format("%s=%08X", "BattleSize", battleHeapSize),
				String.format("%s=%08X", "SpriteBase", spriteHeapBase),
				String.format("%s=%08X", "SpriteSize", spriteHeapSize),
				String.format("%s=%X", "SpriteSizeUpper", spriteHeapSize >> 16));
		}

		p.setGlobalPointer(DefaultGlobals.WORLD_HEAP, mapHeapBase);
		p.setGlobalPointer(DefaultGlobals.COLLISION_HEAP, collisionHeapBase);
		p.setGlobalPointer(DefaultGlobals.SPRITE_HEAP, spriteHeapBase);
		p.setGlobalPointer(DefaultGlobals.BATTLE_HEAP, battleHeapBase);
		p.setGlobalPointer(DefaultGlobals.AUDIO_HEAP, audioHeapBase);

		p.setGlobalConstant(DefaultGlobals.WORLD_HEAP_SIZE, String.format("%08X", worldHeapSize));
		p.setGlobalConstant(DefaultGlobals.COLLISION_HEAP_SIZE, String.format("%08X", collisionHeapSize));
		p.setGlobalConstant(DefaultGlobals.SPRITE_HEAP_SIZE, String.format("%08X", spriteHeapSize));
		p.setGlobalConstant(DefaultGlobals.BATTLE_HEAP_SIZE, String.format("%08X", battleHeapSize));
		p.setGlobalConstant(DefaultGlobals.AUDIO_HEAP_SIZE, String.format("%08X", audioHeapSize));
	}

	@Deprecated
	public static void setInitialMap(RomPatcher rp, int areaID, int mapID, int entryID) throws IOException
	{
		int offset = rp.nextAlignedOffset();

		// clear setting default entry
		//	rp.seek("Initial Map", 0x168090); // 80247830
		//	rp.writeInt(0);

		rp.seek("Initial Map Hook", 0x168084); // 80247824
		rp.writeInt(MIPS.getJumpIns(Patcher.toAddress(offset)));
		rp.writeInt(0);
		rp.skip(4);
		rp.writeInt(0); // clear setting default entry

		rp.seek("Set Initial Map", offset);
		AsmUtils.assembleAndWrite("SetInitialMap", rp, new String[] {
				String.format("ADDIU		AT, R0, %X", areaID),
				"SH			AT, 86 (V1)",
				String.format("ADDIU		AT, R0, %X", mapID),
				"SH			AT, 8C (V1)",
				String.format("ADDIU		AT, R0, %X", entryID),
				"J			8024782C",
				"SH			AT, 8E (V1)"
		});
	}
}

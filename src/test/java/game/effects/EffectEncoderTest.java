package game.effects;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

import app.AppVersion;
import app.Directories;
import app.Environment;
import app.Project;
import app.input.InputFileException;
import app.input.InvalidInputException;
import game.ROM;
import game.ROM.LibScope;
import game.map.MapIndex;
import game.shared.ProjectDatabase;
import game.shared.struct.Struct;
import patcher.DefaultGlobals;
import patcher.IGlobalDatabase;
import patcher.RomPatcher;

@ResourceLock("StarRodEnvironment")
public class EffectEncoderTest
{
	@TempDir
	Path tempDir;

	@Test
	public void codePatchRetainsEffectVirtualAddress() throws Exception
	{
		prepareDirectories();

		String sourceName = "00 Test";
		int codeAddress = 0xE0002000;
		Files.write(EffectEncoder.getDumpRawFile(sourceName).toPath(), new byte[] {
				0x03, (byte) 0xE0, 0x00, 0x08,
				0x00, 0x00, 0x00, 0x00
		});
		Files.writeString(EffectEncoder.getDumpIndexFile(sourceName).toPath(),
			"$Function_Init#Function#0#E0002000#8\n"
				+ "$Start#Unknown#0#E0002000#8\n"
				+ "$End#Unknown#8#E0002008#FF8\n");

		File patchFile = Directories.MOD_EFFECT_PATCH.getFile(sourceName + ".epat");
		Files.writeString(patchFile.toPath(), "@ $Function_Init {\n    [0] NOP\n}\n");

		EffectTableEntry entry = readEntry(codeAddress, 8);
		EffectEncoder encoder = new EffectEncoder(new DummyGlobalsDatabase(), entry, false);
		encoder.encode(sourceName, List.of(patchFile));

		assertArrayEquals(new byte[16], Files.readAllBytes(EffectEncoder.getOutputFile(sourceName).toPath()));
		assertEquals(codeAddress, encoder.readInitAddress(sourceName));
	}

	@Test
	public void graphicsPatchRetainsSegmentNineAddress() throws Exception
	{
		prepareDirectories();

		String sourceName = "00 Test_Gfx";
		int graphicsAddress = EffectEditor.GRAPHICS_BASE;
		Files.write(EffectEncoder.getDumpRawFile(sourceName).toPath(), new byte[] {
				(byte) 0xDF, 0x00, 0x00, 0x00,
				0x00, 0x00, 0x00, 0x00
		});
		Files.writeString(EffectEncoder.getDumpIndexFile(sourceName).toPath(),
			"$DisplayList_Test#DisplayList#0#09000000#8\n"
				+ "$Start#Unknown#0#09000000#8\n"
				+ "$End#Unknown#8#09000008#0\n");

		File patchFile = Directories.MOD_EFFECT_PATCH.getFile(sourceName + ".epat");
		Files.writeString(patchFile.toPath(), "@ $DisplayList_Test {\n    [0] G_ENDDL\n}\n");

		EffectTableEntry entry = readEntry(0xE0002000, 8);
		EffectEncoder encoder = new EffectEncoder(new DummyGlobalsDatabase(), entry, true);
		encoder.encode(sourceName, List.of(patchFile));

		byte[] expected = new byte[16];
		expected[0] = (byte) 0xDF;
		assertArrayEquals(expected, Files.readAllBytes(EffectEncoder.getOutputFile(sourceName).toPath()));

		HashMap<String, Struct> structMap = new HashMap<>();
		encoder.loadIndexFile(structMap, EffectEncoder.getOutputIndexFile(sourceName));
		assertEquals(graphicsAddress, structMap.get("$Start").originalAddress);
	}

	@Test
	public void codePatchCannotExceedMappedPage() throws Exception
	{
		prepareDirectories();

		String sourceName = "00 Test";
		int codeAddress = 0xE0002000;
		Files.write(EffectEncoder.getDumpRawFile(sourceName).toPath(), new byte[] {
				0x03, (byte) 0xE0, 0x00, 0x08,
				0x00, 0x00, 0x00, 0x00
		});
		Files.writeString(EffectEncoder.getDumpIndexFile(sourceName).toPath(),
			"$Function_Init#Function#0#E0002000#8\n"
				+ "$Start#Unknown#0#E0002000#8\n"
				+ "$End#Unknown#8#E0002008#FF8\n");

		File patchFile = Directories.MOD_EFFECT_PATCH.getFile(sourceName + ".epat");
		Files.writeString(patchFile.toPath(), "#reserve 1000 $TooLarge\n");

		EffectTableEntry entry = readEntry(codeAddress, 8);
		EffectEncoder encoder = new EffectEncoder(new DummyGlobalsDatabase(), entry, false);
		assertThrows(InputFileException.class, () -> encoder.encode(sourceName, List.of(patchFile)));
	}

	@Test
	public void codePatchUsesOnlyFXLibrary() throws Exception
	{
		prepareDirectories();

		ROM previousRom = ProjectDatabase.rom;
		try {
			ProjectDatabase.reload(false);
			assertEquals(0xE0200000, ProjectDatabase.rom.getLibrary(LibScope.Effect).get("effect_rand_int").address);
			assertEquals(0xE0200420, ProjectDatabase.rom.getLibrary(LibScope.Effect).get("guTranslateF").address);
			assertNull(ProjectDatabase.rom.getLibrary(LibScope.Effect).get("PlayEffect"));

			String sourceName = "00 Test";
			int codeAddress = 0xE0002000;
			Files.write(EffectEncoder.getDumpRawFile(sourceName).toPath(), new byte[8]);
			Files.writeString(EffectEncoder.getDumpIndexFile(sourceName).toPath(),
				"$Function_Init#Function#0#E0002000#8\n"
					+ "$Start#Unknown#0#E0002000#8\n"
					+ "$End#Unknown#8#E0002008#FF8\n");

			File patchFile = Directories.MOD_EFFECT_PATCH.getFile(sourceName + ".epat");
			Files.writeString(patchFile.toPath(), "@ $Function_Init {\n    [0] JAL ~Func:guTranslateF\n}\n");

			EffectTableEntry entry = readEntry(codeAddress, 8);
			new EffectEncoder(new DummyGlobalsDatabase(), entry, false).encode(sourceName, List.of(patchFile));
			assertEquals(0x0C080108, ByteBuffer.wrap(Files.readAllBytes(EffectEncoder.getOutputFile(sourceName).toPath())).getInt());

			Files.writeString(patchFile.toPath(), "@ $Function_Init {\n    [0] JAL ~Func:PlayEffect\n}\n");
			EffectEncoder encoder = new EffectEncoder(new DummyGlobalsDatabase(), entry, false);
			assertThrows(InputFileException.class, () -> encoder.encode(sourceName, List.of(patchFile)));
		}
		finally {
			ProjectDatabase.rom = previousRom;
		}
	}

	private void prepareDirectories() throws Exception
	{
		Path dumpDir = tempDir.resolve("dump");
		Path modDir = tempDir.resolve("mod");
		Files.createDirectories(dumpDir);
		Files.createDirectories(modDir);
		Files.createFile(modDir.resolve("mod.cfg"));

		Directories.setDumpDirectory(dumpDir.toString());
		Directories.setProjectDirectory(modDir.toString());
		setCurrentVersion(AppVersion.fromString("0.5.9"));
		Environment.project = new Project(modDir.toFile());

		Files.createDirectories(Directories.DUMP_EFFECT_RAW.toFile().toPath());
		Files.createDirectories(Directories.DUMP_EFFECT_SRC.toFile().toPath());
		Files.createDirectories(Directories.MOD_EFFECT_PATCH.toFile().toPath());
		Files.createDirectories(Directories.MOD_EFFECT_TEMP.toFile().toPath());
	}

	private static void setCurrentVersion(AppVersion version) throws ReflectiveOperationException
	{
		Field field = Environment.class.getDeclaredField("currentVersion");
		field.setAccessible(true);
		field.set(null, version);
	}

	private static EffectTableEntry readEntry(int codeAddress, int codeSize)
	{
		ByteBuffer buffer = ByteBuffer.allocate(EffectTableEntry.SIZE);
		buffer.putInt(codeAddress);
		buffer.putInt(0x100000);
		buffer.putInt(0x100000 + codeSize);
		buffer.putInt(codeAddress);
		buffer.putInt(0);
		buffer.putInt(0);
		buffer.flip();
		return new EffectTableEntry(buffer);
	}

	private static class DummyGlobalsDatabase implements IGlobalDatabase
	{
		@Override
		public void setGlobalPointer(DefaultGlobals global, int addr)
		{}

		@Override
		public void setGlobalPointer(String name, int addr)
		{}

		@Override
		public boolean hasGlobalPointer(String name)
		{
			return false;
		}

		@Override
		public int getGlobalPointerAddress(String name)
		{
			return 0;
		}

		@Override
		public void setGlobalConstant(DefaultGlobals global, String value)
		{}

		@Override
		public void setGlobalConstant(String name, String value)
		{}

		@Override
		public boolean hasGlobalConstant(String name)
		{
			return false;
		}

		@Override
		public String getGlobalConstant(String name)
		{
			return name;
		}

		@Override
		public int resolveStringID(String s) throws InvalidInputException
		{
			return 0;
		}

		@Override
		public boolean hasStringName(String name)
		{
			return false;
		}

		@Override
		public int getStringFromName(String name)
		{
			return 0;
		}

		@Override
		public boolean hasMapIndex(String name)
		{
			return false;
		}

		@Override
		public MapIndex getMapIndex(String name)
		{
			return null;
		}

		@Override
		public int getNpcAnimID(String spriteName, String animName, String palName)
		{
			return 0;
		}

		@Override
		public int getPlayerAnimID(String spriteName, String animName, String palName)
		{
			return 0;
		}

		@Override
		public RomPatcher getRomPatcher()
		{
			return null;
		}
	}
}

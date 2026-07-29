package game.sound;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import game.sound.sfx.SfxArchive;
import game.sound.sfx.SfxBinary;
import game.sound.sfx.SfxFormatException;
import game.sound.sfx.SfxNames;
import game.sound.sfx.SfxValidator;
import game.sound.sfx.SfxXml;
import util.Logger;

public final class SfxModder
{
	private SfxModder()
	{}

	public record DumpSummary(int sounds, int effectFiles, int envelopes, List<String> warnings)
	{}

	public record BuildSummary(int size, List<String> warnings)
	{}

	public static DumpSummary dump(Path inputSef, Path outputDirectory) throws IOException
	{
		return dump(inputSef, outputDirectory, SfxNames.loadBundled());
	}

	public static DumpSummary dump(Path inputSef, Path outputDirectory,
		SoundBankCatalog catalog) throws IOException
	{
		return dump(inputSef, outputDirectory, SfxNames.loadBundled(), catalog);
	}

	public static DumpSummary dump(Path inputSef, Path outputDirectory, SfxNames names) throws IOException
	{
		return dump(inputSef, outputDirectory, names, catalogFor(outputDirectory));
	}

	public static DumpSummary dump(Path inputSef, Path outputDirectory, SfxNames names,
		SoundBankCatalog catalog) throws IOException
	{
		byte[] bytes = Files.readAllBytes(inputSef);
		SfxBinary.DecodeResult decoded = SfxBinary.decode(bytes, names);
		SfxArchive archive = decoded.archive();
		List<String> validationWarnings = SfxValidator.validate(archive, names);
		SfxXml.write(archive, outputDirectory, catalog);

		List<String> warnings = new ArrayList<>(decoded.warnings());
		warnings.addAll(validationWarnings);
		int effectFiles = (int) archive.sounds.values().stream()
			.filter(sound -> !sound.isEmpty() && !sound.canInlineOneShot())
			.count();
		return new DumpSummary(archive.sounds.size(), effectFiles, archive.envelopes.size(), List.copyOf(warnings));
	}

	public static BuildSummary build(Path archiveXml, Path outputSef) throws IOException
	{
		return build(archiveXml, outputSef, SfxNames.loadBundled());
	}

	public static BuildSummary build(Path archiveXml, Path outputSef,
		SoundBankCatalog catalog) throws IOException
	{
		return build(archiveXml, outputSef, SfxNames.loadBundled(), catalog);
	}

	public static BuildSummary build(Path archiveXml, Path outputSef, SfxNames names) throws IOException
	{
		return build(archiveXml, outputSef, names,
			catalogFor(archiveXml.toAbsolutePath().normalize().getParent()));
	}

	public static BuildSummary build(Path archiveXml, Path outputSef, SfxNames names,
		SoundBankCatalog catalog) throws IOException
	{
		SfxArchive archive = SfxXml.read(archiveXml, catalog);
		List<String> warnings = SfxValidator.validate(archive, names);
		byte[] bytes = SfxBinary.encode(archive);
		writeAtomically(outputSef, bytes);
		return new BuildSummary(bytes.length, warnings);
	}

	public static List<String> lint(Path archiveXml) throws IOException
	{
		return lint(archiveXml, SfxNames.loadBundled());
	}

	public static List<String> lint(Path archiveXml, SfxNames names)
	{
		return SfxValidator.validate(SfxXml.read(archiveXml,
			catalogFor(archiveXml.toAbsolutePath().normalize().getParent())), names);
	}

	private static SoundBankCatalog catalogFor(Path audioDirectory)
	{
		Path directory = audioDirectory.toAbsolutePath().normalize();
		return new SoundBankCatalog(
			directory.resolve("bank").toFile(), directory.resolve("Banks.xml").toFile());
	}

	private static void writeAtomically(Path output, byte[] bytes) throws IOException
	{
		Path absolute = output.toAbsolutePath().normalize();
		Path parent = absolute.getParent();
		if (parent == null)
			throw new IOException("Output path has no parent: " + output);
		Files.createDirectories(parent);
		Path temporary = Files.createTempFile(parent, absolute.getFileName().toString(), ".tmp");
		try {
			Files.write(temporary, bytes);
			try {
				Files.move(temporary, absolute,
					StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			}
			catch (AtomicMoveNotSupportedException e) {
				Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
			}
		}
		finally {
			Files.deleteIfExists(temporary);
		}
	}

	public static void main(String[] args) throws Exception
	{
		if (args.length < 1) {
			printUsage();
			return;
		}

		try {
			switch (args[0]) {
				case "dump":
					runDump(args);
					break;
				case "build":
					runBuild(args);
					break;
				case "lint":
					runLint(args);
					break;
				default:
					printUsage();
					throw new IllegalArgumentException("Unknown SFX command: " + args[0]);
			}
		}
		catch (SfxFormatException e) {
			Logger.logError("SFX error: " + e.getMessage());
		}
	}

	private static void runDump(String[] args) throws IOException
	{
		if (args.length != 3 && args.length != 4)
			throw new IllegalArgumentException("dump requires: input.sef audio-directory [names.txt]");
		SfxNames names = args.length == 4 ? SfxNames.load(Path.of(args[3])) : SfxNames.loadBundled();
		DumpSummary summary = dump(Path.of(args[1]), Path.of(args[2]), names);
		Logger.logf("Dumped %d sounds (%d effect files, %d envelopes).",
			summary.sounds(), summary.effectFiles(), summary.envelopes());
		printWarnings(summary.warnings());
	}

	private static void runBuild(String[] args) throws IOException
	{
		if (args.length != 3 && args.length != 4)
			throw new IllegalArgumentException("build requires: SoundEffects.xml output.sef [names.txt]");
		SfxNames names = args.length == 4 ? SfxNames.load(Path.of(args[3])) : SfxNames.loadBundled();
		BuildSummary summary = build(Path.of(args[1]), Path.of(args[2]), names);
		Logger.logf("Built SEF: 0x%X bytes.", summary.size());
		printWarnings(summary.warnings());
	}

	private static void runLint(String[] args) throws IOException
	{
		if (args.length != 2 && args.length != 3)
			throw new IllegalArgumentException("lint requires: SoundEffects.xml [names.txt]");
		SfxNames names = args.length == 3 ? SfxNames.load(Path.of(args[2])) : SfxNames.loadBundled();
		List<String> warnings = lint(Path.of(args[1]), names);
		Logger.log("SFX assets are valid.");
		printWarnings(warnings);
	}

	private static void printWarnings(List<String> warnings)
	{
		for (String warning : warnings)
			Logger.logWarning(warning);
	}

	private static void printUsage()
	{
		Logger.log("Usage:");
		Logger.log("  SfxModder dump  input.sef audio-directory [names.txt]");
		Logger.log("  SfxModder build SoundEffects.xml output.sef [names.txt]");
		Logger.log("  SfxModder lint  SoundEffects.xml [names.txt]");
	}
}

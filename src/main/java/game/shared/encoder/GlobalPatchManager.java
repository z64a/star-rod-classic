package game.shared.encoder;

import static app.Directories.MOD_PATCH;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FilenameUtils;

import app.Directories;
import app.Resource;
import app.Resource.ResourceType;
import app.StarRodException;
import app.input.FileSource;
import app.input.IOUtils;
import app.input.StreamSource;
import patcher.Patcher;
import util.CaseInsensitiveMap;
import util.Logger;
import util.Priority;

public class GlobalPatchManager
{
	private final Patcher patcher;
	private final List<GlobalEncoder> encoders;
	private boolean digested = false;

	public GlobalPatchManager(Patcher patcher)
	{
		this.patcher = patcher;
		encoders = new ArrayList<>();
	}

	public void readInternalPatch(String patchName) throws IOException
	{
		if (digested)
			throw new StarRodException("Cannot read global patch file after digest has been invoked!");

		readInternalPatch(patchName, new CaseInsensitiveMap<>());
	}

	// supply preprocessor rules via additional args in the form of "key=value" or "key" (implicity ="true")
	public void readInternalPatch(String ... args) throws IOException
	{
		if (digested)
			throw new StarRodException("Cannot read global patch file after digest has been invoked!");

		Pattern kvPattern = Pattern.compile("(\\S+)=([\\S\\s]+)");
		Matcher kvMatcher = kvPattern.matcher("");

		CaseInsensitiveMap<String> rules = new CaseInsensitiveMap<>();
		for (int i = 1; i < args.length; i++) {
			kvMatcher.reset(args[i]);
			if (kvMatcher.matches())
				rules.put(kvMatcher.group(1), kvMatcher.group(2));
			else if (args[i].matches("[\\w:]+"))
				rules.put(args[i].toUpperCase(), "true");
			else if (args[i].isEmpty())
				; // empty arg -> no rule
			else
				throw new StarRodException("Patch for " + args[0] + " has malformed argument: " + args[i]);
		}

		readInternalPatch(args[0], rules);
	}

	public void readInternalPatch(String patchName, CaseInsensitiveMap<String> rules) throws IOException
	{
		if (digested)
			throw new StarRodException("Cannot read global patch file after digest has been invoked!");

		File modFile = new File(Directories.MOD_SYSTEM + patchName);
		File dbFile = new File(Directories.DATABASE_SYSTEM + patchName);

		InputStream is;
		if (modFile.exists())
			is = new FileInputStream(modFile);
		else if (dbFile.exists())
			is = new FileInputStream(dbFile);
		else
			is = Resource.getStream(ResourceType.InternalPatch, patchName);

		if (is == null) {
			Logger.log("Unable to find resource " + patchName, Priority.ERROR);
			throw new RuntimeException("Unable to find resource " + patchName);
		}

		GlobalEncoder e = new GlobalEncoder(patcher, new StreamSource(FilenameUtils.getBaseName(patchName)));
		encoders.add(e);
		e.readPatchStream(is, patchName, rules);
	}

	public void encodeAndWrite(Patcher patcher) throws IOException
	{
		if (digested)
			throw new StarRodException("Cannot read global patch files after digest has been invoked!");
		digested = true;

		// read user patches

		Collection<File> patchFiles = IOUtils.getFilesWithExtension(MOD_PATCH, "patch", true);
		for (File f : patchFiles) {
			GlobalEncoder e = new GlobalEncoder(patcher, new FileSource(f));
			encoders.add(e);
			e.readPatchFile(f);
		}

		patcher.recordTime("Global Patches Read");

		// compile the patches

		for (GlobalEncoder encoder : encoders)
			encoder.digest();

		for (GlobalEncoder encoder : encoders)
			encoder.buildGlobals();

		patcher.recordTime("Global Patches Compiled");

		// direct patches to ROM

		for (GlobalEncoder encoder : encoders)
			encoder.writeROMPatches(patcher.getRomPatcher());

		patcher.recordTime("Global Patches Written");
	}

	// ADDED structs loaded at boot
	public void addNewStructs() throws IOException
	{
		if (!digested)
			throw new StarRodException("Cannot write global structs until they have been digested!");

		for (GlobalEncoder encoder : encoders)
			encoder.addNewStructs(patcher.getRomPatcher());
	}
}

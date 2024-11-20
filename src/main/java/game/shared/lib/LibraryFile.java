package game.shared.lib;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import app.input.IOUtils;
import app.input.InputFileException;
import app.input.InvalidInputException;
import game.ROM.LibScope;
import game.ROM.Version;
import util.Logger;

public class LibraryFile implements Iterable<LibEntry>
{
	public final File source;
	public final Version version;
	public final LibScope scope;
	public final String versionString;

	private List<LibEntry> entries = new LinkedList<>();
	public List<LibEntry> signatures = new LinkedList<>();

	public LibraryFile(Version targetVersion, File source) throws IOException
	{
		this.source = source;
		List<String> lines = IOUtils.readFormattedTextFile(source, false);
		Iterator<String> iter = lines.iterator();

		String versionString = "0";
		Version version = null;
		LibScope scope = null;

		boolean readingHeader = true;
		while (iter.hasNext()) {
			String line = iter.next();
			if (!line.startsWith("{")) {
				readingHeader = false;
				break;
			}

			iter.remove();
			String[] options = line.split("\\s+");
			for (String opt : options) {
				String contents = LibEntry.matchOption(opt);
				if (contents == null)
					throw new InputFileException(source, "Invalid header line: %n%s", line);

				// if(opt.matches("[^=\\s]+=[^=\\s]+"))
				if (contents.startsWith("version=")) {
					versionString = contents.substring(contents.indexOf('=') + 1);
					if (!versionString.matches("[0-9]+(?:\\.[0-9]+)+"))
						throw new InputFileException(source, "Invalid version string: %n%s", versionString);
				}
				else if (contents.startsWith("scope=")) {
					String scopeString = contents.substring(contents.indexOf('=') + 1);
					if (!scopeString.contains(":"))
						scopeString = "us:" + scopeString;

					String[] scopeParts = scopeString.split(":");

					for (Version v : Version.values()) {
						if (scopeParts[0].equalsIgnoreCase(v.name()))
							version = v;
					}

					for (LibScope s : LibScope.values()) {
						if (scopeParts[1].equalsIgnoreCase(s.name()))
							scope = s;
					}
				}
			}
		}

		if (scope == null)
			throw new InputFileException(source, "Missing or invalid library scope!");

		this.versionString = versionString;
		this.version = version;
		this.scope = scope;

		if (version != targetVersion)
			return;

		if (readingHeader) {
			Logger.logfWarning("Library %s is empty!", source.getName());
			return;
		}

		iter = lines.iterator();
		while (iter.hasNext()) {
			String line = iter.next();

			while (line.endsWith("...")) {
				if (!iter.hasNext())
					throw new InputFileException(source, "Can't have line continuation on the last line.");
				line = line.substring(0, line.length() - 3) + iter.next();
			}

			try {
				entries.add(LibEntry.parse(this, line));
			}
			catch (InvalidInputException e) {
				line = line.replaceAll("(.{80}\\S+)", "$1 ...\n");
				InputFileException out = new InputFileException(source, "%s %n%s", e.getMessage(), line);
				out.setStackTrace(e.getStackTrace());
				throw out;
			}
		}
	}

	public int count()
	{
		return entries.size();
	}

	@Override
	public Iterator<LibEntry> iterator()
	{
		return entries.iterator();
	}
}

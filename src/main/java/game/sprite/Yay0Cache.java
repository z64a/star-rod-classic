package game.sprite;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

import org.apache.commons.io.FileUtils;

import app.Directories;
import app.StarRodException;
import app.input.IOUtils;
import game.yay0.Yay0Helper;

public class Yay0Cache
{
	public static final String FN_CACHE = "checksums.txt";

	public static class CacheResult
	{
		public final boolean fromCache;
		public final byte[] data;

		private CacheResult(boolean fromCache, byte[] data)
		{
			this.fromCache = fromCache;
			this.data = data;
		}
	}

	private final TreeMap<String, Long> cachedChecksums;
	private final Directories cacheDir;
	private boolean modified = false;

	public Yay0Cache(Directories cacheDir) throws IOException
	{
		cachedChecksums = new TreeMap<>();
		this.cacheDir = cacheDir;

		FileUtils.forceMkdir(cacheDir.toFile());
		File cacheFile = new File(cacheDir + FN_CACHE);

		if (cacheFile.exists())
			loadCache(cacheFile);
	}

	public CacheResult get(File newFile) throws IOException
	{
		return get(newFile, FileUtils.readFileToByteArray(newFile));
	}

	public CacheResult get(File newFile, byte[] newBytes) throws IOException
	{
		String filename = newFile.getName();
		long newHash = hash(newBytes);

		File cachedFile = new File(cacheDir + filename);
		if (!cachedFile.exists())
			return addToCache(cachedFile, newBytes, newHash);

		Long oldHash = cachedChecksums.get(filename);
		if (oldHash == null)
			return addToCache(cachedFile, newBytes, newHash);

		if (oldHash != newHash)
			return addToCache(cachedFile, newBytes, newHash);

		byte[] cacheBytes = FileUtils.readFileToByteArray(cachedFile);
		return new CacheResult(true, cacheBytes);
	}

	private CacheResult addToCache(File cacheFile, byte[] data, long hash) throws IOException
	{
		byte[] encoded = Yay0Helper.encode(data);
		FileUtils.writeByteArrayToFile(cacheFile, encoded);
		cachedChecksums.put(cacheFile.getName(), hash);
		modified = true;
		return new CacheResult(false, encoded);
	}

	private long hash(byte[] data)
	{
		Checksum checksum = new CRC32();
		checksum.update(data, 0, data.length);
		return checksum.getValue();
	}

	public void save() throws IOException
	{
		FileUtils.forceMkdir(cacheDir.toFile());
		File outFile = new File(cacheDir + FN_CACHE);
		if (outFile.exists() && !modified)
			return;
		writeCache(outFile);
	}

	private void loadCache(File f) throws IOException
	{
		List<String> lines = IOUtils.readFormattedTextFile(f, false);

		if (lines.size() % 2 != 0)
			throw new StarRodException(String.format("Invalid line count for %s: %d", f.getName(), lines.size()));

		for (int i = 0; i < lines.size(); i += 2) {
			try {
				long crc = Long.parseLong(lines.get(i + 1), 16);
				cachedChecksums.put(lines.get(i), crc);
			}
			catch (NumberFormatException e) {
				throw new StarRodException(String.format("Invalid line in %s: %s", f.getName(), lines.get(i + 1)));
			}
		}
	}

	private void writeCache(File f) throws IOException
	{
		PrintWriter pw = IOUtils.getBufferedPrintWriter(f);

		for (Entry<String, Long> e : cachedChecksums.entrySet()) {
			pw.println(e.getKey());
			pw.printf("%08X%n", e.getValue());
		}

		pw.close();
	}
}

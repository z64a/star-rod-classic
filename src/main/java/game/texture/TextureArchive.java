package game.texture;

import static app.Directories.DUMP_MAP_RAW;
import static app.Directories.MOD_IMG_TEX;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import app.Environment;
import app.input.IOUtils;
import app.input.InputFileException;

public class TextureArchive
{
	public static void main(String[] args) throws IOException
	{
		Environment.initialize();
		//	testBinary();
		testTxa();
		Environment.exit();
	}

	private static void testBinary() throws IOException
	{
		File[] assets = DUMP_MAP_RAW.toFile().listFiles();
		for (File f : assets)
			if (f.getName().endsWith("_tex")) {
				TextureArchive ta = new TextureArchive(f);
				String temp = DUMP_MAP_RAW + "/test/";
				ta.writeBinary(temp);

				ByteBuffer buf1 = IOUtils.getDirectBuffer(f);
				ByteBuffer buf2 = IOUtils.getDirectBuffer(new File(temp + ta.name));

				System.out.print("Testing " + f.getName() + "...");
				buf1.rewind();
				buf2.rewind();
				assert (buf1.equals(buf2));
				System.out.println(" passed.");
			}
		System.out.println("Done.");
	}

	private static void testTxa() throws IOException
	{
		Collection<File> archives = IOUtils.getFilesWithExtension(MOD_IMG_TEX, TextureArchive.EXT, true);
		for (File f : archives) {
			TextureArchive ta = TextureArchive.loadText(f);
			String temp = DUMP_MAP_RAW + "/test/";
			ta.writeBinary(temp);

			ByteBuffer buf1 = IOUtils.getDirectBuffer(new File(DUMP_MAP_RAW + ta.name));
			ByteBuffer buf2 = IOUtils.getDirectBuffer(new File(temp + ta.name));

			System.out.print("Testing " + f.getName() + "...");
			buf1.rewind();
			buf2.rewind();
			assert (buf1.equals(buf2));
			System.out.println(" passed.");
		}
		System.out.println("Done.");
	}

	public static final String EXT = "txa";
	public final String name;

	public final List<Texture> textureList;

	private TextureArchive(String name)
	{
		this.name = name;
		textureList = new LinkedList<>();
	}

	/**
	 * Loads from binary *_tex file.
	 */
	public TextureArchive(File f) throws IOException
	{
		name = f.getName();
		textureList = new LinkedList<>();

		ByteBuffer fileBuffer = IOUtils.getDirectBuffer(f);
		while (fileBuffer.hasRemaining()) {
			Texture tx = new Texture(fileBuffer);
			textureList.add(tx);
		}
	}

	public void dumpToDirectory(File dir) throws IOException
	{
		String subdir = dir.getPath() + "/" + name + "/";
		FileUtils.forceMkdir(new File(subdir));

		for (Texture tx : textureList) {
			tx.main.savePNG(subdir + tx.name);

			if (tx.hasAux)
				tx.aux.savePNG(subdir + tx.name + "_AUX");

			if (tx.hasMipmaps) {
				for (int i = 0; i < tx.mipmapList.size(); i++) {
					Tile mipmap = tx.mipmapList.get(i);
					mipmap.savePNG(subdir + tx.name + "_MIPMAP_" + (i + 1));
				}
			}
		}

		writeText(dir + "/" + name + "." + EXT);
		writeMaterials(subdir + name + ".mtl");
	}

	public void writeBinary(String dir) throws IOException
	{
		//	File f = Paths.get(dir, name).toFile();

		File f = new File(
			dir,
			Environment.project.isDecomp
				? name + ".bin"
				: name
		);

		int fileSize = 0;
		for (Texture tx : textureList)
			fileSize += tx.getFileSize();
		ByteBuffer bb = ByteBuffer.allocateDirect(fileSize);

		for (Texture tx : textureList)
			tx.write(bb);

		byte[] bytes = new byte[bb.limit()];
		bb.rewind();
		bb.get(bytes);
		FileUtils.writeByteArrayToFile(f, bytes);
	}

	private void writeText(String fileName) throws IOException
	{
		writePlaintext(new File(fileName));
	}

	private void writePlaintext(File f) throws IOException
	{
		PrintWriter pw = IOUtils.getBufferedPrintWriter(f);

		for (Texture tx : textureList)
			tx.print(pw);

		pw.close();
	}

	public static TextureArchive loadText(File texFile) throws IOException
	{
		String texName = FilenameUtils.getBaseName(texFile.getName());
		TextureArchive ta = new TextureArchive(texName);

		File parentDirectory = texFile.getParentFile();
		String subdir = parentDirectory.getAbsolutePath() + "/" + texName + "/";
		List<String> lines = IOUtils.readFormattedTextFile(texFile, false);

		Iterator<String> iter = lines.iterator();
		while (iter.hasNext()) {
			String line = iter.next();
			if (!line.startsWith("tex:"))
				throw new InputFileException(texFile, "Invalid texture declaration: " + line);

			String[] tokens = line.split(":\\s*");
			if (tokens.length != 2)
				throw new InputFileException(texFile, "Invalid texture name: " + line);

			String name = tokens[1];

			line = iter.next();
			if (!line.equals("{"))
				throw new InputFileException(texFile, "Texture " + name + " is missing open curly bracket.");

			int curlyBalance = 1;
			List<String> textureLines = new LinkedList<>();

			while (true) {
				if (!iter.hasNext())
					throw new InputFileException(texFile, "Texture " + name + " is missing closed curly bracket.");

				line = iter.next();
				if (line.equals("{"))
					curlyBalance++;
				else if (line.equals("}")) {
					curlyBalance--;
					if (curlyBalance == 0)
						break;
				}
				if (line.startsWith("tex:"))
					throw new InputFileException(texFile, "Texture " + name + " is missing closed curly bracket.");

				textureLines.add(line);
			}

			Texture tx = Texture.parseTexture(texFile, subdir, name, textureLines);
			ta.textureList.add(tx);
		}

		return ta;
	}

	public void writeMaterials(String fileName) throws IOException
	{
		writeMaterials(new File(fileName));
	}

	public void writeMaterials(File f) throws IOException
	{
		PrintWriter pw = IOUtils.getBufferedPrintWriter(f);

		for (Texture tx : textureList) {
			pw.println("newmtl m_" + tx.name);
			pw.println("Ka 1 1 1");
			pw.println("Kd 1 1 1");
			pw.println("Ks 1 1 1");
			pw.println("Ns 0");
			pw.println("map_Ka " + tx.name + ".png");
			pw.println("map_Kd " + tx.name + ".png");
			pw.println();
		}
		pw.close();
	}
}

package game.yay0;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;

import app.Environment;
import app.ParallelFileTask;
import app.ParallelFileTask.WorkBytes;
import util.Logger;
import util.Priority;
import util.SimpleProgressBarDialog;

public class Yay0AlgorithmTester
{
	public static void main(String args[]) throws IOException
	{
		Environment.initialize();
		new Yay0AlgorithmTester();
		Environment.exit();
	}

	private static final String ENCODED = "./yay0/encoded/";
	private static final String DECODED = "./yay0/decoded/";
	private static final String REFERENCE = "./yay0/reference/";

	private Yay0AlgorithmTester() throws IOException
	{
		//Logger.setVerbosity(Verbosity.DETAIL);

		//	dump2();
		// compressFiles();
		compressFilesStreams();
		//checkCompressedFiles();
		//verifyCompressedFiles();

		//compressTest("01E9E692.bin");
		//verifyTest("01E9E692.bin");

		/*
		byte[] reference = FileUtils.readFileToByteArray(new File(REFERENCE + "01E9E692.bin"));
		byte[] decodedFromReference = Yay0Helper.decode(reference);

		System.out.println("");

		byte[] encoded = FileUtils.readFileToByteArray(new File(ENCODED + "01E9E692.bin"));
		byte[] decodedFromEncoded = Yay0Helper.decode(encoded);
		*/
	}

	private void compressFiles() throws IOException
	{
		FileUtils.deleteDirectory(new File(ENCODED));
		File[] decompressedFiles = new File(DECODED).listFiles();

		SimpleProgressBarDialog progressBar = new SimpleProgressBarDialog("Yay0 Compressor", "Compressing files...");
		float count = 0.01f;

		long t0 = System.nanoTime();

		for (File f : decompressedFiles) {
			System.out.println("Compressing " + f.getName());
			byte[] source = FileUtils.readFileToByteArray(f);
			byte[] encoded = Yay0Helper.encode(source);
			FileUtils.writeByteArrayToFile(new File(ENCODED + f.getName()), encoded);

			progressBar.setProgress((int) (100 * (count / decompressedFiles.length)));
			count++;
		}

		long t1 = System.nanoTime();

		System.out.printf("Total time: %f%n", (t1 - t0) * 1e-9);

		progressBar.destroy();
	}

	private void compressFilesStreams() throws IOException
	{
		FileUtils.deleteDirectory(new File(ENCODED));
		File[] decompressedFiles = new File(DECODED).listFiles();

		SimpleProgressBarDialog progressBar = new SimpleProgressBarDialog("Yay0 Compressor", "Compressing files...");
		float count = 0.01f;

		List<WorkBytes<String>> records = new ArrayList<>(decompressedFiles.length);
		for (int i = 0; i < decompressedFiles.length; i++) {
			File in = decompressedFiles[i];
			File out = new File(ENCODED + in.getName());
			records.add(new WorkBytes<>(in.getName(), in, out));
		}

		long t0 = System.nanoTime();

		ParallelFileTask.applyBytes(records, (rec) -> {
			// 	System.out.println("Compressing " + rec.taskData);
			rec.outBytes = Yay0Helper.encode(rec.inBytes);
		});

		long t1 = System.nanoTime();

		System.out.printf("Total time: %f%n", (t1 - t0) * 1e-9);

		progressBar.destroy();
	}

	private void compressTest(String name) throws IOException
	{
		byte[] source = FileUtils.readFileToByteArray(new File(DECODED + name));
		byte[] encoded = Yay0Helper.encode(source);
		FileUtils.writeByteArrayToFile(new File(ENCODED + name), encoded);
	}

	private void printBytes(byte[] buf, boolean newLines)
	{
		for (int i = 0; i < buf.length;) {
			System.out.print(String.format("%02X", buf[i]));
			i++;

			if (i % 4 == 0)
				System.out.print(" ");

			if (i % 16 == 0 && newLines)
				System.out.println("");
		}

		if (buf.length % 16 != 0 && newLines)
			System.out.println("");

		System.out.println("");
	}

	private void verifyTest(String name) throws IOException
	{
		String title = "Verify " + name + ": ";

		Logger.log("From REFERENCE:", Priority.DETAIL);
		byte[] reference = FileUtils.readFileToByteArray(new File(REFERENCE + name));
		byte[] decodedFromReference = Yay0Helper.decode(reference);

		Logger.log("From ENCODED:", Priority.DETAIL);
		byte[] encoded = FileUtils.readFileToByteArray(new File(ENCODED + name));
		byte[] decodedFromEncoded = Yay0Helper.decode(encoded);

		//printBytes(encoded, false);
		//printBytes(reference, false);

		if (decodedFromEncoded.length != decodedFromReference.length) {
			System.out.println(title + "Lengths do not match!");
		}
		else {
			System.out.println(title + "Length matches.");
			boolean matches = true;
			for (int i = 0; i < decodedFromEncoded.length; i++) {
				//	System.out.println(String.format("%02X vs %02X", decodedFromEncoded[i], decodedFromReference[i]));
				if (decodedFromEncoded[i] != decodedFromReference[i]) {
					System.out.println(title + String.format("Decoded bytes not equal to reference at offset %X", i));
					matches = false;
					break;
				}
			}
			if (matches)
				System.out.println("VERIFY: Decompressed files match.");
		}
	}

	private void dump2() throws IOException
	{
		FileUtils.deleteDirectory(new File(ENCODED));
		FileUtils.deleteDirectory(new File(DECODED));
		FileUtils.deleteDirectory(new File(REFERENCE));

		RandomAccessFile raf = Environment.getBaseRomReader();

		raf.seek(0x1E40020);
		for (int i = 0; i < 1033; i++) // 1033 = 0x409
		{
			raf.seek(0x1E40020 + i * 0x1C);
			String name = readString(raf, 0x10);
			int offset = raf.readInt() + 0x1E40020;
			int compressedLength = raf.readInt();
			int decompressedLength = raf.readInt();

			raf.seek(offset);
			if (raf.readInt() == 0x59617930) // "Yay0"
			{
				int yay0length = raf.readInt();
				assert (yay0length == decompressedLength);

				byte[] dumpedBytes = new byte[compressedLength];
				raf.seek(offset);
				raf.read(dumpedBytes);

				File referenceFile = new File(REFERENCE + String.format("%08X.bin", offset));
				FileUtils.writeByteArrayToFile(referenceFile, dumpedBytes);

				byte[] decodedBytes = Yay0Helper.decode(dumpedBytes);
				File decodedFile = new File(DECODED + String.format("%08X.bin", offset));
				FileUtils.writeByteArrayToFile(decodedFile, decodedBytes);

				byte[] encodedBytes = Yay0Helper.encode(decodedBytes);
				File encodedFile = new File(ENCODED + String.format("%08X.bin", offset));
				FileUtils.writeByteArrayToFile(encodedFile, encodedBytes);
			}
		}

		// while !name.equals("end_data")

		raf.close();
	}

	/*
	private void dumpFiles() throws IOException
	{
		RandomAccessFile raf = new RandomAccessFile(Database.PRISTINE_ROM, "r");

		raf.seek(0x1E40020);
		for(int i = 0; i < 1033; i++) // 1033 = 0x409
		{
			raf.seek(0x1E40020 + i * 0x1C);
			String name = readString(raf, 0x10);
			int offset = raf.readInt() + 0x1E40020;
			int compressedLength = raf.readInt();
			int decompressedLength = raf.readInt();

			raf.seek(offset);
			if(raf.readInt() == 0x59617930) // "Yay0"
			{
				int yay0length = raf.readInt();
				assert(yay0length == decompressedLength);

				byte[] dumpedBytes = new byte[compressedLength];
				raf.seek(offset);
				raf.read(dumpedBytes);

				File refCompressedFile = new File(REF_COMPRESSED + String.format("%08X.bin", offset));
				FileUtils.writeByteArrayToFile(refCompressedFile, dumpedBytes);

				byte[] decodedBytes = Yay0BufferHelper.decode(dumpedBytes);

				File verDecompressedFile = new File(VER_DECOMPRESSED + String.format("%08X.bin", offset));
				FileUtils.writeByteArrayToFile(verDecompressedFile, decodedBytes);

				byte[] encodedBytes = Yay0BufferHelper.encode(decodedBytes);

				File verCompressedFile = new File(VER_COMPRESSED + String.format("%08X.bin", offset));
				FileUtils.writeByteArrayToFile(verCompressedFile, encodedBytes);
			}
		}

		// while !name.equals("end_data")

		raf.close();
	}
	 */

	/*
	public void checkDecompressedFiles() throws IOException
	{
		int errors = 0;
		int total = 0;

		for(File f : new File(VER_DECOMPRESSED).listFiles())
		{
			File ref = new File(REF_DECOMPRESSED + f.getName());
			assert(ref.exists());

			if(!equals(f, ref))
			{
				System.out.println("PROBLEM: " + f.getName());
				errors++;
			}

			total++;
		}

		System.out.println(total + " files decompressed with " + errors + " errors.");
	}
	 */

	public void checkCompressedFiles() throws IOException
	{
		int total = 0;
		int larger = 0;
		int smaller = 0;

		int totalDiff = 0;
		int totalReferenceSize = 0;

		for (File f : new File(ENCODED).listFiles()) {
			File ref = new File(REFERENCE + f.getName());
			assert (ref.exists());

			if (f.length() != ref.length()) {
				float diff = f.length() - ref.length();

				if (diff > 0) {
					System.out.println(String.format("%s is %d bytes LARGER than reference! (%.3f",
						f.getName(), (int) diff, 100 * diff / ref.length()) + "%) " + ref.length());
					larger++;
				}

				if (diff < 0) {
					System.out.println(String.format("%s is %d bytes SMALLER than reference! (%.3f",
						f.getName(), (int) -diff, 100 * -diff / ref.length()) + "%) " + ref.length());
					smaller++;
				}

				totalDiff += diff;
			}

			totalReferenceSize += ref.length();
			total++;
		}

		System.out.println(total + " files compressed: " + smaller + " are smaller and " + larger + " are larger.");
		System.out.println(String.format("Total difference: %d bytes (%.3f",
			totalDiff, 100 * (float) Math.abs(totalDiff) / totalReferenceSize) + "%)");
	}

	public void verifyCompressedFiles() throws IOException
	{
		int errors = 0;
		int total = 0;

		for (File f : new File(ENCODED).listFiles()) {
			System.out.println(f.getName());
			File ref = new File(REFERENCE + f.getName());
			assert (ref.exists());

			byte[] encoded = FileUtils.readFileToByteArray(f);
			byte[] decodedFromEncoded = Yay0Helper.decode(encoded);

			byte[] reference = FileUtils.readFileToByteArray(ref);
			byte[] decodedFromRefernece = Yay0Helper.decode(reference);

			if (decodedFromEncoded.length != decodedFromRefernece.length) {
				System.out.println(f.getName() + " does not match reference length!");
				errors++;
			}
			else {
				for (int i = 0; i < decodedFromEncoded.length; i++) {
					if (decodedFromEncoded[i] != decodedFromRefernece[i]) {
						System.out.println(String.format("%s not equal to reference at offset %X", f.getName(), i));
						errors++;
					}
				}
			}

			total++;
		}

		System.out.println(total + " files decoded with " + (total - errors) + " correct and " + errors + " errors.");
	}

	public static String readString(RandomAccessFile raf, int maxlength) throws IOException
	{
		StringBuilder sb = new StringBuilder();
		int read = 0;

		for (; read < maxlength; read++) {
			byte b = raf.readByte();

			if (b == (byte) 0)
				break;
			else
				sb.append((char) b);
		}
		raf.skipBytes(maxlength - read - 1); // DOC: if arg is negative, no bytes are skipped

		return sb.toString();
	}

	public static boolean equals(File f1, File f2) throws IOException
	{
		if (f1.length() != f2.length())
			return false;

		byte[] b1 = FileUtils.readFileToByteArray(f1);
		byte[] b2 = FileUtils.readFileToByteArray(f2);

		for (int i = 0; i < b1.length; i++) {
			if (b1[i] != b2[i])
				return false;
		}

		return true;
	}
}

package app;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.Consumer;

import org.apache.commons.io.FileUtils;

import app.input.IOUtils;

public class ParallelFileTask
{
	public static class WorkBytes<T>
	{
		public WorkBytes(T taskData, File in, File out)
		{
			this.taskData = taskData;
			this.inFile = in;
			this.outFile = out;
		}

		public final T taskData;
		public int index;

		public final File inFile;
		public byte[] inBytes;

		public final File outFile;
		public byte[] outBytes;
	}

	public static <T> void applyBytes(List<WorkBytes<T>> recs, Consumer<WorkBytes<T>> task) throws IOException
	{
		int i = 0;
		for (WorkBytes<T> rec : recs) {
			rec.inBytes = FileUtils.readFileToByteArray(rec.inFile);
			rec.index = i++;
		}

		recs.parallelStream().forEach(task);

		for (WorkBytes<T> rec : recs)
			FileUtils.writeByteArrayToFile(rec.outFile, rec.outBytes);
	}

	public static class WorkBuffer<T>
	{
		public WorkBuffer(T taskData, File in, File out)
		{
			this.taskData = taskData;
			this.inFile = in;
			this.outFile = out;
		}

		public final T taskData;
		public int index;

		public final File inFile;
		public ByteBuffer inBuffer;

		public final File outFile;
		public ByteBuffer outBuffer;
	}

	public static <T> void applyBuffer(List<WorkBuffer<T>> recs, Consumer<WorkBuffer<T>> task) throws IOException
	{
		int i = 0;
		for (WorkBuffer<T> rec : recs) {
			rec.inBuffer = IOUtils.getDirectBuffer(rec.inFile);
			rec.index = i++;
		}

		recs.parallelStream().forEach(task);

		for (WorkBuffer<T> rec : recs)
			IOUtils.writeBufferToFile(rec.outBuffer, rec.outFile);
	}
}

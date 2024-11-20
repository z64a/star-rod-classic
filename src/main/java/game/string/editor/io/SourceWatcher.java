package game.string.editor.io;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardWatchEventKinds.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;

public class SourceWatcher
{
	private final WatchService watcher;
	private final Map<WatchKey, Path> keyMap;
	private final HashSet<File> eventSet;

	private boolean running = false;

	private final ConcurrentLinkedDeque<FileEvent> eventQueue;
	private boolean overflow = false;

	public static enum FileEventType
	{
		Created, Deleted, Modified
	}

	public static class FileEvent
	{
		public final File file;
		public final FileEventType type;

		private FileEvent(File file, FileEventType type)
		{
			this.file = file;
			this.type = type;
		}

		@Override
		public String toString()
		{
			return type.name().toUpperCase() + " " + file.toString();
		}
	}

	public boolean hadOverflow()
	{
		return overflow;
	}

	public void clear()
	{
		overflow = false;
		eventQueue.clear();
	}

	public boolean hasEvents()
	{
		return !eventQueue.isEmpty();
	}

	public FileEvent getNextEvent()
	{
		FileEvent evt = eventQueue.pollFirst();
		if (evt != null)
			eventSet.remove(evt.file);
		return evt;
	}

	public SourceWatcher() throws IOException
	{
		watcher = FileSystems.getDefault().newWatchService();
		keyMap = new HashMap<>();
		eventSet = new HashSet<>();

		eventQueue = new ConcurrentLinkedDeque<>();
	}

	public void registerAll(Path dir) throws IOException
	{
		Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
				throws IOException
			{
				register(dir);
				return FileVisitResult.CONTINUE;
			}
		});
	}

	private void register(Path dir) throws IOException
	{
		keyMap.put(dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY), dir);
	}

	public void run()
	{
		if (running) {
			System.err.println("Tried to relaunch a running Watcher");
			return;
		}

		running = true;

		Thread thread = new Thread() {
			@Override
			public void run()
			{
				processEvents();
			}
		};
		thread.start();
	}

	private void processEvents()
	{
		while (true) {
			WatchKey key;
			try {
				key = watcher.take();
			}
			catch (InterruptedException e) {
				System.err.print("File watcher interrupted!");
				return;
			}

			Path dir = keyMap.get(key);
			if (dir == null) {
				System.err.println("WatchKey not recognized! " + key);
				continue;
			}

			for (WatchEvent<?> evt : key.pollEvents()) {
				Kind<?> kind = evt.kind();

				if (kind == OVERFLOW) {
					overflow = true;
					continue;
				}

				@SuppressWarnings("unchecked")
				WatchEvent<Path> dirEvt = (WatchEvent<Path>) evt;
				Path name = dirEvt.context();
				Path child = dir.resolve(name);

				if (kind == ENTRY_CREATE) {
					tryEnqueue(new FileEvent(child.toFile(), FileEventType.Created));
					try {
						if (Files.isDirectory(child, NOFOLLOW_LINKS))
							registerAll(child);
					}
					catch (IOException e) {}
				}
				else if (kind == ENTRY_DELETE) {
					tryEnqueue(new FileEvent(child.toFile(), FileEventType.Deleted));
				}
				else if (kind == ENTRY_MODIFY) {
					tryEnqueue(new FileEvent(child.toFile(), FileEventType.Modified));
				}
			}

			boolean valid = key.reset();
			if (!valid) {
				keyMap.remove(key);
				if (keyMap.isEmpty())
					break;
			}
		}
	}

	private void tryEnqueue(FileEvent evt)
	{
		if (eventSet.contains(evt.file))
			return;

		eventQueue.add(evt);
		eventSet.add(evt.file);
	}
}

package util.xml;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Stack;

import app.Directories;
import app.Environment;
import app.input.IOUtils;
import app.input.InputFileException;
import game.map.MapKey;
import util.xml.XmlWrapper.XmlReader;

public class FastML
{
	/*
	 * With pruning
	 * (1) Parsed 16384 files in 56.960 s
	 * (2) Parsed 16384 files in 50.424 s
	 *
	 * (old) Parsed 16384 files in 57.429 s
	 * (new) Parsed 16384 files in 44.612 s
	 *
	 * 22% speedup...
	 *
	 * Doesnt seem to be worth refactoring this.
	 */
	public static void main(String[] args) throws IOException
	{
		Environment.initialize();

		HashMap<String, XmlKey> keyMap = new HashMap<>();
		for (XmlKey key : MapKey.values())
			keyMap.put(key.toString(), key);

		Collection<File> xmlFiles = IOUtils.getFilesWithExtension(Directories.DUMP_MAP_SRC, "xml", false);

		int count = 0;
		long t0 = System.nanoTime();

		/*
		for(int i = 0; i < 2; i++)
			for(File xml : xmlFiles)
			{
				new XmlReader(xml);
				count++;
			}
		*/

		for (int i = 0; i < 32; i++) {
			xmlFiles.parallelStream().forEach((xml) -> {
				new XmlReader(xml);
			});
		}

		long t1 = System.nanoTime();
		System.out.printf("(old) Parsed %d files in %5.3f s%n", count, (t1 - t0) * 1e-9);

		count = 0;
		t0 = System.nanoTime();

		/*
		for(int i = 0; i < 2; i++)
			for(File xml : xmlFiles)
			{
				parse(xml, keyMap);
				count++;
			}
		*/

		for (int i = 0; i < 32; i++) {
			xmlFiles.parallelStream().forEach((xml) -> {
				try {
					parse(xml, keyMap);
				}
				catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			});
		}

		t1 = System.nanoTime();
		System.out.printf("(new) Parsed %d files in %5.3f s%n", count, (t1 - t0) * 1e-9);

		Environment.exit();
	}

	private static class Tag
	{
		private final String name;
		public XmlKey key;
		public ArrayList<Tag> children;
		public ArrayList<Attribute> attrs;

		public Tag(String name)
		{
			this.name = name;
			children = new ArrayList<>();
			attrs = new ArrayList<>();
		}

		public void print()
		{
			print("");
		}

		private void print(String indent)
		{
			System.out.println(indent + "<" + name + ">");
			for (Tag t : children)
				t.print(indent + "\t");
		}

		private void prune(HashMap<String, XmlKey> keys)
		{
			for (int i = children.size() - 1; i >= 0; i--) {
				Tag child = children.get(i);
				child.key = keys.get(child.name);
				if (child.key == null)
					;//	children.remove(i);
				else
					child.prune(keys);
			}

			for (int i = attrs.size() - 1; i >= 0; i--) {
				Attribute attr = attrs.get(i);
				attr.key = keys.get(attr.name);
				if (attr.key == null)
					;//	attrs.remove(i);
			}
		}
	}

	private static class Attribute
	{
		public Attribute(String name)
		{
			this.name = name;
		}

		private final String name;
		public XmlKey key;
		public String value;
	}

	private static enum State
	{
		READING_DOC,
		READING_OPEN_TAG_NAME,
		READING_CLOSE_TAG_NAME,
		READING_TAG,
		READING_ATTR_NAME,
		READING_ATTR_VALUE,
		SKIPPING_PROLOGUE,
		SKIPPING_COMMENT
	}

	// <? IGNORED ?>
	// <!-- IGNORED -->
	// <tag attr="value" />

	public static Tag parse(File f, HashMap<String, XmlKey> keyMap) throws IOException
	{
		// FASTER!
		//	final Field field = String.class.getDeclaredField("value");
		//	field.setAccessible(true);
		//	final char[] chars = (char[]) field.get(doc);

		StringBuilder docBuilder = new StringBuilder();
		try (BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
			String line;
			while ((line = in.readLine()) != null)
				docBuilder.append(line);
		}

		char[] chars = docBuilder.toString().toCharArray();
		State state = State.READING_DOC;
		State prevState = State.READING_DOC;

		int lineNum = 1;

		Stack<Tag> tags = new Stack<>();
		StringBuilder sb = new StringBuilder(256);
		Tag currentTag = null;
		Tag lastTag = null;
		Attribute currentAttr = null;

		try {
			for (int i = 0; i < chars.length; i++) {
				char c = chars[i];

				if (c == '\r')
					continue;
				if (c == '\n') {
					lineNum++;
					continue;
				}

				switch (state) {
					case READING_DOC:
						if (c == ' ' || c == '\t')
							continue;
						if (c != '<')
							throw new InputFileException(f, "LINE %d: encountered %c while %s (expected <)", lineNum, c, state);
						if (chars[i + 1] == '?') // prologue
						{
							prevState = state;
							state = State.SKIPPING_PROLOGUE;
							i++;
							continue;
						}
						if (chars[i + 1] == '!') // comment
						{
							prevState = state;
							state = State.SKIPPING_COMMENT;
							i++;
							continue;
						}
						if (chars[i + 1] == '/') // closing
						{
							state = State.READING_CLOSE_TAG_NAME;
							sb.setLength(0);
							i++;
							continue;
						}
						state = State.READING_OPEN_TAG_NAME;
						sb.setLength(0);
						continue;

					case READING_CLOSE_TAG_NAME:
						if (c == ' ' || c == '\t')
							throw new InputFileException(f, "LINE %d: encountered whitespace in tag name", lineNum);
						if (c == '>') // end of tag
						{
							if (tags.isEmpty())
								throw new InputFileException(f, "LINE %d: tag closed without corresponding open", lineNum);

							currentTag = new Tag(sb.toString());
							lastTag = tags.pop();
							if (!lastTag.name.equals(currentTag.name))
								throw new InputFileException(f, "LINE %d: tag closed without corresponding open", lineNum);

							state = State.READING_DOC;
							currentTag = null;
							continue;
						}
						if (c < '0' || (c > '9' && c < 'A') || (c > 'Z' && c < 'a') || c > 'z')
							throw new InputFileException(f, "LINE %d: encountered invalid character %c while %s", lineNum, c, state);
						else
							sb.append(c);
						continue;

					case READING_OPEN_TAG_NAME:
						if (c == '>') // degenerate tag
						{
							currentTag = new Tag(sb.toString());
							if (!tags.isEmpty())
								tags.peek().children.add(currentTag);
							tags.push(currentTag);

							state = State.READING_DOC;
							currentTag = null;

							continue;
						}

						if (c == ' ' || c == '\t') // end of name
						{
							currentTag = new Tag(sb.toString());
							if (!tags.isEmpty())
								tags.peek().children.add(currentTag);
							tags.push(currentTag);

							state = State.READING_TAG;
							continue;
						}
						if (c < '0' || (c > '9' && c < 'A') || (c > 'Z' && c < 'a') || c > 'z')
							throw new InputFileException(f, "LINE %d: encountered invalid character %c while %s", lineNum, c, state);
						else
							sb.append(c);
						continue;

					case READING_TAG:
						if (c == '/') {
							if (chars[i + 1] == '>') // self close
							{
								lastTag = tags.pop(); // current tag

								state = State.READING_DOC;
								currentTag = null;
								i++;
								continue;
							}
							else
								throw new InputFileException(f, "LINE %d: encountered invalid character %c while %s", lineNum, c, state);
						}

						if (c == '>') // close
						{
							state = State.READING_DOC;
							currentTag = null;
							continue;
						}

						if (c == ' ' || c == '\t')
							continue;

						if (c < '0' || (c > '9' && c < 'A') || (c > 'Z' && c < 'a') || c > 'z')
							throw new InputFileException(f, "LINE %d: encountered invalid character %c while %s", lineNum, c, state);

						state = State.READING_ATTR_NAME;
						sb.setLength(0);
						continue;

					case READING_ATTR_NAME:
						if (c == '=' && chars[i + 1] == '\"') {
							currentAttr = new Attribute(sb.toString());
							currentTag.attrs.add(currentAttr);
							state = State.READING_ATTR_VALUE;
							sb.setLength(0);
							i++;
							continue;
						}
						if (c == ' ' || c == '\t')
							throw new InputFileException(f, "LINE %d: encountered invalid whitespace", lineNum);
						if (c < '0' || (c > '9' && c < 'A') || (c > 'Z' && c < 'a') || c > 'z')
							throw new InputFileException(f, "LINE %d: encountered invalid character %c while %s", lineNum, c, state);
						else
							sb.append(c);
						continue;

					case READING_ATTR_VALUE:
						if (c == '"') {
							currentAttr.value = sb.toString();
							currentAttr = null;
							state = State.READING_TAG;
						}
						else
							sb.append(c);
						continue;

					case SKIPPING_PROLOGUE:
						if (c == '>' && chars[i - 1] == '?')
							state = prevState;
						continue;
					case SKIPPING_COMMENT:
						if (c == '>' && chars[i - 1] == '-' && chars[i - 2] == '-')
							state = prevState;
						continue;
				}
			}

		}
		catch (ArrayIndexOutOfBoundsException e) {
			throw new InputFileException(f, "LINE %d: parse error while %s", lineNum, state);
		}

		if (!tags.isEmpty())
			throw new InputFileException(f, "LINE %d: not every tag has been closed", lineNum);

		// assign keys and prune tags and attributs not in the key set
		if (lastTag != null) {
			lastTag.key = keyMap.get(lastTag.name);
			if (lastTag.key == null)
				return null;
			else
				lastTag.prune(keyMap);
		}

		return lastTag;
	}
}

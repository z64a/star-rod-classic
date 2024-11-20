package game.string.editor.io;

import java.io.File;

import org.apache.commons.io.FilenameUtils;

public class FileMetadata
{
	public static enum FileType
	{
		Root, Dir, Resource
	}

	private final FileType type;

	// content dir
	public FileMetadata contentDir;
	public String[] dirAllowedExtensions;
	public boolean dirIncludeEmpty;

	// subdir
	private String dirName;
	private File dir;

	// resource
	private StringResource res;

	// content info
	public boolean error;
	public boolean modified;
	public int stringCount = 0;

	public static FileMetadata createRootData()
	{
		return new FileMetadata();
	}

	private FileMetadata()
	{
		type = FileType.Root;
	}

	public FileMetadata(File dir, boolean includeEmpty, String[] allowedExtensions)
	{
		this(dir, dir.getName(), includeEmpty, allowedExtensions);
	}

	public FileMetadata(File dir, String dirName, boolean includeEmpty, String[] allowedExtensions)
	{
		type = FileType.Dir;
		this.dir = dir;
		this.dirName = dirName;
		dirAllowedExtensions = allowedExtensions;
		dirIncludeEmpty = includeEmpty;
		this.contentDir = this;
	}

	public FileMetadata(FileMetadata contentDir, File dir)
	{
		type = FileType.Dir;
		this.dir = dir;
		this.dirName = dir.getName();
		this.contentDir = contentDir;
	}

	public FileMetadata(FileMetadata contentDir, StringResource res)
	{
		type = FileType.Resource;
		this.res = res;
		this.contentDir = contentDir;
	}

	public FileType getType()
	{
		return type;
	}

	public File getFile()
	{
		switch (type) {
			default:
			case Root:
				return null;
			case Dir:
				return dir;
			case Resource:
				return res.file;
		}
	}

	public File getDirectory()
	{
		assert (type == FileType.Dir);
		return dir;
	}

	public StringResource getResource()
	{
		assert (type == FileType.Resource);
		return res;
	}

	@Override
	public String toString()
	{
		String baseName = "ERROR";
		switch (type) {
			case Root:
				baseName = "All";
				break;
			case Dir:
				baseName = dirName;
				break;
			case Resource:
				baseName = FilenameUtils.getBaseName(res.file.getName());
				break;
		}
		return baseName + "  (" + stringCount + ")";
	}
}

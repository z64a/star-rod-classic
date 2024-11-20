package game.string.editor.io;

import static app.Directories.*;

import java.io.File;
import java.util.HashMap;
import java.util.Stack;

import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;

import org.apache.commons.io.FilenameUtils;

import app.Environment;
import app.input.InputFileException;
import game.string.editor.io.FileMetadata.FileType;
import util.Logger;

public class StringTreeModel extends DefaultTreeModel
{
	public StringTreeModel()
	{
		super(new StringTreeNode(FileMetadata.createRootData()));
	}

	@Override
	public StringTreeNode getRoot()
	{
		return (StringTreeNode) super.getRoot();
	}

	public void print()
	{
		print(getRoot(), "");
	}

	private void print(TreeNode node, String prefix)
	{
		System.out.println(prefix + node.toString());
		for (int i = 0; i < node.getChildCount(); i++)
			print(node.getChildAt(i), prefix + "    ");
	}

	public HashMap<FileMetadata, StringTreeNode> getNodeLookup()
	{
		HashMap<FileMetadata, StringTreeNode> nodeMap = new HashMap<>();
		Stack<StringTreeNode> nodes = new Stack<>();
		nodes.push(getRoot());

		while (!nodes.isEmpty()) {
			StringTreeNode node = nodes.pop();
			FileMetadata nodeData = node.getUserObject();
			nodeMap.put(nodeData, node);

			for (int i = 0; i < node.getChildCount(); i++)
				nodes.push(node.getChildAt(i));
		}

		return nodeMap;
	}

	private HashMap<File, FileMetadata> buildResourceCache()
	{
		HashMap<File, FileMetadata> fileMap = new HashMap<>();
		Stack<StringTreeNode> nodes = new Stack<>();
		nodes.push(getRoot());

		while (!nodes.isEmpty()) {
			StringTreeNode node = nodes.pop();
			FileMetadata nodeData = node.getUserObject();

			if (nodeData.getType() == FileType.Resource)
				fileMap.put(nodeData.getFile(), nodeData);

			for (int i = 0; i < node.getChildCount(); i++)
				nodes.push(node.getChildAt(i));
		}

		return fileMap;
	}

	public void forceFullReload()
	{
		reloadAll(new HashMap<>());
	}

	public void refresh()
	{
		HashMap<File, FileMetadata> resources = buildResourceCache();
		for (FileMetadata data : resources.values())
			data.getResource().isValid = false;

		reloadAll(buildResourceCache());

		resources = buildResourceCache();
		for (FileMetadata data : resources.values())
			data.getResource().isValid = true;
	}

	private void reloadAll(HashMap<File, FileMetadata> resourceMap)
	{
		long t0 = System.nanoTime();

		StringTreeNode root = getRoot();
		FileMetadata rootData = root.getUserObject();

		root.removeAllChildren();
		reload();

		rootData.stringCount = 0;
		int resourceCount = 0;

		if (Environment.project.isDecomp) {
			for (File assetDir : Environment.project.decompConfig.assetDirectories) {
				File msgDir = new File(assetDir, "msg");
				if (!msgDir.exists())
					continue;
				resourceCount += addContentDirectory(true, resourceMap,
					new FileMetadata(assetDir, true, new String[] { "msg" }));
			}
		}
		else {
			resourceCount += addContentDirectory(true, resourceMap,
				new FileMetadata(MOD_STRINGS_SRC.toFile(), true, new String[] { "str", "msg" }));
			resourceCount += addContentDirectory(true, resourceMap,
				new FileMetadata(MOD_STRINGS_PATCH.toFile(), true, new String[] { "str", "msg" }));

			resourceCount += addContentDirectory(false, resourceMap,
				new FileMetadata(MOD_MAP_PATCH.toFile(), "Map Patches", false, new String[] { "mpat" }));
			resourceCount += addContentDirectory(false, resourceMap,
				new FileMetadata(MOD_FORMA_PATCH.toFile(), "Battle Patches", false, new String[] { "bpat" }));
			resourceCount += addContentDirectory(false, resourceMap,
				new FileMetadata(MOD_PATCH.toFile(), "Global Patches", false, new String[] { "patch" }));
		}

		long t1 = System.nanoTime();
		Logger.logf("Loaded %d strings from %d files in %.2fs.", rootData.stringCount, resourceCount, (t1 - t0) / 1e9);
	}

	private int addContentDirectory(boolean insert, HashMap<File, FileMetadata> resourceMap, FileMetadata contentDirData)
	{
		StringTreeNode root = getRoot();
		FileMetadata rootData = root.getUserObject();

		StringTreeNode contentDirNode = new StringTreeNode(contentDirData);

		if (insert)
			insertNodeInto(contentDirNode, root, 0);
		else
			root.add(contentDirNode);

		int resourceCount = addDirectory(contentDirNode, resourceMap);
		rootData.stringCount += contentDirData.stringCount;
		return resourceCount;
	}

	private int addDirectory(StringTreeNode dirNode, HashMap<File, FileMetadata> resourceMap)
	{
		FileMetadata dirData = dirNode.getUserObject();

		int resourceCount = 0;
		dirData.stringCount = 0;
		File[] fileList = dirData.getDirectory().listFiles();

		if (fileList == null) {
			Logger.logError("Could not list files for directory: " + dirData.getDirectory().getAbsolutePath());
			return resourceCount;
		}

		// check subdirectories
		for (File f : fileList) {
			if (f.isDirectory()) {
				FileMetadata subdirData = new FileMetadata(dirData.contentDir, f);
				StringTreeNode subdirNode = new StringTreeNode(subdirData);

				resourceCount += addDirectory(subdirNode, resourceMap);
				if (dirData.contentDir.dirIncludeEmpty || subdirData.stringCount > 0) {
					dirData.stringCount += subdirData.stringCount;
					dirNode.add(subdirNode);
				}
			}
		}

		// add string resources
		for (File f : fileList) {
			if (!f.isDirectory()) {
				if (FilenameUtils.isExtension(f.getName(), dirData.contentDir.dirAllowedExtensions)) {
					try {
						FileMetadata resData = resourceMap.get(f);
						if (resData != null) {
							StringResource res = resData.getResource();
							if (res.lastModified < res.file.lastModified()) {
								res.reload();
								Logger.log("Reloaded " + res.file.getAbsolutePath());
							}
						}
						else {
							StringResource res = new StringResource(f);
							resData = new FileMetadata(dirData.contentDir, res);
						}

						StringResource res = resData.getResource();

						if (dirData.contentDir.dirIncludeEmpty || res.strings.size() > 0) {
							StringTreeNode resNode = new StringTreeNode(resData);
							resData.stringCount = res.strings.size();
							dirData.stringCount += res.strings.size();
							dirNode.add(resNode);
							resourceCount++;
						}
					}
					catch (InputFileException e) {
						Logger.logError(e.getMessage());
					}
				}
			}
		}

		return resourceCount;
	}
}

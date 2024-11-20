package game.string.editor.io;

import java.util.Iterator;
import java.util.List;

import javax.swing.tree.DefaultMutableTreeNode;

import game.string.PMString;
import game.string.editor.io.FileMetadata.FileType;

public class StringTreeNode extends DefaultMutableTreeNode implements Iterable<StringTreeNode>
{
	public StringTreeNode(FileMetadata data)
	{
		super(data);
	}

	@Override
	public void setUserObject(Object obj)
	{
		throw new IllegalStateException("StringTreeNode may only hold FileMetadata objects!");
	}

	public void setUserObject(FileMetadata data)
	{
		super.setUserObject(data);
	}

	@Override
	public FileMetadata getUserObject()
	{
		return (FileMetadata) super.getUserObject();
	}

	public void addStrings(List<PMString> list)
	{
		FileMetadata data = getUserObject();
		switch (data.getType()) {
			case Root:
			case Dir:
				for (int i = 0; i < getChildCount(); i++)
					getChildAt(i).addStrings(list);
				return;
			case Resource:
				list.addAll(data.getResource().strings);
				return;
		}
		throw new IllegalStateException("Unsupported node type: " + data.getType());
	}

	public void updateEditorInfo()
	{
		FileMetadata data = getUserObject();

		if (data.getType() == FileType.Resource) {
			data.stringCount = data.getResource().strings.size();
			data.error = false;
			data.modified = false;

			for (PMString s : data.getResource().strings) {
				if (s.hasError())
					data.error = true;
				if (s.isModified())
					data.modified = true;
			}
		}
		else {
			data.stringCount = 0;
			data.error = false;
			data.modified = false;

			for (int i = 0; i < getChildCount(); i++) {
				StringTreeNode child = getChildAt(i);
				FileMetadata childData = child.getUserObject();

				child.updateEditorInfo();

				if (childData.error)
					data.error = true;
				if (childData.modified)
					data.modified = true;
				data.stringCount += childData.stringCount;
			}
		}
	}

	@Override
	public StringTreeNode getChildAt(int index)
	{
		return (StringTreeNode) super.getChildAt(index);
	}

	@Override
	public Iterator<StringTreeNode> iterator()
	{
		return new Iterator<>() {
			int i = 0;

			@Override
			public boolean hasNext()
			{
				return i < StringTreeNode.this.getChildCount();
			}

			@Override
			public StringTreeNode next()
			{
				return StringTreeNode.this.getChildAt(i++);
			}
		};
	}
}

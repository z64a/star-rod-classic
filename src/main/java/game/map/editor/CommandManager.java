package game.map.editor;

import java.util.Stack;

import game.map.editor.commands.AbstractCommand;
import util.EvictingStack;
import util.Logger;

public class CommandManager
{
	private EvictingStack<HistoryEntry> undoStack;
	private Stack<HistoryEntry> redoStack;

	private long currentState;
	private long savedState;
	private long nextState;

	private static class HistoryEntry
	{
		private final AbstractCommand command;
		private final long beforeState;
		private final long afterState;

		private HistoryEntry(AbstractCommand command, long beforeState, long afterState)
		{
			this.command = command;
			this.beforeState = beforeState;
			this.afterState = afterState;
		}
	}

	public CommandManager(int undoLimit)
	{
		undoStack = new EvictingStack<>(undoLimit);
		redoStack = new Stack<>();
	}

	public void setUndoLimit(int undoLimit)
	{
		undoStack.setCapacity(undoLimit);
	}

	public void executeCommand(AbstractCommand cmd)
	{
		if (!cmd.shouldExec())
			return;

		cmd.exec();
		pushCommand(cmd);
	}

	/**
	 * Certain actions only use commands for undo/redo maintainence, and not for the
	 * initial execution. Those commands are sent here. However, this practice should
	 * be avoided wherever possible because it may lead to subtle errors involving
	 * undo/redo state integrity.
	 */
	public void pushCommand(AbstractCommand cmd)
	{
		long beforeState = currentState;
		if (cmd.modifiesMap())
			currentState = ++nextState;

		undoStack.push(new HistoryEntry(cmd, beforeState, currentState));
		redoStack.clear();
		if (cmd.modifiesMap())
			updateModified();
	}

	public void markSaved()
	{
		savedState = currentState;
		updateModified();
	}

	private void updateModified()
	{
		MapEditor.instance().map.modified = currentState != savedState;
	}

	public void action_Undo()
	{
		if (undoStack.size() > 0) {
			HistoryEntry entry = undoStack.pop();
			entry.command.undo();
			currentState = entry.beforeState;
			redoStack.push(entry);
			if (entry.command.modifiesMap())
				updateModified();
		}
		else {
			Logger.log("Can't undo any more.");
		}
	}

	public void action_Redo()
	{
		if (redoStack.size() > 0) {
			HistoryEntry entry = redoStack.pop();
			entry.command.exec();
			currentState = entry.afterState;
			undoStack.push(entry);
			if (entry.command.modifiesMap())
				updateModified();
		}
		else {
			Logger.log("Can't redo anything.");
		}
	}

	public void flush()
	{
		undoStack.clear();
		redoStack.clear();
	}
}

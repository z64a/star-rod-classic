package common;

import java.awt.Component;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.HashSet;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Collects raw AWT key events and physical key state. It does not assign keys to editor inputs.
 */
public class RawKeyboard implements KeyListener
{
	public static class KeyInputEvent
	{
		public final int code;
		public final int modifiers;

		private KeyInputEvent(KeyEvent evt)
		{
			code = evt.getKeyCode();
			modifiers = evt.getModifiersEx();
		}

		public KeyInputEvent(int keyCode)
		{
			code = keyCode;
			modifiers = 0;
		}
	}

	public static interface RawKeyboardListener
	{
		public default void keyPress(KeyInputEvent evt)
		{}

		public default void keyRelease(KeyInputEvent evt)
		{}
	}

	private HashSet<Integer> isKeyDown = new HashSet<>();
	private final Component component;

	private BlockingQueue<KeyEvent> pressed = new LinkedBlockingQueue<>();
	private BlockingQueue<KeyEvent> released = new LinkedBlockingQueue<>();

	public RawKeyboard(Component comp)
	{
		component = comp;
		comp.addKeyListener(this);
	}

	public boolean ownsEvent(KeyEvent event)
	{
		return event.getSource() == component;
	}

	public void reset()
	{
		isKeyDown = new HashSet<>();
		pressed = new LinkedBlockingQueue<>();
		released = new LinkedBlockingQueue<>();
	}

	@Override
	public void keyTyped(KeyEvent e)
	{}

	@Override
	public void keyPressed(KeyEvent e)
	{
		if (!isKeyDown.contains(e.getKeyCode())) {
			pressed.add(e);
			isKeyDown.add(e.getKeyCode());
		}
	}

	@Override
	public void keyReleased(KeyEvent e)
	{
		if (isKeyDown.contains(e.getKeyCode())) {
			released.add(e);
			isKeyDown.remove(e.getKeyCode());
		}
	}

	public boolean isCtrlDown()
	{
		return isKeyDown.contains(KeyEvent.VK_CONTROL);
	}

	public boolean isShiftDown()
	{
		return isKeyDown.contains(KeyEvent.VK_SHIFT);
	}

	public boolean isAltDown()
	{
		return isKeyDown.contains(KeyEvent.VK_ALT);
	}

	public boolean isKeyDown(int keycode)
	{
		return isKeyDown.contains(keycode);
	}

	boolean isKeyDown(KeyBinding binding)
	{
		if (!binding.isBound() || !isKeyDown(binding.keyCode))
			return false;

		int modifiers = 0;
		if (isCtrlDown())
			modifiers |= InputEvent.CTRL_DOWN_MASK;
		if (isShiftDown())
			modifiers |= InputEvent.SHIFT_DOWN_MASK;
		if (isAltDown())
			modifiers |= InputEvent.ALT_DOWN_MASK;
		if (isKeyDown(KeyEvent.VK_META))
			modifiers |= InputEvent.META_DOWN_MASK;

		return (modifiers & binding.modifiers) == binding.modifiers;
	}

	public void update(RawKeyboardListener listener, boolean hasFocus)
	{
		if (hasFocus) {
			while (!pressed.isEmpty())
				listener.keyPress(new KeyInputEvent(pressed.poll()));

			while (!released.isEmpty())
				listener.keyRelease(new KeyInputEvent(released.poll()));
		}
		else if (!isKeyDown.isEmpty()) {
			reset(listener);
		}
	}

	public void reset(RawKeyboardListener listener)
	{
		for (int keyCode : isKeyDown)
			listener.keyRelease(new KeyInputEvent(keyCode));
		isKeyDown.clear();
		pressed.clear();
		released.clear();
	}
}

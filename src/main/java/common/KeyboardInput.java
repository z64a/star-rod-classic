package common;

import java.awt.KeyEventDispatcher;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.function.Consumer;

import common.RawKeyboard.KeyInputEvent;
import common.RawKeyboard.RawKeyboardListener;

/**
 * Resolves physical keyboard events into exact logical editor inputs and exposes bound physical state for polled inputs.
 */
public final class KeyboardInput implements RawKeyboardListener, KeyEventDispatcher
{
	private static final class PressedInput
	{
		private final KeyInput input;
		private final KeyInput alias;

		private PressedInput(KeyInput input, KeyInput alias)
		{
			this.input = input;
			this.alias = alias;
		}
	}

	private static final class ListenerRegistration<T extends KeyInput>
	{
		private final Class<T> inputType;
		private final Consumer<T> pressedHandler;
		private final Consumer<T> releasedHandler;
		private final Consumer<T> enqueueHandler;

		private ListenerRegistration(Class<T> inputType, Consumer<T> pressedHandler, Consumer<T> releasedHandler, Consumer<T> enqueueHandler)
		{
			this.inputType = inputType;
			this.pressedHandler = pressedHandler;
			this.releasedHandler = releasedHandler;
			this.enqueueHandler = enqueueHandler;
		}

		private void inputPressed(KeyInput input)
		{
			if (pressedHandler != null && inputType.isInstance(input))
				pressedHandler.accept(inputType.cast(input));
		}

		private void inputReleased(KeyInput input)
		{
			if (releasedHandler != null && inputType.isInstance(input))
				releasedHandler.accept(inputType.cast(input));
		}

		private void enqueueInput(KeyInput input)
		{
			if (enqueueHandler != null && inputType.isInstance(input))
				enqueueHandler.accept(inputType.cast(input));
		}
	}

	private final RawKeyboard rawKeyboard;
	private final KeyboardInputConfig config;
	private final HashMap<Integer, PressedInput> pressedKeys = new HashMap<>();
	private final HashSet<KeyInput> activeInputs = new HashSet<>();
	private final List<ListenerRegistration<?>> listeners = new ArrayList<>();

	public KeyboardInput(RawKeyboard rawKeyboard, KeyboardInputConfig config)
	{
		this.rawKeyboard = rawKeyboard;
		this.config = config;
	}

	public <T extends KeyInput> void addListener(Class<T> inputType, Consumer<T> pressedHandler, Consumer<T> releasedHandler, Consumer<T> enqueueHandler)
	{
		listeners.add(new ListenerRegistration<>(inputType, pressedHandler, releasedHandler, enqueueHandler));
	}

	@Override
	public void keyPress(KeyInputEvent key)
	{
		if (KeyBinding.isModifierKey(key.code))
			return;

		KeyInput input = config.resolve(key.code, key.modifiers);
		if (input != null) {
			KeyInput alias = config.getAlias(input);
			pressedKeys.put(key.code, new PressedInput(input, alias));
			activeInputs.add(input);
			notifyPressed(input);
			if (alias != null) {
				activeInputs.add(alias);
				notifyPressed(alias);
			}
		}
	}

	@Override
	public void keyRelease(KeyInputEvent key)
	{
		if (KeyBinding.isModifierKey(key.code))
			return;

		PressedInput pressedInput = pressedKeys.remove(key.code);
		if (pressedInput != null) {
			activeInputs.remove(pressedInput.input);
			notifyReleased(pressedInput.input);
			if (pressedInput.alias != null) {
				activeInputs.remove(pressedInput.alias);
				notifyReleased(pressedInput.alias);
			}
		}
	}

	@Override
	public boolean dispatchKeyEvent(KeyEvent event)
	{
		if (event.getID() != KeyEvent.KEY_PRESSED || rawKeyboard.ownsEvent(event))
			return false;

		KeyInput input = config.resolve(event.getKeyCode(), event.getModifiersEx());
		if (input != null && config.isGlobal(input)) {
			notifyEnqueued(input);
			return true;
		}
		return false;
	}

	/**
	 * Tests the input's physical binding as held state. Required modifiers must be down; additional modifiers are ignored.
	 */
	public boolean isDown(KeyInput input)
	{
		return rawKeyboard.isKeyDown(config.getHeldBinding(input));
	}

	public boolean isShiftDown()
	{
		return rawKeyboard.isShiftDown();
	}

	public void reset()
	{
		for (KeyInput input : new ArrayList<>(activeInputs))
			notifyReleased(input);
		pressedKeys.clear();
		activeInputs.clear();
		rawKeyboard.reset();
	}

	private void notifyPressed(KeyInput input)
	{
		for (ListenerRegistration<?> listener : listeners)
			listener.inputPressed(input);
	}

	private void notifyReleased(KeyInput input)
	{
		for (ListenerRegistration<?> listener : listeners)
			listener.inputReleased(input);
	}

	private void notifyEnqueued(KeyInput input)
	{
		for (ListenerRegistration<?> listener : listeners)
			listener.enqueueInput(input);
	}
}

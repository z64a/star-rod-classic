package common;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import util.Logger;

/**
 * Editor-specific key bindings and policy for resolving physical key chords into logical inputs.
 */
public abstract class KeyboardInputConfig
{
	public static interface Listener
	{
		public void keyBindingsChanged();
	}

	private final LinkedHashMap<KeyInput, KeyBinding> defaults = new LinkedHashMap<>();
	private final LinkedHashMap<KeyInput, KeyBinding> bindings = new LinkedHashMap<>();
	private final List<Listener> listeners = new ArrayList<>();
	private final HashSet<KeyInput> globalInputs = new HashSet<>();
	private final Map<KeyInput, KeyInput> aliases = new HashMap<>();
	private final Map<KeyInput, KeyInput> aliasSources = new HashMap<>();

	private volatile HashMap<KeyBinding, KeyInput> inputMap = new HashMap<>();

	protected final void addDefault(KeyInput input, int keyCode)
	{
		addDefault(input, keyCode, 0);
	}

	protected final void addDefault(KeyInput input, int keyCode, int modifiers)
	{
		defaults.put(input, new KeyBinding(keyCode, modifiers));
	}

	protected final void finishDefaults()
	{
		bindings.clear();
		bindings.putAll(defaults);
		rebuildInputMap();
	}

	protected final void makeGlobal(KeyInput input)
	{
		globalInputs.add(input);
	}

	protected final void addAlias(KeyInput input, KeyInput alias)
	{
		KeyInput existingSource = aliasSources.get(alias);
		if (existingSource != null && !existingSource.equals(input))
			throw new IllegalArgumentException("Input alias already has a source: " + alias);

		KeyInput previousAlias = aliases.put(input, alias);
		if (previousAlias != null)
			aliasSources.remove(previousAlias);
		aliasSources.put(alias, input);
	}

	public void addListener(Listener listener)
	{
		listeners.add(listener);
	}

	public KeyBinding getBinding(KeyInput input)
	{
		KeyBinding binding = bindings.get(input);
		return (binding == null) ? KeyBinding.NONE : binding;
	}

	public KeyBinding getDefaultBinding(KeyInput input)
	{
		KeyBinding binding = defaults.get(input);
		return (binding == null) ? KeyBinding.NONE : binding;
	}

	public String getBindingText(KeyInput input)
	{
		return getBinding(input).getDisplayText();
	}

	public boolean isUserBindable(KeyInput input)
	{
		return defaults.containsKey(input);
	}

	public List<KeyInput> getUserBindableInputs()
	{
		return new ArrayList<>(defaults.keySet());
	}

	public Map<KeyInput, KeyBinding> copyBindings()
	{
		return new LinkedHashMap<>(bindings);
	}

	public KeyInput findConflict(Map<KeyInput, KeyBinding> candidateBindings, KeyInput target, KeyBinding binding)
	{
		if (!binding.isBound())
			return null;

		for (KeyInput input : defaults.keySet()) {
			if (!input.equals(target) && binding.equals(candidateBindings.get(input)))
				return input;
		}
		return null;
	}

	public synchronized void setBindings(Map<KeyInput, KeyBinding> newBindings)
	{
		bindings.clear();
		bindings.putAll(defaults);
		for (KeyInput input : defaults.keySet()) {
			KeyBinding binding = newBindings.get(input);
			if (binding != null)
				bindings.put(input, binding);
		}

		rebuildInputMap();
		notifyListeners();
	}

	public void resetAll()
	{
		setBindings(defaults);
	}

	final boolean isGlobal(KeyInput input)
	{
		return globalInputs.contains(input);
	}

	final KeyInput getAlias(KeyInput input)
	{
		return aliases.get(input);
	}

	final KeyInput resolve(int keyCode, int modifiers)
	{
		return inputMap.get(new KeyBinding(keyCode, modifiers));
	}

	final KeyBinding getHeldBinding(KeyInput input)
	{
		KeyBinding binding = getBinding(input);
		if (binding.isBound())
			return binding;

		KeyInput source = aliasSources.get(input);
		return (source == null) ? KeyBinding.NONE : getBinding(source);
	}

	private void rebuildInputMap()
	{
		HashMap<KeyBinding, KeyInput> newInputMap = new HashMap<>();
		for (KeyInput input : defaults.keySet()) {
			KeyBinding binding = getBinding(input);
			if (!binding.isBound())
				continue;

			KeyInput conflict = newInputMap.get(binding);
			if (conflict != null) {
				Logger.logWarning(String.format("Duplicate key binding %s for %s and %s; disabling %s.",
					binding.getDisplayText(), conflict, input, input));
				bindings.put(input, KeyBinding.NONE);
				continue;
			}
			newInputMap.put(binding, input);
		}
		inputMap = newInputMap;
	}

	private void notifyListeners()
	{
		for (Listener listener : listeners)
			listener.keyBindingsChanged();
	}
}

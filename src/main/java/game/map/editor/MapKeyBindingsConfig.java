package game.map.editor;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import common.KeyBinding;
import common.KeyInput;
import util.Logger;

/**
 * Reads and writes Map Editor key-binding overrides. Only values that differ
 * from defaults are written, so new actions can gain defaults without a config
 * migration.
 */
public final class MapKeyBindingsConfig
{
	private MapKeyBindingsConfig()
	{}

	public static void load(File file, MapKeyConfig keyConfig)
	{
		keyConfig.resetAll();
		if (!file.exists())
			return;

		Map<KeyInput, KeyBinding> bindings = keyConfig.copyBindings();

		try (BufferedReader reader = new BufferedReader(new InputStreamReader(
			new FileInputStream(file), StandardCharsets.UTF_8))) {
			String line;
			int lineNumber = 0;
			while ((line = reader.readLine()) != null) {
				lineNumber++;
				line = stripComment(line).trim();
				if (line.isEmpty())
					continue;

				int equals = line.indexOf('=');
				if (equals < 0) {
					Logger.logWarning(String.format("Ignoring malformed key binding on line %d of %s.",
						lineNumber, file.getName()));
					continue;
				}

				String actionName = line.substring(0, equals).trim();
				String bindingText = line.substring(equals + 1).trim();
				try {
					MapInput input = MapInput.valueOf(actionName);
					if (!keyConfig.isUserBindable(input)) {
						Logger.logWarning("Ignoring non-bindable Map Editor action: " + actionName);
						continue;
					}
					bindings.put(input, KeyBinding.parse(bindingText));
				}
				catch (IllegalArgumentException e) {
					Logger.logWarning(String.format("Ignoring invalid key binding on line %d of %s: %s",
						lineNumber, file.getName(), line));
				}
			}
		}
		catch (IOException e) {
			Logger.logWarning("Could not read Map Editor key bindings: " + e.getMessage());
			return;
		}

		keyConfig.setBindings(bindings);
	}

	public static void save(File file, MapKeyConfig keyConfig)
	{
		File parent = file.getParentFile();
		if (parent != null && !parent.exists() && !parent.mkdirs()) {
			Logger.logWarning("Could not create key-binding directory: " + parent.getAbsolutePath());
			return;
		}

		try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(
			new FileOutputStream(file), StandardCharsets.UTF_8))) {
			writer.println("% Map Editor key bindings. Only overrides from the defaults are listed.");
			writer.println("% Use NONE to leave an action unbound.");

			for (MapInput input : MapInput.values()) {
				if (!keyConfig.isUserBindable(input))
					continue;
				if (keyConfig.getBinding(input).equals(keyConfig.getDefaultBinding(input)))
					continue;

				writer.printf("%s = %s%n", input.name(), keyConfig.getBinding(input).serialize());
			}
		}
		catch (IOException e) {
			Logger.logWarning("Could not save Map Editor key bindings: " + e.getMessage());
		}
	}

	private static String stripComment(String line)
	{
		int comment = line.indexOf('%');
		if (comment >= 0)
			return line.substring(0, comment);
		return line;
	}
}

package common;

/**
 * Marker for a logical keyboard input. Physical keys are assigned separately by KeyboardInputConfig.
 */
public interface KeyInput
{
	public default String getDisplayName()
	{
		StringBuilder result = new StringBuilder();
		String[] words = toString().toLowerCase().split("_");
		for (String word : words) {
			if (result.length() > 0)
				result.append(' ');
			result.append(Character.toUpperCase(word.charAt(0)));
			result.append(word.substring(1));
		}
		return result.toString();
	}

	public default String getCategory()
	{
		return "General";
	}
}

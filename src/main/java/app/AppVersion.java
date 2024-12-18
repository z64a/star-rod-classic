package app;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AppVersion
{
	private static final Pattern pattern = Pattern.compile("v?(\\d+)\\.(\\d+)\\.(\\d+)(?:-(\\w+))?");
	private static final Matcher matcher = pattern.matcher("");

	public static enum VersionLevel
	{
		MAJOR,
		MINOR,
		PATCH,
	}

	private final String text;
	public final int major;
	public final int minor;
	public final int patch;
	public final String suffix;

	private AppVersion(int major, int minor, int patch, String suffix)
	{
		this.major = major;
		this.minor = minor;
		this.patch = patch;
		this.suffix = suffix;

		if (!suffix.isBlank())
			text = String.format("v%d.%d.%d-%s", major, minor, patch, suffix);
		else
			text = String.format("v%d.%d.%d", major, minor, patch);
	}

	public static AppVersion fromString(String s)
	{
		matcher.reset(s);
		if (!matcher.matches()) {
			return null;
		}

		int major = Integer.parseInt(matcher.group(1));
		int minor = Integer.parseInt(matcher.group(2));
		int patch = Integer.parseInt(matcher.group(3));
		String suffix = matcher.group(4) != null ? matcher.group(4) : "";

		return new AppVersion(major, minor, patch, suffix);
	}

	@Override
	public String toString()
	{
		return text;
	}

	public int compareTo(AppVersion other, VersionLevel specificity)
	{
		if (major != other.major)
			return Integer.compare(major, other.major);

		if (specificity == VersionLevel.MAJOR)
			return 0;

		if (minor != other.minor)
			return Integer.compare(minor, other.minor);

		if (specificity == VersionLevel.MINOR)
			return 0;

		return Integer.compare(patch, other.patch);
	}

	public boolean isOlderThan(AppVersion other, VersionLevel specificity)
	{
		return this.compareTo(other, specificity) < 0;
	}

	public boolean isNewerThan(AppVersion other, VersionLevel specificity)
	{
		return this.compareTo(other, specificity) > 0;
	}

	public boolean isOlderThan(AppVersion other)
	{
		return isOlderThan(other, VersionLevel.PATCH);
	}

	public boolean isNewerThan(AppVersion other)
	{
		return isNewerThan(other, VersionLevel.PATCH);
	}
}

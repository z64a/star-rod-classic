package app;

import org.apache.commons.lang3.SystemUtils;

public final class OS
{
	private static enum Family
	{
		Windows,
		Mac,
		Linux,
		Other
	}

	private final Family family;

	public OS()
	{
		if (SystemUtils.IS_OS_WINDOWS)
			family = Family.Windows;
		else if (SystemUtils.IS_OS_LINUX)
			family = Family.Linux;
		else if (SystemUtils.IS_OS_MAC)
			family = Family.Mac;
		else
			family = Family.Other;
	}

	public boolean isWindows()
	{
		return family == Family.Windows;
	}

	public boolean isMacOS()
	{
		return family == Family.Mac;
	}

	public boolean isLinux()
	{
		return family == Family.Linux;
	}
}

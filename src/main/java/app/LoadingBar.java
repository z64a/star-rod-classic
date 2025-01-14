package app;

import java.awt.Dimension;

import javax.swing.JLabel;
import javax.swing.JProgressBar;

import net.miginfocom.swing.MigLayout;
import util.Logger;
import util.Logger.Listener;
import util.Logger.Message;
import util.Priority;

public class LoadingBar
{
	public static void show(String title)
	{
		show(title, Priority.STANDARD, false);
	}

	public static void show(String title, Priority priority)
	{
		show(title, priority, false);
	}

	public static void show(String title, boolean useImage)
	{
		show(title, Priority.STANDARD, useImage);
	}

	public static void show(String title, Priority priority, boolean useImage)
	{
		if (Environment.isCommandLine())
			return;

		if (instance == null) {
			instance = new LoadingBarWindow(title, useImage);
		}
		else {
			instance.setTitle(title);
			instance.titleLabel.setText(title);
			Logger.removeListener(instance);
		}

		Logger.addListener(instance, priority);
	}

	public static void dismiss()
	{
		if (instance != null) {
			instance.dispose();
			instance = null;
		}
	}

	private static LoadingBarWindow instance;

	private static class LoadingBarWindow extends StarRodFrame implements Listener
	{
		private final JProgressBar progressBar;
		private final JLabel titleLabel;

		private LoadingBarWindow(String title, boolean useImage)
		{
			super();

			setTitle("Initializing");

			setMinimumSize(new Dimension(320, 64));
			setLocationRelativeTo(null);
			setUndecorated(true);
			progressBar = new JProgressBar();

			progressBar.setIndeterminate(true);
			progressBar.setStringPainted(true);
			progressBar.setString("Loading...");

			titleLabel = SwingUtils.getCenteredLabel(title, 14);

			setLayout(new MigLayout("fill"));

			add(titleLabel, "grow, wrap");
			add(progressBar, "grow, pushy");
			pack();
			setLocationRelativeTo(null);
			setVisible(true);
		}

		@Override
		public void dispose()
		{
			super.dispose();
			Logger.removeListener(this);
		}

		@Override
		public void post(Message msg)
		{
			progressBar.setString(msg.text);
		}
	}
}

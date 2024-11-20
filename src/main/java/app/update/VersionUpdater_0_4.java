package app.update;

import static app.Directories.LOGS;

import java.awt.Dimension;
import java.io.File;
import java.io.IOException;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JProgressBar;
import javax.swing.SwingWorker;

import app.Directories;
import app.Environment;
import app.StarRodClassic;
import app.StarRodException;
import app.input.InvalidInputException;
import shared.Globals;
import util.LogFile;
import util.Logger;

public class VersionUpdater_0_4 extends JFrame
{
	public static void main(String[] args)
	{
		Environment.initialize();
		try {
			new VersionUpdater_0_4(Directories.DATABASE.toFile());
		}
		catch (Throwable t) {
			t.printStackTrace();
		}
		Environment.exit();
	}

	public VersionUpdater_0_4(File oldDatabase) throws IOException, InterruptedException
	{
		if (Environment.isCommandLine()) {
			doWork(oldDatabase);
			return;
		}

		setTitle("Star Rod Updater");
		setIconImage(Globals.getDefaultIconImage());

		final JDialog dialog = new JDialog(this, true); // modal
		dialog.setMinimumSize(new Dimension(480, 32));
		dialog.setLocationRelativeTo(null);
		dialog.setUndecorated(true);

		JProgressBar progressBar = new JProgressBar();
		progressBar.setIndeterminate(true);
		progressBar.setStringPainted(true);
		dialog.add(progressBar);
		dialog.pack();

		Logger.addListener(msg -> progressBar.setString(msg.text));

		LogFile updateLog = new LogFile(new File(LOGS + "update.log"), false);

		SwingWorker<Boolean, String> worker = new SwingWorker<>() {
			@Override
			protected Boolean doInBackground() throws IOException
			{
				doWork(oldDatabase);
				return true;
			}

			@Override
			protected void done()
			{
				dialog.dispose();
			}
		};

		worker.execute();
		dialog.setVisible(true);
		updateLog.close();
	}

	public void doWork(File oldDatabase) throws IOException
	{
		if (!Environment.hasCurrentDump)
			StarRodClassic.dumpAssets(true);
		Logger.log("Converting strings to new format");
		new StringFormatUpdater_0_4a();
		new StringFormatUpdater_0_4b();
		Logger.log("Updating library names");
		new LibraryUpdater(oldDatabase);
		Logger.log("Updating struct names");
		StructNameUpdater.updateAll();
		try {
			Logger.log("Updating var names");
			MapVarNameUpdater.run();
		}
		catch (InvalidInputException e) {
			throw new StarRodException(e);
		}
		Logger.log("Update complete");
	}
}

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
import shared.Globals;
import util.LogFile;
import util.Logger;

public class MinorUpdator extends JFrame
{
	public static void main(String[] args)
	{
		Environment.initialize();
		try {
			new MinorUpdator(Directories.DATABASE.toFile());
		}
		catch (Throwable t) {
			t.printStackTrace();
		}
		Environment.exit();
	}

	public MinorUpdator(File oldDatabase) throws IOException, InterruptedException
	{
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
				// do the work
				new LibraryUpdater(oldDatabase);
				StructNameUpdater.updateAll();
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
}

package app.update;

import static app.Directories.LOGS;

import java.awt.Dimension;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JProgressBar;
import javax.swing.SwingWorker;

import org.apache.commons.io.FileUtils;

import app.Directories;
import app.Environment;
import app.Project;
import app.config.Options;
import shared.Globals;
import util.LogFile;
import util.Logger;

public class BackupCreator extends JFrame
{
	public static void main(String[] args) throws IOException
	{
		Environment.initialize();
		new BackupCreator(Environment.project);
		Environment.exit();
	}

	private JProgressBar progressBar = new JProgressBar();

	public BackupCreator(Project mod) throws IOException
	{
		// frame setup
		setTitle("Star Rod Backup Creator");
		setIconImage(Globals.getDefaultIconImage());

		File fileToZip = mod.getDirectory();

		final JDialog dialog = new JDialog(this, true); // modal
		dialog.setMinimumSize(new Dimension(480, 32));
		dialog.setLocationRelativeTo(null);
		dialog.setUndecorated(true);

		progressBar.setIndeterminate(true);
		progressBar.setStringPainted(true);
		dialog.add(progressBar);
		dialog.pack();

		/*
		Logger.addListener(new Listener() {
			@Override
			public void post(Message msg) {
				progressBar.setString(msg.text);
			}
		});
		 */

		// files setup
		SimpleDateFormat formatter = new SimpleDateFormat("dd-MM-yyyy--HH-mm-ss");
		Date date = new Date();
		String cleanModName = mod.config.getString(Options.ModVersionString).replaceAll("\\W", "");
		String outName = "backup-" + cleanModName + "-" + formatter.format(date) + ".zip";
		File outFile = new File(Directories.BACKUPS + outName);

		if (!outFile.exists())
			FileUtils.touch(outFile);

		// do work
		try (FileOutputStream fos = new FileOutputStream(outFile);
			ZipOutputStream zipOut = new ZipOutputStream(fos);
			LogFile updateLog = new LogFile(new File(LOGS + "update.log"), false);) {
			SwingWorker<Boolean, String> worker = new SwingWorker<>() {
				@Override
				protected Boolean doInBackground() throws IOException
				{
					zipFile(fileToZip, fileToZip.getName(), zipOut);
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
		}

		Logger.log("Created mod backup: " + outName);
	}

	private void zipFile(File fileToZip, String fileName, ZipOutputStream zipOut) throws IOException
	{
		if (fileToZip.isHidden())
			return;

		if (fileToZip.isDirectory()) {
			if (fileName.endsWith("/")) {
				zipOut.putNextEntry(new ZipEntry(fileName));
				zipOut.closeEntry();
			}
			else {
				zipOut.putNextEntry(new ZipEntry(fileName + "/"));
				zipOut.closeEntry();
			}
			File[] children = fileToZip.listFiles();
			for (File childFile : children) {
				zipFile(childFile, fileName + "/" + childFile.getName(), zipOut);
			}
			return;
		}

		progressBar.setString("Backing up " + fileToZip.getName());

		FileInputStream fis = new FileInputStream(fileToZip);
		ZipEntry zipEntry = new ZipEntry(fileName);
		zipOut.putNextEntry(zipEntry);
		byte[] bytes = new byte[1024];
		int length;
		while ((length = fis.read(bytes)) >= 0) {
			zipOut.write(bytes, 0, length);
		}
		fis.close();
	}
}

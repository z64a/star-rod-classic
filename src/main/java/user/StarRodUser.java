package user;

import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.WindowConstants;

import org.apache.commons.io.FileUtils;

import net.miginfocom.swing.MigLayout;
import shared.Globals;
import shared.RomValidator;
import shared.SwingUtils;

public class StarRodUser extends JFrame
{
	private final JFileChooser romChooser;
	private final JFileChooser patchChooser;

	private File baseRom = null;
	private File patchFile = null;

	private JDialog pleaseWaitDialog;
	private JLabel currentTaskLabel;

	private boolean taskRunning = false;

	private List<JButton> buttons = new ArrayList<>();

	public static void main(String[] args)
	{
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		}
		catch (Exception e) {
			// just deal with it
		}

		new StarRodUser();
	}

	private StarRodUser()
	{
		setTitle("Star Rod Mod Patcher");
		setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		setIconImage(Globals.getDefaultIconImage());

		setMinimumSize(new Dimension(480, 32));
		setLocationRelativeTo(null);

		romChooser = new JFileChooser();
		romChooser.setCurrentDirectory(new File("."));
		romChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		romChooser.setDialogTitle("Choose Base ROM");

		patchChooser = new JFileChooser();
		patchChooser.setCurrentDirectory(new File("."));
		patchChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		patchChooser.setDialogTitle("Choose Patch File");

		JProgressBar progressBar = new JProgressBar();
		progressBar.setIndeterminate(true);

		currentTaskLabel = new JLabel("Applying patch to ROM...");

		pleaseWaitDialog = new JDialog(this);
		pleaseWaitDialog.setLocationRelativeTo(null);
		pleaseWaitDialog.setLayout(new MigLayout("fill"));
		pleaseWaitDialog.setTitle("Please Wait");
		pleaseWaitDialog.setIconImage(Globals.getDefaultIconImage());
		pleaseWaitDialog.add(currentTaskLabel, "wrap");
		pleaseWaitDialog.add(progressBar, "growx");
		pleaseWaitDialog.setMinimumSize(new Dimension(240, 80));
		pleaseWaitDialog.setResizable(false);
		pleaseWaitDialog.pack();

		final JTextField romFileField = new JTextField();
		romFileField.setEditable(false);
		romFileField.setMinimumSize(new Dimension(64, 24));

		JButton chooseROMButton = new JButton("Choose");
		// no lambda expressions
		chooseROMButton.addActionListener(arg0 -> new Thread() {
			@Override
			public void run()
			{
				for (JButton button : buttons)
					button.setEnabled(false);

				File choice = promptSelectBaseRom();
				if (choice != null) {
					romFileField.setText(choice.getAbsolutePath());
					baseRom = choice;
				}

				for (JButton button : buttons)
					button.setEnabled(true);
			}
		}.start());
		buttons.add(chooseROMButton);

		final JTextField patchFileField = new JTextField();
		patchFileField.setEditable(false);
		patchFileField.setMinimumSize(new Dimension(64, 24));

		JButton choosePatchButton = new JButton("Choose");
		choosePatchButton.addActionListener(arg0 -> {
			File choice = promptSelectPatch();
			if (choice != null) {
				patchFileField.setText(choice.getAbsolutePath());
				patchFile = choice;
			}
		});
		buttons.add(choosePatchButton);

		JButton patchRomButton = new JButton("Create Modded ROM");
		patchRomButton.addActionListener(arg0 -> {
			if (baseRom == null) {
				SwingUtils.getWarningDialog()
					.setTitle("Missing ROM")
					.setMessage("You have not selected a valid ROM.")
					.show();
				return;
			}

			if (patchFile == null) {
				SwingUtils.getWarningDialog()
					.setTitle("Missing Patch File")
					.setMessage("You have not selected a valid patch file.")
					.show();
				return;
			}

			startTask(new TaskWorker(() -> patchRom()));
		});
		buttons.add(patchRomButton);

		setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e)
			{
				int choice = JOptionPane.OK_OPTION;

				if (taskRunning)
					choice = SwingUtils.getConfirmDialog()
						.setTitle("Task Still Running")
						.setMessage("A task is still running.", "Are you sure you want to exit?")
						.setOptionsType(JOptionPane.YES_NO_OPTION)
						.setMessageType(JOptionPane.WARNING_MESSAGE)
						.choose();

				if (choice == JOptionPane.OK_OPTION)
					System.exit(0);
			}
		});

		setLayout(new MigLayout("fillx, ins 16 16 n 16, hidemode 3"));
		add(new JLabel("Paper Mario ROM:"));
		add(romFileField, "pushx, growx");
		add(chooseROMButton, "wrap");
		add(new JLabel("Star Rod Patch File:"));
		add(patchFileField, "pushx, growx");
		add(choosePatchButton, "wrap 16");
		add(patchRomButton, "growy, w 50%, center, span");

		pack();
		setResizable(false);
		setVisible(true);
	}

	public File promptSelectBaseRom()
	{
		if (SwingUtils.showFramedOpenDialog(romChooser, null) == JFileChooser.APPROVE_OPTION) {
			File romChoice = romChooser.getSelectedFile();
			if (baseRom == null || !romChoice.equals(baseRom)) {
				try {
					return RomValidator.validateROM(romChoice);
				}
				catch (IOException e) {
					SwingUtils.getErrorDialog()
						.setTitle("ROM Validation Failure")
						.setMessage("IOException during ROM validation.", e.getMessage())
						.show();
				}
			}
		}
		return null;
	}

	public File promptSelectPatch()
	{
		if (SwingUtils.showFramedOpenDialog(patchChooser, null) == JFileChooser.APPROVE_OPTION) {
			File patchChoice = patchChooser.getSelectedFile();
			if (patchFile == null || !patchChoice.equals(patchFile)) {
				return patchChoice;
			}
		}
		return null;
	}

	private void startTask(SwingWorker<?, ?> worker)
	{
		taskRunning = true;
		pleaseWaitDialog.setVisible(true);
		for (JButton button : buttons)
			button.setEnabled(false);
		worker.execute();
	}

	private void endTask()
	{
		for (JButton button : buttons)
			button.setEnabled(true);
		pleaseWaitDialog.setVisible(false);
		taskRunning = false;
	}

	private class TaskWorker extends SwingWorker<Boolean, String>
	{
		private final Runnable runnable;

		private TaskWorker(Runnable runnable)
		{
			this.runnable = runnable;
		}

		@Override
		protected Boolean doInBackground()
		{
			runnable.run();
			return true;
		}

		@Override
		protected void done()
		{
			endTask();
		}
	}

	private void patchRom()
	{
		try {
			final File patchedRom = applyPatch();
			if (patchedRom != null) {
				SwingUtilities.invokeLater(() -> {
					Toolkit.getDefaultToolkit().beep();

					SwingUtils.getMessageDialog()
						.setTitle("Patching Done")
						.setMessage("Your modded ROM is ready to play:", patchedRom.getAbsolutePath())
						.setMessageType(JOptionPane.INFORMATION_MESSAGE)
						.show();
				});
			}
		}
		catch (final Throwable e) {
			SwingUtilities.invokeLater(() -> {
				e.printStackTrace();
				displayStackTrace(e);
			});
		}
	}

	private File applyPatch() throws IOException
	{
		// load the patch file to a bytebuffer
		byte[] patchBytes = FileUtils.readFileToByteArray(patchFile);
		ByteBuffer bb = ByteBuffer.wrap(patchBytes);

		int firstWord = bb.getInt();
		bb.rewind();

		if (firstWord == 0x61593079 || firstWord == 0x4D505253) {
			byte[] swapped = new byte[patchBytes.length];

			for (int i = 0; i < patchBytes.length; i += 2) {
				swapped[i] = patchBytes[i + 1];
				swapped[i + 1] = patchBytes[i];
			}

			patchBytes = swapped;
		}

		if (firstWord == 0x59617930) {
			currentTaskLabel.setText("Decompressing mod package...");
			patchBytes = decodeYay0(patchBytes);
			bb = ByteBuffer.wrap(patchBytes);
		}

		if (bb.getInt() != 0x504D5352) {
			SwingUtils.getWarningDialog()
				.setTitle("Invalid Patch File")
				.setMessage("The patch file you selected is invalid.")
				.show();
			return null;
		}

		currentTaskLabel.setText("Applying patches to ROM...");

		// get path and extension
		String path = baseRom.getParentFile().getAbsolutePath();
		String extension = baseRom.getName().substring(baseRom.getName().lastIndexOf("."));

		// get patch filename (minus extension)
		String name = patchFile.getName();
		name = name.substring(0, name.lastIndexOf("."));

		File patchedRom = new File(path + File.separator + name + extension);
		if (patchedRom.exists()) {
			int choice = SwingUtils.getWarningDialog()
				.setTitle("Replace Existing File?")
				.setMessage("The file \"" + patchedRom.getName() + "\" already exists.",
					"Do you want to replace it?")
				.setOptionsType(JOptionPane.OK_CANCEL_OPTION)
				.choose();

			if (choice != JOptionPane.YES_OPTION)
				return null;
		}

		FileUtils.copyFile(baseRom, patchedRom);

		RandomAccessFile raf = new RandomAccessFile(patchedRom, "rw");

		int patchCount = bb.getInt();
		for (int i = 0; i < patchCount; i++) {
			int patchStart = bb.getInt();
			int patchLength = bb.getInt();

			byte[] patch = new byte[patchLength];
			bb.get(patch);

			raf.seek(patchStart);
			raf.write(patch);
		}

		raf.seek(0x10);
		int crc1 = raf.readInt();
		int crc2 = raf.readInt();

		raf.close();

		printProject64RDB(name, crc1, crc2);

		return patchedRom;
	}

	public static void displayStackTrace(Throwable e)
	{
		StackTraceElement[] stackTrace = e.getStackTrace();

		JTextArea textArea = new JTextArea(20, 50);
		textArea.setEditable(false);
		JScrollPane detailScrollPane = new JScrollPane(textArea);
		detailScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

		textArea.append(e.getClass().toString() + System.lineSeparator());
		for (StackTraceElement ele : stackTrace)
			textArea.append("  at " + ele.toString() + System.lineSeparator());

		String title = e.getClass().getSimpleName();
		if (title.isEmpty())
			title = "Anonymous Exception";

		if (e instanceof AssertionError)
			title = "Assertion Failed";

		StringBuilder msgBuilder = new StringBuilder();

		if (e.getMessage() != null)
			msgBuilder.append(e.getMessage());
		else if (stackTrace.length > 0)
			msgBuilder.append("at " + stackTrace[0].toString() + System.lineSeparator());

		int choice = SwingUtils.getErrorDialog()
			.setTitle(title)
			.setMessage(msgBuilder.toString())
			.setOptionsType(JOptionPane.YES_NO_CANCEL_OPTION)
			.setOptions("OK", "Details")
			.choose();

		if (choice == 1) {
			choice = SwingUtils.getErrorDialog()
				.setTitle("Exception Details")
				.setMessage(detailScrollPane)
				.setOptionsType(JOptionPane.YES_NO_CANCEL_OPTION)
				.setOptions("OK", "Copy to Clipboard")
				.choose();

			if (choice == 1) {
				StringSelection stringSelection = new StringSelection(textArea.getText());
				Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
				cb.setContents(stringSelection, null);
			}
		}
	}

	private static void printProject64RDB(String name, int crc1, int crc2) throws IOException
	{
		PrintWriter pw = new PrintWriter(new File("Project64 Settings.txt"));
		pw.printf(String.format("[%08X-%08X-C:45]%s", crc1, crc2, System.lineSeparator()));
		pw.printf("Good Name=%s%s", name, System.lineSeparator());
		pw.println("Internal Name=PAPER MARIO");
		pw.println("Status=Compatible");
		pw.println("32bit=No");
		pw.println("Clear Frame=1");
		pw.println("Counter Factor=1");
		pw.println("Culling=1");
		pw.println("Emulate Clear=0");
		pw.println("Primary Frame Buffer=0");
		pw.println("RDRAM Size=8");
		pw.println("Self Texture=0");
		pw.println("glide64-depthmode=1");
		pw.println("glide64-fb_hires=1");
		pw.println("glide64-fb_read_alpha=1");
		pw.println("glide64-fb_smart=1");
		pw.println("glide64-filtering=1");
		pw.println("glide64-hires_buf_clear=0");
		pw.println("glide64-optimize_texrect=0");
		pw.println("glide64-swapmode=2");
		pw.println("glide64-useless_is_useless=1");
		pw.close();
	}

	public static byte[] decodeYay0(byte[] source)
	{
		int decompressedSize = getInteger(source, 4);
		int linkOffset = getInteger(source, 8);
		int sourceOffset = getInteger(source, 12);

		byte currentCommand = 0;
		int commandOffset = 16;
		int remainingBits = 0;

		byte[] decoded = new byte[decompressedSize];
		int decodedBytes = 0;

		do {
			// get the next command
			if (remainingBits == 0) {
				currentCommand = source[commandOffset];
				commandOffset++;
				remainingBits = 8;
			}

			// bit == 1 --> copy directly from source
			if ((currentCommand & 0x80) != 0) {
				decoded[decodedBytes] = source[sourceOffset];
				sourceOffset++;
				decodedBytes++;

				// bit == 0 --> copy from decoded buffer
			}
			else {
				// find out where to copy from
				short link = getShort(source, linkOffset);
				linkOffset += 2;

				int dist = link & 0x0FFF;
				int copySrc = decodedBytes - (dist + 1);
				int length = ((link >> 12) & 0x0F);

				// determine how many bytes to copy
				if (length == 0) {
					length = (source[sourceOffset] & 0x0FF);
					length += (byte) 0x10;
					sourceOffset++;
				}

				length += 2;

				// copy
				for (int i = 0; i < length; i++) {
					decoded[decodedBytes] = decoded[copySrc + i];
					decodedBytes++;
				}
			}

			currentCommand <<= 1;
			remainingBits--;
		}
		while (decodedBytes < decompressedSize);

		return decoded;
	}

	private static short getShort(byte[] buffer, int start)
	{
		return (short) ((buffer[start + 1] & 0xFF) | (buffer[start] & 0xFF) << 8);
	}

	private static int getInteger(byte[] buffer, int start)
	{
		return (buffer[start + 3] & 0xFF) |
			(buffer[start + 2] & 0xFF) << 8 |
			(buffer[start + 1] & 0xFF) << 16 |
			(buffer[start + 0] & 0xFF) << 24;
	}
}

package game.string.editor;

import java.awt.Component;
import java.util.ArrayList;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import app.Environment;
import app.SwingUtils;
import game.string.editor.io.SourceWatcher.FileEvent;
import net.miginfocom.swing.MigLayout;

public class FileChangesDialogPanel extends JDialog
{
	private static final String TITLE = "File Changes Detected";

	public FileChangesDialogPanel(JFrame parent, ArrayList<FileEvent> pendingFileEvents)
	{
		super(parent, true);

		setLayout(new MigLayout("fill, wrap, ins 20"));

		if (pendingFileEvents.size() > 1)
			add(SwingUtils.getLabel(pendingFileEvents.size() + " files have been changed outside the editor:", 14));
		else
			add(SwingUtils.getLabel("1 file has been changed outside the editor:", 14));

		StringBuilder sb = new StringBuilder();
		for (FileEvent evt : pendingFileEvents)
			sb.append(evt.type.name().toUpperCase()).append("  ")
				.append(evt.file.getName()).append(System.lineSeparator());

		JTextArea text = new JTextArea(6, 32);
		text.setText(sb.toString());
		text.setEditable(false);
		text.setBorder(null);

		JScrollPane scroll = new JScrollPane(text);
		add(scroll, "gaptop 12");

		add(SwingUtils.getCenteredLabel("Affected content folders will now be reloaded.", 14), "growx, gaptop 6");

		JButton cancelButton = new JButton("OK");
		cancelButton.addActionListener((e) -> {
			setVisible(false);
		});
		SwingUtils.setFontSize(cancelButton, 14);
		add(cancelButton, "span, center, w 50%, gaptop 6, gapbottom 4");
	}

	public static void showFramedDialog(Component parentComponent, ArrayList<FileEvent> pendingFileEvents)
	{
		JFrame dialogFrame = createDialogFrame(parentComponent, TITLE);
		FileChangesDialogPanel panel = new FileChangesDialogPanel(dialogFrame, pendingFileEvents);
		dialogFrame.setResizable(false);
		dialogFrame.pack();

		panel.setTitle(TITLE);
		panel.setIconImage(Environment.getDefaultIconImage());
		panel.pack();

		panel.setLocationRelativeTo(parentComponent);
		panel.setVisible(true);

		dialogFrame.dispose();
		return;
	}

	private static final JFrame createDialogFrame(Component parentComponent, String title)
	{
		JFrame dialogFrame = new JFrame(title);
		dialogFrame.setUndecorated(true);
		dialogFrame.setVisible(true);
		dialogFrame.setLocationRelativeTo(parentComponent);
		dialogFrame.setIconImage(Environment.getDefaultIconImage());
		return dialogFrame;
	}
}

package app;

import java.awt.Dimension;

import javax.swing.JFrame;
import javax.swing.JProgressBar;

import net.miginfocom.swing.MigLayout;
import shared.Globals;
import shared.SwingUtils;
import util.Logger;
import util.Logger.Listener;
import util.Logger.Message;

public class LoadingScreen extends JFrame implements Listener
{
	private final JProgressBar progressBar;

	public LoadingScreen()
	{
		super();
		setTitle("Loading");
		setIconImage(Globals.getDefaultIconImage());

		setMinimumSize(new Dimension(320, 64));
		setLocationRelativeTo(null);
		setUndecorated(true);
		progressBar = new JProgressBar();

		progressBar.setIndeterminate(true);
		progressBar.setStringPainted(true);
		progressBar.setString("Loading...");

		setLayout(new MigLayout("fill"));
		add(SwingUtils.getCenteredLabel("Please Wait", 14), "grow, wrap");
		add(progressBar, "grow, pushy");
		pack();
		setVisible(true);

		Logger.addListener(this);
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

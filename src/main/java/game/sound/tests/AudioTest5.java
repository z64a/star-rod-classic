package game.sound.tests;

import static app.Directories.MOD_AUDIO_MSEQ;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.Collection;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;

import app.Environment;
import app.input.IOUtils;
import common.FrameLimiter;
import game.sound.engine.AudioEngine;
import game.sound.engine.Instrument;
import game.sound.engine.SoundBank;
import game.sound.engine.Voice;
import game.sound.mseq.Mseq;
import game.sound.mseq.MseqPlayer;
import net.miginfocom.swing.MigLayout;

public class AudioTest5
{
	private static final int TARGET_FPS = 60; // PM audio thread

	private final Object threadLock = new Object();

	public static void main(String[] args) throws Exception
	{
		Environment.initialize();
		new AudioTest5();
	}

	private AudioEngine engine;
	private SoundBank bank;
	private MseqPlayer player;

	private boolean ignoreSliderUpdate = false;
	private JSlider timeSlider;

	private volatile boolean running = true;

	private AudioTest5() throws Exception
	{
		engine = new AudioEngine();
		bank = new SoundBank();
		player = new MseqPlayer(engine, bank);

		// required for radio songs, just keep this always loaded
		bank.installAuxBank("SPC3", 2);

		Instrument testInstrument = bank.getInstrument(0x30, 0).instrument();

		Collection<File> mseqFiles = IOUtils.getFilesWithExtension(MOD_AUDIO_MSEQ, "xml", false);

		JFrame frame = new JFrame("Audio Test");
		frame.setSize(400, 200);
		frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		frame.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e)
			{
				running = false;
				engine.shutdown();
				frame.dispose();
				Environment.exit();
			}
		});

		frame.setLayout(new MigLayout("fill, ins 16"));

		JButton playButton = new JButton("Play Sound");
		playButton.addActionListener((e) -> {
			Voice testVoice = engine.getVoice();
			testVoice.setInstrument(testInstrument);
			testVoice.play();

			engine.addVoice(testVoice);
		});

		JSlider masterVolumeSlider = new JSlider(0, 256, engine.getMasterVolume());
		masterVolumeSlider.setPaintTicks(true);
		masterVolumeSlider.setMajorTickSpacing(64);
		masterVolumeSlider.setMinorTickSpacing(16);
		masterVolumeSlider.addChangeListener((e) -> {
			engine.setMasterVolume(masterVolumeSlider.getValue());
		});

		timeSlider = new JSlider(0, 1, 0);
		//timeSlider.setPaintTicks(true);
		timeSlider.addChangeListener((e) -> {
			if (!ignoreSliderUpdate) {
				synchronized (threadLock) {
					player.seekTime(timeSlider.getValue());
				}
			}
		});

		DefaultListModel<File> filesModel = new DefaultListModel<>();
		filesModel.addAll(mseqFiles);

		JList<File> mseqFileList = new JList<>();
		mseqFileList.setModel(filesModel);
		mseqFileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

		JScrollPane fileListScroll = new JScrollPane(mseqFileList);
		fileListScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		fileListScroll.setWheelScrollingEnabled(true);

		mseqFileList.addListSelectionListener((evt) -> {
			if (evt.getValueIsAdjusting())
				return;

			File selected = mseqFileList.getSelectedValue();

			Mseq mseq = Mseq.load(selected);
			mseq.calculateTiming();

			synchronized (threadLock) {
				timeSlider.setMaximum(mseq.duration);
				timeSlider.setValue(0);
				player.setMseq(mseq);
			}
		});

		JButton pauseButton = new JButton("Pause");
		pauseButton.addActionListener((evt) -> {
			synchronized (threadLock) {
				boolean paused = player.getPaused();
				player.setPaused(!paused);
				pauseButton.setText(player.getPaused() ? "Play" : "Pause");
			}
		});

		frame.add(playButton);
		frame.add(masterVolumeSlider, "grow, wrap");
		frame.add(mseqFileList, "span, wrap");
		frame.add(pauseButton);
		frame.add(timeSlider, "grow");

		frame.pack();
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);

		new Thread(() -> {
			try {
				run();
			}
			catch (Exception e) {
				e.printStackTrace();
			}
		}).start();
	}

	public void run() throws InterruptedException
	{
		double deltaTime = 1.0 / TARGET_FPS;

		FrameLimiter limiter = new FrameLimiter();

		while (running) {
			long t0 = System.nanoTime();

			synchronized (threadLock) {
				engine.renderFrame(deltaTime);
			}

			ignoreSliderUpdate = true;
			timeSlider.setValue(player.getTime());
			ignoreSliderUpdate = false;

			limiter.sync(TARGET_FPS);

			deltaTime = (System.nanoTime() - t0) / 1e9;
		}
	}

	private static class WaveformPanel extends JPanel
	{
		private float[] samples = new float[0];

		public void updateSamples(float[] samples)
		{
			this.samples = samples;
			repaint(); // trigger repaint on UI thread
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			super.paintComponent(g);

			if (samples.length == 0)
				return;

			int w = getWidth();
			int h = getHeight();

			g.setColor(Color.BLACK);
			g.fillRect(0, 0, w, h);

			g.setColor(Color.GREEN);
			int midY = h / 2;

			int step = Math.max(1, samples.length / w);
			for (int x = 0; x < w && x * step < samples.length; x++) {
				int i = x * step;
				float sample = samples[i];
				int y = (int) (sample * midY);
				g.drawLine(x, midY - y, x, midY + y);
			}
		}
	}
}

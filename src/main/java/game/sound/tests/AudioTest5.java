package game.sound.tests;

import static app.Directories.MOD_AUDIO_MSEQ;

import java.awt.Color;
import java.awt.Dimension;
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
import javax.swing.Timer;

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
	private static final int WAVEFORM_SAMPLE_COUNT = 8192;

	private final Object threadLock = new Object();
	private final WaveformBuffer waveformBuffer = new WaveformBuffer(WAVEFORM_SAMPLE_COUNT);

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
	private Timer uiTimer;

	private volatile boolean running = true;

	// used when issuing seek commands
	private volatile int seekTime = -1;

	private AudioTest5() throws Exception
	{
		engine = new AudioEngine();
		engine.setAudioMonitor(waveformBuffer::append);
		bank = new SoundBank();
		player = new MseqPlayer(engine, bank);
		player.attach();

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
				uiTimer.stop();
				engine.setAudioMonitor(null);
				player.close();
				engine.shutdown();
				frame.dispose();
				Environment.exit();
			}
		});

		frame.setLayout(new MigLayout("fill, ins 16"));

		JButton playButton = new JButton("Test Instrument");
		playButton.addActionListener((e) -> {
			Voice testVoice = new Voice();
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
				seekTime = timeSlider.getValue();
			}
		});

		DefaultListModel<File> filesModel = new DefaultListModel<>();
		filesModel.addAll(mseqFiles);

		JList<File> mseqFileList = new JList<>();
		mseqFileList.setModel(filesModel);
		mseqFileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		mseqFileList.setVisibleRowCount(12);

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
				player.setMseq(mseq);
				timeSlider.setMaximum(player.getDuration());
				timeSlider.setValue(0);
			}
		});

		JButton pauseButton = new JButton("Pause");
		pauseButton.addActionListener((evt) -> {
			synchronized (threadLock) {
				boolean paused = player.isPaused();
				player.setPaused(!paused);
				pauseButton.setText(player.isPaused() ? "Play" : "Pause");
			}
		});

		WaveformPanel waveformPanel = new WaveformPanel();
		waveformPanel.setPreferredSize(new Dimension(400, 120));

		frame.add(playButton);
		frame.add(masterVolumeSlider, "grow, wrap");
		frame.add(fileListScroll, "span, grow, push, wrap");
		frame.add(waveformPanel, "span, growx, wrap");
		frame.add(pauseButton);
		frame.add(timeSlider, "grow");

		uiTimer = new Timer(33, (evt) -> {
			waveformPanel.updateSamples(waveformBuffer.snapshot());

			synchronized (threadLock) {
				ignoreSliderUpdate = true;
				timeSlider.setValue(player.getTime());
				ignoreSliderUpdate = false;
			}
		});
		uiTimer.start();

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
		double deltaTime = AudioEngine.FRAME_TIME;

		FrameLimiter limiter = new FrameLimiter();

		while (running) {
			long t0 = System.nanoTime();

			synchronized (threadLock) {
				if (seekTime >= 0) {
					player.seekTime(seekTime);
					seekTime = -1;
				}

				engine.renderFrame(deltaTime, false);

			}

			limiter.sync(AudioEngine.TARGET_FPS);

			deltaTime = (System.nanoTime() - t0) / 1e9;
		}
	}

	private static class WaveformBuffer
	{
		private final float[] samples;
		private int writePos;
		private int sampleCount;

		private WaveformBuffer(int capacity)
		{
			samples = new float[capacity];
		}

		private synchronized void append(float[] left, float[] right, int start, int end)
		{
			for (int i = start; i < end; i++) {
				samples[writePos] = (left[i] + right[i]) * 0.5f;
				writePos = (writePos + 1) % samples.length;
				if (sampleCount < samples.length)
					sampleCount++;
			}
		}

		private synchronized float[] snapshot()
		{
			float[] snapshot = new float[sampleCount];
			int readPos = (writePos - sampleCount + samples.length) % samples.length;
			for (int i = 0; i < sampleCount; i++)
				snapshot[i] = samples[(readPos + i) % samples.length];
			return snapshot;
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

			int w = getWidth();
			int h = getHeight();

			g.setColor(Color.BLACK);
			g.fillRect(0, 0, w, h);

			if (samples.length == 0 || w == 0)
				return;

			g.setColor(Color.GREEN);
			int midY = h / 2;

			for (int x = 0; x < w; x++) {
				int start = x * samples.length / w;
				int end = Math.max(start + 1, (x + 1) * samples.length / w);
				float min = 0.0f;
				float max = 0.0f;
				for (int i = start; i < end && i < samples.length; i++) {
					min = Math.min(min, samples[i]);
					max = Math.max(max, samples[i]);
				}
				min = Math.max(-1.0f, min);
				max = Math.min(1.0f, max);
				int y1 = midY - (int) (max * midY);
				int y2 = midY - (int) (min * midY);
				g.drawLine(x, y1, x, y2);
			}
		}
	}
}

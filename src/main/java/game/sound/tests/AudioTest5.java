package game.sound.tests;

import static app.Directories.MOD_AUDIO_MSEQ;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;

import app.Environment;
import game.sound.BankEditor.SoundBank;
import game.sound.engine.AudioEngine;
import game.sound.engine.Instrument;
import game.sound.engine.Voice;
import game.sound.mseq.Mseq;
import game.sound.mseq.MseqPlayer;
import net.miginfocom.swing.MigLayout;

public class AudioTest5
{
	private static final int TARGET_FPS = 60; // PM audio thread
	private static final long FRAME_TIME_MS = 1000 / TARGET_FPS;

	public static void main(String[] args) throws Exception
	{
		Environment.initialize();

		AudioTest5 test = new AudioTest5();

		SwingUtilities.invokeLater(() -> {
			JFrame frame = new JFrame("Audio Test");
			frame.setSize(400, 200);
			frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
			frame.addWindowListener(new WindowAdapter() {
				@Override
				public void windowClosing(WindowEvent e)
				{
					test.running = false;
					test.engine.shutdown();
					frame.dispose();
					Environment.exit();
				}
			});

			frame.setLayout(new MigLayout("fill, ins 16"));

			JButton playButton = new JButton("Play Sound");
			playButton.addActionListener((e) -> {
				test.testVoice.reset();
				test.testVoice.play();
			});

			JSlider masterVolumeSlider = new JSlider(0, 256, test.engine.getMasterVolume());
			masterVolumeSlider.setPaintTicks(true);
			masterVolumeSlider.setMajorTickSpacing(64);
			masterVolumeSlider.setMinorTickSpacing(16);
			masterVolumeSlider.addChangeListener((e) -> {
				test.engine.setMasterVolume(masterVolumeSlider.getValue());
			});

			frame.add(playButton);
			frame.add(masterVolumeSlider);

			frame.pack();
			frame.setLocationRelativeTo(null);
			frame.setVisible(true);

			new Thread(() -> {
				try {
					test.run();
				}
				catch (Exception e) {
					e.printStackTrace();
				}
			}).start();
		});
	}

	private AudioEngine engine;
	private SoundBank bank;
	private MseqPlayer player;

	private Instrument testInstrument;
	private Voice testVoice;

	private volatile boolean running = true;

	private AudioTest5() throws Exception
	{
		engine = new AudioEngine();
		bank = new SoundBank();
		player = new MseqPlayer(engine, bank);

		testInstrument = bank.getInstrument(0x30, 0).instrument();

		testVoice = new Voice();
		testVoice.setInstrument(testInstrument);
		engine.addVoice(testVoice);

		Mseq mseq = Mseq.load(MOD_AUDIO_MSEQ.getFile("DB_501.xml"));
		//Mseq mseq = Mseq.load(MOD_AUDIO_MSEQ.getFile("DC_502.xml"));
		player.setMseq(mseq);
	}

	public void run() throws InterruptedException
	{
		long prevTime = System.nanoTime();
		double time = 0.0f;
		double deltaTime = FRAME_TIME_MS / 1e6;

		while (running) {
			long curTime = System.nanoTime();
			deltaTime = (curTime - prevTime) / 1e9;
			prevTime = curTime;
			time += deltaTime;

			player.update();

			engine.renderFrame(deltaTime);

			long sleep = FRAME_TIME_MS - (long) (deltaTime / 1e6);
			if (sleep > 0)
				Thread.sleep(sleep);
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

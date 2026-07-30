package game.sound;

import static app.Directories.MOD_AUDIO;
import static app.Directories.MOD_AUDIO_BGM;
import static app.Directories.MOD_AUDIO_MSEQ;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import app.Environment;
import app.SwingUtils;
import app.input.IOUtils;
import common.FrameLimiter;
import game.sound.engine.AudioEngine;
import game.sound.engine.SoundBank;
import game.sound.mseq.Mseq;
import game.sound.mseq.MseqPlayer;
import game.sound.sfx.SfxArchive;
import game.sound.sfx.SfxArchive.Sound;
import game.sound.sfx.SfxPlayer;
import game.sound.sfx.SfxXml;
import net.miginfocom.swing.MigLayout;
import util.Logger;
import util.ui.FilteredListModel;

public class AudioBooth
{
	private enum PlaybackType
	{
		NONE,
		SFX,
		MSEQ
	}

	private final Object threadLock = new Object();

	public static void main(String[] args) throws Exception
	{
		Environment.initialize();
		new AudioBooth();
	}

	private final AudioEngine engine;
	private final SoundBank bank;
	private final MseqPlayer mseqPlayer;
	private final SfxPlayer sfxPlayer;

	private final JLabel statusLabel;
	private final JButton pauseButton;
	private final JSlider timeSlider;

	private boolean ignoreSliderUpdate = false;
	private volatile boolean running = true;
	private volatile int seekTime = -1;
	private PlaybackType playbackType = PlaybackType.NONE;

	private AudioBooth() throws Exception
	{
		engine = new AudioEngine();
		bank = new SoundBank();
		mseqPlayer = new MseqPlayer(engine, bank);
		sfxPlayer = new SfxPlayer(engine, bank);

		// required for radio songs, just keep this always loaded
		bank.installAuxBank("SPC3", 2);

		JFrame frame = new JFrame(Environment.decorateTitle("Audio Booth"));
		frame.setIconImage(Environment.getDefaultIconImage());
		frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		frame.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e)
			{
				close(frame);
			}
		});

		frame.setLayout(new MigLayout("fill, ins 12", "[][grow]", "[][grow][][]"));

		JSlider masterVolumeSlider = new JSlider(0, 256, engine.getMasterVolume());
		masterVolumeSlider.setPaintTicks(true);
		masterVolumeSlider.setMajorTickSpacing(64);
		masterVolumeSlider.setMinorTickSpacing(16);
		masterVolumeSlider.addChangeListener((e) -> {
			engine.setMasterVolume(masterVolumeSlider.getValue());
		});

		statusLabel = new JLabel("Select an audio asset.");

		timeSlider = new JSlider(0, 1, 0);
		timeSlider.setEnabled(false);
		timeSlider.addChangeListener((e) -> {
			if (!ignoreSliderUpdate)
				seekTime = timeSlider.getValue();
		});

		pauseButton = new JButton("Pause");
		pauseButton.setEnabled(false);
		pauseButton.addActionListener((e) -> {
			synchronized (threadLock) {
				boolean paused;
				if (playbackType == PlaybackType.SFX) {
					paused = sfxPlayer.getPaused();
					sfxPlayer.setPaused(!paused);
					pauseButton.setText(sfxPlayer.getPaused() ? "Play" : "Pause");
				}
				else if (playbackType == PlaybackType.MSEQ) {
					paused = mseqPlayer.getPaused();
					mseqPlayer.setPaused(!paused);
					pauseButton.setText(mseqPlayer.getPaused() ? "Play" : "Pause");
				}
			}
		});

		JTabbedPane assetTabs = new JTabbedPane();
		assetTabs.addTab("SFX", createSfxTab());
		assetTabs.addTab("MSEQ", createFileTab(
			IOUtils.getFilesWithExtension(MOD_AUDIO_MSEQ, "xml", false), this::selectMseq, "MSEQ"));
		assetTabs.addTab("BGM", createFileTab(
			IOUtils.getFilesWithExtension(MOD_AUDIO_BGM, "xml", false), this::selectBgm, "BGM"));

		frame.add(SwingUtils.getLabel("Volume:", 12));
		frame.add(masterVolumeSlider, "growx, wrap");
		frame.add(assetTabs, "span, grow, push, wrap");
		frame.add(pauseButton);
		frame.add(timeSlider, "growx, wrap");
		frame.add(statusLabel, "span, growx");

		frame.setMinimumSize(new Dimension(520, 360));
		frame.pack();
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);

		Thread audioThread = new Thread(() -> {
			try {
				run();
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			catch (Exception e) {
				Logger.logError("Audio Booth playback thread failed");
				Logger.printStackTrace(e);
			}
		}, "Audio Booth");
		audioThread.start();
	}

	private JPanel createSfxTab()
	{
		DefaultListModel<Sound> model = new DefaultListModel<>();
		File manifest = MOD_AUDIO.getFile(SfxXml.FN_SOUND_EFFECTS);

		if (manifest.isFile()) {
			try {
				SoundBankCatalog catalog = SoundBankCatalog.loadMod();
				SfxArchive archive = SfxXml.read(manifest.toPath(), catalog);
				sfxPlayer.setArchive(archive);
				model.addAll(archive.sounds.values());
			}
			catch (Exception e) {
				Logger.logError("Could not load SFX assets for Audio Booth");
				Logger.printStackTrace(e);
			}
		}

		JList<Sound> list = new JList<>(model);
		configureList(list);
		list.setCellRenderer(new DefaultListCellRenderer() {
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index,
				boolean isSelected, boolean cellHasFocus)
			{
				Component component = super.getListCellRendererComponent(
					list, value, index, isSelected, cellHasFocus);
				if (value instanceof Sound sound) {
					String suffix = sound.isEmpty() ? "  (empty)" : "";
					setText(String.format("%04X  %s%s", sound.id, sound.name, suffix));
				}
				return component;
			}
		});
		list.addListSelectionListener((e) -> {
			if (e.getValueIsAdjusting())
				return;
			selectSfx(list.getSelectedValue());
		});

		return createListPanel(list, model, "SFX", manifest, (sound) ->
			String.format("%04X %s %s", sound.id, sound.name, String.join(" ", sound.aliases)));
	}

	private JPanel createFileTab(Collection<File> files, Consumer<File> selectAsset, String assetType)
	{
		List<File> sortedFiles = new ArrayList<>(files);
		sortedFiles.sort(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));

		DefaultListModel<File> model = new DefaultListModel<>();
		model.addAll(sortedFiles);

		JList<File> list = new JList<>(model);
		configureList(list);
		list.setCellRenderer(new DefaultListCellRenderer() {
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index,
				boolean isSelected, boolean cellHasFocus)
			{
				Component component = super.getListCellRendererComponent(
					list, value, index, isSelected, cellHasFocus);
				if (value instanceof File file)
					setText(file.getName());
				return component;
			}
		});
		list.addListSelectionListener((e) -> {
			if (e.getValueIsAdjusting())
				return;
			File selected = list.getSelectedValue();
			if (selected != null)
				selectAsset.accept(selected);
		});

		File directory = assetType.equals("MSEQ") ? MOD_AUDIO_MSEQ.toFile() : MOD_AUDIO_BGM.toFile();
		return createListPanel(list, model, assetType, directory, File::getName);
	}

	private static void configureList(JList<?> list)
	{
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.setVisibleRowCount(16);
	}

	private static <T> JPanel createListPanel(JList<T> list, DefaultListModel<T> sourceModel,
		String assetType, File source, Function<T, String> filterText)
	{
		FilteredListModel<T> filteredModel = new FilteredListModel<>(sourceModel);
		list.setModel(filteredModel);

		JTextField filterField = new JTextField();
		filterField.setMargin(SwingUtils.TEXTBOX_INSETS);
		SwingUtils.addBorderPadding(filterField);

		JLabel countLabel = new JLabel();
		countLabel.setToolTipText(source.getAbsolutePath());
		setAssetCount(countLabel, sourceModel.size(), sourceModel.size(), assetType);

		Runnable updateFilter = () -> updateListFilter(
			filteredModel, filterField, countLabel, assetType, filterText);
		filterField.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void changedUpdate(DocumentEvent e)
			{
				updateFilter.run();
			}

			@Override
			public void insertUpdate(DocumentEvent e)
			{
				updateFilter.run();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				updateFilter.run();
			}
		});

		JScrollPane scrollPane = new JScrollPane(list);
		scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane.setWheelScrollingEnabled(true);
		scrollPane.setPreferredSize(new Dimension(520, 300));

		JPanel panel = new JPanel(new MigLayout("fill, ins 8", "[][grow]", "[][grow][]"));
		panel.add(SwingUtils.getLabel("Filter:", 12));
		panel.add(filterField, "growx, wrap");
		panel.add(scrollPane, "span, grow, push, wrap");
		panel.add(countLabel, "span, growx");
		return panel;
	}

	@SuppressWarnings("unchecked")
	private static <T> void updateListFilter(FilteredListModel<T> model, JTextField filterField,
		JLabel countLabel, String assetType, Function<T, String> filterText)
	{
		String filter = filterField.getText().toUpperCase(Locale.ROOT);
		model.setFilter((element) -> filterText.apply((T) element)
			.toUpperCase(Locale.ROOT).contains(filter));
		setAssetCount(countLabel, model.getSize(), model.getSource().getSize(), assetType);
	}

	private static void setAssetCount(JLabel label, int visible, int total, String assetType)
	{
		if (total == 0)
			label.setText("No " + assetType + " assets found.");
		else if (visible == total)
			label.setText(String.format("%d %s assets", total, assetType));
		else
			label.setText(String.format("%d of %d %s assets", visible, total, assetType));
	}

	private void selectSfx(Sound sound)
	{
		if (sound == null)
			return;

		int duration = 0;
		synchronized (threadLock) {
			mseqPlayer.stop();
			seekTime = -1;

			if (sound.isEmpty()) {
				sfxPlayer.stop();
				playbackType = PlaybackType.NONE;
			}
			else {
				sfxPlayer.play(sound.id);
				duration = sfxPlayer.getDuration();
				playbackType = PlaybackType.SFX;
			}
		}

		ignoreSliderUpdate = true;
		timeSlider.setMaximum(Math.max(1, duration));
		timeSlider.setValue(0);
		ignoreSliderUpdate = false;
		timeSlider.setEnabled(duration > 0);

		if (sound.isEmpty()) {
			pauseButton.setEnabled(false);
			statusLabel.setText(String.format("SFX %04X %s is an available empty slot.", sound.id, sound.name));
		}
		else {
			pauseButton.setText("Pause");
			pauseButton.setEnabled(true);
			statusLabel.setText(String.format("Playing SFX %04X %s.", sound.id, sound.name));
		}
	}

	private void selectMseq(File file)
	{
		try {
			Mseq mseq = Mseq.load(file);
			mseq.calculateTiming();

			synchronized (threadLock) {
				sfxPlayer.stop();
				timeSlider.setMaximum(Math.max(1, mseq.duration));
				timeSlider.setValue(0);
				seekTime = -1;
				mseqPlayer.setMseq(mseq);
				playbackType = PlaybackType.MSEQ;
			}

			pauseButton.setText("Pause");
			pauseButton.setEnabled(true);
			timeSlider.setEnabled(true);
			statusLabel.setText("Playing MSEQ " + file.getName());
		}
		catch (Exception e) {
			Logger.logfError("Could not load MSEQ asset %s", file.getName());
			Logger.printStackTrace(e);
			statusLabel.setText("Could not load MSEQ " + file.getName());
		}
	}

	private void selectBgm(File file)
	{
		synchronized (threadLock) {
			sfxPlayer.stop();
			mseqPlayer.stop();
			playbackType = PlaybackType.NONE;
			seekTime = -1;
		}
		pauseButton.setEnabled(false);
		timeSlider.setEnabled(false);
		statusLabel.setText("Selected BGM " + file.getName() + ". BGM playback is not implemented yet.");
	}

	private void close(JFrame frame)
	{
		running = false;
		synchronized (threadLock) {
			engine.shutdown();
		}
		frame.dispose();
		Environment.exit();
	}

	private void run() throws InterruptedException
	{
		double deltaTime = AudioEngine.FRAME_TIME;
		FrameLimiter limiter = new FrameLimiter();

		while (running) {
			long startTime = System.nanoTime();
			int playbackTime;

			synchronized (threadLock) {
				if (seekTime >= 0) {
					if (playbackType == PlaybackType.MSEQ)
						mseqPlayer.seekTime(seekTime);
					else if (playbackType == PlaybackType.SFX)
						sfxPlayer.seekTime(seekTime);
					seekTime = -1;
				}

				engine.renderFrame(deltaTime, false);
				if (playbackType == PlaybackType.MSEQ)
					playbackTime = mseqPlayer.getTime();
				else if (playbackType == PlaybackType.SFX)
					playbackTime = sfxPlayer.getTime();
				else
					playbackTime = 0;
			}

			updateTimeSlider(playbackTime);
			limiter.sync(AudioEngine.TARGET_FPS);
			deltaTime = (System.nanoTime() - startTime) / 1e9;
		}
	}

	private void updateTimeSlider(int playbackTime)
	{
		SwingUtilities.invokeLater(() -> {
			if (!running)
				return;
			ignoreSliderUpdate = true;
			timeSlider.setValue(playbackTime);
			ignoreSliderUpdate = false;
		});
	}
}

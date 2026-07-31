package game.sound;

import static app.Directories.MOD_AUDIO;
import static app.Directories.MOD_AUDIO_BGM;
import static app.Directories.MOD_AUDIO_MSEQ;
import static app.Directories.FN_AUDIO_AMBIENTS;
import static app.Directories.FN_AUDIO_SONGS;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.apache.commons.io.FilenameUtils;

import app.Environment;
import app.SwingUtils;
import app.input.IOUtils;
import common.FrameLimiter;
import game.sound.bgm.BgmPlayer;
import game.sound.bgm.Song;
import game.sound.bgm.SongKey;
import game.sound.engine.AudioEngine;
import game.sound.engine.SoundBank;
import game.sound.mseq.Mseq;
import game.sound.mseq.MseqPlayer;
import game.sound.sfx.SfxArchive;
import game.sound.sfx.SfxArchive.Command;
import game.sound.sfx.SfxArchive.Node;
import game.sound.sfx.SfxArchive.OneShot;
import game.sound.sfx.SfxArchive.Op;
import game.sound.sfx.SfxArchive.Sequence;
import game.sound.sfx.SfxArchive.Sound;
import game.sound.sfx.SfxArchive.Track;
import game.sound.sfx.SfxPlayer;
import game.sound.sfx.SfxXml;
import net.miginfocom.swing.MigLayout;
import util.Logger;
import util.ui.FilteredListModel;
import util.ui.ThemedIcon;
import util.xml.XmlWrapper.XmlReader;

public class AudioBooth
{
	private static final int VOLUME_SLIDER_MAX = 100;

	private enum PlaybackType
	{
		NONE,
		SFX,
		MSEQ,
		BGM
	}

	private enum ProximityAmount
	{
		NONE("None", 0),
		PARTIAL("Partial", 87),
		FULL("Full", 127);

		private final String name;
		private final int value;

		private ProximityAmount(String name, int value)
		{
			this.name = name;
			this.value = value;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	private final Object threadLock = new Object();

	public static void main(String[] args) throws Exception
	{
		Environment.initialize();

		CountDownLatch guiClosedSignal = new CountDownLatch(1);
		new AudioBooth(guiClosedSignal);
		guiClosedSignal.await();

		Environment.exit();
	}

	private final AudioEngine engine;
	private final CountDownLatch guiClosedSignal;
	public boolean exitToMainMenu;
	private SoundBank bank;
	private AudioExporter audioExporter;
	private MseqPlayer mseqPlayer;
	private SfxPlayer sfxPlayer;
	private BgmPlayer bgmPlayer;

	private final JLabel statusLabel;
	private final JLabel timeLabel;
	private final JButton pauseButton;
	private final JButton restartButton;
	private final JButton stopButton;
	private final JMenuItem reloadItem;
	private final JMenuItem exportItem;
	private final JSlider timeSlider;
	private final JSlider masterVolumeSlider;
	private final JButton muteButton;
	private final JTabbedPane assetTabs;
	private final JCheckBox alternativeSoundBox;
	private final JCheckBox alternativeVolumeBox;

	private boolean ignoreSliderUpdate = false;
	private volatile boolean running = true;
	private volatile int seekTime = -1;
	private int timelineDuration;
	private PlaybackType playbackType = PlaybackType.NONE;
	private PlaybackType selectedType = PlaybackType.NONE;
	private SfxArchive sfxArchive;
	private Sound selectedSfx;
	private Mseq selectedMseq;
	private File selectedMseqFile;
	private JList<Sound> sfxList;
	private JList<File> mseqList;
	private JList<File> bgmList;
	private JComboBox<Integer> bgmCompositionBox;
	private JComboBox<Integer> bgmProximityMixBox;
	private JComboBox<ProximityAmount> bgmProximityAmountBox;
	private JCheckBox bgmInstantBox;
	private Song selectedBgm;
	private File selectedBgmFile;
	private boolean updatingBgmControls;
	private boolean exporting;
	private int unmutedVolume = VOLUME_SLIDER_MAX;

	public AudioBooth(CountDownLatch guiClosedSignal) throws Exception
	{
		this.guiClosedSignal = guiClosedSignal;
		engine = new AudioEngine();
		bank = new SoundBank();
		audioExporter = new AudioExporter(bank);
		mseqPlayer = new MseqPlayer(engine, bank);
		sfxPlayer = new SfxPlayer(engine, bank);
		bgmPlayer = new BgmPlayer(engine, bank);

		// required for radio songs, just keep this always loaded
		bank.installAuxBank("SPC3", 2);

		JFrame frame = new JFrame(Environment.decorateTitle("Audio Booth"));
		frame.setIconImage(Environment.getDefaultIconImage());
		frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		frame.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e)
			{
				close(frame, false);
			}
		});

		frame.setLayout(new MigLayout("fill, ins 12", "[grow,fill]", "[grow][][]"));

		masterVolumeSlider = new JSlider(0, VOLUME_SLIDER_MAX,
			gainToVolumeSlider(engine.getMasterVolume()));
		if (masterVolumeSlider.getValue() > 0)
			unmutedVolume = masterVolumeSlider.getValue();

		muteButton = new JButton(ThemedIcon.VOLUME_UP_16);
		muteButton.setMargin(new Insets(0, 2, 0, 2));
		muteButton.addActionListener((e) -> toggleMute());
		masterVolumeSlider.addChangeListener((e) -> updateMasterVolume());
		updateMasterVolume();

		statusLabel = new JLabel("Select an audio asset.");
		statusLabel.setVerticalAlignment(SwingConstants.CENTER);
		timeLabel = new JLabel(formatTime(PlaybackType.NONE, 0) + " / " + formatTime(PlaybackType.NONE, 0));

		timeSlider = new JSlider(0, 1, 0);
		timeSlider.setEnabled(false);
		timeSlider.addChangeListener((e) -> {
			if (!ignoreSliderUpdate)
				seekTime = timeSlider.getValue();
		});

		Insets playbackButtonInsets = new Insets(0, 2, 0, 2);

		pauseButton = new JButton(ThemedIcon.PAUSE_24);
		pauseButton.setToolTipText("Pause");
		pauseButton.setMargin(playbackButtonInsets);
		pauseButton.setEnabled(false);
		pauseButton.addActionListener((e) -> {
			boolean restart = false;
			synchronized (threadLock) {
				if (!isPlaybackActive()) {
					restart = true;
				}
				else if (playbackType == PlaybackType.SFX) {
					boolean paused = sfxPlayer.getPaused();
					sfxPlayer.setPaused(!paused);
					setPlaybackPaused(sfxPlayer.getPaused());
				}
				else if (playbackType == PlaybackType.MSEQ) {
					boolean paused = mseqPlayer.getPaused();
					mseqPlayer.setPaused(!paused);
					setPlaybackPaused(mseqPlayer.getPaused());
				}
				else if (playbackType == PlaybackType.BGM) {
					boolean paused = bgmPlayer.getPaused();
					bgmPlayer.setPaused(!paused);
					setPlaybackPaused(bgmPlayer.getPaused());
				}
			}
			if (restart)
				restartPlayback();
		});

		restartButton = new JButton(ThemedIcon.REWIND_24);
		restartButton.setToolTipText("Restart");
		restartButton.setMargin(playbackButtonInsets);
		restartButton.setEnabled(false);
		restartButton.addActionListener((e) -> restartPlayback());

		stopButton = new JButton(ThemedIcon.STOP_24);
		stopButton.setToolTipText("Stop");
		stopButton.setMargin(playbackButtonInsets);
		stopButton.setEnabled(false);
		stopButton.addActionListener((e) -> stopPlayback());

		alternativeSoundBox = new JCheckBox("Alternative Sound");
		alternativeSoundBox.addActionListener((e) -> updateSfxPreview());

		alternativeVolumeBox = new JCheckBox("Alternative Volume");
		alternativeVolumeBox.addActionListener((e) -> updateSfxPreview());

		JMenuBar menuBar = new JMenuBar();
		JMenu fileMenu = new JMenu("File");
		JMenuItem switchToolsItem = new JMenuItem("Switch Tools");
		switchToolsItem.addActionListener((e) -> close(frame, true));
		JMenuItem exitItem = new JMenuItem("Exit");
		exitItem.addActionListener((e) -> close(frame, false));
		fileMenu.add(switchToolsItem);
		fileMenu.add(exitItem);
		menuBar.add(fileMenu);

		JMenu selectedMenu = new JMenu("Selected");
		reloadItem = new JMenuItem("Reload");
		reloadItem.setEnabled(false);
		reloadItem.addActionListener((e) -> reloadSelected());
		exportItem = new JMenuItem("Export");
		exportItem.setEnabled(false);
		exportItem.addActionListener((e) -> chooseExport());
		selectedMenu.add(reloadItem);
		selectedMenu.add(exportItem);
		menuBar.add(selectedMenu);
		frame.setJMenuBar(menuBar);

		assetTabs = new JTabbedPane();
		populateAssetTabs();

		JPanel playbackPanel = new JPanel(new MigLayout(
			"ins 0 8 0 8, fillx", "[][][][grow,fill][]", "[]"));
		playbackPanel.add(restartButton, "sg playbackButton, h 24!");
		playbackPanel.add(stopButton, "sg playbackButton, h 24!");
		playbackPanel.add(pauseButton, "sg playbackButton, h 24!");
		playbackPanel.add(timeSlider, "growx");
		playbackPanel.add(timeLabel);

		int volumeSliderWidth = timeLabel.getPreferredSize().width;
		JPanel footerPanel = new JPanel(new MigLayout(
			"ins 0 8 0 8, fillx", "[grow,fill][][" + volumeSliderWidth + "!,fill]", "[grow,fill]"));
		footerPanel.add(statusLabel, "grow");
		footerPanel.add(muteButton, "h 24!");
		footerPanel.add(masterVolumeSlider, "growx");

		frame.add(assetTabs, "grow, push, wrap");
		frame.add(playbackPanel, "growx, wrap");
		frame.add(footerPanel, "growx");

		frame.setMinimumSize(new Dimension(640, 360));
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

	private void populateAssetTabs() throws IOException
	{
		JPanel sfxTab = createSfxTab();
		JPanel mseqTab = createMseqTab();
		JPanel bgmTab = createBgmTab();

		assetTabs.removeAll();
		assetTabs.addTab("SFX", sfxTab);
		assetTabs.addTab("MSEQ", mseqTab);
		assetTabs.addTab("BGM", bgmTab);
	}

	private void reloadSelected()
	{
		if (exporting || selectedType == PlaybackType.NONE)
			return;

		PlaybackType type = selectedType;
		Sound soundSelection = selectedSfx;
		int soundID = soundSelection == null ? -1 : soundSelection.id;
		File mseqFile = selectedMseqFile;
		File bgmFile = selectedBgmFile;

		try {
			SoundBank newBank = new SoundBank();
			newBank.installAuxBank("SPC3", 2);

			synchronized (threadLock) {
				mseqPlayer.stop();
				sfxPlayer.stop();
				bgmPlayer.stop();
				engine.removeClient(mseqPlayer);
				engine.removeClient(sfxPlayer);
				engine.removeClient(bgmPlayer);

				bank = newBank;
				audioExporter = new AudioExporter(bank);
				mseqPlayer = new MseqPlayer(engine, bank);
				sfxPlayer = new SfxPlayer(engine, bank);
				bgmPlayer = new BgmPlayer(engine, bank);
				seekTime = -1;
				playbackType = PlaybackType.NONE;
			}

			sfxArchive = null;
			selectedSfx = null;
			selectedMseq = null;
			selectedMseqFile = null;
			selectedBgm = null;
			selectedBgmFile = null;
			selectedType = PlaybackType.NONE;
			populateAssetTabs();
			updateTimeline(0);
			setPlaybackPaused(false);
			updateTransportControls();

			if (type == PlaybackType.SFX && sfxArchive != null) {
				Sound sound = sfxArchive.sounds.get(soundID);
				if (sound != null) {
					assetTabs.setSelectedIndex(0);
					sfxList.setSelectedValue(sound, true);
				}
			}
			else if (type == PlaybackType.MSEQ && mseqFile != null && mseqFile.isFile()) {
				assetTabs.setSelectedIndex(1);
				mseqList.setSelectedValue(mseqFile, true);
			}
			else if (type == PlaybackType.BGM && bgmFile != null && bgmFile.isFile()) {
				assetTabs.setSelectedIndex(2);
				bgmList.setSelectedValue(bgmFile, true);
			}

			if (selectedType == PlaybackType.NONE)
				statusLabel.setText("The selected audio asset is no longer available.");
		}
		catch (Exception e) {
			Logger.logError("Could not reload the selected audio asset");
			Logger.printStackTrace(e);
			selectedType = type;
			selectedSfx = soundSelection;
			selectedMseqFile = mseqFile;
			selectedBgmFile = bgmFile;
			updateTimeline(0);
			setPlaybackPaused(false);
			updateTransportControls();
			statusLabel.setText("Could not reload the selected audio asset.");
		}
	}

	private JPanel createSfxTab()
	{
		DefaultListModel<Sound> model = new DefaultListModel<>();
		Map<Sound, String> sampleNames = new HashMap<>();
		File manifest = MOD_AUDIO.getFile(SfxXml.FN_SOUND_EFFECTS);

		if (manifest.isFile()) {
			try {
				SoundBankCatalog catalog = SoundBankCatalog.loadMod();
				sfxArchive = SfxXml.read(manifest.toPath(), catalog);
				sfxPlayer.setArchive(sfxArchive);
				for (Sound sound : sfxArchive.sounds.values())
					sampleNames.put(sound, getSfxSampleNames(sound, catalog));
				model.addAll(sfxArchive.sounds.values());
			}
			catch (Exception e) {
				Logger.logError("Could not load SFX assets for Audio Booth");
				Logger.printStackTrace(e);
			}
		}

		sfxList = new JList<>(model);
		configureList(sfxList);
		sfxList.setCellRenderer(new DefaultListCellRenderer() {
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index,
				boolean isSelected, boolean cellHasFocus)
			{
				Component component = super.getListCellRendererComponent(
					list, value, index, isSelected, cellHasFocus);
				if (value instanceof Sound sound) {
					String samples = sampleNames.get(sound);
					String suffix = "";
					if (sound.isEmpty())
						suffix = "  (empty)";
					else {
						if (sound.unused)
							suffix = "  (unused)";
						if (samples != null && !samples.isEmpty())
							suffix += "  (" + samples + ")";
					}
					setText(String.format("%04X  %s%s%s",
						sound.id, sound.name, suffix, triggerIndicator(sound)));
				}
				return component;
			}
		});
		sfxList.addListSelectionListener((e) -> {
			if (e.getValueIsAdjusting())
				return;
			selectSfx(sfxList.getSelectedValue());
		});

		JPanel listPanel = createListPanel(sfxList, model, "SFX", manifest, (sound) ->
			String.format("%04X %s %s %s %s", sound.id, sound.name,
				String.join(" ", sound.aliases), sound.unused ? "unused" : "", sampleNames.get(sound)));

		JPanel alternativePanel = new JPanel(new MigLayout("ins 0 8 8 8", "[][]", "[]"));
		alternativePanel.add(alternativeSoundBox);
		alternativePanel.add(alternativeVolumeBox);

		JPanel panel = new JPanel(new MigLayout("fill, ins 0", "[grow,fill]", "[grow][]"));
		panel.add(listPanel, "grow, push, wrap");
		panel.add(alternativePanel, "growx");
		return panel;
	}

	private static String getSfxSampleNames(Sound sound, SoundBankCatalog catalog)
	{
		LinkedHashSet<String> samples = new LinkedHashSet<>();
		collectSfxSampleNames(samples, sound.tracks, catalog);
		for (SfxArchive.SpawnedEffect effect : sound.spawnedEffects)
			collectSfxSampleNames(samples, effect.tracks, catalog);
		return String.join(", ", samples);
	}

	private static void collectSfxSampleNames(Collection<String> samples,
		Collection<Track> tracks, SoundBankCatalog catalog)
	{
		for (Track track : tracks) {
			if (track.definition instanceof OneShot oneShot) {
				samples.add(catalog.getWav(oneShot.bank, oneShot.patch).wav);
			}
			else if (track.definition instanceof Sequence sequence) {
				for (Node node : sequence.nodes) {
					if (node instanceof Command command && command.op == Op.SET_INSTRUMENT)
						samples.add(catalog.getWav(command.a, command.b).wav);
				}
			}
		}
	}

	private static String triggerIndicator(Sound sound)
	{
		String indicator = "";
		if (hasCommand(sound, Op.SET_ALTERNATIVE))
			indicator += " [S]";
		if (hasCommand(sound, Op.SET_ALTERNATIVE_VOLUME))
			indicator += " [V]";
		return indicator;
	}

	private static boolean hasCommand(Sound sound, Op op)
	{
		for (Track track : sound.tracks) {
			if (!(track.definition instanceof Sequence sequence))
				continue;
			for (Node node : sequence.nodes) {
				if (node instanceof Command command && command.op == op)
					return true;
			}
		}
		return false;
	}

	private JPanel createMseqTab() throws IOException
	{
		Map<String, String> names = loadMseqNames();
		List<File> sortedFiles = new ArrayList<>(
			IOUtils.getFilesWithExtension(MOD_AUDIO_MSEQ, "xml", false));
		sortedFiles.sort(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));

		DefaultListModel<File> model = new DefaultListModel<>();
		model.addAll(sortedFiles);

		mseqList = new JList<>(model);
		configureList(mseqList);
		mseqList.setCellRenderer(new DefaultListCellRenderer() {
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index,
				boolean isSelected, boolean cellHasFocus)
			{
				Component component = super.getListCellRendererComponent(
					list, value, index, isSelected, cellHasFocus);
				if (value instanceof File file)
					setText(formatMseqName(file, names));
				return component;
			}
		});
		mseqList.addListSelectionListener((e) -> {
			if (e.getValueIsAdjusting())
				return;
			File selected = mseqList.getSelectedValue();
			if (selected != null)
				selectMseq(selected);
		});

		return createListPanel(mseqList, model, "MSEQ", MOD_AUDIO_MSEQ.toFile(),
			(file) -> formatMseqName(file, names));
	}

	private JPanel createBgmTab() throws IOException
	{
		Map<String, String> names = loadBgmNames();
		Collection<File> files = IOUtils.getFilesWithExtension(MOD_AUDIO_BGM, "xml", false);
		Map<File, BgmSummary> summaries = new HashMap<>();
		for (File file : files)
			summaries.put(file, readBgmSummary(file));

		List<File> sortedFiles = new ArrayList<>(files);
		sortedFiles.sort(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));

		DefaultListModel<File> model = new DefaultListModel<>();
		model.addAll(sortedFiles);

		bgmList = new JList<>(model);
		configureList(bgmList);
		bgmList.setCellRenderer(new DefaultListCellRenderer() {
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index,
				boolean isSelected, boolean cellHasFocus)
			{
				Component component = super.getListCellRendererComponent(
					list, value, index, isSelected, cellHasFocus);
				if (value instanceof File file)
					setText(formatBgmName(file, summaries.get(file), names));
				return component;
			}
		});
		bgmList.addListSelectionListener((e) -> {
			if (e.getValueIsAdjusting())
				return;
			File selected = bgmList.getSelectedValue();
			if (selected != null)
				selectBgm(selected);
		});

		JPanel listPanel = createListPanel(
			bgmList, model, "BGM", MOD_AUDIO_BGM.toFile(),
			(file) -> formatBgmName(file, summaries.get(file), names));

		bgmCompositionBox = new JComboBox<>();
		bgmCompositionBox.setEnabled(false);
		SwingUtils.centerComboBoxText(bgmCompositionBox);
		SwingUtils.addBorderPadding(bgmCompositionBox);
		bgmCompositionBox.addActionListener((e) -> {
			if (updatingBgmControls || selectedBgm == null)
				return;
			Integer composition = (Integer) bgmCompositionBox.getSelectedItem();
			if (composition != null)
				playBgm(composition);
		});

		bgmProximityMixBox = new JComboBox<>();
		bgmProximityMixBox.setEnabled(false);
		bgmProximityMixBox.setToolTipText("Proximity-mix ID used by the song's branch tables.");
		SwingUtils.centerComboBoxText(bgmProximityMixBox);
		SwingUtils.addBorderPadding(bgmProximityMixBox);
		bgmProximityMixBox.addActionListener((e) -> updateBgmProximityMix());

		bgmProximityAmountBox = new JComboBox<>(ProximityAmount.values());
		bgmProximityAmountBox.setEnabled(false);
		bgmProximityAmountBox.setToolTipText("Proximity amounts: None 0, Partial 87, Full 127.");
		SwingUtils.centerComboBoxText(bgmProximityAmountBox);
		SwingUtils.addBorderPadding(bgmProximityAmountBox);
		bgmProximityAmountBox.addActionListener((e) -> updateBgmProximityMix());

		bgmInstantBox = new JCheckBox("Instant");
		bgmInstantBox.setEnabled(false);
		bgmInstantBox.setToolTipText("Bypass the engine's proximity-volume fades.");
		bgmInstantBox.addActionListener((e) -> updateBgmProximityMix());

		JPanel controls = new JPanel(new MigLayout(
			"ins 0 8 8 8, fillx", "[][72!][grow,fill][][72!][112!][]", "[]"));
		controls.add(SwingUtils.getLabel("Composition:", 12));
		controls.add(bgmCompositionBox, "w 72!");
		controls.add(SwingUtils.getLabel("Branch:", 12), "cell 3 0");
		controls.add(bgmProximityMixBox, "w 72!");
		controls.add(bgmProximityAmountBox, "w 112!");
		controls.add(bgmInstantBox);

		JPanel panel = new JPanel(new MigLayout("fill, ins 0", "[grow,fill]", "[grow][]"));
		panel.add(listPanel, "grow, push, wrap");
		panel.add(controls, "growx");
		return panel;
	}

	private static BgmSummary readBgmSummary(File file)
	{
		try {
			XmlReader xmr = new XmlReader(file);
			int compositions = xmr.getTags(
				xmr.getUniqueRequiredTag(xmr.getRootElement(), SongKey.TAG_COMP_LIST),
				SongKey.TAG_COMPOSITION).size();
			boolean hasProximityMixes = xmr.readInt(
				xmr.getRootElement(), SongKey.ATTR_BRANCHES) > 1;
			return new BgmSummary(compositions, hasProximityMixes);
		}
		catch (Exception e) {
			Logger.logfWarning("Could not read BGM summary from asset %s", file.getName());
			return new BgmSummary(0, false);
		}
	}

	private static Map<String, String> loadMseqNames()
	{
		File catalog = MOD_AUDIO.getFile(FN_AUDIO_AMBIENTS);
		try {
			return AudioCatalog.readMseqNames(catalog, MOD_AUDIO.getFile(FN_AUDIO_SONGS));
		}
		catch (Exception e) {
			Logger.logError("Could not read MSEQ names from " + catalog.getName());
			Logger.printStackTrace(e);
			return new HashMap<>();
		}
	}

	private static Map<String, String> loadBgmNames()
	{
		File catalog = MOD_AUDIO.getFile(FN_AUDIO_SONGS);
		try {
			return AudioCatalog.readSongNames(catalog);
		}
		catch (Exception e) {
			Logger.logError("Could not read BGM names from " + catalog.getName());
			Logger.printStackTrace(e);
			return new HashMap<>();
		}
	}

	private static String formatMseqName(File file, Map<String, String> names)
	{
		String name = FilenameUtils.getBaseName(file.getName());
		String canonicalName = AudioCatalog.getName(names, file.getName());
		if (canonicalName != null)
			name += "  " + canonicalName;
		return name;
	}

	private static String formatBgmName(File file, BgmSummary summary,
		Map<String, String> names)
	{
		String name = FilenameUtils.getBaseName(file.getName());
		String canonicalName = AudioCatalog.getName(names, file.getName());
		if (canonicalName != null)
			name += "  " + canonicalName;
		name += " [" + summary.compositions + "]";
		if (summary.hasProximityMixes)
			name += " [P]";
		return name;
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
		countLabel.setHorizontalAlignment(SwingConstants.RIGHT);
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

		JPanel filterControls = new JPanel(new MigLayout("ins 0, fillx", "[][grow,fill]", "[]"));
		filterControls.add(SwingUtils.getLabel("Filter:", 12));
		filterControls.add(filterField, "growx");

		JPanel filterPanel = new JPanel(new MigLayout("ins 0, fillx", "[grow 2,fill][grow 1,fill]", "[]"));
		filterPanel.add(filterControls, "growx");
		filterPanel.add(countLabel, "growx");

		JPanel panel = new JPanel(new MigLayout("fill, ins 8", "[grow,fill]", "[][grow]"));
		panel.add(filterPanel, "growx, wrap");
		panel.add(scrollPane, "grow, push");
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
			label.setText("No assets found");
		else if (visible == total)
			label.setText(String.format("%d assets", total));
		else
			label.setText(String.format("%d of %d assets", visible, total));
	}

	private void selectSfx(Sound sound)
	{
		if (sound == null)
			return;

		selectedType = PlaybackType.SFX;
		selectedSfx = sound;
		int duration = 0;
		synchronized (threadLock) {
			mseqPlayer.stop();
			bgmPlayer.stop();
			seekTime = -1;

			if (sound.isEmpty()) {
				sfxPlayer.stop();
				playbackType = PlaybackType.NONE;
			}
			else {
				if (alternativeSoundBox.isSelected() && hasCommand(sound, Op.SET_ALTERNATIVE))
					sfxPlayer.playAlternativeSound(sound.id);
				else if (alternativeVolumeBox.isSelected()
					&& hasCommand(sound, Op.SET_ALTERNATIVE_VOLUME))
					sfxPlayer.playAlternativeVolume(sound.id);
				else
					sfxPlayer.play(sound.id);
				duration = sfxPlayer.getDuration();
				playbackType = PlaybackType.SFX;
			}
		}

		updateTimeline(duration);

		if (sound.isEmpty()) {
			statusLabel.setText(String.format("SFX %04X %s is an available empty slot.", sound.id, sound.name));
		}
		else {
			setPlaybackPaused(false);
			statusLabel.setText(String.format("Playing SFX %04X %s.", sound.id, sound.name));
		}
		updateTransportControls();
	}

	private void selectMseq(File file)
	{
		selectedType = PlaybackType.MSEQ;
		selectedMseqFile = file;
		try {
			selectedMseq = Mseq.load(file);
			selectedMseq.calculateTiming();
			playMseq();
		}
		catch (Exception e) {
			Logger.logfError("Could not load MSEQ asset %s", file.getName());
			Logger.printStackTrace(e);
			stopFailedSelection();
			statusLabel.setText("Could not load MSEQ " + file.getName());
		}
	}

	private void playMseq()
	{
		if (selectedMseq == null)
			return;

		synchronized (threadLock) {
			sfxPlayer.stop();
			bgmPlayer.stop();
			seekTime = -1;
			mseqPlayer.setMseq(selectedMseq);
			playbackType = PlaybackType.MSEQ;
		}

		updateTimeline(selectedMseq.duration);
		setPlaybackPaused(false);
		statusLabel.setText("Playing MSEQ " + selectedMseqFile.getName());
		updateTransportControls();
	}

	private void selectBgm(File file)
	{
		selectedType = PlaybackType.BGM;
		selectedBgmFile = file;
		try {
			SoundBankCatalog catalog = SoundBankCatalog.loadMod();
			String bgmFilename = FilenameUtils.getBaseName(file.getName()) + ".bgm";
			SoundBankCatalog songCatalog = catalog.withSongBanks(
				MOD_AUDIO.getFile(FN_AUDIO_SONGS), bgmFilename);
			Song song = new Song();
			song.setSoundBankCatalog(songCatalog);
			XmlReader xmr = new XmlReader(file);
			song.fromXML(xmr, xmr.getRootElement());

			selectedBgm = song;
			updateBgmControls(song);
			Integer composition = (Integer) bgmCompositionBox.getSelectedItem();
			playBgm(composition == null ? 0 : composition);
		}
		catch (Exception e) {
			Logger.logfError("Could not load BGM asset %s", file.getName());
			Logger.printStackTrace(e);
			stopFailedSelection();
			statusLabel.setText("Could not load BGM " + file.getName());
		}
	}

	private void stopFailedSelection()
	{
		synchronized (threadLock) {
			sfxPlayer.stop();
			mseqPlayer.stop();
			bgmPlayer.stop();
			seekTime = -1;
			playbackType = PlaybackType.NONE;
		}
		updateTimeline(0);
		setPlaybackPaused(false);
		updateTransportControls();
	}

	private void updateBgmControls(Song song)
	{
		updatingBgmControls = true;
		bgmCompositionBox.removeAllItems();
		for (int i = 0; i < 4; i++) {
			if (song.getComposition(i) != null)
				bgmCompositionBox.addItem(i);
		}
		bgmCompositionBox.setEnabled(bgmCompositionBox.getItemCount() > 1);

		bgmProximityMixBox.removeAllItems();
		for (int i = 0; i < Math.max(1, song.branchOptions); i++)
			bgmProximityMixBox.addItem(i);
		boolean hasProximityMixes = bgmProximityMixBox.getItemCount() > 1;
		bgmProximityMixBox.setEnabled(hasProximityMixes);
		bgmProximityAmountBox.setEnabled(hasProximityMixes);
		bgmInstantBox.setEnabled(hasProximityMixes);
		bgmProximityMixBox.setSelectedIndex(0);
		bgmProximityAmountBox.setSelectedItem(ProximityAmount.FULL);
		bgmInstantBox.setSelected(false);
		updatingBgmControls = false;
	}

	private void updateBgmProximityMix()
	{
		if (updatingBgmControls || selectedBgm == null)
			return;

		Integer mixID = (Integer) bgmProximityMixBox.getSelectedItem();
		ProximityAmount amount = (ProximityAmount) bgmProximityAmountBox.getSelectedItem();
		if (mixID == null || amount == null)
			return;
		synchronized (threadLock) {
			bgmPlayer.setProximityMix(mixID, amount.value, bgmInstantBox.isSelected());
		}
	}

	private void playBgm(int composition)
	{
		if (selectedBgm == null)
			return;
		try {
			synchronized (threadLock) {
				sfxPlayer.stop();
				mseqPlayer.stop();
				seekTime = -1;
				bgmPlayer.play(selectedBgm, composition);
				Integer mixID = (Integer) bgmProximityMixBox.getSelectedItem();
				ProximityAmount amount = (ProximityAmount) bgmProximityAmountBox.getSelectedItem();
				if (mixID != null && amount != null)
					bgmPlayer.setProximityMix(mixID, amount.value, bgmInstantBox.isSelected());
				playbackType = bgmPlayer.isPlaying() ? PlaybackType.BGM : PlaybackType.NONE;
				updateTimeline(bgmPlayer.getDuration());
			}
			if (playbackType == PlaybackType.BGM) {
				setPlaybackPaused(false);
				statusLabel.setText("Playing BGM " + selectedBgmFile.getName());
			}
			else {
				statusLabel.setText("BGM " + selectedBgmFile.getName()
					+ " has an empty composition at index " + composition + ".");
			}
			updateTransportControls();
		}
		catch (Exception e) {
			Logger.logfError("Could not play BGM asset %s", selectedBgmFile.getName());
			Logger.printStackTrace(e);
			playbackType = PlaybackType.NONE;
			timeSlider.setEnabled(false);
			statusLabel.setText("Could not play BGM " + selectedBgmFile.getName());
			updateTransportControls();
		}
	}

	private void updateTimeline(int duration)
	{
		timelineDuration = duration;
		ignoreSliderUpdate = true;
		timeSlider.setMaximum(Math.max(1, duration));
		timeSlider.setValue(0);
		ignoreSliderUpdate = false;
		timeSlider.setEnabled(duration > 0);
		updateTimeLabel(0);
	}

	private void updateTransportControls()
	{
		boolean hasAsset = playbackType != PlaybackType.NONE;
		restartButton.setEnabled(hasAsset);
		pauseButton.setEnabled(hasAsset);
		stopButton.setEnabled(isPlaybackActive());
		reloadItem.setEnabled(selectedType != PlaybackType.NONE && !exporting);
		exportItem.setEnabled(hasAsset && !exporting);

		boolean hasAlternativeSound = selectedSfx != null
			&& hasCommand(selectedSfx, Op.SET_ALTERNATIVE);
		boolean hasAlternativeVolume = selectedSfx != null
			&& hasCommand(selectedSfx, Op.SET_ALTERNATIVE_VOLUME);
		alternativeSoundBox.setForeground(UIManager.getColor(
			hasAlternativeSound ? "CheckBox.foreground" : "Label.disabledForeground"));
		alternativeVolumeBox.setForeground(UIManager.getColor(
			hasAlternativeVolume ? "CheckBox.foreground" : "Label.disabledForeground"));
		alternativeSoundBox.setToolTipText(hasAlternativeSound
			? "Preview this sound's alternative program."
			: "This sound has no alternative program; the preference will apply to other SFX.");
		alternativeVolumeBox.setToolTipText(hasAlternativeVolume
			? "Preview this sound using its alternative volume."
			: "This sound has no alternative volume; the preference will apply to other SFX.");
	}

	private void updateSfxPreview()
	{
		if (playbackType == PlaybackType.SFX && selectedSfx != null)
			selectSfx(selectedSfx);
		else
			updateTransportControls();
	}

	private boolean isPlaybackActive()
	{
		if (playbackType == PlaybackType.SFX)
			return sfxPlayer.isPlaying();
		if (playbackType == PlaybackType.MSEQ)
			return mseqPlayer.isPlaying();
		if (playbackType == PlaybackType.BGM)
			return bgmPlayer.isPlaying();
		return false;
	}

	private void setPlaybackPaused(boolean paused)
	{
		pauseButton.setIcon(paused ? ThemedIcon.PLAY_24 : ThemedIcon.PAUSE_24);
		pauseButton.setToolTipText(paused ? "Play" : "Pause");
	}

	private void toggleMute()
	{
		if (masterVolumeSlider.getValue() == 0)
			masterVolumeSlider.setValue(unmutedVolume);
		else {
			unmutedVolume = masterVolumeSlider.getValue();
			masterVolumeSlider.setValue(0);
		}
	}

	private void updateMasterVolume()
	{
		int volume = masterVolumeSlider.getValue();
		engine.setMasterVolume(volumeSliderToGain(volume));
		if (volume == 0) {
			muteButton.setIcon(ThemedIcon.VOLUME_OFF_16);
			muteButton.setToolTipText("Unmute");
		}
		else {
			unmutedVolume = volume;
			muteButton.setIcon(ThemedIcon.VOLUME_UP_16);
			muteButton.setToolTipText("Mute");
		}
	}

	private void stopPlayback()
	{
		synchronized (threadLock) {
			if (playbackType == PlaybackType.SFX)
				sfxPlayer.stop();
			else if (playbackType == PlaybackType.MSEQ)
				mseqPlayer.stop();
			else if (playbackType == PlaybackType.BGM)
				bgmPlayer.stop();
			seekTime = -1;
		}

		ignoreSliderUpdate = true;
		timeSlider.setValue(0);
		ignoreSliderUpdate = false;
		timeSlider.setEnabled(false);
		updateTimeLabel(0);
		setPlaybackPaused(true);
		updateTransportControls();
		statusLabel.setText("Playback stopped.");
	}

	private void restartPlayback()
	{
		if (playbackType == PlaybackType.SFX) {
			selectSfx(selectedSfx);
		}
		else if (playbackType == PlaybackType.MSEQ) {
			playMseq();
		}
		else if (playbackType == PlaybackType.BGM) {
			Integer composition = (Integer) bgmCompositionBox.getSelectedItem();
			playBgm(composition == null ? 0 : composition);
		}
	}

	private void updateTimeLabel(int playbackTime)
	{
		timeLabel.setText(formatTime(playbackType, playbackTime)
			+ " / " + formatTime(playbackType, timelineDuration));
	}

	private static String formatTime(PlaybackType type, int time)
	{
		long samplesPerTick = AudioEngine.FRAME_SAMPLES;
		if (type == PlaybackType.MSEQ)
			samplesPerTick = MseqPlayer.SAMPLES_PER_TICK;

		long milliseconds = Math.round(time * samplesPerTick * 1000.0 / AudioEngine.OUTPUT_RATE);
		long minutes = milliseconds / 60000;
		long seconds = (milliseconds / 1000) % 60;
		long millis = milliseconds % 1000;
		return String.format("%d:%02d.%03d", minutes, seconds, millis);
	}

	private static int gainToVolumeSlider(int gain)
	{
		double ratio = Math.max(0.0, Math.min(1.0,
			gain / (double) AudioEngine.MAX_MASTER_VOLUME));
		return (int) Math.round(Math.sqrt(ratio) * VOLUME_SLIDER_MAX);
	}

	private static int volumeSliderToGain(int volume)
	{
		double ratio = volume / (double) VOLUME_SLIDER_MAX;
		return (int) Math.round(ratio * ratio * AudioEngine.MAX_MASTER_VOLUME);
	}

	private void chooseExport()
	{
		if (playbackType == PlaybackType.NONE)
			return;

		int composition = 0;
		int proximityMixID = 0;
		int proximityMixVolume = 0;
		boolean proximityMixInstant = false;
		if (playbackType == PlaybackType.BGM) {
			Integer selectedComposition = (Integer) bgmCompositionBox.getSelectedItem();
			Integer selectedMix = (Integer) bgmProximityMixBox.getSelectedItem();
			if (selectedComposition != null)
				composition = selectedComposition;
			if (selectedMix != null)
				proximityMixID = selectedMix;
			ProximityAmount amount = (ProximityAmount) bgmProximityAmountBox.getSelectedItem();
			if (amount != null)
				proximityMixVolume = amount.value;
			proximityMixInstant = bgmInstantBox.isSelected();
		}

		int loopRepetitions = 0;
		if (hasInfiniteLoop(playbackType, composition)) {
			JSpinner spinner = new JSpinner(new SpinnerNumberModel(2, 1, 100, 1));
			SwingUtils.centerSpinnerText(spinner);
			JPanel panel = new JPanel(new MigLayout("ins 0", "[][grow]", "[]"));
			panel.add(SwingUtils.getLabel("Loop repetitions:", 12));
			panel.add(spinner, "growx");
			int choice = JOptionPane.showConfirmDialog(assetTabs, panel,
				"Export Looping Audio", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
			if (choice != JOptionPane.OK_OPTION)
				return;
			loopRepetitions = (Integer) spinner.getValue();
		}

		File source = getSelectedSourceFile();
		File defaultFile = new File(source.getParentFile(),
			getExportName(composition, proximityMixID, proximityMixVolume));
		JFileChooser chooser = new JFileChooser(source.getParentFile());
		chooser.setDialogTitle("Export Audio as WAV");
		chooser.setFileFilter(new FileNameExtensionFilter("WAV audio (*.wav)", "wav"));
		chooser.setSelectedFile(defaultFile);
		if (chooser.showSaveDialog(assetTabs) != JFileChooser.APPROVE_OPTION)
			return;

		File outputFile = chooser.getSelectedFile();
		if (!outputFile.getName().toLowerCase(Locale.ROOT).endsWith(".wav"))
			outputFile = new File(outputFile.getParentFile(), outputFile.getName() + ".wav");
		if (outputFile.isFile()) {
			int choice = SwingUtils.getConfirmDialog()
				.setParent(assetTabs)
				.setTitle("Replace WAV File")
				.setMessage(outputFile.getName() + " already exists. Replace it?")
				.setOptionsType(JOptionPane.YES_NO_OPTION)
				.choose();
			if (choice != JOptionPane.YES_OPTION)
				return;
		}

		ExportRequest request = new ExportRequest(playbackType, outputFile, engine.getMasterVolume(),
			loopRepetitions, selectedSfx, sfxArchive, selectedMseq, selectedBgm, composition,
			proximityMixID, proximityMixVolume, proximityMixInstant);
		startExport(request);
	}

	private File getSelectedSourceFile()
	{
		if (playbackType == PlaybackType.MSEQ)
			return selectedMseqFile;
		if (playbackType == PlaybackType.BGM)
			return selectedBgmFile;
		return MOD_AUDIO.getFile(SfxXml.FN_SOUND_EFFECTS);
	}

	private String getExportName(int composition, int proximityMixID, int proximityMixVolume)
	{
		if (playbackType == PlaybackType.SFX)
			return selectedSfx.name + ".wav";
		if (playbackType == PlaybackType.MSEQ)
			return FilenameUtils.getBaseName(selectedMseqFile.getName()) + ".wav";

		String name = FilenameUtils.getBaseName(selectedBgmFile.getName()) + "_Comp" + composition;
		if (proximityMixID != 0 || proximityMixVolume != 0)
			name += "_Mix" + proximityMixID + "_" + proximityMixVolume;
		return name + ".wav";
	}

	private boolean hasInfiniteLoop(PlaybackType type, int compositionIndex)
	{
		if (type == PlaybackType.SFX)
			return AudioExporter.hasInfiniteLoop(selectedSfx);
		if (type == PlaybackType.MSEQ)
			return AudioExporter.hasInfiniteLoop(selectedMseq);
		if (type == PlaybackType.BGM)
			return AudioExporter.hasInfiniteLoop(selectedBgm, compositionIndex);
		return false;
	}

	private void startExport(ExportRequest request)
	{
		exporting = true;
		updateTransportControls();
		statusLabel.setText("Exporting " + request.outputFile.getName() + "...");

		SwingWorker<AudioExporter.Result, Void> worker = new SwingWorker<>() {
			@Override
			protected AudioExporter.Result doInBackground() throws Exception
			{
				return renderExport(request);
			}

			@Override
			protected void done()
			{
				exporting = false;
				updateTransportControls();
				try {
					AudioExporter.Result result = get();
					if (result.truncated()) {
						statusLabel.setText("Export reached the safety limit: "
							+ request.outputFile.getName());
					}
					else {
						statusLabel.setText("Exported " + request.outputFile.getName()
							+ " (" + formatSampleTime(result.samples()) + ").");
					}
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				catch (ExecutionException e) {
					Logger.logfError("Could not export WAV file %s", request.outputFile.getName());
					Logger.printStackTrace(e.getCause());
					statusLabel.setText("Could not export " + request.outputFile.getName());
				}
			}
		};
		worker.execute();
	}

	private AudioExporter.Result renderExport(ExportRequest request) throws Exception
	{
		if (request.type == PlaybackType.SFX) {
			return audioExporter.exportSfx(request.outputFile, request.volume,
				request.loopRepetitions, request.archive, request.sound);
		}
		if (request.type == PlaybackType.MSEQ) {
			return audioExporter.exportMseq(request.outputFile, request.volume,
				request.loopRepetitions, request.mseq);
		}
		if (request.type == PlaybackType.BGM) {
			return audioExporter.exportBgm(request.outputFile, request.volume,
				request.loopRepetitions, request.song, request.composition,
				request.proximityMixID, request.proximityMixVolume, request.proximityMixInstant);
		}
		throw new IllegalStateException("No audio asset is selected");
	}

	private static String formatSampleTime(long samples)
	{
		long milliseconds = Math.round(samples * 1000.0 / AudioEngine.OUTPUT_RATE);
		long minutes = milliseconds / 60000;
		long seconds = (milliseconds / 1000) % 60;
		long millis = milliseconds % 1000;
		return String.format("%d:%02d.%03d", minutes, seconds, millis);
	}

	private record ExportRequest(
		PlaybackType type,
		File outputFile,
		int volume,
		int loopRepetitions,
		Sound sound,
		SfxArchive archive,
		Mseq mseq,
		Song song,
		int composition,
		int proximityMixID,
		int proximityMixVolume,
		boolean proximityMixInstant)
	{}

	private record BgmSummary(int compositions, boolean hasProximityMixes)
	{}

	private void close(JFrame frame, boolean returnToMainMenu)
	{
		if (!running)
			return;

		running = false;
		synchronized (threadLock) {
			engine.shutdown();
		}
		exitToMainMenu = returnToMainMenu;
		frame.dispose();
		guiClosedSignal.countDown();
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
					else if (playbackType == PlaybackType.BGM)
						bgmPlayer.seekTime(seekTime);
					seekTime = -1;
				}

				engine.renderFrame(deltaTime, false);
				if (playbackType == PlaybackType.MSEQ)
					playbackTime = mseqPlayer.getTime();
				else if (playbackType == PlaybackType.SFX)
					playbackTime = sfxPlayer.getTime();
				else if (playbackType == PlaybackType.BGM)
					playbackTime = bgmPlayer.getTime();
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
			updateTimeLabel(playbackTime);
		});
	}
}

package game.sound.booth;

import static app.Directories.FN_AUDIO_SONGS;
import static app.Directories.MOD_AUDIO;
import static app.Directories.MOD_AUDIO_BGM;

import java.awt.Component;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.JPanel;

import org.apache.commons.io.FilenameUtils;

import app.SwingUtils;
import app.input.IOUtils;
import game.sound.AudioCatalog;
import game.sound.AudioExporter;
import game.sound.SoundBankCatalog;
import game.sound.bgm.BgmPlayer;
import game.sound.bgm.Song;
import game.sound.bgm.SongKey;
import game.sound.engine.AudioEngine;
import game.sound.engine.SoundBank;
import net.miginfocom.swing.MigLayout;
import util.Logger;
import util.xml.XmlWrapper.XmlReader;

final class BgmTab extends AudioBoothTab
{
	private enum ProximityAmount
	{
		NONE ("None", 0),
		PARTIAL ("Partial", 87),
		FULL ("Full", 127);

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

	private final BgmPlayer player;
	private final JList<File> fileList;
	private final JComboBox<Integer> compositionBox;
	private final JComboBox<Integer> proximityMixBox;
	private final JComboBox<ProximityAmount> proximityAmountBox;
	private final JCheckBox instantBox;

	private File selectedFile;
	private Song selectedSong;
	private boolean updatingControls;
	private boolean suppressEvents;

	BgmTab(AudioBooth booth, AudioEngine engine, SoundBank bank) throws IOException
	{
		super(booth, "BGM", new BgmPlayer(engine, bank));
		player = (BgmPlayer) getSession();

		Map<String, String> names = loadNames();
		Collection<File> files = IOUtils.getFilesWithExtension(MOD_AUDIO_BGM, "xml", false);
		Map<File, BgmSummary> summaries = new HashMap<>();
		for (File file : files)
			summaries.put(file, readSummary(file));

		List<File> sortedFiles = new ArrayList<>(files);
		sortedFiles.sort(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));

		DefaultListModel<File> model = new DefaultListModel<>();
		model.addAll(sortedFiles);

		fileList = new JList<>(model);
		configureList(fileList);
		fileList.setCellRenderer(new DefaultListCellRenderer() {
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index,
				boolean isSelected, boolean cellHasFocus)
			{
				Component component = super.getListCellRendererComponent(
					list, value, index, isSelected, cellHasFocus);
				if (value instanceof File file)
					setText(formatName(file, summaries.get(file), names));
				return component;
			}
		});
		fileList.addListSelectionListener((e) -> {
			if (suppressEvents || e.getValueIsAdjusting())
				return;
			File selected = fileList.getSelectedValue();
			if (selected != null)
				selectBgm(selected);
		});

		JPanel listPanel = createListPanel(fileList, model, MOD_AUDIO_BGM.toFile(),
			(file) -> formatName(file, summaries.get(file), names));

		compositionBox = new JComboBox<>();
		compositionBox.setEnabled(false);
		SwingUtils.centerComboBoxText(compositionBox);
		SwingUtils.addBorderPadding(compositionBox);
		compositionBox.addActionListener((e) -> {
			if (updatingControls || selectedSong == null)
				return;
			Integer composition = (Integer) compositionBox.getSelectedItem();
			if (composition != null)
				playBgm(composition);
		});

		proximityMixBox = new JComboBox<>();
		proximityMixBox.setEnabled(false);
		proximityMixBox.setToolTipText("Proximity-mix ID used by the song's branch tables.");
		proximityMixBox.setRenderer(new DefaultListCellRenderer() {
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index,
				boolean isSelected, boolean cellHasFocus)
			{
				Component component = super.getListCellRendererComponent(
					list, value, index, isSelected, cellHasFocus);
				if (value instanceof Integer mixID)
					setText(formatBranchName(mixID));
				return component;
			}
		});
		SwingUtils.centerComboBoxText(proximityMixBox);
		SwingUtils.addBorderPadding(proximityMixBox);
		proximityMixBox.addActionListener((e) -> updateProximityMix());

		proximityAmountBox = new JComboBox<>(ProximityAmount.values());
		proximityAmountBox.setEnabled(false);
		proximityAmountBox.setToolTipText("Proximity amounts: None 0, Partial 87, Full 127.");
		SwingUtils.centerComboBoxText(proximityAmountBox);
		SwingUtils.addBorderPadding(proximityAmountBox);
		proximityAmountBox.addActionListener((e) -> updateProximityMix());

		instantBox = new JCheckBox("Instant");
		instantBox.setEnabled(false);
		instantBox.setToolTipText("Bypass the engine's proximity-volume fades.");
		instantBox.addActionListener((e) -> updateProximityMix());

		JPanel controls = new JPanel(new MigLayout(
			"ins 0 8 8 8, fillx", "[][72!][grow,fill][][128!][112!][]", "[]"));
		controls.add(SwingUtils.getLabel("Composition:", 12));
		controls.add(compositionBox, "w 72!");
		controls.add(SwingUtils.getLabel("Branch:", 12), "cell 3 0");
		controls.add(proximityMixBox, "w 128!");
		controls.add(proximityAmountBox, "w 112!");
		controls.add(instantBox);

		setLayout(new MigLayout("fill, ins 0", "[grow,fill]", "[grow][]"));
		add(listPanel, "grow, push, wrap");
		add(controls, "growx");
	}

	private void selectBgm(File file)
	{
		selectedFile = file;
		try {
			selectedSong = loadBgm(file);
			updateControls(selectedSong);
			Integer composition = (Integer) compositionBox.getSelectedItem();
			playBgm(composition == null ? 0 : composition);
		}
		catch (Exception e) {
			Logger.logfError("Could not load BGM asset %s", file.getName());
			Logger.printStackTrace(e);
			selectedSong = null;
			booth.selectWithoutPlayback(this);
			booth.setStatus("Could not load " + file.getName());
		}
	}

	private void playBgm(int composition)
	{
		if (selectedSong == null || selectedFile == null)
			return;
		try {
			boolean playing = booth.startPlayback(this, player, () -> {
				player.play(selectedSong, composition);
				Integer mixID = (Integer) proximityMixBox.getSelectedItem();
				ProximityAmount amount = (ProximityAmount) proximityAmountBox.getSelectedItem();
				if (mixID != null && amount != null) {
					if (mixID == 0)
						player.resetProximityMix();
					else
						player.setProximityMix(mixID, amount.value, instantBox.isSelected());
				}
			});
			if (playing)
				booth.setStatus("Playing song " + selectedFile.getName());
			else
				booth.setStatus(selectedFile.getName() + " composition " + composition + " is empty.");
		}
		catch (Exception e) {
			Logger.logfError("Could not play BGM asset %s", selectedFile.getName());
			Logger.printStackTrace(e);
			booth.selectWithoutPlayback(this);
			booth.setStatus("Could not play " + selectedFile.getName());
		}
	}

	private void updateControls(Song song)
	{
		updatingControls = true;
		compositionBox.removeAllItems();
		for (int i = 0; i < 4; i++) {
			if (song.getComposition(i) != null)
				compositionBox.addItem(i);
		}
		compositionBox.setEnabled(compositionBox.getItemCount() > 1);

		proximityMixBox.removeAllItems();
		for (int i = 0; i < Math.max(1, song.branchOptions); i++)
			proximityMixBox.addItem(i);
		boolean hasProximityMixes = proximityMixBox.getItemCount() > 1;
		proximityMixBox.setEnabled(hasProximityMixes);
		proximityMixBox.setSelectedIndex(0);
		proximityAmountBox.setSelectedItem(ProximityAmount.NONE);
		instantBox.setSelected(false);
		updateProximityControlState(0);
		updatingControls = false;
	}

	private String formatBranchName(int mixID)
	{
		String branchName = selectedSong == null ? null : selectedSong.getBranchName(mixID);
		if (branchName == null)
			return Integer.toString(mixID);
		return branchName;
	}

	private void updateProximityMix()
	{
		if (updatingControls || selectedSong == null)
			return;
		Integer mixID = (Integer) proximityMixBox.getSelectedItem();
		ProximityAmount amount = (ProximityAmount) proximityAmountBox.getSelectedItem();
		if (mixID == null || amount == null)
			return;

		if (mixID == 0) {
			updatingControls = true;
			proximityAmountBox.setSelectedItem(ProximityAmount.NONE);
			instantBox.setSelected(false);
			updatingControls = false;
			updateProximityControlState(mixID);
			booth.runAudioAction(player::resetProximityMix);
		}
		else {
			updateProximityControlState(mixID);
			booth.runAudioAction(() -> player.setProximityMix(mixID, amount.value, instantBox.isSelected()));
		}
	}

	private void updateProximityControlState(int mixID)
	{
		boolean enabled = proximityMixBox.isEnabled() && mixID != 0;
		proximityAmountBox.setEnabled(enabled);
		instantBox.setEnabled(enabled);
	}

	@Override
	public boolean hasSelection()
	{
		return selectedFile != null;
	}

	@Override
	public PreparedSelection prepareReload(AudioBoothTab replacement) throws Exception
	{
		if (!(replacement instanceof BgmTab bgmTab) || selectedFile == null || !selectedFile.isFile())
			return null;
		File restoredFile = selectedFile;
		Song restoredSong = loadBgm(restoredFile);
		return () -> bgmTab.restoreSelection(restoredFile, restoredSong);
	}

	private void restoreSelection(File file, Song song)
	{
		suppressEvents = true;
		fileList.setSelectedValue(file, true);
		suppressEvents = false;
		selectedFile = file;
		selectedSong = song;
		updateControls(song);
		Integer composition = (Integer) compositionBox.getSelectedItem();
		playBgm(composition == null ? 0 : composition);
	}

	@Override
	public BoothExportSource getExportSource()
	{
		if (selectedFile == null || selectedSong == null)
			return null;
		File file = selectedFile;
		Song song = selectedSong;
		Integer selectedComposition = (Integer) compositionBox.getSelectedItem();
		Integer selectedMix = (Integer) proximityMixBox.getSelectedItem();
		ProximityAmount selectedAmount = (ProximityAmount) proximityAmountBox.getSelectedItem();
		int composition = selectedComposition == null ? 0 : selectedComposition;
		int mixID = selectedMix == null ? 0 : selectedMix;
		int mixVolume = selectedAmount == null ? 0 : selectedAmount.value;
		boolean instant = instantBox.isSelected();

		return new BoothExportSource() {
			@Override
			public File getSourceFile()
			{
				return file;
			}

			@Override
			public String getDefaultFileName()
			{
				String name = FilenameUtils.getBaseName(file.getName()) + "_Comp" + composition;
				if (mixID != 0 || mixVolume != 0)
					name += "_Mix" + mixID + "_" + mixVolume;
				return name + ".wav";
			}

			@Override
			public boolean hasInfiniteLoop()
			{
				return AudioExporter.hasInfiniteLoop(song, composition);
			}

			@Override
			public BoothExportRequest createRequest(File outputFile, int volume, int loopRepetitions)
			{
				return new BgmExportRequest(outputFile, volume, loopRepetitions,
					song, composition, mixID, mixVolume, instant);
			}
		};
	}

	private record BgmExportRequest(
		File outputFile,
		int volume,
		int loopRepetitions,
		Song song,
		int composition,
		int proximityMixID,
		int proximityMixVolume,
		boolean proximityMixInstant) implements BoothExportRequest
	{
		@Override
		public File getOutputFile()
		{
			return outputFile;
		}

		@Override
		public AudioExporter.Result render(AudioExporter exporter) throws Exception
		{
			return exporter.exportBgm(outputFile, volume, loopRepetitions, song, composition,
				proximityMixID, proximityMixVolume, proximityMixInstant);
		}
	}

	private static Song loadBgm(File file) throws Exception
	{
		SoundBankCatalog catalog = SoundBankCatalog.loadMod();
		String bgmFilename = FilenameUtils.getBaseName(file.getName()) + ".bgm";
		SoundBankCatalog songCatalog = catalog.withSongBanks(
			MOD_AUDIO.getFile(FN_AUDIO_SONGS), bgmFilename);
		Song song = new Song();
		song.setSoundBankCatalog(songCatalog);
		XmlReader xmr = new XmlReader(file);
		song.fromXML(xmr, xmr.getRootElement());
		return song;
	}

	private static Map<String, String> loadNames()
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

	private static BgmSummary readSummary(File file)
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

	private static String formatName(File file, BgmSummary summary, Map<String, String> names)
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

	private record BgmSummary(int compositions, boolean hasProximityMixes)
	{}
}

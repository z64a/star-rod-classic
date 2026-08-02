package game.sound.booth;

import static app.Directories.FN_AUDIO_AMBIENTS;
import static app.Directories.FN_AUDIO_SONGS;
import static app.Directories.MOD_AUDIO;
import static app.Directories.MOD_AUDIO_MSEQ;

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
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JPanel;

import org.apache.commons.io.FilenameUtils;

import app.input.IOUtils;
import game.sound.AudioCatalog;
import game.sound.AudioExporter;
import game.sound.engine.AudioEngine;
import game.sound.engine.PlaybackSession;
import game.sound.engine.SoundBank;
import game.sound.mseq.Mseq;
import game.sound.mseq.Mseq.MseqKey;
import game.sound.mseq.MseqPlayer;
import net.miginfocom.swing.MigLayout;
import util.Logger;
import util.xml.XmlWrapper.XmlReader;

final class MseqTab extends AudioBoothTab
{
	private final MseqPlayer player;
	private final JList<File> fileList;
	private final JButton rampButton;

	private File selectedFile;
	private Mseq selectedMseq;
	private boolean suppressEvents;

	MseqTab(AudioBooth booth, AudioEngine engine, SoundBank bank) throws IOException
	{
		super(booth, "MSEQ", new MseqPlayer(engine, bank));
		player = (MseqPlayer) getSession();

		Map<String, String> names = loadNames();
		Collection<File> files = IOUtils.getFilesWithExtension(MOD_AUDIO_MSEQ, "xml", false);
		Map<File, Boolean> hasRamps = new HashMap<>();
		for (File file : files)
			hasRamps.put(file, readHasRamps(file));

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
					setText(formatName(file, names, hasRamps.get(file)));
				return component;
			}
		});
		fileList.addListSelectionListener((e) -> {
			if (suppressEvents || e.getValueIsAdjusting())
				return;
			File selected = fileList.getSelectedValue();
			if (selected != null)
				selectMseq(selected);
		});

		JPanel listPanel = createListPanel(fileList, model, MOD_AUDIO_MSEQ.toFile(),
			(file) -> formatName(file, names, hasRamps.get(file)));
		rampButton = new JButton("Trigger Ramps");
		rampButton.setToolTipText("Start this MSEQ's track tune and volume ramps.");
		rampButton.setEnabled(false);
		rampButton.addActionListener((e) -> triggerRamps());
		JPanel rampPanel = new JPanel(new MigLayout("ins 0 8 8 8", "[]", "[]"));
		rampPanel.add(rampButton);

		setLayout(new MigLayout("fill, ins 0", "[grow,fill]", "[grow][]"));
		add(listPanel, "grow, push, wrap");
		add(rampPanel, "growx");
	}

	private void selectMseq(File file)
	{
		selectedFile = file;
		try {
			Mseq mseq = Mseq.load(file);
			mseq.calculateTiming();
			selectedMseq = mseq;
			playMseq();
		}
		catch (Exception e) {
			Logger.logfError("Could not load MSEQ asset %s", file.getName());
			Logger.printStackTrace(e);
			selectedMseq = null;
			booth.selectWithoutPlayback(this);
			booth.setStatus("Could not load MSEQ " + file.getName());
		}
	}

	private void playMseq()
	{
		if (selectedMseq == null || selectedFile == null)
			return;
		booth.startPlayback(this, player, () -> player.setMseq(selectedMseq));
		booth.setStatus("Playing MSEQ " + selectedFile.getName());
	}

	private void triggerRamps()
	{
		if (!booth.isCurrentSession(player) || selectedMseq == null)
			return;
		booth.runAudioAction(() -> player.triggerTrackRamps());
		booth.setStatus("Triggered track ramps for MSEQ " + selectedFile.getName());
	}

	@Override
	public boolean hasSelection()
	{
		return selectedFile != null;
	}

	@Override
	public PreparedSelection prepareReload(AudioBoothTab replacement) throws Exception
	{
		if (!(replacement instanceof MseqTab mseqTab) || selectedFile == null || !selectedFile.isFile())
			return null;
		Mseq restoredMseq = Mseq.load(selectedFile);
		restoredMseq.calculateTiming();
		File restoredFile = selectedFile;
		return () -> mseqTab.restoreSelection(restoredFile, restoredMseq);
	}

	private void restoreSelection(File file, Mseq mseq)
	{
		suppressEvents = true;
		fileList.setSelectedValue(file, true);
		suppressEvents = false;
		selectedFile = file;
		selectedMseq = mseq;
		playMseq();
	}

	@Override
	public BoothExportSource getExportSource()
	{
		if (selectedFile == null || selectedMseq == null)
			return null;
		File file = selectedFile;
		Mseq mseq = selectedMseq;
		return new BoothExportSource() {
			@Override
			public File getSourceFile()
			{
				return file;
			}

			@Override
			public String getDefaultFileName()
			{
				return FilenameUtils.getBaseName(file.getName()) + ".wav";
			}

			@Override
			public boolean hasInfiniteLoop()
			{
				return AudioExporter.hasInfiniteLoop(mseq);
			}

			@Override
			public BoothExportRequest createRequest(File outputFile, int volume, int loopRepetitions)
			{
				return new MseqExportRequest(outputFile, volume, loopRepetitions, mseq);
			}
		};
	}

	@Override
	public void updatePlaybackState(PlaybackSession currentSession, boolean exporting)
	{
		rampButton.setEnabled(currentSession == player && selectedMseq != null && !selectedMseq.trackRamps.isEmpty()
			&& player.isPlaying() && !exporting);
	}

	private record MseqExportRequest(
		File outputFile,
		int volume,
		int loopRepetitions,
		Mseq mseq) implements BoothExportRequest
	{
		@Override
		public File getOutputFile()
		{
			return outputFile;
		}

		@Override
		public AudioExporter.Result render(AudioExporter exporter) throws Exception
		{
			return exporter.exportMseq(outputFile, volume, loopRepetitions, mseq);
		}
	}

	private static Map<String, String> loadNames()
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

	private static boolean readHasRamps(File file)
	{
		try {
			XmlReader xmr = new XmlReader(file);
			return !xmr.getTags(
				xmr.getUniqueRequiredTag(xmr.getRootElement(), MseqKey.TAG_RAMP_LIST),
				MseqKey.TAG_RAMP).isEmpty();
		}
		catch (Exception e) {
			Logger.logfWarning("Could not read MSEQ summary from asset %s", file.getName());
			return false;
		}
	}

	private static String formatName(File file, Map<String, String> names, boolean hasRamps)
	{
		String name = FilenameUtils.getBaseName(file.getName());
		String canonicalName = AudioCatalog.getName(names, file.getName());
		if (canonicalName != null)
			name += "  " + canonicalName;
		if (hasRamps)
			name += " [R]";
		return name;
	}
}

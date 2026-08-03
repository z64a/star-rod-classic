package game.sound.booth;

import static app.Directories.MOD_AUDIO;

import java.awt.Component;
import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;

import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JCheckBox;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.UIManager;

import game.sound.AudioExporter;
import game.sound.SoundBankCatalog;
import game.sound.engine.AudioEngine;
import game.sound.engine.SoundBank;
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

final class SfxTab extends AudioBoothTab
{
	private final SfxPlayer player;
	private final JList<Sound> soundList;
	private final JCheckBox alternativeSoundBox;
	private final JCheckBox alternativeVolumeBox;

	private SfxArchive archive;
	private Sound selectedSound;
	private Exception loadFailure;
	private boolean suppressEvents;

	SfxTab(AudioBooth booth, AudioEngine engine, SoundBank bank)
	{
		super(booth, "SFX", new SfxPlayer(engine, bank));
		player = (SfxPlayer) getSession();

		DefaultListModel<Sound> model = new DefaultListModel<>();
		Map<Sound, String> sampleNames = new HashMap<>();
		File manifest = MOD_AUDIO.getFile(SfxXml.FN_SOUND_EFFECTS);

		if (manifest.isFile()) {
			try {
				SoundBankCatalog catalog = SoundBankCatalog.loadMod();
				archive = SfxXml.read(manifest.toPath(), catalog);
				player.setArchive(archive);
				for (Sound sound : archive.sounds.values())
					sampleNames.put(sound, getSampleNames(sound, catalog));
				model.addAll(archive.sounds.values());
			}
			catch (Exception e) {
				archive = null;
				loadFailure = e;
				Logger.logError("Could not load SFX assets for Audio Booth");
				Logger.printStackTrace(e);
			}
		}

		soundList = new JList<>(model);
		configureList(soundList);
		soundList.setCellRenderer(new DefaultListCellRenderer() {
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
		soundList.addListSelectionListener((e) -> {
			if (suppressEvents || e.getValueIsAdjusting())
				return;
			selectSound(soundList.getSelectedValue());
		});

		JPanel listPanel = createListPanel(soundList, model, manifest,
			(sound) -> String.format("%04X %s %s %s %s %s", sound.id, sound.name,
				sound.unused ? "unused" : "", sound.desc,
				String.join(" ", sound.tags), sampleNames.get(sound)));

		alternativeSoundBox = new JCheckBox("Alternative Sound");
		alternativeSoundBox.addActionListener((e) -> updatePreview());
		alternativeVolumeBox = new JCheckBox("Alternative Volume");
		alternativeVolumeBox.addActionListener((e) -> updatePreview());

		JPanel alternativePanel = new JPanel(new MigLayout("ins 0 8 8 8", "[][]", "[]"));
		alternativePanel.add(alternativeSoundBox);
		alternativePanel.add(alternativeVolumeBox);

		setLayout(new MigLayout("fill, ins 0", "[grow,fill]", "[grow][]"));
		add(listPanel, "grow, push, wrap");
		add(alternativePanel, "growx");
		updateAlternativeControls();
	}

	private void selectSound(Sound sound)
	{
		if (sound == null)
			return;

		selectedSound = sound;
		boolean playing = false;
		if (sound.isEmpty()) {
			booth.selectWithoutPlayback(this);
		}
		else {
			playing = booth.startPlayback(this, player, () -> {
				if (alternativeSoundBox.isSelected() && hasCommand(sound, Op.SET_ALTERNATIVE))
					player.playAlternativeSound(sound.id);
				else if (alternativeVolumeBox.isSelected() && hasCommand(sound, Op.SET_ALTERNATIVE_VOLUME))
					player.playAlternativeVolume(sound.id);
				else
					player.play(sound.id);
			});
		}

		if (sound.isEmpty())
			booth.setStatus(String.format("Nothing to play for %04X %s.", sound.id, sound.name));
		else if (playing)
			booth.setStatus(String.format("Playing sound %04X %s", sound.id, sound.name));
		else
			booth.setStatus(String.format("Could not play sound %04X %s.", sound.id, sound.name));
		updateAlternativeControls();
		booth.updatePlaybackControls();
	}

	private void updatePreview()
	{
		if (booth.isCurrentSession(player) && selectedSound != null)
			selectSound(selectedSound);
		else {
			updateAlternativeControls();
			booth.updatePlaybackControls();
		}
	}

	private void updateAlternativeControls()
	{
		boolean hasAlternativeSound = selectedSound != null && hasCommand(selectedSound, Op.SET_ALTERNATIVE);
		boolean hasAlternativeVolume = selectedSound != null && hasCommand(selectedSound, Op.SET_ALTERNATIVE_VOLUME);
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

	@Override
	public boolean hasSelection()
	{
		return selectedSound != null;
	}

	@Override
	public PreparedSelection prepareReload(AudioBoothTab replacement) throws Exception
	{
		if (!(replacement instanceof SfxTab sfxTab) || selectedSound == null)
			return null;
		if (sfxTab.loadFailure != null)
			throw sfxTab.loadFailure;
		if (sfxTab.archive == null)
			return null;
		Sound restoredSound = sfxTab.archive.sounds.get(selectedSound.id);
		if (restoredSound == null)
			return null;
		return () -> sfxTab.restoreSelection(restoredSound);
	}

	private void restoreSelection(Sound sound)
	{
		suppressEvents = true;
		soundList.setSelectedValue(sound, true);
		suppressEvents = false;
		selectSound(sound);
	}

	@Override
	public BoothExportSource getExportSource()
	{
		if (archive == null || selectedSound == null)
			return null;
		SfxArchive selectedArchive = archive;
		Sound sound = selectedSound;
		return new BoothExportSource() {
			@Override
			public File getSourceFile()
			{
				return MOD_AUDIO.getFile(SfxXml.FN_SOUND_EFFECTS);
			}

			@Override
			public String getDefaultFileName()
			{
				return sound.name + ".wav";
			}

			@Override
			public boolean hasInfiniteLoop()
			{
				return AudioExporter.hasInfiniteLoop(sound);
			}

			@Override
			public BoothExportRequest createRequest(File outputFile, int volume, int loopRepetitions)
			{
				return new SfxExportRequest(outputFile, volume, loopRepetitions, selectedArchive, sound);
			}
		};
	}

	private record SfxExportRequest(
		File outputFile,
		int volume,
		int loopRepetitions,
		SfxArchive archive,
		Sound sound) implements BoothExportRequest
	{
		@Override
		public File getOutputFile()
		{
			return outputFile;
		}

		@Override
		public AudioExporter.Result render(AudioExporter exporter) throws Exception
		{
			return exporter.exportSfx(outputFile, volume, loopRepetitions, archive, sound);
		}
	}

	private static String getSampleNames(Sound sound, SoundBankCatalog catalog)
	{
		LinkedHashSet<String> samples = new LinkedHashSet<>();
		collectSampleNames(samples, sound.tracks, catalog);
		for (SfxArchive.SpawnedEffect effect : sound.spawnedEffects)
			collectSampleNames(samples, effect.tracks, catalog);
		return String.join(", ", samples);
	}

	private static void collectSampleNames(Collection<String> samples, Collection<Track> tracks, SoundBankCatalog catalog)
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
}

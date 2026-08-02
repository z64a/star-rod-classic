package game.sound.booth;

import java.awt.Component;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSlider;

import app.SwingUtils;
import game.sound.BankModder.Bank;
import game.sound.engine.AudioEngine;
import game.sound.engine.Instrument;
import game.sound.engine.PlaybackSession;
import game.sound.engine.SamplePlayer;
import game.sound.engine.SoundBank;
import net.miginfocom.swing.MigLayout;

final class SamplesTab extends AudioBoothTab
{
	private final SoundBank bank;
	private final SamplePlayer player;
	private final JList<Bank> bankList;
	private final JList<Instrument> sampleList;
	private final DefaultListModel<Instrument> sampleModel;
	private final JLabel sampleCountLabel;
	private final JComboBox<Integer> envelopeBox;
	private final JSlider pitchSlider;
	private final JLabel pitchLabel;
	private final JCheckBox loopBox;
	private final JButton playButton;
	private final JButton releaseButton;

	private Bank browsedBank;
	private Bank selectedBank;
	private Instrument selectedInstrument;
	private boolean updatingControls;
	private boolean suppressEvents;

	SamplesTab(AudioBooth booth, AudioEngine engine, SoundBank bank)
	{
		super(booth, "Samples", new SamplePlayer(engine));
		this.bank = bank;
		player = (SamplePlayer) getSession();

		List<Bank> banks = new ArrayList<>(bank.getBanks());
		banks.sort(Comparator.comparing((Bank entry) -> entry.name, String.CASE_INSENSITIVE_ORDER));

		DefaultListModel<Bank> bankModel = new DefaultListModel<>();
		bankModel.addAll(banks);
		bankList = new JList<>(bankModel);
		configureList(bankList);
		bankList.setCellRenderer(new DefaultListCellRenderer() {
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index,
				boolean isSelected, boolean cellHasFocus)
			{
				Component component = super.getListCellRendererComponent(
					list, value, index, isSelected, cellHasFocus);
				if (value instanceof Bank bankEntry)
					setText(String.format("%s  (%d samples)", bankEntry.name, bankEntry.instruments.size()));
				return component;
			}
		});

		sampleModel = new DefaultListModel<>();
		sampleList = new JList<>(sampleModel);
		configureList(sampleList);
		sampleList.setCellRenderer(new DefaultListCellRenderer() {
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index,
				boolean isSelected, boolean cellHasFocus)
			{
				Component component = super.getListCellRendererComponent(
					list, value, index, isSelected, cellHasFocus);
				if (value instanceof Instrument instrument) {
					setText(String.format("%02X  %s%s", index, instrument.name,
						instrument.hasLoop ? "  (loop)" : ""));
					setToolTipText(String.format("Sample rate: %d Hz; key base: %.2f; envelope: %s (%d variants)",
						instrument.sampleRate, instrument.keyBase / 100.0, instrument.envelope.name, instrument.envelope.count()));
				}
				return component;
			}
		});

		envelopeBox = new JComboBox<>();
		envelopeBox.setEnabled(false);
		envelopeBox.setToolTipText("Envelope variant from the selected sample's sound bank.");
		SwingUtils.centerComboBoxText(envelopeBox);
		SwingUtils.addBorderPadding(envelopeBox);
		envelopeBox.addActionListener((e) -> {
			if (!updatingControls && selectedInstrument != null)
				playSample();
		});

		pitchLabel = SwingUtils.getLabel("Pitch: native", 12);
		pitchSlider = new JSlider(-12, 12, 0);
		pitchSlider.setEnabled(false);
		pitchSlider.setMajorTickSpacing(12);
		pitchSlider.setMinorTickSpacing(1);
		pitchSlider.setPaintTicks(true);
		pitchSlider.setToolTipText("Pitch offset from the sample's native tuning, in semitones.");
		pitchSlider.addChangeListener((e) -> updatePitch());

		loopBox = new JCheckBox("Loop");
		loopBox.setEnabled(false);
		loopBox.setToolTipText("Use the loop points stored in the sound bank.");
		loopBox.addActionListener((e) -> updateLooping());

		playButton = new JButton("Play");
		playButton.setEnabled(false);
		playButton.addActionListener((e) -> playSample());

		releaseButton = new JButton("Release");
		releaseButton.setEnabled(false);
		releaseButton.setToolTipText("Apply the selected bank envelope's release program.");
		releaseButton.addActionListener((e) -> releaseSample());

		bankList.addListSelectionListener((e) -> {
			if (!suppressEvents && !e.getValueIsAdjusting())
				selectBank(bankList.getSelectedValue());
		});
		sampleList.addListSelectionListener((e) -> {
			if (!suppressEvents && !e.getValueIsAdjusting())
				selectSample(sampleList.getSelectedValue(), true);
		});

		sampleCountLabel = new JLabel("0");
		JPanel lists = new JPanel(new MigLayout("fill, ins 0", "[grow,fill][grow,fill]", "[grow,fill]"));
		JLabel bankCountLabel = new JLabel(String.format("%d", bankModel.size()));
		lists.add(createUnfilteredListPanel(bankList, "Banks", bankCountLabel), "grow, push");
		lists.add(createUnfilteredListPanel(sampleList, "Samples", sampleCountLabel), "grow, push");

		JPanel controls = new JPanel(new MigLayout(
			"ins 0 8 8 8, fillx", "[][grow,fill][][72!][][]", "[]"));
		controls.add(pitchLabel);
		controls.add(pitchSlider, "growx");
		controls.add(SwingUtils.getLabel("Envelope:", 12));
		controls.add(envelopeBox, "w 72!");
		controls.add(loopBox);
		controls.add(playButton, "split 2");
		controls.add(releaseButton);

		setLayout(new MigLayout("fill, ins 0", "[grow,fill]", "[grow][]"));
		add(lists, "grow, push, wrap");
		add(controls, "growx");

		if (!bankModel.isEmpty())
			bankList.setSelectedIndex(0);
	}

	private void selectBank(Bank selected)
	{
		if (selected == null || selected == browsedBank)
			return;

		boolean clearedSample = selectedInstrument != null;
		boolean ownedSelection = booth.ownsSelection(this);
		if (clearedSample && ownedSelection)
			booth.clearSelection(this);

		browsedBank = selected;
		selectedBank = null;
		selectedInstrument = null;
		sampleModel.clear();
		sampleModel.addAll(selected.instruments);
		sampleCountLabel.setText(String.format("%d", sampleModel.size()));

		updatingControls = true;
		envelopeBox.removeAllItems();
		envelopeBox.setEnabled(false);
		pitchSlider.setEnabled(false);
		loopBox.setSelected(false);
		loopBox.setEnabled(false);
		playButton.setEnabled(false);
		releaseButton.setEnabled(false);
		updatingControls = false;

		if (clearedSample && ownedSelection) {
			booth.setStatus("Select a sample from bank " + selected.name + ".");
			booth.updatePlaybackControls();
		}
	}

	private void selectSample(Instrument instrument, boolean audition)
	{
		if (instrument == null || browsedBank == null)
			return;

		selectedBank = browsedBank;
		selectedInstrument = instrument;

		updatingControls = true;
		envelopeBox.removeAllItems();
		for (int i = 0; i < instrument.envelope.count(); i++)
			envelopeBox.addItem(i);
		if (envelopeBox.getItemCount() > 0)
			envelopeBox.setSelectedIndex(0);
		envelopeBox.setEnabled(envelopeBox.getItemCount() > 1);
		pitchSlider.setEnabled(true);
		loopBox.setSelected(instrument.hasLoop);
		loopBox.setEnabled(instrument.hasLoop);
		playButton.setEnabled(true);
		updatingControls = false;

		if (audition)
			playSample();
	}

	private void playSample()
	{
		if (selectedBank == null || selectedInstrument == null)
			return;
		Integer envelopeIndex = (Integer) envelopeBox.getSelectedItem();
		if (envelopeIndex == null)
			return;

		booth.startPlayback(this, player, () -> {
			player.setPitch(AudioEngine.detuneToPitchRatio(pitchSlider.getValue() * 100));
			player.setLooping(loopBox.isSelected());
			player.play(selectedInstrument, selectedInstrument.envelope.get(envelopeIndex));
		});
		booth.setStatus(String.format("Playing bank %s sample %s with envelope %d at %s.",
			selectedBank.name, selectedInstrument.name, envelopeIndex, formatPitch()));
	}

	private void releaseSample()
	{
		if (!booth.isCurrentSession(player) || selectedInstrument == null)
			return;
		booth.runAudioAction(() -> player.release());
		booth.updatePlaybackControls();
		booth.setStatus("Released bank sample " + selectedInstrument.name + ".");
	}

	private void updatePitch()
	{
		pitchLabel.setText("Pitch: " + formatPitch());
		if (booth.isCurrentSession(player)) {
			booth.runAudioAction(() -> player.setPitch(AudioEngine.detuneToPitchRatio(pitchSlider.getValue() * 100)));
			booth.refreshTimeline(player);
		}
	}

	private String formatPitch()
	{
		int semitones = pitchSlider.getValue();
		if (semitones == 0)
			return "native";
		return String.format("%+d st", semitones);
	}

	private void updateLooping()
	{
		if (updatingControls || !booth.isCurrentSession(player))
			return;
		booth.runAudioAction(() -> player.setLooping(loopBox.isSelected()));
	}

	@Override
	public boolean hasSelection()
	{
		return selectedInstrument != null;
	}

	@Override
	public PreparedSelection prepareReload(AudioBoothTab replacement)
	{
		if (!(replacement instanceof SamplesTab samplesTab) || selectedBank == null || selectedInstrument == null)
			return null;

		Bank matchingBank = null;
		Instrument matchingInstrument = null;
		for (Bank candidateBank : samplesTab.bank.getBanks()) {
			if (!candidateBank.name.equals(selectedBank.name))
				continue;
			matchingBank = candidateBank;
			for (Instrument candidateInstrument : candidateBank.instruments) {
				if (candidateInstrument.name.equals(selectedInstrument.name)) {
					matchingInstrument = candidateInstrument;
					break;
				}
			}
			break;
		}
		if (matchingBank == null || matchingInstrument == null)
			return null;

		Bank restoredBank = matchingBank;
		Instrument restoredInstrument = matchingInstrument;
		Integer selectedEnvelope = (Integer) envelopeBox.getSelectedItem();
		int envelope = selectedEnvelope == null ? 0 : selectedEnvelope;
		int pitch = pitchSlider.getValue();
		boolean looping = loopBox.isSelected();
		return () -> samplesTab.restoreSelection(restoredBank, restoredInstrument, envelope, pitch, looping);
	}

	private void restoreSelection(Bank restoredBank, Instrument restoredInstrument, int envelope, int pitch, boolean looping)
	{
		suppressEvents = true;
		bankList.setSelectedValue(restoredBank, true);
		selectBank(restoredBank);
		sampleList.setSelectedValue(restoredInstrument, true);
		selectSample(restoredInstrument, false);
		updatingControls = true;
		pitchSlider.setValue(pitch);
		if (envelope >= 0 && envelope < envelopeBox.getItemCount())
			envelopeBox.setSelectedIndex(envelope);
		loopBox.setSelected(looping && restoredInstrument.hasLoop);
		updatingControls = false;
		suppressEvents = false;
		playSample();
	}

	@Override
	public void updatePlaybackState(PlaybackSession currentSession, boolean exporting)
	{
		releaseButton.setEnabled(currentSession == player && player.isPlaying() && !player.isReleasing());
	}
}

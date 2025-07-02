package game.sound.bgm;

import util.xml.XmlKey;

public enum SongKey implements XmlKey
{
	// @formatter:off
	TAG_SONG            	("Song"),
    ATTR_MEASURE			("measure"),
    ATTR_BRANCHES			("branches"),
	TAG_INS_LIST			("Instruments"),
	TAG_DRUM_LIST			("Drums"),
	TAG_COMP_LIST			("Compositions"),
	TAG_COMPOSITION			("Composition"),
	TAG_COMP_PLAY			("Play"),
	TAG_COMP_START_LOOP		("StartLoop"),
	TAG_COMP_END_LOOP		("EndLoop"),
	ATTR_COMP_LOOP_INDEX	("index"),
	ATTR_COMP_LOOP_COUNT	("count"),
	TAG_PHRASE_LIST			("Phrases"),
	TAG_PHRASE				("Phrase"),
	TAG_TRACK				("Track"),
	TAG_DETOUR_LIST			("Detours"),
	TAG_DETOUR				("Detour"),
	TAG_COMMANDS			("Commands"),
	TAG_BRANCH_LIST			("Branches"),
	TAG_BRANCH				("Branch"),
    TAG_OPTION              ("Option"),
	ATTR_SERIAL_ID			("id"),
	ATTR_FILE_POS			("offset"),

	TAG_CMD_NOTE			("Note"),
	ATTR_NOTE_PITCH			("pitch"),
	ATTR_NOTE_VELOCITY		("velocity"),
	ATTR_NOTE_LENGTH		("length"),

	TAG_CMD_DELAY				("Delay"),
	TAG_CMD_MASTER_TEMPO		("MasterTempo"),
	TAG_CMD_MASTER_VOLUME		("MasterVolume"),
	TAG_CMD_MASTER_DETUNE		("MasterDetune"),
	TAG_CMD_MASTER_TEMPO_LERP	("MasterTempoLerp"),
	TAG_CMD_MASTER_VOLUME_LERP	("MasterVolumeLerp"),
	TAG_CMD_MASTER_EFFECT		("MasterEffect"),
	TAG_CMD_OVERRIDE_PATCH		("OverridePatch"),
	TAG_CMD_USE_INSTRUMENT		("UseInstrument"),
	TAG_CMD_BUS_EFFECT          ("BusEffect"),
    TAG_CMD_INSTRUMENT_VOLUME   ("InstrumentVolume"),     // E9
    TAG_CMD_INSTRUMENT_PAN      ("InstrumentPan"),        // EA
    TAG_CMD_INSTRUMENT_REVERB   ("InstrumentReverb"),     // EB
    TAG_CMD_TRACK_VOLUME        ("TrackVolume"),          // EC
    TAG_CMD_INSTR_COARSE_TUNE   ("InstrumentCoarseTune"), // ED
    TAG_CMD_INSTR_FINE_TUNE     ("InstrumentFineTune"),   // EE
    TAG_CMD_TRACK_DETUNE        ("TrackDetune"),          // EF
    TAG_CMD_TRACK_TREMOLO       ("TrackTremolo"),         // F0
    TAG_CMD_TREMOLO_RATE        ("TrackTremoloRate"),     // F1
    TAG_CMD_TREMOLO_DEPTH       ("TrackTremoloDepth"),    // F2
    TAG_CMD_TREMOLO_STOP        ("TrackTremoloStop"),     // F3
    TAG_CMD_RANDOM_PAN          ("RandomPan"),            // F4
    TAG_CMD_INSTR_VOL_LERP      ("InstrumentVolumeLerp"), // F6
    TAG_CMD_REVERB_TYPE         ("ReverbType"),           // F7
    TAG_CMD_BRANCH              ("Branch"),               // FC
    TAG_CMD_EVENT_TRIGGER       ("EventTrigger"),         // FD
    TAG_CMD_DETOUR              ("Detour"),               // FE
    TAG_CMD_STEREO_DELAY        ("StereoDelay"),          // FF-1
    TAG_CMD_SEEK_CUSTOM_ENV	 	("SeekCustomEnvelope"),   // FF-2
	TAG_CMD_WRITE_CUSTOM_ENV	("WriteCustomEnvelope"),  // FF-3
	TAG_CMD_USE_CUSTOM_ENV	 	("UseCustomEnvelope"),    // FF-4
	TAG_CMD_TRIGGER_SOUND	 	("TriggerSound"),         // FF-5
	TAG_CMD_PROX_MIX_OVERRIDE	("ProxMixOverride"),      // FF-6

    ATTR_POLYPHONY		("polyphony"),
    ATTR_LINKED			("linked"),
    ATTR_ENABLED		("enabled"),
    ATTR_UNK_FLAG		("flag"),
    ATTR_IS_DRUM		("isDrum"),

    ATTR_TABLE_POS 		("tablePos"),

	ATTR_NAME		   	("name"),
	ATTR_ID				("id"),
	ATTR_INDEX			("index"),
	ATTR_COPY_OF		("copyOf"),
	ATTR_TIMING			("timing"),
	ATTR_BPM			("bpm"),
	ATTR_VALUE			("value"),
	ATTR_CENTS			("cents"),
	ATTR_SEMITONES		("semitones"),
	ATTR_TICKS			("ticks"),
	ATTR_USE_GLOBAL		("global"),

	ATTR_BANK           ("bank"),
	ATTR_PATCH          ("patch"),

	ATTR_EFFECT_TYPE	("effectType"),

    ATTR_OFFSET       	("offset"),
    ATTR_TABLE_COUNT  	("tableCount"),
    ATTR_EVENT_INFO   	("eventInfo"),
    ATTR_LENGTH       	("length"),
    ATTR_TYPE         	("type"),

    ATTR_TIME         	("time"),
    ATTR_SIDE         	("side"),

    ATTR_DELAY        	("delay"),
    ATTR_SPEED        	("speed"),
    ATTR_DEPTH        	("depth"),
    ATTR_PAN1         	("pan1"),
    ATTR_PAN2         	("pan2"),
    ATTR_VOL1         	("vol1"),
    ATTR_VOL2         	("vol2");
	// @formatter:on

	private final String key;

	SongKey(String key)
	{
		this.key = key;
	}

	@Override
	public String toString()
	{
		return key;
	}
}

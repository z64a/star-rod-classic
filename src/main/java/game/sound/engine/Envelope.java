package game.sound.engine;

import static game.sound.BankModder.BankKey.*;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Element;

import util.DynamicByteBuffer;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlSerializable;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public class Envelope implements XmlSerializable
{
	public static final int ENV_CMD_END = 0xFF;
	public static final int ENV_CMD_SET_SCALE = 0xFE;
	public static final int ENV_CMD_ADD_SCALE = 0xFD;
	public static final int ENV_CMD_START_LOOP = 0xFC;
	public static final int ENV_CMD_END_LOOP = 0xFB;

	public static final int ENV_VOL_MAX = 127;

	// duration of a single audio frame sent to the RSP in microseconds
	// = (AUDIO_SAMPLES / HARDWARE_OUTPUT_RATE), expressed in microseconds
	private static final int AUDIO_FRAME_USEC = 5750;

	public String name;
	private List<EnvelopePair> scripts = new ArrayList<>();

	public transient int buildOffset;

	public Envelope(ByteBuffer bb, int start)
	{
		bb.position(start);
		int count = bb.get() & 0xFF;
		byte b1 = bb.get();
		byte b2 = bb.get();
		byte b3 = bb.get();

		int[][] cmdLists = new int[count][2];
		for (int i = 0; i < count; i++) {
			cmdLists[i][0] = start + (bb.getShort() & 0xFFFF);
			cmdLists[i][1] = start + (bb.getShort() & 0xFFFF);
		}

		for (int i = 0; i < count; i++)
			scripts.add(new EnvelopePair(bb, cmdLists[i][0], cmdLists[i][1]));
	}

	public EnvelopePair get(int envIndex)
	{
		return scripts.get(envIndex);
	}

	public int count()
	{
		return scripts.size();
	}

	public void build(DynamicByteBuffer dbb)
	{
		buildOffset = dbb.position();
		dbb.putByte((byte) scripts.size());
		dbb.position(buildOffset + 4 + 4 * scripts.size());

		for (EnvelopePair script : scripts) {
			script.pressOffset = dbb.position();
			for (int v : script.press)
				dbb.putByte((byte) v);

			script.releaseOffset = dbb.position();
			for (int v : script.release)
				dbb.putByte((byte) v);
		}

		int endPos = dbb.position();

		dbb.position(buildOffset + 4);
		for (EnvelopePair script : scripts) {
			dbb.putShort((short) (script.pressOffset - buildOffset));
			dbb.putShort((short) (script.releaseOffset - buildOffset));
		}

		dbb.position(endPos);
	}

	public Envelope(XmlReader xmr, Element insElem)
	{
		fromXML(xmr, insElem);
	}

	@Override
	public void fromXML(XmlReader xmr, Element elem)
	{
		xmr.requiresAttribute(elem, ATTR_ENV_NAME);
		name = xmr.getAttribute(elem, ATTR_ENV_NAME);

		List<Element> scriptElems = xmr.getTags(elem, TAG_ENV_CMDS);

		for (Element scriptElem : scriptElems) {
			xmr.requiresAttribute(scriptElem, ATTR_PRESS);
			int[] press = xmr.readHexArray(scriptElem, ATTR_PRESS);

			xmr.requiresAttribute(scriptElem, ATTR_RELEASE);
			int[] release = xmr.readHexArray(scriptElem, ATTR_RELEASE);

			scripts.add(new EnvelopePair(press, release));
		}
	}

	@Override
	public void toXML(XmlWriter xmw)
	{
		XmlTag tag = xmw.createTag(TAG_ENVELOPE, false);
		xmw.addAttribute(tag, ATTR_ENV_NAME, name);

		xmw.openTag(tag);

		for (EnvelopePair script : scripts) {
			XmlTag scriptTag = xmw.createTag(TAG_ENV_CMDS, true);
			xmw.addHexArray(scriptTag, ATTR_PRESS, script.press);
			xmw.addHexArray(scriptTag, ATTR_RELEASE, script.release);
			xmw.printTag(scriptTag);
		}

		xmw.closeTag(tag);
	}

	public static class EnvelopePair
	{
		private final int[] press;
		private final int[] release;

		private int pressOffset;
		private int releaseOffset;

		public EnvelopePair(int[] press, int[] release)
		{
			this.press = press;
			this.release = release;
		}

		private EnvelopePair(ByteBuffer bb, int pressOffset, int releaseOffset)
		{
			ArrayList<Integer> pressList = readCmdList(bb, pressOffset);
			ArrayList<Integer> releaseList = readCmdList(bb, releaseOffset);

			press = new int[pressList.size()];
			for (int i = 0; i < press.length; i++)
				press[i] = pressList.get(i);

			release = new int[releaseList.size()];
			for (int i = 0; i < release.length; i++)
				release[i] = releaseList.get(i);
		}

		private static ArrayList<Integer> readCmdList(ByteBuffer bb, int start)
		{
			ArrayList<Integer> bytes = new ArrayList<>();

			boolean done = false;
			bb.position(start);

			do {
				int cmd = bb.get();
				int arg = bb.get();

				bytes.add(cmd & 0xFF);
				bytes.add(arg & 0xFF);

				if ((byte) cmd >= 0)
					continue;

				switch (cmd & 0xFF) {
					default:
						break;
					case ENV_CMD_END_LOOP:
					case ENV_CMD_START_LOOP:
					case ENV_CMD_ADD_SCALE:
					case ENV_CMD_SET_SCALE:
						break;
					case ENV_CMD_END:
						done = true;
						break;
				}

			}
			while (!done);

			return bytes;
		}
	}

	public static class EnvelopePlayer
	{
		private enum EnvelopePhase
		{
			INIT,
			PRESS,
			SUSTAIN,
			RELEASE,
			DONE
		};

		private EnvelopePhase phase;

		private int[] cmdList;
		private int cmdPos;

		private int initial = 0;
		private int target = 0;

		private int timeLeft = 0;
		private int duration = 0;

		private float delta = 0f;

		private int scale = ENV_VOL_MAX;
		private int relativeStart = ENV_VOL_MAX;

		private boolean isRelativeRelease = false;

		private int loopStartPos = -1;
		private int loopCounter = 0;

		public EnvelopePlayer()
		{
			phase = EnvelopePhase.INIT;
		}

		public void reset()
		{
			phase = EnvelopePhase.INIT;
		}

		public boolean isDone()
		{
			return phase == EnvelopePhase.DONE;
		}

		public void press(EnvelopePair env)
		{
			phase = EnvelopePhase.PRESS;

			cmdList = env.press;
			cmdPos = 0;
			loopStartPos = -1;
			loopCounter = 0;

			scale = ENV_VOL_MAX;

			EnvelopeInterval interval = step();
			if (interval == null) {
				// no interval found
				phase = EnvelopePhase.DONE;
				return;
			}

			initial = 0;
			target = interval.volume;

			duration = getTime(interval.index);
			timeLeft = duration;

			delta = (duration != 0) ? (float) (target - initial) / duration : 0.0f;

			relativeStart = ENV_VOL_MAX;
			isRelativeRelease = false;
		}

		public void release(EnvelopePair env)
		{
			phase = EnvelopePhase.RELEASE;

			cmdList = env.release;
			cmdPos = 0;
			loopStartPos = -1;
			loopCounter = 0;

			// guard against for invalid release commands
			if (cmdList.length < 2) {
				phase = EnvelopePhase.DONE;
				return;
			}

			// if press hasn't completed before we release, compute current value
			if (timeLeft > AUDIO_FRAME_USEC)
				initial = initial + Math.round(delta * (duration - timeLeft));
			else
				initial = target;

			// read first interval
			int cmd = cmdList[cmdPos++];
			int arg = cmdList[cmdPos++];

			target = arg & 0x7F;

			duration = getTime(cmd);
			timeLeft = duration;

			// check for relative flag
			if ((byte) arg < 0) {
				isRelativeRelease = true;
				relativeStart = initial;
			}
			else {
				relativeStart = ENV_VOL_MAX;
			}

			delta = (duration != 0) ? (float) (target - initial) / duration : 0.0f;
		}

		public void update()
		{
			EnvelopeInterval interval;

			switch (phase) {
				default:
				case INIT:
				case SUSTAIN:
				case DONE:
					// nothing to do, for varying reasons
					return;

				case PRESS:
					timeLeft -= AUDIO_FRAME_USEC;
					if (timeLeft > 0)
						return;

					interval = step();
					if (interval == null) {
						phase = EnvelopePhase.SUSTAIN;
						initial = target;
						timeLeft = -1;
						return;
					}
					break;

				case RELEASE:
					timeLeft -= AUDIO_FRAME_USEC;
					if (timeLeft > 0)
						return;

					interval = step();
					if (interval == null) {
						phase = EnvelopePhase.DONE;
						return;
					}
					break;
			}

			// next envelope point
			initial = target;
			target = interval.volume & 0x7F;
			duration = getTime(interval.index);
			timeLeft = duration;

			delta = (duration != 0) ? (float) (target - initial) / duration : 0.0f;
		}

		record EnvelopeInterval(int index, int volume)
		{}

		private EnvelopeInterval step()
		{
			while (cmdPos + 1 < cmdList.length) {
				int cmd = cmdList[cmdPos++];
				int arg = cmdList[cmdPos++];

				if ((byte) cmd >= 0) {
					target = arg & 0x7F;
					duration = getTime(cmd);
					timeLeft = duration;
					delta = (target - initial) / (float) duration;

					// handle relative flag (same as sign bit)
					if ((byte) arg < 0) {
						isRelativeRelease = true;
						relativeStart = initial;
					}
					return new EnvelopeInterval(cmd, arg);
				}

				switch (cmd & 0xFF) {
					case ENV_CMD_SET_SCALE:
						scale = arg;
						break;
					case ENV_CMD_ADD_SCALE:
						scale = Math.max(0, Math.min(ENV_VOL_MAX, scale + (byte) arg));
						break;
					case ENV_CMD_START_LOOP:
						loopCounter = arg;
						loopStartPos = cmdPos;
						break;
					case ENV_CMD_END_LOOP:
						if (loopCounter == 0 || --loopCounter > 0) {
							cmdPos = loopStartPos;
						}
						break;
					case ENV_CMD_END:
						return null;
					default:
						break; // skip unknown
				}
			}

			// command list finished without encountering END
			return null;
		}

		public float getEnvelopeVolume()
		{
			int current;
			if (timeLeft >= 0)
				current = initial + Math.round(delta * (duration - timeLeft));
			else
				current = target;

			float volume = (float) current / ENV_VOL_MAX;

			volume *= (float) scale / ENV_VOL_MAX;

			if (phase == EnvelopePhase.RELEASE && isRelativeRelease) {
				volume *= (float) relativeStart / ENV_VOL_MAX;
			}

			return volume;
		}
	}

	private static int getTime(int index)
	{
		if (index < ENVELOPE_TIMES.length)
			return ENVELOPE_TIMES[index] * AUDIO_FRAME_USEC;
		else
			return 0;
	}

	// @formatter:off
	public static final int[] ENVELOPE_TIMES = {
			10434,  9565,  8695,  7826,  6956,  6086,  5217,  4782,
			 4347,  3913,  3478,  3304,  3130,  2956,  2782,  2608,
			 2434,  2260,  2086,  1913,  1739,  1565,  1391,  1217,
			 1043,   869,   782,   695,   608,   521,   478,   434,
			  391,   347,   330,   313,   295,   278,   260,   243,
			  226,   208,   191,   173,   165,   156,   147,   139,
			  130,   121,   113,   104,    95,    86,    78,    69,
			   65,    60,    56,    52,    50,    48,    46,    45,
			   43,    41,    40,    38,    36,    34,    33,    31,
			   29,    27,    26,    24,    22,    20,    19,    17,
			   16,    14,    12,    11,    10,     9,     8,     7,
			    6,     5,     4,     3,     2,     1,     0
	};

	public static final String[] ENVELOPE_NAMES = {
			"60 seconds", "55 seconds", "50 seconds", "45 seconds",
			"40 seconds", "35 seconds", "30 seconds", "27.5 seconds",
			"25 seconds", "22.5 seconds", "20 seconds", "19 seconds",
			"18 seconds", "17 seconds", "16 seconds", "15 seconds",
			"14 seconds", "13 seconds", "12 seconds", "11 seconds",
			"10 seconds", "9 seconds", "8 seconds", "7 seconds",
			"6 seconds", "5 seconds", "4.5 seconds", "4 seconds",
			"3.5 seconds", "3 seconds", "2.75 seconds", "2.5 seconds",
			"2.25 seconds", "2 seconds", "1.9 seconds", "1.8 seconds",
			"1.7 seconds", "1.6 seconds", "1.5 seconds", "1.4 seconds",
			"1.3 seconds", "1.2 seconds", "1.1 seconds", "1 seconds",
			"0.95 seconds", "0.9 seconds", "0.85 seconds", "0.8 seconds",
			"0.75 seconds", "0.7 seconds", "0.65 seconds", "0.6 seconds",
			"0.55 seconds", "0.5 seconds", "0.45 seconds", "0.4 seconds",
			"0.375 seconds", "0.35 seconds", "0.325 seconds", "0.3 seconds",
			"0.29 seconds", "0.28 seconds", "0.27 seconds", "0.26 seconds",
			"0.25 seconds", "0.24 seconds", "0.23 seconds", "0.22 seconds",
			"0.21 seconds", "0.2 seconds",  "0.19 seconds", "0.18 seconds",
			"0.17 seconds", "0.16 seconds", "0.15 seconds", "0.14 seconds",
			"0.13 seconds", "0.12 seconds", "0.11 seconds", "0.1 seconds",
			"16 frames", "14 frames", "12 frames", "11 frames",
			"10 frames",  "9 frames",  "8 frames",  "7 frames",
			 "6 frames",  "5 frames",  "4 frames",  "3 frames",
			 "2 frames",  "1 frames",  "0 frames"
	};

	public static enum EnvelopeTime {
		TIME_60S     ( 0, 10434, "60 seconds"),
		TIME_55S     ( 1,  9565, "55 seconds"),
		TIME_50S     ( 2,  8695, "50 seconds"),
		TIME_45S     ( 3,  7826, "45 seconds"),
		TIME_40S     ( 4,  6956, "40 seconds"),
		TIME_35S     ( 5,  6086, "35 seconds"),
		TIME_30S     ( 6,  5217, "30 seconds"),
		TIME_27_5S   ( 7,  4782, "27.5 seconds"),
		TIME_25S     ( 8,  4347, "25 seconds"),
		TIME_22_5S   ( 9,  3913, "22.5 seconds"),
		TIME_20S     (10,  3478, "20 seconds"),
		TIME_19S     (11,  3304, "19 seconds"),
		TIME_18S     (12,  3130, "18 seconds"),
		TIME_17S     (13,  2956, "17 seconds"),
		TIME_16S     (14,  2782, "16 seconds"),
		TIME_15S     (15,  2608, "15 seconds"),
		TIME_14S     (16,  2434, "14 seconds"),
		TIME_13S     (17,  2260, "13 seconds"),
		TIME_12S     (18,  2086, "12 seconds"),
		TIME_11S     (19,  1913, "11 seconds"),
		TIME_10S     (20,  1739, "10 seconds"),
		TIME_9S      (21,  1565, "9 seconds"),
		TIME_8S      (22,  1391, "8 seconds"),
		TIME_7S      (23,  1217, "7 seconds"),
		TIME_6S      (24,  1043, "6 seconds"),
		TIME_5S      (25,   869, "5 seconds"),
		TIME_4_5S    (26,   782, "4.5 seconds"),
		TIME_4S      (27,   695, "4 seconds"),
		TIME_3_5S    (28,   608, "3.5 seconds"),
		TIME_3S      (29,   521, "3 seconds"),
		TIME_2750MS  (30,   478, "2.75 seconds"),
		TIME_2500MS  (31,   434, "2.5 seconds"),
		TIME_2250MS  (32,   391, "2.25 seconds"),
		TIME_2S      (33,   347, "2 seconds"),
		TIME_1900MS  (34,   330, "1.9 seconds"),
		TIME_1800MS  (35,   313, "1.8 seconds"),
		TIME_1700MS  (36,   295, "1.7 seconds"),
		TIME_1600MS  (37,   278, "1.6 seconds"),
		TIME_1500MS  (38,   260, "1.5 seconds"),
		TIME_1400MS  (39,   243, "1.4 seconds"),
		TIME_1300MS  (40,   226, "1.3 seconds"),
		TIME_1200MS  (41,   208, "1.2 seconds"),
		TIME_1100MS  (42,   191, "1.1 seconds"),
		TIME_1S      (43,   173, "1 seconds"),
		TIME_950MS   (44,   165, "0.95 seconds"),
		TIME_900MS   (45,   156, "0.9 seconds"),
		TIME_850MS   (46,   147, "0.85 seconds"),
		TIME_800MS   (47,   139, "0.8 seconds"),
		TIME_750MS   (48,   130, "0.75 seconds"),
		TIME_700MS   (49,   121, "0.7 seconds"),
		TIME_650MS   (50,   113, "0.65 seconds"),
		TIME_600MS   (51,   104, "0.6 seconds"),
		TIME_550MS   (52,    95, "0.55 seconds"),
		TIME_500MS   (53,    86, "0.5 seconds"),
		TIME_450MS   (54,    78, "0.45 seconds"),
		TIME_400MS   (55,    69, "0.4 seconds"),
		TIME_375MS   (56,    65, "0.375 seconds"),
		TIME_350MS   (57,    60, "0.35 seconds"),
		TIME_325MS   (58,    56, "0.325 seconds"),
		TIME_300MS   (59,    52, "0.3 seconds"),
		TIME_290MS   (60,    50, "0.29 seconds"),
		TIME_280MS   (61,    48, "0.28 seconds"),
		TIME_270MS   (62,    46, "0.27 seconds"),
		TIME_260MS   (63,    45, "0.26 seconds"),
		TIME_250MS   (64,    43, "0.25 seconds"),
		TIME_240MS   (65,    41, "0.24 seconds"),
		TIME_230MS   (66,    40, "0.23 seconds"),
		TIME_220MS   (67,    38, "0.22 seconds"),
		TIME_210MS   (68,    36, "0.21 seconds"),
		TIME_200MS   (69,    34, "0.2 seconds"),
		TIME_190MS   (70,    33, "0.19 seconds"),
		TIME_180MS   (71,    31, "0.18 seconds"),
		TIME_170MS   (72,    29, "0.17 seconds"),
		TIME_160MS   (73,    27, "0.16 seconds"),
		TIME_150MS   (74,    26, "0.15 seconds"),
		TIME_140MS   (75,    24, "0.14 seconds"),
		TIME_130MS   (76,    22, "0.13 seconds"),
		TIME_120MS   (77,    20, "0.12 seconds"),
		TIME_110MS   (78,    19, "0.11 seconds"),
		TIME_100MS   (79,    17, "0.1 seconds"),
		TIME_16UNITS (80,    16, "16 frames"),
		TIME_14UNITS (81,    14, "14 frames"),
		TIME_12UNITS (81,    12, "12 frames"),
		TIME_11UNITS (83,    11, "11 frames"),
		TIME_10UNITS (84,    10, "10 frames"),
		TIME_9UNITS  (85,     9, "9 frames"),
		TIME_8UNITS  (86,     8, "8 frames"),
		TIME_7UNITS  (87,     7, "7 frames"),
		TIME_6UNITS  (88,     6, "6 frames"),
		TIME_5UNITS  (89,     5, "5 frames"),
		TIME_4UNITS  (90,     4, "4 frames"),
		TIME_3UNITS  (91,     3, "3 frames"),
		TIME_2UNITS  (92,     2, "2 frames"),
		TIME_1UNITS  (93,     1, "1 frames"),
		TIME_0       (94,     0, "0 frames");

		public final int index;
		public final int time; // in usec
		public final String name;

		private EnvelopeTime(int index, int time, String name)
		{
			this.index = index;
			this.time = time * AUDIO_FRAME_USEC;
			this.name = name;
		}
	};
	// @formatter:on
}

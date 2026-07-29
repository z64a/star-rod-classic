package game.sound.sfx;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Semantic, layout-independent representation of a Paper Mario SEF archive.
 *
 * Binary offsets, BASIC/COMPACT choices, and pointer sharing deliberately do
 * not appear here.  The same model is used by the XML and binary codecs.
 */
public final class SfxArchive
{
	public static final int DEFAULT_MAX_BINARY_SIZE = 0x5200;

	public String name = "DAT1";
	public int maxBinarySize = DEFAULT_MAX_BINARY_SIZE;
	public final Map<Integer, Sound> sounds = new TreeMap<>();
	public final List<Envelope> envelopes = new ArrayList<>();

	public static final class Sound
	{
		public final int id;
		public String name;
		public boolean generatedName;
		public final List<String> aliases = new ArrayList<>();
		public Routing routing;
		public final List<Track> tracks = new ArrayList<>();
		public final List<SpawnedEffect> spawnedEffects = new ArrayList<>();
		public String source;

		public Sound(int id, String name)
		{
			this.id = id;
			this.name = name;
		}

		public boolean isEmpty()
		{
			return tracks.isEmpty();
		}

		public boolean canInlineOneShot()
		{
			return tracks.size() == 1 && tracks.get(0).definition instanceof OneShot && spawnedEffects.isEmpty();
		}
	}

	public enum Allocation
	{
		DYNAMIC,
		FIXED;

		public String xmlName()
		{
			return name().toLowerCase();
		}

		public static Allocation fromXml(String value)
		{
			switch (value) {
				case "dynamic":
					return DYNAMIC;
				case "fixed":
					return FIXED;
				default:
					throw new IllegalArgumentException("Unknown allocation mode: " + value);
			}
		}
	}

	public static final class Routing
	{
		public Allocation allocation;
		public int maxPlayer = 7;
		public int player;
		public int priority;
		public int exclusiveGroup;

		public Routing(Allocation allocation)
		{
			this.allocation = allocation;
		}
	}

	public static final class Track
	{
		public int slot;
		public Integer player;
		public Integer priority;
		public Definition definition;

		public Track(int slot, Definition definition)
		{
			this.slot = slot;
			this.definition = definition;
		}
	}

	public sealed interface Definition permits Empty, OneShot, Sequence
	{}

	public enum Empty implements Definition
	{
		INSTANCE
	}

	public static final class OneShot implements Definition
	{
		public int bank;
		public int patch;
		public int volume;
		public int pan = 64;
		public int reverb;
		public int pitch = 48;
		public int randomPitch;
		public boolean lockVolume;
		public boolean lockPan;
		public boolean lockPitch;
		public boolean lockReverb;

		public int flags()
		{
			return (lockVolume ? 0x04 : 0)
				| (lockPan ? 0x08 : 0)
				| (lockPitch ? 0x10 : 0)
				| (lockReverb ? 0x20 : 0);
		}
	}

	public static final class Sequence implements Definition
	{
		public String entry = "main";
		public boolean lockVolume;
		public boolean lockPan;
		public boolean lockPitch;
		public boolean lockReverb;
		public final List<Node> nodes = new ArrayList<>();

		public int flags()
		{
			return 0x01
				| (lockVolume ? 0x04 : 0)
				| (lockPan ? 0x08 : 0)
				| (lockPitch ? 0x10 : 0)
				| (lockReverb ? 0x20 : 0);
		}
	}

	public sealed interface Node permits Label, Command
	{}

	public record Label(String name) implements Node
	{}

	/**
	 * Generic semantic command.  Only fields used by {@link #op} are set.
	 * References never contain binary offsets.
	 */
	public static final class Command implements Node
	{
		public Op op;
		public int a;
		public int b;
		public int c;
		public String ref;

		public Command(Op op)
		{
			this.op = op;
		}

		public Command(Op op, int a)
		{
			this(op);
			this.a = a;
		}

		public Command(Op op, int a, int b)
		{
			this(op, a);
			this.b = b;
		}

		public Command(Op op, int a, int b, int c)
		{
			this(op, a, b);
			this.c = c;
		}

		public static Command reference(Op op, String ref)
		{
			Command command = new Command(op);
			command.ref = ref;
			return command;
		}
	}

	public enum Op
	{
		END,
		DELAY,
		PLAY,
		SET_VOLUME,
		SET_PAN,
		SET_INSTRUMENT,
		SET_REVERB,
		SET_ENVELOPE,
		COARSE_TUNE,
		FINE_TUNE,
		WAIT_FOR_END,
		PITCH_SWEEP,
		START_LOOP,
		END_LOOP,
		WAIT_FOR_RELEASE,
		SET_CURRENT_VOLUME,
		VOLUME_RAMP,
		SET_ALTERNATIVE,
		STOP,
		JUMP,
		RESTART,
		NOP,
		SET_RANDOM_PITCH,
		SET_RANDOM_VELOCITY,
		SET_RANDOM_UNUSED,
		SET_PRESS_ENVELOPE,
		SPAWN,
		SET_ALTERNATIVE_VOLUME
	}

	public static final class SpawnedEffect
	{
		public String name;
		public Routing routing;
		public final List<Track> tracks = new ArrayList<>();

		public SpawnedEffect(String name)
		{
			this.name = name;
		}
	}

	public static final class Envelope
	{
		public String name;
		public final List<EnvelopeCommand> commands = new ArrayList<>();

		public Envelope(String name)
		{
			this.name = name;
		}
	}

	public static final class EnvelopeCommand
	{
		public EnvelopeOp op;
		public int value;
		public int durationIndex;

		public EnvelopeCommand(EnvelopeOp op)
		{
			this.op = op;
		}

		public EnvelopeCommand(EnvelopeOp op, int value)
		{
			this(op);
			this.value = value;
		}
	}

	public enum EnvelopeOp
	{
		POINT,
		SET_SCALE,
		ADD_SCALE,
		START_LOOP,
		END_LOOP,
		END
	}
}

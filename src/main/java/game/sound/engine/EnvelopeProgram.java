package game.sound.engine;

import java.util.ArrayList;
import java.util.List;

public final class EnvelopeProgram
{
	public static final int CMD_END_LOOP = 0xFB;
	public static final int CMD_START_LOOP = 0xFC;
	public static final int CMD_ADD_SCALE = 0xFD;
	public static final int CMD_SET_SCALE = 0xFE;
	public static final int CMD_END = 0xFF;
	public static final int MAX_VOLUME = 127;

	private EnvelopeProgram()
	{}

	public static Decoded decode(int[] bytes, boolean release)
	{
		if (bytes == null)
			throw new IllegalArgumentException("Envelope bytecode is null");
		if ((bytes.length & 1) != 0)
			throw new IllegalArgumentException("Envelope bytecode must contain two-byte commands");

		List<EnvelopeCommand> commands = new ArrayList<>();
		boolean relativeRelease = false;
		boolean foundEnd = false;

		for (int pos = 0; pos < bytes.length; pos += 2) {
			int op = bytes[pos] & 0xFF;
			int arg = bytes[pos + 1] & 0xFF;
			if (release && pos == 0) {
				if (op >= EnvelopeTimes.COUNT)
					throw new IllegalArgumentException("A release envelope must begin with a Point");
				relativeRelease = (arg & 0x80) != 0;
			}

			EnvelopeCommand command = decodeCommand(op, arg);
			commands.add(command);
			if (command.op == EnvelopeOp.END) {
				if (pos + 2 != bytes.length)
					throw new IllegalArgumentException("Envelope bytecode contains data after End");
				foundEnd = true;
				break;
			}
		}

		if (!foundEnd)
			throw new IllegalArgumentException("Envelope bytecode does not end with End");
		return new Decoded(commands, relativeRelease);
	}

	public static EnvelopeCommand decodeCommand(int op, int arg)
	{
		op &= 0xFF;
		arg &= 0xFF;
		if (op < EnvelopeTimes.COUNT) {
			EnvelopeCommand command = new EnvelopeCommand(EnvelopeOp.POINT, arg & 0x7F);
			command.durationIndex = op;
			return command;
		}

		switch (op) {
			case CMD_END_LOOP:
				return new EnvelopeCommand(EnvelopeOp.END_LOOP);
			case CMD_START_LOOP:
				return new EnvelopeCommand(EnvelopeOp.START_LOOP, arg);
			case CMD_ADD_SCALE:
				return new EnvelopeCommand(EnvelopeOp.ADD_SCALE, (byte) arg);
			case CMD_SET_SCALE:
				return new EnvelopeCommand(EnvelopeOp.SET_SCALE, Math.min(arg, MAX_VOLUME));
			case CMD_END:
				return new EnvelopeCommand(EnvelopeOp.END);
			default:
				throw new IllegalArgumentException(String.format("Unknown envelope opcode %02X", op));
		}
	}

	public static int[] encode(List<EnvelopeCommand> commands, boolean relativeRelease)
	{
		validate(commands);
		if (relativeRelease && commands.get(0).op != EnvelopeOp.POINT)
			throw new IllegalArgumentException("A relative release envelope must begin with a Point");

		int[] bytes = new int[commands.size() * 2];
		for (int i = 0; i < commands.size(); i++) {
			EnvelopeCommand command = commands.get(i);
			int op;
			int arg;
			switch (command.op) {
				case POINT:
					op = command.durationIndex;
					arg = command.value;
					if (relativeRelease && i == 0)
						arg |= 0x80;
					break;
				case SET_SCALE:
					op = CMD_SET_SCALE;
					arg = command.value;
					break;
				case ADD_SCALE:
					op = CMD_ADD_SCALE;
					arg = command.value;
					break;
				case START_LOOP:
					op = CMD_START_LOOP;
					arg = command.value;
					break;
				case END_LOOP:
					op = CMD_END_LOOP;
					arg = 0;
					break;
				case END:
					op = CMD_END;
					arg = 0;
					break;
				default:
					throw new IllegalArgumentException("Unknown envelope operation: " + command.op);
			}
			bytes[i * 2] = op & 0xFF;
			bytes[i * 2 + 1] = arg & 0xFF;
		}
		return bytes;
	}

	public static int[] encodeRelease(List<EnvelopeCommand> commands, boolean relativeRelease)
	{
		validate(commands);
		if (commands.get(0).op != EnvelopeOp.POINT)
			throw new IllegalArgumentException("A release envelope must begin with a Point");
		return encode(commands, relativeRelease);
	}

	public static void validate(List<EnvelopeCommand> commands)
	{
		if (commands == null || commands.isEmpty())
			throw new IllegalArgumentException("Envelope must contain commands");

		for (int i = 0; i < commands.size(); i++) {
			EnvelopeCommand command = commands.get(i);
			if (command == null || command.op == null)
				throw new IllegalArgumentException("Envelope contains a command without an operation");

			switch (command.op) {
				case POINT:
					requireRange(command.durationIndex, 0, EnvelopeTimes.COUNT - 1, "Point duration index");
					requireRange(command.value, 0, MAX_VOLUME, "Point value");
					break;
				case SET_SCALE:
					requireRange(command.value, 0, MAX_VOLUME, "SetScale value");
					break;
				case ADD_SCALE:
					requireRange(command.value, -128, 127, "AddScale value");
					break;
				case START_LOOP:
					requireRange(command.value, 0, 255, "StartLoop count");
					break;
				case END_LOOP:
					break;
				case END:
					if (i + 1 != commands.size())
						throw new IllegalArgumentException("End must be the final envelope command");
					break;
			}
		}

		if (commands.get(commands.size() - 1).op != EnvelopeOp.END)
			throw new IllegalArgumentException("Envelope must end with End");
	}

	private static void requireRange(int value, int min, int max, String name)
	{
		if (value < min || value > max)
			throw new IllegalArgumentException(name + " must be between " + min + " and " + max + ": " + value);
	}

	public static final class Decoded
	{
		public final List<EnvelopeCommand> commands;
		public final boolean relativeRelease;

		private Decoded(List<EnvelopeCommand> commands, boolean relativeRelease)
		{
			this.commands = commands;
			this.relativeRelease = relativeRelease;
		}
	}
}

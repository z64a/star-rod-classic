package patcher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import asm.AsmUtils;
import game.shared.struct.Struct;

public abstract class SubscriptionManager
{
	private static HashMap<String, SubscriptionList> subscribers;

	public static void initialize()
	{
		subscribers = new HashMap<>();
		subscribers.put("Boot", new Boot());
		subscribers.put("RenderGeometry", new RenderGeometry());

		// these three are incompatible with ExtendedGlobals implementation
		/*
		subscribers.put("SetGlobalByte", new SetGlobalByte());
		subscribers.put("SetGlobalFlag", new SetGlobalFlag());
		subscribers.put("ClearGlobalFlag", new ClearGlobalFlag());
		*/
	}

	public static boolean subscribe(Struct body, String name)
	{
		return subscribe(body, name, 0);
	}

	public static boolean subscribe(Struct body, String name, int priority)
	{
		if (!subscribers.containsKey(name))
			return false;

		SubscriptionList list = subscribers.get(name);
		Subscription sub = new Subscription(body, name, priority);
		list.subscribers.add(sub);
		return true;
	}

	public static void writeHooks(RomPatcher rp) throws IOException
	{
		for (SubscriptionList list : subscribers.values())
			list.writeList(rp);
	}

	public static class Subscription implements Comparable<Subscription>
	{
		public final Struct body;
		public final String name;
		public final int priority;

		private Subscription(Struct body, String name, int priority)
		{
			this.body = body;
			this.name = name;
			this.priority = priority;
		}

		@Override
		public int compareTo(Subscription other)
		{
			return priority - other.priority;
		}
	}

	private static abstract class SubscriptionList
	{
		protected final String name;
		public final ArrayList<Subscription> subscribers = new ArrayList<>();
		{}

		public SubscriptionList(String name)
		{
			this.name = name;
		}

		private void writeList(RomPatcher rp) throws IOException
		{
			if (subscribers.size() > 0) {
				Collections.sort(subscribers);
				hook(rp);
			}
		}

		protected abstract void hook(RomPatcher rp) throws IOException;
	}

	private static class RenderGeometry extends SubscriptionList
	{
		public RenderGeometry()
		{
			super("RenderGeometry");
		}

		@Override
		protected void hook(RomPatcher rp) throws IOException
		{
			int offset = rp.nextAlignedOffset();

			rp.seek(name + "Hook", 0x914C); // 8002DD4C
			AsmUtils.assembleAndWrite(name + "Hook", rp, new String[] { String.format("JAL %X", rp.toAddress(offset), "NOP") });

			List<String> lines = new LinkedList<>();
			lines.add("PUSH   RA");
			lines.add("JAL    8011D9B8");
			lines.add("NOP");

			for (Subscription sub : subscribers) {
				lines.add(String.format("JAL %X", rp.toAddress(sub.body.finalFileOffset)));
				lines.add("NOP");
			}

			lines.add("JPOP   RA");

			rp.seek(name + "Dispatch", offset);
			AsmUtils.assembleAndWrite(true, name + "Dispatch", rp, lines);
		}
	}

	private static class Boot extends SubscriptionList
	{
		public Boot()
		{
			super("BootSubscriber");
		}

		@Override
		protected void hook(RomPatcher rp) throws IOException
		{
			int offset = rp.nextAlignedOffset();

			// hook right before we set the handlers for retrace and pre-nmi
			// this is the last point in execution before the proper game loop begins
			rp.seek(name + "Hook", 0x140C); // 8002600C
			//	rp.seek("Boot Hook", 0x144C); // 8002604C
			AsmUtils.assembleAndWrite(name + "Hook", rp, new String[] { String.format("JAL %X", rp.toAddress(offset), "NOP") });

			List<String> lines = new LinkedList<>();
			lines.add("JAL    802B203C");
			lines.add("NOP");

			//	for hook at 8002604C
			//	lines.add("JAL    8005F430");
			//	lines.add("SW     V1, 0 (A0)");

			for (Subscription sub : subscribers) {
				lines.add(String.format("JAL %X", rp.toAddress(sub.body.finalFileOffset)));
				lines.add("NOP");
			}
			lines.add("J      80026014");
			//	lines.add("J      80026054");

			rp.seek(name + "Dispatch", offset);
			AsmUtils.assembleAndWrite(true, name + "Dispatch", rp, lines);
		}
	}

	/*
	private static class SetGlobalByte extends SubscriptionList
	{
		public SetGlobalByte()
		{
			super("SetGlobalByte");
		}

		@Override
		protected void hook(RomPatcher rp) throws IOException
		{
			int offset = rp.nextAlignedOffset();

			rp.seek(name + "Hook", 0xDBC20); // 80145520
			AsmUtils.assembleAndWrite(name + "Hook", rp, new String[]
					{ String.format("J  %X", Patcher.toAddress(offset), "NOP")});

			List<String> lines = new LinkedList<>();
			lines.add("PUSH   RA");
			for(Subscription sub : subscribers)
			{
				lines.add(String.format("JAL %X", Patcher.toAddress(sub.fileOffset)));
				lines.add("NOP");
			}
			lines.add("LA    V0, 800DACC0");
			lines.add("POP   RA");
			lines.add("J     80145528");
			lines.add("RESERVED");

			rp.seek(name + "Dispatch", offset);
			AsmUtils.assembleAndWrite(true, name + "Dispatch", rp, lines);
		}
	}

	private static class SetGlobalFlag extends SubscriptionList
	{
		public SetGlobalFlag()
		{
			super("SetGlobalFlag");
		}

		@Override
		protected void hook(RomPatcher rp) throws IOException
		{
			int offset = rp.nextAlignedOffset();

			rp.seek(name + "Hook", 0xDBB50 ); // 80145450
			AsmUtils.assembleAndWrite(name + "Hook", rp, new String[]
					{ String.format("J  %X", Patcher.toAddress(offset), "NOP")});

			List<String> lines = new LinkedList<>();
			lines.add("PUSH   RA");
			for(Subscription sub : subscribers)
			{
				lines.add(String.format("JAL %X", Patcher.toAddress(sub.fileOffset)));
				lines.add("NOP");
			}
			lines.add("LI    V0, F8D8F200");
			lines.add("POP   RA");
			lines.add("J     80145458");
			lines.add("RESERVED");

			rp.seek(name + "Dispatch", offset);
			AsmUtils.assembleAndWrite(true, name + "Dispatch", rp, lines);
		}
	}

	private static class ClearGlobalFlag extends SubscriptionList
	{
		public ClearGlobalFlag()
		{
			super("ClearGlobalFlag");
		}

		@Override
		protected void hook(RomPatcher rp) throws IOException
		{
			int offset = rp.nextAlignedOffset();

			rp.seek(name + "Hook", 0xDBAE0 ); // 801453E0
			AsmUtils.assembleAndWrite(name + "Hook", rp, new String[]
					{ String.format("J  %X", Patcher.toAddress(offset), "NOP")});

			List<String> lines = new LinkedList<>();
			lines.add("PUSH   RA");
			for(Subscription sub : subscribers)
			{
				lines.add(String.format("JAL %X", Patcher.toAddress(sub.fileOffset)));
				lines.add("NOP");
			}
			lines.add("LI    V0, F8D8F200");
			lines.add("POP   RA");
			lines.add("J     801453E8");
			lines.add("RESERVED");

			rp.seek(name + "Dispatch", offset);
			AsmUtils.assembleAndWrite(true, name + "Dispatch", rp, lines);
		}
	}
	*/
}

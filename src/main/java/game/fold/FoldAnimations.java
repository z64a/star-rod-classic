package game.fold;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import app.Environment;

public class FoldAnimations
{
	public static void main(String[] args) throws IOException
	{
		Environment.initialize();
		List<FoldAnim> anims = load();

		int i = 0;
		for (FoldAnim anim : anims) {
			System.out.println(i++);
			//anim.print();
			anim.toJson();
		}

		Environment.exit();
	}

	private static final int FOLD_START = 0x24B7F0;
	private static final int ANIM_TABLE = 0xE5664;

	public static List<FoldAnim> load() throws IOException
	{
		ByteBuffer bb = Environment.getBaseRomBuffer();
		List<FoldAnim> anims = new ArrayList<>();

		bb.position(ANIM_TABLE); // 8014EF64

		int[] offsets = new int[20];
		for (int i = 0; i < offsets.length; i++)
			offsets[i] = bb.getInt();

		for (int i = 0; i < offsets.length; i++)
			System.out.printf("%02X %08X %08X%n", i, offsets[i], offsets[i] + FOLD_START);

		for (int i = 0; i < offsets.length; i++) {
			bb.position(FOLD_START + offsets[i]);
			anims.add(new FoldAnim(bb));
		}

		return anims;
	}

	public static class FoldAnim
	{
		/*
		typedef struct FoldAnimHeader {
		    / 0x00 / Vtx* vtx;
		    / 0x04 / Gfx* gfx;
		    / 0x08 / u16 vtxCount;
		    / 0x0A / u16 gfxCount;
		    / 0x0C / u16 unk_0C;
		    / 0x0E / u16 useAbsoluteValues;
		} FoldAnimHeader; // size = 0x10
		*/

		int offset;
		int vtxOffset;
		int gfxOffset;
		int vtxCount;
		int gfxCount;
		int keyFrames;
		int flags;

		FoldVertex frames[][];

		ArrayList<FoldTriangle> triangles;

		private FoldAnim(ByteBuffer bb)
		{
			// read header
			offset = bb.position();
			vtxOffset = bb.getInt();
			gfxOffset = bb.getInt();
			vtxCount = bb.getShort() & 0xFFFF;
			gfxCount = bb.getShort() & 0xFFFF;
			keyFrames = bb.getShort() & 0xFFFF;
			flags = bb.getShort() & 0xFFFF;

			frames = new FoldVertex[keyFrames][vtxCount];

			// read vertex data
			bb.position(FOLD_START + vtxOffset);
			for (int i = 0; i < keyFrames; i++) {
				for (int j = 0; j < vtxCount; j++) {
					frames[i][j] = new FoldVertex(j, bb);
				}
			}

			triangles = new ArrayList<>();
			int[] vtxBuf = new int[32];

			// read triangle data
			bb.position(FOLD_START + gfxOffset);
			for (int i = 0; i < gfxCount; i++) {
				int w0 = bb.getInt();
				int w1 = bb.getInt();
				int op = (w0 >> 24) & 0xFF;

				switch (op) {
					case 0x1: // G_VTX
						int num = (w0 >>> 12) & 0xFF;
						int end = (w0 & 0xFF) / 2;
						int srcIdx = (w1 - vtxOffset) / 0xC; // divide by size of FoldVtx

						int start = end - num;
						for (int j = 0; j < num; j++) {
							vtxBuf[start + j] = srcIdx + j;
						}
						break;
					case 0x5: // G_TRI1
						triangles.add(new FoldTriangle(w0, vtxBuf));
						break;
					case 0x6: // G_TRI2
						triangles.add(new FoldTriangle(w0, vtxBuf));
						triangles.add(new FoldTriangle(w1, vtxBuf));
						break;
					case 0xDF: // G_ENDDL
						break;
					default:
						assert (false) : String.format("Unsupported GFX command: %08X %08X", w0, w1);
				}
			}
		}

		public void toJson()
		{
			System.out.println("{");

			System.out.println("\t\"triangles\": [");

			for (int i = 0; i < triangles.size(); i++) {
				FoldTriangle tri = triangles.get(i);

				System.out.printf("\t\t[%d,%d,%d]", tri.i, tri.j, tri.j);

				if (i + 1 < triangles.size())
					System.out.print(",");
				System.out.println();
			}
			System.out.println("\t],");

			System.out.println("\t\"keyframes\": [");
			for (int i = 0; i < keyFrames; i++) {
				System.out.println("\t\t[");
				for (int j = 0; j < vtxCount; j++) {
					FoldVertex vtx = frames[i][j];

					System.out.printf("\t\t\t{ \"xyz\": [%d,%d,%d], \"uv\": [%d, %d], \"rgba\": [%d, %d, %d, %d] }",
						vtx.x, vtx.y, vtx.z, vtx.u, vtx.v, vtx.r, vtx.g, vtx.b, vtx.a);

					if (j + 1 < vtxCount)
						System.out.print(",");
					System.out.println();
				}
				System.out.print("\t\t]");
				if (i + 1 < keyFrames)
					System.out.print(",");
				System.out.println();
			}
			System.out.println("\t]");

			System.out.println("}");
		}

		public void print()
		{
			System.out.printf(" @ %6X%n", offset);
			System.out.printf("  Vtx: %6X - %6X (x %d)%n",
				FOLD_START + vtxOffset,
				FOLD_START + vtxOffset + keyFrames * vtxCount * 0xC,
				vtxCount);
			System.out.printf("  Gfx: %6X - %6X (x %d)%n",
				FOLD_START + gfxOffset,
				FOLD_START + gfxOffset + 0x8 * gfxCount,
				gfxCount);
			System.out.printf("  Frames: %d%n", keyFrames);
			System.out.printf("  Flags: %d%n", flags);
		}
	}

	public static class FoldVertex
	{
		int idx;
		int x;
		int y;
		int z;
		int u;
		int v;
		int r;
		int g;
		int b;
		int a; // unused?

		private FoldVertex(int i, ByteBuffer bb)
		{
			idx = i;
			x = bb.getShort();
			y = bb.getShort();
			z = bb.getShort();
			u = bb.get() & 0xFF;
			v = bb.get() & 0xFF;
			r = bb.get() & 0xFF;
			g = bb.get() & 0xFF;
			b = bb.get() & 0xFF;
			a = bb.get() & 0xFF;
		}

		public void print()
		{
			System.out.printf("    [%2d] (%4d %4d %4d) (%3d %3d) (%3d %3d %3d %3d)%n",
				idx,
				x, y, z,
				u, v,
				r, g, b, a);
		}
	}

	public static class FoldTriangle
	{
		int i;
		int j;
		int k;

		public FoldTriangle(int i, int j, int k)
		{
			this.i = i;
			this.j = j;
			this.k = k;
		}

		public FoldTriangle(int packed, int[] vtxBuf)
		{
			i = (packed >> 16) & 0xFF;
			j = (packed >> 8) & 0xFF;
			k = packed & 0xFF;

			i = vtxBuf[i / 2];
			j = vtxBuf[j / 2];
			k = vtxBuf[k / 2];
		}
	}
}

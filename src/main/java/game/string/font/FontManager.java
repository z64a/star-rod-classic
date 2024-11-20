package game.string.font;

import static app.Directories.DUMP_STRINGS_FONT;
import static app.Directories.MOD_STRINGS_FONT;
import static game.string.font.FontKey.*;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.w3c.dom.Element;

import app.Environment;
import patcher.RomPatcher;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public class FontManager
{
	public static void main(String[] args) throws IOException
	{
		Environment.initialize();
		dump();
		Environment.exit();
	}

	private FontManager()
	{}

	private static FontManager instance = new FontManager();

	public static void dump() throws IOException
	{
		CharSet.dump(instance);

		try (XmlWriter xmw = new XmlWriter(new File(DUMP_STRINGS_FONT + "fonts.xml"))) {
			XmlTag rootTag = xmw.createTag(TAG_ROOT, false);
			xmw.openTag(rootTag);

			for (FontType font : FontType.values())
				font.toXML(xmw);

			for (CharSet chars : CharSet.values())
				chars.toXML(xmw);

			xmw.closeTag(rootTag);
			xmw.save();
		}
	}

	private static boolean loaded = false;
	private static boolean readyForGL = false;

	public static boolean isLoaded()
	{
		return loaded;
	}

	public static boolean isReadyForGL()
	{
		return readyForGL;
	}

	public static void loadData() throws IOException
	{
		XmlReader xmr = new XmlReader(new File((Environment.project.isDecomp ? DUMP_STRINGS_FONT : MOD_STRINGS_FONT) + "fonts.xml"));

		List<Element> fontElems = xmr.getTags(xmr.getRootElement(), TAG_FONT);
		if (fontElems.size() != 4)
			xmr.complain("fonts.xml must contain exactly 4 fonts.");

		List<Element> charsetElems = xmr.getTags(xmr.getRootElement(), TAG_CHARSET);
		if (charsetElems.size() != 3)
			xmr.complain("fonts.xml must contain exactly 3 charsets.");

		for (int i = 0; i < 4; i++)
			FontType.values()[i].fromXML(xmr, fontElems.get(i));

		for (int i = 0; i < 3; i++)
			CharSet.values()[i].fromXML(xmr, charsetElems.get(i));

		CharSet.loadImages(instance);

		loaded = true;
	}

	public static void glLoad() throws IOException
	{
		if (readyForGL)
			glDelete();

		CharSet.glLoad(instance);
		readyForGL = true;
	}

	public static void glDelete()
	{
		CharSet.glDelete(instance);
	}

	public static void patch(RomPatcher rp) throws IOException
	{
		loadData();

		CharSet.patch(instance, rp);
		FontType.patch(instance, rp);

		applyPatches(rp);
	}

	private static void applyPatches(RomPatcher rp)
	{
		if (CharSet.Normal.texSize[0] == 32 && CharSet.Normal.texSize[1] == 32) {
			/*
			 * causes a crash for main menu, not needed
			@Hook 16A00C	% 802497AC = Function_80249380[42C]
			{
				DADDU     S2, S2, S2
				DADDU     S3, S3, S3
				BEQ       T5, R0, C
				LUI       T2, FCFF
				J         802497AC
				NOP
				J         80249898
				NOP
			}
			*/

			// draw_char[1C0]
			rp.seek("2X Font", 0xC2BE4); // 8012C4E4
			rp.writeInt(0x3C014000); // LUI   AT, 4000 (change dsdx/dtdy divisor)

			// draw_number -- get pointer and load size from it
			rp.seek("2X Font", 0xBDDF4); // 801276F4
			rp.writeInt(0x24170200); // ADDIU   S7, R0, 200

			// draw_digit
			rp.seek("2X Font", 0xBDCB8); // 801275B8
			rp.writeInt(0x3C050800); // dsdx 400 --> 800

			// draw_digit
			rp.seek("2X Font", 0xBDCF4); // 801275F4
			rp.writeInt(0x34A50800); // dtdy 400 --> 800

			// file select menu - digit shadow
			rp.seek("2X Font", 0x16A088); // 80249828
			rp.writeInt(0x24020800); // dsdx/dtdy 400 --> 800

			// file select menu - digit
			rp.seek("2X Font", 0x16A110); // 802498B0
			rp.writeInt(0x24020800); // dsdx/dtdy 400 --> 800
		}

		rp.seek("Custom Font", 0xE2A40); // 8014C340
		rp.writeInt(CharSet.Normal.patch_ptrRasters + (CharSet.Normal.imgSize * 0x10)); // numbers start with 16th character
		rp.writeByte(0x80);
		rp.writeByte(CharSet.Normal.texSize[0]);
		rp.writeByte(CharSet.Normal.texSize[1]);

		rp.seek("Custom Font", 0xE2A54); // 8014C354
		rp.writeInt(CharSet.Normal.patch_ptrRasters + (CharSet.Normal.imgSize * 0x10)); // numbers start with 16th character
		rp.writeByte(0x80);
		rp.writeByte(CharSet.Normal.texSize[0]);
		rp.writeByte(CharSet.Normal.texSize[1]);

		// 00B9E58 <==> 80123758 (load_font)
		// only hard-coded reference to 802EE8D0

		// we have to let vanilla font data load or the game

		//		rp.seek("Custom Font", 0xB9E34); // 80123734
		//		AsmUtils.assembleAndWrite("Custom Font", rp, new String[] { "JR  RA", "NOP" });

		/*
		int lower = CharSet.Normal.patch_ptrRasters & 0xFFFF;
		int upper = CharSet.Normal.patch_ptrRasters >>> 16;
		if((lower & 0x8000) != 0)
			upper++;

		rp.seek("Custom Font", 0xB9E58);
		rp.writeInt(0x3C060000 | upper);
		rp.writeInt(0x24C60000 | lower);
		*/

		//	rp.seek("Custom Font", 0xB9E58); // 80123758 (load_font)
		//	AsmUtils.assembleAndWrite(true, "Custom Font", rp, String.format("LIA  A2, %X", CharSet.Normal.ptrRasters));
		//	rp.seek("Custom Font", 0xB9E88); // 80123788 (load_font)
		//	AsmUtils.assembleAndWrite(true, "Custom Font", rp, String.format("LIA  A2, %X", CharSet.Title.ptrRasters));
		//	rp.seek("Custom Font", 0xB9EA0); // 801237A0 (load_font)
		//	AsmUtils.assembleAndWrite(true, "Custom Font", rp, String.format("LIA  A2, %X", CharSet.Subtitle.ptrRasters));
	}
}

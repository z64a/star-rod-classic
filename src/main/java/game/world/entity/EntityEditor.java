package game.world.entity;

import static app.Directories.DUMP_ENTITY_RAW;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import org.apache.commons.io.FileUtils;

import app.Environment;
import game.world.entity.EntityInfo.EntityType;
import util.Logger;

public class EntityEditor
{
	public static void main(String[] args) throws IOException
	{
		Environment.initialize();
		dumpWorldEntities(Environment.getBaseRomBuffer());
		Environment.exit();
	}

	public static void dumpWorldEntities(ByteBuffer fileBuffer) throws IOException
	{
		for (EntitySet set : EntitySet.values()) {
			if (set == EntitySet.NONE)
				continue;

			for (int i = 0; i < 3; i++) {
				byte[] bytes = new byte[set.dmaEnd - set.dmaStart];
				fileBuffer.position(set.dmaStart);
				fileBuffer.get(bytes);

				File rawFile = new File(DUMP_ENTITY_RAW + "_" + set.name() + ".bin");
				FileUtils.writeByteArrayToFile(rawFile, bytes);
			}
		}

		for (EntityType entity : EntityType.values()) {
			if (entity.set == EntitySet.NONE)
				continue;

			if (entity.typeData == null)
				Logger.log("Missing data for entity: " + entity.name);

			int dmaStart = entity.typeData.dmaArgs[0][0];
			int dmaEnd = entity.typeData.dmaArgs[0][1];
			byte[] bytes = new byte[dmaEnd - dmaStart];
			fileBuffer.position(dmaStart);
			fileBuffer.get(bytes);

			File rawFile = new File(DUMP_ENTITY_RAW + entity.name() + ".bin");
			FileUtils.writeByteArrayToFile(rawFile, bytes);

			dmaStart = entity.typeData.dmaArgs[1][0];
			dmaEnd = entity.typeData.dmaArgs[1][1];
			if (dmaStart != 0) {
				bytes = new byte[dmaEnd - dmaStart];
				fileBuffer.position(dmaStart);
				fileBuffer.get(bytes);

				rawFile = new File(DUMP_ENTITY_RAW + entity.name() + "_AUX.bin");
				FileUtils.writeByteArrayToFile(rawFile, bytes);
			}
		}
	}

	public static void check(ByteBuffer fileBuffer, int addr)
	{
		int offset = (addr - 0x802E0D90) + 0x102610;

		fileBuffer.position(offset + 0x18);

		System.out.printf("%08X %08X %08X %08X%n",
			addr, offset,
			fileBuffer.getInt(), fileBuffer.getInt());
	}

	public static void checkB(ByteBuffer fileBuffer, int addr)
	{
		int offset = (addr - 0x802BAE00) + 0xE2D730;

		fileBuffer.position(offset + 0x18);

		System.out.printf("%08X %08X %08X %08X%n",
			addr, offset,
			fileBuffer.getInt(), fileBuffer.getInt());
	}

	public static final Integer[] Loader = new Integer[] {
			0x3C02802C, 0x2442AE00, 0x3C030A00, null /* 0x24631EF8 */,
			0x3063FFFF, 0xAC82003C, 0x8C820044, 0x8C840040,
			0x00431021, 0xAC820014, 0x3C020A00, null /* 0x24421FA0 */,
			0x03E00008, 0xAC820018
	};
}

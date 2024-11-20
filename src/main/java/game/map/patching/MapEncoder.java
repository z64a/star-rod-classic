package game.map.patching;

import static app.Directories.*;
import static game.RAM.WORLD_MAP_DATA_LIMIT;
import static game.RAM.WORLD_MAP_DATA_START;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import app.StarRodException;
import app.input.FileSource;
import app.input.IOUtils;
import app.input.InputFileException;
import app.input.Line;
import game.ROM.LibScope;
import game.map.Map;
import game.map.MapIndex;
import game.map.marker.Marker;
import game.shared.StructTypes;
import game.shared.encoder.BaseDataEncoder;
import patcher.IGlobalDatabase;

public class MapEncoder extends BaseDataEncoder
{
	private final MapIndex index;

	public MapEncoder(IGlobalDatabase db, MapIndex index) throws IOException
	{
		super(StructTypes.mapTypes, LibScope.World, db, MOD_MAP_IMPORT, true);
		this.index = index;
		setAddressLimit(WORLD_MAP_DATA_LIMIT);
	}

	public void encode(List<File> sources, String mapName) throws IOException
	{
		File[] matches = IOUtils.getFileWithin(MOD_MAP_SAVE, mapName + Map.EXTENSION, true);
		if (matches.length > 1)
			throw new StarRodException("Found multiple files named %s in %s", mapName + Map.EXTENSION, MOD_MAP_SAVE);

		setSource(new FileSource(sources.get(0)));

		File sourceMapFile = new File(MOD_MAP_SRC + mapName + Map.EXTENSION);
		File mapFile = (matches.length != 0) ? matches[0] : sourceMapFile;

		File indexFile = new File(DUMP_MAP_SRC + mapName + ".midx");
		File rawFile = new File(DUMP_MAP_RAW + mapName + ".bin");

		File outFile = new File(MOD_MAP_TEMP + mapName + ".bin");
		File outIndexFile = new File(MOD_MAP_TEMP + mapName + ".midx");

		boolean newMap = !rawFile.exists();

		if (!mapFile.exists())
			throw new StarRodException("Could not find map %s for patch: %n%s",
				mapName + Map.EXTENSION, sources.get(0).getAbsolutePath());

		if (newMap) {
			fileBuffer = ByteBuffer.allocateDirect(0);
			setOverlayMemoryLocation(WORLD_MAP_DATA_START, WORLD_MAP_DATA_LIMIT - WORLD_MAP_DATA_START);
		}
		else {
			fileBuffer = IOUtils.getDirectBuffer(rawFile);
			readIndexFile(indexFile); // reads Start/End/Padding/Missing, uses Start to find base
		}

		File genFile = new File(MOD_MAP_GEN + mapName + ".mpat");
		if (genFile.exists())
			readPatchFile(genFile);
		for (File f : sources)
			readPatchFile(f); // read all patches into patchedStructures

		digest();
		buildOverlay(outFile, outIndexFile);
	}

	@Override
	protected void replaceExpression(Line line, String[] args, List<String> newTokenList)
	{
		switch (args[0]) {
			case "PathXZd":
			case "PathXZf": {
				if (args.length != 3)
					throw new InputFileException(line, "Incorrect args for " + args[0]);

				Marker m = index.getMarker(args[1]);
				if (m == null)
					throw new InputFileException(line, "No such marker: %s", args[1]);
				int i = Integer.parseInt(args[2]);
				m.putPathXZ(newTokenList, args[0].endsWith("f"), i);
			}
				break;

			case "Path3d":
			case "Path3f": {
				if (args.length == 2) {
					Marker m = index.getMarker(args[1]);
					if (m == null)
						throw new InputFileException(line, "No such marker: %s", args[1]);
					m.putPath(newTokenList, false);
				}
				else if (args.length == 3) {
					Marker m = index.getMarker(args[1]);
					if (m == null)
						throw new InputFileException(line, "No such marker: %s", args[1]);
					int i = Integer.parseInt(args[2]);
					m.putPath(newTokenList, args[0].endsWith("f"), i);
				}
				else
					throw new InputFileException(line, "Incorrect args for " + args[0]);
			}
				break;

			case "PushGrid": {
				if (args.length != 2)
					throw new InputFileException(line, "Incorrect args for " + args[0]);

				Marker m = index.getMarker(args[1]);
				if (m == null)
					throw new InputFileException(line, "No such marker: %s", args[1]);
				m.putGrid(newTokenList);
			}
				break;
		}
	}

	@Override
	public MapIndex getCurrentMap()
	{
		return index;
	}
}

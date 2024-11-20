package game.map.struct;

import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.util.List;

import app.input.InputFileException;
import app.input.Line;
import game.map.MapIndex;
import game.map.marker.Marker;
import game.map.marker.Marker.MarkerType;
import game.map.patching.MapDecoder;
import game.shared.BaseStruct;
import game.shared.SyntaxConstants;
import game.shared.decoder.BaseDataDecoder;
import game.shared.decoder.Pointer;
import game.shared.encoder.BaseDataEncoder;

public class TriggerCoord extends BaseStruct
{
	public static final TriggerCoord instance = new TriggerCoord();

	private TriggerCoord()
	{}

	@Override
	public void scan(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer)
	{
		fileBuffer.getFloat();
		fileBuffer.getFloat();
		fileBuffer.getFloat();
		fileBuffer.getFloat();
	}

	@Override
	public void print(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer, PrintWriter pw)
	{
		String markerName = String.format("Bomb_%X", ptr.address);
		float x = fileBuffer.getFloat();
		float y = fileBuffer.getFloat();
		float z = fileBuffer.getFloat();
		float r = fileBuffer.getFloat();

		if (decoder instanceof MapDecoder mapDecoder) {
			Marker m = new Marker(markerName, MarkerType.Trigger, x, y, z, 0);
			m.bombPosComponent.radius.set(r / 2);
			m.setDescription("Trigger");
			mapDecoder.addMarker(m);
			pw.printf("%cBombPos:%s %% %f %f %f %f%n", SyntaxConstants.EXPRESSION_PREFIX, markerName, x, y, z, r);
		}
		else {
			pw.printf("%f %f %f %f%n", x, y, z, r);
		}
	}

	@Override
	public void replaceExpression(Line line, BaseDataEncoder encoder, String[] tokens, List<String> newTokens)
	{
		if (tokens[0].equals("BombPos")) {
			MapIndex index = encoder.getCurrentMap();
			if (index == null)
				throw new InputFileException(line, "Invalid BombPos expression: no map is associated with source");

			Marker m = index.getMarker(tokens[1]);
			if (m == null)
				throw new InputFileException(line, "No such marker: " + tokens[1]);
			m.putVector(newTokens, true);
			newTokens.add("" + 2 * m.bombPosComponent.radius.get());
		}
	}
}

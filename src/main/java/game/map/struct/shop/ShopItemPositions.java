package game.map.struct.shop;

import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.util.List;

import app.input.InputFileException;
import app.input.Line;
import game.map.MapIndex;
import game.map.patching.MapDecoder;
import game.shared.BaseStruct;
import game.shared.SyntaxConstants;
import game.shared.decoder.BaseDataDecoder;
import game.shared.decoder.Pointer;
import game.shared.encoder.BaseDataEncoder;

public class ShopItemPositions extends BaseStruct
{
	public static final ShopItemPositions instance = new ShopItemPositions();

	private ShopItemPositions()
	{}

	@Override
	public void scan(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer)
	{
		// variable size
	}

	@Override
	public void print(BaseDataDecoder decoder, Pointer ptr, ByteBuffer fileBuffer, PrintWriter pw)
	{
		int n = 0;
		for (int i = 0; i < ptr.getSize(); i += 4) {
			short modelID = fileBuffer.getShort();
			short colliderID = fileBuffer.getShort();

			if (decoder instanceof MapDecoder mapDecoder) {
				String modelName = mapDecoder.getModelName(modelID);
				String colliderName = mapDecoder.getColliderName(colliderID);

				pw.printf("%cShort:Model:%s ", SyntaxConstants.EXPRESSION_PREFIX, modelName);
				pw.printf("%cShort:Collider:%s ", SyntaxConstants.EXPRESSION_PREFIX, colliderName);
				pw.println();
			}
			else {
				pw.printf("%Xs %Xs%n", modelID, colliderID);
			}
		}
		if (n % 4 != 0)
			pw.println();
	}

	@Override
	public void replaceExpression(Line line, BaseDataEncoder encoder, String[] tokens, List<String> newTokens)
	{
		if (tokens[0].equals("ShopItemPos")) {
			MapIndex index = encoder.getCurrentMap();
			if (index == null)
				throw new InputFileException(line, "Invalid ShopItemPos expression: no map is associated with source");

			if (tokens.length != 3)
				throw new InputFileException(line, "Invalid shop item expression on line: %n%s", line.trimmedInput());

			int modelID = index.getModelID(tokens[1]);
			int colliderID = index.getColliderID(tokens[2]);

			if (modelID < 0)
				throw new InputFileException(line, "No such model: " + tokens[1]);

			if (colliderID < 0)
				throw new InputFileException(line, "No such collider: " + tokens[2]);

			newTokens.add(String.format("%04X%04X", modelID, colliderID));
		}
	}
}

package game.shared.struct.f3dex2;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import app.input.InvalidInputException;
import game.shared.struct.f3dex2.DisplayList.CommandType;

public class DisplayListTest
{
	@Test
	public void geometryModeCanClearAndSetFlags() throws InvalidInputException
	{
		BaseF3DEX2 command = DisplayList.parse("G_GEOMETRYMODE (Clear, G_FOG, Set, G_LIGHTING)");
		assertArrayEquals(new int[] { 0xD9FEFFFF, 0x00020000 }, command.assemble());

		BaseF3DEX2 decoded = CommandType.G_GEOMETRYMODE.create(0xD9FEFFFF, 0x00020000);
		assertArrayEquals(new int[] { 0xD9FEFFFF, 0x00020000 }, DisplayList.parse(decoded.getString(null)).assemble());
	}

	@Test
	public void moveWordDoesNotAcceptModifyVertexFields()
	{
		assertThrows(InvalidInputException.class,
			() -> DisplayList.parse("G_MOVEWORD (G_MW_FORCEMTX, G_MWO_POINT_RGBA, 0)"));
	}
}

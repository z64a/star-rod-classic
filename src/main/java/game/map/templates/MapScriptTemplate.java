package game.map.templates;

import java.nio.ByteBuffer;

import game.shared.decoder.BaseDataDecoder;

public enum MapScriptTemplate
{
	// @formatter:off
	TEX_PANNER			("SetupTextureScroll", MapTemplateData.TEXTURE_PANNER),
	EXIT_WALKOFF		("UseExitWalk", MapTemplateData.EXIT_WALKOFF),
	EXIT_SINGLE_DOOR	("UseExitSingleDoor", MapTemplateData.EXIT_SINGLE_DOOR),
	EXIT_DOUBLE_DOOR	("UseExitDoubleDoor", MapTemplateData.EXIT_DOUBLE_DOOR),
	SHOW_GOT_ITEM		("ShowGotItem", MapTemplateData.SHOW_GOT_X),
	SEARCH_BUSH			("SearchBush", MapTemplateData.SEARCH_BUSH),
	SHAKE_TREE 			("ShakeTree", MapTemplateData.SHAKE_TREE);
	// @formatter:on

	private final String name;
	private final Integer[] template;

	private MapScriptTemplate(String name, Integer[] template)
	{
		this.name = name;
		this.template = template;
	}

	public String getName()
	{
		return name;
	}

	public boolean matches(BaseDataDecoder decoder, ByteBuffer fileBuffer, int scriptOffset)
	{
		int initialBufferPosition = fileBuffer.position();
		boolean matches = true;

		fileBuffer.position(scriptOffset);
		if (fileBuffer.remaining() < 4 * template.length) {
			fileBuffer.position(initialBufferPosition);
			return false; // can't fit
		}

		for (int i = 0; i < template.length; i++) {
			int v = fileBuffer.getInt();
			if (template[i] == null) {
				//	if(!decoder.isLocalAddress(v)) {
				//		matches = false;
				//		break;
				//	}
			}
			else if (v != template[i]) {
				matches = false;
				break;
			}
		}

		fileBuffer.position(initialBufferPosition);
		return matches;
	}
}

package util.japanese;

public class Translations
{
	public static String getAddr(int addr)
	{
		// @formatter:off
		switch(addr)
		{
		case 0x800989A0: return "Nothing";
		case 0x800989A8: return "Awakening";
		case 0x800989B4: return "Preach";
		case 0x800989C0: return "Mumble";			// alternatively: muttering
		case 0x800989D0: return "Lone Fool";
		case 0x800989DC: return "Final Bob-omb";	// Bomuhei = BombTrooper
		case 0x800989F0: return "Final Goompa";		// Kurijii = Goompa's Japanese name
		case 0x80098A04: return "Common";			// alternatively: general, normal
		}
		// @formatter:on
		return null;
	}

	public static String getOffset(int offset)
	{
		// @formatter:off
		switch(offset)
		{
		case 0x73DA0: return "Nothing";
		case 0x73DA8: return "Awakening";
		case 0x73DB4: return "Preach";
		case 0x73DC0: return "Mumble";
		case 0x73DD0: return "Lone Fool";
		case 0x73DDC: return "Final Bob-omb";
		case 0x73DF0: return "Final Goompa";
		case 0x73E04: return "Common";

		case 0x74BCC: return "Test Map";
		case 0x74BE8: return "Game Over";				// 'gemuoba'
		case 0x74C00: return "Minigames";
		case 0x74C18: return "Ending";
		case 0x74C34: return "Mushroom Castle Grounds";	// literally 'Outside Mushroom Castle'
		case 0x74C50: return "Bowser's Castle";
		case 0x74C68: return "Parallel Palace";
		case 0x74C88: return "Cold Cold Village";		// 'sam' = 'samui samui mura'
		case 0x74CA0: return "Flower Land";
		case 0x74CC0: return "Volcano";					// 'kzn' = 'kazan'
		case 0x74CD4: return "Jungle";					// 'jan' = 'janguru'
		case 0x74CEC: return "Shy Guy's Toybox";		// 'omo' ~ omochabako, meaning 'toy box'
		case 0x74D10: return "Tubba's Castle";			// 'dgb' = 'dogabon no shiro'
		case 0x74D2C: return "Wasteland";				// 'arn' = 'areno'
		case 0x74D40: return "Boo House";
		case 0x74D5C: return "Lost Forest";				// 'mim' = 'mayoinomori'
		case 0x74D74: return "Dry Dry Ruins";			// 'isk' = 'kara kara iseki' - what does 'kara' mean here?
		case 0x74D90: return "Dry Dry Desert";			// 'sbk' = 'kara kara sabaku'
		case 0x74DAC: return "Dry Dry Town";			// 'kara kara taun'
		case 0x74DC8: return "Rocky Mountain";			// 'iwa' = 'iwayama'
		case 0x74DDC: return "Fortress";				// 'trd' = 'toride'
		case 0x74DF0: return "Koopa Village";			// 'nok' = 'nokonomura'
		case 0x74E0C: return "Starry Hill";				// 'hos' = 'hoshi furu oka'
		case 0x74E24: return "Mushroom Castle";			// 'kkj' = 'kinoko-jou'
		case 0x74E3C: return "Inside the Whale";		// 'kgr' = 'kujira no naka'
		case 0x74E58: return "Below the Town";			// 'tik' = 'machi no chika'
		case 0x74E70: return "Town";					// 'mac' = 'machi'
		case 0x74E84: return "Goomba Village";			// 'kmr' = 'kuri mura'

		case 0x512538: return "Invincible Tubba Blubba, No Dialogue";
		case 0x512554: return "Invincible Tubba Blubba";
		case 0x512594: return "Spike Soldier";			// Japanese name for Clubbas
		}
		// @formatter:on

		return null;
	}
}

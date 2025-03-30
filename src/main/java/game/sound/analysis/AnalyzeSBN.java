package game.sound.analysis;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.TreeSet;

import app.Environment;
import app.input.IOUtils;
import game.shared.ProjectDatabase;
import game.shared.ProjectDatabase.ConstEnum;

public class AnalyzeSBN
{
	public static void main(String[] args) throws IOException
	{
		Environment.initialize();
		new AnalyzeSBN();
		Environment.exit();
	}

	private static final int TABLE_BASE = 0xF00000;
	private static final int AUDIO_DATA_END = 0x1942C40;

	private AnalyzeSBN() throws IOException
	{
		RandomAccessFile raf = Environment.getBaseRomReader();

		/*
		SBNHeader:
		    0x00  AUFileMetadata mdata;	// uses identifer 'SBN '
		    0x08  char unused_08[8];
		    0x10  s32 fileListOffset; 	// offset in the SBN file of the file table (== sizeof(SBNHeader))
		    0x14  s32 numEntries;      	// number of entries in the SBN file table
		    0x18  s32 fullFileSize; 	// full size of the SBN file (unread)
		    0x1C  s32 versionOffset;
		    0x20  char unused_04[4];
		    0x24  s32 INIToffset;
		    0x28  char unused_28[24];
		    0x40  SBNFileEntry entries[...];
		*/

		raf.seek(TABLE_BASE);
		System.out.println(IOUtils.readString(raf, 4));
		System.out.printf("End: %X%n", TABLE_BASE + raf.readInt());
		raf.skipBytes(8);

		int fileListOffset = raf.readInt();
		int numEntries = raf.readInt();
		raf.skipBytes(12);
		int initOffset = raf.readInt();

		String[] types = new String[numEntries];
		String[] names = new String[numEntries];

		List<Integer> fileIndexList = new ArrayList<>();
		TreeSet<Integer> usedSongBanks = new TreeSet<>();

		for (int i = 0; i < numEntries; i++) {
			raf.seek(TABLE_BASE + fileListOffset + 8 * i);
			int offset = raf.readInt();
			int word2 = raf.readInt();
			int fmt = word2 >>> 24;
			int lenSBN = word2 & 0x00FFFFFF; // bytes from offset (includes header)

			raf.seek(TABLE_BASE + offset);
			String type = IOUtils.readString(raf, 4);
			int len = raf.readInt();
			String name = IOUtils.readString(raf, 4);

			System.out.printf("(%2X) %7X %5X %-3s", i, TABLE_BASE + offset, len, type.trim());

			fileIndexList.add(i);

			types[i] = type;
			names[i] = name;

			// BK file length listed in SBN excludes wav data, engine expects size < 0x840
			if (!type.equals("BK  "))
				assert (len == lenSBN);
			System.out.print(" -- '" + name + "'");

			switch (type) {
				case "BGM ": // 00 - 8E
					assert (fmt == 0x10);
					break;

				case "SEF ": // 8F
					assert (fmt == 0x20);
					break;

				case "BK  ": // 90 - D8
					assert (fmt == 0x30);

					raf.seek(TABLE_BASE + offset + 0x32);
					short len1 = raf.readShort();
					raf.skipBytes(2);
					short len2 = raf.readShort();
					raf.skipBytes(2);
					short len3 = raf.readShort();
					raf.skipBytes(2);
					short len4 = raf.readShort();

					int sumLen = ((len1 + len2 + len3 + len4 + 0x40) + 0xF) & 0xFFFFFFF0;

					assert (sumLen == lenSBN);
					assert (len1 % 0x30 == 0);

					break;

				case "PER ": // D9
					assert (fmt == 0x40);
					break;

				case "PRG ": // DA
					assert (fmt == 0x40);
					break;

				case "MSEQ": // DB - EB
					assert (fmt == 0x40);
					break;

				default:
					throw new RuntimeException("Unknown file type " + type);
			}

			System.out.println();
		}

		// READ INIT FILE

		int initBase = TABLE_BASE + initOffset;
		raf.seek(initBase);
		String type = IOUtils.readString(raf, 4);

		ConstEnum songEnum = ProjectDatabase.getFromLibraryName("songID");
		System.out.printf("     %7X %s", initBase, type.trim());
		System.out.println();

		System.out.println("");
		System.out.println("Reading INIT file:");

		/*
		InitBankEntry:
			0x0  u16 fileIndex;
			0x2  u8 bankIndex;
			0x3  u8 bankGroup;
		 	0x4	 END
		 */

		System.out.println("----- Bank List ----- ");
		for (int i = 0; i < 64; i++) {
			raf.seek(initBase + 0x20 + 4 * i);
			int bank = raf.readShort();
			int bankIndex = raf.readByte();
			int bankGroup = raf.readByte();

			assert (fileIndexList.remove((Integer) bank));

			assert (bankIndex >= 0 && bankIndex <= 15);
			assert (bankGroup >= 1 && bankGroup <= 6);
			assert (bank >= 0x90 && bank <= 0xD8); // bank is always one of the banks
			System.out.printf("[%02X] %2X (%4s) %2X %2X%n", i, bank, names[bank], bankIndex, bankGroup);
		}

		System.out.println("----- Song List ----- ");
		for (int i = 0; i < 0xA0; i++) {
			raf.seek(initBase + 0x130 + 8 * i);
			int bgm = raf.readShort();
			int bank1 = raf.readShort();
			int bank2 = raf.readShort();
			int bank3 = raf.readShort();
			System.out.printf("%07X [%02X] %2X %2X %2X %2X  %s%n", initBase + 0x130 + 8 * i, i, bgm, bank1, bank2, bank3, songEnum.getName(i));
			assert (bgm >= 0x00 && bgm <= 0x8E);
			assert (bank1 == 0 || (bank1 >= 0x90 && bank1 <= 0xD8));
			assert (bank2 == 0 || (bank2 >= 0x90 && bank2 <= 0xD8));
			assert (bank3 == 0 || (bank3 >= 0x90 && bank3 <= 0xD8));

			usedSongBanks.add(bank1);
			usedSongBanks.add(bank2);
			usedSongBanks.add(bank3);

			if (bgm != 0x2F)
				assert (fileIndexList.remove((Integer) bgm));
		}
		fileIndexList.remove((Integer) 0x2F);

		System.out.println("----- Aux List ----- ");
		for (int i = 0; i < 24; i++) {
			raf.seek(initBase + 0x640 + 2 * i);
			int sbnEntry = raf.readShort();
			System.out.printf("[%02X] %2X  %s (%s)%n", i, sbnEntry, types[sbnEntry], names[sbnEntry]);

			if (sbnEntry != 0xE5)
				assert (fileIndexList.remove((Integer) sbnEntry));
		}
		fileIndexList.remove((Integer) 0xE5);

		System.out.println("----- Orphans ----- ");
		for (int sbnID : fileIndexList)
			System.out.printf("%2X  %s (%s)%n", sbnID, types[sbnID], names[sbnID]);

		System.out.println("----- Song Banks ----- ");
		usedSongBanks.remove(0);
		for (int sbnID : usedSongBanks)
			System.out.printf("%2X  %s (%s)%n", sbnID, types[sbnID], names[sbnID]);

		raf.close();
	}

	private static final HashMap<Integer, String> Bank3InsNameMap;

	static {
		Bank3InsNameMap = new HashMap<>();
		Bank3InsNameMap.put(0x00, "Marimba_1");
		Bank3InsNameMap.put(0x01, "Marimba_2");
		Bank3InsNameMap.put(0x02, "Marimba_3");
		Bank3InsNameMap.put(0x03, "Xylophone_1");
		Bank3InsNameMap.put(0x04, "Xylophone_2");
		Bank3InsNameMap.put(0x05, "Xylophone_3");
		Bank3InsNameMap.put(0x06, "Vibraphone_1");
		Bank3InsNameMap.put(0x07, "Vibraphone_2");
		Bank3InsNameMap.put(0x08, "Vibraphone_3");
		Bank3InsNameMap.put(0x09, "Celesta_1");
		Bank3InsNameMap.put(0x0A, "Celesta_2");
		Bank3InsNameMap.put(0x0B, "Huff_N_Puff_Lead_4_Chiff_1");
		Bank3InsNameMap.put(0x0C, "Huff_N_Puff_Lead_4_Chiff_2");
		Bank3InsNameMap.put(0x0D, "Huff_N_Puff_Lead_4_Chiff_3");
		Bank3InsNameMap.put(0x0E, "Unk_0E");
		Bank3InsNameMap.put(0x0F, "Unk_0F");
		Bank3InsNameMap.put(0x10, "Cello");
		Bank3InsNameMap.put(0x11, "Viola");
		Bank3InsNameMap.put(0x12, "Violin");
		Bank3InsNameMap.put(0x13, "Violin_2");
		Bank3InsNameMap.put(0x14, "Pizzicato_Strings_1A");
		Bank3InsNameMap.put(0x15, "Pizzicato_Strings_1B");
		Bank3InsNameMap.put(0x16, "Pizzicato_Strings_2A");
		Bank3InsNameMap.put(0x17, "Pizzicato_Strings_2B");
		Bank3InsNameMap.put(0x18, "String_Ensemble");
		Bank3InsNameMap.put(0x19, "Synth_String_1");
		Bank3InsNameMap.put(0x1A, "Synth_String_2");
		Bank3InsNameMap.put(0x1B, "Synth_Flute");
		Bank3InsNameMap.put(0x1C, "Timpani_1A");
		Bank3InsNameMap.put(0x1D, "Timpani_2A");
		Bank3InsNameMap.put(0x1E, "Timpani_1B");
		Bank3InsNameMap.put(0x1F, "Timpani_2B");
		Bank3InsNameMap.put(0x20, "Electric_Piano_1A");
		Bank3InsNameMap.put(0x21, "Electric_Piano_1B");
		Bank3InsNameMap.put(0x22, "Electric_Piano_2_Alt");
		Bank3InsNameMap.put(0x23, "Acoustic_Piano_1");
		Bank3InsNameMap.put(0x24, "Acoustic_Piano_2");
		Bank3InsNameMap.put(0x25, "Music_Box");
		Bank3InsNameMap.put(0x26, "Music_Box_2");
		Bank3InsNameMap.put(0x27, "Nylon_Guitar_1");
		Bank3InsNameMap.put(0x28, "Nylon_Guitar_2");
		Bank3InsNameMap.put(0x29, "Acoustic_Guitar_1A");
		Bank3InsNameMap.put(0x2A, "Acoustic_Guitar_1B");
		Bank3InsNameMap.put(0x2B, "Acoustic_Guitar_2A");
		Bank3InsNameMap.put(0x2C, "Acoustic_Guitar_2B");
		Bank3InsNameMap.put(0x2D, "English_Horn_1");
		Bank3InsNameMap.put(0x2E, "English_Horn_2");
		Bank3InsNameMap.put(0x2F, "Synth_Bass");
		Bank3InsNameMap.put(0x30, "French_Horn");
		Bank3InsNameMap.put(0x31, "Tuba_1");
		Bank3InsNameMap.put(0x32, "Tuba_2");
		Bank3InsNameMap.put(0x33, "Trombone_1");
		Bank3InsNameMap.put(0x34, "Trombone_2");
		Bank3InsNameMap.put(0x35, "Bassoon_1");
		Bank3InsNameMap.put(0x36, "Bassoon_2");
		Bank3InsNameMap.put(0x37, "Bassoon_3");
		Bank3InsNameMap.put(0x38, "Clarinet");
		Bank3InsNameMap.put(0x39, "Alto_Sax");
		Bank3InsNameMap.put(0x3A, "Oboe_1A");
		Bank3InsNameMap.put(0x3B, "Oboe_1B");
		Bank3InsNameMap.put(0x3C, "Oboe_2");
		Bank3InsNameMap.put(0x3D, "Muted_Flute");
		Bank3InsNameMap.put(0x3E, "Koopa_Bros_Synth_A");
		Bank3InsNameMap.put(0x3F, "Koopa_Bros_Synth_B");
		Bank3InsNameMap.put(0x40, "Acoustic_Bass_1");
		Bank3InsNameMap.put(0x41, "Acoustic_Bass_2");
		Bank3InsNameMap.put(0x42, "Synth_Brass_1");
		Bank3InsNameMap.put(0x43, "Synth_Brass_2");
		Bank3InsNameMap.put(0x44, "Overdriven_Guitar_1");
		Bank3InsNameMap.put(0x45, "Overdriven_Guitar_2");
		Bank3InsNameMap.put(0x46, "Plucky_Music_Box");
		Bank3InsNameMap.put(0x47, "Flute_1");
		Bank3InsNameMap.put(0x48, "Flute_2");
		Bank3InsNameMap.put(0x49, "Flute_3");
		Bank3InsNameMap.put(0x4A, "Steel_Drum_A");
		Bank3InsNameMap.put(0x4B, "Steel_Drum_B");
		Bank3InsNameMap.put(0x4C, "Steel_Drum_C");
		Bank3InsNameMap.put(0x4D, "Percussive_Organ");
		Bank3InsNameMap.put(0x4E, "Drawbar_Organ_1");
		Bank3InsNameMap.put(0x4F, "Drawbar_Organ_2");
		Bank3InsNameMap.put(0x50, "Muted_Trumpet_1");
		Bank3InsNameMap.put(0x51, "Muted_Trumpet_2");
		Bank3InsNameMap.put(0x52, "Guitar_Harmonics_1");
		Bank3InsNameMap.put(0x53, "Guitar_Harmonics_2");
		Bank3InsNameMap.put(0x54, "Percussive_Organ_2");
		Bank3InsNameMap.put(0x55, "Banjo");
		Bank3InsNameMap.put(0x56, "Bari_Sax");
		Bank3InsNameMap.put(0x57, "Bari_Sax_2");
		Bank3InsNameMap.put(0x58, "Muted_Trumpet_3");
		Bank3InsNameMap.put(0x59, "Choir_1A_Lead_6_Voice");
		Bank3InsNameMap.put(0x5A, "Choir_1B_Lead_6_Voice");
		Bank3InsNameMap.put(0x5B, "Choir_2");
		Bank3InsNameMap.put(0x5C, "Electric_Bass_1");
		Bank3InsNameMap.put(0x5D, "Electric_Bass_2");
		Bank3InsNameMap.put(0x5E, "Unk_5E");
		Bank3InsNameMap.put(0x5F, "Unk_5F");
		Bank3InsNameMap.put(0x60, "Honkytonk_1");
		Bank3InsNameMap.put(0x61, "Honkytonk_2");
		Bank3InsNameMap.put(0x62, "Celesta_3");
		Bank3InsNameMap.put(0x63, "Rock_Organ_1");
		Bank3InsNameMap.put(0x64, "Rock_Organ_2");
		Bank3InsNameMap.put(0x65, "Plucked_Synth_Bass");
		Bank3InsNameMap.put(0x66, "Sitar_A");
		Bank3InsNameMap.put(0x67, "Sitar_B");
		Bank3InsNameMap.put(0x68, "Volcano_Synth_Bass_A");
		Bank3InsNameMap.put(0x69, "Volcano_Synth_Bass_B");
		Bank3InsNameMap.put(0x6A, "Unk_Synth_6A");
		Bank3InsNameMap.put(0x6B, "Unk_Synth_6B");
		Bank3InsNameMap.put(0x6C, "Whistle_A");
		Bank3InsNameMap.put(0x6D, "Whistle_B");
		Bank3InsNameMap.put(0x6E, "Pan_Flute_1");
		Bank3InsNameMap.put(0x6F, "Pan_Flute_2");
		Bank3InsNameMap.put(0x70, "Shooting_Star_Pad_8_Sweep");
		Bank3InsNameMap.put(0x71, "Slap_Bass");
		Bank3InsNameMap.put(0x72, "Tubular_Bells");
		Bank3InsNameMap.put(0x73, "Unk_Synth_73");
		Bank3InsNameMap.put(0x74, "Unk_Synth_74");
		Bank3InsNameMap.put(0x75, "Electric_Piano_2");
		Bank3InsNameMap.put(0x76, "Electric_Piano_3");
		Bank3InsNameMap.put(0x77, "Electric_Piano_4");
		Bank3InsNameMap.put(0x78, "Electric_Piano_5");
		Bank3InsNameMap.put(0x79, "Music_Box_3");
		Bank3InsNameMap.put(0x7A, "Starry_Xylophone");
		Bank3InsNameMap.put(0x7B, "Unk_Pitched_Percussion");
		Bank3InsNameMap.put(0x7C, "Electric_Bass_Plucked");
		Bank3InsNameMap.put(0x7D, "Twinkly_Whistle");
		Bank3InsNameMap.put(0x7E, "Harmonized_Synth_Voice_1");
		Bank3InsNameMap.put(0x7F, "Harmonized_Synth_Voice_2");
		Bank3InsNameMap.put(0x80, "Glockenspiel_1");
		Bank3InsNameMap.put(0x81, "Glockenspiel_2");
		Bank3InsNameMap.put(0x82, "Dulcimer_1");
		Bank3InsNameMap.put(0x83, "Dulcimer_2");
		Bank3InsNameMap.put(0x84, "Unk_Soft_Synth_Pluck_84");
		Bank3InsNameMap.put(0x85, "Unk_Soft_Synth_Pluck_85");
		Bank3InsNameMap.put(0x86, "Sitar_2");
		Bank3InsNameMap.put(0x87, "Kalimba");
		Bank3InsNameMap.put(0x88, "Orchestra_Hit");
		Bank3InsNameMap.put(0x89, "Accordion_1");
		Bank3InsNameMap.put(0x8A, "Accordion_2");
		Bank3InsNameMap.put(0x8B, "Accordion_3");
		Bank3InsNameMap.put(0x8C, "Chrono_Trigger_Vibraphone");
		Bank3InsNameMap.put(0x8D, "Unk_Distorted_String");
		Bank3InsNameMap.put(0x8E, "Music_Box_4");
		Bank3InsNameMap.put(0x8F, "Music_Box_5");
		Bank3InsNameMap.put(0x90, "Harp");
		Bank3InsNameMap.put(0x91, "Unk_Piano_With_Harsh_Hammer");
		Bank3InsNameMap.put(0x92, "Harpsichord_1");
		Bank3InsNameMap.put(0x93, "Harpsichord_2");
		Bank3InsNameMap.put(0x94, "Tenor_Sax_1");
		Bank3InsNameMap.put(0x95, "Tenor_Sax_2");
		Bank3InsNameMap.put(0x96, "Unk_Synth_96");
		Bank3InsNameMap.put(0x97, "Unk_Synth_97");
		Bank3InsNameMap.put(0x98, "Unk_Mosquito");
		Bank3InsNameMap.put(0x99, "Cat_Lead_8_Bass_And_Lead");
		Bank3InsNameMap.put(0x9A, "Misc_Percussion_9A");
		Bank3InsNameMap.put(0x9B, "Misc_Percussion_9B");
		Bank3InsNameMap.put(0x9C, "Misc_Percussion_9C");
		Bank3InsNameMap.put(0x9D, "Misc_Percussion_9D");
		Bank3InsNameMap.put(0x9E, "Misc_Percussion_9E");
		Bank3InsNameMap.put(0x9F, "Misc_Percussion_9F");
		Bank3InsNameMap.put(0xA0, "Music_Box_6");
		Bank3InsNameMap.put(0xA1, "Music_Box_7");
		Bank3InsNameMap.put(0xA2, "Unk_Pluck_And_Percussion");
		Bank3InsNameMap.put(0xA3, "Church_Organ");
		Bank3InsNameMap.put(0xA4, "Reverse_Cymbal");
		Bank3InsNameMap.put(0xA5, "Synth_Voice");
		Bank3InsNameMap.put(0xA6, "Misc_Percussion_A6");
		Bank3InsNameMap.put(0xA7, "Misc_Percussion_A7");
		Bank3InsNameMap.put(0xA8, "Misc_Percussion_A8");
		Bank3InsNameMap.put(0xA9, "Misc_Percussion_A9");
		Bank3InsNameMap.put(0xAA, "Misc_Percussion_AA");
		Bank3InsNameMap.put(0xAB, "Misc_Percussion_AB");
		Bank3InsNameMap.put(0xAC, "Misc_Percussion_AC");
		Bank3InsNameMap.put(0xAD, "Misc_Percussion_AD");
		Bank3InsNameMap.put(0xAE, "Misc_Percussion_AE");
		Bank3InsNameMap.put(0xAF, "Misc_Percussion_AF");
		Bank3InsNameMap.put(0xB0, "Misc_Percussion_B0");
		Bank3InsNameMap.put(0xB1, "Misc_Percussion_B1");
		Bank3InsNameMap.put(0xB2, "Misc_Percussion_B2");
		Bank3InsNameMap.put(0xB3, "Misc_Percussion_B3");
		Bank3InsNameMap.put(0xB4, "Misc_Percussion_B4");
		Bank3InsNameMap.put(0xB5, "Misc_Percussion_B5");
		Bank3InsNameMap.put(0xB6, "Misc_Percussion_B6");
		Bank3InsNameMap.put(0xB7, "Misc_Percussion_B7");
		Bank3InsNameMap.put(0xB8, "Misc_Percussion_B8");
		Bank3InsNameMap.put(0xB9, "Misc_Percussion_B9");
		Bank3InsNameMap.put(0xBA, "Misc_Percussion_BA");
		Bank3InsNameMap.put(0xBB, "Misc_Percussion_BB");
		Bank3InsNameMap.put(0xBC, "Misc_Percussion_BC");
		Bank3InsNameMap.put(0xBD, "Misc_Percussion_BD");
		Bank3InsNameMap.put(0xBE, "Misc_Percussion_BE");
		Bank3InsNameMap.put(0xBF, "Misc_Percussion_BF");
		Bank3InsNameMap.put(0xC0, "Misc_Percussion_C0");
		Bank3InsNameMap.put(0xC1, "Misc_Percussion_C1");
		Bank3InsNameMap.put(0xC2, "Misc_Percussion_C2");
		Bank3InsNameMap.put(0xC3, "Misc_Percussion_C3");
		Bank3InsNameMap.put(0xC4, "Misc_Percussion_C4");
		Bank3InsNameMap.put(0xC5, "Misc_Percussion_C5");
		Bank3InsNameMap.put(0xC6, "Misc_Percussion_C6");
		Bank3InsNameMap.put(0xC7, "Misc_Percussion_C7");
		Bank3InsNameMap.put(0xC8, "Misc_Percussion_C8");
		Bank3InsNameMap.put(0xC9, "Misc_Percussion_C9");
		Bank3InsNameMap.put(0xCA, "Misc_Percussion_CA");
		Bank3InsNameMap.put(0xCB, "Misc_Percussion_CB");
		Bank3InsNameMap.put(0xCC, "Misc_Percussion_CC");
		Bank3InsNameMap.put(0xCD, "Misc_Percussion_CD");
		Bank3InsNameMap.put(0xCE, "Misc_Percussion_CE");
		Bank3InsNameMap.put(0xCF, "Misc_Percussion_CF");
		Bank3InsNameMap.put(0xD0, "Misc_Percussion_D0");
		Bank3InsNameMap.put(0xD1, "Misc_Percussion_D1");
		Bank3InsNameMap.put(0xD2, "Misc_Percussion_D2");
		Bank3InsNameMap.put(0xD3, "Misc_Percussion_D3");
		Bank3InsNameMap.put(0xD4, "Misc_Percussion_D4");
		Bank3InsNameMap.put(0xD5, "Misc_Percussion_D5");
		Bank3InsNameMap.put(0xD6, "Misc_Percussion_D6");
		Bank3InsNameMap.put(0xD7, "Misc_Percussion_D7");
		Bank3InsNameMap.put(0xD8, "Misc_Percussion_D8");
		Bank3InsNameMap.put(0xD9, "Misc_Percussion_D9");
		Bank3InsNameMap.put(0xDA, "Misc_Percussion_DA");
		Bank3InsNameMap.put(0xDB, "Misc_Percussion_DB");
		Bank3InsNameMap.put(0xDC, "Misc_Percussion_DC");
		Bank3InsNameMap.put(0xDD, "Misc_Percussion_DD");
		Bank3InsNameMap.put(0xDE, "Misc_Percussion_DE");
		Bank3InsNameMap.put(0xDF, "Misc_Percussion_DF");
		Bank3InsNameMap.put(0xE0, "Misc_Percussion_E0");
		Bank3InsNameMap.put(0xE1, "Misc_Percussion_E1");
		Bank3InsNameMap.put(0xE2, "Misc_Percussion_E2");
		Bank3InsNameMap.put(0xE3, "Misc_Percussion_E3");
		Bank3InsNameMap.put(0xE4, "Misc_Percussion_E4");
		Bank3InsNameMap.put(0xE5, "Misc_Percussion_E5");
		Bank3InsNameMap.put(0xE6, "Misc_Percussion_E6");
		Bank3InsNameMap.put(0xE7, "Misc_Percussion_E7");
		Bank3InsNameMap.put(0xE8, "Misc_Percussion_E8");
		Bank3InsNameMap.put(0xE9, "Misc_Percussion_E9");
		Bank3InsNameMap.put(0xEA, "Misc_Percussion_EA");
		Bank3InsNameMap.put(0xEB, "Misc_Percussion_EB");
		Bank3InsNameMap.put(0xEC, "Misc_Percussion_EC");
		Bank3InsNameMap.put(0xED, "Misc_Percussion_ED");
		Bank3InsNameMap.put(0xEE, "Misc_Percussion_EE");
		Bank3InsNameMap.put(0xEF, "Misc_Percussion_EF");
		Bank3InsNameMap.put(0xF0, "Misc_Percussion_F0");
		Bank3InsNameMap.put(0xF1, "Misc_Percussion_F1");
		Bank3InsNameMap.put(0xF2, "Misc_Percussion_F2");
		Bank3InsNameMap.put(0xF3, "Misc_Percussion_F3");
		Bank3InsNameMap.put(0xF4, "Misc_Percussion_F4");
		Bank3InsNameMap.put(0xF5, "Misc_Percussion_F5");
		Bank3InsNameMap.put(0xF6, "Misc_Percussion_F6");
		Bank3InsNameMap.put(0xF7, "Misc_Percussion_F7");
		Bank3InsNameMap.put(0xF8, "Misc_Percussion_F8");
		Bank3InsNameMap.put(0xF9, "Misc_Percussion_F9");
		Bank3InsNameMap.put(0xFA, "Misc_Percussion_FA");
		Bank3InsNameMap.put(0xFB, "Misc_Percussion_FB");
		Bank3InsNameMap.put(0xFC, "Misc_Percussion_FC");
		Bank3InsNameMap.put(0xFD, "Misc_Percussion_FD");
		Bank3InsNameMap.put(0xFE, "Misc_Percussion_FE");
		Bank3InsNameMap.put(0xFF, "Misc_Percussion_FF");
	}
}

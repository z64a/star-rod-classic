package util.japanese;

import util.DualHashMap;

// modified from https://github.com/MasterKale/WanaKanaJava

/**
 * A Java version of the Javascript WanaKana romaji-to-kana converter library (https://github.com/WaniKani/WanaKana)
 * Version 1.1.1
 */
public class WanaKanaJava
{
	public static void main(String[] args)
	{
		WanaKanaJava wkj = new WanaKanaJava();
		wkj.print();

	}

	public void print()
	{
		// J2R.add("７", "7");
		for (String s : J2R.getKeySet()) {
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < s.length(); i++)
				sb.append(toUnicode(s.charAt(i)));

			System.out.printf("J2R.add(\"%s\", \"%s\"); // %s%n", sb.toString(), J2R.get(s), s);
		}
	}

	private static String toUnicode(char ch)
	{
		return String.format("\\u%04x", (int) ch);
	}

	private static final int HIRAGANA_START = 0x3041;
	private static final int HIRAGANA_END = 0x3096;
	private static final int KATAKANA_START = 0x30A1;
	private static final int KATAKANA_END = 0x30FA;
	private DualHashMap<String, String> J2R = new DualHashMap<>();

	private interface Command
	{
		public boolean run(String str);
	}

	public WanaKanaJava()
	{
		prepareMap();
	}

	// Pass every character of a string through a function and return TRUE if every character passes the function's check
	private boolean _allTrue(String checkStr, Command func)
	{
		for (int _i = 0; _i < checkStr.length(); _i++) {
			if (!func.run(String.valueOf(checkStr.charAt(_i)))) {
				return false;
			}
		}
		return true;
	}

	// Check if a character is within a Unicode range
	private boolean _isCharInRange(char chr, int start, int end)
	{
		int code = chr;
		return (start <= code && code <= end);
	}

	private boolean _isCharKatakana(char chr)
	{
		return _isCharInRange(chr, KATAKANA_START, KATAKANA_END);
	}

	private boolean _isCharHiragana(char chr)
	{
		return _isCharInRange(chr, HIRAGANA_START, HIRAGANA_END);
	}

	private String _katakanaToHiragana(String kata)
	{
		int code;
		String hira = "";

		for (int _i = 0; _i < kata.length(); _i++) {
			char kataChar = kata.charAt(_i);

			if (_isCharKatakana(kataChar)) {
				code = kataChar;
				code += HIRAGANA_START - KATAKANA_START;
				hira += String.valueOf(Character.toChars(code));
			}
			else {
				hira += kataChar;
			}
		}

		return hira;
	}

	public String _hiraganaToRomaji(String hira)
	{
		if (isRomaji(hira)) {
			return hira;
		}

		String chunk = "";
		int chunkSize;
		int cursor = 0;
		int len = hira.length();
		int maxChunk = 2;
		boolean nextCharIsDoubleConsonant = false;
		String roma = "";
		String romaChar = null;

		while (cursor < len) {
			chunkSize = Math.min(maxChunk, len - cursor);
			while (chunkSize > 0) {
				chunk = hira.substring(cursor, (cursor + chunkSize));

				if (isKatakana(chunk)) {
					chunk = _katakanaToHiragana(chunk);
				}

				if (String.valueOf(chunk.charAt(0)).equals("\u3063") && chunkSize == 1 && cursor < (len - 1)) {
					nextCharIsDoubleConsonant = true;
					romaChar = "";
					break;
				}

				romaChar = J2R.get(chunk);

				if ((romaChar != null) && nextCharIsDoubleConsonant) {
					romaChar = romaChar.charAt(0) + romaChar;
					nextCharIsDoubleConsonant = false;
				}

				if (romaChar != null) {
					break;
				}

				chunkSize--;
			}
			if (romaChar == null) {
				romaChar = chunk;
			}

			roma += romaChar;
			cursor += chunkSize > 0 ? chunkSize : 1;
		}
		return roma;
	}

	public boolean isHiragana(String input)
	{
		return _allTrue(input, str -> _isCharHiragana(str.charAt(0)));
	}

	public boolean isKatakana(String input)
	{
		return _allTrue(input, str -> _isCharKatakana(str.charAt(0)));
	}

	public boolean isKana(String input)
	{
		return _allTrue(input, str -> (isHiragana(str)) || (isKatakana(str)));
	}

	public boolean isRomaji(String input)
	{
		return _allTrue(input, str -> (!isHiragana(str)) && (!isKatakana(str)));
	}

	public String toRomaji(String input)
	{
		return _hiraganaToRomaji(input);
	}

	// could do a better job of converting full width roman characters to standard half width ascii
	private void prepareMap()
	{
		/*
		J2R.add("ー", "-");
		J2R.add("�?", ",");
		J2R.add("�?", "0");
		J2R.add("１", "1");
		J2R.add("２", "2");
		J2R.add("３", "3");
		J2R.add("４", "4");
		J2R.add("５", "5");
		J2R.add("６", "6");
		J2R.add("７", "7");
		J2R.add("８", "8");
		J2R.add("９", "9");
		J2R.add("x", "x");
		J2R.add("�?�", "a");
		J2R.add("�?�", "i");
		J2R.add("�?�", "u");
		J2R.add("�?�", "e");
		J2R.add("�?�", "o");
		J2R.add("ゔ�??", "va");
		J2R.add("ゔ�?�", "vi");
		J2R.add("ゔ", "vu");
		J2R.add("ゔ�?�", "ve");
		J2R.add("ゔ�?�", "vo");
		J2R.add("�?�", "ka");
		J2R.add("�??", "ki");
		J2R.add("�??ゃ", "kya");
		J2R.add("�??�?�", "kyi");
		J2R.add("�??ゅ", "kyu");
		J2R.add("�??", "ku");
		J2R.add("�?�", "ke");
		J2R.add("�?�", "ko");
		J2R.add("�?�", "ga");
		J2R.add("�?�", "gi");
		J2R.add("�??", "gu");
		J2R.add("�?�", "ge");
		J2R.add("�?�", "go");
		J2R.add("�?�ゃ", "gya");
		J2R.add("�?��?�", "gyi");
		J2R.add("�?�ゅ", "gyu");
		J2R.add("�?��?�", "gye");
		J2R.add("�?�ょ", "gyo");
		J2R.add("�?�", "sa");
		J2R.add("�?�", "su");
		J2R.add("�?�", "se");
		J2R.add("�??", "so");
		J2R.add("�?�", "za");
		J2R.add("�?�", "zu");
		J2R.add("�?�", "ze");
		J2R.add("�?�", "zo");
		J2R.add("�?�", "shi");
		J2R.add("�?�ゃ", "sha");
		J2R.add("�?�ゅ", "shu");
		J2R.add("�?�ょ", "sho");
		J2R.add("�?�", "ji");
		J2R.add("�?�ゃ", "ja");
		J2R.add("�?�ゅ", "ju");
		J2R.add("�?�ょ", "jo");
		J2R.add("�?�", "ta");
		J2R.add("�?�", "chi");
		J2R.add("�?�ゃ", "cha");
		J2R.add("�?�ゅ", "chu");
		J2R.add("�?�ょ", "cho");
		J2R.add("�?�", "tsu");
		J2R.add("�?�", "te");
		J2R.add("�?�", "to");
		J2R.add("�?�", "da");
		J2R.add("�?�", "di");
		J2R.add("�?�", "du");
		J2R.add("�?�", "de");
		J2R.add("�?�", "do");
		J2R.add("�?�", "na");
		J2R.add("�?�", "ni");
		J2R.add("�?�ゃ", "nya");
		J2R.add("�?�ゅ", "nyu");
		J2R.add("�?�ょ", "nyo");
		J2R.add("�?�", "nu");
		J2R.add("�?�", "ne");
		J2R.add("�?�", "no");
		J2R.add("�?�", "ha");
		J2R.add("�?�", "hi");
		J2R.add("�?�", "fu");
		J2R.add("�?�", "he");
		J2R.add("�?�", "ho");
		J2R.add("�?�ゃ", "hya");
		J2R.add("�?�ゅ", "hyu");
		J2R.add("�?�ょ", "hyo");
		J2R.add("�?��??", "fa");
		J2R.add("�?��?�", "fi");
		J2R.add("�?��?�", "fe");
		J2R.add("�?��?�", "fo");
		J2R.add("�?�", "ba");
		J2R.add("�?�", "bi");
		J2R.add("�?�", "bu");
		J2R.add("�?�", "be");
		J2R.add("�?�", "bo");
		J2R.add("�?�ゃ", "bya");
		J2R.add("�?�ゅ", "byu");
		J2R.add("�?�ょ", "byo");
		J2R.add("�?�", "pa");
		J2R.add("�?�", "pi");
		J2R.add("�?�", "pu");
		J2R.add("�?�", "pe");
		J2R.add("�?�", "po");
		J2R.add("�?�ゃ", "pya");
		J2R.add("�?�ゅ", "pyu");
		J2R.add("�?�ょ", "pyo");
		J2R.add("�?�", "ma");
		J2R.add("�?�", "mi");
		J2R.add("む", "mu");
		J2R.add("�?", "me");
		J2R.add("も", "mo");
		J2R.add("�?�ゃ", "mya");
		J2R.add("�?�ゅ", "myu");
		J2R.add("�?�ょ", "myo");
		J2R.add("や", "ya");
		J2R.add("ゆ", "yu");
		J2R.add("よ", "yo");
		J2R.add("ら", "ra");
		J2R.add("り", "ri");
		J2R.add("る", "ru");
		J2R.add("れ", "re");
		J2R.add("�?", "ro");
		J2R.add("りゃ", "rya");
		J2R.add("りゅ", "ryu");
		J2R.add("りょ", "ryo");
		J2R.add("�?", "wa");
		J2R.add("を", "wo");
		J2R.add("ん", "n");
		J2R.add("�?", "wi");
		J2R.add("ゑ", "we");
		J2R.add("�??�?�", "kye");
		J2R.add("�??ょ", "kyo");
		J2R.add("�?��?�", "jyi");
		J2R.add("�?��?�", "jye");
		J2R.add("�?��?�", "cyi");
		J2R.add("�?��?�", "che");
		J2R.add("�?��?�", "hyi");
		J2R.add("�?��?�", "hye");
		J2R.add("�?��?�", "byi");
		J2R.add("�?��?�", "bye");
		J2R.add("�?��?�", "pyi");
		J2R.add("�?��?�", "pye");
		J2R.add("�?��?�", "mye");
		J2R.add("�?��?�", "myi");
		J2R.add("り�?�", "ryi");
		J2R.add("り�?�", "rye");
		J2R.add("�?��?�", "nyi");
		J2R.add("�?��?�", "nye");
		J2R.add("�?��?�", "syi");
		J2R.add("�?��?�", "she");
		J2R.add("�?��?�", "ye");
		J2R.add("�?��??", "wha");
		J2R.add("�?��?�", "who");
		J2R.add("�?��?�", "wi");
		J2R.add("�?��?�", "we");
		J2R.add("ゔゃ", "vya");
		J2R.add("ゔゅ", "vyu");
		J2R.add("ゔょ", "vyo");
		J2R.add("�?��??", "swa");
		J2R.add("�?��?�", "swi");
		J2R.add("�?��?�", "swu");
		J2R.add("�?��?�", "swe");
		J2R.add("�?��?�", "swo");
		J2R.add("�??ゃ", "qya");
		J2R.add("�??ゅ", "qyu");
		J2R.add("�??ょ", "qyo");
		J2R.add("�??�??", "qwa");
		J2R.add("�??�?�", "qwi");
		J2R.add("�??�?�", "qwu");
		J2R.add("�??�?�", "qwe");
		J2R.add("�??�?�", "qwo");
		J2R.add("�??�??", "gwa");
		J2R.add("�??�?�", "gwi");
		J2R.add("�??�?�", "gwu");
		J2R.add("�??�?�", "gwe");
		J2R.add("�??�?�", "gwo");
		J2R.add("�?��??", "tsa");
		J2R.add("�?��?�", "tsi");
		J2R.add("�?��?�", "tse");
		J2R.add("�?��?�", "tso");
		J2R.add("�?�ゃ", "tha");
		J2R.add("�?��?�", "thi");
		J2R.add("�?�ゅ", "thu");
		J2R.add("�?��?�", "the");
		J2R.add("�?�ょ", "tho");
		J2R.add("�?��??", "twa");
		J2R.add("�?��?�", "twi");
		J2R.add("�?��?�", "twu");
		J2R.add("�?��?�", "twe");
		J2R.add("�?��?�", "two");
		J2R.add("�?�ゃ", "dya");
		J2R.add("�?��?�", "dyi");
		J2R.add("�?�ゅ", "dyu");
		J2R.add("�?��?�", "dye");
		J2R.add("�?�ょ", "dyo");
		J2R.add("�?�ゃ", "dha");
		J2R.add("�?��?�", "dhi");
		J2R.add("�?�ゅ", "dhu");
		J2R.add("�?��?�", "dhe");
		J2R.add("�?�ょ", "dho");
		J2R.add("�?��??", "dwa");
		J2R.add("�?��?�", "dwi");
		J2R.add("�?��?�", "dwu");
		J2R.add("�?��?�", "dwe");
		J2R.add("�?��?�", "dwo");
		J2R.add("�?��?�", "fwu");
		J2R.add("�?�ゃ", "fya");
		J2R.add("�?�ゅ", "fyu");
		J2R.add("�?�ょ", "fyo");
		J2R.add("�??", "a");
		J2R.add("�?�", "i");
		J2R.add("�?�", "e");
		J2R.add("�?�", "u");
		J2R.add("�?�", "o");
		J2R.add("ゃ", "ya");
		J2R.add("ゅ", "yu");
		J2R.add("ょ", "yo");
		J2R.add("�?�", "");
		J2R.add("ゕ", "ka");
		J2R.add("ゖ", "ka");
		J2R.add("ゎ", "wa");
		J2R.add("'　'", " ");
		J2R.add("ん�?�", "n'a");
		J2R.add("ん�?�", "n'i");
		J2R.add("ん�?�", "n'u");
		J2R.add("ん�?�", "n'e");
		J2R.add("ん�?�", "n'o");
		J2R.add("んや", "n'ya");
		J2R.add("んゆ", "n'yu");
		J2R.add("んよ", "n'yo");
		*/
		// we don't want the code to break if this source is not encoded with unicode
		J2R.add("\u30fc", "-"); // ー
		J2R.add("\u3001", ","); // �?
		J2R.add("\uff10", "0"); // �?
		J2R.add("\uff11", "1"); // １
		J2R.add("\uff12", "2"); // ２
		J2R.add("\uff13", "3"); // ３
		J2R.add("\uff14", "4"); // ４
		J2R.add("\uff15", "5"); // ５
		J2R.add("\uff16", "6"); // ６
		J2R.add("\uff17", "7"); // ７
		J2R.add("\uff18", "8"); // ８
		J2R.add("\uff19", "9"); // ９
		J2R.add("\u0078", "x"); // x
		J2R.add("\u3042", "a"); // �?�
		J2R.add("\u3044", "i"); // �?�
		J2R.add("\u3046", "u"); // �?�
		J2R.add("\u3048", "e"); // �?�
		J2R.add("\u304a", "o"); // �?�
		J2R.add("\u3094\u3041", "va"); // ゔ�??
		J2R.add("\u3094\u3043", "vi"); // ゔ�?�
		J2R.add("\u3094", "vu"); // ゔ
		J2R.add("\u3094\u3047", "ve"); // ゔ�?�
		J2R.add("\u3094\u3049", "vo"); // ゔ�?�
		J2R.add("\u304b", "ka"); // �?�
		J2R.add("\u304d", "ki"); // �??
		J2R.add("\u304d\u3083", "kya"); // �??ゃ
		J2R.add("\u304d\u3043", "kyi"); // �??�?�
		J2R.add("\u304d\u3085", "kyu"); // �??ゅ
		J2R.add("\u304f", "ku"); // �??
		J2R.add("\u3051", "ke"); // �?�
		J2R.add("\u3053", "ko"); // �?�
		J2R.add("\u304c", "ga"); // �?�
		J2R.add("\u304e", "gi"); // �?�
		J2R.add("\u3050", "gu"); // �??
		J2R.add("\u3052", "ge"); // �?�
		J2R.add("\u3054", "go"); // �?�
		J2R.add("\u304e\u3083", "gya"); // �?�ゃ
		J2R.add("\u304e\u3043", "gyi"); // �?��?�
		J2R.add("\u304e\u3085", "gyu"); // �?�ゅ
		J2R.add("\u304e\u3047", "gye"); // �?��?�
		J2R.add("\u304e\u3087", "gyo"); // �?�ょ
		J2R.add("\u3055", "sa"); // �?�
		J2R.add("\u3059", "su"); // �?�
		J2R.add("\u305b", "se"); // �?�
		J2R.add("\u305d", "so"); // �??
		J2R.add("\u3056", "za"); // �?�
		J2R.add("\u305a", "zu"); // �?�
		J2R.add("\u305c", "ze"); // �?�
		J2R.add("\u305e", "zo"); // �?�
		J2R.add("\u3057", "shi"); // �?�
		J2R.add("\u3057\u3083", "sha"); // �?�ゃ
		J2R.add("\u3057\u3085", "shu"); // �?�ゅ
		J2R.add("\u3057\u3087", "sho"); // �?�ょ
		J2R.add("\u3058", "ji"); // �?�
		J2R.add("\u3058\u3083", "ja"); // �?�ゃ
		J2R.add("\u3058\u3085", "ju"); // �?�ゅ
		J2R.add("\u3058\u3087", "jo"); // �?�ょ
		J2R.add("\u305f", "ta"); // �?�
		J2R.add("\u3061", "chi"); // �?�
		J2R.add("\u3061\u3083", "cha"); // �?�ゃ
		J2R.add("\u3061\u3085", "chu"); // �?�ゅ
		J2R.add("\u3061\u3087", "cho"); // �?�ょ
		J2R.add("\u3064", "tsu"); // �?�
		J2R.add("\u3066", "te"); // �?�
		J2R.add("\u3068", "to"); // �?�
		J2R.add("\u3060", "da"); // �?�
		J2R.add("\u3062", "di"); // �?�
		J2R.add("\u3065", "du"); // �?�
		J2R.add("\u3067", "de"); // �?�
		J2R.add("\u3069", "do"); // �?�
		J2R.add("\u306a", "na"); // �?�
		J2R.add("\u306b", "ni"); // �?�
		J2R.add("\u306b\u3083", "nya"); // �?�ゃ
		J2R.add("\u306b\u3085", "nyu"); // �?�ゅ
		J2R.add("\u306b\u3087", "nyo"); // �?�ょ
		J2R.add("\u306c", "nu"); // �?�
		J2R.add("\u306d", "ne"); // �?�
		J2R.add("\u306e", "no"); // �?�
		J2R.add("\u306f", "ha"); // �?�
		J2R.add("\u3072", "hi"); // �?�
		J2R.add("\u3075", "fu"); // �?�
		J2R.add("\u3078", "he"); // �?�
		J2R.add("\u307b", "ho"); // �?�
		J2R.add("\u3072\u3083", "hya"); // �?�ゃ
		J2R.add("\u3072\u3085", "hyu"); // �?�ゅ
		J2R.add("\u3072\u3087", "hyo"); // �?�ょ
		J2R.add("\u3075\u3041", "fa"); // �?��??
		J2R.add("\u3075\u3043", "fi"); // �?��?�
		J2R.add("\u3075\u3047", "fe"); // �?��?�
		J2R.add("\u3075\u3049", "fo"); // �?��?�
		J2R.add("\u3070", "ba"); // �?�
		J2R.add("\u3073", "bi"); // �?�
		J2R.add("\u3076", "bu"); // �?�
		J2R.add("\u3079", "be"); // �?�
		J2R.add("\u307c", "bo"); // �?�
		J2R.add("\u3073\u3083", "bya"); // �?�ゃ
		J2R.add("\u3073\u3085", "byu"); // �?�ゅ
		J2R.add("\u3073\u3087", "byo"); // �?�ょ
		J2R.add("\u3071", "pa"); // �?�
		J2R.add("\u3074", "pi"); // �?�
		J2R.add("\u3077", "pu"); // �?�
		J2R.add("\u307a", "pe"); // �?�
		J2R.add("\u307d", "po"); // �?�
		J2R.add("\u3074\u3083", "pya"); // �?�ゃ
		J2R.add("\u3074\u3085", "pyu"); // �?�ゅ
		J2R.add("\u3074\u3087", "pyo"); // �?�ょ
		J2R.add("\u307e", "ma"); // �?�
		J2R.add("\u307f", "mi"); // �?�
		J2R.add("\u3080", "mu"); // む
		J2R.add("\u3081", "me"); // �?
		J2R.add("\u3082", "mo"); // も
		J2R.add("\u307f\u3083", "mya"); // �?�ゃ
		J2R.add("\u307f\u3085", "myu"); // �?�ゅ
		J2R.add("\u307f\u3087", "myo"); // �?�ょ
		J2R.add("\u3084", "ya"); // や
		J2R.add("\u3086", "yu"); // ゆ
		J2R.add("\u3088", "yo"); // よ
		J2R.add("\u3089", "ra"); // ら
		J2R.add("\u308a", "ri"); // り
		J2R.add("\u308b", "ru"); // る
		J2R.add("\u308c", "re"); // れ
		J2R.add("\u308d", "ro"); // �?
		J2R.add("\u308a\u3083", "rya"); // りゃ
		J2R.add("\u308a\u3085", "ryu"); // りゅ
		J2R.add("\u308a\u3087", "ryo"); // りょ
		J2R.add("\u308f", "wa"); // �?
		J2R.add("\u3092", "wo"); // を
		J2R.add("\u3093", "n"); // ん
		J2R.add("\u3090", "wi"); // �?
		J2R.add("\u3091", "we"); // ゑ
		J2R.add("\u304d\u3047", "kye"); // �??�?�
		J2R.add("\u304d\u3087", "kyo"); // �??ょ
		J2R.add("\u3058\u3043", "jyi"); // �?��?�
		J2R.add("\u3058\u3047", "jye"); // �?��?�
		J2R.add("\u3061\u3043", "cyi"); // �?��?�
		J2R.add("\u3061\u3047", "che"); // �?��?�
		J2R.add("\u3072\u3043", "hyi"); // �?��?�
		J2R.add("\u3072\u3047", "hye"); // �?��?�
		J2R.add("\u3073\u3043", "byi"); // �?��?�
		J2R.add("\u3073\u3047", "bye"); // �?��?�
		J2R.add("\u3074\u3043", "pyi"); // �?��?�
		J2R.add("\u3074\u3047", "pye"); // �?��?�
		J2R.add("\u307f\u3047", "mye"); // �?��?�
		J2R.add("\u307f\u3043", "myi"); // �?��?�
		J2R.add("\u308a\u3043", "ryi"); // り�?�
		J2R.add("\u308a\u3047", "rye"); // り�?�
		J2R.add("\u306b\u3043", "nyi"); // �?��?�
		J2R.add("\u306b\u3047", "nye"); // �?��?�
		J2R.add("\u3057\u3043", "syi"); // �?��?�
		J2R.add("\u3057\u3047", "she"); // �?��?�
		J2R.add("\u3044\u3047", "ye"); // �?��?�
		J2R.add("\u3046\u3041", "wha"); // �?��??
		J2R.add("\u3046\u3049", "who"); // �?��?�
		J2R.add("\u3046\u3043", "wi"); // �?��?�
		J2R.add("\u3046\u3047", "we"); // �?��?�
		J2R.add("\u3094\u3083", "vya"); // ゔゃ
		J2R.add("\u3094\u3085", "vyu"); // ゔゅ
		J2R.add("\u3094\u3087", "vyo"); // ゔょ
		J2R.add("\u3059\u3041", "swa"); // �?��??
		J2R.add("\u3059\u3043", "swi"); // �?��?�
		J2R.add("\u3059\u3045", "swu"); // �?��?�
		J2R.add("\u3059\u3047", "swe"); // �?��?�
		J2R.add("\u3059\u3049", "swo"); // �?��?�
		J2R.add("\u304f\u3083", "qya"); // �??ゃ
		J2R.add("\u304f\u3085", "qyu"); // �??ゅ
		J2R.add("\u304f\u3087", "qyo"); // �??ょ
		J2R.add("\u304f\u3041", "qwa"); // �??�??
		J2R.add("\u304f\u3043", "qwi"); // �??�?�
		J2R.add("\u304f\u3045", "qwu"); // �??�?�
		J2R.add("\u304f\u3047", "qwe"); // �??�?�
		J2R.add("\u304f\u3049", "qwo"); // �??�?�
		J2R.add("\u3050\u3041", "gwa"); // �??�??
		J2R.add("\u3050\u3043", "gwi"); // �??�?�
		J2R.add("\u3050\u3045", "gwu"); // �??�?�
		J2R.add("\u3050\u3047", "gwe"); // �??�?�
		J2R.add("\u3050\u3049", "gwo"); // �??�?�
		J2R.add("\u3064\u3041", "tsa"); // �?��??
		J2R.add("\u3064\u3043", "tsi"); // �?��?�
		J2R.add("\u3064\u3047", "tse"); // �?��?�
		J2R.add("\u3064\u3049", "tso"); // �?��?�
		J2R.add("\u3066\u3083", "tha"); // �?�ゃ
		J2R.add("\u3066\u3043", "thi"); // �?��?�
		J2R.add("\u3066\u3085", "thu"); // �?�ゅ
		J2R.add("\u3066\u3047", "the"); // �?��?�
		J2R.add("\u3066\u3087", "tho"); // �?�ょ
		J2R.add("\u3068\u3041", "twa"); // �?��??
		J2R.add("\u3068\u3043", "twi"); // �?��?�
		J2R.add("\u3068\u3045", "twu"); // �?��?�
		J2R.add("\u3068\u3047", "twe"); // �?��?�
		J2R.add("\u3068\u3049", "two"); // �?��?�
		J2R.add("\u3062\u3083", "dya"); // �?�ゃ
		J2R.add("\u3062\u3043", "dyi"); // �?��?�
		J2R.add("\u3062\u3085", "dyu"); // �?�ゅ
		J2R.add("\u3062\u3047", "dye"); // �?��?�
		J2R.add("\u3062\u3087", "dyo"); // �?�ょ
		J2R.add("\u3067\u3083", "dha"); // �?�ゃ
		J2R.add("\u3067\u3043", "dhi"); // �?��?�
		J2R.add("\u3067\u3085", "dhu"); // �?�ゅ
		J2R.add("\u3067\u3047", "dhe"); // �?��?�
		J2R.add("\u3067\u3087", "dho"); // �?�ょ
		J2R.add("\u3069\u3041", "dwa"); // �?��??
		J2R.add("\u3069\u3043", "dwi"); // �?��?�
		J2R.add("\u3069\u3045", "dwu"); // �?��?�
		J2R.add("\u3069\u3047", "dwe"); // �?��?�
		J2R.add("\u3069\u3049", "dwo"); // �?��?�
		J2R.add("\u3075\u3045", "fwu"); // �?��?�
		J2R.add("\u3075\u3083", "fya"); // �?�ゃ
		J2R.add("\u3075\u3085", "fyu"); // �?�ゅ
		J2R.add("\u3075\u3087", "fyo"); // �?�ょ
		J2R.add("\u3041", "a"); // �??
		J2R.add("\u3043", "i"); // �?�
		J2R.add("\u3047", "e"); // �?�
		J2R.add("\u3045", "u"); // �?�
		J2R.add("\u3049", "o"); // �?�
		J2R.add("\u3083", "ya"); // ゃ
		J2R.add("\u3085", "yu"); // ゅ
		J2R.add("\u3087", "yo"); // ょ
		J2R.add("\u3063", ""); // �?�
		J2R.add("\u3095", "ka"); // ゕ
		J2R.add("\u3096", "ka"); // ゖ
		J2R.add("\u308e", "wa"); // ゎ
		J2R.add("\u3000", " "); // '　'
		J2R.add("\u3093\u3042", "n'a"); // ん�?�
		J2R.add("\u3093\u3044", "n'i"); // ん�?�
		J2R.add("\u3093\u3046", "n'u"); // ん�?�
		J2R.add("\u3093\u3048", "n'e"); // ん�?�
		J2R.add("\u3093\u304a", "n'o"); // ん�?�
		J2R.add("\u3093\u3084", "n'ya"); // んや
		J2R.add("\u3093\u3086", "n'yu"); // んゆ
		J2R.add("\u3093\u3088", "n'yo"); // んよ
	}
}

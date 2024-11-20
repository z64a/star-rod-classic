package game.shared.decoder;

import game.shared.struct.StructType;

public class Hint
{
	public final int address;

	private boolean toldType;
	private StructType suggestedType;

	private boolean toldLength;
	private int suggestedLength;

	private boolean toldName;
	private String suggestedName;

	private boolean toldWordsPerRow;
	private int suggestedWordsPerRow;

	public Hint(int address)
	{
		this.address = address;
		toldType = false;
		toldLength = false;
		toldName = false;
		toldWordsPerRow = false;
	}

	public boolean hasType()
	{
		return toldType;
	}

	public StructType getTypeSuggestion()
	{
		return suggestedType;
	}

	public void setTypeSuggestion(StructType type)
	{
		suggestedType = type;
		toldType = true;
	}

	public boolean hasLength()
	{
		return toldLength;
	}

	public int getLengthSuggestion()
	{
		return suggestedLength;
	}

	public void setLengthSuggestion(int length)
	{
		suggestedLength = length;
		toldLength = true;
	}

	public boolean hasName()
	{
		return toldName;
	}

	public String getNameSuggestion()
	{
		return suggestedName;
	}

	public void setNameSuggestion(String name)
	{
		suggestedName = name;
		toldName = true;
	}

	public boolean hasWordsPerRow()
	{
		return toldWordsPerRow;
	}

	public int getWordsPerRowSuggestion()
	{
		return suggestedWordsPerRow;
	}

	public void setWordsPerRowSuggestion(int wordsPerRow)
	{
		suggestedWordsPerRow = wordsPerRow;
		toldWordsPerRow = true;
	}
}

package game.shared.encoder;

import java.util.List;

import app.input.Line;

@FunctionalInterface
public interface IReplaceExpression
{
	public void replaceExpression(Line sourceLine, BaseDataEncoder encoder, String[] args, List<String> newTokenList);
}

package game.shared.encoder;

import static game.shared.encoder.ConstMath.TokenType.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Stack;
import java.util.regex.Pattern;

import app.Environment;
import app.input.InvalidInputException;
import game.shared.DataUtils;
import util.CaseInsensitiveMap;

public abstract class ConstMath
{
	public static void main(String[] args) throws InvalidInputException
	{
		Environment.initialize();
		//	parse(new DummyDataEncoder(), null, ".Song:FinalBowserBattle + 2*(sizeof:Npc-1)");
		//	System.out.println(parse(new DummyDataEncoder(), null, "100`/-5"));
		System.out.println(parse(new DummyDataEncoder(), null, "3*(-4)"));
		Environment.exit();
	}

	protected static enum TokenType
	{
		LITERAL, // 10`, 1F0, etc. -- sizeof:Struct and .Constant reduce to this
		LPAREN, // (
		RPAREN, // )
		ADD (1), // +
		SUB (1), // -
		MUL (0), // *
		DIV (0); // *

		private final boolean isOperator;
		private final int precedence;

		private TokenType()
		{
			this.isOperator = false;
			this.precedence = -1;
		}

		private TokenType(int precedence)
		{
			this.isOperator = true;
			this.precedence = precedence;
		}
	}

	private static class Token
	{
		private final TokenType cat;
		private int value;

		private Token(BaseDataEncoder encoder, CaseInsensitiveMap<Integer> overrideConstants, String s) throws InvalidInputException
		{
			if (s.isEmpty())
				throw new InvalidInputException("Could not parse math expression.");

			if ((s.length() > 7) && s.substring(0, 7).equalsIgnoreCase("sizeof:")) {
				cat = LITERAL;
				String typeName = s.substring(7);
				Integer size = encoder.getSizeOf(typeName);
				if (size == null)
					throw new InvalidInputException("Could not determine size for struct type: " + typeName);
				value = size;
				return;
			}

			if (s.charAt(0) == '.') {
				cat = LITERAL;
				if (overrideConstants != null && overrideConstants.containsKey(s)) {
					value = overrideConstants.get(s);
				}
				else {
					String constValue = encoder.resolveConstant(s, false);
					try {
						value = (int) Long.parseLong(constValue, 16);
					}
					catch (NumberFormatException e) {
						throw new InvalidInputException("Could not parse constant value: " + constValue);
					}
				}
				return;
			}

			switch (s) {
				case "+":
					cat = ADD;
					return;
				case "-":
					cat = SUB;
					return;
				case "*":
					cat = MUL;
					return;
				case "/":
					cat = DIV;
					return;
				case "(":
					cat = LPAREN;
					return;
				case ")":
					cat = RPAREN;
					return;
			}

			cat = LITERAL;
			try {
				value = DataUtils.parseIntString(s);
			}
			catch (NumberFormatException e) {
				throw new InvalidInputException("Invalid literal: " + s);
			}
		}
	}

	private static final Pattern TokenizerPattern = Pattern.compile("((?<=[\\(\\)*+-])|(?=[\\(\\)*/+-]))");

	public static int parse(BaseDataEncoder encoder, CaseInsensitiveMap<Integer> overrideConstants, String line) throws InvalidInputException
	{
		String[] tokens = TokenizerPattern.split(line);

		ArrayList<Token> rawTokenList = new ArrayList<>();
		for (int i = 0; i < tokens.length; i++)
			rawTokenList.add(new Token(encoder, overrideConstants, tokens[i].trim()));

		// distinguish between negative numbers and subtraction
		// "-5", "3--5", "100/-5", etc
		ArrayList<Token> tokenList = new ArrayList<>();
		for (int i = 0; i < tokens.length; i++) {
			Token prev = (i > 0) ? rawTokenList.get(i - 1) : null;
			Token next = (tokens.length > i + 1) ? rawTokenList.get(i + 1) : null;
			Token t = rawTokenList.get(i);

			if (t.cat == SUB && next != null && next.cat == LITERAL) {
				if (prev == null || prev.cat != LITERAL) {
					tokenList.add(new Token(encoder, overrideConstants, "-" + next.value));
					i++;
					continue;
				}
			}
			tokenList.add(t);
		}

		tokenList = toPostfix(tokenList);

		Stack<Token> evalStack = new Stack<>();
		for (Token token : tokenList) {
			if (token.cat.isOperator) {
				if (evalStack.size() < 2)
					throw new InvalidInputException("Invalid math expression: " + line.trim());

				int A = evalStack.pop().value;
				int B = evalStack.pop().value;

				switch (token.cat) {
					case ADD:
						token.value = B + A;
						break;
					case SUB:
						token.value = B - A;
						break; //note: order
					case MUL:
						token.value = B * A;
						break;
					case DIV:
						token.value = B / A;
						break; //note: order
					default:
						throw new IllegalStateException("Unimplemented operator: " + token.cat);
				}
			}
			evalStack.push(token);
		}

		/*
		for(String s : tokens)
			System.out.println("T: " + s);

		for(OffsetToken t : tokenList)
			System.out.printf("%-10s %s%n", t.cat, t.value != 0 ? "" + t.value : 0);
		*/

		if (evalStack.size() != 1)
			throw new InvalidInputException("Invalid math expression: " + line.trim());

		return evalStack.pop().value;
	}

	private static ArrayList<Token> toPostfix(ArrayList<Token> tokens) throws InvalidInputException
	{
		ArrayList<Token> output = new ArrayList<>();
		Stack<Token> operatorStack = new Stack<>();

		Iterator<Token> iter = tokens.iterator();
		while (iter.hasNext()) {
			Token t = iter.next();

			if (t.cat.isOperator) {
				while (true) {
					if (operatorStack.isEmpty())
						break;

					Token s = operatorStack.peek();
					if (s.cat == LPAREN)
						break;

					if (s.cat.precedence > t.cat.precedence)
						break;

					output.add(operatorStack.pop());
				}
				operatorStack.push(t);
				continue;
			}

			switch (t.cat) {
				case LITERAL:
					output.add(t);
					break;

				case LPAREN:
					operatorStack.push(t);
					break;

				case RPAREN:
					while (true) {
						if (operatorStack.isEmpty())
							throw new InvalidInputException("Found mismatched parentheses!");

						Token s = operatorStack.pop();
						if (s.cat == LPAREN)
							break;
						output.add(s);
					}
					break;

				case ADD:
				case SUB:
				case MUL:
				case DIV:
					throw new IllegalStateException();
			}
		}

		while (!operatorStack.isEmpty())
			output.add(operatorStack.pop());

		return output;
	}
}

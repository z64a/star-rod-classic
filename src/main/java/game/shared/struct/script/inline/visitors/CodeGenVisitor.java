package game.shared.struct.script.inline.visitors;

import java.util.ArrayList;
import java.util.Stack;

import game.shared.struct.script.inline.BinaryCommand;
import game.shared.struct.script.inline.BinaryCommand.CommandType;
import game.shared.struct.script.inline.ValueType;
import game.shared.struct.script.inline.generated.ASTAssign;
import game.shared.struct.script.inline.generated.ASTBinaryOperation;
import game.shared.struct.script.inline.generated.ASTConstant;
import game.shared.struct.script.inline.generated.ASTFloat;
import game.shared.struct.script.inline.generated.ASTInteger;
import game.shared.struct.script.inline.generated.ASTOffset;
import game.shared.struct.script.inline.generated.ASTPointer;
import game.shared.struct.script.inline.generated.ASTUnaryOperation;
import game.shared.struct.script.inline.generated.ASTVariable;
import game.shared.struct.script.inline.generated.SimpleNode;

public class CodeGenVisitor extends BaseVisitor
{
	private ArrayList<BinaryCommand> code = new ArrayList<>();
	private Stack<Object> operandStack = new Stack<>();

	public BinaryCommand[] getCommands()
	{
		BinaryCommand[] cmds = new BinaryCommand[code.size()];
		return code.toArray(cmds);
	}

	@Override
	public Object visit(ASTAssign node, Object data)
	{
		Object out = node.childrenAccept(this, data);

		operandStack.pop();
		code.add(new BinaryCommand(node.type == ValueType.FLOAT,
			node.type == ValueType.FLOAT ? CommandType.SETF : CommandType.SET,
			node.getVarName(), operandStack.size()));

		return out;
	}

	@Override
	public Object visit(ASTBinaryOperation node, Object data)
	{
		Object out = node.childrenAccept(this, data);
		operandStack.pop();
		operandStack.pop();

		switch (node.getOperator()) {
			case ADD:
				code.add(new BinaryCommand(node.type == ValueType.FLOAT,
					node.type == ValueType.FLOAT ? CommandType.ADDF : CommandType.ADD,
					operandStack.size(), operandStack.size() + 1));
				break;
			case SUB:
				code.add(new BinaryCommand(node.type == ValueType.FLOAT,
					node.type == ValueType.FLOAT ? CommandType.SUBF : CommandType.SUB,
					operandStack.size(), operandStack.size() + 1));
				break;
			case MUL:
				code.add(new BinaryCommand(node.type == ValueType.FLOAT,
					node.type == ValueType.FLOAT ? CommandType.MULF : CommandType.MUL,
					operandStack.size(), operandStack.size() + 1));
				break;
			case DIV:
				code.add(new BinaryCommand(node.type == ValueType.FLOAT,
					node.type == ValueType.FLOAT ? CommandType.DIVF : CommandType.DIV,
					operandStack.size(), operandStack.size() + 1));
				break;
			case MOD:
				code.add(new BinaryCommand(node.type == ValueType.FLOAT, CommandType.MOD,
					operandStack.size(), operandStack.size() + 1));
				break;
			case AND:
				code.add(new BinaryCommand(node.type == ValueType.FLOAT, CommandType.AND,
					operandStack.size(), operandStack.size() + 1));
				break;
			case OR:
				code.add(new BinaryCommand(node.type == ValueType.FLOAT, CommandType.OR,
					operandStack.size(), operandStack.size() + 1));
				break;
		}

		operandStack.push(node);
		return out;
	}

	@Override
	public Object visit(ASTUnaryOperation node, Object data)
	{
		Object out = node.childrenAccept(this, data);
		operandStack.pop();

		switch (node.getOperator()) {
			case INT:
				BinaryCommand castInt = new BinaryCommand(node.type == ValueType.FLOAT, CommandType.SET,
					operandStack.size(), operandStack.size());
				castInt.typeConversion = true;
				code.add(castInt);
				break;
			case FLT:
				BinaryCommand castFloat = new BinaryCommand(node.type == ValueType.FLOAT, CommandType.SETF,
					operandStack.size(), operandStack.size());
				castFloat.typeConversion = true;
				code.add(castFloat);
				break;
			case SQR:
				code.add(new BinaryCommand(node.type == ValueType.FLOAT,
					node.type == ValueType.FLOAT ? CommandType.MULF : CommandType.MUL,
					operandStack.size(), operandStack.size()));
				break;
			case NEG:
				throw new VisitorException("Encountered negation during code generation: " + node.toString());
		}

		operandStack.push(node);
		return out;
	}

	@Override
	public Object visit(ASTVariable node, Object data)
	{
		Object out = node.childrenAccept(this, data);

		if (node.type == ValueType.FLOAT)
			code.add(new BinaryCommand(node.type == ValueType.FLOAT, CommandType.SETF,
				operandStack.size(), node.getName()));
		else
			code.add(new BinaryCommand(node.type == ValueType.FLOAT, CommandType.SET,
				operandStack.size(), node.getName()));

		operandStack.push(node);
		return out;
	}

	@Override
	public Object visit(ASTPointer node, Object data)
	{
		Object out = node.childrenAccept(this, data);

		if (node.type == ValueType.FLOAT)
			code.add(new BinaryCommand(node.type == ValueType.FLOAT, CommandType.SETF,
				operandStack.size(), node.getName()));
		else
			code.add(new BinaryCommand(node.type == ValueType.FLOAT, CommandType.SET,
				operandStack.size(), node.getName()));

		operandStack.push(node);
		return out;
	}

	@Override
	public Object visit(ASTInteger node, Object data)
	{
		Object out = node.childrenAccept(this, data);

		// only use set_const if the value corresponds to a script variable
		if (node.intValue < -250000000 || node.intValue > -20000000)
			code.add(new BinaryCommand(false, CommandType.SET,
				operandStack.size(), String.format("%X", node.intValue)));
		else
			code.add(new BinaryCommand(false, CommandType.SET_CONST,
				operandStack.size(), String.format("%X", node.intValue)));

		operandStack.push(node);
		return out;
	}

	@Override
	public Object visit(ASTFloat node, Object data)
	{
		Object out = node.childrenAccept(this, data);

		code.add(new BinaryCommand(true, CommandType.SETF,
			operandStack.size(),
			String.format("*Fixed[%s]", node.floatValue)));

		operandStack.push(node);
		return out;
	}

	@Override
	public Object visit(SimpleNode node, Object data)
	{
		throw new VisitorException("Encountered unsupported node during code generation: " + node.toString());
	}

	@Override
	public Object visit(ASTOffset node, Object data)
	{
		throw new VisitorException("Encountered offset during code generation: " + node.toString());
	}

	@Override
	public Object visit(ASTConstant node, Object data)
	{
		throw new VisitorException("Encountered unresolved constant during code generation: " + node.toString());
	}
}

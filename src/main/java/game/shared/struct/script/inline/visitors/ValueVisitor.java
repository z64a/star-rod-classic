package game.shared.struct.script.inline.visitors;

import game.shared.struct.script.inline.ConstantDatabase;
import game.shared.struct.script.inline.ValueType;
import game.shared.struct.script.inline.generated.ASTAssign;
import game.shared.struct.script.inline.generated.ASTBinaryOperation;
import game.shared.struct.script.inline.generated.ASTConstant;
import game.shared.struct.script.inline.generated.ASTNode;
import game.shared.struct.script.inline.generated.ASTUnaryOperation;

public class ValueVisitor extends BaseVisitor
{
	@Override
	public Object visit(ASTAssign node, Object data)
	{
		return node.childrenAccept(this, data);
	}

	/*
	@Override
	public Object visit(ASTPointer node, Object data)
	{
		Object result = node.childrenAccept(this, data);
		node.assignValue((LocalDatabase)data);
		return result;
	}
	*/

	@Override
	public Object visit(ASTConstant node, Object data)
	{
		Object result = node.childrenAccept(this, data);
		node.assignValue((ConstantDatabase) data);
		return result;
	}

	@Override
	public Object visit(ASTBinaryOperation node, Object data)
	{
		Object result = node.childrenAccept(this, data);

		ASTNode operandL = (ASTNode) node.jjtGetChild(0);
		ASTNode operandR = (ASTNode) node.jjtGetChild(1);

		// error checking
		switch (node.getOperator()) {
			case ADD:
				break;
			case SUB:
				break;
			case MUL:
				break;

			case DIV:
				if (operandR.isZero())
					throw new VisitorException("Division by zero!");
				break;

			case MOD:
				if (operandR.isZero())
					throw new VisitorException("Division by zero in modulus operation!");
				break;

			case AND:
				if (node.type == ValueType.FLOAT)
					throw new VisitorException("Cannot use AND operation on floating point values!");
				break;

			case OR:
				if (node.type == ValueType.FLOAT)
					throw new VisitorException("Cannot use OR operation on floating point values!");
				break;
		}

		if (operandL.isConst && operandR.isConst) {
			node.isConst = true;

			// assign value
			switch (node.getOperator()) {
				case ADD:
					if (node.type == ValueType.FLOAT)
						node.floatValue = operandL.floatValue + operandR.floatValue;
					else
						node.intValue = operandL.intValue + operandR.intValue;
					break;
				case SUB:
					if (node.type == ValueType.FLOAT)
						node.floatValue = operandL.floatValue - operandR.floatValue;
					else
						node.intValue = operandL.intValue - operandR.intValue;
					break;
				case MUL:
					if (node.type == ValueType.FLOAT)
						node.floatValue = operandL.floatValue * operandR.floatValue;
					else
						node.intValue = operandL.intValue * operandR.intValue;
					break;

				case DIV:
					if (node.type == ValueType.FLOAT)
						node.floatValue = operandL.floatValue / operandR.floatValue;
					else
						node.intValue = operandL.intValue / operandR.intValue;
					break;

				case MOD:
					node.intValue = operandL.intValue % operandR.intValue;
					break;

				case AND:
					node.intValue = operandL.intValue & operandR.intValue;
					break;

				case OR:
					node.intValue = operandL.intValue | operandR.intValue;
					break;
			}
		}

		return result;
	}

	@Override
	public Object visit(ASTUnaryOperation node, Object data)
	{
		Object result = node.childrenAccept(this, data);

		ASTNode operand = (ASTNode) node.jjtGetChild(0);

		switch (node.getOperator()) {
			case FLT:
				if (operand.isConst) {
					node.isConst = true;
					if (operand.type == ValueType.FLOAT)
						node.floatValue = operand.floatValue;
					else
						node.floatValue = operand.intValue;
				}
				break;
			case INT:
				if (operand.isConst) {
					node.isConst = true;
					if (operand.type == ValueType.FLOAT)
						node.intValue = (int) operand.floatValue;
					else
						node.intValue = operand.intValue;
				}
				break;
			case NEG:
				if (operand.isConst) {
					node.isConst = true;
					if (operand.type == ValueType.FLOAT)
						node.floatValue = -operand.floatValue;
					else
						node.intValue = -operand.intValue;
				}
				break;
			case SQR:
				if (operand.isConst) {
					node.isConst = true;
					if (operand.type == ValueType.FLOAT)
						node.floatValue = operand.floatValue * operand.floatValue;
					else
						node.intValue = operand.intValue * operand.intValue;
				}
		}

		return result;
	}
}

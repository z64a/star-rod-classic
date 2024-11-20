package game.shared.struct.script.inline.visitors;

import game.shared.struct.script.inline.ValueType;
import game.shared.struct.script.inline.generated.ASTAssign;
import game.shared.struct.script.inline.generated.ASTBinaryOperation;
import game.shared.struct.script.inline.generated.ASTNode;
import game.shared.struct.script.inline.generated.ASTUnaryOperation;

public class TypeVisitor extends BaseVisitor
{
	private void propagateDown(ASTNode node)
	{
		for (int i = 0; i < node.jjtGetNumChildren(); i++) {
			ASTNode child = (ASTNode) node.jjtGetChild(i);
			if (child.type == ValueType.UNK) {
				child.type = node.type;
			}
			else if (child.isZero()) {
				child.type = node.type;
				child.floatValue = 0.0f;
				child.intValue = 0;
			}
		}
	}

	@Override
	public Object visit(ASTAssign node, Object data)
	{
		propagateDown(node);

		Object result = node.childrenAccept(this, data);

		ASTNode child = (ASTNode) node.jjtGetChild(0);
		if (child.type != node.type)
			throw new VisitorException("Assignment mixes float and integer!");

		return result;
	}

	@Override
	public Object visit(ASTBinaryOperation node, Object data)
	{
		propagateDown(node);

		Object result = node.childrenAccept(this, data);

		ASTNode operandL = (ASTNode) node.jjtGetChild(0);
		ASTNode operandR = (ASTNode) node.jjtGetChild(1);

		if (operandL.type.isResolved() && operandR.type.isResolved()) {
			if (operandL.type != operandR.type)
				throw new VisitorException(node.getOperator() + " operation mixes float and integer!");
		}
		else if (operandL.type.isResolved() && !operandR.type.isResolved()) {
			operandR.type = operandL.type;
		}
		else if (operandR.type.isResolved() && !operandL.type.isResolved()) {
			operandL.type = operandR.type;
		}

		if (node.type == ValueType.DC)
			node.type = operandL.type;

		return result;
	}

	@Override
	public Object visit(ASTUnaryOperation node, Object data)
	{
		ASTNode operand = (ASTNode) node.jjtGetChild(0);

		switch (node.getOperator()) {
			case FLT:
				if (operand.type == ValueType.UNK)
					operand.type = ValueType.DC;
				break;
			case INT:
				if (operand.type == ValueType.UNK)
					operand.type = ValueType.DC;
				break;

			case NEG:
			case SQR:
				propagateDown(node);
				break;
		}

		Object result = node.childrenAccept(this, data);

		switch (node.getOperator()) {
			case FLT:
			case INT:
				break;

			case NEG:
			case SQR:
				/*
				if(operand.type.isResolved()) {
					if(operandL.type != operandR.type)
						throw new VisitorException(node.getOperator() + " operation mixes float and integer!");
				}
				else if(operandL.type.isResolved() && !operandR.type.isResolved()) {
					operandR.type = operandL.type;
				}
				else if(operandR.type.isResolved() && !operandL.type.isResolved()) {
					operandL.type = operandR.type;
				}
				*/

				if (node.type == ValueType.DC)
					node.type = operand.type;

				if (operand.type != node.type)
					throw new VisitorException(node.getOperator() + " operation mixes float and integer!");
		}

		return result;
	}
}

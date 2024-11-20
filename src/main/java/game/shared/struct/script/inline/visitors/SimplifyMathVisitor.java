package game.shared.struct.script.inline.visitors;

import game.shared.struct.script.inline.BinaryOperator;
import game.shared.struct.script.inline.UnaryOperator;
import game.shared.struct.script.inline.ValueType;
import game.shared.struct.script.inline.generated.ASTBinaryOperation;
import game.shared.struct.script.inline.generated.ASTNode;
import game.shared.struct.script.inline.generated.ASTPointer;
import game.shared.struct.script.inline.generated.ASTUnaryOperation;
import game.shared.struct.script.inline.generated.ASTVariable;
import game.shared.struct.script.inline.generated.Node;
import game.shared.struct.script.inline.generated.SimpleNode;

public class SimplifyMathVisitor extends BaseVisitor
{
	@Override
	public Object visit(SimpleNode node, Object data)
	{
		Object out = node.childrenAccept(this, data);

		for (int i = 0; i < node.jjtGetNumChildren(); i++) {
			Node child = node.jjtGetChild(i);
			if (child instanceof ASTBinaryOperation) {
				tryCommutativeOperandReordering(node, i);
				if (tryCommutativeTreeRestructuring(node, i))
					continue;
				if (checkIdentityOperation(node, i))
					continue;
				if (checkPointerOffset(node, i))
					continue;
			}
			else if (child instanceof ASTUnaryOperation) {
				if (checkDegenerateCast(node, i))
					continue;
			}
		}

		return out;
	}

	private void tryCommutativeOperandReordering(SimpleNode parent, int index)
	{
		ASTBinaryOperation binary = (ASTBinaryOperation) parent.jjtGetChild(index);
		ASTNode operandL = (ASTNode) binary.jjtGetChild(0);
		ASTNode operandR = (ASTNode) binary.jjtGetChild(1);

		switch (binary.getOperator()) {
			case ADD:
			case MUL:
			case AND:
			case OR:
				// operation is commutative
				if (operandL.isConst && !operandR.isConst) {
					binary.jjtAddChild(operandR, 0);
					binary.jjtAddChild(operandL, 1);
				}
				break;

			case SUB:
			case DIV:
			case MOD:
				// operation is NOT commutative
				return;
		}
	}

	private boolean tryCommutativeTreeRestructuring(SimpleNode parent, int index)
	{
		ASTBinaryOperation binary = (ASTBinaryOperation) parent.jjtGetChild(index);
		ASTNode operandL = (ASTNode) binary.jjtGetChild(0);
		ASTNode operandR = (ASTNode) binary.jjtGetChild(1);

		if (!(operandL instanceof ASTBinaryOperation binaryL) || !operandR.isConst)
			return false;

		if (binaryL.getOperator() != binary.getOperator())
			return false;

		ASTNode childOperandL = (ASTNode) binaryL.jjtGetChild(0);
		ASTNode childOperandR = (ASTNode) binaryL.jjtGetChild(1);

		if (!childOperandR.isConst)
			return false;

		switch (binary.getOperator()) {
			case ADD:
			case MUL:
			case AND:
			case OR:
				// operation is commutative
				if (!childOperandL.isConst && childOperandR.isConst) {
					binary.jjtAddChild(childOperandL, 0);
					binary.jjtAddChild(operandL, 1);
					childOperandL.jjtSetParent(binary);

					binaryL.jjtAddChild(childOperandR, 0);
					binaryL.jjtAddChild(operandR, 0);
					childOperandR.jjtSetParent(binaryL);
					operandR.jjtSetParent(binaryL);

					return true;
				}
				break;

			case SUB:
			case DIV:
			case MOD:
				// operation is NOT commutative
				break;
		}

		return false;
	}

	private boolean checkIdentityOperation(SimpleNode parent, int index)
	{
		ASTBinaryOperation binary = (ASTBinaryOperation) parent.jjtGetChild(index);
		ASTNode operandL = (ASTNode) binary.jjtGetChild(0);
		ASTNode operandR = (ASTNode) binary.jjtGetChild(1);

		boolean promoteL = false;
		boolean promoteR = false;

		switch (binary.getOperator()) {
			case ADD:
				if (operandL.isZero())
					promoteR = true;
				if (operandR.isZero())
					promoteL = true;
				break;
			case SUB:
				if (operandR.isZero())
					promoteL = true;
				break;

			case MUL:
				if (operandL.isOne())
					promoteR = true;
				if (operandR.isOne())
					promoteL = true;
				break;
			case DIV:
				if (operandR.isOne())
					promoteL = true;
				break;
			case MOD:
				// never degenerate if valid
				break;

			case AND:
				if (operandL.isConst && (operandL.type == ValueType.INT) && operandL.intValue == 0xFFFFFFFF)
					promoteR = true;
				if (operandR.isConst && (operandR.type == ValueType.INT) && operandR.intValue == 0xFFFFFFFF)
					promoteL = true;
				break;
			case OR:
				if (operandL.isZero())
					promoteR = true;
				if (operandR.isZero())
					promoteL = true;
				break;
		}

		if (promoteR) {
			parent.jjtAddChild(operandR, index);
			operandR.jjtSetParent(parent);
			return true;
		}

		if (promoteL) {
			parent.jjtAddChild(operandL, index);
			operandL.jjtSetParent(parent);
			return true;
		}

		return false;
	}

	// remove cast(*Var) or unnecessary casting
	private boolean checkDegenerateCast(SimpleNode parent, int index)
	{
		ASTUnaryOperation unary = (ASTUnaryOperation) parent.jjtGetChild(index);
		ASTNode operand = (ASTNode) unary.jjtGetChild(0);
		boolean promote = false;

		if (unary.getOperator() == UnaryOperator.INT) {
			if (operand instanceof ASTVariable) {
				operand.type = ValueType.INT;
				promote = true;
			}
			else
				promote = (operand.type == ValueType.INT);
		}
		else if (unary.getOperator() == UnaryOperator.FLT) {
			if (operand instanceof ASTVariable) {
				operand.type = ValueType.FLOAT;
				promote = true;
			}
			else
				promote = (operand.type == ValueType.FLOAT);
		}

		if (promote) {
			parent.jjtAddChild(operand, index);
			operand.jjtSetParent(parent);
			return true;
		}

		return false;
	}

	// pointer - const
	// pointer + const
	// const + pointer
	private boolean checkPointerOffset(SimpleNode parent, int index)
	{
		ASTBinaryOperation binary = (ASTBinaryOperation) parent.jjtGetChild(index);
		ASTNode operandL = (ASTNode) binary.jjtGetChild(0);
		ASTNode operandR = (ASTNode) binary.jjtGetChild(1);

		if ((binary.getOperator() != BinaryOperator.ADD) && (binary.getOperator() != BinaryOperator.SUB))
			return false;

		ASTPointer ptrL = (operandL instanceof ASTPointer) ? (ASTPointer) operandL : null;
		ASTPointer ptrR = (operandR instanceof ASTPointer) ? (ASTPointer) operandR : null;
		Integer constValL = (operandL.isConst && (operandL.type == ValueType.INT)) ? operandL.intValue : null;
		Integer constValR = (operandR.isConst && (operandR.type == ValueType.INT)) ? operandR.intValue : null;

		boolean leftOp = (constValL != null && ptrR != null);
		boolean rightOp = (ptrL != null && constValR != null);

		if (!leftOp && !rightOp)
			return false; // not valid pattern

		boolean promoteL = false;
		boolean promoteR = false;

		if (leftOp) {
			// const + pointer
			if (binary.getOperator() == BinaryOperator.ADD) {
				ptrR.addOffset(constValL);
				promoteR = true;
			}
		}
		else if (rightOp) {
			// pointer + const
			if (binary.getOperator() == BinaryOperator.ADD) {
				ptrL.addOffset(constValR);
				promoteL = true;
			}
			// pointer - const
			else if (binary.getOperator() == BinaryOperator.SUB) {
				ptrL.addOffset(-constValR);
				promoteL = true;
			}
		}

		if (promoteR) {
			parent.jjtAddChild(operandR, index);
			operandR.jjtSetParent(parent);
			return true;
		}

		if (promoteL) {
			parent.jjtAddChild(operandL, index);
			operandL.jjtSetParent(parent);
			return true;
		}

		return false;
	}
}

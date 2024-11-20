package game.shared.struct.script.inline.visitors;

import game.shared.struct.script.inline.BinaryOperator;
import game.shared.struct.script.inline.UnaryOperator;
import game.shared.struct.script.inline.generated.ASTBinaryOperation;
import game.shared.struct.script.inline.generated.ASTChainedExpression;
import game.shared.struct.script.inline.generated.ASTInteger;
import game.shared.struct.script.inline.generated.ASTOffset;
import game.shared.struct.script.inline.generated.ASTUnaryOperation;
import game.shared.struct.script.inline.generated.Node;
import game.shared.struct.script.inline.generated.SimpleNode;

public class ChainExpansionVisitor extends BaseVisitor
{
	@Override
	public Object visit(SimpleNode node, Object data)
	{
		for (int i = 0; i < node.jjtGetNumChildren(); i++) {
			Node child = node.jjtGetChild(i);
			if (child instanceof ASTChainedExpression) {
				expandChild(node, i);
			}
			else if (child instanceof ASTOffset) {
				replaceOffset(node, i);
			}
			else if (child instanceof ASTUnaryOperation unaryOp) {
				if (unaryOp.getOperator() == UnaryOperator.NEG)
					replaceNegate(node, i);
			}
		}

		return node.childrenAccept(this, data);
	}

	private void replaceOffset(SimpleNode parent, int index)
	{
		ASTOffset offset = (ASTOffset) parent.jjtGetChild(index);

		Node operandL = offset.jjtGetChild(0);
		Node operandR = offset.jjtGetChild(1);

		ASTBinaryOperation binary = new ASTBinaryOperation();
		binary.setOperator(BinaryOperator.ADD);

		binary.jjtAddChild(operandL, 0);
		binary.jjtAddChild(operandR, 1);
		operandL.jjtSetParent(binary);
		operandR.jjtSetParent(binary);

		parent.jjtAddChild(binary, index);
		binary.jjtSetParent(parent);
	}

	private void replaceNegate(SimpleNode parent, int index)
	{
		ASTUnaryOperation unaryOp = (ASTUnaryOperation) parent.jjtGetChild(index);
		Node operand = unaryOp.jjtGetChild(0);

		ASTInteger zero = new ASTInteger();
		zero.setValue(0);

		ASTBinaryOperation binary = new ASTBinaryOperation();
		binary.setOperator(BinaryOperator.SUB);

		binary.jjtAddChild(zero, 0);
		zero.jjtSetParent(binary);
		binary.jjtAddChild(operand, 1);
		operand.jjtSetParent(binary);

		parent.jjtAddChild(binary, index);
		binary.jjtSetParent(parent);
	}

	private void expandChild(SimpleNode parent, int index)
	{
		ASTChainedExpression chained = (ASTChainedExpression) parent.jjtGetChild(index);

		int numOperations = chained.jjtGetNumChildren() - 1;
		ASTBinaryOperation lastOp = null;

		for (int i = 0; i < numOperations; i++) {
			ASTBinaryOperation binary = new ASTBinaryOperation();
			binary.setOperator(chained.getOperator(i));

			if (i == 0) {
				Node operandL = chained.jjtGetChild(i);
				Node operandR = chained.jjtGetChild(i + 1);

				binary.jjtAddChild(operandL, 0);
				binary.jjtAddChild(operandR, 1);
				operandL.jjtSetParent(binary);
				operandR.jjtSetParent(binary);
			}
			else {
				Node operandR = chained.jjtGetChild(i + 1);
				binary.jjtAddChild(lastOp, 0);
				binary.jjtAddChild(operandR, 1);
				lastOp.jjtSetParent(binary);
				operandR.jjtSetParent(binary);
			}

			lastOp = binary;
		}

		if (lastOp != null) {
			parent.jjtAddChild(lastOp, index);
			lastOp.jjtSetParent(parent);
		}
	}
}

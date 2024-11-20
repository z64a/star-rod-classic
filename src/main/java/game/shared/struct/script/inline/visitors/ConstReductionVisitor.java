package game.shared.struct.script.inline.visitors;

import game.shared.struct.script.inline.ValueType;
import game.shared.struct.script.inline.generated.ASTFloat;
import game.shared.struct.script.inline.generated.ASTInteger;
import game.shared.struct.script.inline.generated.ASTNode;
import game.shared.struct.script.inline.generated.SimpleNode;

public class ConstReductionVisitor extends BaseVisitor
{
	@Override
	public Object visit(SimpleNode node, Object data)
	{
		for (int i = 0; i < node.jjtGetNumChildren(); i++) {
			ASTNode child = (ASTNode) node.jjtGetChild(i);
			if (child.isConst) {
				if (child.type == ValueType.FLOAT) {
					// replace with float
					ASTFloat floatConst = new ASTFloat();
					floatConst.setValue(child.floatValue);

					node.jjtAddChild(floatConst, i);
					floatConst.jjtSetParent(node);
				}
				else if (child.type == ValueType.INT) {
					// replace with int
					ASTInteger intConst = new ASTInteger();
					intConst.setValue(child.intValue);

					node.jjtAddChild(intConst, i);
					intConst.jjtSetParent(node);
				}
			}
		}

		return node.childrenAccept(this, data);
	}
}

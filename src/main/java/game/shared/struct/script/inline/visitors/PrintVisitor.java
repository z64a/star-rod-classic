package game.shared.struct.script.inline.visitors;

import game.shared.struct.script.inline.generated.SimpleNode;

public class PrintVisitor extends BaseVisitor
{
	@Override
	public Object visit(SimpleNode node, Object data)
	{
		System.out.println(data + node.toString());
		data = data + "  ";
		return node.childrenAccept(this, data);
	}
}

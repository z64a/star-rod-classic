package game.map.tree;

import game.map.hit.Collider;

public class ColliderTreeModel extends MapObjectTreeModel<Collider>
{
	private static final long serialVersionUID = 1L;

	public ColliderTreeModel()
	{
		super(Collider.createDefaultRoot().getNode());
	}

	public ColliderTreeModel(MapObjectNode<Collider> root)
	{
		super(root);
	}

	@Override
	public void recalculateIndicies()
	{
		getRoot().reassignIndexDepthFirstPost(0);
	}

	@Override
	public Collider createNewObject()
	{
		return new Collider();
	}
}

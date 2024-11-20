package game.map.tree;

import game.map.hit.Zone;

public class ZoneTreeModel extends MapObjectTreeModel<Zone>
{
	private static final long serialVersionUID = 1L;

	public ZoneTreeModel()
	{
		super(Zone.createDefaultRoot().getNode());
	}

	public ZoneTreeModel(MapObjectNode<Zone> root)
	{
		super(root);
	}

	@Override
	public void recalculateIndicies()
	{
		getRoot().reassignIndexDepthFirstPost(0);
	}

	@Override
	public Zone createNewObject()
	{
		return new Zone();
	}
}

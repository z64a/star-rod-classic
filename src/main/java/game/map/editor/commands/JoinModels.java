package game.map.editor.commands;

import java.util.ArrayList;
import java.util.List;

import game.map.mesh.TexturedMesh;
import game.map.mesh.Triangle;
import game.map.shape.Model;
import game.map.shape.TriangleBatch;
import game.map.shape.commands.DisplayCommand;
import game.map.shape.commands.DisplayCommand.CmdType;
import util.identity.IdentityArrayList;

public class JoinModels extends AbstractCommand
{
	private final List<Model> models;
	private final ArrayList<List<DisplayCommand>> oldDisplayLists;
	private final TriangleBatch joinedBatch;
	private final boolean reuseBatch;
	private final IdentityArrayList<Triangle> oldTriangles;
	private final IdentityArrayList<Triangle> joinedTriangles;

	public JoinModels(List<Model> models)
	{
		super("Join Models");

		this.models = new ArrayList<>(models);
		oldDisplayLists = new ArrayList<>(models.size());

		for (Model mdl : models) {
			List<DisplayCommand> displayList = new ArrayList<>(mdl.getMesh().displayListModel.size());
			for (DisplayCommand cmd : mdl.getMesh().displayListModel)
				displayList.add(cmd);
			oldDisplayLists.add(displayList);
		}

		TexturedMesh mesh = models.get(0).getMesh();
		int last = mesh.displayListModel.size() - 1;
		reuseBatch = last >= 0 && mesh.displayListModel.get(last) instanceof TriangleBatch;
		joinedBatch = reuseBatch ? (TriangleBatch) mesh.displayListModel.get(last) : new TriangleBatch(mesh);
		oldTriangles = new IdentityArrayList<>(joinedBatch.triangles);
		joinedTriangles = new IdentityArrayList<>(oldTriangles);
		for (int i = 1; i < models.size(); i++)
			for (Triangle t : models.get(i).getMesh())
				joinedTriangles.add(t);
	}

	@Override
	public void exec()
	{
		super.exec();

		Model first = models.get(0);
		joinedBatch.triangles = new IdentityArrayList<>(joinedTriangles);
		// Joined geometry uses the destination's render state, so it can share its final draw batch.
		if (!reuseBatch && !joinedTriangles.isEmpty())
			first.getMesh().displayListModel.addElement(joinedBatch);

		for (int i = 1; i < models.size(); i++) {
			Model mdl = models.get(i);
			editor.map.remove(mdl);
			editor.selectionManager.deleteObject(mdl);
		}

		first.updateMeshHierarchy();
		first.dirtyAABB = true;
	}

	@Override
	public void undo()
	{
		super.undo();
		joinedBatch.triangles = new IdentityArrayList<>(oldTriangles);

		for (int i = models.size() - 1; i >= 0; i--) {
			Model mdl = models.get(i);

			if (i != 0) {
				editor.map.add(mdl);
				editor.selectionManager.createObject(mdl);
			}

			TexturedMesh mesh = mdl.getMesh();
			mesh.displayListModel.clear();

			for (DisplayCommand cmd : oldDisplayLists.get(i)) {
				mesh.displayListModel.addElement(cmd);
				if (cmd.getType() == CmdType.DrawTriangleBatch)
					((TriangleBatch) cmd).setParent(mesh);
			}

			mdl.updateMeshHierarchy();
			mdl.dirtyAABB = true;
		}
	}
}

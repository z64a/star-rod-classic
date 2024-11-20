package game.map;

import static app.Directories.MOD_MAP_BUILD;

import java.io.Externalizable;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import game.map.hit.Collider;
import game.map.hit.Zone;
import game.map.marker.Marker;
import game.map.marker.Marker.MarkerType;
import game.map.shape.Model;
import util.Logger;

public class MapIndex implements Externalizable
{
	private String mapName;
	private String bgName;
	private long lastModified;

	private HashMap<String, IndexedModel> modelNameLookup = new HashMap<>();
	private HashMap<Integer, IndexedModel> modelIDLookup = new HashMap<>();
	private HashMap<String, IndexedCollider> colliderNameLookup = new HashMap<>();
	private HashMap<Integer, IndexedCollider> colliderIDLookup = new HashMap<>();
	private HashMap<String, IndexedZone> zoneNameLookup = new HashMap<>();
	private HashMap<Integer, IndexedZone> zoneIDLookup = new HashMap<>();
	private HashMap<String, Marker> markerNameLookup = new HashMap<>();

	private ArrayList<Marker> entryList = new ArrayList<>();

	public static File getFile()
	{
		return new File(MOD_MAP_BUILD + "index.bin");
	}

	public MapIndex()
	{}

	public MapIndex(Map map)
	{
		if (map.source != null)
			this.lastModified = map.source.lastModified();
		this.mapName = map.name;
		this.bgName = map.hasBackground ? map.bgName : "";
		Map.validateObjectData(map);

		for (Model mdl : map.modelTree.getList()) {
			IndexedModel indexed = new IndexedModel(mdl);
			modelNameLookup.put(indexed.name, indexed);
			modelIDLookup.put(indexed.id, indexed);
		}

		for (Collider c : map.colliderTree.getList()) {
			IndexedCollider indexed = new IndexedCollider(c);
			colliderNameLookup.put(indexed.name, indexed);
			colliderIDLookup.put(indexed.id, indexed);
		}

		for (Zone z : map.zoneTree.getList()) {
			IndexedZone indexed = new IndexedZone(z);
			zoneNameLookup.put(indexed.name, indexed);
			zoneIDLookup.put(indexed.id, indexed);
		}

		for (Marker m : map.markerTree) {
			markerNameLookup.put(m.getName(), m);
			if (m.getType() == MarkerType.Entry) {
				m.entryID = entryList.size();
				entryList.add(m);
			}
			else
				m.entryID = -1;
		}
	}

	// dumper want to use this, but defer markers until later
	public void refreshMarkers(Map map)
	{
		markerNameLookup.clear();
		entryList.clear();

		for (Marker m : map.markerTree) {
			markerNameLookup.put(m.getName(), m);
			if (m.getType() == MarkerType.Entry) {
				m.entryID = entryList.size();
				entryList.add(m);
			}
			else
				m.entryID = -1;
		}
	}

	public long sourceLastModified()
	{
		return lastModified;
	}

	public String getMapName()
	{
		return mapName;
	}

	public String getBackgroundName()
	{
		return bgName;
	}

	private IndexedModel getModel(String name)
	{
		return modelNameLookup.get(name);
	}

	private IndexedModel getModel(int id)
	{
		return modelIDLookup.get(id);
	}

	private IndexedCollider getCollider(String name)
	{
		return colliderNameLookup.get(name);
	}

	private IndexedCollider getCollider(int id)
	{
		return colliderIDLookup.get(id);
	}

	private IndexedZone getZone(String name)
	{
		return zoneNameLookup.get(name);
	}

	private IndexedZone getZone(int id)
	{
		return zoneIDLookup.get(id);
	}

	public Marker getMarker(String name)
	{
		return markerNameLookup.get(name);
	}

	public int getEntry(String name)
	{
		Marker obj = getMarker(name);
		if (obj == null || obj.entryID < 0) {
			Logger.logWarning(name + " is not a valid entrance for " + mapName);
			return -1;
		}

		return obj.entryID;
	}

	public int getModelID(String name)
	{
		IndexedModel obj = getModel(name);
		return (obj == null) ? -1 : obj.id;
	}

	public int getColliderID(String name)
	{
		IndexedCollider obj = getCollider(name);
		return (obj == null) ? -1 : obj.id;
	}

	public int getZoneID(String name)
	{
		IndexedZone obj = getZone(name);
		return (obj == null) ? -1 : obj.id;
	}

	public String getModelName(int id)
	{
		IndexedModel obj = getModel(id);
		return (obj == null) ? null : obj.name;
	}

	public String getColliderName(int id)
	{
		IndexedCollider obj = getCollider(id);
		return (obj == null) ? null : obj.name;
	}

	public String getZoneName(int id)
	{
		IndexedZone obj = getZone(id);
		return (obj == null) ? null : obj.name;
	}

	public int getEntryCount()
	{
		return entryList.size();
	}

	public List<Marker> getEntryList()
	{
		return entryList;
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException
	{
		mapName = in.readUTF();
		bgName = in.readUTF();
		lastModified = in.readLong();

		int numModels = in.readInt();
		for (int i = 0; i < numModels; i++) {
			IndexedModel indexed = (IndexedModel) in.readObject();
			modelNameLookup.put(indexed.name, indexed);
			modelIDLookup.put(indexed.id, indexed);
		}

		int numColliders = in.readInt();
		for (int i = 0; i < numColliders; i++) {
			IndexedCollider indexed = (IndexedCollider) in.readObject();
			colliderNameLookup.put(indexed.name, indexed);
			colliderIDLookup.put(indexed.id, indexed);
		}

		int numZones = in.readInt();
		for (int i = 0; i < numZones; i++) {
			IndexedZone indexed = (IndexedZone) in.readObject();
			zoneNameLookup.put(indexed.name, indexed);
			zoneIDLookup.put(indexed.id, indexed);
		}

		int numMarkers = in.readInt();
		for (int i = 0; i < numMarkers; i++) {
			Marker indexed = (Marker) in.readObject();
			markerNameLookup.put(indexed.getName(), indexed);
		}

		int numEntries = in.readInt();
		for (int i = 0; i < numEntries; i++) {
			Marker indexed = (Marker) in.readObject();
			entryList.add(indexed);
		}
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException
	{
		out.writeUTF(mapName);
		out.writeUTF(bgName);
		out.writeLong(lastModified);

		Collection<IndexedModel> models = modelNameLookup.values();
		out.writeInt(models.size());
		for (IndexedModel indexed : models)
			out.writeObject(indexed);

		Collection<IndexedCollider> colliders = colliderNameLookup.values();
		out.writeInt(colliders.size());
		for (IndexedCollider indexed : colliders)
			out.writeObject(indexed);

		Collection<IndexedZone> zones = zoneNameLookup.values();
		out.writeInt(zones.size());
		for (IndexedZone indexed : zones)
			out.writeObject(indexed);

		Collection<Marker> markers = markerNameLookup.values();
		out.writeInt(markers.size());
		for (Marker indexed : markers)
			out.writeObject(indexed);

		out.writeInt(entryList.size());
		for (Marker indexed : entryList)
			out.writeObject(indexed);
	}

	public static class IndexedModel implements Externalizable
	{
		private int id;
		private String name;

		public IndexedModel()
		{}

		public IndexedModel(Model mdl)
		{
			this.id = mdl.getNode().getTreeIndex();
			this.name = mdl.getName();
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException
		{
			id = in.readInt();
			name = in.readUTF();
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException
		{
			out.writeInt(id);
			out.writeUTF(name);
		}
	}

	public static class IndexedCollider implements Externalizable
	{
		private int id;
		private String name;

		public IndexedCollider()
		{}

		public IndexedCollider(Collider c)
		{
			this.id = c.getNode().getTreeIndex();
			this.name = c.getName();
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException
		{
			id = in.readInt();
			name = in.readUTF();
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException
		{
			out.writeInt(id);
			out.writeUTF(name);
		}
	}

	public static class IndexedZone implements Externalizable
	{
		private int id;
		private String name;

		public IndexedZone()
		{}

		public IndexedZone(Zone z)
		{
			this.id = z.getNode().getTreeIndex();
			this.name = z.getName();
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException
		{
			id = in.readInt();
			name = in.readUTF();
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException
		{
			out.writeInt(id);
			out.writeUTF(name);
		}
	}

	/*
	public static class IndexedMarker implements Externalizable
	{
		private String name;
		private int entryID;
		private Vector3f pos;
		private double angle;

		public IndexedMarker()
		{}

		public IndexedMarker(Marker m)
		{
			this.name = m.name;
			this.entryID = m.entryID;
			pos = m.position.getVector();
			angle = m.yaw.getAngle();
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException
		{
			name = in.readUTF();
			entryID = in.readInt();
			angle = in.readDouble();
			pos = new Vector3f(in.readFloat(), in.readFloat(), in.readFloat());
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException
		{
			out.writeUTF(name);
			out.writeInt(entryID);
			out.writeDouble(angle);
			out.writeFloat(pos.x);
			out.writeFloat(pos.y);
			out.writeFloat(pos.z);
		}

		public String getName()
		{
			return name;
		}
	}
	*/
}

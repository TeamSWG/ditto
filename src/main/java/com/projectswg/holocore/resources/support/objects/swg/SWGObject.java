/***********************************************************************************
 * Copyright (c) 2018 /// Project SWG /// www.projectswg.com                       *
 *                                                                                 *
 * ProjectSWG is the first NGE emulator for Star Wars Galaxies founded on          *
 * July 7th, 2011 after SOE announced the official shutdown of Star Wars Galaxies. *
 * Our goal is to create an emulator which will provide a server for players to    *
 * continue playing a game similar to the one they used to play. We are basing     *
 * it on the final publish of the game prior to end-game events.                   *
 *                                                                                 *
 * This file is part of Holocore.                                                  *
 *                                                                                 *
 * --------------------------------------------------------------------------------*
 *                                                                                 *
 * Holocore is free software: you can redistribute it and/or modify                *
 * it under the terms of the GNU Affero General Public License as                  *
 * published by the Free Software Foundation, either version 3 of the              *
 * License, or (at your option) any later version.                                 *
 *                                                                                 *
 * Holocore is distributed in the hope that it will be useful,                     *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of                  *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the                   *
 * GNU Affero General Public License for more details.                             *
 *                                                                                 *
 * You should have received a copy of the GNU Affero General Public License        *
 * along with Holocore.  If not, see <http://www.gnu.org/licenses/>.               *
 ***********************************************************************************/
package com.projectswg.holocore.resources.support.objects.swg;

import com.projectswg.common.data.CRC;
import com.projectswg.common.data.encodables.oob.StringId;
import com.projectswg.common.data.location.Location;
import com.projectswg.common.data.location.Point3D;
import com.projectswg.common.data.location.Terrain;
import com.projectswg.common.data.swgfile.visitors.ObjectData.ObjectDataAttribute;
import com.projectswg.common.network.NetBuffer;
import com.projectswg.common.network.NetBufferStream;
import com.projectswg.common.network.packets.SWGPacket;
import com.projectswg.common.network.packets.swg.zone.baselines.Baseline.BaselineType;
import com.projectswg.common.persistable.Persistable;
import com.projectswg.holocore.ProjectSWG;
import com.projectswg.holocore.intents.support.objects.swg.ContainerTransferIntent;
import com.projectswg.holocore.resources.support.data.location.InstanceLocation;
import com.projectswg.holocore.resources.support.data.location.InstanceType;
import com.projectswg.holocore.resources.support.data.persistable.SWGObjectFactory;
import com.projectswg.holocore.resources.support.global.network.BaselineBuilder;
import com.projectswg.holocore.resources.support.global.network.BaselineObject;
import com.projectswg.holocore.resources.support.global.player.Player;
import com.projectswg.holocore.resources.support.objects.GameObjectType;
import com.projectswg.holocore.resources.support.objects.ObjectCreator;
import com.projectswg.holocore.resources.support.objects.awareness.AwarenessType;
import com.projectswg.holocore.resources.support.objects.awareness.ObjectAware;
import com.projectswg.holocore.resources.support.objects.permissions.ContainerPermissionsType;
import com.projectswg.holocore.resources.support.objects.permissions.ContainerResult;
import com.projectswg.holocore.resources.support.objects.swg.building.BuildingObject;
import com.projectswg.holocore.resources.support.objects.swg.cell.CellObject;
import com.projectswg.holocore.resources.support.objects.swg.cell.Portal;
import com.projectswg.holocore.resources.support.objects.swg.creature.CreatureObject;
import com.projectswg.holocore.utilities.ScheduledUtilities;
import me.joshlarson.jlcommon.log.Log;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class SWGObject extends BaselineObject implements Comparable<SWGObject>, Persistable {
	
	private final long 								objectId;
	private final InstanceLocation 					location		= new InstanceLocation();
	private final Set<SWGObject>					containedObjects= new CopyOnWriteArraySet<>();
	private final Map <String, SWGObject>			slots			= new ConcurrentHashMap<>();
	private final Map <String, String>				attributes		= Collections.synchronizedMap(new LinkedHashMap<>());
	private final Set<String>						slotsAvailable	= new CopyOnWriteArraySet<>();
	private final ObjectAware						awareness		= new ObjectAware(this);
	private final Map <ObjectDataAttribute, Object>	dataAttributes	= new EnumMap<>(ObjectDataAttribute.class);
	private final AtomicInteger						updateCounter	= new AtomicInteger(1);
	
	private GameObjectType gameObjectType	= GameObjectType.GOT_NONE;
	private ContainerPermissionsType	permissions		= ContainerPermissionsType.DEFAULT;
	private List <List <String>>		arrangement		= new ArrayList<>();
	private Player						owner			= null;
	
	private SWGObject	parent			= null;
	private StringId 	stringId		= new StringId("", "");
	private StringId 	detailStringId	= new StringId("", "");
	private String		template		= "";
	private int			crc				= 0;
	private String		objectName		= "";
	private int			loadRange		= 0;
	private int			volume			= 0;
	private float		complexity		= 1;
	private int     	containerType	= 0;
	private int			areaId			= -1;
	private int     	slotArrangement	= -1;
	private boolean		generated		= true;
	
	public SWGObject() {
		this(0, null);
	}
	
	public SWGObject(long objectId, BaselineType objectType) {
		super(objectType);
		this.objectId = objectId;
	}
	
	/**
	 * Adds the specified object to this object and places it in the appropriate slot if needed
	 * @param object Object to add to this container, which will either be put into the appropriate slot(s) or become a contained object
	 */
	public void addObject(SWGObject object) {
		int arrangementId = getArrangementId(object);
		object.setSlotArrangement(arrangementId);
		if (arrangementId == -1) {
			containedObjects.add(object);
			updateLoadRange();
			
			// We need to adjust the volume of our container accordingly!
			setVolume(getVolume() + object.getVolume() + 1);
		} else {
			handleSlotReplacement(object.parent, object, arrangementId);
			for (String requiredSlot : object.getArrangement().get(arrangementId - 4)) {
				setSlot(requiredSlot, object);
			}
		}
		
		onAddedChild(object);
		object.parent = this;
		object.setTerrain(getTerrain());
	}
	
	/**
	 * Removes the specified object from this current object.
	 * @param object Object to remove
	 */
	public void removeObject(SWGObject object) {
		if (object.getSlotArrangement() == -1) {
			containedObjects.remove(object);
			updateLoadRange();
			
			// We need to adjust the volume of our container accordingly!
			setVolume(getVolume() - object.getVolume() - 1);
		} else {
			for (String requiredSlot : object.getArrangement().get(object.getSlotArrangement() - 4)) {
				slots.remove(requiredSlot);
			}
		}
		
		// Remove as parent
		onRemovedChild(object);
		object.parent = null;
		object.slotArrangement = -1;
	}
	
	/**
	 * Moves the object to the new parent without invoking any intents. This is only meant for very specific services (awareness and buildouts)
	 * @param newParent the container to move this object to
	 * @return TRUE if the container changed, FALSE otherwise
	 */
	public boolean systemMove(SWGObject newParent) {
		SWGObject oldParent = parent;
		if (oldParent != newParent) {
			if (oldParent != null)
				oldParent.removeObject(this);
			if (newParent != null)
				newParent.addObject(this);
			return true;
		}
		return false;
	}
	
	/**
	 * Moves the object to the new parent and new location without invoking any intents. This is only meant for very specific services (awareness and buildouts)
	 * @param newParent the container to move this object to
	 * @param newLocation the location to move this object to
	 * @return TRUE if the container or location changed, FALSE otherwise
	 */
	public boolean systemMove(SWGObject newParent, Location newLocation) {
		SWGObject oldParent = parent;
		Location oldLocation = getLocation();
		if (oldParent != newParent) {
			if (oldParent != null)
				oldParent.removeObject(this);
			setLocation(newLocation);
			if (newParent != null)
				newParent.addObject(this);
		} else {
			setLocation(newLocation);
		}
		return oldParent != newParent || !oldLocation.equals(newLocation);
	}
	
	/**
	 * Attempts to move this object to the defined container without checking for permissions
	 * @param newParent the container to move this object to
	 * @return {@link ContainerResult}
	 */
	public void moveToContainer(SWGObject newParent) {
		SWGObject oldParent = parent;
		if (systemMove(newParent))
			ContainerTransferIntent.broadcast(this, oldParent, newParent);
	}
	
	/**
	 * Moves this object to the passed container if the requester has the MOVE permission for the container
	 *
	 * @param requester Object that is requesting to move the object, used for permission checking
	 * @param newParent Where this object should be moved to
	 * @return {@link ContainerResult}
	 */
	public ContainerResult moveToContainer(@NotNull SWGObject requester, SWGObject newParent) {
		ContainerResult result = isAllowedToMove(requester, newParent);
		if (result == ContainerResult.SUCCESS) {
			moveToContainer(newParent);
		}
		
		return result;
	}
	
	/**
	 * Checks if an object can be moved to the container by the requester
	 * @param requester Object that is requesting to move the object, used for permission checking
	 * @param container Where this object should be moved to
	 * @return {@link ContainerResult}
	 */
	protected ContainerResult isAllowedToMove(@NotNull SWGObject requester, SWGObject container) {
		if (!permissions.canMove(requester, this)) {
			Log.w("No permission 'MOVE' for requestor %s with object %s", requester, this);
			return ContainerResult.NO_PERMISSION;
		}
		if (container == null)
			return ContainerResult.SUCCESS;
		
		// Check if the requester has MOVE permissions to the destination container
		if (!permissions.canMove(requester, container)) {
			Log.w("No permission 'MOVE' for requestor %s with container %s", requester, this);
			return ContainerResult.NO_PERMISSION;
		}
		
		// Check if object can fit into container or slots
		int arrangementId = container.getArrangementId(this);
		if (arrangementId == -1) {
			if (container.getMaxContainerSize() <= container.getContainedObjects().size() && container.getMaxContainerSize() > 0) {
				Log.w("Unable to add object to container! Container Full. Max Size: %d", container.getMaxContainerSize());
				return ContainerResult.CONTAINER_FULL;
			}
		}
		return ContainerResult.SUCCESS;
	}
	
	protected void handleSlotReplacement(SWGObject oldParent, SWGObject obj, int arrangement) {
		for (String slot : obj.getArrangement().get(arrangement-4)) {
			SWGObject slotObj = getSlottedObject(slot);
			if (slotObj != null)
				slotObj.moveToContainer(oldParent);
		}
	}
	
	protected void onAddedChild(SWGObject child) {
		SWGObject parent = this.parent;
		if (parent != null)
			parent.onAddedChild(child);
	}
	
	protected void onRemovedChild(SWGObject child) {
		SWGObject parent = this.parent;
		if (parent != null)
			parent.onRemovedChild(child);
	}
	
	public boolean isVisible(SWGObject target) {
		if (target == null)
			return true;
		if (!permissions.canView(target, this))
			return false;
		if (getParent() != null)
			return getParent().isVisible(target);
		return true;
	}
	
	public boolean isLineOfSight(@NotNull SWGObject target) {
		SWGObject myParent = parent;
		SWGObject theirParent = target.parent;
		SWGObject superParent = null;
		if (myParent == theirParent)
			return true;
		
		Portal portal = null;
		if (myParent instanceof CellObject) {
			portal = theirParent instanceof CellObject ? ((CellObject) myParent).getPortalTo((CellObject) theirParent) : ((CellObject) myParent).getPortalTo(null);
			superParent = myParent.getParent();
		} else if (theirParent instanceof CellObject) {
			portal = ((CellObject) theirParent).getPortalTo(null);
			superParent = theirParent.getParent();
		}
		if (portal == null)
			return false; // If no portal, and they aren't in the same parent, then they can't see each other
		assert superParent != null;
		
		Point3D p1 = portal.getFrame1();
		Point3D p2 = portal.getFrame2();
		double headingToTarget = getWorldLocation().getHeadingTo(target.getWorldLocation());
		double headingToPortalLeft = getWorldLocation().getHeadingTo(Location.builder().setPosition(p1.getX(), p1.getY(), p1.getZ()).translateLocation(superParent.getLocation()).build());
		double headingToPortalRight = getWorldLocation().getHeadingTo(Location.builder().setPosition(p2.getX(), p2.getY(), p2.getZ()).translateLocation(superParent.getLocation()).build());
		if (headingToPortalLeft-headingToPortalRight > 180)
			headingToPortalRight += 360;
		if (headingToPortalLeft-headingToPortalRight < -180)
			headingToPortalLeft += 360;
		if (Math.abs(headingToPortalLeft-headingToTarget) > 180)
			headingToTarget += 360;
		
		if (headingToTarget > headingToPortalLeft)
			return headingToTarget < headingToPortalRight;
		return headingToTarget > headingToPortalRight;
	}
	
	/**
	 * Adds an attribute with the given value to this object. If the attribute exists, the old value is replaced with the new.
	 * @param attribute attribute name
	 * @param value new value for the attribute
	 */
	public void addAttribute(String attribute, String value) {
		if (attribute == null || value == null) {
			return;
		}

		attributes.put(attribute, value);
	}

	/**
	 * Gets the object that occupies the specified slot
	 * @param slotName
	 * @return The {@link SWGObject} occupying the slot. Returns null if there is nothing in the slot or it doesn't exist.
	 */
	public SWGObject getSlottedObject(String slotName) {
		return slots.get(slotName);
	}

	/**
	 * Gets a list of all the objects in the current container. This should only be used for viewing the objects
	 * in the current container.
	 * @return An unmodifiable {@link Collection} of {@link SWGObject}'s in the container
	 */
	public Collection<SWGObject> getContainedObjects() {
		return Collections.unmodifiableSet(containedObjects);
	}
	
	public void setSlots(@NotNull Collection<String> slots) {
		this.slotsAvailable.clear();
		this.slotsAvailable.addAll(slots);
	}

	public boolean hasSlot(@NotNull String slotName) {
		return slotsAvailable.contains(slotName);
	}
	
	public void setSlot(@NotNull String name, @NotNull SWGObject value) {
		slots.put(name, value);
	}
	
	public void clearSlot(@NotNull String name) {
		slots.remove(name);
	}
	
	@NotNull
	public Map<String, SWGObject> getSlots() {
		return Collections.unmodifiableMap(slots);
	}
	
	@NotNull
	public Collection<SWGObject> getSlottedObjects() {
		return Collections.unmodifiableCollection(slots.values());
	}
	
	public void setOwner(Player player) {
		if (owner == player)
			return;
		if (owner != null)
			owner.setCreatureObject(null);
		this.owner = player;
		if (player != null && this instanceof CreatureObject)
			player.setCreatureObject((CreatureObject) this);
		updateLoadRange();
	}
	
	public void setLocation(Location location) {
		if (parent != null && location.getTerrain() != parent.getTerrain())
			throw new IllegalArgumentException("Attempted to set different terrain from parent!");
		this.location.setLocation(location);
		updateChildrenTerrain();
	}
	
	public void setTerrain(@NotNull Terrain terrain) {
		if (parent != null && terrain != parent.getTerrain())
			throw new IllegalArgumentException("Attempted to set different terrain from parent!");
		if (location.getTerrain() != terrain) {
			location.setTerrain(terrain);
			updateChildrenTerrain();
		}
	}
	
	public void setPosition(@NotNull Terrain terrain, double x, double y, double z) {
		if (parent != null && terrain != parent.getTerrain())
			throw new IllegalArgumentException("Attempted to set different terrain from parent!");
		location.setPosition(terrain, x, y, z);
		updateChildrenTerrain();
	}
	
	public void setPosition(double x, double y, double z) {
		location.setPosition(x, y, z);
	}
	
	public void setOrientation(double oX, double oY, double oZ, double oW) {
		location.setOrientation(oX, oY, oZ, oW);
	}
	
	public void setHeading(double heading) {
		location.setHeading(heading);
	}
	
	public void setInstance(InstanceType instanceType, int instanceNumber) {
		location.setInstance(instanceType, instanceNumber);
	}
	
	private void updateChildrenTerrain() {
		Terrain terrain = getTerrain();
		containedObjects.forEach(child -> child.setTerrain(terrain));
		for (SWGObject child : slots.values()) {
			child.setTerrain(terrain);
		}
	}
	
	public void setStf(String stfFile, String stfKey) {
		this.stringId = new StringId(stfFile, stfKey);
	}
	
	public void setStringId(StringId stringId) {
		this.stringId = stringId;
	}
	
	public void setDetailStf(StringId detailStringId) {
		this.detailStringId = detailStringId;
	}
	
	public void setTemplate(String template) {
		this.template = template;
		this.crc = CRC.getCrc(template);
	}
	
	public void setObjectName(String name) {
		this.objectName = name;
	}
	
	public void setVolume(int volume) {
		this.volume = volume;
	}
	
	public void setComplexity(float complexity) {
		this.complexity = complexity;
	}
	
	public void setBuildoutAreaId(int areaId) {
		this.areaId = areaId;
	}
	
	public void setArrangement(List<List<String>> arrangement) {
		this.arrangement = arrangement;
	}
	
	public Player getOwner() {
		if (owner != null)
			return owner;

		SWGObject parent = this.parent;
		assert parent != this;
		if (parent != null)
			return parent.getOwner();
		
		return null;
	}
	
	public Player getOwnerShallow() {
		return owner;
	}
	
	@Nullable
	public SWGObject getParent() {
		return parent;
	}
	
	@Nullable
	public SWGObject getSuperParent() {
		SWGObject sParent = parent;
		if (sParent == null)
			return null;
		while (sParent.getParent() != null)
			sParent = sParent.getParent();
		return sParent;
	}
	
	public StringId getStringId() {
		return stringId;
	}
	
	public StringId getDetailStringId() {
		return detailStringId;
	}
	
	public String getTemplate() {
		return template;
	}
	
	public int getCrc() {
		return crc;
	}
	
	public long getObjectId() {
		return objectId;
	}
	
	public InstanceLocation getInstanceLocation() {
		return location;
	}
	
	public Location getLocation() {
		return location.getLocation();
	}
	
	public Location getWorldLocation() {
		return location.getWorldLocation(this);
	}
	
	public double getX() {
		return location.getPositionX();
	}
	
	public double getY() {
		return location.getPositionY();
	}
	
	public double getZ() {
		return location.getPositionZ();
	}
	
	public int getTruncX() {
		return (int) location.getPositionX();
	}
	
	public int getTruncY() {
		return (int) location.getPositionY();
	}
	
	public int getTruncZ() {
		return (int) location.getPositionZ();
	}
	
	@NotNull
	public Terrain getTerrain() {
		return location.getTerrain();
	}
	
	public String getObjectName() {
		return objectName;
	}
	
	public int getVolume() {
		return volume;
	}
	
	public float getComplexity() {
		return complexity;
	}
	
	public int getBuildoutAreaId() {
		return areaId;
	}
	
	public Object getDataAttribute(ObjectDataAttribute key) {
		return dataAttributes.get(key);
	}
	
	public int getDataIntAttribute(ObjectDataAttribute key) {
		return ((Number) dataAttributes.get(key)).intValue();
	}
	
	public long getDataLongAttribute(ObjectDataAttribute key) {
		return ((Number) dataAttributes.get(key)).longValue();
	}
	
	public double getDataDoubleAttribute(ObjectDataAttribute key) {
		return ((Number) dataAttributes.get(key)).doubleValue();
	}
	
	public String getDataTextAttribute(ObjectDataAttribute key) {
		return (String) dataAttributes.get(key);
	}
	
	public StringId getDataStfAttribute(ObjectDataAttribute key) {
		return (StringId) dataAttributes.get(key);
	}

	public void setDataAttribute(ObjectDataAttribute key, Object value) {
		dataAttributes.put(key, value);
	}
	
	public ObjectAware getAwareness() {
		return awareness;
	}
	
	public List<List<String>> getArrangement() {
		return arrangement;
	}

	public String getAttribute(String attribute) {
		return attributes.get(attribute);
	}

	public boolean hasAttribute(String attribute) {
		return attributes.containsKey(attribute);
	}
	
	public Map<String, String> getAttributes() {
		return Collections.unmodifiableMap(attributes);
	}

	public int getContainerType() {
		return containerType;
	}

	public void setContainerType(int containerType) {
		this.containerType = containerType;
	}

	public int getSlotArrangement() {
		return slotArrangement;
	}

	public void setSlotArrangement(int slotArrangement) {
		this.slotArrangement = slotArrangement;
	}
	
	public int getMaxContainerSize() {
		return getDataIntAttribute(ObjectDataAttribute.CONTAINER_VOLUME_LIMIT);
	}
	
	public int getNextUpdateCount() {
		return updateCounter.getAndIncrement();
	}
	
	public void setGenerated(boolean generated) {
		this.generated = generated;
	}
	
	public GameObjectType getGameObjectType() {
		return gameObjectType;
	}
	
	public void setGameObjectType(GameObjectType gameObjectType) {
		this.gameObjectType = gameObjectType;
	}
	
	public ContainerPermissionsType getContainerPermissions() {
		return permissions;
	}
	
	public void setContainerPermissions(ContainerPermissionsType permissions) {
		this.permissions = permissions;
	}
	
	public boolean isGenerated() {
		return generated;
	}
	
	public final int getLoadRange() {
		return loadRange;
	}
	
	public final void updateLoadRange() {
		loadRange = calculateLoadRange();
		SWGObject parent = this.parent;
		if (parent != null)
			parent.updateLoadRange();
	}
	
	protected int calculateLoadRange() {
		int loadRange = 0;
		int containedRange;
		for (SWGObject contained : containedObjects) {
			containedRange = contained.getLoadRange();
			if (containedRange > loadRange)
				loadRange = containedRange;
		}
		return loadRange;
	}
	
	/**
	 * Gets the arrangementId for the {@link SWGObject} for the current instance
	 * @param object
	 * @return Arrangement ID for the object
	 */
	public int getArrangementId(SWGObject object) {
		if (slotsAvailable.isEmpty() || object.getArrangement() == null)
			return -1;

		int arrangementId = 4;
		int filledId = -1;

		for (List<String> arrangementList : object.getArrangement()) {
			boolean passesCompletely = true;
			boolean isValid = true;
			for (String slot : arrangementList) {
				if (!hasSlot(slot)) {
					isValid = false;
					break;
				}  else if (getSlottedObject(slot) != null) {
					passesCompletely = false;
				}
			}
			if (isValid && passesCompletely)
				return arrangementId;
			else if (isValid)
				filledId = arrangementId;

			arrangementId++;
		}
		return filledId;
	}
	
	public void setAware(AwarenessType type, Collection<SWGObject> aware) {
		awareness.setAware(type, aware);
	}
	
	public Set<SWGObject> getObjectsAware() {
		return getAware(AwarenessType.OBJECT);
	}
	
	/**
	 * Called when this object has been moved somehow via awareness. This will
	 * not run on any buildout or snapshot object!
	 */
	public void onObjectMoved() {
		if (!isGenerated())
			return;
		Set<SWGObject> aware = getAware();
		// Running on a different thread to make sure this doesn't slow down awareness
		ScheduledUtilities.run(() -> onObjectMoved(aware), 0, TimeUnit.MILLISECONDS);
	}
	
	private void onObjectMoved(Set<SWGObject> aware) {
		for (SWGObject a : aware) {
			try {
				a.onObjectMoveInAware(this);
			} catch (Throwable t) {
				Log.e(t);
			}
		}
		for (SWGObject child : getContainedObjects()) {
			child.onObjectMoved(aware);
		}
	}
	
	/**
	 * Called when an object enters this object's awareness
	 * @param aware the object entering awareness
	 */
	public void onObjectEnterAware(SWGObject aware) {
		
	}
	
	/**
	 * Called when an object enters this object's awareness
	 * @param aware the object entering awareness
	 */
	public void onObjectLeaveAware(SWGObject aware) {
		
	}
	
	/**
	 * Called when an object moves within this object's awareness
	 * @param aware the object that moved
	 */
	public void onObjectMoveInAware(SWGObject aware) {
		
	}
	
	public Object getAwarenessLock() {
		return awareness;
	}
	
	public Set<Player> getObservers() {
		return awareness.getObservers();
	}
	
	public Set<SWGObject> getAware() {
		return awareness.getAware();
	}
	
	public Set<SWGObject> getAware(AwarenessType type) {
		return awareness.getAware(type);
	}
	
	public int sendObservers(SWGPacket ... SWGPackets) {
		int sent = 0;
		for (Player observer : getObservers()) {
			observer.sendPacket(SWGPackets);
			sent++;
		}
		return sent;
	}
	
	public int sendSelf(SWGPacket ... SWGPackets) {
		Player owner = getOwner();
		if (owner != null)
			owner.sendPacket(SWGPackets);
		return owner != null ? 1 : 0;
	}
	
	public boolean isInBuilding() {
		SWGObject parent = getParent();
		if (parent == null)
			return false;
		parent = parent.getParent();
		return parent instanceof BuildingObject;
	}

	@Override
	public String toString() {
		return String.format("%s[%d '%s' %s]", getClass().getSimpleName(), objectId, objectName, template.replace("object/", ""));
	}
	
	@Override
	public int compareTo(@NotNull SWGObject obj) {
		return Long.compare(objectId, obj.getObjectId());
	}
	
	@Override
	public boolean equals(Object o) {
		return o instanceof SWGObject && objectId == ((SWGObject) o).objectId;
	}
	
	@Override
	public int hashCode() {
		return Long.hashCode(objectId);
	}
	
	@Override
	protected void createBaseline3(Player target, BaselineBuilder bb) {
		super.createBaseline3(target, bb);
		bb.addFloat(complexity); // 0
		bb.addObject(stringId); // 1
		bb.addUnicode(objectName); // custom name -- 2
		bb.addInt(volume); // 3

		bb.incrementOperandCount(4);
	}
	
	@Override
	protected void createBaseline6(Player target, BaselineBuilder bb) {
		super.createBaseline6(target, bb);
		bb.addInt(ProjectSWG.getGalaxy().getId()); // 0
		bb.addObject(detailStringId); // 1
		
		bb.incrementOperandCount(2);
	}
	
	@Override
	protected void parseBaseline3(NetBuffer buffer) {
		super.parseBaseline3(buffer);
		complexity = buffer.getFloat();
		stringId = buffer.getEncodable(StringId.class);
		objectName = buffer.getUnicode();
		volume = buffer.getInt();
	}
	
	@Override
	protected void parseBaseline6(NetBuffer buffer) {
		super.parseBaseline6(buffer);
		buffer.getInt(); // Immutable ... can't change the galaxy id
		detailStringId = buffer.getEncodable(StringId.class);
	}
	
	/* Baseline send permissions based on SWGPacket observations:
	 * 
	 * Baseline1 sent if you have full permissions to the object.
	 * Baseline4 sent if you have full permissions to the object.
	 * 
	 * Baseline8 sent if you have some permissions to the object.
	 * Baseline9 sent if you have some permissions to the object.
	 * 
	 * Baseline3 always sent.
	 * Baseline6 always sent.
	 * 
	 * Baseline7 sent on using the object.
	 * 
	 * Only sent if they are defined (can still be empty if defined).
	 */
	
	@Override
	public void save(NetBufferStream stream) {
		stream.addByte(8);
		location.save(stream);
		boolean hasParent = parent != null;
		boolean hasGrandparent = hasParent && parent.getParent() instanceof BuildingObject && parent instanceof CellObject;
		stream.addBoolean(hasParent);
		if (hasParent) {
			SWGObject written = parent;
			if (hasGrandparent)
				written = parent.getParent();
			SWGObjectFactory.save(ObjectCreator.createObjectFromTemplate(written.getObjectId(), written.getTemplate()), stream);
			stream.addBoolean(hasGrandparent);
			if (hasGrandparent)
				stream.addInt(((CellObject) parent).getNumber());
		}
		stream.addAscii(permissions.name());
		stream.addBoolean(generated);
		stream.addUnicode(objectName);
		stringId.save(stream);
		detailStringId.save(stream);
		stream.addFloat(complexity);
		synchronized (attributes) {
			stream.addMap(attributes, (e) -> {
				stream.addAscii(e.getKey());
				stream.addAscii(e.getValue());
			});
		}
		Set<SWGObject> contained = new HashSet<>(containedObjects);
		contained.addAll(slots.values());
		contained.remove(null);
		stream.addList(contained, (c) -> SWGObjectFactory.save(c, stream));
	}
	
	@Override
	public void read(NetBufferStream stream) {
		switch(stream.getByte()) {
			default:
			case 8:
				readVersion8(stream);
				break;
			case 7:
				readVersion7(stream);
				break;
			case 6:
				readVersion6(stream);
				break;
			case 5:
				readVersion5(stream);
				break;
			case 4:
				readVersion4(stream);
				break;
			case 3:
				readVersion3(stream);
				break;
			case 2:
				readVersion2(stream);
				break;
			case 1:
				readVersion1(stream);
				break;
			case 0:
				readVersion0(stream);
				break;
		}
	}
	
	private void readVersion8(NetBufferStream stream) {
		location.read(stream);
		if (stream.getBoolean()) {
			parent = SWGObjectFactory.create(stream);
			if (stream.getBoolean()) {
				CellObject cell = (CellObject) ObjectCreator.createObjectFromTemplate("object/cell/shared_cell.iff");
				cell.setNumber(stream.getInt());
				parent.addObject(cell);
				parent = cell;
			}
		}
		permissions = ContainerPermissionsType.valueOf(stream.getAscii());
		generated = stream.getBoolean();
		objectName = stream.getUnicode();
		stringId.read(stream);
		detailStringId.read(stream);
		complexity = stream.getFloat();
		stream.getList((i) -> attributes.put(stream.getAscii(), stream.getAscii()));
		stream.getList((i) -> addObject(SWGObjectFactory.create(stream)));
	}
	
	private void readVersion7(NetBufferStream stream) {
		location.read(stream);
		if (stream.getBoolean()) {
			parent = SWGObjectFactory.create(stream);
			if (stream.getBoolean()) {
				CellObject cell = (CellObject) ObjectCreator.createObjectFromTemplate("object/cell/shared_cell.iff");
				cell.setNumber(stream.getInt());
				parent.addObject(cell);
				parent = cell;
			}
		}
		permissions = ContainerPermissionsType.valueOf(stream.getAscii());
		generated = stream.getAscii().equals("GENERATED");
		objectName = stream.getUnicode();
		stringId.read(stream);
		detailStringId.read(stream);
		complexity = stream.getFloat();
		stream.getList((i) -> attributes.put(stream.getAscii(), stream.getAscii()));
		stream.getList((i) -> addObject(SWGObjectFactory.create(stream)));
	}
	
	private void readVersion6(NetBufferStream stream) {
		location.read(stream);
		if (stream.getBoolean()) {
			parent = SWGObjectFactory.create(stream);
			if (stream.getBoolean()) {
				CellObject cell = (CellObject) ObjectCreator.createObjectFromTemplate("object/cell/shared_cell.iff");
				cell.setNumber(stream.getInt());
				parent.addObject(cell);
				parent = cell;
			}
		}
		permissions = ContainerPermissionsType.valueOf(stream.getAscii());
		generated = stream.getAscii().equals("GENERATED");
		objectName = stream.getUnicode();
		stringId.read(stream);
		detailStringId.read(stream);
		complexity = stream.getFloat();
		stream.getFloat(); // loadRange
		stream.getList((i) -> attributes.put(stream.getAscii(), stream.getAscii()));
		stream.getList((i) -> addObject(SWGObjectFactory.create(stream)));
	}
	
	private void readVersion5(NetBufferStream stream) {
		Location loc = new Location();
		loc.read(stream);
		location.setLocation(loc);
		areaId = stream.getInt();
		if (stream.getBoolean()) {
			parent = SWGObjectFactory.create(stream);
			if (stream.getBoolean()) {
				CellObject cell = (CellObject) ObjectCreator.createObjectFromTemplate("object/cell/shared_cell.iff");
				cell.setNumber(stream.getInt());
				parent.addObject(cell);
				parent = cell;
			}
		}
		permissions = ContainerPermissionsType.valueOf(stream.getAscii());
		generated = stream.getAscii().equals("GENERATED");
		objectName = stream.getUnicode();
		stringId.read(stream);
		detailStringId.read(stream);
		complexity = stream.getFloat();
		stream.getFloat(); // loadRange
		stream.getList((i) -> attributes.put(stream.getAscii(), stream.getAscii()));
		stream.getList((i) -> addObject(SWGObjectFactory.create(stream)));
	}
	
	private void readVersion4(NetBufferStream stream) {
		Location loc = new Location();
		loc.read(stream);
		location.setLocation(loc);
		new Location().read(stream); // ignored now
		if (stream.getBoolean()) {
			parent = SWGObjectFactory.create(stream);
			if (stream.getBoolean()) {
				CellObject cell = (CellObject) ObjectCreator.createObjectFromTemplate("object/cell/shared_cell.iff");
				cell.setNumber(stream.getInt());
				parent.addObject(cell);
				parent = cell;
			}
		}
		permissions = ContainerPermissionsType.valueOf(stream.getAscii());
		generated = stream.getAscii().equals("GENERATED");
		objectName = stream.getUnicode();
		stringId.read(stream);
		detailStringId.read(stream);
		complexity = stream.getFloat();
		stream.getFloat(); // loadRange
		stream.getList((i) -> attributes.put(stream.getAscii(), stream.getAscii()));
		stream.getList((i) -> addObject(SWGObjectFactory.create(stream)));
	}
	
	private void readVersion3(NetBufferStream stream) {
		Location loc = new Location();
		loc.read(stream);
		location.setLocation(loc);
		new Location().read(stream); // ignored now
		if (stream.getBoolean())
			parent = SWGObjectFactory.create(stream);
		permissions = ContainerPermissionsType.valueOf(stream.getAscii());
		generated = stream.getAscii().equals("GENERATED");
		objectName = stream.getUnicode();
		stringId.read(stream);
		detailStringId.read(stream);
		complexity = stream.getFloat();
		stream.getFloat(); // loadRange
		stream.getList((i) -> attributes.put(stream.getAscii(), stream.getAscii()));
		stream.getList((i) -> addObject(SWGObjectFactory.create(stream)));
	}
	
	private void readVersion2(NetBufferStream stream) {
		Location loc = new Location();
		loc.read(stream);
		location.setLocation(loc);
		if (stream.getBoolean())
			parent = SWGObjectFactory.create(stream);
		permissions = ContainerPermissionsType.valueOf(stream.getAscii());
		generated = stream.getAscii().equals("GENERATED");
		objectName = stream.getUnicode();
		stringId.read(stream);
		detailStringId.read(stream);
		complexity = stream.getFloat();
		stream.getFloat(); // loadRange
		stream.getList((i) -> attributes.put(stream.getAscii(), stream.getAscii()));
		stream.getList((i) -> addObject(SWGObjectFactory.create(stream)));
	}
	
	private void readVersion1(NetBufferStream stream) {
		Location loc = new Location();
		loc.read(stream);
		location.setLocation(loc);
		if (stream.getBoolean())
			parent = SWGObjectFactory.create(stream);
		permissions = ContainerPermissionsType.valueOf(stream.getAscii());
		generated = stream.getAscii().equals("GENERATED");
		objectName = stream.getUnicode();
		complexity = stream.getFloat();
		stream.getFloat(); // loadRange
		stream.getList((i) -> attributes.put(stream.getAscii(), stream.getAscii()));
		stream.getList((i) -> addObject(SWGObjectFactory.create(stream)));
	}
	
	private void readVersion0(NetBufferStream stream) {
		Location loc = new Location();
		loc.read(stream);
		location.setLocation(loc);
		if (stream.getBoolean())
			parent = SWGObjectFactory.create(stream);
		permissions = ContainerPermissionsType.valueOf(stream.getAscii());
		generated = stream.getAscii().equals("GENERATED");
		objectName = stream.getUnicode();
		// Ignore the saved volume - this is now set automagically in addObject() and removeObject()
		stream.getInt();
		complexity = stream.getFloat();
		stream.getFloat(); // loadRange
		stream.getList((i) -> attributes.put(stream.getAscii(), stream.getAscii()));
		stream.getList((i) -> addObject(SWGObjectFactory.create(stream)));
	}
	
}

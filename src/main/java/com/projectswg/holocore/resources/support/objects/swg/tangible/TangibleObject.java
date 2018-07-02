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
package com.projectswg.holocore.resources.support.objects.swg.tangible;

import com.projectswg.common.data.customization.CustomizationString;
import com.projectswg.common.data.customization.CustomizationVariable;
import com.projectswg.common.data.encodables.tangible.PvpFaction;
import com.projectswg.common.data.encodables.tangible.PvpFlag;
import com.projectswg.common.data.encodables.tangible.PvpStatus;
import com.projectswg.common.encoding.StringType;
import com.projectswg.common.network.NetBuffer;
import com.projectswg.common.network.NetBufferStream;
import com.projectswg.common.network.packets.swg.zone.baselines.Baseline.BaselineType;
import com.projectswg.holocore.intents.gameplay.gcw.faction.FactionIntent;
import com.projectswg.holocore.intents.gameplay.gcw.faction.FactionIntent.FactionIntentType;
import com.projectswg.holocore.intents.support.objects.swg.DestroyObjectIntent;
import com.projectswg.holocore.resources.support.data.collections.SWGSet;
import com.projectswg.holocore.resources.support.global.network.BaselineBuilder;
import com.projectswg.holocore.resources.support.global.player.Player;
import com.projectswg.holocore.resources.support.objects.swg.SWGObject;
import com.projectswg.holocore.resources.support.objects.swg.creature.CreatureObject;

import java.util.*;

public class TangibleObject extends SWGObject {
	
	private CustomizationString	appearanceData	= new CustomizationString();
	private int		maxHitPoints	= 1000;
	private int		components		= 0;
	private boolean	inCombat		= false;
	private int		condition		= 0;
	private int		pvpFlags		= 0;
	private PvpStatus pvpStatus = PvpStatus.COMBATANT;
	private PvpFaction pvpFaction = PvpFaction.NEUTRAL;
	private boolean	visibleGmOnly	= true;
	private byte []	objectEffects	= new byte[0];
	private int     optionFlags     = 0;
	private int		counter			= 0;
	private String	currentCity				= "";
	
	private Set<Long>	defenders	= new HashSet<>();
	
	public TangibleObject(long objectId) {
		this(objectId, BaselineType.TANO);
		addOptionFlags(OptionFlag.INVULNERABLE);
	}
	
	public TangibleObject(long objectId, BaselineType objectType) {
		super(objectId, objectType);
	}
	
	@Override
	public void moveToContainer(SWGObject newParent) {
		// Check if object is stackable
		if (newParent != null && counter > 0) {
			// Scan container for matching stackable item
			String ourTemplate = getTemplate();
			Map<String, String> ourAttributes = getAttributes();
			
			for (SWGObject candidate : newParent.getContainedObjects()) {
				String theirTemplate = candidate.getTemplate();
				Map<String, String> theirAttributes = candidate.getAttributes();
				
				if (this != candidate && candidate instanceof TangibleObject && ourTemplate.equals(theirTemplate) && ourAttributes.equals(theirAttributes)) {
					DestroyObjectIntent.broadcast(this);
					
					// Increase stack count on matching stackable item
					TangibleObject tangibleMatch = (TangibleObject) candidate;
					int theirCounter = tangibleMatch.getCounter();
					
					tangibleMatch.setCounter(theirCounter + counter);
					return;	// Stackable and matching item was found
				}
			}
		}
		
		super.moveToContainer(newParent);    // Not stackable, use default behavior
	}
	
	public int getMaxHitPoints() {
		return maxHitPoints;
	}
	
	public int getComponents() {
		return components;
	}
	
	public boolean isInCombat() {
		return inCombat;
	}
	
	public int getCondition() {
		return condition;
	}
	
	public void setPvpFlags(PvpFlag... pvpFlags) {
		for(PvpFlag pvpFlag : pvpFlags)
			this.pvpFlags |= pvpFlag.getBitmask();
		
		new FactionIntent(this, FactionIntentType.FLAGUPDATE).broadcast();
	}
	
	public void clearPvpFlags(PvpFlag... pvpFlags) {
		for(PvpFlag pvpFlag : pvpFlags)
			this.pvpFlags  &= ~pvpFlag.getBitmask();
		
		new FactionIntent(this, FactionIntentType.FLAGUPDATE).broadcast();
	}
	
	public boolean hasPvpFlag(PvpFlag pvpFlag) {
		return (pvpFlags & pvpFlag.getBitmask()) != 0;
	}
	
	public PvpStatus getPvpStatus() {
		return pvpStatus;
	}

	public void setPvpStatus(PvpStatus pvpStatus) {
		this.pvpStatus = pvpStatus;
		
		sendDelta(3, 5, pvpStatus.getValue());
	}
	
	public PvpFaction getPvpFaction() {
		return pvpFaction;
	}
	
	public void setPvpFaction(PvpFaction pvpFaction) {
		this.pvpFaction = pvpFaction;
		
		sendDelta(3, 4, pvpFaction.getCrc());
	}
	
	public int getPvpFlags() {
		return pvpFlags;
	}
	
	public boolean isVisibleGmOnly() {
		return visibleGmOnly;
	}
	
	public byte [] getObjectEffects() {
		return objectEffects;
	}
	
	public void putCustomization(String name, CustomizationVariable value) {
		appearanceData.put(name, value);
	}
	
	public CustomizationVariable getCustomization(String name) {
		return appearanceData.get(name);
	}
	
	public void setAppearanceData(CustomizationString appearanceData) {
		this.appearanceData = appearanceData;
		
		sendDelta(3, 6, appearanceData);
	}
	
	public void setMaxHitPoints(int maxHitPoints) {
		this.maxHitPoints = maxHitPoints;
		sendDelta(3, 11, maxHitPoints);
	}
	
	public void setComponents(int components) {
		this.components = components;
	}
	
	public void setInCombat(boolean inCombat) {
		this.inCombat = inCombat;
	}
	
	public void setCondition(int condition) {
		this.condition = condition;
	}
	
	public void setVisibleGmOnly(boolean visibleGmOnly) {
		this.visibleGmOnly = visibleGmOnly;
	}
	
	public void setObjectEffects(byte [] objectEffects) {
		this.objectEffects = objectEffects;
	}

	public void setOptionFlags(int optionsBitmask) {
		this.optionFlags = optionsBitmask;
	}

	public void setOptionFlags(OptionFlag ... options) {
		optionFlags = 0;
		addOptionFlags(options);
	}

	public void addOptionFlags(OptionFlag ... options) {
		for (OptionFlag flag : options) {
			optionFlags |= flag.getFlag();
		}
		sendDelta(3, 8, optionFlags);
	}

	public void toggleOptionFlags(OptionFlag ... options) {
		for (OptionFlag option : options) {
			optionFlags ^= option.getFlag();
		}
		sendDelta(3, 8, optionFlags);
	}

	public void removeOptionFlags(OptionFlag ... options) {
		for (OptionFlag option : options) {
			optionFlags &= ~option.getFlag();
		}
		sendDelta(3, 8, optionFlags);
	}

	public boolean hasOptionFlags(OptionFlag ... options) {
		for (OptionFlag option : options) {
			if ((optionFlags & option.getFlag()) == 0)
				return false;
		}
		return true;
	}
	
	public Set<OptionFlag> getOptionFlags() {
		return OptionFlag.toEnumSet(optionFlags);
	}
	
	public void addDefender(CreatureObject creature) {
		defenders.add(creature.getObjectId());
	}
	
	public void removeDefender(CreatureObject creature) {
		defenders.remove(creature.getObjectId());
	}
	
	public List<Long> getDefenders() {
		return new ArrayList<>(defenders);
	}
	
	public void clearDefenders() {
		defenders.clear();
	}
	
	public boolean hasDefenders() {
		return !defenders.isEmpty();
	}

	public int getCounter() {
		return counter;
	}

	public void setCounter(int counter) {
		this.counter = counter;
		sendDelta(3, 7, counter);
	}
	
	/**
	 *
	 * @param otherObject
	 * @return true if this object is an enemy of {@code otherObject}
	 */
	public boolean isEnemyOf(TangibleObject otherObject) {
		if (otherObject.hasOptionFlags(OptionFlag.INVULNERABLE)) {
			return false;
		}
		
		if (otherObject.hasPvpFlag(PvpFlag.ATTACKABLE)) {
			return true;
		}
		
		PvpFaction ourFaction = getPvpFaction();
		PvpFaction otherFaction = otherObject.getPvpFaction();
		
		if (ourFaction == PvpFaction.NEUTRAL || otherFaction == PvpFaction.NEUTRAL) {
			// Neutrals are always excluded from factional combat
			return false;
		}
		
		// At this point, neither are neutral
		
		if (ourFaction == otherFaction) {
			// Members of the same faction are not enemies
			return false;
		}
		
		// At this point, they're members of opposing factions
		
		PvpStatus ourStatus = getPvpStatus();
		PvpStatus otherStatus = otherObject.getPvpStatus();
		
		if (ourStatus == PvpStatus.ONLEAVE || otherStatus == PvpStatus.ONLEAVE) {
			// They're of opposing factions, but one of them on leave
			return false;
		}
		
		// At this point, they're both either combatant or special forces
		
		boolean ourPlayer = getSlottedObject("ghost") != null;
		boolean otherPlayer = otherObject.getSlottedObject("ghost") != null;
		
		if (ourPlayer && otherPlayer) {
			// Two players can only attack each other if both are Special Forces
			return ourStatus == PvpStatus.SPECIALFORCES && otherStatus == PvpStatus.SPECIALFORCES;
		} else {
			// At this point, we're dealing with player vs npc or npc vs npc
			// In this case, they just need to not be on leave and we've already established this
			return true;
		}
	}
	
	public String getCurrentCity() {
		return currentCity;
	}
	
	public void setCurrentCity(String currentCity) {
		this.currentCity = currentCity;
	}
	
	@Override
	protected void createBaseline3(Player target, BaselineBuilder bb) {
		super.createBaseline3(target, bb); // 4 variables - BASE3 (4)
		bb.addObject(appearanceData); // - 4
		bb.addInt(0); // Component customization (Set, Integer) - 5
			bb.addInt(0); //updates
		bb.addInt(optionFlags); // 6
		bb.addInt(counter); // Generic Counter -- use count and incap timer - 7
		bb.addInt(condition); // 8
		bb.addInt(maxHitPoints); // maxHitPoints - 9
		bb.addBoolean(visibleGmOnly); // isVisible - 10
		
		bb.incrementOperandCount(7);
	}
	
	@Override
	protected void parseBaseline3(NetBuffer buffer) {
		super.parseBaseline3(buffer);
		appearanceData.decode(buffer);
		SWGSet.getSwgSet(buffer, 3, 7, Integer.class);
		optionFlags = buffer.getInt();
		buffer.getInt();
		condition = buffer.getInt();
		maxHitPoints = buffer.getInt();
		visibleGmOnly = buffer.getBoolean();
	}
	
	@Override
	protected void parseBaseline6(NetBuffer buffer) {
		super.parseBaseline6(buffer);
		inCombat = buffer.getBoolean();
		defenders = SWGSet.getSwgSet(buffer, 6, 3, Long.TYPE);
		buffer.getInt();
		SWGSet.getSwgSet(buffer, 6, 5, StringType.ASCII);
		SWGSet.getSwgSet(buffer, 6, 6, StringType.ASCII);
	}
	
	@Override
	public void save(NetBufferStream stream) {
		super.save(stream);
		stream.addByte(1);
		appearanceData.save(stream);
		stream.addInt(maxHitPoints);
		stream.addInt(components);
		stream.addInt(condition);
		stream.addInt(pvpFlags);
		stream.addAscii(pvpStatus.name());
		stream.addAscii(pvpFaction.name());
		stream.addBoolean(visibleGmOnly);
		stream.addArray(objectEffects);
		stream.addInt(optionFlags);
	}
	
	@Override
	public void read(NetBufferStream stream) {
		super.read(stream);
		byte version = stream.getByte();
		appearanceData.read(stream);
		maxHitPoints = stream.getInt();
		components = stream.getInt();
		if (version == 0)
			stream.getBoolean();
		condition = stream.getInt();
		pvpFlags = stream.getInt();
		pvpStatus = PvpStatus.valueOf(stream.getAscii());
		pvpFaction = PvpFaction.valueOf(stream.getAscii());
		visibleGmOnly = stream.getBoolean();
		objectEffects = stream.getArray();
		optionFlags = stream.getInt();
	}

}

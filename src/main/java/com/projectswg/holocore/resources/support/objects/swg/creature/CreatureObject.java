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
package com.projectswg.holocore.resources.support.objects.swg.creature;

import com.projectswg.common.data.CRC;
import com.projectswg.common.data.encodables.tangible.Posture;
import com.projectswg.common.data.encodables.tangible.Race;
import com.projectswg.common.encoding.StringType;
import com.projectswg.common.network.NetBuffer;
import com.projectswg.common.network.NetBufferStream;
import com.projectswg.common.network.packets.swg.zone.baselines.Baseline.BaselineType;
import com.projectswg.common.network.packets.swg.zone.object_controller.PostureUpdate;
import com.projectswg.holocore.resources.gameplay.crafting.trade.TradeSession;
import com.projectswg.holocore.resources.gameplay.player.group.GroupInviterData;
import com.projectswg.holocore.resources.support.data.collections.SWGList;
import com.projectswg.holocore.resources.support.data.collections.SWGSet;
import com.projectswg.holocore.resources.support.data.persistable.SWGObjectFactory;
import com.projectswg.holocore.resources.support.global.network.BaselineBuilder;
import com.projectswg.holocore.resources.support.global.player.Player;
import com.projectswg.holocore.resources.support.objects.awareness.AwarenessType;
import com.projectswg.holocore.resources.support.objects.swg.SWGObject;
import com.projectswg.holocore.resources.support.objects.swg.player.PlayerObject;
import com.projectswg.holocore.resources.support.objects.swg.tangible.TangibleObject;
import com.projectswg.holocore.resources.support.objects.swg.weapon.WeaponObject;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class CreatureObject extends TangibleObject {
	
	private final CreatureObjectAwareness		awareness	= new CreatureObjectAwareness(this);
	private final CreatureObjectClientServerNP	creo4 		= new CreatureObjectClientServerNP();
	private final CreatureObjectSharedNP		creo6 		= new CreatureObjectSharedNP();
	private final Map<CreatureObject, Integer> damageMap 	= new HashMap<>();	
	
	private Posture	posture					= Posture.UPRIGHT;
	private Race	race					= Race.HUMAN_MALE;
	private double	height					= 0;
	private int		cashBalance				= 0;
	private int		bankBalance				= 0;
	private byte 	factionRank				= 0;
	private long 	ownerId					= 0;
	private int 	battleFatigue			= 0;
	private long 	statesBitmask			= 0;
	private SWGList<Integer>	wounds		= new SWGList<Integer>(3, 17);
	private long	lastTransform			= 0;
	private long	lastCombat				= 0;
	private TradeSession tradeSession		= null;
	
	private SWGSet<String> skills					= new SWGSet<>(1, 3, StringType.ASCII);
	
	private SWGList<Integer> baseAttributes			= new SWGList<>(1, 2);
	
	private List<CreatureObject> sentDuels			= new ArrayList<>();
	
	public CreatureObject(long objectId) {
		super(objectId, BaselineType.CREO);
		initBaseAttributes();
		initWounds();
		getAwareness().setAware(AwarenessType.SELF, List.of(this));
	}
	
	@Override
	public void onObjectEnterAware(SWGObject aware) {
		awareness.addAware(aware);
	}
	
	@Override
	public void onObjectLeaveAware(SWGObject aware) {
		awareness.removeAware(aware);
	}
	
	public void flushObjectsAware() {
		awareness.flushAware();
	}
	
	public void resetObjectsAware() {
		awareness.resetObjectsAware();
	}
	
	@Override
	public void addObject(SWGObject obj) {
		super.addObject(obj);
		if (obj.getSlotArrangement() != -1 && !(obj instanceof PlayerObject)) {
			addEquipment(obj);
		}
	}
	
	@Override
	public void removeObject(SWGObject obj) {
		super.removeObject(obj);
		removeEquipment(obj);
	}
	
	@NotNull
	public SWGObject getInventory() {
		SWGObject inventory = getSlottedObject("inventory");
		assert inventory != null;
		return inventory;
	}
	
	@Override
	protected void handleSlotReplacement(SWGObject oldParent, SWGObject obj, int arrangement) {
		SWGObject inventory = getSlottedObject("inventory");
		for (String slot : obj.getArrangement().get(arrangement-4)) {
			SWGObject slotObj = getSlottedObject(slot);
			if (slotObj != null) {
				slotObj.moveToContainer(inventory);
			}
		}
	}
	
	@Override
	protected void onAddedChild(SWGObject child) {
		super.onAddedChild(child);
		Set<SWGObject> children = new HashSet<>(getAwareness().getAware(AwarenessType.SELF));
		getAllChildren(children, child);
		getAwareness().setAware(AwarenessType.SELF, children);
	}
	
	@Override
	protected void onRemovedChild(SWGObject child) {
		super.onRemovedChild(child);
		Set<SWGObject> children = new HashSet<>(getAwareness().getAware(AwarenessType.SELF));
		{
			Set<SWGObject> removed = new HashSet<>();
			getAllChildren(removed, child);
			children.removeAll(removed);
			assert !removed.contains(this);
		}
		getAwareness().setAware(AwarenessType.SELF, children);
	}
	
	private void getAllChildren(Collection<SWGObject> children, SWGObject child) {
		children.add(child);
		for (SWGObject obj : child.getSlottedObjects())
			getAllChildren(children, obj);
		for (SWGObject obj : child.getContainedObjects())
			getAllChildren(children, obj);
	}
	
	@Override
	protected int calculateLoadRange() {
		if (isLoggedInPlayer())
			return 300;
		return super.calculateLoadRange();
	}
	
	@Override
	public boolean isVisible(SWGObject target) {
		return !isLoggedOutPlayer() && super.isVisible(target);
	}
	
	@Override
	public boolean isInCombat() {
		// CreatureObjects use CreatureState
		
		return isStatesBitmask(CreatureState.COMBAT);
	}
	
	@Override
	public void setInCombat(boolean inCombat) {
		// CreatureObjects use CreatureState
		if (inCombat) {
			setStatesBitmask(CreatureState.COMBAT);
		} else {
			clearStatesBitmask(CreatureState.COMBAT);
		}
	}
	
	private void addEquipment(SWGObject obj) {
		creo6.addEquipment(obj, this);
	}

	private void removeEquipment(SWGObject obj) {
		creo6.removeEquipment(obj, this);
	}
	
	public void addSkill(String... skillList) {
		synchronized (skills) {
			boolean delta = false;
			for (String skillName : skillList)
				delta |= skills.add(skillName);
			if (delta)
				skills.sendDeltaMessage(this);
		}
	}
	
	public boolean hasSkill(String skillName) {
		return skills.contains(skillName);
	}
	
	public Set<String> getSkills() {
		return Collections.unmodifiableSet(skills);
	}
	
	public void removeSkill(String... skillList) {
		synchronized (skills) {
			boolean delta = false;
			for (String skillName : skillList)
				delta |= skills.remove(skillName);
			if (delta)
				skills.sendDeltaMessage(this);
		}
	}
	
	public void handleLevelSkillMods(String modName, int modValue){
		adjustSkillmod(modName, 0, modValue);
	}
	
	public int getCashBalance() {
		return cashBalance;
	}

	public int getBankBalance() {
		return bankBalance;
	}
	
	public Posture getPosture() {
		return posture;
	}
	
	public Race getRace() {
		return race;
	}
	
	public double getMovementScale() {
		return creo4.getMovementScale();
	}
	
	public double getMovementPercent() {
		return creo4.getMovementPercent();
	}
	
	public double getWalkSpeed() {
		return creo4.getWalkSpeed();
	}
	
	public double getRunSpeed() {
		return creo4.getRunSpeed();
	}
	
	public double getAccelScale() {
		return creo4.getAccelScale();
	}
	
	public double getAccelPercent() {
		return creo4.getAccelPercent();
	}
	
	public double getTurnScale() {
		return creo4.getTurnScale();
	}
	
	public double getSlopeModAngle() {
		return creo4.getSlopeModAngle();
	}
	
	public double getSlopeModPercent() {
		return creo4.getSlopeModPercent();
	}
	
	public double getWaterModPercent() {
		return creo4.getWaterModPercent();
	}
	
	public double getHeight() {
		return height;
	}
	
	public long getPerformanceListenTarget() {
		return creo4.getPerformanceListenTarget();
	}
	
	public int getGuildId() {
		return creo6.getGuildId();
	}
	
	public short getLevel() {
		return creo6.getLevel();
	}
	
	public int getLevelHealthGranted() {
		return creo6.getLevelHealthGranted();
	}
	
	public CreatureDifficulty getDifficulty() {
		return creo6.getDifficulty();
	}
	
	public double getTimeSinceLastTransform() {
		return (System.nanoTime()-lastTransform)/1E6;
	}
	
	public double getTimeSinceLastCombat() {
		return (System.nanoTime() - lastCombat) / 1E6;
	}
	
	public PlayerObject getPlayerObject() {
		return (PlayerObject) getSlottedObject("ghost");
	}
	
	public boolean isPlayer() {
		return getSlottedObject("ghost") != null;
	}
	
	public boolean isLoggedInPlayer() {
		return getOwner() != null && isPlayer();
	}
	
	public boolean isLoggedOutPlayer() {
		return getOwner() == null && isPlayer();
	}	
	
	public TradeSession getTradeSession() {
		return tradeSession;
	}

	public void setTradeSession(TradeSession tradeSession) {
		this.tradeSession = tradeSession;
	}

	public void setPosture(Posture posture) {
		this.posture = posture;
		sendDelta(3, 11, posture.getId());
		if (isPlayer())
			sendObservers(new PostureUpdate(getObjectId(), posture));
	}
	
	public void setRace(Race race) {
		this.race = race;
	}
	
	public void setCashBalance(long cashBalance) {
		this.cashBalance = (int) cashBalance;
		sendDelta(1, 1, (int) cashBalance);
	}

	public void setBankBalance(long bankBalance) {
		this.bankBalance = (int) bankBalance;
		sendDelta(1, 0, (int) bankBalance);
	}
	
	/**
	 * Removes amount from cash first, then bank after. Returns true if the
	 * operation was successful
	 * @param amount the amount to remove
	 * @return TRUE if successfully withdrawn, FALSE otherwise
	 */
	public boolean removeFromCashAndBank(long amount) {
		long amountBalance = bankBalance + cashBalance;
		if (amountBalance < amount)
			return false;
		if (cashBalance < amount) {
			setBankBalance(bankBalance - (amount - cashBalance));
			setCashBalance(0);
		} else {
			setCashBalance(cashBalance - amount);
		}
		return true;
	}
	
	/**
	 * Removes amount from bank first, then cash after. Returns true if the
	 * operation was successful
	 * @param amount the amount to remove
	 * @return TRUE if successfully withdrawn, FALSE otherwise
	 */
	public boolean removeFromBankAndCash(long amount) {
		long amountBalance = bankBalance + cashBalance;
		if (amountBalance < amount)
			return false;
		if (bankBalance < amount) {
			setCashBalance(cashBalance - (amount - bankBalance));
			setBankBalance(0);
		} else {
			setBankBalance(bankBalance - amount);
		}
		return true;
	}
	
	/**
	 * Adds amount to cash balance.
	 * @param amount the amount to add
	 */
	public void addToCash(long amount) {
		setCashBalance(cashBalance + amount);
	}
	
	/**
	 * Adds amount to bank balance.
	 * @param amount the amount to add
	 */
	public void addToBank(long amount) {
		setBankBalance(bankBalance + amount);
	}
	
	public void setMovementScale(double movementScale) {
		creo4.setMovementScale(movementScale);
		sendDelta(4, 5, movementScale);
	}
	
	public void setMovementPercent(double movementPercent) {
		creo4.setMovementPercent(movementPercent);
		sendDelta(4, 4, movementPercent);
	}
	
	public void setWalkSpeed(double walkSpeed) {
		creo4.setWalkSpeed(walkSpeed);
		sendDelta(4, 11, walkSpeed);
	}
	
	public void setRunSpeed(double runSpeed) {
		creo4.setRunSpeed(runSpeed);
		sendDelta(4, 7, runSpeed);
	}
	
	public void setAccelScale(double accelScale) {
		creo4.setAccelScale(accelScale);
		sendDelta(4, 1, accelScale);
	}
	
	public void setAccelPercent(double accelPercent) {
		creo4.setAccelPercent(accelPercent);
		sendDelta(4, 0, accelPercent);
	}
	
	public void setTurnScale(double turnScale) {
		creo4.setTurnScale(turnScale);
		sendDelta(4, 10, turnScale);
	}
	
	public void setSlopeModAngle(double slopeModAngle) {
		creo4.setSlopeModAngle(slopeModAngle);
		sendDelta(4, 8, slopeModAngle);
	}
	
	public void setSlopeModPercent(double slopeModPercent) {
		creo4.setSlopeModPercent(slopeModPercent);
		sendDelta(4, 9, slopeModPercent);
	}
	
	public void setWaterModPercent(double waterModPercent) {
		creo4.setWaterModPercent(waterModPercent);
		sendDelta(4, 12, waterModPercent);
	}
	
	public void setHeight(double height) {
		this.height = height;
		sendDelta(3, 14, height);
	}
	
	public void setPerformanceListenTarget(long performanceListenTarget) {
		creo4.setPerformanceListenTarget(performanceListenTarget);
		sendDelta(4, 6, performanceListenTarget);
	}
	
	public void setGuildId(int guildId) {
		creo6.setGuildId(guildId);
		sendDelta(6, 9, guildId);
	}
	
	public void setLevel(int level) {
		creo6.setLevel(level);
		sendDelta(6, 2, (short) level);
	}
	
	public void setLevelHealthGranted(int levelHealthGranted) {
		creo6.setLevelHealthGranted(levelHealthGranted);
		sendDelta(6, 3, levelHealthGranted);
	}
	
	public void setDifficulty(CreatureDifficulty difficulty) {
		creo6.setDifficulty(difficulty);
		sendDelta(6, 21, difficulty.getDifficulty());
	}
	
	public void updateLastTransformTime() {
		lastTransform = System.nanoTime();
	}
	
	public void updateLastCombatTime() {
		lastCombat = System.nanoTime();
	}
	
	public String getMoodAnimation() {
		return creo6.getMoodAnimation();
	}

	public void setMoodAnimation(String moodAnimation) {
		creo6.setMoodAnimation(moodAnimation);
		sendDelta(6, 5, moodAnimation, StringType.ASCII);
	}

	public String getAnimation() {
		return creo6.getAnimation();
	}

	public void setAnimation(String animation) {
		creo6.setAnimation(animation);
		sendDelta(6, 4, animation, StringType.ASCII);
	}

	public WeaponObject getEquippedWeapon() {
		return creo6.getEquippedWeapon();
	}

	public void setEquippedWeapon(WeaponObject weapon) {
		WeaponObject equippedWeapon;
		
		if(weapon == null)
			equippedWeapon = (WeaponObject) getSlottedObject("default_weapon");
		else
			equippedWeapon = weapon;
		
		creo6.setEquippedWeapon(equippedWeapon);
		sendDelta(6, 6, equippedWeapon.getObjectId());
	}

	public byte getMoodId() {
		return creo6.getMoodId();
	}

	public void setMoodId(byte moodId) {
		creo6.setMoodId(moodId);
		sendDelta(6, 11, moodId);
	}

	public long getLookAtTargetId() {
		return creo6.getLookAtTargetId();
	}

	public void setLookAtTargetId(long lookAtTargetId) {
		creo6.setLookAtTargetId(lookAtTargetId);
		sendDelta(6, 10, lookAtTargetId);
	}

	public int getPerformanceCounter() {
		return creo6.getPerformanceCounter();
	}

	public void setPerformanceCounter(int performanceCounter) {
		creo6.setPerformanceCounter(performanceCounter);
		sendDelta(6, 12, performanceCounter);
	}

	public int getPerformanceId() {
		return creo6.getPerformanceId();
	}

	public void setPerformanceId(int performanceId) {
		creo6.setPerformanceId(performanceId);
		sendDelta(6, 13, performanceId);
	}

	public String getCostume() {
		return creo6.getCostume();
	}

	public void setCostume(String costume) {
		creo6.setCostume(costume);
		sendDelta(6, 17, costume, StringType.ASCII);
	}

	public long getGroupId() {
		return creo6.getGroupId();
	}

	public void updateGroupInviteData(Player sender, long groupId) {
		creo6.updateGroupInviteData(sender, groupId);
		sendDelta(6, 8, creo6.getInviterData());
	}

	public GroupInviterData getInviterData() {
		return creo6.getInviterData();
	}

	public void setGroupId(long groupId) {
		creo6.setGroupId(groupId);
		sendDelta(6, 7, groupId);
	}

	public byte getFactionRank() {
		return factionRank;
	}

	public void setFactionRank(byte factionRank) {
		this.factionRank = factionRank;
		sendDelta(3, 14, factionRank);
	}

	public long getOwnerId() {
		return ownerId;
	}

	public void setOwnerId(long ownerId) {
		this.ownerId = ownerId;
		sendDelta(3, 15, ownerId);
	}

	public int getBattleFatigue() {
		return battleFatigue;
	}

	public void setBattleFatigue(int battleFatigue) {
		this.battleFatigue = battleFatigue;
		sendDelta(3, 17, battleFatigue);
	}

	public long getStatesBitmask() {
		return statesBitmask;
	}
	
	public boolean isStatesBitmask(CreatureState ... states) {
		for (CreatureState state : states) {
			if ((statesBitmask & state.getBitmask()) == 0)
				return false;
		}
		return true;
	}

	public void setStatesBitmask(CreatureState ... states) {
		for (CreatureState state : states)
			statesBitmask |= state.getBitmask();
		sendDelta(3, 16, statesBitmask);
	}

	public void toggleStatesBitmask(CreatureState ... states) {
		for (CreatureState state : states)
			statesBitmask ^= state.getBitmask();
		sendDelta(3, 16, statesBitmask);
	}

	public void clearStatesBitmask(CreatureState ... states) {
		for (CreatureState state : states)
			statesBitmask &= ~state.getBitmask();
		sendDelta(3, 16, statesBitmask);
	}

	public void clearAllStatesBitmask() {
		statesBitmask = 0;
		sendDelta(3, 16, statesBitmask);
	}

	public synchronized void adjustSkillmod(String skillModName, int base, int modifier) {
		creo4.adjustSkillmod(skillModName, base, modifier, this);
	}
	
	public int getSkillModValue(String skillModName) {
		return creo4.getSkillModValue(skillModName);
	}
	
	public void addBuff(Buff buff) {
		creo6.putBuff(buff, this);
	}
	
	public Buff removeBuff(CRC buffCrc) {
		return creo6.removeBuff(buffCrc, this);
	}
	
	public boolean hasBuff(String buffName) {
		return getBuffEntries(buff -> CRC.getCrc(buffName.toLowerCase(Locale.ENGLISH)) == buff.getCrc()).count() > 0;
	}
	
	public Stream<Buff> getBuffEntries(Predicate<Buff> predicate) {
		return creo6.getBuffEntries(predicate);
	}
	
	public void adjustBuffStackCount(CRC buffCrc, int adjustment) {
		creo6.adjustBuffStackCount(buffCrc, adjustment, this);
	}
	
	public void setBuffDuration(CRC buffCrc, int playTime, int duration) {
		creo6.setBuffDuration(buffCrc, playTime, duration, this);
	}
	
	public boolean isVisible() {
		return creo6.isVisible();
	}

	public void setVisible(boolean visible) {
		creo6.setVisible(visible);
		sendDelta(6, 18, visible);
	}

	public boolean isPerforming() {
		return creo6.isPerforming();
	}

	public void setPerforming(boolean performing) {
		creo6.setPerforming(performing);
		sendDelta(6, 20, performing);
	}
	
	public int getHealth() {
		return creo6.getHealth();
	}
	
	public int getMaxHealth() {
		return creo6.getMaxHealth();
	}
	
	public int getBaseHealth() {
		synchronized (baseAttributes) {
			return baseAttributes.get(0);
		}
	}
	
	public int getAction() {
		return creo6.getAction();
	}
	
	public int getMaxAction() {
		return creo6.getMaxAction();
	}
	
	public int getBaseAction() {
		synchronized (baseAttributes) {
			return baseAttributes.get(2);
		}
	}
	
	public int getMind() {
		return creo6.getMind();
	}
	
	public int getMaxMind() {
		return creo6.getMaxMind();
	}
	
	public int getBaseMind() {
		synchronized (baseAttributes) {
			return baseAttributes.get(4);
		}
	}

	public void addAbility(String ... abilities) {
		creo4.addAbility(this, abilities);
	}
	
	public void removeAbility(String ... abilityName) {
		creo4.removeAbility(this, abilityName);
	}
	
	public boolean hasAbility(String abilityName) {
		return creo4.hasAbility(abilityName);
	}
	
	public Set<String> getAbilityNames() {
		return creo4.getAbilityNames();
	}

	public void setBaseHealth(int baseHealth) {
		synchronized(baseAttributes) {
			baseAttributes.set(0, baseHealth);
			baseAttributes.sendDeltaMessage(this);
		}
	}
	
	public void setHealth(int health) {
		creo6.setHealth(health, this);
	}
	
	public void modifyHealth(int mod) {
		creo6.modifyHealth(mod, this);
	}
	
	public void setMaxHealth(int maxHealth) {
		creo6.setMaxHealth(maxHealth, this);
	}
	
	public void setBaseAction(int baseAction) {
		synchronized(baseAttributes) {
			baseAttributes.set(2, baseAction);
			baseAttributes.sendDeltaMessage(this);
		}
	}
	
	public void setAction(int action) {
		creo6.setAction(action, this);
	}
	
	public void modifyAction(int mod) {
		creo6.modifyAction(mod, this);
	}
	
	public void setMaxAction(int maxAction) {
		creo6.setMaxAction(maxAction, this);
	}
	
	public void setMind(int mind) {
		creo6.setMind(mind, this);
	}
	
	public int modifyMind(int mod) {
		return creo6.modifyMind(mod, this);
	}
	
	public void setMaxMind(int maxMind) {
		creo6.setMaxMind(maxMind, this);
	}
	
	private void initBaseAttributes() {
		baseAttributes.add(0, 1000); // Health
		baseAttributes.add(1, 0);
		baseAttributes.add(2, 300); // Action
		baseAttributes.add(3, 0);
		baseAttributes.add(4, 300); // Mind
		baseAttributes.add(5, 0);
		baseAttributes.clearDeltaQueue();
	}
	private void initWounds() {
		wounds.add(0, 0);	// CU only has health wounds :-)
	}
	
	public void setHealthWounds(int healthWounds) {
		wounds.set(0, healthWounds);
	}
	
	public int getHealthWounds() {
		return wounds.get(0);
	}
	
	public Collection<SWGObject> getItemsByTemplate(String slotName, String template) {
		Collection<SWGObject> items = new ArrayList<>(getContainedObjects()); // We also search the creature itself - not just the inventory.
		SWGObject container = getSlottedObject(slotName);
		Collection<SWGObject> candidateChildren;
		
		for(SWGObject candidate : container.getContainedObjects()) {
			
			if(candidate.getTemplate().equals(template)) {
				items.add(candidate);
			} else {
				// check the children. This way we're also searching containers, such as backpacks.
				candidateChildren = candidate.getContainedObjects();
				
				for(SWGObject candidateChild : candidateChildren) {
					if(candidate.getTemplate().equals(template)) {
						items.add(candidateChild);
					}
				}
			}
		}
		return items;
	}
	
	public Map<CreatureObject, Integer> getDamageMap(){
		return Collections.unmodifiableMap(damageMap);
	}
	
	public CreatureObject getHighestDamageDealer(){
		synchronized (damageMap){
			return damageMap.keySet().stream().max(Comparator.comparingInt(damageMap::get)).orElse(null);
		}
	}
	
	public void handleDamage(CreatureObject attacker, int damage){
		synchronized (damageMap){
			if(damageMap.containsKey(attacker))
				damageMap.put(attacker, damageMap.get(attacker) + damage);
			else 
				damageMap.put(attacker, damage);
		}
	}

	public boolean isAttackable(CreatureObject otherObject) {
		Posture otherPosture = otherObject.getPosture();
		
		return isEnemyOf(otherObject) && otherPosture != Posture.INCAPACITATED && otherPosture != Posture.DEAD;
	}
	
	public boolean hasSentDuelRequestToPlayer(CreatureObject player) {
		return sentDuels.contains(player);
	}
	
	public boolean isDuelingPlayer(CreatureObject player) {
		return hasSentDuelRequestToPlayer(player) && player.hasSentDuelRequestToPlayer(this);
	}
	
	public void addPlayerToSentDuels(CreatureObject player) {
		sentDuels.add(player);
	}
	
	public void removePlayerFromSentDuels(CreatureObject player) {
		sentDuels.remove(player);
	}
	
	/**
	 * Members of the same faction might be enemies, if there are players
	 * involved.
	 * @param otherObject
	 * @return
	 */
	@Override
	public boolean isEnemyOf(TangibleObject otherObject) {
		if (!(otherObject instanceof CreatureObject)) {
			// If the other object isn't a creature, then our job here's done
			return super.isEnemyOf(otherObject);
		}
		
		// TODO bounty hunting
		// TODO pets, vehicles etc having same flagging as their owner
		// TODO guild wars
		
		if (isDuelingPlayer((CreatureObject)otherObject)) {
			// Dueling is an exception, since it allows allies to be enemies
			return true;
		}
		
		return super.isEnemyOf(otherObject);	// Default
	}
	
	@Override
	public void createBaseline1(Player target, BaselineBuilder bb) {
		super.createBaseline1(target, bb); // 0 variables
		bb.addInt(bankBalance); // 0
		bb.addInt(cashBalance); // 1
		bb.addObject(baseAttributes); // Attributes player has without any gear on -- 2
		bb.addObject(skills); // 3
		
		bb.incrementOperandCount(4);
	}
	
	@Override
	public void createBaseline3(Player target, BaselineBuilder bb) {
		super.createBaseline3(target, bb); // 11 variables - TANO3 (7) + BASE3 (4)
		bb.addByte(posture.getId()); // 13
		bb.addByte(factionRank); // 14
		bb.addLong(ownerId); // 15
		bb.addFloat((float) height); // 16
		bb.addInt(battleFatigue); // 17
		bb.addLong(statesBitmask); // 18
		bb.addObject(wounds);	// 19
		
		bb.incrementOperandCount(7);
	}
	
	@Override
	public void createBaseline4(Player target, BaselineBuilder bb) {
		super.createBaseline4(target, bb); // 0 variables
		creo4.createBaseline4(target, bb);
	}
	
	@Override
	public void createBaseline6(Player target, BaselineBuilder bb) {
		super.createBaseline6(target, bb); // 8 variables - TANO6 (6) + BASE6 (2)
		creo6.createBaseline6(target, bb);
	}
	
	@Override
	protected void parseBaseline1(NetBuffer buffer) {
		super.parseBaseline1(buffer);
		if (getStringId().toString().equals("@obj_n:unknown_object"))
			return;
		bankBalance = buffer.getInt();
		cashBalance = buffer.getInt();
		baseAttributes = SWGList.getSwgList(buffer, 1, 2, Integer.class);
		skills = SWGSet.getSwgSet(buffer, 1, 3, StringType.ASCII);
	}
	
	@Override
	protected void parseBaseline3(NetBuffer buffer) {
		super.parseBaseline3(buffer);
		posture = Posture.getFromId(buffer.getByte());
		factionRank = buffer.getByte();
		ownerId = buffer.getLong();
		height = buffer.getFloat();
		battleFatigue = buffer.getInt();
		statesBitmask = buffer.getLong();
		wounds = SWGList.getSwgList(buffer, 3, 17, Integer.class);
	}
	
	@Override
	protected void parseBaseline4(NetBuffer buffer) {
		super.parseBaseline4(buffer);
		creo4.parseBaseline4(buffer);
	}
	
	@Override
	protected void parseBaseline6(NetBuffer buffer) {
		super.parseBaseline6(buffer);
		creo6.parseBaseline6(buffer);
	}
	
	@Override
	public void save(NetBufferStream stream) {
		super.save(stream);
		stream.addByte(1);
		creo4.save(stream);
		creo6.save(stream);
		stream.addAscii(posture.name());
		stream.addAscii(race.name());
		stream.addFloat((float) height);
		stream.addInt(battleFatigue);
		stream.addInt(cashBalance);
		stream.addInt(bankBalance);
		stream.addLong(ownerId);
		stream.addLong(statesBitmask);
		stream.addByte(factionRank);
		synchronized (skills) {
			stream.addList(skills, stream::addAscii);
		}
		synchronized (baseAttributes) {
			stream.addList(baseAttributes, stream::addInt);
		}
		synchronized (wounds) {
			stream.addList(wounds, stream::addInt);
		}
	}
	
	@Override
	public void read(NetBufferStream stream) {
		super.read(stream);
		switch(stream.getByte()) {
			case 0: readVersion0(stream); break;
			case 1: readVersion1(stream); break;
		}
		
	}
	
	private void readVersion0(NetBufferStream stream) {
		creo4.read(stream);
		creo6.read(stream);
		posture = Posture.valueOf(stream.getAscii());
		race = Race.valueOf(stream.getAscii());
		height = stream.getFloat();
		battleFatigue = stream.getInt();
		cashBalance = stream.getInt();
		bankBalance = stream.getInt();
		ownerId = stream.getLong();
		statesBitmask = stream.getLong();
		factionRank = stream.getByte();
		if (stream.getBoolean()) {
			SWGObject defaultWeapon = SWGObjectFactory.create(stream);
			defaultWeapon.moveToContainer(this);	// The weapon will be moved into the default_weapon slot
		}
		stream.getList((i) -> skills.add(stream.getAscii()));
		stream.getList((i) -> baseAttributes.set(i, stream.getInt()));
		stream.getList((i) -> wounds.set(i, stream.getInt()));
	}
	
	private void readVersion1(NetBufferStream stream) {
		creo4.read(stream);
		creo6.read(stream);
		posture = Posture.valueOf(stream.getAscii());
		race = Race.valueOf(stream.getAscii());
		height = stream.getFloat();
		battleFatigue = stream.getInt();
		cashBalance = stream.getInt();
		bankBalance = stream.getInt();
		ownerId = stream.getLong();
		statesBitmask = stream.getLong();
		factionRank = stream.getByte();
		stream.getList((i) -> skills.add(stream.getAscii()));
		stream.getList((i) -> baseAttributes.set(i, stream.getInt()));
		stream.getList((i) -> wounds.set(i, stream.getInt()));
	}
	
}

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
package com.projectswg.holocore.services.gameplay.combat.buffs;

import com.projectswg.common.data.CRC;
import com.projectswg.common.network.packets.swg.zone.PlayClientEffectObjectMessage;
import com.projectswg.holocore.ProjectSWG;
import com.projectswg.holocore.intents.gameplay.combat.buffs.BuffIntent;
import com.projectswg.holocore.intents.support.global.zone.PlayerEventIntent;
import com.projectswg.holocore.intents.gameplay.player.experience.skills.SkillModIntent;
import com.projectswg.holocore.intents.gameplay.combat.CreatureKilledIntent;
import com.projectswg.holocore.resources.support.objects.swg.creature.Buff;
import com.projectswg.holocore.resources.support.objects.swg.creature.CreatureObject;
import com.projectswg.holocore.resources.support.data.server_info.StandardLog;
import com.projectswg.holocore.resources.gameplay.combat.buff.BuffData;
import com.projectswg.holocore.resources.gameplay.combat.buff.BuffMap;
import me.joshlarson.jlcommon.concurrency.BasicScheduledThread;
import me.joshlarson.jlcommon.control.IntentHandler;
import me.joshlarson.jlcommon.control.Service;
import me.joshlarson.jlcommon.log.Log;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Stream;

public class BuffService extends Service {
	
	/*
	 * TODO allow removal of buffs with BuffData where PLAYER_REMOVABLE == 1
	 * TODO remove buffs on respec. Listen for respec event and remove buffs with BuffData where
	 *      REMOVE_ON_RESPEC == 1
	 * TODO remove group buff(s) from receiver when distance between caster and receiver is 100m.
	 *      Perform same check upon zoning in. Skillmod1 effect name is "group"
	 */
	
	private final BasicScheduledThread timerCheckThread;
	private final Set<CreatureObject> monitored;
	private final BuffMap dataMap;	// All CRCs are lower-cased buff names!
	
	public BuffService() {
		timerCheckThread = new BasicScheduledThread("buff-timer-check", this::checkBuffTimers);
		monitored = new HashSet<>();
		dataMap = new BuffMap();
	}
	
	@Override
	public boolean initialize() {
		long startTime = StandardLog.onStartLoad("buffs");
		dataMap.load();
		StandardLog.onEndLoad(dataMap.size(), "buffs", startTime);
		return super.initialize();
	}
	
	@Override
	public boolean start() {
		timerCheckThread.startWithFixedRate(1000, 1000);
		return super.start();
	}
	
	@Override
	public boolean stop() {
		timerCheckThread.stop();
		return super.stop();
	}
	
	private void checkBuffTimers() {
		List<CreatureObject> creatures;
		synchronized (monitored) {
			creatures = new ArrayList<>(monitored);
		}
		for (CreatureObject creature : creatures) {
			removeAllBuffs(creature, creature.getBuffEntries(buff -> isBuffExpired(creature, buff)));
		}
	}
	
	@IntentHandler
	private void handleBuffIntent(BuffIntent bi) {
		BuffData buffData = getBuff(bi.getBuffName());
		Objects.requireNonNull(buffData, "No known buff: " + bi.getBuffName());
		assert buffData.getName().equals(bi.getBuffName()) : "BuffIntent name ["+bi.getBuffName()+"] does not match BuffData name ["+buffData.getName()+ ']';
		if (bi.isRemove()) {
			removeBuff(bi.getReceiver(), buffData, false);
		} else {
			addBuff(bi.getReceiver(), buffData, bi.getBuffer());
		}
	}
	
	@IntentHandler
	private void handlePlayerEventIntent(PlayerEventIntent pei) {
		CreatureObject creature = pei.getPlayer().getCreatureObject();
		
		switch (pei.getEvent()) {
			case PE_FIRST_ZONE:
				handleFirstZone(creature);
				break;
			case PE_DISAPPEAR:
				handleDisappear(creature);
				break;
			default:
				break;
		}
	}
	
	private void handleFirstZone(CreatureObject creature) {
		if (isCreatureBuffed(creature)) {
			addToMonitored(creature);
		}
	}
	
	private void handleDisappear(CreatureObject creature) {
		removeFromMonitored(creature);
		removeAllBuffs(creature, creature.getBuffEntries(buff -> true));
	}
	
	@IntentHandler
	private void handleCreatureKilledIntent(CreatureKilledIntent cki) {
		CreatureObject corpse = cki.getCorpse();
		
		// All buffs are removed upon death
		removeAllBuffs(corpse, corpse.getBuffEntries(buff -> true));
	}
	
	private void addToMonitored(CreatureObject creature) {
		synchronized (monitored) {
			monitored.add(creature);
		}
	}
	
	private void removeFromMonitored(CreatureObject creature) {
		synchronized (monitored) {
			monitored.remove(creature);
		}
	}
	
	private int calculatePlayTime(CreatureObject creature) {
		if (creature.isPlayer()) {
			creature.getPlayerObject().updatePlayTime();
			return creature.getPlayerObject().getPlayTime();
		}
		
		// NPC time is based on the current galactic time because NPCs don't have playTime
		return (int)ProjectSWG.getGalacticTime();
	}
	
	private boolean isBuffExpired(CreatureObject creature, Buff buff) {
		return buff.getDuration() >= 0 && calculatePlayTime(creature) >= buff.getEndTime();
	}
	
	private boolean isBuffInfinite(BuffData buffData) {
		return buffData.getDefaultDuration() < 0;
	}
	
	private boolean isCreatureBuffed(CreatureObject creature) {
		return creature.getBuffEntries(buff -> !isBuffInfinite(getBuff(buff))).count() > 0;
	}
	
	private void decayDuration(CreatureObject creature, Buff buff) {
		int newDuration = (int) (buff.getDuration() * 0.10);	// Duration decays with 10%
		
		creature.setBuffDuration(new CRC(buff.getCrc()), buff.getStartTime(), newDuration);
	}
	
	private void removeAllBuffs(CreatureObject creature, Stream<Buff> buffStream) {
		buffStream.forEach(buff -> removeBuff(creature, getBuff(buff), true));
	}
	
	private void addBuff(CreatureObject receiver, @NotNull BuffData buffData, CreatureObject buffer) {
		String groupName = buffData.getGroupName();
		Optional<Buff> groupBuff = receiver.getBuffEntries(buff -> groupName.equals(getBuff(buff).getGroupName())).findAny();
		
		int applyTime = calculatePlayTime(receiver);
		
		if (groupBuff.isPresent()) {
			Buff buff = groupBuff.get();
			
			if (buff.getCrc() == buffData.getCrc()) {
				if (isBuffInfinite(buffData)) {
					removeBuff(receiver, buffData, true);
				}
			} else {
				BuffData oldBuff = getBuff(buff);
				if (buffData.getGroupPriority() >= oldBuff.getGroupPriority()) {
					removeBuff(receiver, oldBuff, true);
					applyBuff(receiver, buffer, buffData, applyTime);
				}
			}
		} else {
			applyBuff(receiver, buffer, buffData, applyTime);
		}
	}
	
	private void removeBuff(CreatureObject creature, @NotNull BuffData buffData, boolean expired) {
		Optional<Buff> optionalEntry = creature.getBuffEntries(buff -> buff.getCrc() == buffData.getCrc()).findAny();
		if (!optionalEntry.isPresent())
			return; // Obique: Used to be an assertion, however if a service sends the removal after it expires it would assert - so I just removed it.
		
		Buff buff = optionalEntry.get();
		Buff removedBuff = creature.removeBuff(new CRC(buff.getCrc()));
		Objects.requireNonNull(removedBuff, "Buff must exist if being removed");
		
		checkSkillMods(buffData, creature, -removedBuff.getStackCount());
		checkCallback(buffData, creature);
		
		if (!isCreatureBuffed(creature)) {
			removeFromMonitored(creature);
		}
	}
	
	private void applyBuff(CreatureObject receiver, CreatureObject buffer, BuffData buffData, int applyTime) {
		// TODO stack counts upon add/remove need to be defined on a per-buff basis due to skillmod influence. Scripts might not be a bad idea.
		int stackCount = 1;
		int buffDuration = (int) buffData.getDefaultDuration();
		Log.d("buff %s applied to %s from %s; applyTime: %d, buffDuration: %d", buffData.getName(), receiver.getObjectName(), buffer.getObjectName(), applyTime, buffDuration);
		Buff buff = new Buff(buffData.getCrc(), applyTime + buffDuration, buffData.getEffectValue(0), buffDuration, buffer.getObjectId(), stackCount);
		
		checkSkillMods(buffData, receiver, 1);
		receiver.addBuff(buff);
		
		sendParticleEffect(buffData.getEffectFileName(), receiver, "");
		
		addToMonitored(receiver);
	}
	
	private void sendParticleEffect(String effectFileName, CreatureObject receiver, String hardPoint) {
		if (!effectFileName.isEmpty()) {
			receiver.sendObservers(new PlayClientEffectObjectMessage(effectFileName, hardPoint, receiver.getObjectId(), ""));
		}
	}
	
	private void checkCallback(BuffData buffData, CreatureObject creature) {
		String callback = buffData.getCallback();
		
		if (callback.equals("none")) {
			return;
		}
		
		if (dataMap.containsBuff(callback)) {
			addBuff(creature, getBuff(callback), creature);
		}
	}
	
	private void checkSkillMods(BuffData buffData, CreatureObject creature, int valueFactor) {
		/*
		 * TODO Check effectName == "group". If yes, every group member within 100m range (maybe
		 *      just the ones aware of the buffer) receive the buff. Once outside range, buff needs
		 *      removal
		 */
		for (int i = 0; i < 5; i++)
			sendSkillModIntent(creature, buffData.getEffectName(i), buffData.getEffectValue(i), valueFactor);
	}
	
	private void sendSkillModIntent(CreatureObject creature, String effectName, float effectValue, int valueFactor) {
		if (!effectName.isEmpty())
			new SkillModIntent(effectName, 0, (int) effectValue * valueFactor, creature).broadcast();
	}
	
	@Nullable
	private BuffData getBuff(String name) {
		return dataMap.getBuff(name);
	}
	
	@Nullable
	private BuffData getBuff(@NotNull Buff buff) {
		return dataMap.getBuff(buff.getCrc());
	}
	
}

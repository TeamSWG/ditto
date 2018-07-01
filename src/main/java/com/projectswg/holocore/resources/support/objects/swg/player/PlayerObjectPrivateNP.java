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
package com.projectswg.holocore.resources.support.objects.swg.player;

import com.projectswg.common.encoding.StringType;
import com.projectswg.common.network.NetBufferStream;
import com.projectswg.common.persistable.Persistable;
import com.projectswg.holocore.resources.support.data.collections.SWGList;
import com.projectswg.holocore.resources.support.data.collections.SWGMap;
import com.projectswg.holocore.resources.support.data.collections.SWGSet;
import com.projectswg.holocore.resources.support.global.network.BaselineBuilder;
import com.projectswg.holocore.resources.support.objects.swg.SWGObject;
import com.projectswg.holocore.resources.support.global.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

class PlayerObjectPrivateNP implements Persistable {
	
	private int 				experimentFlag		= 0;
	private int 				craftingStage		= 0;
	private long 				nearbyCraftStation	= 0;
	private SWGMap<Long, Integer> 	draftSchemMap	= new SWGMap<>(9, 3);
	private int 				experimentPoints	= 0;
	private SWGList<String> 	friendsList			= new SWGList<>(9, 7, StringType.ASCII);
	private SWGList<String> 	ignoreList			= new SWGList<>(9, 8, StringType.ASCII);
	private int 				languageId			= 0;
	private int					currentStomach		= 0;
	private int					maxStomach			= 100;
	private int					currentDrink		= 0;
	private int					maxDrink			= 100;
	private int					currentConsumable	= 0;
	private int					maxConsumable		= 100;
	private int					jediStateBitmask	= 0;
	
	public PlayerObjectPrivateNP() {
	
	}
	
	public boolean addFriend(String friend, SWGObject target) {
		friend = friend.toLowerCase(Locale.US);
		synchronized (friendsList) {
			if (friendsList.contains(friend))
				return false;
			friendsList.add(friend);
		}
		friendsList.sendDeltaMessage(target);
		return true;
	}
	
	public boolean removeFriend(String friend, SWGObject target) {
		boolean changed;
		synchronized (friendsList) {
			changed = friendsList.remove(friend.toLowerCase(Locale.US));
		}
		friendsList.sendDeltaMessage(target);
		return changed;
	}
	
	public boolean isFriend(String friend) {
		synchronized (friendsList) {
			return friendsList.contains(friend.toLowerCase(Locale.US));
		}
	}
	
	public List<String> getFriendsList() {
		return new ArrayList<>(friendsList);
	}
	
	public void sendFriendsList(SWGObject target) {
		friendsList.sendRefreshedListData(target);
	}
	
	public boolean addIgnored(String ignored, SWGObject target) {
		ignored = ignored.toLowerCase(Locale.US);
		synchronized (ignoreList) {
			if (ignoreList.contains(ignored))
				return false;
			ignoreList.add(ignored);
		}
		ignoreList.sendDeltaMessage(target);
		return true;
	}
	
	public boolean removeIgnored(String ignored, SWGObject target) {
		boolean changed;
		synchronized (ignoreList) {
			changed = ignoreList.remove(ignored.toLowerCase(Locale.US));
		}
		ignoreList.sendDeltaMessage(target);
		return changed;
	}
	
	public boolean isIgnored(String target) {
		return ignoreList.contains(target.toLowerCase(Locale.US));
	}
	
	public List<String> getIgnoreList() {
		return new ArrayList<>(ignoreList);
	}
	
	public void sendIgnoreList(SWGObject target) {
		ignoreList.sendRefreshedListData(target);
	}
	
	public void addDraftSchematic(long combinedCrc, int counter, SWGObject target) {
		draftSchemMap.put(combinedCrc, counter);
		draftSchemMap.sendDeltaMessage(target);
	}
	
	public void createBaseline9(Player target, BaselineBuilder bb) {
		bb.addInt(experimentFlag); // 0
		bb.addInt(craftingStage); // 1
		bb.addLong(nearbyCraftStation); // 2
		bb.addObject(draftSchemMap); // 3
		bb.addInt(0); // Might or might not be a list, two ints that are part of the same delta -- 4
		bb.addInt(0);
		bb.addInt(experimentPoints); // 5
		bb.addInt(0); // Accomplishment Counter - Pre-NGE? -- 6
		bb.addObject(friendsList); // 7
		bb.addObject(ignoreList); // 8
		bb.addInt(languageId); // 9
		bb.addInt(currentStomach); // Current Stomach -- 10
		bb.addInt(maxStomach); // Max Stomach -- 11
		bb.addInt(currentDrink); // Current Drink -- 12
		bb.addInt(maxDrink); // Max Drink -- 13
		bb.addInt(currentConsumable); // Current Consumable -- 14
		bb.addInt(maxConsumable); // Max Consumable -- 15
		bb.addInt(0); // Group Waypoints -- 16
		bb.addInt(0);
		bb.addInt(jediStateBitmask); // Jedi state bitmask -- 17
		
		bb.incrementOperandCount(18);
	}
	
	@Override
	public void save(NetBufferStream stream) {
		stream.addByte(0);
		stream.addInt(languageId);
		stream.addInt(currentStomach);
		stream.addInt(maxStomach);
		stream.addInt(currentDrink);
		stream.addInt(maxDrink);
		stream.addInt(currentConsumable);
		stream.addInt(maxConsumable);
		stream.addMap(draftSchemMap, (s) -> {
			stream.addLong(s.getKey());
			stream.addInt(s.getValue());
		});
		stream.addList(friendsList, (s) -> stream.addAscii(s));
		stream.addList(ignoreList, (s) -> stream.addAscii(s));
	}
	
	@Override
	public void read(NetBufferStream stream) {
		stream.getByte();
		languageId = stream.getInt();
		currentStomach = stream.getInt();
		maxStomach = stream.getInt();
		currentDrink = stream.getInt();
		maxDrink = stream.getInt();
		currentConsumable = stream.getInt();
		maxConsumable = stream.getInt();
		stream.getList((i) -> draftSchemMap.put(stream.getLong(), stream.getInt()));
		stream.getList((i) -> friendsList.add(stream.getAscii()));
		stream.getList((i) -> ignoreList.add(stream.getAscii()));
	}
}

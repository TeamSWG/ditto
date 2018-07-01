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

import com.projectswg.common.data.Badges;
import com.projectswg.common.encoding.StringType;
import com.projectswg.common.network.NetBufferStream;
import com.projectswg.common.network.packets.swg.zone.baselines.Baseline.BaselineType;
import com.projectswg.holocore.resources.support.data.collections.SWGMap;
import com.projectswg.holocore.resources.support.global.network.BaselineBuilder;
import com.projectswg.holocore.resources.support.objects.swg.intangible.IntangibleObject;
import com.projectswg.holocore.resources.support.objects.swg.waypoint.WaypointObject;
import com.projectswg.holocore.resources.support.global.player.AccessLevel;
import com.projectswg.holocore.resources.support.global.player.Player;
import com.projectswg.holocore.resources.support.global.player.PlayerFlags;

import java.util.ArrayList;
import java.util.List;

public class PlayerObject extends IntangibleObject {
	
	private final PlayerObjectShared	play3		= new PlayerObjectShared();
	private final PlayerObjectSharedNP	play6		= new PlayerObjectSharedNP();
	private final PlayerObjectPrivate	play8		= new PlayerObjectPrivate();
	private final PlayerObjectPrivateNP	play9		= new PlayerObjectPrivateNP();
	private final List<String>			joinedChannels	= new ArrayList<>();
	
	private int		startPlayTime	= 0;
	private String	biography		= "";
	
	private int	lastUpdatePlayTime = 0;
	private Badges badges			= new Badges();
	
	public PlayerObject(long objectId) {
		super(objectId, BaselineType.PLAY);
		setVolume(0);
	}
	
	public void addWaypoint(WaypointObject waypoint) {
		play8.addWaypoint(waypoint, this);
	}
	
	public WaypointObject getWaypoint(long objId) {
		return play8.getWaypoint(objId);
	}
	public SWGMap<Long, WaypointObject> getWaypoints() {
		return play8.getWaypoints();
	}

	public void updateWaypoint(WaypointObject obj) {
		play8.updateWaypoint(obj, this);
	}
	
	public void removeWaypoint(long objId) {
		play8.removeWaypoint(objId, this);
	}

	public String getTitle() {
		return play3.getTitle();
	}

	public void setTitle(String title) {
		play3.setTitle(title);
		sendDelta(3, 7, title, StringType.ASCII);
	}
	
	public int getPlayTime() {
		return play3.getPlayTime();
	}

	public void updatePlayTime() {
		int currentTime = (int)(System.currentTimeMillis() / 1000);
		
		// calculate how long it's been since the last updatePlayTime and add it to playTime
		int playTime = getPlayTime() + (currentTime - lastUpdatePlayTime);
		lastUpdatePlayTime = currentTime;
		
		play3.setPlayTime(playTime);
		sendDelta(3, 9, playTime);
	}

	public String getBiography() {
		return biography;
	}
	
	public void setBiography(String biography) {
		this.biography = biography;
	}
	
	public Badges getBadges() {
		return badges;
	}
	
	public void setBornDate(int year, int month, int day) {
		play3.setBornDate(year, month, day);
	}
	
	public int getBornDate() {
		return play3.getBornDate();
	}
	
	public int getAdminTag() {
		return play6.getAdminTag();
	}
	
	public void setAdminTag(AccessLevel access) {
		play6.setAdminTag(access);
		sendDelta(6, 2, play6.getAdminTag());
	}
	
	public String getHome() {
		return play6.getHome();
	}

	public void setHome(String home) {
		play6.setHome(home);
		sendDelta(6, 8, home, StringType.ASCII);
	}
	
	public boolean isCitizen() {
		return play6.isCitizen();
	}
	
	public void setCitizen(boolean citizen) {
		play6.setCitizen(citizen);
		sendDelta(6, 9, citizen);
	}
	
	public int getCurrentForcePower() {
		return play8.getCurrentForcePower();
	}
	
	public void setCurrentForcePower(int currentForcePower) {
		play8.setCurrentForcePower(currentForcePower);
		sendDelta(8, 2, currentForcePower);
	}
	
	public int getMaxForcePower() {
		return play8.getMaxForcePower();
	}
	
	public void setMaxForcePower(int maxForcePower) {
		play8.setMaxForcePower(maxForcePower);
		sendDelta(8, 3, maxForcePower);
	}
	
	public int getActiveQuest() {
		return play8.getActiveQuest();
	}

	public void setActiveQuest(int activeQuest) {
		play8.setActiveQuest(activeQuest);
		sendDelta(8, 6, activeQuest);
	}

	public boolean addFriend(String friend) {
		return play9.addFriend(friend, this);
	}

	public boolean removeFriend(String friend) {
		return play9.removeFriend(friend, this);
	}

	public boolean isFriend(String target) {
		return play9.isFriend(target);
	}
	
	public List<String> getFriendsList() {
		return play9.getFriendsList();
	}
	
	public void sendFriendsList() {
		play9.sendFriendsList(this);
	}

	public boolean addIgnored(String ignored) {
		return play9.addIgnored(ignored, this);
	}

	public boolean removeIgnored(String ignored) {
		return play9.removeIgnored(ignored, this);
	}

	public boolean isIgnored(String target) {
		return play9.isIgnored(target);
	}
	
	public List<String> getIgnoreList() {
		return play9.getIgnoreList();
	}
	
	public void sendIgnoreList() {
		play9.sendIgnoreList(this);
	}

	public void setFlagBitmask(PlayerFlags ... flags) {
		play3.setFlagBitmask(this, flags);
	}
	
	public void clearFlagBitmask(PlayerFlags ... flags) {
		play3.clearFlagBitmask(this, flags);
	}
	
	public void toggleFlag(PlayerFlags ... flags) {
		play3.toggleFlag(this, flags);
	}

	public List<String> getJoinedChannels() {
		return new ArrayList<>(joinedChannels);
	}

	public boolean addJoinedChannel(String path) {
		synchronized (joinedChannels) {
//			return true;
			// TODO: Refactor
//			System.out.println("add joined channel: " + path);
			return !joinedChannels.contains(path) && joinedChannels.add(path);
		}
	}

	public boolean removeJoinedChannel(String path) {
		synchronized (joinedChannels) {
//			return true;
			// TODO: Refactor
//			System.out.println("remove joined channel: " + path);
			return joinedChannels.remove(path);
		}
	}

	public void setProfessionIcon(int professionIcon) {
		play3.setProfessionIcon(professionIcon);
		sendDelta(3, 10, professionIcon);
	}
	
	public int getProfessionIcon() {
		return play3.getProfessionIcon();
	}
	
	public void addDraftSchematic(int serverCrc, int clientCrc, int counter) {
		long combinedCrc = (((long) serverCrc << 32) & 0xFFFFFFFF00000000L) | (clientCrc & 0x00000000FFFFFFFFL);
		play9.addDraftSchematic(combinedCrc, counter , this);
	}
	
	public int getExperiencePoints(String xpType) {
		return play8.getExperiencePoints(xpType);
	}
	
	public void setExperiencePoints(String xpType, int experiencePoints) {
		play8.setExperiencePoints(xpType, experiencePoints, this);
	}
	
	public void addExperiencePoints(String xpType, int experiencePoints) {
		play8.addExperiencePoints(xpType, experiencePoints, this);
	}
	
	public int getStartPlayTime() {
		return startPlayTime;
	}

	public void initStartPlayTime() {
		startPlayTime = (int)(System.currentTimeMillis() / 1000);
		lastUpdatePlayTime = startPlayTime;
	}
	
	@Override
	public void createBaseline3(Player target, BaselineBuilder bb) {
		super.createBaseline3(target, bb); // 5 variables
		play3.createBaseline3(target, bb);
	}
	
	@Override
	public void createBaseline6(Player target, BaselineBuilder bb) {
		super.createBaseline6(target, bb); // 2 variables
		play6.createBaseline6(target, bb);
	}
	
	@Override
	public void createBaseline8(Player target, BaselineBuilder bb) {
		super.createBaseline8(target, bb); // 0 variables
		play8.createBaseline8(target, bb);
	}
	
	@Override
	public void createBaseline9(Player target, BaselineBuilder bb) {
		super.createBaseline9(target, bb); // 0 variables
		play9.createBaseline9(target, bb);
	}
	
	@Override
	public void save(NetBufferStream stream) {
		super.save(stream);
		stream.addByte(0);
		play3.save(stream);
		play6.save(stream);
		play8.save(stream);
		play9.save(stream);
		stream.addInt(startPlayTime);
		stream.addUnicode(biography);
	}
	
	@Override
	public void read(NetBufferStream stream) {
		super.read(stream);
		stream.getByte();
		play3.read(stream);
		play6.read(stream);
		play8.read(stream);
		play9.read(stream);
		startPlayTime = stream.getInt();
		biography = stream.getUnicode();
	}
}

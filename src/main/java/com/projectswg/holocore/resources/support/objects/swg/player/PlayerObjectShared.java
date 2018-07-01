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

import com.projectswg.common.network.NetBufferStream;
import com.projectswg.common.persistable.Persistable;
import com.projectswg.holocore.resources.support.data.collections.SWGBitSet;
import com.projectswg.holocore.resources.support.data.collections.SWGFlag;
import com.projectswg.holocore.resources.support.global.network.BaselineBuilder;
import com.projectswg.holocore.resources.support.objects.swg.SWGObject;
import com.projectswg.holocore.resources.support.global.player.Player;
import com.projectswg.holocore.resources.support.global.player.PlayerFlags;
import com.projectswg.holocore.utilities.MathUtils;

import java.util.BitSet;

class PlayerObjectShared implements Persistable {

	private final SWGFlag		flagsList			= new SWGFlag(3, 5);
	private final SWGFlag		profileFlags		= new SWGFlag(3, 6);
	private String 				title				= "";
	private int 				bornDate			= 0;
	private int 				playTime			= 0;
	private int					professionIcon		= 0;
	
	public PlayerObjectShared() {
		
	}
	
	public SWGFlag getFlagsList() {
		return flagsList;
	}
	
	public SWGFlag getProfileFlags() {
		return profileFlags;
	}
	
	public String getTitle() {
		return title;
	}
	
	public int getBornDate() {
		return bornDate;
	}
	
	public int getPlayTime() {
		return playTime;
	}
	
	public int getProfessionIcon() {
		return professionIcon;
	}
	
	public void setTitle(String title) {
		this.title = title;
	}
	
	public void setBornDate(int bornDate) {
		this.bornDate = bornDate;
	}
	
	public void setPlayTime(int playTime) {
		this.playTime = playTime;
	}
	
	public void setProfessionIcon(int professionIcon) {
		this.professionIcon = professionIcon;
	}
	
	public void setBornDate(int year, int month, int day) {
		this.bornDate = MathUtils.numberDaysSince(year, month, day, 2000, 12, 31);
	}
	
	public void setFlagBitmask(SWGObject target, PlayerFlags ... flags) {
		boolean changed = false;
		for (PlayerFlags flag : flags) {
			changed |= !flagsList.get(flag.getFlag());
			flagsList.set(flag.getFlag());
		}
		if (changed)
			flagsList.sendDeltaMessage(target);
	}
	
	public void clearFlagBitmask(SWGObject target, PlayerFlags ... flags) {
		boolean changed = false;
		for (PlayerFlags flag : flags) {
			changed |= flagsList.get(flag.getFlag());
			flagsList.clear(flag.getFlag());
		}
		if (changed)
			flagsList.sendDeltaMessage(target);
	}
	
	public void toggleFlag(SWGObject target, PlayerFlags ... flags) {
		for (PlayerFlags flag : flags)
			flagsList.flip(flag.getFlag());
		flagsList.sendDeltaMessage(target);
	}
	
	public void createBaseline3(Player target, BaselineBuilder bb) {
		bb.addObject(flagsList); // 4 flags -- 5
		bb.addObject(profileFlags); // 4 flags -- 6
		bb.addAscii(title); // 7
		bb.addInt(bornDate); // Born Date -- 4001 = 12/15/2011 || Number of days after 12/31/2000 -- 8
		bb.addInt(playTime); // 9
		bb.addInt(professionIcon); // 10
		
		bb.incrementOperandCount(6);
	}
	
	@Override
	public void save(NetBufferStream stream) {
		stream.addByte(0);
		flagsList.save(stream);
		profileFlags.save(stream);
		stream.addAscii(title);
		stream.addInt(bornDate);
		stream.addInt(playTime);
		stream.addInt(professionIcon);
	}
	
	@Override
	public void read(NetBufferStream stream) {
		stream.getByte();
		flagsList.read(stream);
		profileFlags.read(stream);
		title = stream.getAscii();
		bornDate = stream.getInt();
		playTime = stream.getInt();
		professionIcon = stream.getInt();
	}
	
}

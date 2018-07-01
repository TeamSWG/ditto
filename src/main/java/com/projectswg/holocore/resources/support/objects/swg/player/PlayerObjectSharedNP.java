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
import com.projectswg.holocore.resources.support.global.network.BaselineBuilder;
import com.projectswg.holocore.resources.support.global.player.AccessLevel;
import com.projectswg.holocore.resources.support.global.player.Player;

class PlayerObjectSharedNP implements Persistable {
	
	private int			adminTag			= 0;
	private String 		home				= "";
	private boolean 	citizen				= false;
	
	public PlayerObjectSharedNP() {
		
	}
	
	public int getAdminTag() {
		return adminTag;
	}
	
	public void setAdminTag(AccessLevel access) {
		switch (access) {
			case PLAYER:	adminTag = 0; break;
			case CSR:		adminTag = 1; break;
			case DEV:		adminTag = 2; break;
			case WARDEN:	adminTag = 3; break;
			case QA:		adminTag = 4; break;
		}
	}
	
	public void setAdminTag(int tag) {
		this.adminTag = tag;
	}
	
	public String getHome() {
		return home;
	}
	
	public void setHome(String home) {
		this.home = home;
	}
	
	public boolean isCitizen() {
		return citizen;
	}
	
	public void setCitizen(boolean citizen) {
		this.citizen = citizen;
	}
	
	public void createBaseline6(Player target, BaselineBuilder bb) {
		bb.addByte(adminTag); // Admin Tag (0 = none, 1 = CSR, 2 = Developer, 3 = Warden, 4 = QA) -- 2
		bb.addAscii(home); // 8
		bb.addBoolean(citizen); // 9
		bb.addAscii(""); // 13
		bb.addInt(0); // Citizen Rank Title? 6 bytes -- 14
		bb.addInt(0); // Environment Flags Override -- 15
		bb.addAscii(""); // Vehicle Attack Command -- 16
		
		bb.incrementOperandCount(6);
	}
	
	@Override
	public void save(NetBufferStream stream) {
		stream.addByte(0);
		stream.addInt(adminTag);
		stream.addAscii(home);
		stream.addBoolean(citizen);
	}
	
	@Override
	public void read(NetBufferStream stream) {
		stream.getByte();
		adminTag = stream.getInt();
		home = stream.getAscii();
		citizen = stream.getBoolean();
	}
	
}
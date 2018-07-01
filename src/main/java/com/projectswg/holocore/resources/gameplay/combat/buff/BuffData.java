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
package com.projectswg.holocore.resources.gameplay.combat.buff;

import com.projectswg.common.data.CRC;
import me.joshlarson.jlcommon.utilities.Arguments;

import java.util.Locale;

public class BuffData {
	
	private final int crc;
	private final String name;
	private final String groupName;
	private final int groupPriority;
	private final String [] effectNames;
	private final float [] effectValues;
	
	private float defaultDuration;
	private String effectFileName;
	private String callback;
	
	public BuffData(String name, String groupName, int groupPriority) {
		this.crc = CRC.getCrc(name.toLowerCase(Locale.ENGLISH));
		this.name = name;
		this.groupName = groupName;
		this.groupPriority = groupPriority;
		this.effectNames = new String[5];
		this.effectValues = new float[5];
	}
	
	public int getCrc() {
		return crc;
	}
	
	public String getName() {
		return name;
	}
	
	public String getGroupName() {
		return groupName;
	}
	
	public int getGroupPriority() {
		return groupPriority;
	}
	
	public String getEffectName(int effect) {
		Arguments.validate(effect >= 0 && effect < 5, "Effect # must be in range: [0, 5)");
		return effectNames[effect];
	}
	
	public float getEffectValue(int effect) {
		Arguments.validate(effect >= 0 && effect < 5, "Effect # must be in range: [0, 5)");
		return effectValues[effect];
	}
	
	public float getDefaultDuration() {
		return defaultDuration;
	}
	
	public String getEffectFileName() {
		return effectFileName;
	}
	
	public String getCallback() {
		return callback;
	}
	
	public void setEffectName(int effect, String name) {
		Arguments.validate(effect >= 0 && effect < 5, "Effect # must be in range: [0, 5)");
		effectNames[effect] = name;
	}
	
	public void setEffectValue(int effect, float value) {
		Arguments.validate(effect >= 0 && effect < 5, "Effect # must be in range: [0, 5)");
		effectValues[effect] = value;
	}
	
	public void setDefaultDuration(float defaultDuration) {
		this.defaultDuration = defaultDuration;
	}
	
	public void setEffectFileName(String effectFileName) {
		this.effectFileName = effectFileName;
	}
	
	public void setCallback(String callback) {
		this.callback = callback;
	}
	
}

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
package com.projectswg.holocore.resources.support.objects.awareness;

import com.projectswg.common.data.location.Location;
import com.projectswg.common.network.packets.swg.zone.UpdateTransformMessage;
import com.projectswg.common.network.packets.swg.zone.UpdateTransformWithParentMessage;
import com.projectswg.holocore.resources.support.objects.swg.SWGObject;

public class DataTransformHandler {
	
	public DataTransformHandler() {
		
	}
	
	public void setSpeedCheck(boolean enabled) {
		
	}
	
	public boolean handleMove(SWGObject obj, double speed) {
		obj.sendObservers(createTransform(obj, speed));
		return true;
	}
	
	public boolean handleMove(SWGObject obj, double speed, SWGObject parent) {
		obj.sendObservers(createTransform(obj, speed, parent.getObjectId()));
		return true;
	}
	
	private UpdateTransformMessage createTransform(SWGObject obj, double speed) {
		Location loc = obj.getLocation();
		UpdateTransformMessage transform = new UpdateTransformMessage();
		transform.setObjectId(obj.getObjectId());
		transform.setX((short) (loc.getX() * 4 + 0.5));
		transform.setY((short) (loc.getY() * 4 + 0.5));
		transform.setZ((short) (loc.getZ() * 4 + 0.5));
		transform.setUpdateCounter(obj.getNextUpdateCount());
		transform.setDirection(getMovementAngle(loc));
		transform.setSpeed((byte) (speed+0.5));
		return transform;
	}
	
	private UpdateTransformWithParentMessage createTransform(SWGObject obj, double speed, long cellId) {
		Location loc = obj.getLocation();
		UpdateTransformWithParentMessage transform = new UpdateTransformWithParentMessage(cellId, obj.getObjectId());
		transform.setLocation(loc);
		transform.setUpdateCounter(obj.getNextUpdateCount());
		transform.setDirection(getMovementAngle(loc));
		transform.setSpeed((byte) (speed + 0.5));
		return transform;
	}
	
	private byte getMovementAngle(Location requestedLocation) {
		byte movementAngle = (byte) 0.0f;
		double wOrient = requestedLocation.getOrientationW();
		double yOrient = requestedLocation.getOrientationY();
		double sq = Math.sqrt(1 - (wOrient*wOrient));
		
		if (sq != 0) {
			if (requestedLocation.getOrientationW() > 0 && requestedLocation.getOrientationY() < 0) {
				wOrient *= -1;
				yOrient *= -1;
			}
			movementAngle = (byte) ((yOrient / sq) * (2 * Math.acos(wOrient) / 0.06283f));
		}
		
		return movementAngle;
	}
	
}

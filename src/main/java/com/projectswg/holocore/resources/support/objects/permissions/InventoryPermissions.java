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

package com.projectswg.holocore.resources.support.objects.permissions;

import com.projectswg.holocore.resources.support.objects.swg.SWGObject;
import com.projectswg.holocore.resources.support.objects.swg.creature.CreatureObject;
import com.projectswg.holocore.resources.gameplay.crafting.trade.TradeSession;

class InventoryPermissions extends DefaultPermissions {
	
	@Override
	public boolean canView(SWGObject requester, SWGObject container) {
		if (requester == null)
			return true;
		if (container.getOwner() == null || requester.getOwner() == null)
			return false;
		if (requester.getOwner().equals(container.getOwner()))
			return true;
		return canTradePartnerView(requester, container);
	}

	@Override
	public boolean canEnter(SWGObject requester, SWGObject container) {
		if (requester == null)
			return true;
		if (container.getOwner() == null || requester.getOwner() == null)
			return false;
		return requester.getOwner().equals(container.getOwner());
	}
	
	private boolean canTradePartnerView(SWGObject requester, SWGObject object) {
		CreatureObject creature = object.getOwner().getCreatureObject();
		if (creature == null || !(requester instanceof CreatureObject))
			return false;
		if(object.getOwner() == null)
			return false;
		TradeSession session = creature.getTradeSession();
		if (session == null || !session.isValidSession() || !session.isItemTraded(creature, object))
			return false;
		return ((CreatureObject) requester).getTradeSession() == session; // must be the same instance
	}
	
}

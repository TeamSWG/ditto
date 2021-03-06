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
package com.projectswg.holocore;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

import me.joshlarson.jlcommon.control.IntentManager;

import com.projectswg.holocore.resources.TestResources;
import com.projectswg.holocore.resources.support.data.server_info.DataManager;
import com.projectswg.holocore.services.TestServices;
import com.projectswg.holocore.utilities.ScheduledUtilities;

@RunWith(Suite.class)
@SuiteClasses({
	TestResources.class,
	TestServices.class
})
public class TestAll {
	
	@BeforeClass
	public static void initLog() {
		DataManager.initialize();
	}
	
	@AfterClass
	public static void terminateLog() {
		DataManager.terminate();
		if (IntentManager.getInstance() != null)
			IntentManager.getInstance().close();
		ScheduledUtilities.shutdown();
	}
	
}

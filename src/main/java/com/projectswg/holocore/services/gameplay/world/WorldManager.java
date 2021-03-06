package com.projectswg.holocore.services.gameplay.world;

import com.projectswg.holocore.services.gameplay.world.map.MapService;
import com.projectswg.holocore.services.gameplay.world.travel.TravelService;
import com.projectswg.holocore.services.gameplay.world.weather.WeatherService;
import me.joshlarson.jlcommon.control.Manager;
import me.joshlarson.jlcommon.control.ManagerStructure;

@ManagerStructure(children ={
		MapService.class,
		
		TravelService.class,
		
		WeatherService.class
})
public class WorldManager extends Manager {
	
	public WorldManager() {
		
	}
	
}

package com.projectswg.holocore.resources.support.objects.radial.terminal;

import com.projectswg.common.data.location.Location;
import com.projectswg.common.data.location.Terrain;
import com.projectswg.common.data.radial.RadialItem;
import com.projectswg.common.data.radial.RadialOption;
import com.projectswg.common.data.sui.SuiEvent;
import com.projectswg.holocore.intents.gameplay.player.experience.skills.GrantSkillIntent;
import com.projectswg.holocore.intents.support.objects.items.CreateStaticItemIntent;
import com.projectswg.holocore.intents.support.objects.swg.ObjectCreatedIntent;
import com.projectswg.holocore.intents.support.objects.swg.ObjectTeleportIntent;
import com.projectswg.holocore.resources.support.global.player.Player;
import com.projectswg.holocore.resources.support.global.zone.sui.SuiButtons;
import com.projectswg.holocore.resources.support.global.zone.sui.SuiListBox;
import com.projectswg.holocore.resources.support.objects.ObjectCreator;
import com.projectswg.holocore.resources.support.objects.permissions.ContainerPermissionsType;
import com.projectswg.holocore.resources.support.objects.radial.RadialHandlerInterface;
import com.projectswg.holocore.resources.support.objects.swg.SWGObject;
import com.projectswg.holocore.resources.support.objects.swg.creature.CreatureObject;
import com.projectswg.holocore.resources.support.objects.swg.tangible.TangibleObject;
import com.projectswg.holocore.services.support.objects.items.StaticItemService;

import java.util.List;
import java.util.Map;

public class TerminalCharacterBuilderRadial implements RadialHandlerInterface {
	
	public TerminalCharacterBuilderRadial() {
		
	}
	
	@Override
	public void getOptions(List<RadialOption> options, Player player, SWGObject target) {
		options.add(new RadialOption(RadialItem.ITEM_USE));
		options.add(new RadialOption(RadialItem.EXAMINE));
	}
	
	@Override
	public void handleSelection(Player player, SWGObject target, RadialItem selection) {
		switch (selection) {
			case ITEM_USE: {
				SuiListBox listBox = new SuiListBox(SuiButtons.OK_CANCEL, "Character Builder Terminal", "Select a category.");
				
				listBox.addListItem("Weapons");
				listBox.addListItem("Wearables");
				listBox.addListItem("Travel");
				listBox.addListItem("Skills");
				
				listBox.addCallback(SuiEvent.OK_PRESSED, "handleCategorySelection", (event, parameters) -> handleCategorySelection(player, parameters));
				listBox.display(player);
				break;
			}
		}
	}
	
	private static void handleCategorySelection(Player player, Map<String, String> parameters) {
		int selection = SuiListBox.getSelectedRow(parameters);
		
		switch (selection) {
			case 0: handleWeapons(player); break;
			case 1: handleWearables(player); break;
			case 2: handleTravel(player); break;
			case 3: handleSkills(player); break;
		}
	}
	
	private static void spawnItems(Player player, String ... items) {
		CreatureObject creature = player.getCreatureObject();
		SWGObject inventory = creature.getSlottedObject("inventory");
		
		new CreateStaticItemIntent(creature, inventory, new StaticItemService.LootBoxHandler(creature), ContainerPermissionsType.DEFAULT, items).broadcast();
	}
	
	private static void handleWeapons(Player player) {
		SuiListBox listBox = new SuiListBox(SuiButtons.OK_CANCEL, "Character Builder Terminal", "Select a weapon category to receive a weapon of that type.");
		
		listBox.addListItem("Lightsabers");
		listBox.addListItem("Melee");
		listBox.addListItem("Ranged");
		
		listBox.addCallback(SuiEvent.OK_PRESSED, "handleWeaponSelection", (event, parameters) -> handleWeaponSelection(player, parameters));
		listBox.display(player);
	}
	
	private static void handleWeaponSelection(Player player, Map<String, String> parameters) {
		int selection = SuiListBox.getSelectedRow(parameters);
		
		switch (selection) {
			case 0: handleLightsabers(player); break;
			case 1: handleMelee(player); break;
			case 2: handleRanged(player); break;
		}
	}
	
	private static void handleLightsabers(Player player) {
		spawnItems(player, 
				"weapon_mandalorian_lightsaber_04_01",
				"weapon_npe_lightsaber_02_01",
				"weapon_npe_lightsaber_02_02",
				"weapon_roadmap_lightsaber_02_02"
		);
	}
	
	private static void handleMelee(Player player) {
		spawnItems(player, 
				"soul_leecher"
		);
	}
	
	private static void handleRanged(Player player) {
		spawnItems(player, 
				"rifle_trando_hunter"
		);
	}
	
	private static void handleWearables(Player player) {
		SuiListBox listBox = new SuiListBox(SuiButtons.OK_CANCEL, "Character Builder Terminal", "Select a wearable category to receive a weapon of that type.");

		listBox.addListItem("Jedi equipment");
		
		listBox.addCallback(SuiEvent.OK_PRESSED, "handleWearablesSelection", (event, parameters) -> handleWearablesSelection(player, parameters));
		listBox.display(player);
	}
	
	private static void handleWearablesSelection(Player player, Map<String, String> parameters) {
		int selection = SuiListBox.getSelectedRow(parameters);
		
		switch (selection) {
			case 0: handleJediEquipment(player); break;
		}
		
	}
	
	private static void handleJediEquipment(Player player) {
		spawnItems(player,
				"jedi_robe_padawan"
		);
	}
	
	private static void handleTravel(Player player) {
		SuiListBox listBox = new SuiListBox(SuiButtons.OK_CANCEL, "Character Builder Terminal", "Select a location you want to get teleported to.");
		
		listBox.addListItem("Corellia - Stronghold");
		listBox.addListItem("Coreliia - Corsec Base");
		listBox.addListItem("Corellia - Rebel Base with X-Wings");
		listBox.addListItem("Dantooine - Force Crystal Hunter's Cave");
		listBox.addListItem("Dantooine - Jedi Temple Ruins");
		listBox.addListItem("Dantooine - The Warren");
		listBox.addListItem("Dathomir - Imperial Prison");
		listBox.addListItem("Dathomir - Nightsister Stronghold");
		listBox.addListItem("Dathomir - Nightsister vs. Singing Moutain Clan");
		listBox.addListItem("Dathomir - Quarantine Zone");
		listBox.addListItem("Endor - DWB");
		listBox.addListItem("Endor - Jinda Cave");
		listBox.addListItem("Kashyyyk - Etyyy, The Hunting Grounds");
		listBox.addListItem("Kashyyyk - Kachirho, Slaver Camp");
		listBox.addListItem("Kashyyyk - Kkowir, The Dead Forest");
		listBox.addListItem("Lok - Droid Cave");
		listBox.addListItem("Lok - Great Maze of Lok");
		listBox.addListItem("Lok - Imperial Outpost");
		listBox.addListItem("Lok - Kimogila Town");
		listBox.addListItem("Naboo - Emperor's Retreat");
		listBox.addListItem("Naboo - Weapon Development Facility");
		listBox.addListItem("Rori - Hyperdrive Research Facility");
		listBox.addListItem("Talus - Detainment Center");
		listBox.addListItem("Tatooine - Fort Tusken");
		listBox.addListItem("Tatooine - Imperial Oasis");
		listBox.addListItem("Tatooine - Krayt Graveyard");
		listBox.addListItem("Tatooine - Mos Eisley");
		listBox.addListItem("Tatooine - Mos Taike");
		listBox.addListItem("Tatooine - Squill Cave");
		listBox.addListItem("Yavin 4 - Blueleaf Temple");
		listBox.addListItem("Yavin 4 - Dark Enclave");
		listBox.addListItem("Yavin 4 - Exar Kun");
		listBox.addListItem("Yavin 4 - Geonosian Cave");
		listBox.addListItem("Yavin 4 - Light Enclave");
		
		listBox.addCallback(SuiEvent.OK_PRESSED, "handleTravelSelection", (event, parameters) -> handleTravelSelection(player, parameters));
		listBox.display(player);
	}
	
	private static void handleTravelSelection(Player player, Map<String, String> parameters) {
		int selection = SuiListBox.getSelectedRow(parameters);
		
		switch (selection) {
		
		// Planet: Corellia
			case 0: handleCorStronghold(player); break;
			case 1: handleCorCorsecBase(player); break;
			case 2: handleCorRebelXwingBase(player); break;
		// Planet: Dantooine
			case 3: handleDanCrystalCave(player); break;
			case 4: handleDanJediTemple(player); break;
			case 5: handleDanWarren(player); break;
		// Planet: Dathomir
			case 6: handleDatImperialPrison(player); break;
			case 7: handleDatNS(player); break;
			case 8: handleDatNSvsSMC(player); break;
			case 9: handleDatQz(player); break;
		// Planet: Endor
			case 10: handleEndDwb(player); break;
			case 11: handleEndJindaCave(player); break;
		// Planet: Kashyyyk
			case 12: handleKasEtyyy(player); break;
			case 13: handleKasKachirho(player); break;
			case 14: handleKasKkowir(player); break;
		// Planet: Lok
			case 15: handleLokDroidCave(player); break;
			case 16: handleLokGreatMaze(player); break;
			case 17: handleLokImperialOutpost(player); break;
			case 18: handleLokKimogilaTown(player); break;
		// Planet: Naboo
			case 19: handleNabEmperorsRetreat(player); break;
			case 20: handleNabWeaponFac(player); break;
		// Planet: Rori
			case 21: handleRorHyperdriveFacility(player); break;
		// Planet: Talus
			case 22: handleTalDetainmentCenter(player); break;
		// Planet: Tatooine
			case 23: handleTatFortTusken(player); break;
			case 24: handleTatImperialOasis(player); break;
			case 25: handleTatKraytGrave(player); break;
			case 26: handleTatMosEisley(player); break;
			case 27: handleTatMosTaike(player); break;
			case 28: handleTatSquillCave(player); break;
		// Planet: Yavin 4
			case 29: handleYavBlueleafTemple(player); break;
			case 30: handleYavDarkEnclave(player); break;
			case 31: handleYavExarKun(player); break;
			case 32: handleYavGeoCave(player); break;
			case 33: handleYavLightEnclave(player); break;
			
		}
	}

// Planet: Corellia
	
	private static void handleCorStronghold(Player player) {
		new ObjectTeleportIntent(player.getCreatureObject(), new Location(4735d, 26d, -5676d, Terrain.CORELLIA)).broadcast();
	}
	
	private static void handleCorCorsecBase(Player player) {
		new ObjectTeleportIntent(player.getCreatureObject(), new Location(5137d, 16d, 1518d, Terrain.CORELLIA)).broadcast();
	}
	
	private static void handleCorRebelXwingBase(Player player) {
		new ObjectTeleportIntent(player.getCreatureObject(), new Location(213d, 50d, 4533d, Terrain.CORELLIA)).broadcast();
	}

// Planet: Dantooine
	
	private static void handleDanJediTemple(Player player) {
		new ObjectTeleportIntent(player.getCreatureObject(), new Location(4078d, 10d, 5370d, Terrain.DANTOOINE)).broadcast();
	}
	
	private static void handleDanCrystalCave(Player player) {
		new ObjectTeleportIntent(player.getCreatureObject(), new Location(-6225d, 48d, 7381d, Terrain.DANTOOINE)).broadcast();
	}
	
	private static void handleDanWarren(Player player) {
		new ObjectTeleportIntent(player.getCreatureObject(), new Location(-564d, 1d, -3789d, Terrain.DANTOOINE)).broadcast();
	}

// Planet: Dathomir
	
	private static void handleDatImperialPrison(Player player) {
		new ObjectTeleportIntent(player.getCreatureObject(), new Location(-6079d, 132d, 971d, Terrain.DATHOMIR)).broadcast();
	}
	
	private static void handleDatNS(Player player) {
		new ObjectTeleportIntent(player.getCreatureObject(), new Location(-3989d, 124d, -10d, Terrain.DATHOMIR)).broadcast();
	}
	
	private static void handleDatNSvsSMC(Player player) {
		new ObjectTeleportIntent(player.getCreatureObject(), new Location(-2457d, 117d, 1530d, Terrain.DATHOMIR)).broadcast();
	}
	
	private static void handleDatQz(Player player) {
		new ObjectTeleportIntent(player.getCreatureObject(), new Location(-5786d, 510d, -6554d, Terrain.DATHOMIR)).broadcast();
	}

// Planet: Endor
	
	private static void handleEndJindaCave(Player player) {
		new ObjectTeleportIntent(player.getCreatureObject(), new Location(-1714d, 31d, -8d, Terrain.ENDOR)).broadcast();
	}
	
	private static void handleEndDwb(Player player) {
		new ObjectTeleportIntent(player.getCreatureObject(), new Location(-4683d, 13d, 4326d, Terrain.ENDOR)).broadcast();
	}

// Planet: Kashyyyk
	
	private static void handleKasEtyyy(Player player) {
		new ObjectTeleportIntent(player.getCreatureObject(), new Location(275d, 48d, 503d, Terrain.KASHYYYK_HUNTING)).broadcast();
	}
	
	private static void handleKasKachirho(Player player) {
		new ObjectTeleportIntent(player.getCreatureObject(), new Location(146d, 19d, 162d, Terrain.KASHYYYK_MAIN)).broadcast();
	}
	
	
	private static void handleKasKkowir(Player player) {
		new ObjectTeleportIntent(player.getCreatureObject(), new Location(-164d, 16d, -262d, Terrain.KASHYYYK_DEAD_FOREST)).broadcast();
	}

// Planet: Lok
	
	private static void handleLokDroidCave(Player player) {
		new ObjectTeleportIntent(player.getCreatureObject(), new Location(3331d, 105d, -4912d, Terrain.LOK)).broadcast();
	}
	
	private static void handleLokGreatMaze(Player player) {
		new ObjectTeleportIntent(player.getCreatureObject(), new Location(3848d, 62d, -464d, Terrain.LOK)).broadcast();
	}
	
	private static void handleLokImperialOutpost(Player player) {
		new ObjectTeleportIntent(player.getCreatureObject(), new Location(-1914d, 11d, -3299d, Terrain.LOK)).broadcast();
	}
	
	private static void handleLokKimogilaTown(Player player) {
		new ObjectTeleportIntent(player.getCreatureObject(), new Location(-70d, 42d, 2769d, Terrain.LOK)).broadcast();
	}

// Planet: Naboo
	
	private static void handleNabEmperorsRetreat(Player player) {
		new ObjectTeleportIntent(player.getCreatureObject(), new Location(2535d, 295d, -3887d, Terrain.NABOO)).broadcast();
	}
	
	private static void handleNabWeaponFac(Player player) {
		new ObjectTeleportIntent(player.getCreatureObject(), new Location(-6439d, 41d, -3265d, Terrain.NABOO)).broadcast();
	}

// Planet: Rori
	
	private static void handleRorHyperdriveFacility(Player player) {
		new ObjectTeleportIntent(player.getCreatureObject(), new Location(-1211d, 98d, 4552d, Terrain.RORI)).broadcast();
	}

// Planet: Talus
	
	private static void handleTalDetainmentCenter(Player player) {
		new ObjectTeleportIntent(player.getCreatureObject(), new Location(4958d, 449d, -5983d, Terrain.TALUS)).broadcast();
	}

// Planet: Tatooine
	
	private static void handleTatFortTusken(Player player) {
		new ObjectTeleportIntent(player.getCreatureObject(), new Location(-3941d, 59d, 6318d, Terrain.TATOOINE)).broadcast();
	}
	
	private static void handleTatKraytGrave(Player player) {
		new ObjectTeleportIntent(player.getCreatureObject(), new Location(7380d, 122d, 4298d, Terrain.TATOOINE)).broadcast();
	}
	
	private static void handleTatMosEisley(Player player) {
		new ObjectTeleportIntent(player.getCreatureObject(), new Location(3525d, 4d, -4807d, Terrain.TATOOINE)).broadcast();
	}
	
	private static void handleTatMosTaike(Player player) {
		new ObjectTeleportIntent(player.getCreatureObject(), new Location(3684d, 7d, 2357d, Terrain.TATOOINE)).broadcast();
	}
	
	private static void handleTatSquillCave(Player player) {
		new ObjectTeleportIntent(player.getCreatureObject(), new Location(57d, 152d, -79d, Terrain.TATOOINE)).broadcast();
	}
	
	private static void handleTatImperialOasis(Player player) {
		new ObjectTeleportIntent(player.getCreatureObject(), new Location(-5458d, 10d, 2601d, Terrain.TATOOINE)).broadcast();
	}

// Planet: Yavin 4
	
	private static void handleYavBlueleafTemple(Player player) {
		new ObjectTeleportIntent(player.getCreatureObject(), new Location(-947d, 86d, -2131d, Terrain.YAVIN4)).broadcast();
	}
	
	private static void handleYavExarKun(Player player) {
		new ObjectTeleportIntent(player.getCreatureObject(), new Location(4928d, 103d, 5587d, Terrain.YAVIN4)).broadcast();
	}
	
	private static void handleYavDarkEnclave(Player player) {
		new ObjectTeleportIntent(player.getCreatureObject(), new Location(5107d, 81d, 301d, Terrain.YAVIN4)).broadcast();
	}
	
	private static void handleYavLightEnclave(Player player) {
		new ObjectTeleportIntent(player.getCreatureObject(), new Location(-5575d, 87d, 4902d, Terrain.YAVIN4)).broadcast();
	}
	
	private static void handleYavGeoCave(Player player) {
		new ObjectTeleportIntent(player.getCreatureObject(), new Location(-6485d, 83d, -446d, Terrain.YAVIN4)).broadcast();
	}
	
	private static void handleSkills(Player player) {
		SuiListBox listBox = new SuiListBox(SuiButtons.OK_CANCEL, "Character Builder Terminal", "Select a location you want to get teleported to.");
		
		listBox.addListItem("Master Marksman");
		listBox.addListItem("Master Dancer");
		
		listBox.addCallback(SuiEvent.OK_PRESSED, "handleSkillSelection", (event, parameters) -> handleSkillSelection(player, parameters));
		listBox.display(player);
	}
	
	private static void handleSkillSelection(Player player, Map<String, String> parameters) {
		int selection = SuiListBox.getSelectedRow(parameters);
		
		switch (selection) {
			case 0:
				GrantSkillIntent.broadcast(GrantSkillIntent.IntentType.GRANT,  "combat_marksman_master", player.getCreatureObject(), true); break;
			case 1:
				GrantSkillIntent.broadcast(GrantSkillIntent.IntentType.GRANT,  "social_dancer_master", player.getCreatureObject(), true); break;
		}
	}
	
}

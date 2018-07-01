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
package com.projectswg.holocore.resources.support.global.zone.creation;

import com.projectswg.common.data.customization.CustomizationString;
import com.projectswg.common.data.encodables.tangible.PvpFlag;
import com.projectswg.common.data.encodables.tangible.Race;
import com.projectswg.common.data.swgfile.ClientFactory;
import com.projectswg.common.data.swgfile.visitors.ProfTemplateData;
import com.projectswg.common.network.packets.swg.login.creation.ClientCreateCharacter;
import com.projectswg.holocore.intents.gameplay.player.experience.skills.GrantSkillIntent;
import com.projectswg.holocore.intents.support.objects.swg.ObjectCreatedIntent;
import com.projectswg.holocore.resources.support.objects.permissions.ContainerPermissionsType;
import com.projectswg.holocore.resources.support.objects.swg.SWGObject;
import com.projectswg.holocore.resources.support.objects.swg.building.BuildingObject;
import com.projectswg.holocore.resources.support.objects.swg.cell.CellObject;
import com.projectswg.holocore.resources.support.objects.swg.creature.CreatureObject;
import com.projectswg.holocore.resources.support.objects.swg.player.PlayerObject;
import com.projectswg.holocore.resources.support.objects.swg.tangible.TangibleObject;
import com.projectswg.holocore.resources.support.objects.swg.weapon.WeaponObject;
import com.projectswg.holocore.resources.support.objects.swg.weapon.WeaponType;
import com.projectswg.holocore.resources.support.global.player.AccessLevel;
import com.projectswg.holocore.resources.support.objects.ObjectCreator;
import com.projectswg.holocore.services.support.objects.ObjectStorageService.ObjectLookup;
import com.projectswg.holocore.resources.support.global.zone.TerrainZoneInsertion.SpawnInformation;
import me.joshlarson.jlcommon.utilities.Arguments;
import org.jetbrains.annotations.NotNull;

import java.util.Calendar;

public class CharacterCreation {
	
	private final ProfTemplateData templateData;
	private final ClientCreateCharacter create;
	private final String biography;
	
	public CharacterCreation(ProfTemplateData templateData, ClientCreateCharacter create, String biography) {
		this.templateData = templateData;
		this.create = create;
		this.biography = biography;
	}
	
	public CreatureObject createCharacter(AccessLevel accessLevel, SpawnInformation info) {
		Race			race		= Race.getRaceByFile(create.getRace());
		CreatureObject	creatureObj	= createCreature(race.getFilename(), info);
		PlayerObject	playerObj	= createPlayer(creatureObj);
		
		setCreatureObjectValues(creatureObj);
		setPlayerObjectValues(playerObj);
		createHair(creatureObj, create.getHair(), create.getHairCustomization());
		createStarterClothing(creatureObj, create.getRace());
		playerObj.setAdminTag(accessLevel);
		playerObj.setBiography(biography);
		new ObjectCreatedIntent(creatureObj).broadcast();
		return creatureObj;
	}
	
	@NotNull
	private CreatureObject createCreature(String template, SpawnInformation info) {
		if (info.building)
			return createCreatureBuilding(template, info);
		SWGObject obj = ObjectCreator.createObjectFromTemplate(template);
		assert obj instanceof CreatureObject;
		obj.setLocation(info.location);
		return (CreatureObject) obj;
	}
	
	@NotNull
	private CreatureObject createCreatureBuilding(String template, SpawnInformation info) {
		SWGObject parent = ObjectLookup.getObjectById(info.buildingId);
		Arguments.validate(parent instanceof BuildingObject, String.format("Invalid parent! Either null or not a building: %s  BUID: %d", parent, info.buildingId));
		
		CellObject cell = ((BuildingObject) parent).getCellByName(info.cell);
		Arguments.validate(cell != null, String.format("Invalid cell! Cell does not exist: %s  B-Template: %s  BUID: %d", info.cell, parent.getTemplate(), info.buildingId));
		
		SWGObject obj = ObjectCreator.createObjectFromTemplate(template);
		assert obj instanceof CreatureObject;
		obj.setLocation(info.location);
		obj.moveToContainer(cell);
		return (CreatureObject) obj;
	}
	
	@NotNull
	private PlayerObject createPlayer(CreatureObject creatureObj) {
		SWGObject obj = ObjectCreator.createObjectFromTemplate("object/player/shared_player.iff");
		assert obj instanceof PlayerObject;
		obj.moveToContainer(creatureObj);
		new ObjectCreatedIntent(obj).broadcast();
		return (PlayerObject) obj;
	}
	
	@NotNull
	private TangibleObject createTangible(SWGObject container, ContainerPermissionsType type, String template) {
		SWGObject obj = ObjectCreator.createObjectFromTemplate(template);
		assert obj instanceof TangibleObject;
		obj.setContainerPermissions(type);
		obj.moveToContainer(container);
		new ObjectCreatedIntent(obj).broadcast();
		return (TangibleObject) obj;
	}
	
	/** Creates an object with default world visibility */
	@NotNull
	private TangibleObject createDefaultObject(SWGObject container, String template) {
		return createTangible(container, ContainerPermissionsType.DEFAULT, template);
	}
	
	/** Creates an object with inventory-level world visibility (only the owner) */
	@NotNull
	private TangibleObject createInventoryObject(SWGObject container, String template) {
		return createTangible(container, ContainerPermissionsType.INVENTORY, template);
	}
	
	private void createHair(CreatureObject creatureObj, String hair, CustomizationString customization) {
		if (hair.isEmpty())
			return;
		TangibleObject hairObj = createDefaultObject(creatureObj, ClientFactory.formatToSharedFile(hair));
		hairObj.setAppearanceData(customization);
	}
	
	private void setCreatureObjectValues(CreatureObject creatureObj) {
		creatureObj.setRace(Race.getRaceByFile(create.getRace()));
		creatureObj.setAppearanceData(create.getCharCustomization());
		creatureObj.setHeight(create.getHeight());
		creatureObj.setObjectName(create.getName());
		creatureObj.setPvpFlags(PvpFlag.PLAYER);
		creatureObj.setVolume(0x000F4240);
		creatureObj.setBankBalance(1000);
		creatureObj.setCashBalance(100);
		
		// New characters are Novices in all basic professions in the Combat Upgrade
		new GrantSkillIntent(GrantSkillIntent.IntentType.GRANT, "species_" + creatureObj.getRace().getSpecies(), creatureObj, true).broadcast();
		new GrantSkillIntent(GrantSkillIntent.IntentType.GRANT, "social_entertainer_novice", creatureObj, true).broadcast();
		new GrantSkillIntent(GrantSkillIntent.IntentType.GRANT, "outdoors_scout_novice", creatureObj, true).broadcast();
		new GrantSkillIntent(GrantSkillIntent.IntentType.GRANT, "science_medic_novice", creatureObj, true).broadcast();
		new GrantSkillIntent(GrantSkillIntent.IntentType.GRANT, "crafting_artisan_novice", creatureObj, true).broadcast();
		new GrantSkillIntent(GrantSkillIntent.IntentType.GRANT, "combat_brawler_novice", creatureObj, true).broadcast();
		new GrantSkillIntent(GrantSkillIntent.IntentType.GRANT, "combat_marksman_novice", creatureObj, true).broadcast();
		
		WeaponObject defWeapon = (WeaponObject) createInventoryObject(creatureObj, "object/weapon/melee/unarmed/shared_unarmed_default_player.iff");
		defWeapon.setMaxRange(5);
		defWeapon.setType(WeaponType.UNARMED);
		defWeapon.setAttackSpeed(1);
		defWeapon.setMinDamage(50);
		defWeapon.setMaxDamage(100);
		creatureObj.setEquippedWeapon(defWeapon);
		createDefaultObject(creatureObj, "object/tangible/inventory/shared_character_inventory.iff");
		createInventoryObject(creatureObj, "object/tangible/datapad/shared_character_datapad.iff");
		createInventoryObject(creatureObj, "object/tangible/bank/shared_character_bank.iff");
		createInventoryObject(creatureObj, "object/tangible/mission_bag/shared_mission_bag.iff");
	}
	
	private void setPlayerObjectValues(PlayerObject playerObj) {
		Calendar date = Calendar.getInstance();
		playerObj.setBornDate(date.get(Calendar.YEAR), date.get(Calendar.MONTH) + 1, date.get(Calendar.DAY_OF_MONTH));
	}
	
	private void createStarterClothing(CreatureObject creature, String race) {
		for (String template : templateData.getItems(ClientFactory.formatToSharedFile(race))) {
			createDefaultObject(creature, template);
		}
		
		SWGObject inventory = creature.getSlottedObject("inventory");
		assert inventory != null : "inventory is not defined";
		createDefaultObject(inventory, "object/tangible/npe/shared_npe_uniform_box.iff");
	}
	
}

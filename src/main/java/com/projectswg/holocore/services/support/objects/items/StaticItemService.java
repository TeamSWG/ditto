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
package com.projectswg.holocore.services.support.objects.items;

import com.projectswg.common.data.combat.DamageType;
import com.projectswg.common.data.customization.CustomizationVariable;
import com.projectswg.common.data.encodables.oob.StringId;
import com.projectswg.common.data.swgfile.ClientFactory;
import com.projectswg.common.network.packets.swg.zone.object_controller.ShowLootBox;
import com.projectswg.holocore.intents.support.global.chat.SystemMessageIntent;
import com.projectswg.holocore.intents.support.objects.items.CreateStaticItemIntent;
import com.projectswg.holocore.intents.support.objects.swg.ObjectCreatedIntent;
import com.projectswg.holocore.resources.support.objects.ObjectCreator;
import com.projectswg.holocore.resources.support.objects.permissions.ContainerPermissionsType;
import com.projectswg.holocore.resources.support.objects.swg.SWGObject;
import com.projectswg.holocore.resources.support.objects.swg.creature.CreatureObject;
import com.projectswg.holocore.resources.support.objects.swg.tangible.TangibleObject;
import com.projectswg.holocore.resources.support.objects.swg.weapon.WeaponObject;
import com.projectswg.holocore.resources.support.objects.swg.weapon.WeaponType;
import com.projectswg.holocore.resources.support.global.player.Player;
import com.projectswg.holocore.resources.support.data.server_info.StandardLog;
import me.joshlarson.jlcommon.control.IntentHandler;
import me.joshlarson.jlcommon.control.Service;
import me.joshlarson.jlcommon.log.Log;
import me.joshlarson.json.JSON;
import me.joshlarson.json.JSONArray;
import me.joshlarson.json.JSONObject;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author mads
 */
public class StaticItemService extends Service {

	private final Map<String, ItemLoader> itemLoaderMap;
	private final Map<String, String> itemFileMap;	// Maps item names to file locations

	public StaticItemService() {
		itemLoaderMap = new HashMap<>();
		itemFileMap = new HashMap<>();
	}
	
	@Override
	public boolean initialize() {
		return super.initialize() && loadStaticItems();
	}
	
	private boolean loadStaticItems() {
		try (InputStream inputStream = new FileInputStream("serverdata/items/items.json")) {
			long startTime = StandardLog.onStartLoad("static items");
			JSONArray itemArray = JSON.readArray(inputStream, false);
			int itemCount = itemArray.size();
			
			for (int i = 0; i < itemCount; i++) {
				Map<String, Object> itemEntry = itemArray.getObject(i);
				String type = (String) itemEntry.get("type");
				String file = (String) itemEntry.get("file");
				String existingFile = itemFileMap.put(file, type + "/" + file);
				
				if (existingFile != null) {
					Log.w("Duplicate file name: " + existingFile);
				}
				
				ItemLoader itemLoader = createObjectAttributes(type, file);
				
				if (itemLoader == null) {
					Log.e("Item %s was not loaded because the specified type %s is unknown", file, type);
					continue;
				}
				
				itemLoaderMap.put(file, itemLoader);
			}
			
			
			StandardLog.onEndLoad(itemLoaderMap.size(), "static items", startTime);
			
			return true;
		} catch (IOException e) {
			Log.e(e);
			
			return false;
		}
	}
	
	private ItemLoader createObjectAttributes(String type, String itemName) {
		switch (type) {
			case "armor":		return new ArmorItemLoader(itemName);
			case "weapon":		return new WeaponItemLoader(itemName);
			case "wearable":	return new WearableItemLoader(itemName);
			default:			return null;
		}
	}

	@IntentHandler
	private void handleCreateStaticItemIntent(CreateStaticItemIntent csii) {
		SWGObject container = csii.getContainer();
		String[] itemNames = csii.getItemNames();
		Player requesterOwner = csii.getRequester().getOwner();
		ObjectCreationHandler objectCreationHandler = csii.getObjectCreationHandler();
		ContainerPermissionsType permissions = csii.getPermissions();
		
		// If adding these items to the container would exceed the max capacity...
		if(!objectCreationHandler.isIgnoreVolume() && container.getVolume() + itemNames.length > container.getMaxContainerSize()) {
			objectCreationHandler.containerFull();
			return;
		}
		
		int itemCount = itemNames.length;
		
		if(itemCount > 0) {
			SWGObject[] createdObjects = new SWGObject[itemCount];
			
			for(int j = 0; j < itemCount; j++) {
				String itemName = itemNames[j];
				String typeItemPath = itemFileMap.get(itemName);
				
				if (typeItemPath == null) {
					continue;
				}
				
				String filePath = "serverdata/items/" + typeItemPath + ".json";
				try (FileInputStream fileInputStream = new FileInputStream(filePath)) {
					JSONObject jsonItem = JSON.readObject(fileInputStream);
					ItemLoader itemLoader = itemLoaderMap.get(itemName);
					
					if (itemLoader != null) {
						itemLoader.loadAttributes(jsonItem);
						
						String iffTemplate = ClientFactory.formatToSharedFile(itemLoader.getIffTemplate());
						SWGObject object = ObjectCreator.createObjectFromTemplate(iffTemplate);
						
						// Global attributes and type-specific attributes are applied
						itemLoader.applyAttributes(object);
						object.setContainerPermissions(permissions);
						object.moveToContainer(container);
						Log.d("Successfully moved %s into container %s", itemName, container);
						createdObjects[j] = object;
						new ObjectCreatedIntent(object).broadcast();
					} else {
						String errorMessage = String.format("%s could not be spawned because the item name is unknown", itemName);
						Log.e(errorMessage);
						SystemMessageIntent.broadcastPersonal(requesterOwner, errorMessage);
						return;
					}
				} catch (Exception e) {
					Log.e(e);
				}
			}
			
			objectCreationHandler.success(createdObjects);
		} else {
			Log.w("No item names were specified in CreateStaticItemIntent - no objects were spawned into container %s", container);
		}
	}

	/**
	 * This class contains every attribute that all items have in common.
	 * Type-specific implementations for items like armour hold armour-specific
	 * attributes, such as protection values.
	 * <p>
	 * It is a read-only information object. One {@code ItemLoader} object
	 * is created per item_name. It holds all the needed information to
	 * create the object with every attribute and value.
	 * <p>
	 * This class is designed for inheritance. We only want to store the relevant
	 * attributes for an object.
	 */
	private static abstract class ItemLoader {
		
		private final String itemName;
		
		private boolean noTrade;
		private boolean unique;
		private String conditionString;
		private int volume;
		private String stringName;
		// TODO bio-link
		private String iffTemplate;

		public ItemLoader(String itemName) {
			this.itemName = itemName;
		}

		/**
		 * This method is only called once per item!
		 *
		 * @param jsonObject to get attributes from
		 * @return {@code true} if the attributes for this object were loaded
		 * successfully.
		 */
		public boolean loadAttributes(JSONObject jsonObject) {
			iffTemplate = jsonObject.getString("template");
			stringName = jsonObject.getString("name");
			
			// load global attributes
			// Boolean.getBoolean() is case insensitive. "TRUE" and "true" both work.
			
			if (jsonObject.containsKey("noTrade")) {
				noTrade = jsonObject.getBoolean("noTrade");
			}
			
			if (jsonObject.containsKey("unique")) {
				unique = jsonObject.getBoolean("unique");
			}
			
			
			int hitPoints = jsonObject.getInt("condition");
			conditionString = String.format("%d/%d", hitPoints, hitPoints);
			
			if (jsonObject.containsKey("volume")) {
				volume = jsonObject.getInt("volume");
			} else {
				volume = 1;
			}
			

			// load type-specific attributes
			return loadTypeAttributes(jsonObject);
		}
		
		protected abstract boolean loadTypeAttributes(JSONObject jsonObject);

		/**
		 * This method is called every time an item is to be created
		 *
		 * @param object to apply the attributes to
		 */
		private void applyAttributes(SWGObject object) {
			object.setStf("static_item_n", itemName);
			object.setDetailStf(new StringId("static_item_d", itemName));
			
			if (noTrade)
				object.addAttribute("no_trade", "1");
			if (unique)
				object.addAttribute("unique", "1");
			object.addAttribute("condition", conditionString);
			object.addAttribute("volume", String.valueOf(volume));
			
			object.setObjectName(stringName);
			
			// apply type-specific attributes
			applyTypeAttributes(object);
		}

		/**
		 * Each implementation of {@code ItemLoader} must implement this
		 * method. Once the base attributes have been applied by
		 * {@code ItemLoader.applyAttributes()}, {@code applyTypeAttributes}
		 * will be called.
		 *
		 * @param object to apply the type-specific attributes to.
		 */
		protected abstract void applyTypeAttributes(SWGObject object);

		public final String getIffTemplate() {
			return iffTemplate;
		}
		
		public String getItemName() {
			return itemName;
		}
	}

	private static class WearableItemLoader extends ItemLoader {

		private Map<String, String> mods;
		private String requiredSkill;
		private String requiredLevel;
		private String requiredFaction;
		private boolean wearableByWookiees = true;
		private boolean wearableByIthorians  = true;
		private boolean wearableByRodians  = true;
		private boolean wearableByTrandoshans  = true;
		private boolean wearableByRest  = true;
		private int colorIndex0;
		private int colorIndex1;
		private int colorIndex2;
		private int colorIndex3;

		public WearableItemLoader(String itemName) {
			super(itemName);
			mods = new HashMap<>();
		}

		@Override
		protected boolean loadTypeAttributes(JSONObject jsonObject) {
			if (jsonObject.containsKey("requiredLevel")) {
				requiredLevel = String.valueOf(jsonObject.getInt("requiredLevel"));
			}
			
			if (jsonObject.containsKey("requiredSkill")) {
				requiredSkill = "@skl_n:" + jsonObject.getString("requiredSkill");
			}
			
			if (jsonObject.containsKey("requiredFaction")) {
				requiredFaction = jsonObject.getString("requiredFaction");
			}

			// Load skill mods
			if (jsonObject.containsKey("skillMods")) {
				JSONArray skillMods = new JSONArray(jsonObject.getArray("skillMods"));
				int skillModCount = skillMods.size();
				
				for (int i = 0; i < skillModCount ; i++) {
					JSONObject skillModObject = new JSONObject(skillMods.getObject(i));
					
					String modName = skillModObject.getString("name");
					int modValue = skillModObject.getInt("value");
					
					mods.put(modName, String.valueOf(modValue));
				}
			}
			
			// Load species restrictions, convert to boolean
			if (jsonObject.containsKey("race_wookiee")) {
				wearableByWookiees = jsonObject.getInt("race_wookiee") != 0;
			}
			
			if (jsonObject.containsKey("race_ithorian")) {
				wearableByIthorians = jsonObject.getInt("race_ithorian") != 0;
			}
			
			if (jsonObject.containsKey("race_rodian")) {
				wearableByRodians = jsonObject.getInt("race_rodian") != 0;
			}
			
			if (jsonObject.containsKey("race_trandoshan")) {
				wearableByTrandoshans = jsonObject.getInt("race_trandoshan") != 0;
			}
			
			if (jsonObject.containsKey("race_rest")) {
				wearableByRest = jsonObject.getInt("race_rest") != 0;
			}
			
			if (jsonObject.containsKey("index_color_0")) {
				colorIndex0 = jsonObject.getInt("index_color_0");
			}
			
			if (jsonObject.containsKey("index_color_1")) {
				colorIndex1 = jsonObject.getInt("index_color_1");
			}
			
			if (jsonObject.containsKey("index_color_2")) {
				colorIndex2 = jsonObject.getInt("index_color_2");
			}
			
			if (jsonObject.containsKey("index_color_3")) {
				colorIndex3 = jsonObject.getInt("index_color_3");
			}

			return true;
		}

		@Override
		protected void applyTypeAttributes(SWGObject object) {
			object.addAttribute("healing_combat_level_required", requiredLevel);

			if (requiredFaction != null)
				object.addAttribute("faction_restriction", requiredFaction);
			
			if (requiredSkill != null) {
				object.addAttribute("required_skill", requiredSkill);
			}

			// Apply the mods!
			for (Map.Entry<String, String> modEntry : mods.entrySet())
				object.addAttribute("cat_skill_mod_bonus.@stat_n:" + modEntry.getKey(), modEntry.getValue());

			// Add the race restrictions only if there are any
			if (!wearableByWookiees || !wearableByIthorians || !wearableByRodians || !wearableByTrandoshans || !wearableByRest)
				object.addAttribute("species_restrictions.species_name", buildRaceRestrictionString());
			
			if (!(object instanceof TangibleObject)) {
				return;
			}
			
			TangibleObject tangible = (TangibleObject) object;
			
			applyColor(tangible, "/private/index_color_0", colorIndex0);
			applyColor(tangible, "/private/index_color_1", colorIndex1);
			applyColor(tangible, "/private/index_color_2", colorIndex2);
			applyColor(tangible, "/private/index_color_3", colorIndex3);
		}

		private String buildRaceRestrictionString() {
			String races = "";

			if (wearableByWookiees)
				races = races.concat("Wookiee ");
			if (wearableByIthorians)
				races = races.concat("Ithorian ");
			if (wearableByRodians)
				races = races.concat("Rodian ");
			if (wearableByTrandoshans)
				races = races.concat("Trandoshan ");
			if (wearableByRest)
				races = races.concat("MonCal Human Zabrak Bothan Sullustan Twi'lek ");
			
			return races.substring(0, races.length() - 1);
		}
		
		private void applyColor(TangibleObject tangible, String variable, int colorIndex) {
			if (colorIndex >= 0) {
				CustomizationVariable color = new CustomizationVariable();
				
				color.setValue(colorIndex);
				tangible.putCustomization(variable, color);
			}
		}
	}

	private static final class ArmorItemLoader extends WearableItemLoader {

		private String armorCategory;
		private String kinetic, energy, elementals;
		private float protectionWeight;

		public ArmorItemLoader(String itemName) {
			super(itemName);
		}

		@Override
		protected boolean loadTypeAttributes(JSONObject jsonObject) {
			boolean wearableAttributesLoaded = super.loadTypeAttributes(jsonObject);

			if (!wearableAttributesLoaded) {
				return false;
			}
			
			String armorType = jsonObject.getString("armor_category");
			protectionWeight = jsonObject.getFloat("protection");

			switch (armorType) {
				case "assault":
					kinetic = getProtectionValue((short) 7000, protectionWeight);
					energy = getProtectionValue((short) 5000, protectionWeight);
					break;
				case "battle":
					kinetic = getProtectionValue((short) 6000, protectionWeight);
					energy = getProtectionValue((short) 6000, protectionWeight);
					break;
				case "recon":
					kinetic = getProtectionValue((short) 5000, protectionWeight);
					energy = getProtectionValue((short) 7000, protectionWeight);
					armorType = "reconnaissance";
					break;
				default:
					// TODO log the fact that the armor type isn't recognised
					return false;
			}

			elementals = getProtectionValue((short) 6000, protectionWeight);
			armorCategory = "@obj_attr_n:armor_" + armorType;

			return true;
		}

		@Override
		protected void applyTypeAttributes(SWGObject object) {
			super.applyTypeAttributes(object);
			object.addAttribute("armor_category", armorCategory);
			object.addAttribute("cat_armor_standard_protection.armor_eff_kinetic", kinetic);
			object.addAttribute("cat_armor_standard_protection.energy", energy);
			object.addAttribute("cat_armor_special_protection.armor_eff_elemental_heat", elementals);
			object.addAttribute("cat_armor_special_protection.armor_eff_elemental_cold", elementals);
			object.addAttribute("cat_armor_special_protection.armor_eff_elemental_acid", elementals);
			object.addAttribute("cat_armor_special_protection.armor_eff_elemental_electrical", elementals);
		}

		private String getProtectionValue(short protection, float protectionWeight) {
			return String.valueOf((short) Math.floor(protection * protectionWeight));
		}

	}

	private static final class WeaponItemLoader extends WearableItemLoader {

		private WeaponType category;
		private DamageType damageTypeEnum;
		private DamageType elementalTypeEnum;
		private String damageType;
		private String damageTypeString;
		private String elementalType;
		private float attackSpeed;
		private float maxRange;
		private String rangeString;
		private int minDamage;
		private int maxDamage;
		private String damageString;
		private String elementalTypeString;
		private int elementalDamage;
		private String dps;
		private float wound;
		private int specialAttackCost;
		private int accuracy;

		public WeaponItemLoader(String itemName) {
			super(itemName);
		}
		
		private DamageType getDamageTypeForName(String damageTypeName) {
			switch(damageTypeName) {
				case "kinetic": return DamageType.KINETIC;
				case "energy": return DamageType.ENERGY;
				case "heat": return DamageType.ELEMENTAL_HEAT;
				case "cold": return DamageType.ELEMENTAL_COLD;
				case "acid": return DamageType.ELEMENTAL_ACID;
				case "electricity": return DamageType.ELEMENTAL_ELECTRICAL;
				default:
					Log.e("Unknown damage type %s", damageTypeName);
					return null;	// TODO Unknown DamageType... now what?
			}
		}

		@Override
		protected boolean loadTypeAttributes(JSONObject jsonObject) {
			super.loadTypeAttributes(jsonObject);
			String weaponType = jsonObject.getString("weaponType");

			switch (weaponType) {
				case "RIFLE": category = WeaponType.RIFLE; break;
				case "CARBINE": category = WeaponType.CARBINE; break;
				case "PISTOL": category = WeaponType.PISTOL; break;
				case "HEAVY": category = WeaponType.HEAVY; break; // pre-NGE artifact for pre-NGE heavy weapons
				case "ONE_HANDED_MELEE": category = WeaponType.ONE_HANDED_MELEE; break;
				case "TWO_HANDED_MELEE": category = WeaponType.TWO_HANDED_MELEE; break;
				case "UNARMED": category = WeaponType.UNARMED; break;
				case "POLEARM_MELEE": category = WeaponType.POLEARM_MELEE; break;
				case "THROWN": category = WeaponType.THROWN; break;
				case "ONE_HANDED_SABER": category = WeaponType.ONE_HANDED_SABER; break;
				case "TWO_HANDED_SABER": category = WeaponType.TWO_HANDED_SABER; break;
				case "POLEARM_SABER": category = WeaponType.POLEARM_SABER; break;
				case "GROUND_TARGETTING": category = WeaponType.HEAVY_WEAPON; break;
				case "DIRECTIONAL_TARGET_WEAPON": category = WeaponType.DIRECTIONAL_TARGET_WEAPON; break;
				case "LIGHT_RIFLE": category = WeaponType.LIGHT_RIFLE; break;
				default:
					Log.e("Unrecognised weapon type for item with name ", super.getItemName());
					return false;
			}

			damageType = jsonObject.getString("damageType");
			damageTypeEnum = getDamageTypeForName(damageType);
			damageTypeString = "@obj_attr_n:armor_eff_" + damageType;
			attackSpeed = jsonObject.getFloat("attackSpeed");

			maxRange = jsonObject.getInt("range");
			rangeString = String.format("0-%dm", (int) maxRange);

			minDamage = jsonObject.getInt("minDamage");
			maxDamage = jsonObject.getInt("maxDamage");
			damageString = String.format("%d - %d", minDamage, maxDamage);
			
			if(jsonObject.containsKey("elemental_type")) {
				elementalType = jsonObject.getString("elemental_type");
				elementalTypeEnum = getDamageTypeForName(elementalType);
				elementalTypeString = "@obj_attr_n:elemental_" + elementalType;
				elementalDamage = jsonObject.getInt("elemental_damage");
			}
			
			
			wound = jsonObject.getFloat("wound");
			specialAttackCost = jsonObject.getInt("specialAttackCost");
			
			if (jsonObject.containsKey("accuracy")) {
				accuracy = jsonObject.getInt("accuracy");
			}
			
			// TODO calculate and set DPS?
			
			return true;
		}

		@Override
		protected void applyTypeAttributes(SWGObject object) {
			super.applyTypeAttributes(object);
			object.addAttribute("cat_wpn_damage.wpn_damage_type", damageTypeString);
			object.addAttribute("cat_wpn_damage.wpn_attack_speed", String.valueOf(attackSpeed));
			object.addAttribute("cat_wpn_damage.damage", damageString);
			if(elementalTypeString != null) {	// Not all weapons have elemental damage.
				object.addAttribute("cat_wpn_damage.wpn_elemental_type", elementalTypeString);
				object.addAttribute("cat_wpn_damage.wpn_elemental_value", String.valueOf(elementalDamage));
			}
			
			object.addAttribute("cat_wpn_damage.wpn_wound_chance", wound + "%");
			
			object.addAttribute("cat_wpn_damage.weapon_dps", dps);
			
			object.addAttribute("cat_wpn_other.wpn_range", rangeString);
			object.addAttribute("cat_wpn_other.attackcost", String.valueOf(specialAttackCost));
			// Ziggy: Special Action Cost would go under cat_wpn_other as well, but it's a pre-NGE artifact.

			WeaponObject weapon = (WeaponObject) object;
			weapon.setType(category);
			weapon.setAttackSpeed(attackSpeed);
			weapon.setMaxRange(maxRange);
			weapon.setDamageType(damageTypeEnum);
			weapon.setElementalType(elementalTypeEnum);
			weapon.setMinDamage(minDamage);
			weapon.setMaxDamage(maxDamage);
			weapon.setSpecialAttackCost(specialAttackCost);
			weapon.setAccuracy(accuracy);
		}
	}

	// TODO ConsumableAttributes extending ItemLoader
	// int uses
	// healingPower, if specified.
	// reuseTime

	public static abstract class ObjectCreationHandler {
		public abstract void success(SWGObject[] createdObjects);
		public abstract boolean isIgnoreVolume();
		
		public void containerFull() {
			
		}
	}
	
	public static final class LootBoxHandler extends ObjectCreationHandler {

		private final CreatureObject receiver;

		public LootBoxHandler(CreatureObject receiver) {
			this.receiver = receiver;
		}
		
		@Override
		public void success(SWGObject[] createdObjects) {
			long[] objectIds = new long[createdObjects.length];

			for (int i = 0; i < objectIds.length; i++) {
				objectIds[i] = createdObjects[i].getObjectId();
			}

			receiver.sendSelf(new ShowLootBox(receiver.getObjectId(), objectIds));
		}

		@Override
		public boolean isIgnoreVolume() {
			return true;
		}
		
	}
}

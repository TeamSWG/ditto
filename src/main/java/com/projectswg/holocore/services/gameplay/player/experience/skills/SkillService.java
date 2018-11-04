package com.projectswg.holocore.services.gameplay.player.experience.skills;

import com.projectswg.common.data.RGB;
import com.projectswg.common.data.encodables.oob.OutOfBandPackage;
import com.projectswg.common.data.encodables.oob.ProsePackage;
import com.projectswg.common.data.encodables.oob.StringId;
import com.projectswg.common.data.info.RelationalDatabase;
import com.projectswg.common.data.info.RelationalServerFactory;
import com.projectswg.common.data.swgfile.ClientFactory;
import com.projectswg.common.data.swgfile.visitors.DatatableData;
import com.projectswg.common.network.packets.swg.zone.object_controller.ShowFlyText;
import com.projectswg.holocore.intents.gameplay.player.badge.SetTitleIntent;
import com.projectswg.holocore.intents.gameplay.player.experience.ExperienceIntent;
import com.projectswg.holocore.intents.gameplay.player.experience.LevelChangedIntent;
import com.projectswg.holocore.intents.gameplay.player.experience.skills.SkillModIntent;
import com.projectswg.holocore.intents.gameplay.player.experience.skills.GrantSkillIntent;
import com.projectswg.holocore.intents.gameplay.player.experience.skills.SurrenderSkillIntent;
import com.projectswg.holocore.intents.support.global.chat.SystemMessageIntent;
import com.projectswg.holocore.resources.support.data.config.ConfigFile;
import com.projectswg.holocore.resources.support.data.server_info.DataManager;
import com.projectswg.holocore.resources.support.objects.swg.creature.CreatureObject;
import com.projectswg.holocore.resources.support.objects.swg.player.PlayerObject;
import me.joshlarson.jlcommon.control.IntentHandler;
import me.joshlarson.jlcommon.control.Service;
import me.joshlarson.jlcommon.log.Log;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

public class SkillService extends Service {
	
	private static final String GET_ALL_LEVELS = "SELECT * FROM player_level";
	private static final String GET_ALL_MULTIPLIERS = "SELECT * FROM combat_xp_multipliers";
	private static final int SKILL_POINT_CAP = 250;
	
	private final Map<String, SkillData> skillDataMap;
	private final Map<String, Integer> levelXpMultipliers;
	private final Map<Short, PlayerLevelData> playerLevelXp;
	private final double xpMultiplier;
	
	public SkillService() {
		skillDataMap = new HashMap<>();
		levelXpMultipliers = new HashMap<>();
		playerLevelXp = new HashMap<>();
		xpMultiplier = DataManager.getConfig(ConfigFile.FEATURES).getDouble("XP-MULTIPLIER", 1);
		
	}
	
	@Override
	public boolean initialize() {
		loadSkills();
		loadXpMultipliers();
		loadPlayerLevelXp();
		
		return true;
	}
	
	private void loadSkills() {
		DatatableData skillsTable = (DatatableData) ClientFactory.getInfoFromFile("datatables/skill/skills.iff");
		
		for (int i = 0; i < skillsTable.getRowCount(); i++) {
			String skillName = (String) skillsTable.getCell(i, 0);
			String [] skillModsStrings = splitCsv((String) skillsTable.getCell(i, 22));
			Map<String, Integer> skillMods = new HashMap<>();
			for (String skillModString : skillModsStrings) {
				String [] values = skillModString.split("=", 2);
				skillMods.put(values[0], Integer.parseInt(values[1]));
			}
			
			SkillData skillData = new SkillData(
					(boolean) skillsTable.getCell(i, 4),	// Is title
					(int) skillsTable.getCell(i, 8),		// Points required
					splitCsv((String) skillsTable.getCell(i, 10)),	// required skills
					(String) skillsTable.getCell(i, 1),				// parent skill
					(String) skillsTable.getCell(i, 12),			// xp type
					(int) skillsTable.getCell(i, 13),				// xp cost
					(int) skillsTable.getCell(i, 14),				// xp cap
					splitCsv((String) skillsTable.getCell(i, 21)),	// commands
					skillMods,
					splitCsv((String) skillsTable.getCell(i, 23))	// schematics
			);
			
			skillDataMap.put(skillName, skillData);
		}
	}
	
	private void loadXpMultipliers() {
		try (RelationalDatabase spawnerDatabase = RelationalServerFactory.getServerData("experience/combat_xp_multipliers.db", "combat_xp_multipliers")) {
			try (ResultSet set = spawnerDatabase.executeQuery(GET_ALL_MULTIPLIERS)) {
				while (set.next()) {
					levelXpMultipliers.put(set.getString("xp_type"), set.getInt("multiplier"));
				}
			}
		} catch (SQLException e) {
			Log.e(e);
		}
	}
	
	private void loadPlayerLevelXp() {
		try (RelationalDatabase spawnerDatabase = RelationalServerFactory.getServerData("experience/player_level.db", "player_level")) {
			try (ResultSet set = spawnerDatabase.executeQuery(GET_ALL_LEVELS)) {
				while (set.next()) {
					// Load player level
					PlayerLevelData data = new PlayerLevelData(set.getInt("required_combat_xp"), set.getInt("level_health_added"));
					
					playerLevelXp.put(set.getShort("level"), data);
				}
			}
		} catch (SQLException e) {
			Log.e(e);
		}
	}
	
	private String [] splitCsv(String str) {
		if (str.isEmpty())
			return new String[0];
		else if (str.indexOf(',') == -1)
			return new String[]{str};
		return str.split(",");
	}
	
	@IntentHandler
	private void handleGrantSkillIntent(GrantSkillIntent gsi) {
		if (gsi.getIntentType() != GrantSkillIntent.IntentType.GRANT) {
			return;
		}
		
		String skillName = gsi.getSkillName();
		CreatureObject target = gsi.getTarget();
		SkillData skillData = skillDataMap.get(skillName);
		String parentSkillName = skillData.getParentSkill();
		
		if (gsi.isGrantRequiredSkills()) {
			recursivelyGrantSkills(skillData, skillName, target);
		} else if (!target.hasSkill(parentSkillName) || !hasRequiredSkills(skillData, target)) {
			Log.i("%s lacks required skill %s before being granted skill %s", target, parentSkillName, skillName);
		} else {
			grantSkill(skillData, skillName, target);
		}
		
		// See if combat level changes are necessary
		combatLevelCheck(target);
	}
	
	@IntentHandler
	private void handleSetTitleIntent(SetTitleIntent sti) {
		String title = sti.getTitle();
		
		if (!skillDataMap.containsKey(title)) {
			// Might be a Collections title or someone playing tricks
			return;
		}
		
		SkillData skillData = skillDataMap.get(title);
		
		if (!skillData.isTitle()) {
			// There's a skill with this name, but it doesn't grant a title
			return;
		}
		
		sti.getRequester().setTitle(title);
	}
	
	@IntentHandler
	private void handleSurrenderSkillIntent(SurrenderSkillIntent ssi) {
		CreatureObject target = ssi.getTarget();
		String surrenderedSkill = ssi.getSurrenderedSkill();
		
		if (!target.hasSkill(surrenderedSkill)) {
			// They don't even have this skill. Do nothing.
			
			Log.w("%s could not surrender skill %s because they do not have it", target, surrenderedSkill);
			
			return;
		}
		
		Optional<String[]> dependentSkills = target.getSkills().stream()
				.map(skillDataMap::get)	// Get SkillData for this skill name
				.map(SkillData::getRequiredSkills)	// Get required skills for the current skill
				.filter(requiredSkills -> {
					for (String requiredSkill : requiredSkills) {
						if (requiredSkill.equals(surrenderedSkill)) {
							return true;
						}
					}
					
					return false;
				})
				.findAny();
		
		if (dependentSkills.isPresent()) {
			Log.d("%s could not surrender skill %s because these skills depend on it: ",
					target, Arrays.toString(dependentSkills.get()));
			return;
		}
		
		SkillData skillData = skillDataMap.get(surrenderedSkill);
		
		target.removeSkill(surrenderedSkill);
		target.removeAbility(skillData.getCommands());
		skillData.getSkillMods().forEach((skillModName, skillModValue) -> new SkillModIntent(skillModName, 0, -skillModValue, target).broadcast());
		
		// See if combat level changes are necessary
		combatLevelCheck(target);
	}
	
	@IntentHandler
	private void handleExperienceIntent(ExperienceIntent ei) {
		CreatureObject creatureObject = ei.getCreatureObject();
		PlayerObject playerObject = creatureObject.getPlayerObject();
		
		if (playerObject != null) {
			String xpType = ei.getXpType();
			
			awardExperience(creatureObject, playerObject, xpType, ei.getExperienceGained());
		}
	}
	
	private void awardExperience(CreatureObject creatureObject, PlayerObject playerObject, String xpType, int xpGained) {
		int currentXp = playerObject.getExperiencePoints(xpType);
		
		// Check XP caps on skills that the player is progressing towards
		
		int gainableXp = 0;	// Amount of XP this player can gain without hitting the XP cap
		Set<String> skills = creatureObject.getSkills();
		boolean validProfession = false;	// Whether they have a profession that actually requires this XP type or not
		
		for (String skill : skills) {
			SkillData skillData = skillDataMap.get(skill);
			
			if (!xpType.equals(skillData.getXpType())) {
				// We only want to take skills that require the same XP type into consideration
				continue;
			}
			
			validProfession = true;
			
			int xpCap = skillData.getXpCap();
			
			gainableXp += xpCap - currentXp;
		}
		
		if (!validProfession) {
			// They don't have a profession with skills that use this XP type - do nothing
			return;
		}
		
		if (gainableXp <= 0) {
			// They've hit the XP cap
			SystemMessageIntent.broadcastPersonal(creatureObject.getOwner(), new ProsePackage(new StringId("base_player", "prose_hit_xp_cap"), "TO", new StringId("exp_n", xpType)));
			return;
		}
		
		int newXpTotal = currentXp + (int) (xpGained * xpMultiplier);
		
		
		playerObject.setExperiencePoints(xpType, newXpTotal);
		Log.d("%s gained %d %s XP", creatureObject, xpGained, xpType);
		
		// Show flytext above the creature that received XP, but only to them
		creatureObject.sendSelf(new ShowFlyText(creatureObject.getObjectId(), new OutOfBandPackage(new ProsePackage(new StringId("base_player", "prose_flytext_xp"), "DI", xpGained)), ShowFlyText.Scale.MEDIUM, new RGB(255, 0, 255)));
		
		// TODO CU: flytext is displayed over the killed creature
		// TODO CU: is the displayed number the gained Combat XP with all bonuses applied?
		// TODO only display in console. Isn't displayed for Combat XP.
		// TODO display different messages with inspiration bonus and/or group bonus. @base_player:prose_grant_buff_xp, @base_player:prose_grant_group_buff_xp, @base_player:prose_grant_group_xp
		SystemMessageIntent.broadcastPersonal(creatureObject.getOwner(), new ProsePackage(new StringId("base_player", "prose_grant_xp"), "TO", new StringId("exp_n", xpType)));
	}
	
	private boolean recursivelyGrantSkills(SkillData skillData, String skillName, CreatureObject target) {
		if (skillData == null || skillName == null || target == null) {
			return false;
		}
		
		String[] requiredSkills = skillData.getRequiredSkills();
		String parentSkillName = skillData.getParentSkill();
		
		if (parentSkillName != null && !target.hasSkill(parentSkillName)) {
			// Grant parent skill, if there is one and they don't have it
			SkillData parentSkillData = skillDataMap.get(parentSkillName);
			
			recursivelyGrantSkills(parentSkillData, parentSkillName, target);
		}
		
		for (String requiredSkillName : requiredSkills) {
			if (!target.hasSkill(requiredSkillName)) {
				SkillData requiredSkillData = skillDataMap.get(requiredSkillName);
				
				if (requiredSkillData == null) {
					Log.e("Missing SkillData for required skill name: %s", requiredSkillName);
					return false;
				}
				
				if (!recursivelyGrantSkills(requiredSkillData, requiredSkillName, target)) {
					// We reached a limit of some sort - stop granting skills
					return false;
				}
			}
		}
		
		return grantSkill(skillData, skillName, target);	// Grant them that skill
	}
	
	private int skillPointsSpent(CreatureObject creature) {
		return creature.getSkills().stream()
				.map(skillDataMap::get)	// Get skill data for this skill
				.map(SkillData::getPointsRequired)	// Get amount of points required for this skill
				.mapToInt(Integer::intValue)
				.sum();	// Add points required for all skills together
	}
	
	
	private boolean hasRequiredSkills(SkillData skillData, CreatureObject creatureObject) {
		String[] requiredSkills = skillData.getRequiredSkills();
		if (requiredSkills == null)
			return true;
		
		for (String required : requiredSkills) {
			if (!creatureObject.hasSkill(required))
				return false;
		}
		return true;
	}
	
	private boolean grantSkill(SkillData skillData, String skillName, CreatureObject target) {
		int pointsRequired = skillData.getPointsRequired();
		int skillPointsSpent = skillPointsSpent(target);
		
		if (skillPointsSpent(target) + pointsRequired >= SKILL_POINT_CAP) {
			int missingPoints = pointsRequired - (SKILL_POINT_CAP - skillPointsSpent);
			
			Log.d("%s cannot learn %s because they lack %d skill points", target, skillName, missingPoints);
			return false;
		}
		
		target.addSkill(skillName);
		target.addAbility(skillData.getCommands());
		
		skillData.getSkillMods().forEach((skillModName, skillModValue) -> new SkillModIntent(skillModName, 0, skillModValue, target).broadcast());
		
		new GrantSkillIntent(GrantSkillIntent.IntentType.GIVEN, skillName, target, false).broadcast();
		
		return true;
	}
	
	private void combatLevelCheck(CreatureObject target) {
		short oldLevel = target.getLevel();
		
		int combatLevelXp = skillDataMap.entrySet().stream()
				.filter(entry -> target.hasSkill(entry.getKey()))    // We're only interested in the skill entries that the target has
				.filter(entry -> entry.getValue().getXpType() != null)
				.filter(entry -> levelXpMultipliers.containsKey(entry.getValue().getXpType()))    // Skip XP non-combat XP types
				.map(entry -> levelXpMultipliers.get(entry.getValue().getXpType()) * entry.getValue().getXpCost())	// XP type multiplier is applied
				.mapToInt(Integer::intValue).sum();
		
		Optional<Map.Entry<Short, PlayerLevelData>> optionalPlayerLevelEntry = playerLevelXp.entrySet().stream()
				.filter(entry -> entry.getValue().getRequiredXp() >= combatLevelXp)
				.findFirst();	// Find the first combat level that requires the amount of XP that I have
		
		
		PlayerLevelData levelData;
		short newLevel;
		
		if (optionalPlayerLevelEntry.isPresent()) {
			Map.Entry<Short, PlayerLevelData> playerLevelEntry = optionalPlayerLevelEntry.get();
			
			levelData = playerLevelEntry.getValue();
			newLevel = playerLevelEntry.getKey();
		} else {
			// We have reached max level. Last entry of the Map will an Entry for the max level.
			newLevel = (short) (playerLevelXp.size() + 1);
			levelData = playerLevelXp.get(newLevel);
		}
		
		
		if (oldLevel == newLevel) {
			// They qualify for the exact same level. Do nothing.
			return;
		}
		
		int oldLevelHealth = target.getLevelHealthGranted();
		int newLevelHealth = levelData.getAdditionalHealth();
		int newMaxHealth = target.getMaxHealth() - oldLevelHealth + newLevelHealth;
		
		target.setLevelHealthGranted(newLevelHealth);
		target.setMaxHealth(newMaxHealth);
		target.setHealth(newMaxHealth);
		target.setLevel(newLevel);
	}
	
	private static class SkillData {
		private final boolean title;
		private final int pointsRequired;
		private String[] requiredSkills;
		private final String parentSkill;
		private final String xpType;
		private final int xpCost;
		private final int xpCap;
		private final String[] commands;
		private final Map<String, Integer> skillMods;
		private final String[] schematics;

		public SkillData(boolean title, int pointsRequired, String[] requiredSkills, String parentSkill, String xpType, int xpCost, int xpCap, String[] commands, Map<String, Integer> skillMods, String[] schematics) {
			this.title = title;
			this.pointsRequired = pointsRequired;
			this.requiredSkills = requiredSkills;
			this.parentSkill = parentSkill;
			this.xpType = xpType;
			this.xpCost = xpCost;
			this.xpCap = xpCap;
			this.commands = commands;
			this.skillMods = skillMods;
			this.schematics = schematics;
		}
		
		public int getPointsRequired() {
			return pointsRequired;
		}
		private boolean isTitle() { return title; }
		public String[] getRequiredSkills() { return requiredSkills; }
		public String getParentSkill() { return parentSkill; }
		public String getXpType() { return xpType; }
		public int getXpCost() { return xpCost; }
		public String[] getCommands() { return commands; }
		public Map<String, Integer> getSkillMods() { return skillMods; }
		public String[] getSchematics() { return schematics; }

		public int getXpCap() {
			return xpCap;
		}
	}
	
	private static class PlayerLevelData {
		private final int requiredXp;
		private final int additionalHealth;
		
		public PlayerLevelData(int requiredXp, int additionalHealth) {
			this.requiredXp = requiredXp;
			this.additionalHealth = additionalHealth;
		}
		
		public int getRequiredXp() {
			return requiredXp;
		}
		
		public int getAdditionalHealth() {
			return additionalHealth;
		}
	}
	
}

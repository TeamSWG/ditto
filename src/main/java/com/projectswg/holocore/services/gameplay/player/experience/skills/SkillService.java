package com.projectswg.holocore.services.gameplay.player.experience.skills;

import com.projectswg.common.data.info.RelationalDatabase;
import com.projectswg.common.data.info.RelationalServerFactory;
import com.projectswg.common.data.swgfile.ClientFactory;
import com.projectswg.common.data.swgfile.visitors.DatatableData;
import com.projectswg.holocore.intents.gameplay.player.badge.SetTitleIntent;
import com.projectswg.holocore.intents.gameplay.player.experience.LevelChangedIntent;
import com.projectswg.holocore.intents.gameplay.player.experience.skills.SkillModIntent;
import com.projectswg.holocore.intents.gameplay.player.experience.skills.GrantSkillIntent;
import com.projectswg.holocore.intents.gameplay.player.experience.skills.SurrenderSkillIntent;
import com.projectswg.holocore.resources.support.objects.swg.creature.CreatureObject;
import me.joshlarson.jlcommon.control.IntentHandler;
import me.joshlarson.jlcommon.control.Service;
import me.joshlarson.jlcommon.log.Log;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class SkillService extends Service {
	
	private static final String GET_ALL_LEVELS = "SELECT * FROM player_level";
	private static final String GET_ALL_MULTIPLIERS = "SELECT * FROM combat_xp_multipliers";
	
	private final Map<String, SkillData> skillDataMap;
	private final Map<String, Integer> levelXpMultipliers;
	private final Map<Short, PlayerLevelData> playerLevelXp;
	
	public SkillService() {
		skillDataMap = new HashMap<>();
		levelXpMultipliers = new HashMap<>();
		playerLevelXp = new HashMap<>();
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
					splitCsv((String) skillsTable.getCell(i, 10)),	// required skills
					(String) skillsTable.getCell(i, 1),				// parent skill
					(String) skillsTable.getCell(i, 12),			// xp type
					(int) skillsTable.getCell(i, 13),				// xp cost
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
	
	private void recursivelyGrantSkills(SkillData skillData, String skillName, CreatureObject target) {
		if (skillData == null || skillName == null || target == null) {
			return;
		}
		
		grantSkill(skillData, skillName, target);	// Grant them that skill
		
		String[] requiredSkills = skillData.getRequiredSkills();
		String parentSkillName = skillData.getParentSkill();
		
		if (parentSkillName != null && !target.hasSkill(parentSkillName)) {
			// Grant parent skill, if there is one and they don't have it
			SkillData parentSkillData = skillDataMap.get(parentSkillName);
			
			recursivelyGrantSkills(parentSkillData, parentSkillName, target);	// Grant parent skill and any required
		}
		
		for (String requiredSkillName : requiredSkills) {
			if (!target.hasSkill(requiredSkillName)) {
				SkillData requiredSkillData = skillDataMap.get(requiredSkillName);
				
				if (requiredSkillData == null) {
					Log.e("Missing SkillData for required skill name: %s", requiredSkillName);
					return;
				}
				
				recursivelyGrantSkills(requiredSkillData, requiredSkillName, target);
			}
		}
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
	
	private void grantSkill(SkillData skillData, String skillName, CreatureObject target) {
		target.addSkill(skillName);
		target.addAbility(skillData.getCommands());
		
		skillData.getSkillMods().forEach((skillModName, skillModValue) -> new SkillModIntent(skillModName, 0, skillModValue, target).broadcast());
		
		new GrantSkillIntent(GrantSkillIntent.IntentType.GIVEN, skillName, target, false).broadcast();
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
		private boolean title;
		private String[] requiredSkills;
		private final String parentSkill;
		private final String xpType;
		private final int xpCost;
		private final String[] commands;
		private final Map<String, Integer> skillMods;
		private final String[] schematics;

		public SkillData(boolean title, String[] requiredSkills, String parentSkill, String xpType, int xpCost, String[] commands, Map<String, Integer> skillMods, String[] schematics) {
			this.title = title;
			this.requiredSkills = requiredSkills;
			this.parentSkill = parentSkill;
			this.xpType = xpType;
			this.xpCost = xpCost;
			this.commands = commands;
			this.skillMods = skillMods;
			this.schematics = schematics;
		}
		
		private boolean isTitle() { return title; }
		public String[] getRequiredSkills() { return requiredSkills; }
		public String getParentSkill() { return parentSkill; }
		public String getXpType() { return xpType; }
		public int getXpCost() { return xpCost; }
		public String[] getCommands() { return commands; }
		public Map<String, Integer> getSkillMods() { return skillMods; }
		public String[] getSchematics() { return schematics; }
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

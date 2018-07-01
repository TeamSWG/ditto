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
package com.projectswg.holocore.services.gameplay.player.group;

import com.projectswg.common.data.encodables.chat.ChatAvatar;
import com.projectswg.common.data.encodables.oob.ProsePackage;
import com.projectswg.common.data.sui.SuiEvent;
import com.projectswg.holocore.intents.gameplay.player.group.GroupEventIntent;
import com.projectswg.holocore.intents.support.global.zone.PlayerEventIntent;
import com.projectswg.holocore.intents.support.global.chat.ChatRoomUpdateIntent;
import com.projectswg.holocore.intents.support.global.chat.ChatRoomUpdateIntent.UpdateType;
import com.projectswg.holocore.intents.support.global.chat.SystemMessageIntent;
import com.projectswg.holocore.intents.support.objects.swg.DestroyObjectIntent;
import com.projectswg.holocore.intents.support.objects.swg.ObjectCreatedIntent;
import com.projectswg.holocore.resources.gameplay.player.group.GroupInviterData;
import com.projectswg.holocore.resources.support.objects.swg.creature.CreatureObject;
import com.projectswg.holocore.resources.support.objects.swg.group.GroupObject;
import com.projectswg.holocore.resources.support.global.player.Player;
import com.projectswg.holocore.resources.support.global.player.Player.PlayerServer;
import com.projectswg.holocore.resources.support.global.zone.sui.SuiButtons;
import com.projectswg.holocore.resources.support.global.zone.sui.SuiListBox;
import com.projectswg.holocore.resources.support.objects.ObjectCreator;
import com.projectswg.holocore.services.support.objects.ObjectStorageService.ObjectLookup;
import com.projectswg.holocore.utilities.IntentFactory;
import me.joshlarson.jlcommon.control.IntentHandler;
import me.joshlarson.jlcommon.control.Service;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class GroupService extends Service {
	
	private final Map<Long, GroupObject> groups;

	public GroupService() {
		groups = new ConcurrentHashMap<>();
	}
	
	@IntentHandler
	private void handleGroupEventIntent(GroupEventIntent gei) {
		switch (gei.getEventType()) {
			case GROUP_INVITE:
				handleGroupInvite(gei.getPlayer(), gei.getTarget());
				break;
			case GROUP_UNINVITE:
				handleGroupUninvite(gei.getPlayer(), gei.getTarget());
				break;
			case GROUP_JOIN:
				handleGroupJoin(gei.getPlayer());
				break;
			case GROUP_DECLINE:
				handleGroupDecline(gei.getPlayer());
				break;
			case GROUP_DISBAND:
				handleGroupDisband(gei.getPlayer());
				break;
			case GROUP_LEAVE:
				handleGroupLeave(gei.getPlayer());
				break;
			case GROUP_MAKE_LEADER:
				handleMakeLeader(gei.getPlayer(), gei.getTarget());
				break;
			case GROUP_KICK:
				handleKick(gei.getPlayer(), gei.getTarget());
				break;
			case GROUP_MAKE_MASTER_LOOTER:
				handleMakeMasterLooter(gei.getPlayer(), gei.getTarget());
				break;
			case GROUP_LOOT:
				handleGroupLootOptions(gei.getPlayer());
				break;
		}
	}
	
	@IntentHandler
	private void handlePlayerEventIntent(PlayerEventIntent pei) {
		switch (pei.getEvent()) {
			case PE_ZONE_IN_SERVER:
				handleMemberRezoned(pei.getPlayer());
				break;
			case PE_DISAPPEAR:
				handleMemberDisappeared(pei.getPlayer());
				break;
			default:
				break;
		}
	}
	
	private void handleGroupLootOptions(Player groupLeader){
		SuiListBox window = new SuiListBox(SuiButtons.OK_CANCEL, "@group:set_loot_type_title", "@group:set_loot_type_text");
		window.addListItem("Free For All");
		window.addListItem("Master Looter");
		window.addListItem("Lottery");
		window.addListItem("Random");

		window.addCallback("handleSelectedItem", (SuiEvent event, Map<String, String> parameters) -> {
			if (event != SuiEvent.OK_PRESSED)
				return;
			
			int selectedRow = SuiListBox.getSelectedRow(parameters);
			String lootRuleMsg;
			
			switch (window.getListItem(selectedRow).getName()){
				case "Free For All" :
					lootRuleMsg = "selected_free4all";
					break;
				case "Master Looter" :
					lootRuleMsg = "selected_master";
					break;
				case "Lottery" :
					lootRuleMsg = "selected_lotto";
					break;
				case "Random" :
					lootRuleMsg = "selected_random";
					break;
				default:
					lootRuleMsg = "selected_free4all";
			}
			
			GroupObject groupObject = (GroupObject) ObjectLookup.getObjectById(groupLeader.getCreatureObject().getGroupId());
			groupObject.setLootRule(selectedRow);
			groupObject.displayLootRuleChangeBox(lootRuleMsg);
			sendSystemMessage(groupLeader, lootRuleMsg);
		});
		window.display(groupLeader);		
	}	
	
	private void handleMemberRezoned(Player player) {
		CreatureObject creatureObject = player.getCreatureObject();
		if (creatureObject.getGroupId() == 0)
			return;
		
		GroupObject groupObject = getGroup(creatureObject.getGroupId());
		
		if (groupObject == null) {
			// Group was destroyed while logged out
			creatureObject.setGroupId(0);
		}
	}
	
	private void handleMemberDisappeared(Player player) {
		CreatureObject creature = player.getCreatureObject();
		Objects.requireNonNull(creature);
		if (creature.getGroupId() == 0)
			return; // Ignore anyone without a group
		
		removePlayerFromGroup(creature);
	}
	
	private void handleGroupDisband(Player player) {
		CreatureObject creo = player.getCreatureObject();
		Objects.requireNonNull(creo);
		GroupObject group = getGroup(creo.getGroupId());
		Objects.requireNonNull(group);
		
		if (group.getLeaderId() != creo.getObjectId()) {
			sendSystemMessage(player, "must_be_leader");
			return;
		}
		
		destroyGroup(group, player);
	}
	
	private void handleGroupLeave(Player player) {
		removePlayerFromGroup(player.getCreatureObject());
	}
	
	private void handleGroupInvite(Player player, CreatureObject target) {
		CreatureObject creature = player.getCreatureObject();
		Objects.requireNonNull(creature);
		if (target == null || !target.isPlayer() || creature.equals(target)) {
			sendSystemMessage(player, "invite_no_target_self");
			return;
		}
		if (target.getGroupId() != 0) {
			sendSystemMessage(player, "already_grouped", "TT", target.getObjectId());
			return;
		}
		
		long groupId = creature.getGroupId();
		if (groupId != 0) {
			GroupObject group = getGroup(groupId);
			Objects.requireNonNull(group);
			
			if (!handleInviteToExistingGroup(player, target, group))
				return;
		}
		sendInvite(player, target, groupId);
	}
	
	private boolean handleInviteToExistingGroup(Player inviter, CreatureObject target, GroupObject group) {
		if (group.getLeaderId() != inviter.getCreatureObject().getObjectId()) {
			sendSystemMessage(inviter, "must_be_leader");
			return false;
		}
		
		if (group.isFull()) {
			sendSystemMessage(inviter, "full");
			return false;
		}
		
		if (target.getInviterData().getId() != 0) {
			if (target.getInviterData().getId() != inviter.getCreatureObject().getGroupId())
				sendSystemMessage(inviter, "considering_other_group", "TT", target.getObjectId());
			else
				sendSystemMessage(inviter, "considering_your_group", "TT", target.getObjectId());
			return false;
		}
		
		return true;
	}
	
	private void handleGroupUninvite(Player player, CreatureObject target) {
		if (target == null) {
			sendSystemMessage(player, "uninvite_no_target_self");
			return;
		}
		
		Player targetOwner = target.getOwner();
		Objects.requireNonNull(targetOwner);
		String targetName = targetOwner.getCharacterName();
		
		if (target.getInviterData() == null) {
			sendSystemMessage(player, "uninvite_not_invited", "TT", targetName);
			return;
		}
		
		sendSystemMessage(player, "uninvite_self", "TT", targetName);
		sendSystemMessage(targetOwner, "uninvite_target", "TT", player.getCharacterName());
		clearInviteData(target);
	}
	
	private void handleGroupJoin(Player player) {
		CreatureObject creature = player.getCreatureObject();
		try {
			Player sender = creature.getInviterData().getSender();
			if (sender == null || sender.getPlayerServer() != PlayerServer.ZONE) { // Inviter logged out, invitation is no good
				sendSystemMessage(player, "must_be_invited");
				return;
			}
			
			long groupId = creature.getInviterData().getId();
			if (groupId == -1) {
				groupId = sender.getCreatureObject().getGroupId();
				if (groupId == 0)
					groupId = -1; // Client wants -1 for default
			}
			if (groupId == -1) { // Group doesn't exist yet
				createGroup(sender, player);
			} else { // Group already exists
				joinGroup(sender.getCreatureObject(), creature, groupId);
			}
		} finally {
			clearInviteData(creature);
		}
	}
	
	private void handleGroupDecline(Player invitee) {
		CreatureObject creature = invitee.getCreatureObject();
		GroupInviterData invitation = creature.getInviterData();
		
		sendSystemMessage(invitee, "decline_self", "TT", invitation.getSender().getCharacterName());
		sendSystemMessage(invitation.getSender(), "decline_leader", "TT", invitee.getCharacterName());
		
		clearInviteData(creature);
	}
	
	private void handleMakeLeader(Player currentLeader, CreatureObject newLeader) {
		CreatureObject currentLeaderCreature = currentLeader.getCreatureObject();
		Objects.requireNonNull(currentLeaderCreature);
		assert newLeader.getGroupId() != 0 : "new leader is not a part of a group";
		GroupObject group = getGroup(newLeader.getGroupId());
		Objects.requireNonNull(group);
		
		if (group.getLeaderId() != currentLeaderCreature.getObjectId()) {
			sendSystemMessage(currentLeader, "must_be_leader");
			return;
		}
		
		if (group.getLeaderId() == newLeader.getObjectId())
			return;
		
		// Set the group leader to newLeader
		sendGroupSystemMessage(group, "new_leader", "TU", newLeader.getObjectName());
		group.setLeader(newLeader);
	}
	
	private void handleMakeMasterLooter(Player player, CreatureObject target) {
		CreatureObject creature = player.getCreatureObject();
		if (creature.getGroupId() == 0) {
			sendSystemMessage(player, "group_only");
			return;
		}
		
		if (target.getOwner() == null){
			return;
		}
		
		GroupObject group = getGroup(creature.getGroupId());
		Objects.requireNonNull(group);

		group.setLootMaster(target.getObjectId());
		sendGroupSystemMessage(group,"new_master_looter", "TU", target.getObjectName());
	}
	
	private void handleKick(Player leader, CreatureObject kickedCreature) {
		Objects.requireNonNull(kickedCreature);
		CreatureObject leaderCreature = leader.getCreatureObject();
		Objects.requireNonNull(leaderCreature);
		long groupId = leaderCreature.getGroupId();
		if (groupId == 0) { // Requester is not in a group
			sendSystemMessage(leader, "group_only");
			return;
		}
		if (kickedCreature.getGroupId() != groupId) { // Requester and Kicked are not in same group
			sendSystemMessage(leader, "must_be_leader");
			return;
		}
		
		GroupObject group = getGroup(groupId);
		Objects.requireNonNull(group);
		if (group.getLeaderId() != leaderCreature.getObjectId()) { // Requester is not leader of group
			sendSystemMessage(leader, "must_be_leader");
			return;
		}
		
		removePlayerFromGroup(kickedCreature);
	}
	
	private void createGroup(Player leader, Player member) {
		GroupObject group = (GroupObject) ObjectCreator.createObjectFromTemplate("object/group/shared_group_object.iff");
		Objects.requireNonNull(group);
		groups.put(group.getObjectId(), group);
		
		group.formGroup(leader.getCreatureObject(), member.getCreatureObject());
		
		new ObjectCreatedIntent(group).broadcast();
		
		String galaxy = leader.getGalaxyName();
		new ChatRoomUpdateIntent(leader, new ChatAvatar(leader.getCharacterChatName()), getGroupChatPath(group.getObjectId(), galaxy), String.valueOf(group.getObjectId()), false).broadcast();
		sendSystemMessage(leader, "formed_self", "TT", leader.getCreatureObject().getObjectId());
		onJoinGroup(member.getCreatureObject(), group);
	}
	
	private void destroyGroup(GroupObject group, Player player) {
		String galaxy = player.getGalaxyName();
		new ChatRoomUpdateIntent(getGroupChatPath(group.getObjectId(), galaxy), String.valueOf(group.getObjectId()), null, new ChatAvatar(player.getCharacterChatName()), null, ChatRoomUpdateIntent.UpdateType.DESTROY).broadcast();
		
		sendGroupSystemMessage(group, "disbanded");
		group.disbandGroup();
		new DestroyObjectIntent(group).broadcast();
		Objects.requireNonNull(groups.remove(group.getObjectId()));
	}
	
	private void joinGroup(CreatureObject inviter, CreatureObject creature, long groupId) {
		Player player = creature.getOwner();
		Objects.requireNonNull(player);
		GroupObject group = getGroup(groupId);
		Objects.requireNonNull(group);
		
		if (group.getLeaderId() != inviter.getObjectId()) {
			sendSystemMessage(player, "join_inviter_not_leader", "TT", inviter.getObjectName());
			return;
		}
		if (group.isFull()) {
			sendSystemMessage(player, "join_full");
			return;
		}
		
		group.addMember(creature);
		onJoinGroup(creature, group);
	}
	
	private void onJoinGroup(CreatureObject creature, GroupObject group) {
		Player player = creature.getOwner();
		Objects.requireNonNull(player);
		sendSystemMessage(player, "joined_self");
		updateChatRoom(player, group, UpdateType.JOIN);
	}
	
	private void removePlayerFromGroup(CreatureObject creature) {
		Objects.requireNonNull(creature);
		assert creature.getGroupId() != 0 : "creature is not within a group";
		GroupObject group = getGroup(creature.getGroupId());
		Objects.requireNonNull(group);
		
		// Check size of the group, if it only has two members, destroy the group
		if (group.getGroupMembers().size() <= 2) {
			destroyGroup(group, group.getLeaderPlayer());
			return;
		}
		
		sendSystemMessage(creature.getOwner(), "removed");
		group.removeMember(creature);
		updateChatRoom(creature.getOwner(), group, UpdateType.LEAVE);
	}
	
	private void updateChatRoom(Player player, GroupObject group, UpdateType updateType) {
		new ChatRoomUpdateIntent(player, getGroupChatPath(group.getObjectId(), player.getGalaxyName()), updateType).broadcast();
	}
	
	private void clearInviteData(CreatureObject creature) {
		creature.updateGroupInviteData(null, 0);
	}
	
	private void sendInvite(Player groupLeader, CreatureObject invitee, long groupId) {
		sendSystemMessage(invitee.getOwner(), "invite_target", "TT", groupLeader.getCharacterName());
		sendSystemMessage(groupLeader, "invite_leader", "TT", invitee.getObjectName());
		
		// Set the invite data to the current group ID
		if (groupId == 0)
			groupId = -1; // Client wants -1 for default
		invitee.updateGroupInviteData(groupLeader, groupId);
	}
	
	private void sendGroupSystemMessage(GroupObject group, String id) {
		Set<CreatureObject> members = group.getGroupMemberObjects();
		
		for (CreatureObject member : members) {
			Objects.requireNonNull(member.getOwner());
			sendSystemMessage(member.getOwner(), id);
		}
	}
	
	private void sendGroupSystemMessage(GroupObject group, String id, Object... objects) {
		Set<CreatureObject> members = group.getGroupMemberObjects();
		
		for (CreatureObject member : members) {
			Objects.requireNonNull(member.getOwner());
			sendSystemMessage(member.getOwner(), id, objects);
		}
	}
	
	private String getGroupChatPath(long groupId, String galaxy) {
		return "SWG." + galaxy + ".group." + groupId + ".GroupChat";
	}
	
	private GroupObject getGroup(long groupId) {
		return groups.get(groupId);
	}
	
	private void sendSystemMessage(Player target, String id) {
		new SystemMessageIntent(target, new ProsePackage("group", id)).broadcast();
	}
	
	private void sendSystemMessage(Player target, String id, Object... objects) {
		IntentFactory.sendSystemMessage(target, "@group:" + id, objects);
	}
}

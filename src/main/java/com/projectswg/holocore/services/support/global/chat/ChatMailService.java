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
package com.projectswg.holocore.services.support.global.chat;

import com.projectswg.common.data.encodables.chat.ChatResult;
import com.projectswg.common.data.encodables.player.Mail;
import com.projectswg.common.network.packets.SWGPacket;
import com.projectswg.common.network.packets.swg.zone.chat.*;
import com.projectswg.holocore.ProjectSWG;
import com.projectswg.holocore.intents.support.global.zone.PlayerEventIntent;
import com.projectswg.holocore.intents.support.global.chat.PersistentMessageIntent;
import com.projectswg.holocore.intents.support.global.network.InboundPacketIntent;
import com.projectswg.holocore.resources.support.objects.swg.creature.CreatureObject;
import com.projectswg.holocore.resources.support.objects.swg.player.PlayerObject;
import com.projectswg.holocore.resources.support.global.player.Player;
import com.projectswg.holocore.resources.support.data.server_info.CachedObjectDatabase;
import com.projectswg.holocore.resources.support.data.server_info.ObjectDatabase;
import com.projectswg.holocore.services.support.global.zone.CharacterLookupService.PlayerLookup;
import me.joshlarson.jlcommon.control.IntentHandler;
import me.joshlarson.jlcommon.control.Service;

import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

public class ChatMailService extends Service {
	
	private final ObjectDatabase<Mail> mails;
	private int maxMailId;
	
	public ChatMailService() {
		mails = new CachedObjectDatabase<>("odb/mails.db", Mail::create, Mail::saveMail);
		maxMailId = 1;
		
	}
	
	@Override
	public boolean initialize() {
		mails.load();
		mails.traverse(mail -> {
			if (mail.getId() >= maxMailId)
				maxMailId = mail.getId() + 1;
		});
		return super.initialize();
	}
	
	@Override
	public boolean terminate() {
		mails.close();
		return super.terminate();
	}
	
	@IntentHandler
	private void handlePlayerEventIntent(PlayerEventIntent pei) {
		Player player = pei.getPlayer();
		if (player == null)
			return;

		switch (pei.getEvent()) {
			case PE_FIRST_ZONE:
				sendPersistentMessageHeaders(player);
				break;
			default:
				break;
		}
	}
	
	@IntentHandler
	private void handleInboundPacketIntent(InboundPacketIntent gpi){ 
		SWGPacket p = gpi.getPacket();
		String galaxyName = ProjectSWG.getGalaxy().getName();
		switch (p.getPacketType()) {
			/* Mails */
			case CHAT_PERSISTENT_MESSAGE_TO_SERVER:
				if (p instanceof ChatPersistentMessageToServer)
					handleSendPersistentMessage(gpi.getPlayer(), galaxyName, (ChatPersistentMessageToServer) p);
				break;
			case CHAT_REQUEST_PERSISTENT_MESSAGE:
				if (p instanceof ChatRequestPersistentMessage)
					handlePersistentMessageRequest(gpi.getPlayer(), galaxyName, (ChatRequestPersistentMessage) p);
				break;
			case CHAT_DELETE_PERSISTENT_MESSAGE:
				if (p instanceof ChatDeletePersistentMessage)
					deletePersistentMessage(((ChatDeletePersistentMessage) p).getMailId());
				break;
			default: break;
		}
	}
	
	private void handleSendPersistentMessage(Player sender, String galaxy, ChatPersistentMessageToServer request) {
		String recipientStr = request.getRecipient().toLowerCase(Locale.ENGLISH);
		
		if (recipientStr.contains(" "))
			recipientStr = recipientStr.split(" ")[0];
		
		Player recipient = PlayerLookup.getPlayerByFirstName(recipientStr);
		CreatureObject recipientCreature = PlayerLookup.getCharacterByFirstName(recipientStr);
		ChatResult result = ChatResult.SUCCESS;
		
		if (recipientCreature == null)
			result = ChatResult.TARGET_AVATAR_DOESNT_EXIST;
		
		if (sender.getPlayerObject().isIgnored(recipientStr))
			result = ChatResult.IGNORED;
		
		sender.sendPacket(new ChatOnSendPersistentMessage(result, request.getCounter()));
		
		if (result != ChatResult.SUCCESS)
			return;
		
		Mail mail = new Mail(sender.getCharacterName().split(" ")[0].toLowerCase(Locale.US), request.getSubject(), request.getMessage(), recipientCreature.getObjectId());
		mail.setId(maxMailId++);
		mail.setTimestamp((int) (new Date().getTime() / 1000));
		mail.setOutOfBandPackage(request.getOutOfBandPackage());
		mails.add(mail);
		
		if (recipient != null) {
			sendPersistentMessage(recipient, mail, MailFlagType.HEADER_ONLY, galaxy);
		}
	}
	
	@IntentHandler
	private void handlePersistentMessageIntent(PersistentMessageIntent pmi) {
		if (pmi.getReceiver() == null)
			return;
		
		Player recipient = pmi.getReceiver().getOwner();
		
		if (recipient == null)
			return;
		
		Mail mail = pmi.getMail();
		mail.setId(maxMailId);
		maxMailId++;
		mail.setTimestamp((int) (new Date().getTime() / 1000));
		
		mails.add(mail);
		
		sendPersistentMessage(recipient, mail, MailFlagType.HEADER_ONLY, pmi.getGalaxy());
	}
	
	private void handlePersistentMessageRequest(Player player, String galaxy, ChatRequestPersistentMessage request) {
		Mail mail = getMail(request.getMailId());
		if (mail == null)
			return;
		
		if (mail.getReceiverId() != player.getCreatureObject().getObjectId())
			return;
		
		mail.setStatus(Mail.READ);
		sendPersistentMessage(player, mail, MailFlagType.FULL_MESSAGE, galaxy);
	}
	
	private Mail getMail(int id) {
		AtomicReference<Mail> ref = new AtomicReference<>(null);
		mails.traverseInterruptable((m) -> {
			if (m.getId() == id) {
				ref.set(m);
				return false;
			}
			return true;
		});
		return ref.get();
	}
	
	private void sendPersistentMessageHeaders(Player player) {
		if (player == null || player.getCreatureObject() == null)
			return;
		
		final List <Mail> playersMail = new LinkedList<>();
		final long receiverId = player.getCreatureObject().getObjectId();

		mails.traverse(element -> {
			if (element.getReceiverId() == receiverId)
				playersMail.add(element);
		});
		
		String galaxy = ProjectSWG.getGalaxy().getName();
		for (Mail mail : playersMail)
			sendPersistentMessage(player, mail, MailFlagType.HEADER_ONLY, galaxy);
	}
	
	private void sendPersistentMessage(Player receiver, Mail mail, MailFlagType requestType, String galaxy) {
		if (receiver == null || receiver.getCreatureObject() == null)
			return;

		PlayerObject ghost = receiver.getPlayerObject();
		if (ghost.isIgnored(mail.getSender())) {
			mails.remove(mail);
			return;
		}

		ChatPersistentMessageToClient SWGPacket = null;
		
		switch (requestType) {
			case FULL_MESSAGE:
				SWGPacket = new ChatPersistentMessageToClient(mail, ProjectSWG.getGalaxy().getName(), false);
				break;
			case HEADER_ONLY:
				SWGPacket = new ChatPersistentMessageToClient(mail, ProjectSWG.getGalaxy().getName(), true);
				break;
		}
		
		receiver.sendPacket(SWGPacket);
	}
	
	private void deletePersistentMessage(int mailId) {
		mails.remove(getMail(mailId));
	}
	
	private enum MailFlagType {
		FULL_MESSAGE,
		HEADER_ONLY
	}
	
}

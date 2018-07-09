package com.projectswg.holocore.resources.support.objects.radial;

import com.projectswg.common.data.radial.RadialItem;
import com.projectswg.common.data.radial.RadialOption;
import com.projectswg.holocore.resources.support.global.player.Player;
import com.projectswg.holocore.resources.support.objects.GameObjectType;
import com.projectswg.holocore.resources.support.objects.GameObjectTypeMask;
import com.projectswg.holocore.resources.support.objects.radial.object.AIObjectRadial;
import com.projectswg.holocore.resources.support.objects.radial.object.SWGObjectRadial;
import com.projectswg.holocore.resources.support.objects.radial.object.UsableObjectRadial;
import com.projectswg.holocore.resources.support.objects.radial.object.survey.ObjectSurveyToolRadial;
import com.projectswg.holocore.resources.support.objects.radial.object.uniform.ObjectUniformBoxRadial;
import com.projectswg.holocore.resources.support.objects.radial.terminal.*;
import com.projectswg.holocore.resources.support.objects.swg.SWGObject;
import com.projectswg.holocore.resources.support.objects.swg.custom.AIObject;
import org.jetbrains.annotations.NotNull;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public enum RadialHandler {
	INSTANCE;
	
	private final Map<String, RadialHandlerInterface> handlers = new HashMap<>();
	private final Map<GameObjectType, RadialHandlerInterface> gotHandlers = new EnumMap<>(GameObjectType.class);
	private final Map<GameObjectTypeMask, RadialHandlerInterface> gotmHandlers = new EnumMap<>(GameObjectTypeMask.class);
	private final Map<Class<? extends SWGObject>, RadialHandlerInterface> classHandlers = new HashMap<>();
	private final SWGObjectRadial genericRadialHandler = new SWGObjectRadial();
	
	RadialHandler() {
		initializeTerminalRadials();
		initializeSurveyRadials();
		initializeMiscRadials();
		
		RadialHandlerInterface aiHandler = new AIObjectRadial();
		
		classHandlers.put(AIObject.class, aiHandler);
	}
	
	public void registerHandler(String iff, RadialHandlerInterface handler) {
		handlers.put(iff, handler);
	}
	
	public void registerHandler(GameObjectType got, RadialHandlerInterface handler) {
		gotHandlers.put(got, handler);
	}
	
	public void registerHandler(GameObjectTypeMask gotm, RadialHandlerInterface handler) {
		gotmHandlers.put(gotm, handler);
	}
	
	public void getOptions(List<RadialOption> options, Player player, SWGObject target) {
		getHandler(target).getOptions(options, player, target);
	}
	
	public void handleSelection(Player player, SWGObject target, RadialItem selection) {
		getHandler(target).handleSelection(player, target, selection);
	}
	
	@NotNull
	private RadialHandlerInterface getHandler(SWGObject target) {
		String type = target.getTemplate();
		RadialHandlerInterface handler = handlers.get(type);
		if (handler != null)
			return handler;
		
		if (target != null) {
			handler = gotHandlers.get(target.getGameObjectType());
			if (handler != null)
				return handler;
			
			handler = gotmHandlers.get(target.getGameObjectType().getMask());
			if (handler != null)
				return handler;
			
			handler = classHandlers.get(target.getClass());
			
			if (handler != null)
				return handler;
		}
		
		return genericRadialHandler;
	}
	
	private void initializeTerminalRadials() {
		registerHandler("object/tangible/terminal/shared_terminal_bank.iff", new TerminalBankRadial());
		registerHandler("object/tangible/terminal/shared_terminal_bazaar.iff", new TerminalBazaarRadial());
		registerHandler("object/tangible/terminal/shared_terminal_travel.iff", new TerminalTravelRadial());
		registerHandler("object/tangible/travel/ticket_collector/shared_ticket_collector.iff", new TerminalTicketCollectorRadial());
		registerHandler("object/tangible/travel/travel_ticket/base/shared_base_travel_ticket.iff", new TerminalTicketRadial());
		registerHandler("object/tangible/terminal/shared_terminal_character_builder.iff", new TerminalCharacterBuilderRadial());
	}
	
	private void initializeSurveyRadials() {
		registerHandler(GameObjectType.GOT_TOOL_SURVEY, new ObjectSurveyToolRadial());
	}
	
	private void initializeMiscRadials() {
		registerHandler("object/tangible/npe/shared_npe_uniform_box.iff", new UsableObjectRadial());
		registerHandler("object/tangible/npe/shared_npe_uniform_box.iff", new ObjectUniformBoxRadial());
	}
}

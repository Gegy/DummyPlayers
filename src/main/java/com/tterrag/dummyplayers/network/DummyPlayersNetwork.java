package com.tterrag.dummyplayers.network;

import com.tterrag.dummyplayers.DummyPlayers;
import com.tterrag.dummyplayers.entity.UpdateDummyTexturesMessage;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = DummyPlayers.MODID, bus = EventBusSubscriber.Bus.MOD)
public class DummyPlayersNetwork {
	public static final String PROTOCOL_VERSION = "1";

    @SubscribeEvent
	public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToClient(UpdateDummyTexturesMessage.TYPE, UpdateDummyTexturesMessage.STREAM_CODEC, UpdateDummyTexturesMessage::handle);
	}
}


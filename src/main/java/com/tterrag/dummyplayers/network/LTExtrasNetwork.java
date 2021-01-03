package com.tterrag.dummyplayers.network;

import com.tterrag.dummyplayers.DummyPlayers;
import com.tterrag.dummyplayers.entity.UpdateDummyTexturesMessage;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.simple.SimpleChannel;

public class LTExtrasNetwork {

	public static final String PROTOCOL_VERSION = "1";
	
	public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
			new ResourceLocation(DummyPlayers.MODID, "main"),
			() -> PROTOCOL_VERSION,
			PROTOCOL_VERSION::equals,
			PROTOCOL_VERSION::equals
	);

	public static void register() {
		CHANNEL.registerMessage(0, UpdateDummyTexturesMessage.class, UpdateDummyTexturesMessage::toBytes, UpdateDummyTexturesMessage::fromBytes, UpdateDummyTexturesMessage::handle);
	}
}


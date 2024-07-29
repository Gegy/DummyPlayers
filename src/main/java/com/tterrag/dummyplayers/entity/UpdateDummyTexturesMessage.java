package com.tterrag.dummyplayers.entity;

import com.tterrag.dummyplayers.DummyPlayers;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record UpdateDummyTexturesMessage(int id) implements CustomPacketPayload {
	public static final StreamCodec<ByteBuf, UpdateDummyTexturesMessage> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, UpdateDummyTexturesMessage::id,
			UpdateDummyTexturesMessage::new
	);

	public static final Type<UpdateDummyTexturesMessage> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(DummyPlayers.MODID, "update_dummy_textures"));

	public static void handle(UpdateDummyTexturesMessage message, IPayloadContext ctx) {
		Entity entity = Minecraft.getInstance().level.getEntity(message.id);
		if (entity instanceof DummyPlayerEntity dummyPlayer) {
			dummyPlayer.reloadTextures();
		}
	}

	@Override
	public Type<UpdateDummyTexturesMessage> type() {
		return TYPE;
	}
}

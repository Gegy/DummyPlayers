package com.tterrag.dummyplayers.entity;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.logging.LogUtils;
import com.tterrag.dummyplayers.DummyPlayers;
import com.tterrag.dummyplayers.item.DummyPlayerItem;
import com.tterrag.registrate.util.entry.EntityEntry;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public class DummyPlayerEntity extends ArmorStand {

	public static final Logger LOGGER = LogUtils.getLogger();

	private static final EntityDataAccessor<ResolvableProfile> GAME_PROFILE = SynchedEntityData.defineId(DummyPlayerEntity.class, DummyPlayers.PROFILE_SERIALIZER.get());
	private static final EntityDataAccessor<Optional<Component>> PREFIX = SynchedEntityData.defineId(DummyPlayerEntity.class, EntityDataSerializers.OPTIONAL_COMPONENT);
	private static final EntityDataAccessor<Optional<Component>> SUFFIX = SynchedEntityData.defineId(DummyPlayerEntity.class, EntityDataSerializers.OPTIONAL_COMPONENT);

	// Clientside texture info
	private final AtomicBoolean reloadTextures = new AtomicBoolean(true);

	private Supplier<PlayerSkin> skinLookup = () -> DefaultPlayerSkin.get(getUUID());

	public DummyPlayerEntity(EntityType<? extends DummyPlayerEntity> type, Level worldIn) {
		super(type, worldIn);
		if (!worldIn.isClientSide) {
			// Show arms always on
			this.entityData.set(DATA_CLIENT_FLAGS, (byte) 0b100);
		}
	}

	public DummyPlayerEntity(Level worldIn, double posX, double posY, double posZ) {
		this(DummyPlayers.DUMMY_PLAYER.get(), worldIn);
		this.setPos(posX, posY, posZ);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(GAME_PROFILE, new ResolvableProfile(Optional.empty(), Optional.empty(), new PropertyMap()));
		builder.define(PREFIX, Optional.empty());
		builder.define(SUFFIX, Optional.empty());
	}

	@Override
	public Component getTypeName() {
		return getProfile().name().isEmpty() ? super.getTypeName() : Component.literal(getProfile().name().get());
	}

	@Override
	public Component getDisplayName() {
		MutableComponent ret = super.getDisplayName().copy();
		Component prefix = this.entityData.get(PREFIX).orElse(null);
		Component suffix = this.entityData.get(SUFFIX).orElse(null);
		if (prefix != null) {
			ret = prefix.copy().append(ret);
		}
		if (suffix != null) {
			ret = ret.append(suffix.copy());
		}
		return ret;
	}

	@Override
	public ItemStack getPickedResult(HitResult target) {
		return new ItemStack(DummyPlayers.SPAWNER.get());
	}

	public ResolvableProfile getProfile() {
		return this.entityData.get(GAME_PROFILE);
	}

	@Override
	public void addAdditionalSaveData(CompoundTag compound) {
		super.addAdditionalSaveData(compound);

		this.entityData.get(PREFIX)
			.ifPresent(prefix -> compound.putString("name_prefix", Component.Serializer.toJson(prefix, registryAccess())));
		this.entityData.get(SUFFIX)
			.ifPresent(suffix -> compound.putString("name_suffix", Component.Serializer.toJson(suffix, registryAccess())));

		compound.put("profile", ResolvableProfile.CODEC.encodeStart(NbtOps.INSTANCE, getProfile()).getOrThrow());
	}

	@Override
	public void readAdditionalSaveData(CompoundTag compound) {
		super.readAdditionalSaveData(compound);

		if (compound.contains("name_prefix", Tag.TAG_STRING)) {
			this.entityData.set(PREFIX, Optional.ofNullable(Component.Serializer.fromJson(compound.getString("name_prefix"), registryAccess())));
		}
		if (compound.contains("name_suffix", Tag.TAG_STRING)) {
			this.entityData.set(SUFFIX, Optional.ofNullable(Component.Serializer.fromJson(compound.getString("name_suffix"), registryAccess())));
		}

		if (compound.contains("profile")) {
			ResolvableProfile.CODEC.parse(NbtOps.INSTANCE, compound.get("profile"))
					.resultOrPartial(error -> LOGGER.error("Failed to parse profile: {}", error))
					.ifPresent(profile -> {
						// Only update the profile (and thus the texture) if it has changed in some way
						// Avoids unnecessary texture reloads on the client when changing pose/name
						if (!getProfile().equals(profile)) {
							this.entityData.set(GAME_PROFILE, profile);
							fillProfile();
						}
                    });
		}
	}

	@SuppressWarnings("resource")
	@Override
	public void tick() {
		super.tick();
		if (!level().isClientSide && reloadTextures.compareAndSet(true, false)) {
			PacketDistributor.sendToPlayersTrackingEntity(this, new UpdateDummyTexturesMessage(this.getId()));
		}
	}

	void fillProfile() {
		if (getProfile().isResolved()) {
			return;
		}
        getProfile().resolve().thenAcceptAsync(
				resolvedProfile -> {
					entityData.set(GAME_PROFILE, resolvedProfile);
					reloadTextures();
				},
				SkullBlockEntity.CHECKED_MAIN_THREAD_EXECUTOR
		);
	}

	@OnlyIn(Dist.CLIENT)
	protected void updateSkinTexture() {
		if (reloadTextures.compareAndSet(true, false)) {
			ResolvableProfile profile = getProfile();
			PlayerSkin defaultSkin = DefaultPlayerSkin.get(profile.id().orElse(getUUID()));
			if (profile.properties().isEmpty()) {
				skinLookup = () -> defaultSkin;
				return;
			}
			LOGGER.info("Loading skin data for GameProfile: {}", profile);
			skinLookup = createSkinLookup(profile.gameProfile(), defaultSkin);
		}
	}

	@OnlyIn(Dist.CLIENT)
	private static Supplier<PlayerSkin> createSkinLookup(GameProfile profile, PlayerSkin defaultSkin) {
		Minecraft minecraft = Minecraft.getInstance();
        CompletableFuture<PlayerSkin> skinFuture = minecraft.getSkinManager().getOrLoad(profile);
		return () -> {
			PlayerSkin skin = skinFuture.getNow(defaultSkin);
			return !skin.secure() ? defaultSkin : skin;
		};
	}

	@OnlyIn(Dist.CLIENT)
	public PlayerSkin skin() {
		updateSkinTexture();
		return skinLookup.get();
	}

	void reloadTextures() {
		this.reloadTextures.set(true);
	}
}

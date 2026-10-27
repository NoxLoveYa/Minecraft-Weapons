package com.noxloveya.mcweapons.network;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.noxloveya.mcweapons.McWeapons;
import com.noxloveya.mcweapons.combat.Hitscan;
import com.noxloveya.mcweapons.gun.GunDefinition;
import com.noxloveya.mcweapons.gun.GunDefinitions;
import com.noxloveya.mcweapons.gun.GunItem;
import com.noxloveya.mcweapons.gun.GunState;
import com.noxloveya.mcweapons.registry.ModComponents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.commands.ReloadCommand;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.InclusiveRange;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.HitResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Registers the payloads and checks every client intent against the gun definition. */
public final class ServerHandlers {
	/** Present on a player's movement speed while they aim, which also makes it the per-player aiming state. */
	public static final Identifier ADS_SPEED = McWeapons.id("ads_speed");
	/** World datapack the gun tuner saves into. It is picked up by /reload and on the next world load. */
	private static final String TUNING_PACK = "mcweapons_tuning";

	private ServerHandlers() {
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(ShootPayload.TYPE, ShootPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ReloadPayload.TYPE, ReloadPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(AimPayload.TYPE, AimPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(TuneGunPayload.TYPE, TuneGunPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(GunAnimationPayload.TYPE, GunAnimationPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(GunAnimationPayload.TYPE, GunAnimationPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ShotFxPayload.TYPE, ShotFxPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(GunDefinitionsPayload.TYPE, GunDefinitionsPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(HitMarkerPayload.TYPE, HitMarkerPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(ShootPayload.TYPE, (payload, context) -> shoot(context.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(ReloadPayload.TYPE, (payload, context) -> reload(context.player(), payload.slot()));
		ServerPlayNetworking.registerGlobalReceiver(AimPayload.TYPE, (payload, context) -> setAiming(context.player(), payload.aiming()));
		ServerPlayNetworking.registerGlobalReceiver(TuneGunPayload.TYPE, (payload, context) -> tune(context.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(GunAnimationPayload.TYPE, (payload, context) -> inspect(context.player(), payload.action()));

		ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.register((player, joined) ->
				ServerPlayNetworking.send(player, new GunDefinitionsPayload(GunDefinitions.all())));
		ServerTickEvents.END_SERVER_TICK.register(server -> server.getPlayerList().getPlayers().forEach(player -> {
			if (!GunItem.holdsGun(player)) {
				setAiming(player, false);
			}
			keepGunOutOfOffhand(player);
		}));
	}

	/**
	 * Guns are held in the main hand only. One that ends up in the offhand, by the swap key, an inventory click, creative or a command,
	 * goes back into the inventory, or onto the ground when it is full. Checked here because there are too many ways in to guard each one.
	 */
	private static void keepGunOutOfOffhand(ServerPlayer player) {
		ItemStack offhand = player.getOffhandItem();
		if (offhand.getItem() instanceof GunItem) {
			player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
			player.getInventory().placeItemBackInInventory(offhand, Prediction.SERVER_ONLY);
		}
	}

	private static void shoot(ServerPlayer player, ShootPayload payload) {
		ItemStack gun = heldGun(player, payload.slot());
		GunDefinition definition = GunItem.definition(gun);
		if (definition == null) {
			return;
		}
		GunState state = gun.getOrDefault(ModComponents.GUN_STATE, GunState.EMPTY);
		long now = player.level().getGameTime();
		// 1 tick of allowance for latency jitter.
		if (state.reloading() || !definition.readyToFire(state.lastShotTick(), now, 1)) {
			return;
		}
		gun.set(ModComponents.GUN_STATE, new GunState(0, now));
		GunItem item = (GunItem) gun.getItem();
		int ammo = GunItem.ammo(gun, definition);
		if (ammo <= 0) {
			playSound(player, item.sound("dry"), 1.0F);
			return;
		}
		gun.set(ModComponents.AMMO, ammo - 1);
		Hitscan.Shot shot = Hitscan.fire(player, gun, definition, payload.seed(), isAiming(player));
		HitResult hit = shot.hit();
		if (shot.hurt()) {
			ServerPlayNetworking.send(player, new HitMarkerPayload(shot.headshot(), shot.killed()));
		}
		// Volume above 1 only widens the range (16 blocks per unit), so gunshots carry about 48 blocks.
		playSound(player, item.sound("fire"), 3.0F);
		ShotFxPayload fx = new ShotFxPayload(player.getId(), hit.getLocation(), hit.getType());
		PlayerLookup.tracking(player).forEach(other -> ServerPlayNetworking.send(other, fx));
		ServerPlayNetworking.send(player, fx);
	}

	private static void reload(ServerPlayer player, int slot) {
		ItemStack gun = heldGun(player, slot);
		GunDefinition definition = GunItem.definition(gun);
		if (definition == null) {
			return;
		}
		GunState state = gun.getOrDefault(ModComponents.GUN_STATE, GunState.EMPTY);
		int ammo = GunItem.ammo(gun, definition);
		if (state.reloading() || ammo >= definition.reloadTarget(ammo)) {
			return;
		}
		int ticks = ammo == 0 ? definition.reloadEmptyTicks() : definition.reloadTicks();
		// GunItem.inventoryTick adds the rounds once this tick is reached, or cancels if the gun leaves the main hand.
		gun.set(ModComponents.GUN_STATE, new GunState(player.level().getGameTime() + ticks, state.lastShotTick()));
		showOthers(player, ammo == 0 ? "reload_empty" : "reload");
	}

	/** Inspects are the client's own choice (and cosmetic), so the server only checks one is possible before passing it on. */
	// shortcut: no rate limit on relayed inspects; add a per-player cooldown if a client spams them.
	private static void inspect(ServerPlayer player, String action) {
		if (GunItem.INSPECT_ACTIONS.contains(action) && GunItem.holdsGun(player)
				&& !player.getMainHandItem().getOrDefault(ModComponents.GUN_STATE, GunState.EMPTY).reloading()) {
			showOthers(player, action);
		}
	}

	/** Plays a gun animation for everyone who can see {@code player}; their own client already played it. */
	private static void showOthers(ServerPlayer player, String action) {
		GunAnimationPayload payload = new GunAnimationPayload(player.getId(), action);
		PlayerLookup.tracking(player).forEach(other -> ServerPlayNetworking.send(other, payload));
	}

	private static void setAiming(ServerPlayer player, boolean aiming) {
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		GunDefinition definition = GunItem.definition(player.getMainHandItem());
		if (aiming && definition != null && GunItem.holdsGun(player)) {
			speed.addOrUpdateTransientModifier(new AttributeModifier(ADS_SPEED, definition.moveSpeedAdsMultiplier() - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		} else {
			speed.removeModifier(ADS_SPEED);
		}
	}

	public static boolean isAiming(ServerPlayer player) {
		return player.getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(ADS_SPEED);
	}

	/** The gun tuner. Ops only, and only for guns that already have a definition, which also keeps the id safe as a file name. */
	private static void tune(ServerPlayer player, TuneGunPayload payload) {
		Identifier id = payload.id();
		if (!player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER) || GunDefinitions.get(id) == null) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		Path pack = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve(TUNING_PACK);
		Path file = pack.resolve("data/" + id.getNamespace() + "/guns/" + id.getPath() + ".json");
		if (payload.action() == TuneGunPayload.Action.RESET) {
			try {
				Files.deleteIfExists(file);
			} catch (IOException e) {
				tuneFailed(player, file, e);
				return;
			}
			// Vanilla's /reload path; it sends every player the gun definitions again.
			ReloadCommand.reloadPacks(server.getPackRepository().getSelectedIds(), player.createCommandSourceStack());
			return;
		}
		if (payload.action() == TuneGunPayload.Action.SAVE) {
			try {
				Files.createDirectories(file.getParent());
				Files.writeString(pack.resolve("pack.mcmeta"), packMeta());
				Files.writeString(file, GunDefinitions.toJsonText(id, payload.definition()));
			} catch (IOException e) {
				// Still applied below, so the values on screen are not lost.
				tuneFailed(player, file, e);
			}
		}
		GunDefinitions.put(id, payload.definition());
		GunDefinitionsPayload sync = new GunDefinitionsPayload(GunDefinitions.all());
		server.getPlayerList().getPlayers().forEach(other -> ServerPlayNetworking.send(other, sync));
	}

	private static void tuneFailed(ServerPlayer player, Path file, IOException e) {
		McWeapons.LOGGER.error("Gun tuner could not write {}", file, e);
		player.sendSystemMessage(Component.translatable("gui.mcweapons.tuner.failed", String.valueOf(e.getMessage())).withStyle(ChatFormatting.RED));
	}

	private static String packMeta() {
		PackMetadataSection meta = new PackMetadataSection(Component.literal("Gun values saved by the Minecraft Weapons tuner"),
				new InclusiveRange<>(SharedConstants.getCurrentVersion().packVersion(PackType.SERVER_DATA)));
		JsonObject json = new JsonObject();
		json.add(PackMetadataSection.SERVER_TYPE.name(), PackMetadataSection.SERVER_TYPE.codec().encodeStart(JsonOps.INSTANCE, meta).getOrThrow());
		return json.toString();
	}

	/** The main-hand stack if the client named the selected slot, otherwise empty. Checking the slot first also keeps bad indexes out. */
	private static ItemStack heldGun(ServerPlayer player, int slot) {
		return slot == player.getInventory().getSelectedSlot() && player.isAlive() && GunItem.holdsGun(player) ? player.getMainHandItem() : ItemStack.EMPTY;
	}

	private static void playSound(ServerPlayer player, SoundEvent sound, float volume) {
		// Skips the shooter, whose client already played it on the click.
		player.level().playSound(player, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, volume, 1.0F);
	}
}

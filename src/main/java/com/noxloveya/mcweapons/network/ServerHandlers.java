package com.noxloveya.mcweapons.network;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.noxloveya.mcweapons.McWeapons;
import com.noxloveya.mcweapons.combat.Ballistics;
import com.noxloveya.mcweapons.combat.Bullets;
import com.noxloveya.mcweapons.gun.AttachmentItem;
import com.noxloveya.mcweapons.gun.AttachmentSlot;
import com.noxloveya.mcweapons.gun.Attachments;
import com.noxloveya.mcweapons.gun.GunDefinition;
import com.noxloveya.mcweapons.gun.GunDefinitions;
import com.noxloveya.mcweapons.gun.GunItem;
import com.noxloveya.mcweapons.gun.GunState;
import com.noxloveya.mcweapons.gun.RoundReload;
import com.noxloveya.mcweapons.registry.ModComponents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.minecraft.util.Mth;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Registers the payloads and checks every client intent against the gun definition. */
public final class ServerHandlers {
	/** Present on a player's movement speed while they aim, which also makes it the per-player aiming state. */
	public static final Identifier ADS_SPEED = McWeapons.id("ads_speed");
	/** Present while an aiming player holds their breath (see BreathMeter): they move this much slower again. */
	private static final Identifier BREATH_SPEED = McWeapons.id("breath_speed");
	private static final float BREATH_MOVE_SPEED_MULTIPLIER = 0.75F;
	/** World datapack the gun tuner saves into. It is picked up by /reload and on the next world load. */
	private static final String TUNING_PACK = "mcweapons_tuning";
	/** Furthest a shot's reported eye may be from the server's, in blocks: a sprint or a jump between ticks, not a teleport. */
	private static final double MAX_SHOT_ORIGIN_OFFSET = 3;

	private ServerHandlers() {
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(ShootPayload.TYPE, ShootPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ReloadPayload.TYPE, ReloadPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(AimPayload.TYPE, AimPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(TuneGunPayload.TYPE, TuneGunPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(GunAnimationPayload.TYPE, GunAnimationPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(FireModePayload.TYPE, FireModePayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(AttachPayload.TYPE, AttachPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ReticlePayload.TYPE, ReticlePayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(LaserColorPayload.TYPE, LaserColorPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(EyeDistancePayload.TYPE, EyeDistancePayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SwitchPayload.TYPE, SwitchPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(GunAnimationPayload.TYPE, GunAnimationPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(BulletPayload.TYPE, BulletPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(BulletImpactPayload.TYPE, BulletImpactPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(GunDefinitionsPayload.TYPE, GunDefinitionsPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(HitMarkerPayload.TYPE, HitMarkerPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(ShootPayload.TYPE, (payload, context) -> shoot(context.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(ReloadPayload.TYPE, (payload, context) -> {
			if (payload.stopAfter() > 0) {
				stopReload(context.player(), payload.slot(), payload.stopAfter());
			} else {
				reload(context.player(), payload.slot());
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(AimPayload.TYPE, (payload, context) -> setAiming(context.player(), payload.aiming(), payload.holdingBreath()));
		ServerPlayNetworking.registerGlobalReceiver(TuneGunPayload.TYPE, (payload, context) -> tune(context.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(GunAnimationPayload.TYPE, (payload, context) -> inspect(context.player(), payload.action()));
		ServerPlayNetworking.registerGlobalReceiver(FireModePayload.TYPE, (payload, context) -> switchFireMode(context.player(), payload.slot()));
		ServerPlayNetworking.registerGlobalReceiver(AttachPayload.TYPE, (payload, context) -> attach(context.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(ReticlePayload.TYPE, (payload, context) -> {
			// Looks only, and Reticle keeps its own values in range, so any held gun takes it.
			ItemStack gun = heldGun(context.player(), payload.slot());
			if (!gun.isEmpty()) {
				gun.set(ModComponents.RETICLE, payload.reticle());
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(LaserColorPayload.TYPE, (payload, context) -> {
			// Looks only, like the reticle: any held gun takes any color.
			ItemStack gun = heldGun(context.player(), payload.slot());
			if (!gun.isEmpty()) {
				gun.set(ModComponents.LASER_COLOR, payload.color() & 0xFFFFFF);
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(EyeDistancePayload.TYPE, (payload, context) -> {
			// Looks only: any held gun takes one for any optic, kept in range (Attachments.setEyeDistance).
			ItemStack gun = heldGun(context.player(), payload.slot());
			if (!gun.isEmpty() && BuiltInRegistries.ITEM.getValue(payload.scope()) instanceof AttachmentItem scope && scope.slot() == AttachmentSlot.OPTIC) {
				Attachments.setEyeDistance(gun, scope, payload.distance());
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(SwitchPayload.TYPE, (payload, context) -> {
			// Looks only: a laser or flashlight fitted to the held gun switches on or off.
			ItemStack gun = heldGun(context.player(), payload.slot());
			if (!gun.isEmpty() && BuiltInRegistries.ITEM.getValue(payload.device()) instanceof AttachmentItem device && Attachments.switchable(gun).contains(device)) {
				Attachments.switchOn(gun, device, payload.on());
			}
		});

		ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.register((player, joined) ->
				ServerPlayNetworking.send(player, new GunDefinitionsPayload(GunDefinitions.all())));
		ServerTickEvents.END_SERVER_TICK.register(server -> server.getPlayerList().getPlayers().forEach(player -> {
			if (!GunItem.holdsGun(player)) {
				setAiming(player, false, false);
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
		gun.set(ModComponents.GUN_STATE, new GunState(0, definition.shotRecorded(state.lastShotTick(), now, 1)));
		GunItem item = (GunItem) gun.getItem();
		int ammo = GunItem.ammo(gun, definition);
		// Empty, or on safe (the trigger is blocked): the same dry click, and no shot.
		if (ammo <= 0 || GunDefinition.SAFE.equals(GunItem.fireMode(gun, definition))) {
			playSound(player, item.sound("dry"), 1.0F);
			return;
		}
		gun.set(ModComponents.AMMO, ammo - 1);
		// The client's eye and aim when it fired: the server's copies trail it by a tick or so, and its aim here would already include the
		// recoil of this very shot. An eye too far from the server's (a teleport, a lagging client or a forged packet) is not trusted.
		Vec3 eye = player.getEyePosition();
		Vec3 origin = payload.origin().distanceToSqr(eye) <= MAX_SHOT_ORIGIN_OFFSET * MAX_SHOT_ORIGIN_OFFSET ? payload.origin() : eye;
		float xRot = Float.isFinite(payload.xRot()) ? Mth.clamp(payload.xRot(), -90, 90) : player.getXRot();
		float yRot = Float.isFinite(payload.yRot()) ? payload.yRot() : player.getYRot();
		// The stance is the client's word, as the spread already is (the client picks the seed), so its tracer and the bullet agree.
		Vec3 direction = Ballistics.direction(xRot, yRot, payload.seed(), definition.spread(isAiming(player), payload.stance()));
		Bullets.fire(player, payload.shotId(), origin, direction, definition, gun, payload.clientTick());
		// Volume above 1 only widens the range (16 blocks per unit), so gunshots carry about 48 blocks, and silenced ones about 16.
		boolean silenced = Attachments.silenced(gun);
		playSound(player, item.sound(silenced ? "fire_silenced" : "fire"), silenced ? 1.0F : 3.0F);
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
		// GunItem.inventoryTick adds the rounds (a round-at-a-time reload's one by one as they go in), or cancels if the gun leaves the main hand.
		long now = player.level().getGameTime();
		gun.set(ModComponents.GUN_STATE, new GunState(now + definition.reloadDuration(ammo), state.lastShotTick(), now, ammo));
		showOthers(player, definition.reloadAction(ammo));
	}

	/**
	 * Cuts a round-at-a-time reload short once {@code rounds} are in: the client's count, the round its animation is pushing in, so the
	 * rounds counted match the rounds shown. Then the bolt closes, as at the end of a full one; the rounds already in stay.
	 */
	private static void stopReload(ServerPlayer player, int slot, int rounds) {
		ItemStack gun = heldGun(player, slot);
		GunDefinition definition = GunItem.definition(gun);
		GunState state = gun.getOrDefault(ModComponents.GUN_STATE, GunState.EMPTY);
		if (definition == null || !state.roundReload(definition)) {
			return;
		}
		RoundReload reload = definition.roundReload();
		int planned = reload.rounds(state.reloadStartTick(), state.reloadingUntilTick());
		int in = GunItem.ammo(gun, definition) - state.reloadFromAmmo();
		int kept = Mth.clamp(rounds, Math.max(1, in), planned);
		long until = state.reloadStartTick() + reload.ticks(kept);
		gun.set(ModComponents.GUN_STATE, new GunState(until, state.lastShotTick(), state.reloadStartTick(), state.reloadFromAmmo()));
		// GunItem.inventoryTick shows everyone else the bolt closing once that round is in; if it already is, now.
		if (kept < planned && player.level().getGameTime() > until - reload.endTicks()) {
			showOthers(player, GunItem.RELOAD_STOP);
		}
	}

	/** Steps the held gun to its next fire mode (or safe), and shows everyone else the switch. Not during a reload, like the client. */
	private static void switchFireMode(ServerPlayer player, int slot) {
		ItemStack gun = heldGun(player, slot);
		GunDefinition definition = GunItem.definition(gun);
		if (definition == null || gun.getOrDefault(ModComponents.GUN_STATE, GunState.EMPTY).reloading()) {
			return;
		}
		gun.set(ModComponents.FIRE_MODE, definition.nextFireMode(gun.get(ModComponents.FIRE_MODE)));
		showOthers(player, "fire_mode");
	}

	/**
	 * Fits an attachment into a part of the held gun, or empties the part. It must fit the gun and, outside creative, come out of the
	 * player's inventory; whatever it replaces goes back there (or onto the ground when full). Not during a reload, like the client.
	 */
	private static void attach(ServerPlayer player, AttachPayload payload) {
		ItemStack gun = heldGun(player, payload.slot());
		GunDefinition definition = GunItem.definition(gun);
		if (definition == null || gun.getOrDefault(ModComponents.GUN_STATE, GunState.EMPTY).reloading()) {
			return;
		}
		AttachmentSlot part = payload.part();
		AttachmentItem wanted = null;
		if (payload.attachment().isPresent()) {
			if (!(BuiltInRegistries.ITEM.getValue(payload.attachment().get()) instanceof AttachmentItem item) || item.slot() != part || !Attachments.fits(definition, item)) {
				return;
			}
			wanted = item;
		}
		AttachmentItem current = Attachments.in(gun, part);
		if (current == wanted) {
			return;
		}
		boolean creative = player.hasInfiniteMaterials();
		if (wanted != null && !creative && !takeOne(player, wanted)) {
			return;
		}
		int ammo = GunItem.ammo(gun, definition);
		Attachments.set(gun, part, wanted);
		// A magazine swap puts a full magazine in, as a reload does (reloads cost nothing either), and a chambered round stays. Other
		// parts leave the rounds as they are. Always written, because a fresh gun's ammo defaults to whatever its mag size is now.
		// shortcut: magazines are free like reloads; phase 3 (ammo items) should fill them from what the player carries.
		GunDefinition fitted = GunItem.definition(gun);
		if (fitted != null) {
			gun.set(ModComponents.AMMO, part == AttachmentSlot.MAGAZINE ? fitted.reloadTarget(ammo) : fitted.clampAmmo(ammo));
		}
		if (current != null && !creative) {
			player.getInventory().placeItemBackInInventory(new ItemStack(current), Prediction.SERVER_ONLY);
		}
		showOthers(player, wanted != null ? part.attachAction() : part.detachAction());
	}

	private static boolean takeOne(ServerPlayer player, AttachmentItem attachment) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(attachment)) {
				stack.shrink(1);
				return true;
			}
		}
		return false;
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
	public static void showOthers(ServerPlayer player, String action) {
		GunAnimationPayload payload = new GunAnimationPayload(player.getId(), action);
		PlayerLookup.tracking(player).forEach(other -> ServerPlayNetworking.send(other, payload));
	}

	/** Aiming slows the holder by the gun's {@code move_speed_ads_multiplier}, and holding the breath while aiming a little more. */
	private static void setAiming(ServerPlayer player, boolean aiming, boolean holdingBreath) {
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		GunDefinition definition = GunItem.definition(player.getMainHandItem());
		if (aiming && definition != null && GunItem.holdsGun(player)) {
			speed.addOrUpdateTransientModifier(new AttributeModifier(ADS_SPEED, definition.moveSpeedAdsMultiplier() - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		} else {
			speed.removeModifier(ADS_SPEED);
		}
		if (aiming && holdingBreath && speed.hasModifier(ADS_SPEED)) {
			speed.addOrUpdateTransientModifier(new AttributeModifier(BREATH_SPEED, BREATH_MOVE_SPEED_MULTIPLIER - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		} else {
			speed.removeModifier(BREATH_SPEED);
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

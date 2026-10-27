package com.noxloveya.mcweapons.network;

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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;

/** Registers the payloads and checks every client intent against the gun definition. */
public final class ServerHandlers {
	/** Present on a player's movement speed while they aim, which also makes it the per-player aiming state. */
	private static final ResourceLocation ADS_SPEED = McWeapons.id("ads_speed");

	private ServerHandlers() {
	}

	public static void register() {
		PayloadTypeRegistry.playC2S().register(ShootPayload.TYPE, ShootPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(ReloadPayload.TYPE, ReloadPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(AimPayload.TYPE, AimPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(ShotFxPayload.TYPE, ShotFxPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(GunDefinitionsPayload.TYPE, GunDefinitionsPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(ShootPayload.TYPE, (payload, context) -> shoot(context.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(ReloadPayload.TYPE, (payload, context) -> reload(context.player(), payload.slot()));
		ServerPlayNetworking.registerGlobalReceiver(AimPayload.TYPE, (payload, context) -> setAiming(context.player(), payload.aiming()));

		ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.register((player, joined) ->
				ServerPlayNetworking.send(player, new GunDefinitionsPayload(GunDefinitions.all())));
		ServerTickEvents.END_SERVER_TICK.register(server -> server.getPlayerList().getPlayers().forEach(player -> {
			if (!GunItem.holdsGun(player)) {
				setAiming(player, false);
			}
		}));
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
			playSound(player, item.sound("dry"));
			return;
		}
		gun.set(ModComponents.AMMO, ammo - 1);
		HitResult hit = Hitscan.fire(player, gun, definition, payload.seed(), isAiming(player));
		playSound(player, item.sound("fire"));
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

	/** The main-hand stack if the client named the selected slot, otherwise empty. Checking the slot first also keeps bad indexes out. */
	private static ItemStack heldGun(ServerPlayer player, int slot) {
		return slot == player.getInventory().selected && player.isAlive() && GunItem.holdsGun(player) ? player.getMainHandItem() : ItemStack.EMPTY;
	}

	private static void playSound(ServerPlayer player, SoundEvent sound) {
		// Skips the shooter, whose client already played it on the click.
		player.level().playSound(player, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, 1.0F, 1.0F);
	}
}

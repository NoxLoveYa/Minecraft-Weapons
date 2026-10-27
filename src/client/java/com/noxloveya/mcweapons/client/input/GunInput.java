package com.noxloveya.mcweapons.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import com.noxloveya.mcweapons.McWeapons;
import com.noxloveya.mcweapons.client.render.GunRenderer;
import com.noxloveya.mcweapons.client.render.Viewmodel;
import com.noxloveya.mcweapons.client.screen.GunTunerScreen;
import com.noxloveya.mcweapons.gun.GunDefinition;
import com.noxloveya.mcweapons.gun.GunItem;
import com.noxloveya.mcweapons.gun.GunState;
import com.noxloveya.mcweapons.network.AimPayload;
import com.noxloveya.mcweapons.network.GunAnimationPayload;
import com.noxloveya.mcweapons.network.ReloadPayload;
import com.noxloveya.mcweapons.network.ShootPayload;
import com.noxloveya.mcweapons.registry.ModComponents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.List;

/** Turns attack, use and the mod keys into gun intents while a gun is in the main hand, and predicts their animations. */
public final class GunInput {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(McWeapons.id("main"));
	public static final KeyMapping RELOAD = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.mcweapons.reload", InputConstants.KEY_R, CATEGORY));
	public static final KeyMapping INSPECT = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.mcweapons.inspect", InputConstants.KEY_V, CATEGORY));
	/** Opens the gun tuner for ops. The server checks the permission again on every change. */
	public static final KeyMapping TUNER = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.mcweapons.tuner", InputConstants.KEY_F7, CATEGORY));
	/** Holding reload this long (0.4 s) checks the mag instead of reloading. */
	private static final int MAG_CHECK_HOLD_TICKS = 8;

	private static boolean aiming;
	/** Client game time, in fractional ticks, when the next shot is due (see GunDefinition.nextShotDue). */
	private static double nextShotDue;
	/** Predicted end of the reload we asked for, until the server's gun_state arrives. */
	private static long predictedReloadUntil;
	/** After firing the last round, the slide locks back before the server's empty ammo count arrives. */
	private static long predictedEmptyUntil;
	private static String lastInspect = "";
	/** Ticks the reload key has been held, or -1 when no press is being timed. */
	private static int reloadHeldTicks = -1;
	private static int drawnSlot = -1;
	private static @Nullable Item drawnItem;

	private GunInput() {
	}

	public static void register() {
		ClientTickEvents.START_CLIENT_TICK.register(GunInput::tick);
	}

	public static boolean aiming() {
		return aiming;
	}

	/** True while the held gun is reloading, by the server's state or by our own prediction. */
	public static boolean reloading(LocalPlayer player, ItemStack stack) {
		return stack.getOrDefault(ModComponents.GUN_STATE, GunState.EMPTY).reloading() || player.level().getGameTime() < predictedReloadUntil;
	}

	/** An empty gun that is not reloading has its slide locked back. */
	public static boolean slideLocked(LocalPlayer player, ItemStack stack, GunDefinition definition) {
		return !reloading(player, stack) && (GunItem.ammo(stack, definition) == 0 || player.level().getGameTime() < predictedEmptyUntil);
	}

	/** Game time the current reload ends, preferring the server's timer over our prediction. */
	public static long reloadEnd(ItemStack stack) {
		GunState state = stack.getOrDefault(ModComponents.GUN_STATE, GunState.EMPTY);
		return state.reloading() ? state.reloadingUntilTick() : predictedReloadUntil;
	}

	// Runs before vanilla reads the keys, so consuming the clicks here stops vanilla attacking or using with a gun in hand.
	// Held-key repeats that vanilla still reads are blocked by the callbacks in GunItem.registerEvents.
	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		boolean holding = player != null && GunItem.holdsGun(player);

		boolean aim = holding && minecraft.options.keyUse.isDown();
		if (aim != aiming && player != null) {
			ClientPlayNetworking.send(new AimPayload(aim));
		}
		aiming = aim;

		if (!holding) {
			while (RELOAD.consumeClick()) {
			}
			while (INSPECT.consumeClick()) {
			}
			while (TUNER.consumeClick()) {
			}
			reloadHeldTicks = -1;
			drawnSlot = -1;
			drawnItem = null;
			predictedReloadUntil = 0;
			Viewmodel.reset();
			return;
		}

		ItemStack stack = player.getMainHandItem();
		GunItem gun = (GunItem)stack.getItem();
		GunDefinition definition = GunItem.definition(stack);
		int slot = player.getInventory().getSelectedSlot();
		if (slot != drawnSlot || gun != drawnItem) {
			drawnSlot = slot;
			drawnItem = gun;
			predictedReloadUntil = 0;
			reloadHeldTicks = -1;
			Viewmodel.reset();
			play(player, gun, "draw");
		}
		if (definition != null) {
			Viewmodel.tick(player, definition, aim, minecraft.gui.screen() instanceof GunTunerScreen);
		}

		// A tap reloads when the key is let go; holding it checks the mag instead, empty or not, so ammo can be read without the HUD.
		while (RELOAD.consumeClick()) {
			reloadHeldTicks = 0;
		}
		if (reloadHeldTicks >= 0) {
			if (!RELOAD.isDown()) {
				if (reloadHeldTicks < MAG_CHECK_HOLD_TICKS) {
					reload(player, stack, gun, definition);
				}
				reloadHeldTicks = -1;
			} else if (++reloadHeldTicks == MAG_CHECK_HOLD_TICKS && canInspect(player, stack, definition)) {
				inspect(player, gun, "inspect_mag_check");
			}
		}
		while (INSPECT.consumeClick()) {
			if (canInspect(player, stack, definition)) {
				inspect(player, gun, nextInspect(player, slideLocked(player, stack, definition)));
			}
		}
		while (TUNER.consumeClick()) {
			if (definition != null && player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
				minecraft.gui.setScreen(new GunTunerScreen(gun.definitionId(), definition, stack.getHoverName()));
			}
		}
		while (minecraft.options.keyAttack.consumeClick()) {
			shoot(player, stack, gun, definition);
		}
		if (definition != null && definition.fireModes().contains("auto") && minecraft.options.keyAttack.isDown()) {
			shoot(player, stack, gun, definition);
		}
		while (minecraft.options.keyUse.consumeClick()) {
			// Aiming reads keyUse.isDown() above, the clicks only need to be kept from vanilla.
		}
		while (minecraft.options.keySwapOffhand.consumeClick()) {
			// Guns stay in the main hand; the server also moves any that reach the offhand another way.
		}
	}

	private static void shoot(LocalPlayer player, ItemStack stack, GunItem gun, @Nullable GunDefinition definition) {
		long now = player.level().getGameTime();
		if (definition == null || reloading(player, stack) || !definition.shotDue(nextShotDue, now)) {
			return;
		}
		nextShotDue = definition.nextShotDue(nextShotDue, now);
		int ammo = GunItem.ammo(stack, definition);
		player.playSound(gun.sound(ammo > 0 ? "fire" : "dry"), 1.0F, 1.0F);
		if (ammo > 0) {
			play(player, gun, "fire");
			Viewmodel.onShot(player, definition);
			if (ammo == 1) {
				// Covers the round trip until the server says the gun is empty.
				predictedEmptyUntil = now + 10;
			}
		}
		ClientPlayNetworking.send(new ShootPayload(player.getInventory().getSelectedSlot(), now, player.getRandom().nextLong()));
	}

	/** Same checks as the server, so the animation only starts for a reload the server will accept. */
	private static void reload(LocalPlayer player, ItemStack stack, GunItem gun, @Nullable GunDefinition definition) {
		if (definition == null || reloading(player, stack)) {
			return;
		}
		int ammo = GunItem.ammo(stack, definition);
		if (ammo >= definition.reloadTarget(ammo)) {
			return;
		}
		predictedReloadUntil = player.level().getGameTime() + (ammo == 0 ? definition.reloadEmptyTicks() : definition.reloadTicks());
		predictedEmptyUntil = 0;
		play(player, gun, ammo == 0 ? "reload_empty" : "reload");
		ClientPlayNetworking.send(new ReloadPayload(player.getInventory().getSelectedSlot()));
	}

	/** Inspects are cosmetic, so they wait for reloads and shots to finish. */
	private static boolean canInspect(LocalPlayer player, ItemStack stack, @Nullable GunDefinition definition) {
		return definition != null && !reloading(player, stack) && definition.shotDue(nextShotDue, player.level().getGameTime());
	}

	/** A random inspect variant, never the same one twice in a row. A slide locked back on an empty gun cannot be press checked. */
	private static String nextInspect(LocalPlayer player, boolean slideLocked) {
		List<String> options = slideLocked ? GunItem.INSPECT_ACTIONS.stream().filter(action -> !action.equals("inspect_press_check")).toList()
				: GunItem.INSPECT_ACTIONS;
		int last = options.indexOf(lastInspect);
		int pick = player.getRandom().nextInt(last < 0 ? options.size() : options.size() - 1);
		if (last >= 0 && pick >= last) {
			pick++;
		}
		lastInspect = options.get(pick);
		return lastInspect;
	}

	/** Inspects are picked here, so the server is told which one to show everyone else. */
	private static void inspect(LocalPlayer player, GunItem gun, String action) {
		play(player, gun, action);
		ClientPlayNetworking.send(new GunAnimationPayload(player.getId(), action));
	}

	private static void play(LocalPlayer player, GunItem gun, String action) {
		gun.getAnimatableInstanceCache().getManagerForId(GunRenderer.instanceId(player)).tryTriggerAnimation(GunItem.MAIN_CONTROLLER, action);
	}
}

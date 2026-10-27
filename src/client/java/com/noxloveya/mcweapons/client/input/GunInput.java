package com.noxloveya.mcweapons.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import com.noxloveya.mcweapons.gun.GunDefinition;
import com.noxloveya.mcweapons.gun.GunItem;
import com.noxloveya.mcweapons.gun.GunState;
import com.noxloveya.mcweapons.network.AimPayload;
import com.noxloveya.mcweapons.network.ReloadPayload;
import com.noxloveya.mcweapons.network.ShootPayload;
import com.noxloveya.mcweapons.registry.ModComponents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/** Turns attack, use and the mod keys into gun intents while a gun is in the main hand. */
public final class GunInput {
	private static final String CATEGORY = "key.categories.mcweapons";
	public static final KeyMapping RELOAD = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.mcweapons.reload", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY));
	public static final KeyMapping INSPECT = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.mcweapons.inspect", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY));

	private static boolean aiming;
	/** Client game time of the last predicted shot, for the local RPM gate. */
	private static long lastShotTick;

	private GunInput() {
	}

	public static void register() {
		ClientTickEvents.START_CLIENT_TICK.register(GunInput::tick);
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

		while (RELOAD.consumeClick()) {
			if (holding) {
				ClientPlayNetworking.send(new ReloadPayload(player.getInventory().selected));
			}
		}
		while (INSPECT.consumeClick()) {
			// 01c plays the inspect animation here.
		}
		if (!holding) {
			return;
		}

		ItemStack stack = player.getMainHandItem();
		GunDefinition definition = GunItem.definition(stack);
		while (minecraft.options.keyAttack.consumeClick()) {
			shoot(player, stack, definition);
		}
		if (definition != null && definition.fireModes().contains("auto") && minecraft.options.keyAttack.isDown()) {
			shoot(player, stack, definition);
		}
		while (minecraft.options.keyUse.consumeClick()) {
			// Aiming reads keyUse.isDown() above, the clicks only need to be kept from vanilla.
		}
	}

	private static void shoot(LocalPlayer player, ItemStack stack, GunDefinition definition) {
		long now = player.level().getGameTime();
		if (definition == null || stack.getOrDefault(ModComponents.GUN_STATE, GunState.EMPTY).reloading() || !definition.readyToFire(lastShotTick, now, 0)) {
			return;
		}
		lastShotTick = now;
		GunItem gun = (GunItem) stack.getItem();
		player.playSound(gun.sound(GunItem.ammo(stack, definition) > 0 ? "fire" : "dry"), 1.0F, 1.0F);
		ClientPlayNetworking.send(new ShootPayload(player.getInventory().selected, now, player.getRandom().nextLong()));
	}
}

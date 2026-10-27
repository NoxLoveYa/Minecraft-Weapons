package com.noxloveya.mcweapons.gun;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** The one place reloads take rounds from. Phase 3 makes this use ammo items from the inventory. */
public final class AmmoSource {
	private AmmoSource() {
	}

	/** Returns how many of {@code wanted} rounds were taken. Phase 1 reloads are free. */
	public static int take(Player player, ItemStack gun, int wanted) {
		return wanted;
	}
}

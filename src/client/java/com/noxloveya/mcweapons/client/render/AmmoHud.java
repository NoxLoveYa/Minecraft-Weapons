package com.noxloveya.mcweapons.client.render;

import com.noxloveya.mcweapons.gun.GunDefinition;
import com.noxloveya.mcweapons.gun.GunItem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** "17 / 17" in the bottom right while a gun is held. */
public final class AmmoHud {
	private AmmoHud() {
	}

	public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || !GunItem.holdsGun(minecraft.player)) {
			return;
		}
		ItemStack stack = minecraft.player.getMainHandItem();
		GunDefinition definition = GunItem.definition(stack);
		if (definition == null) {
			return;
		}
		Component text = Component.translatable("gui.mcweapons.ammo", GunItem.ammo(stack, definition), definition.magSize());
		// Text colors are ARGB now, so the alpha byte must be set or the text is invisible.
		graphics.text(minecraft.font, text, graphics.guiWidth() - minecraft.font.width(text) - 8, graphics.guiHeight() - minecraft.font.lineHeight - 8, 0xFFFFFFFF);
	}
}

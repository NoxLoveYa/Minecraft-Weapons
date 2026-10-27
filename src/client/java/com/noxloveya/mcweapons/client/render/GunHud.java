package com.noxloveya.mcweapons.client.render;

import com.noxloveya.mcweapons.client.input.GunInput;
import com.noxloveya.mcweapons.gun.GunDefinition;
import com.noxloveya.mcweapons.gun.GunItem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/** Ammo panel in the bottom right, and a spread crosshair that fades out while aiming. */
public final class GunHud {
	private static final int MARGIN = 8;
	private static final int PANEL = 0x99000000;
	private static final int WHITE = 0xFFFFFFFF;
	private static final int GRAY = 0xFFA8A8A8;
	private static final int DIM = 0xFF707070;
	private static final int AMBER = 0xFFFFB648;
	private static final int RED = 0xFFFF5555;

	private GunHud() {
	}

	public static void renderAmmo(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		LocalPlayer player = Minecraft.getInstance().player;
		GunDefinition definition = heldDefinition(player);
		if (definition == null) {
			return;
		}
		Font font = Minecraft.getInstance().font;
		ItemStack stack = player.getMainHandItem();
		int ammo = GunItem.ammo(stack, definition);
		int ammoColor = ammo == 0 ? RED : ammo <= definition.magSize() / 4 ? AMBER : WHITE;
		String name = stack.getHoverName().getString().toUpperCase(Locale.ROOT);
		String mode = definition.fireModes().isEmpty() ? "" : definition.fireModes().getFirst().toUpperCase(Locale.ROOT);
		String count = String.valueOf(ammo);
		String total = "/ " + definition.magSize();

		int countWidth = font.width(count) * 2;
		int width = Math.max(font.width(name) + 8 + font.width(mode), countWidth + 4 + font.width(total)) + 14;
		int right = graphics.guiWidth() - MARGIN;
		int bottom = graphics.guiHeight() - MARGIN;
		int left = right - width;
		int top = bottom - 38;

		graphics.fill(left, top, right, bottom, PANEL);
		graphics.fill(left, top, left + 2, bottom, ammoColor);
		graphics.text(font, name, left + 8, top + 6, GRAY, false);
		graphics.text(font, mode, right - 6 - font.width(mode), top + 6, DIM, false);
		graphics.pose().pushMatrix();
		graphics.pose().translate(left + 8, top + 17);
		graphics.pose().scale(2, 2);
		graphics.text(font, count, 0, 0, ammoColor, true);
		graphics.pose().popMatrix();
		graphics.text(font, total, left + 8 + countWidth + 4, top + 25, GRAY, true);

		if (GunInput.reloading(player, stack)) {
			int reloadTicks = ammo == 0 ? definition.reloadEmptyTicks() : definition.reloadTicks();
			float now = player.level().getGameTime() + deltaTracker.getGameTimeDeltaPartialTick(false);
			float progress = Mth.clamp(1 - (GunInput.reloadEnd(stack) - now) / Math.max(1, reloadTicks), 0, 1);
			graphics.fill(left, bottom - 2, right, bottom, 0x40FFFFFF);
			graphics.fill(left, bottom - 2, left + Math.round(width * progress), bottom, WHITE);
		} else if (ammo == 0 && System.currentTimeMillis() / 400 % 2 == 0) {
			String hint = "RELOAD [" + GunInput.RELOAD.getTranslatedKeyMessage().getString().toUpperCase(Locale.ROOT) + "]";
			graphics.text(font, hint, right - font.width(hint), top - 11, RED, true);
		}
	}

	/** Draws the gun crosshair and returns true, or returns false so vanilla draws its own. Hidden while aiming. */
	public static boolean renderCrosshair(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		GunDefinition definition = heldDefinition(minecraft.player);
		if (definition == null || !minecraft.options.getCameraType().isFirstPerson()) {
			return false;
		}
		float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
		float alpha = 1 - Viewmodel.ads(partialTick) * 2.5F;
		if (alpha <= 0) {
			return true;
		}
		// Gap matches the hip spread cone at the current FOV, plus a little bloom while the recoil settles.
		float fov = minecraft.gameRenderer.mainCamera().getFov();
		float pixelsPerUnit = graphics.guiHeight() / 2.0F / (float)Math.tan(Math.toRadians(fov / 2));
		int gap = 2 + Math.round((float)Math.tan(Math.toRadians(definition.spreadHipDeg())) * pixelsPerUnit + Math.max(0, Viewmodel.recoil(partialTick)) * 3);
		int x = graphics.guiWidth() / 2;
		int y = graphics.guiHeight() / 2;
		int color = ARGB.color(Math.round(alpha * 230), 255, 255, 255);
		int shadow = ARGB.color(Math.round(alpha * 120), 0, 0, 0);
		bar(graphics, x - gap - 4, y, x - gap, y + 1, color, shadow);
		bar(graphics, x + 1 + gap, y, x + 1 + gap + 4, y + 1, color, shadow);
		bar(graphics, x, y - gap - 4, x + 1, y - gap, color, shadow);
		bar(graphics, x, y + 1 + gap, x + 1, y + 1 + gap + 4, color, shadow);
		return true;
	}

	private static void bar(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int color, int shadow) {
		graphics.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, shadow);
		graphics.fill(x0, y0, x1, y1, color);
	}

	private static @Nullable GunDefinition heldDefinition(@Nullable LocalPlayer player) {
		return player != null && GunItem.holdsGun(player) ? GunItem.definition(player.getMainHandItem()) : null;
	}
}

package com.noxloveya.mcweapons.client.render;

import com.noxloveya.mcweapons.client.input.GunInput;
import com.noxloveya.mcweapons.gun.GunDefinition;
import com.noxloveya.mcweapons.gun.GunItem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
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
	private static final int HIT_MARKER = 0xFFFFFF;
	private static final int HEADSHOT_MARKER = 0xFFC83A;
	private static final int KILL_MARKER = 0xFF3B3B;
	private static final int HIT_MARKER_GAP = 4;
	private static final long HIT_MARKER_MILLIS = 260;
	private static final long KILL_MARKER_MILLIS = 480;

	private static long hitmarkerStart;
	private static long hitmarkerEnd;
	private static int hitmarkerColor;
	private static int hitmarkerLength;

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
		renderHitmarker(graphics);
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

	/** One of our shots landed (the server says so): a white X for a hit, gold in the head, red and longer for the kill, with a tick. */
	public static void hitmarker(boolean headshot, boolean kill) {
		long now = Util.getMillis();
		hitmarkerStart = now;
		hitmarkerEnd = now + (kill ? KILL_MARKER_MILLIS : HIT_MARKER_MILLIS);
		hitmarkerColor = kill ? KILL_MARKER : headshot ? HEADSHOT_MARKER : HIT_MARKER;
		hitmarkerLength = kill || headshot ? 6 : 4;
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null) {
			player.playSound(SoundEvents.ARROW_HIT_PLAYER, kill ? 0.5F : 0.3F, kill ? 1.0F : headshot ? 1.9F : 1.5F);
		}
	}

	/** Four diagonal strokes around the crosshair that pop in a little big, settle, and fade. Shown while aiming too. */
	private static void renderHitmarker(GuiGraphicsExtractor graphics) {
		long now = Util.getMillis();
		if (now >= hitmarkerEnd) {
			return;
		}
		float age = (now - hitmarkerStart) / (float)(hitmarkerEnd - hitmarkerStart);
		float scale = 1 + 0.5F * Math.max(0, 1 - age * 4);
		int alpha = Math.round(255 * (1 - age * age));
		graphics.pose().pushMatrix();
		graphics.pose().translate(graphics.guiWidth() / 2 + 0.5F, graphics.guiHeight() / 2 + 0.5F);
		graphics.pose().scale(scale, scale);
		for (int stroke = 0; stroke < 4; stroke++) {
			graphics.pose().pushMatrix();
			graphics.pose().rotate((45 + 90 * stroke) * Mth.DEG_TO_RAD);
			graphics.pose().translate(0, -0.5F);
			graphics.fill(HIT_MARKER_GAP - 1, -1, HIT_MARKER_GAP + hitmarkerLength + 1, 2, ARGB.color(alpha / 2, 0));
			graphics.fill(HIT_MARKER_GAP, 0, HIT_MARKER_GAP + hitmarkerLength, 1, ARGB.color(alpha, hitmarkerColor));
			graphics.pose().popMatrix();
		}
		graphics.pose().popMatrix();
	}

	private static void bar(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int color, int shadow) {
		graphics.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, shadow);
		graphics.fill(x0, y0, x1, y1, color);
	}

	private static @Nullable GunDefinition heldDefinition(@Nullable LocalPlayer player) {
		return player != null && GunItem.holdsGun(player) ? GunItem.definition(player.getMainHandItem()) : null;
	}
}

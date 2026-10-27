package com.noxloveya.mcweapons.client.render;

import com.noxloveya.mcweapons.McWeapons;
import com.noxloveya.mcweapons.client.input.GunInput;
import com.noxloveya.mcweapons.gun.GunDefinition;
import com.noxloveya.mcweapons.gun.GunItem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/** Ammo panel in the bottom right, and a spread crosshair that fades out while aiming. */
public final class GunHud {
	private static final int MARGIN = 8;
	private static final int WHITE = 0xFFFFFFFF;
	private static final int GRAY = 0xFFA8A8A8;
	private static final int DIM = 0xFF707070;
	private static final int AMBER = 0xFFFFB648;
	private static final int RED = 0xFFFF5555;
	private static final int BULLET_WIDTH = 4;
	private static final int HIT_MARKER = 0xFFFFFF;
	private static final int HEADSHOT_MARKER = 0xFFC83A;
	private static final int KILL_MARKER = 0xFF3B3B;
	private static final int HIT_MARKER_GAP = 4;
	private static final long HIT_MARKER_MILLIS = 260;
	private static final long KILL_MARKER_MILLIS = 480;

	private static long safeBlockedUntil;
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
		// The count is what is in the mag; a chambered round (any round at all, on a gun with a chamber) is the bullet icon beside it.
		boolean chambered = definition.chamber() && ammo > 0;
		int inMag = ammo - (chambered ? 1 : 0);
		int color = ammo == 0 ? RED : inMag <= definition.magSize() / 4 ? AMBER : WHITE;
		String name = stack.getHoverName().getString().toUpperCase(Locale.ROOT);
		String fireMode = GunInput.fireMode(player, stack, definition);
		String mode = fireMode.toUpperCase(Locale.ROOT);
		boolean safe = GunDefinition.SAFE.equals(fireMode);
		// Amber on safe, and red for a moment when the trigger is pulled on safe.
		int modeColor = !safe ? GRAY : Util.getMillis() < safeBlockedUntil ? RED : AMBER;
		String count = String.valueOf(inMag);
		String total = "/" + definition.magSize();

		// Right-aligned rows, bottom up: progress line, round ticks, count, then name and fire mode.
		int right = graphics.guiWidth() - MARGIN;
		int bottom = graphics.guiHeight() - MARGIN;
		int countWidth = font.width(count) * 2;
		int totalWidth = font.width(total);
		int ticksWidth = definition.magSize() * 3 - 1;
		int nameRow = font.width(name) + 6 + font.width(mode) + 6;
		int width = Math.max(Math.max(countWidth + 3 + BULLET_WIDTH + 3 + totalWidth, ticksWidth), nameRow);
		int left = right - width;
		int top = bottom - 46;

		// A backdrop that fades in from the left instead of a hard box.
		backdrop(graphics, left - 28, top - 4, right + MARGIN, bottom + MARGIN);

		int nameY = top;
		graphics.text(font, mode, right - 3 - font.width(mode), nameY + 2, modeColor, false);
		graphics.outline(right - 6 - font.width(mode), nameY, font.width(mode) + 6, 12, safe ? modeColor : DIM);
		graphics.text(font, name, right - nameRow, nameY + 2, GRAY, true);

		int countY = top + 15;
		int totalX = right - totalWidth;
		graphics.text(font, total, totalX, countY + 9, GRAY, true);
		bullet(graphics, totalX - 3 - BULLET_WIDTH, countY + 6, chambered);
		graphics.pose().pushMatrix();
		graphics.pose().translate(totalX - 6 - BULLET_WIDTH - countWidth, countY);
		graphics.pose().scale(2, 2);
		graphics.text(font, count, 0, 0, color, true);
		graphics.pose().popMatrix();

		// One tick per round the mag holds, lit for the ones still in it.
		int ticksY = bottom - 9;
		for (int round = 0; round < definition.magSize(); round++) {
			int x = right - ticksWidth + round * 3;
			graphics.fill(x, ticksY, x + 2, ticksY + 5, round < inMag ? ARGB.color(230, color) : 0x40FFFFFF);
		}

		graphics.fill(right - width, bottom - 1, right, bottom, 0x30FFFFFF);
		if (GunInput.reloading(player, stack)) {
			int reloadTicks = ammo == 0 ? definition.reloadEmptyTicks() : definition.reloadTicks();
			float now = player.level().getGameTime() + deltaTracker.getGameTimeDeltaPartialTick(false);
			float progress = Mth.clamp(1 - (GunInput.reloadEnd(stack) - now) / Math.max(1, reloadTicks), 0, 1);
			graphics.fill(right - width, bottom - 1, right - width + Math.round(width * progress), bottom, AMBER);
		} else if (ammo == 0 && System.currentTimeMillis() / 400 % 2 == 0) {
			String hint = "RELOAD [" + GunInput.RELOAD.getTranslatedKeyMessage().getString().toUpperCase(Locale.ROOT) + "]";
			graphics.text(font, hint, right - font.width(hint), top - 14, RED, true);
		}
	}

	/** Dark on the right fading to nothing on the left, in thin vertical slices. */
	private static void backdrop(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1) {
		int slices = 24;
		for (int i = 0; i < slices; i++) {
			int a = x0 + (x1 - x0) * i / slices;
			int b = x0 + (x1 - x0) * (i + 1) / slices;
			float t = (i + 1) / (float)slices;
			graphics.fillGradient(a, y0, b, y1, ARGB.color(Math.round(90 * t * t), 0), ARGB.color(Math.round(150 * t * t), 0));
		}
	}

	/** The chambered round: a small upright cartridge, or just its outline when the chamber is empty. */
	private static void bullet(GuiGraphicsExtractor graphics, int x, int y, boolean chambered) {
		if (!chambered) {
			graphics.outline(x, y, BULLET_WIDTH, 10, 0x60FFFFFF);
			return;
		}
		graphics.fill(x - 1, y - 1, x + BULLET_WIDTH + 1, y + 11, 0x80000000);
		graphics.fill(x + 1, y, x + BULLET_WIDTH - 1, y + 1, 0xFFE09A5A);
		graphics.fill(x, y + 1, x + BULLET_WIDTH, y + 3, 0xFFC8743A);
		graphics.fill(x, y + 3, x + BULLET_WIDTH, y + 9, 0xFFE2B248);
		graphics.fill(x + 1, y + 4, x + 2, y + 8, 0xFFF6D98A);
		graphics.fill(x, y + 9, x + BULLET_WIDTH, y + 10, 0xFFA07A2A);
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

	/** The trigger was pulled on safe: the SAFE tag flashes red. */
	public static void safeBlocked() {
		safeBlockedUntil = Util.getMillis() + 400;
	}

	/** One of our shots landed (the server says so): a white X for a hit, gold in the head, red and longer for the kill, each with its sound. */
	public static void hitmarker(boolean headshot, boolean kill) {
		long now = Util.getMillis();
		hitmarkerStart = now;
		hitmarkerEnd = now + (kill ? KILL_MARKER_MILLIS : HIT_MARKER_MILLIS);
		hitmarkerColor = kill ? KILL_MARKER : headshot ? HEADSHOT_MARKER : HIT_MARKER;
		hitmarkerLength = kill || headshot ? 6 : 4;
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null) {
			player.playSound(SoundEvent.createVariableRangeEvent(McWeapons.id(headshot && !kill ? "headshot" : "hitmarker")), kill ? 0.5F : 0.35F, kill ? 0.8F : 1.0F);
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

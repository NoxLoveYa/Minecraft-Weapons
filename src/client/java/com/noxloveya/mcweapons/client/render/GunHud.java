package com.noxloveya.mcweapons.client.render;

import com.noxloveya.mcweapons.McWeapons;
import com.noxloveya.mcweapons.client.input.GunInput;
import com.noxloveya.mcweapons.gun.AttachmentItem;
import com.noxloveya.mcweapons.gun.AttachmentSlot;
import com.noxloveya.mcweapons.gun.Attachments;
import com.noxloveya.mcweapons.gun.GunDefinition;
import com.noxloveya.mcweapons.gun.GunItem;
import com.noxloveya.mcweapons.gun.Reticle;
import com.noxloveya.mcweapons.registry.ModComponents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/** Ammo panel in the bottom right, a spread crosshair that fades out while aiming, and the held breath running low. */
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
	/** Darkens the screen's border (black at the corners) with the vignette pipeline; white is fully dark. */
	private static final Identifier BREATH_VIGNETTE = McWeapons.id("textures/gui/breath_vignette.png");
	/** Below this much breath left, the vignette comes in. */
	private static final float BREATH_LOW = 0.4F;

	private static long safeBlockedUntil;
	private static long hitmarkerStart;
	private static long hitmarkerEnd;
	private static int hitmarkerColor;
	private static int hitmarkerLength;

	private GunHud() {
	}

	/**
	 * As a held breath runs low the screen's border darkens in from the edges: the vignette is drawn oversized, so its dark rim is off
	 * screen, and closes in to the screen's edges as the breath runs out. It fades as the breath comes back.
	 */
	public static void renderBreath(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		float low = Mth.clamp(1 - GunInput.breath(deltaTracker.getGameTimeDeltaPartialTick(false)) / BREATH_LOW, 0, 1);
		if (low <= 0) {
			return;
		}
		low = low * low * (3 - 2 * low);
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		int x = Math.round(width * 0.4F * (1 - low));
		int y = Math.round(height * 0.4F * (1 - low));
		graphics.blit(RenderPipelines.VIGNETTE, BREATH_VIGNETTE, -x, -y, 0, 0, width + 2 * x, height + 2 * y, width + 2 * x, height + 2 * y,
				ARGB.colorFromFloat(1, low, low, low));
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
		// The count is what is in the mag; a chambered round (there is one whenever the gun has any) is the bullet icon beside it.
		boolean chambered = ammo > 0;
		int inMag = ammo - (chambered ? 1 : 0);
		int color = ammo == 0 ? RED : inMag <= definition.magSize() / 4 ? AMBER : WHITE;
		String name = stack.getHoverName().getString().toUpperCase(Locale.ROOT);
		AttachmentItem optic = Attachments.in(stack, AttachmentSlot.OPTIC);
		if (optic != null && optic.maxZoom() > optic.minZoom()) {
			// A variable scope's power, as the scroll wheel set it.
			name += "  " + Math.round(GunInput.zoom(optic)) + "X";
		}
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
		// Lasers and flashlights: a tag each above the name, its lamp lit while on, the one the switch key acts on outlined.
		List<AttachmentItem> devices = Attachments.switchable(stack);
		int devicesRow = -3;
		for (AttachmentItem device : devices) {
			devicesRow += font.width(deviceLabel(device)) + 13 + 3;
		}
		int width = Math.max(Math.max(Math.max(countWidth + 3 + BULLET_WIDTH + 3 + totalWidth, ticksWidth), nameRow), devicesRow);
		int left = right - width;
		int top = bottom - 46;
		int panelTop = devices.isEmpty() ? top : top - 15;

		// A backdrop that fades in from the left instead of a hard box.
		backdrop(graphics, left - 28, panelTop - 4, right + MARGIN, bottom + MARGIN);

		AttachmentItem selected = GunInput.switchSelected(stack);
		int deviceX = right;
		for (int i = devices.size() - 1; i >= 0; i--) {
			AttachmentItem device = devices.get(i);
			String label = deviceLabel(device);
			boolean on = Attachments.switchedOn(stack, device);
			int tag = font.width(label) + 13;
			deviceX -= tag;
			if (on) {
				int lamp = device.isLaser() ? stack.getOrDefault(ModComponents.LASER_COLOR, Reticle.COLORS.getFirst()) : 0xFFF2DA;
				graphics.fill(deviceX + 3, panelTop + 4, deviceX + 7, panelTop + 8, ARGB.opaque(lamp));
			} else {
				graphics.outline(deviceX + 3, panelTop + 4, 4, 4, DIM);
			}
			graphics.text(font, label, deviceX + 10, panelTop + 2, on ? WHITE : DIM, false);
			graphics.outline(deviceX, panelTop, tag, 12, devices.size() > 1 && device == selected ? WHITE : DIM);
			deviceX -= 3;
		}

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
			// By the reload's start: a round-at-a-time reload's count climbs as it goes, so its length cannot come from the count any more.
			long end = GunInput.reloadEnd(stack);
			long start = GunInput.reloadStart(stack);
			int reloadTicks = start > 0 ? (int)(end - start) : definition.reloadDuration(ammo);
			float now = player.level().getGameTime() + deltaTracker.getGameTimeDeltaPartialTick(false);
			float progress = Mth.clamp(1 - (end - now) / Math.max(1, reloadTicks), 0, 1);
			graphics.fill(right - width, bottom - 1, right - width + Math.round(width * progress), bottom, AMBER);
		} else if (ammo == 0 && System.currentTimeMillis() / 400 % 2 == 0) {
			String hint = "RELOAD [" + GunInput.RELOAD.getTranslatedKeyMessage().getString().toUpperCase(Locale.ROOT) + "]";
			graphics.text(font, hint, right - font.width(hint), panelTop - 14, RED, true);
		}
	}

	private static String deviceLabel(AttachmentItem device) {
		return device.isLaser() ? "LASER" : "LIGHT";
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

	/**
	 * With a gun in first person there is no crosshair (vanilla's neither), as shots go where the barrel points, not the screen center
	 * (see {@link Viewmodel#barrelLine}); only the hitmarker. Returns false to let vanilla draw otherwise.
	 */
	public static boolean renderCrosshair(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		if (heldDefinition(minecraft.player) == null || !minecraft.options.getCameraType().isFirstPerson()) {
			return false;
		}
		renderHitmarker(graphics);
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

	private static @Nullable GunDefinition heldDefinition(@Nullable LocalPlayer player) {
		return player != null && GunItem.holdsGun(player) ? GunItem.definition(player.getMainHandItem()) : null;
	}
}

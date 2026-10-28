package com.noxloveya.mcweapons.client.render;

import com.noxloveya.mcweapons.McWeapons;
import com.noxloveya.mcweapons.client.input.GunInput;
import com.noxloveya.mcweapons.gun.AttachmentItem;
import com.noxloveya.mcweapons.gun.AttachmentSlot;
import com.noxloveya.mcweapons.gun.Attachments;
import com.noxloveya.mcweapons.gun.Reticle;
import com.noxloveya.mcweapons.registry.ModComponents;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemStack;

/**
 * The view through a fitted scope once it is fully aimed (see {@link Viewmodel#scopeView}): the lens with black around it, the scope's
 * own etched reticle ({@code textures/gui/scope/<item>.png}, black lines), and the gun's {@link Reticle} lit in the middle. The world
 * behind it is zoomed by the camera.
 */
public final class ScopeView {
	private static final Identifier LENS = McWeapons.id("textures/gui/scope/lens.png");
	/** The lens is this much of the screen's height across. */
	private static final float LENS_SIZE = 0.94F;
	/** The lit reticle at size 1, as a share of the screen's height (its texture includes the halo). */
	private static final float RETICLE_SIZE = 0.14F;

	private ScopeView() {
	}

	public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		float view = Viewmodel.scopeView(deltaTracker.getGameTimeDeltaPartialTick(false));
		if (player == null || view <= 0 || !minecraft.options.getCameraType().isFirstPerson()) {
			return;
		}
		ItemStack stack = player.getMainHandItem();
		AttachmentItem optic = Attachments.in(stack, AttachmentSlot.OPTIC);
		if (optic == null || !optic.isScope()) {
			return;
		}
		int alpha = Math.round(255 * view);
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		int size = Math.round(height * LENS_SIZE) & ~1;
		int half = size / 2;
		// The scope is on the gun, so it points where the barrel is drawn pointing, as the shots do (see Viewmodel.barrelXRot): sway,
		// breathing and the recoil kick move the picture. The hand is drawn at a fixed FOV, which sets how far an angle is on screen.
		float pixelsPerTan = height / 2.0F / (float)Math.tan(Math.toRadians(Camera.BASE_HUD_FOV / 2));
		float x = width / 2.0F - (float)Math.tan(Math.toRadians(Viewmodel.barrelLeft())) * pixelsPerTan;
		float y = height / 2.0F - (float)Math.tan(Math.toRadians(Viewmodel.barrelUp())) * pixelsPerTan;
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		int black = ARGB.color(alpha, 0);
		graphics.fill(-2 * width, -2 * height, 2 * width, -half, black);
		graphics.fill(-2 * width, half, 2 * width, 2 * height, black);
		graphics.fill(-2 * width, -half, -half, half, black);
		graphics.fill(half, -half, 2 * width, half, black);
		graphics.blit(RenderPipelines.GUI_TEXTURED, LENS, -half, -half, 0, 0, size, size, size, size, ARGB.white(alpha));
		Identifier etched = BuiltInRegistries.ITEM.getKey(optic).withPath(path -> "textures/gui/scope/" + path + ".png");
		graphics.blit(RenderPipelines.GUI_TEXTURED, etched, -half, -half, 0, 0, size, size, size, size, ARGB.white(alpha));

		Reticle reticle = stack.getOrDefault(ModComponents.RETICLE, Reticle.DEFAULT);
		int lit = Math.max(4, Math.round(height * RETICLE_SIZE * reticle.size()) & ~1);
		Identifier shape = McWeapons.id("textures/effect/reticle_" + reticle.shape().getSerializedName() + ".png");
		graphics.blit(RenderPipelines.GUI_TEXTURED, shape, -lit / 2, -lit / 2, 0, 0, lit, lit, lit, lit, ARGB.color(alpha, reticle.color()));
		if (optic.maxZoom() > optic.minZoom()) {
			String power = Math.round(GunInput.zoom(optic)) + "x";
			// Beside the lower post, not on it.
			graphics.text(minecraft.font, power, Math.max(4, Math.round(half * 0.04F)), Math.round(half * 0.62F), ARGB.color(Math.round(alpha * 0.85F), 0xE0E0E0), true);
		}
		graphics.pose().popMatrix();
	}
}

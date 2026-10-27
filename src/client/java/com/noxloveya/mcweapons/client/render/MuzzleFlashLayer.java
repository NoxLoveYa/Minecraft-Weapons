package com.noxloveya.mcweapons.client.render;

import com.geckolib.cache.model.GeoBone;
import com.geckolib.renderer.GeoItemRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.PerBoneRender;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.GeoRenderLayer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.noxloveya.mcweapons.McWeapons;
import com.noxloveya.mcweapons.gun.GunItem;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import java.util.function.BiConsumer;

/** A short additive flash at the {@code muzzle} bone, shown for a couple of frames after a local shot. */
public class MuzzleFlashLayer extends GeoRenderLayer<GunItem, GeoItemRenderer.RenderData, GeoRenderState> {
	private static final Identifier TEXTURE = McWeapons.id("textures/effect/muzzle_flash.png");
	private static final float RADIUS = 0.15F;
	// Pushed out past the slide so the flash is not hidden inside the barrel.
	private static final float OFFSET = -0.05F;
	private static final int FULL_BRIGHT = 0xF000F0;

	public MuzzleFlashLayer(GunRenderer renderer) {
		super(renderer);
	}

	@Override
	public void addPerBoneRender(RenderPassInfo<GeoRenderState> renderPassInfo, BiConsumer<GeoBone, PerBoneRender<GeoRenderState>> consumer) {
		if (renderPassInfo.willRender() && Boolean.TRUE.equals(renderPassInfo.getGeckolibData(GunRenderer.MUZZLE_FLASH))) {
			renderPassInfo.model().getBone("muzzle").ifPresent(bone -> consumer.accept(bone, this::submitFlash));
		}
	}

	private void submitFlash(RenderPassInfo<GeoRenderState> renderPassInfo, GeoBone bone, SubmitNodeCollector renderTasks) {
		float roll = Viewmodel.muzzleFlashRoll();
		float cos = Mth.cos(roll) * RADIUS;
		float sin = Mth.sin(roll) * RADIUS;
		renderTasks.submitCustomGeometry(renderPassInfo.poseStack(), RenderTypes.eyes(TEXTURE), (pose, buffer) -> {
			// A star facing back along the barrel (toward the eye), plus two crossed quads along the barrel for the side view.
			float z = OFFSET;
			float side = RADIUS * 0.6F;
			float far = OFFSET - RADIUS * 2.5F;
			quad(buffer, pose, -cos + sin, -sin - cos, z, cos + sin, sin - cos, z, cos - sin, sin + cos, z, -cos - sin, -sin + cos, z);
			quad(buffer, pose, -side, 0, 0, side, 0, 0, side, 0, far, -side, 0, far);
			quad(buffer, pose, 0, -side, 0, 0, side, 0, 0, side, far, 0, -side, far);
		});
	}

	/** Emits both windings so the quad shows from either side. */
	private static void quad(VertexConsumer buffer, PoseStack.Pose pose, float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3) {
		vertex(buffer, pose, x0, y0, z0, 0, 1);
		vertex(buffer, pose, x1, y1, z1, 1, 1);
		vertex(buffer, pose, x2, y2, z2, 1, 0);
		vertex(buffer, pose, x3, y3, z3, 0, 0);
		vertex(buffer, pose, x3, y3, z3, 0, 0);
		vertex(buffer, pose, x2, y2, z2, 1, 0);
		vertex(buffer, pose, x1, y1, z1, 1, 1);
		vertex(buffer, pose, x0, y0, z0, 0, 1);
	}

	private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z, float u, float v) {
		buffer.addVertex(pose, x, y, z).setColor(-1).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(pose, 0, 0, 1);
	}
}

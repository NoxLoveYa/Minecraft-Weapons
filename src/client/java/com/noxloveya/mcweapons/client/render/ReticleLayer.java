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

import java.util.Set;
import java.util.function.BiConsumer;

/** The glowing dot of a fitted optic, at its {@code <attachment>_reticle} bone, facing back at the eye like a real one. */
public class ReticleLayer extends GeoRenderLayer<GunItem, GeoItemRenderer.RenderData, GeoRenderState> {
	private static final Identifier TEXTURE = McWeapons.id("textures/effect/red_dot.png");
	// Half the quad, halo included, in blocks: the core comes out a few pixels across at the ADS distance.
	private static final float RADIUS = 0.011F;
	private static final int FULL_BRIGHT = 0xF000F0;

	public ReticleLayer(GunRenderer renderer) {
		super(renderer);
	}

	@Override
	public void addPerBoneRender(RenderPassInfo<GeoRenderState> renderPassInfo, BiConsumer<GeoBone, PerBoneRender<GeoRenderState>> consumer) {
		Set<Identifier> shown = renderPassInfo.getGeckolibData(GunRenderer.ATTACHMENTS);
		if (!renderPassInfo.willRender() || shown == null) {
			return;
		}
		for (Identifier id : shown) {
			renderPassInfo.model().getBone(id.getPath() + "_reticle").ifPresent(bone -> consumer.accept(bone, this::submitDot));
		}
	}

	private void submitDot(RenderPassInfo<GeoRenderState> renderPassInfo, GeoBone bone, SubmitNodeCollector renderTasks) {
		renderTasks.submitCustomGeometry(renderPassInfo.poseStack(), RenderTypes.eyes(TEXTURE), (pose, buffer) -> {
			vertex(buffer, pose, -RADIUS, -RADIUS, 0, 1);
			vertex(buffer, pose, RADIUS, -RADIUS, 1, 1);
			vertex(buffer, pose, RADIUS, RADIUS, 1, 0);
			vertex(buffer, pose, -RADIUS, RADIUS, 0, 0);
		});
	}

	private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float u, float v) {
		buffer.addVertex(pose, x, y, 0).setColor(-1).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(pose, 0, 0, 1);
	}
}

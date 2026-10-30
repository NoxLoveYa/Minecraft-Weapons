package com.noxloveya.mcweapons.client.render;

import com.geckolib.cache.model.GeoBone;
import com.geckolib.renderer.GeoItemRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.PerBoneRender;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.GeoRenderLayer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.noxloveya.mcweapons.McWeapons;
import com.noxloveya.mcweapons.gun.AttachmentItem;
import com.noxloveya.mcweapons.gun.GunItem;
import com.noxloveya.mcweapons.gun.Reticle;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * The glowing reticle of a fitted optic, at its {@code <attachment>_reticle} bone, facing back at the eye like a real one. Its shape,
 * color and size come from the gun's {@link Reticle}; the textures are white and the color tints them.
 */
public class ReticleLayer extends GeoRenderLayer<GunItem, GeoItemRenderer.RenderData, GeoRenderState> {
	private static final Map<Reticle.Shape, Identifier> TEXTURES = new EnumMap<>(Reticle.Shape.class);
	/**
	 * In first person, half the quad (halo included) at size 1, as a share of the screen's height: the same on screen through any optic,
	 * at any distance and FOV. The dot's core is under a fifth of the quad, so 8 pixels across at 1080p at size 1, 2 at the smallest.
	 */
	public static final float SCREEN_RADIUS = 0.02F;
	// In someone's hand: half the quad in blocks at size 1, per model pixel from the gun's origin to the reticle.
	private static final float RADIUS = 0.022F / 12.1F;
	private static final int FULL_BRIGHT = 0xF000F0;

	static {
		for (Reticle.Shape shape : Reticle.Shape.values()) {
			TEXTURES.put(shape, McWeapons.id("textures/effect/reticle_" + shape.getSerializedName() + ".png"));
		}
	}

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
			// A scope's reticle is seen only through its lens (ScopeRenderer); its locator is the eyepiece.
			if (!(BuiltInRegistries.ITEM.getValue(id) instanceof AttachmentItem attachment && attachment.isScope())) {
				renderPassInfo.model().getBone(id.getPath() + "_reticle").ifPresent(bone -> consumer.accept(bone, this::submitReticle));
			}
		}
	}

	private void submitReticle(RenderPassInfo<GeoRenderState> renderPassInfo, GeoBone bone, SubmitNodeCollector renderTasks) {
		Reticle reticle = Objects.requireNonNullElse(renderPassInfo.getGeckolibData(GunRenderer.RETICLE), Reticle.DEFAULT);
		float radius = GunRenderer.firstPerson(renderPassInfo.renderState())
				? SCREEN_RADIUS * reticle.size() * blocksPerScreen(renderPassInfo.poseStack().last().pose())
				: RADIUS * reticle.size() * Math.abs(bone.pivotZ());
		int color = 0xFF000000 | reticle.color();
		renderTasks.submitCustomGeometry(renderPassInfo.poseStack(), RenderTypes.eyes(TEXTURES.get(reticle.shape())), (pose, buffer) -> {
			vertex(buffer, pose, -radius, -radius, 0, 1, color);
			vertex(buffer, pose, radius, -radius, 1, 1, color);
			vertex(buffer, pose, radius, radius, 1, 0, color);
			vertex(buffer, pose, -radius, radius, 0, 0, color);
		});
	}

	/** How many blocks the screen's height spans at the depth of {@code pose}'s origin, in the hand's view (see Viewmodel.recordLensPose). */
	private static float blocksPerScreen(Matrix4fc pose) {
		float depth = -new Matrix4f(RenderSystem.getModelViewStack()).mul(pose).transformPosition(new Vector3f()).z;
		return 2 * Math.max(depth, 0.01F) * Viewmodel.hudTanHalf();
	}

	private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float u, float v, int color) {
		buffer.addVertex(pose, x, y, 0).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(pose, 0, 0, 1);
	}
}

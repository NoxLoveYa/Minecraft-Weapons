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
import com.noxloveya.mcweapons.gun.AttachmentItem;
import com.noxloveya.mcweapons.gun.GunItem;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.util.Set;
import java.util.function.BiConsumer;

/**
 * The lens of a fitted scope, in first person: a disc on its eyepiece showing the view through it, which is the screen after the lens
 * pass at the disc's own place on the screen ({@link ScopeRenderer}).
 */
public class ScopeLensLayer extends GeoRenderLayer<GunItem, GeoItemRenderer.RenderData, GeoRenderState> {
	private static final int SEGMENTS = 32;
	private static final int FULL_BRIGHT = 0xF000F0;
	/** In front of the eyepiece's glass (which is half a millimetre proud of it), in blocks, so the two do not fight. */
	private static final float LIFT = 0.0035F;

	private final GunRenderer gunRenderer;

	public ScopeLensLayer(GunRenderer renderer) {
		super(renderer);
		this.gunRenderer = renderer;
	}

	@Override
	public void addPerBoneRender(RenderPassInfo<GeoRenderState> renderPassInfo, BiConsumer<GeoBone, PerBoneRender<GeoRenderState>> consumer) {
		Set<Identifier> shown = renderPassInfo.getGeckolibData(GunRenderer.ATTACHMENTS);
		if (!renderPassInfo.willRender() || shown == null || !GunRenderer.firstPerson(renderPassInfo.renderState()) || !ScopeRenderer.ready()) {
			return;
		}
		for (Identifier id : shown) {
			if (BuiltInRegistries.ITEM.getValue(id) instanceof AttachmentItem attachment && attachment.isScope()) {
				float radius = this.gunRenderer.lensRadius(id);
				renderPassInfo.model().getBone(id.getPath() + "_reticle").ifPresent(bone -> consumer.accept(bone,
						(info, lensBone, renderTasks) -> renderTasks.submitCustomGeometry(info.poseStack(), RenderTypes.text(ScopeRenderer.LENS_TEXTURE),
								(pose, buffer) -> disc(buffer, pose, radius))));
			}
		}
	}

	/** A fan of quads (two corners at the center), facing back at the eye, each corner showing the lens texture where it is on screen. */
	private static void disc(VertexConsumer buffer, PoseStack.Pose pose, float radius) {
		// To view space as the hand pass draws it (see Viewmodel.recordLensPose).
		Matrix4f toView = new Matrix4f(RenderSystem.getModelViewStack()).mul(pose.pose());
		for (int i = 0; i < SEGMENTS; i++) {
			float a0 = i * Mth.TWO_PI / SEGMENTS;
			float a1 = (i + 1) * Mth.TWO_PI / SEGMENTS;
			vertex(buffer, pose, toView, 0, 0, radius);
			vertex(buffer, pose, toView, Mth.cos(a0), Mth.sin(a0), radius);
			vertex(buffer, pose, toView, Mth.cos(a1), Mth.sin(a1), radius);
			vertex(buffer, pose, toView, 0, 0, radius);
		}
	}

	private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, Matrix4f toView, float x, float y, float radius) {
		Vector2f uv = ScopeRenderer.screenUv(toView.transformPosition(new Vector3f(x * radius, y * radius, LIFT)));
		buffer.addVertex(pose, x * radius, y * radius, LIFT).setColor(-1).setUv(uv.x, uv.y).setLight(FULL_BRIGHT);
	}
}

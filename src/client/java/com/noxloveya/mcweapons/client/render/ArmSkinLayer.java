package com.noxloveya.mcweapons.client.render;

import com.geckolib.cache.model.GeoBone;
import com.geckolib.cache.model.cuboid.CuboidGeoBone;
import com.geckolib.cache.model.cuboid.GeoCube;
import com.geckolib.renderer.GeoItemRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.builtin.CustomBoneTextureGeoLayer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.noxloveya.mcweapons.gun.GunItem;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.Identifier;

/** Draws an {@code arm_} bone with the holder's skin instead of the gun texture (Steve arm layout for phase 1). First person only. */
public class ArmSkinLayer extends CustomBoneTextureGeoLayer<GunItem, GeoItemRenderer.RenderData, GeoRenderState> {
	public ArmSkinLayer(GunRenderer renderer, String boneName) {
		super(renderer, boneName, MissingTextureAtlasSprite.getLocation());
	}

	@Override
	public boolean shouldRenderBone(GeoRenderState renderState) {
		return GunRenderer.firstPerson(renderState) && renderState.getGeckolibData(GunRenderer.SKIN) != null;
	}

	@Override
	protected Identifier getTextureResource(GeoRenderState renderState) {
		Identifier skin = renderState.getGeckolibData(GunRenderer.SKIN);
		return skin != null ? skin : this.texture;
	}

	// The base class rescales UVs by texture size, but a skin that is still downloading reports the placeholder's size. Skins are always 64x64, like the gun sheet.
	@Override
	protected void renderBone(RenderPassInfo<GeoRenderState> renderPassInfo, GeoBone bone, SubmitNodeCollector renderTasks) {
		RenderType renderType = getRenderType(renderPassInfo.renderState(), getTextureResource(renderPassInfo.renderState()));
		if (renderType == null) {
			return;
		}
		int packedLight = renderPassInfo.packedLight();
		int packedOverlay = renderPassInfo.packedOverlay();
		int renderColor = renderPassInfo.renderColor();
		renderTasks.submitCustomGeometry(renderPassInfo.poseStack(), renderType, (pose, buffer) -> {
			PoseStack poseStack = renderPassInfo.poseStack();
			poseStack.pushPose();
			poseStack.last().set(pose);
			bone.translateAwayFromPivotPoint(poseStack);
			for (GeoCube cube : ((CuboidGeoBone)bone).cubes) {
				poseStack.pushPose();
				renderCube(cube, poseStack, buffer, packedLight, packedOverlay, renderColor, 1, 1);
				poseStack.popPose();
			}
			poseStack.popPose();
		});
	}
}

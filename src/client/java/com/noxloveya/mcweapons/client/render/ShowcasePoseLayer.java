package com.noxloveya.mcweapons.client.render;

import com.geckolib.cache.model.GeoBone;
import com.geckolib.renderer.GeoItemRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.PerBoneRender;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.GeoRenderLayer;
import com.noxloveya.mcweapons.gun.GunItem;

import java.util.function.BiConsumer;

/** Draws nothing: while the tuner shows the gun, it hands the gun bone's drawn pose to the viewmodel, so the tuner can wire its panels to it. */
public class ShowcasePoseLayer extends GeoRenderLayer<GunItem, GeoItemRenderer.RenderData, GeoRenderState> {
	public ShowcasePoseLayer(GunRenderer renderer) {
		super(renderer);
	}

	@Override
	public void addPerBoneRender(RenderPassInfo<GeoRenderState> renderPassInfo, BiConsumer<GeoBone, PerBoneRender<GeoRenderState>> consumer) {
		if (renderPassInfo.willRender() && GunRenderer.firstPerson(renderPassInfo.renderState())
				&& Boolean.TRUE.equals(renderPassInfo.getGeckolibData(GunItem.SHOWCASE))) {
			renderPassInfo.model().getBone("gun").ifPresent(bone ->
					consumer.accept(bone, (info, gunBone, renderTasks) -> Viewmodel.recordGunPose(info.poseStack().last().pose(), gunBone)));
		}
	}
}

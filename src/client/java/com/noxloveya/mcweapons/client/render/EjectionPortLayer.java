package com.noxloveya.mcweapons.client.render;

import com.geckolib.cache.model.GeoBone;
import com.geckolib.constant.DataTickets;
import com.geckolib.renderer.GeoItemRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.PerBoneRender;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.GeoRenderLayer;
import com.noxloveya.mcweapons.gun.GunItem;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import java.util.function.BiConsumer;

/** Draws nothing: notes where the {@code ejection_port} bone is drawn, first person or in someone's hand, so casings leave from there. */
public class EjectionPortLayer extends GeoRenderLayer<GunItem, GeoItemRenderer.RenderData, GeoRenderState> {
	public EjectionPortLayer(GunRenderer renderer) {
		super(renderer);
	}

	@Override
	public void addPerBoneRender(RenderPassInfo<GeoRenderState> renderPassInfo, BiConsumer<GeoBone, PerBoneRender<GeoRenderState>> consumer) {
		if (!renderPassInfo.willRender()) {
			return;
		}
		if (GunRenderer.firstPerson(renderPassInfo.renderState())) {
			renderPassInfo.model().getBone("ejection_port").ifPresent(bone ->
					consumer.accept(bone, (info, portBone, renderTasks) -> Viewmodel.recordPortPose(info.poseStack().last().pose())));
			return;
		}
		Long instanceId = renderPassInfo.getGeckolibData(DataTickets.ANIMATABLE_INSTANCE_ID);
		Minecraft minecraft = Minecraft.getInstance();
		Entity holder = instanceId == null || minecraft.level == null ? null : GunRenderer.holder(minecraft.level, instanceId);
		if (holder != null) {
			renderPassInfo.model().getBone("ejection_port").ifPresent(bone ->
					consumer.accept(bone, (info, portBone, renderTasks) -> Casings.recordHeldPort(holder, info.poseStack().last().pose())));
		}
	}
}

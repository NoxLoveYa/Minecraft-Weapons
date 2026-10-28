package com.noxloveya.mcweapons.client.render;

import com.geckolib.cache.model.GeoBone;
import com.geckolib.constant.DataTickets;
import com.geckolib.renderer.GeoItemRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.PerBoneRender;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.GeoRenderLayer;
import com.noxloveya.mcweapons.gun.AttachmentItem;
import com.noxloveya.mcweapons.gun.AttachmentSlot;
import com.noxloveya.mcweapons.gun.GunItem;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Draws nothing: notes where bones are drawn so other things can follow them. The ejection port, first person or in someone's hand,
 * for casings to leave from; a fitted laser's emitter, for its beam; a fitted scope's eyepiece in first person, for its view; the gun, magazine, attachment slots and a long gun's support hand
 * locator in someone's hand, for their other hand to hold.
 */
public class BonePoseLayer extends GeoRenderLayer<GunItem, GeoItemRenderer.RenderData, GeoRenderState> {
	private final GunRenderer gunRenderer;

	public BonePoseLayer(GunRenderer renderer) {
		super(renderer);
		this.gunRenderer = renderer;
	}

	@Override
	public void addPerBoneRender(RenderPassInfo<GeoRenderState> renderPassInfo, BiConsumer<GeoBone, PerBoneRender<GeoRenderState>> consumer) {
		if (!renderPassInfo.willRender()) {
			return;
		}
		Set<Identifier> shown = renderPassInfo.getGeckolibData(GunRenderer.ATTACHMENTS);
		if (GunRenderer.firstPerson(renderPassInfo.renderState())) {
			renderPassInfo.model().getBone("ejection_port").ifPresent(bone ->
					consumer.accept(bone, (info, portBone, renderTasks) -> Viewmodel.recordPortPose(info.poseStack().last().pose())));
			for (Identifier id : shown == null ? Set.<Identifier>of() : shown) {
				renderPassInfo.model().getBone(id.getPath() + "_emitter").ifPresent(bone ->
						consumer.accept(bone, (info, emitterBone, renderTasks) -> Viewmodel.recordEmitterPose(info.poseStack().last().pose())));
				if (BuiltInRegistries.ITEM.getValue(id) instanceof AttachmentItem attachment && attachment.isScope()) {
					float radius = this.gunRenderer.lensRadius(id);
					renderPassInfo.model().getBone(id.getPath() + "_reticle").ifPresent(bone ->
							consumer.accept(bone, (info, lensBone, renderTasks) -> Viewmodel.recordLensPose(info.poseStack().last().pose(), radius)));
				}
			}
			return;
		}
		Long instanceId = renderPassInfo.getGeckolibData(DataTickets.ANIMATABLE_INSTANCE_ID);
		Minecraft minecraft = Minecraft.getInstance();
		Entity holder = instanceId == null || minecraft.level == null ? null : GunRenderer.holder(minecraft.level, instanceId);
		if (holder != null) {
			renderPassInfo.model().getBone("ejection_port").ifPresent(bone ->
					consumer.accept(bone, (info, portBone, renderTasks) -> Casings.recordHeldPort(holder, info.poseStack().last().pose())));
			renderPassInfo.model().getBone("gun").ifPresent(bone ->
					consumer.accept(bone, (info, gunBone, renderTasks) -> ThirdPersonGuns.recordGun(holder, info.poseStack().last().pose())));
			renderPassInfo.model().getBone("magazine").ifPresent(bone ->
					consumer.accept(bone, (info, magBone, renderTasks) -> ThirdPersonGuns.recordMag(holder, info.poseStack().last().pose())));
			renderPassInfo.model().getBone("support_hand").ifPresent(bone ->
					consumer.accept(bone, (info, supportBone, renderTasks) -> ThirdPersonGuns.recordSupport(holder, info.poseStack().last().pose())));
			for (AttachmentSlot slot : AttachmentSlot.values()) {
				renderPassInfo.model().getBone("attach_" + slot.getSerializedName()).ifPresent(bone ->
						consumer.accept(bone, (info, slotBone, renderTasks) -> ThirdPersonGuns.recordSlot(holder, slot, info.poseStack().last().pose())));
			}
			for (Identifier id : shown == null ? Set.<Identifier>of() : shown) {
				renderPassInfo.model().getBone(id.getPath() + "_emitter").ifPresent(bone ->
						consumer.accept(bone, (info, emitterBone, renderTasks) -> Lasers.recordHeld(holder, info.poseStack().last().pose())));
			}
		}
	}
}

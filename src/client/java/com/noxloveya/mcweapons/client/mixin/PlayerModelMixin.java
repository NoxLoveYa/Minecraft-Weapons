package com.noxloveya.mcweapons.client.mixin;

import com.noxloveya.mcweapons.client.render.ThirdPersonGuns;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// After vanilla's arm poses: a gun is aimed with both hands on it, a long gun carried at the hip with both, and the other hand holds the
// magazine while it is out.
@Mixin(PlayerModel.class)
public class PlayerModelMixin {
	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
	private void mcweapons$gunArms(AvatarRenderState state, CallbackInfo ci) {
		HumanoidModel<?> model = (HumanoidModel<?>)(Object)this;
		if (Boolean.TRUE.equals(state.getData(ThirdPersonGuns.AIM_HOLD))) {
			ThirdPersonGuns.poseAimHold(model, state.mainArm);
		} else if (Boolean.TRUE.equals(state.getData(ThirdPersonGuns.LOW_READY))) {
			ThirdPersonGuns.poseLowReady(model, state.mainArm);
		}
		Vec3 magazine = state.getData(ThirdPersonGuns.SUPPORT_HAND);
		if (magazine != null) {
			ThirdPersonGuns.reachSupportArm(model, state, magazine);
		}
	}
}

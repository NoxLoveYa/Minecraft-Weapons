package com.noxloveya.mcweapons.client.mixin;

import com.noxloveya.mcweapons.client.render.ThirdPersonGuns;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// After vanilla's arm poses, so a gun is aimed with both hands on it.
@Mixin(PlayerModel.class)
public class PlayerModelMixin {
	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
	private void mcweapons$aimHold(AvatarRenderState state, CallbackInfo ci) {
		if (Boolean.TRUE.equals(state.getData(ThirdPersonGuns.AIM_HOLD))) {
			ThirdPersonGuns.poseAimHold((HumanoidModel<?>)(Object)this, state.mainArm);
		}
	}
}

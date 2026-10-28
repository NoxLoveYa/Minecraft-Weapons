package com.noxloveya.mcweapons.client.mixin;

import com.noxloveya.mcweapons.client.render.ThirdPersonGuns;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// No API lets an item pick its third-person arm pose outside of being used; guns raise the arms to aim, reload and inspect.
@Mixin(AvatarRenderer.class)
public class AvatarRendererMixin {
	@Inject(method = "getArmPose(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/client/model/HumanoidModel$ArmPose;",
			at = @At("HEAD"), cancellable = true)
	private static void mcweapons$gunArmPose(Avatar avatar, ItemStack stack, InteractionHand hand, CallbackInfoReturnable<HumanoidModel.ArmPose> cir) {
		HumanoidModel.ArmPose pose = ThirdPersonGuns.armPose(avatar, stack, hand);
		if (pose != null) {
			cir.setReturnValue(pose);
		}
	}

	// Vanilla's arm poses cannot hold a pistol in both hands, so aiming is flagged here and posed after vanilla (PlayerModelMixin).
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
	private void mcweapons$aimHold(Avatar avatar, AvatarRenderState state, float partialTick, CallbackInfo ci) {
		state.setData(ThirdPersonGuns.AIM_HOLD, ThirdPersonGuns.pose(avatar) == ThirdPersonGuns.Pose.AIM);
	}
}

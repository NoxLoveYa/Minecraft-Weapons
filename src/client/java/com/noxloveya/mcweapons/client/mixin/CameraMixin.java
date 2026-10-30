package com.noxloveya.mcweapons.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.noxloveya.mcweapons.client.render.Viewmodel;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Camera.class)
public class CameraMixin {
	// Fabric API has no FOV event. Applied after vanilla's per-tick FOV smoothing so the zoom follows ads_ticks exactly.
	@ModifyReturnValue(method = "calculateFov", at = @At("RETURN"))
	private float mcweapons$adsZoom(float fov, float partialTicks) {
		return fov * Viewmodel.fovMultiplier(partialTicks);
	}

	// No API scales the view bob. With a gun out the world does not bob at all: only the gun does, so shots and a laser land where it points on screen.
	@ModifyExpressionValue(method = "extractRenderState", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/entity/ClientAvatarState;getInterpolatedBob(F)F"))
	private float mcweapons$scaleViewBob(float bob) {
		return bob * Viewmodel.viewBobScale();
	}
}

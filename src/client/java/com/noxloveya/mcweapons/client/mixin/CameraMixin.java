package com.noxloveya.mcweapons.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.noxloveya.mcweapons.client.render.Viewmodel;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Fabric API has no FOV event. Applied after vanilla's per-tick FOV smoothing so the zoom follows ads_ticks exactly.
@Mixin(Camera.class)
public class CameraMixin {
	@ModifyReturnValue(method = "calculateFov", at = @At("RETURN"))
	private float mcweapons$adsZoom(float fov, float partialTicks) {
		return fov * Viewmodel.fovMultiplier(partialTicks);
	}
}

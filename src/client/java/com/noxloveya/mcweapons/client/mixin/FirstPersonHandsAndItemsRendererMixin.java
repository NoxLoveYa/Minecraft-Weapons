package com.noxloveya.mcweapons.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.noxloveya.mcweapons.client.render.Viewmodel;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Neither Fabric API nor GeckoLib can replace first-person hand rendering, so the viewmodel takes over here.
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public class FirstPersonHandsAndItemsRendererMixin {
	@Inject(method = "submitHandsWithItems", at = @At("HEAD"), cancellable = true)
	private void mcweapons$drawViewmodel(float partialTicks, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, PlayerRenderState playerState,
			FirstPersonHandsAndItemsRenderState state, CallbackInfo ci) {
		if (Viewmodel.submit(partialTicks, poseStack, submitNodeCollector, playerState, state)) {
			ci.cancel();
		}
	}
}

package com.noxloveya.mcweapons.client.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.PoseStack;
import com.noxloveya.mcweapons.client.render.ScopeRenderer;
import com.noxloveya.mcweapons.gun.GunItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
	// No API lets a mod drop vanilla's walk bob on the hands; the viewmodel applies its own, scaled down while aiming.
	@WrapWithCondition(method = "renderItemInHand", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/GameRenderer;bobView(Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V"))
	private boolean mcweapons$skipHandBobWithGun(GameRenderer renderer, CameraRenderState cameraState, PoseStack poseStack) {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.player == null || !GunItem.holdsGun(minecraft.player);
	}

	// No API renders the level a second time: a fitted scope's view is drawn here, once the frame's own level and hand are done.
	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;applyPostEffects()V", shift = At.Shift.AFTER))
	private void mcweapons$renderScope(CallbackInfo ci) {
		ScopeRenderer.render((GameRenderer)(Object)this);
	}

	// While the scope's view is drawn the level goes into its texture: the level renderer always draws into this target.
	@Inject(method = "mainRenderTarget", at = @At("HEAD"), cancellable = true)
	private void mcweapons$scopeTarget(CallbackInfoReturnable<RenderTarget> cir) {
		RenderTarget scope = ScopeRenderer.renderingInto();
		if (scope != null) {
			cir.setReturnValue(scope);
		}
	}
}

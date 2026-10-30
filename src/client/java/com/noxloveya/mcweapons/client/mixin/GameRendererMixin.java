package com.noxloveya.mcweapons.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.noxloveya.mcweapons.client.render.ScopeRenderer;
import com.noxloveya.mcweapons.client.render.Viewmodel;
import com.noxloveya.mcweapons.gun.GunItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
	@Shadow
	@Final
	private SubmitNodeStorage handAndScreenSubmitNodeStorage;
	@Shadow
	@Final
	private Projection hudProjection;

	// No API lets a mod drop vanilla's walk bob on the hands; the viewmodel applies its own, scaled down while aiming.
	@WrapWithCondition(method = "renderItemInHand", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/GameRenderer;bobView(Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V"))
	private boolean mcweapons$skipHandBobWithGun(GameRenderer renderer, CameraRenderState cameraState, PoseStack poseStack) {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.player == null || !GunItem.holdsGun(minecraft.player);
	}

	// Vanilla drops the hand with the HUD (F1); a held gun stays on screen.
	@ModifyExpressionValue(method = "renderItemInHand", at = @At(value = "FIELD",
			target = "Lnet/minecraft/client/renderer/state/gui/GuiRenderState;isHudHidden:Z"))
	private boolean mcweapons$gunWithoutHud(boolean hudHidden) {
		Minecraft minecraft = Minecraft.getInstance();
		return hudHidden && (minecraft.player == null || !GunItem.holdsGun(minecraft.player));
	}

	// No API runs between the level and the hand: an aimed scope's lens pass goes over the level here, so the scope is drawn sharp over it.
	@Inject(method = "render3dHud", at = @At("HEAD"))
	private void mcweapons$scopeLens(CallbackInfo ci) {
		ScopeRenderer.processLevel((GameRenderer)(Object)this);
	}

	// No API runs once the hand is drawn: an aimed 1x sight's glass goes over its window here, over the level and the gun seen through it.
	@Inject(method = "render3dHud", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
			target = "Lnet/minecraft/client/renderer/GameRenderer;renderItemInHand(Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lnet/minecraft/client/renderer/state/level/PlayerRenderState;Lcom/mojang/renderpearl/api/textures/GpuTextureView;)V"))
	private void mcweapons$sightGlass(CallbackInfo ci, @Local GpuTextureView handDepth) {
		ScopeRenderer.processSight((GameRenderer)(Object)this, handDepth, this.hudProjection.getMatrix(new Matrix4f()));
	}

	// A shader pack (Iris) takes the hand call out of this method and draws the hand in its level pipeline; while a scope is up the gun
	// is left out there and drawn here, after the lens pass, with what vanilla's hand pass is about to draw.
	@Inject(method = "renderItemInHand", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher;prepareFrame(Lnet/minecraft/client/renderer/SubmitNodeStorage;)Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;"))
	private void mcweapons$gunAfterLens(CameraRenderState cameraState, PlayerRenderState playerState, GpuTextureView depthTextureView, CallbackInfo ci,
			@Local PoseStack poseStack) {
		if (playerState.firstPersonHandsAndItems == null) {
			return;
		}
		// With a 1x sight the pack's gun stays; this one is drawn for its depth only, its colour taken back off before the glass pass.
		if (ScopeRenderer.sightDepthOnly()) {
			ScopeRenderer.saveBeforeHand((GameRenderer)(Object)this);
		}
		if (ScopeRenderer.gunAfterLens() || ScopeRenderer.sightDepthOnly()) {
			Viewmodel.submit(cameraState.cameraEntityPartialTicks, poseStack, this.handAndScreenSubmitNodeStorage, playerState, playerState.firstPersonHandsAndItems);
		}
	}
}

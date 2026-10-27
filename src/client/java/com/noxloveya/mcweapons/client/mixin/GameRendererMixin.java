package com.noxloveya.mcweapons.client.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.blaze3d.vertex.PoseStack;
import com.noxloveya.mcweapons.gun.GunItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// No API lets a mod drop vanilla's walk bob on the hands; the viewmodel applies its own, scaled down while aiming.
@Mixin(GameRenderer.class)
public class GameRendererMixin {
	@WrapWithCondition(method = "renderItemInHand", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/GameRenderer;bobView(Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V"))
	private boolean mcweapons$skipHandBobWithGun(GameRenderer renderer, CameraRenderState cameraState, PoseStack poseStack) {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.player == null || !GunItem.holdsGun(minecraft.player);
	}
}

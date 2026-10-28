package com.noxloveya.mcweapons.client.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SkyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// The sky keeps the screen's target from when it was made, the only part of the level that does; a scope's view (ScopeRenderer) draws
// the level into its own target, and the sky has to go there too.
@Mixin(SkyRenderer.class)
public class SkyRendererMixin {
	@Redirect(method = "render", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/SkyRenderer;renderTarget:Lcom/mojang/blaze3d/pipeline/RenderTarget;"))
	private RenderTarget mcweapons$currentTarget(SkyRenderer renderer) {
		return Minecraft.getInstance().gameRenderer.mainRenderTarget();
	}
}

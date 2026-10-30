package com.noxloveya.mcweapons.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.noxloveya.mcweapons.gun.GunItem;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

// Under a shader pack Iris draws the hand itself and drops it with the HUD (F1), like vanilla (see GameRendererMixin); a held gun stays.
// Iris is not a dependency: without it this mixin is skipped.
@Pseudo
@Mixin(targets = "net.irisshaders.iris.pathways.HandRenderer")
public class IrisHandRendererMixin {
	@ModifyExpressionValue(method = "canRender", require = 0, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Hud;isHidden()Z"))
	private boolean mcweapons$gunWithoutHud(boolean hudHidden) {
		Minecraft minecraft = Minecraft.getInstance();
		return hudHidden && (minecraft.player == null || !GunItem.holdsGun(minecraft.player));
	}
}

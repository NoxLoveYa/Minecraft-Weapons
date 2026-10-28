package com.noxloveya.mcweapons.client.mixin;

import com.noxloveya.mcweapons.client.input.GunInput;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Aiming or firing a gun ends a sprint and keeps a new one from starting. No API covers this; vanilla checks this method both to
// start a sprint and to keep one going.
@Mixin(LocalPlayer.class)
public class LocalPlayerMixin {
	@Inject(method = "isSprintingPossible", at = @At("HEAD"), cancellable = true)
	private void mcweapons$noSprintWithGunUp(boolean flying, CallbackInfoReturnable<Boolean> cir) {
		if (GunInput.blocksSprint((LocalPlayer)(Object)this)) {
			cir.setReturnValue(false);
		}
	}
}

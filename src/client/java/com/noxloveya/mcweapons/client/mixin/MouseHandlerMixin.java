package com.noxloveya.mcweapons.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.noxloveya.mcweapons.client.input.GunInput;
import com.noxloveya.mcweapons.client.render.Viewmodel;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.joml.Vector2i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
	// Fabric API only has scroll events for screens. A variable scope takes the wheel while aimed; vanilla then sees no scroll at all.
	@ModifyExpressionValue(method = "onScroll", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/ScrollWheelHandler;onMouseScroll(DD)Lorg/joml/Vector2i;"))
	private Vector2i mcweapons$turnScopePower(Vector2i wheel) {
		return wheel.y != 0 && GunInput.scrollZoom(wheel.y) ? new Vector2i() : wheel;
	}

	// No API scales mouse turning. Aiming turns slower by as much as it zooms, or a 10x scope would be impossible to hold on anything.
	@WrapOperation(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
	private void mcweapons$slowAimedTurn(LocalPlayer player, double yRot, double xRot, Operation<Void> original) {
		float scale = Viewmodel.turnScale();
		original.call(player, yRot * scale, xRot * scale);
	}
}

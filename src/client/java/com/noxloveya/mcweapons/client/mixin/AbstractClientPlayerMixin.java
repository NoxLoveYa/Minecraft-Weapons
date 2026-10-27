package com.noxloveya.mcweapons.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.noxloveya.mcweapons.network.ServerHandlers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Vanilla narrows the FOV when movement speed drops, which would stack a second zoom on top of the ADS zoom. No API covers this.
@Mixin(AbstractClientPlayer.class)
public class AbstractClientPlayerMixin {
	@ModifyExpressionValue(method = "getFieldOfViewModifier", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/player/AbstractClientPlayer;getAttributeValue(Lnet/minecraft/core/Holder;)D"))
	private double mcweapons$ignoreAdsSlowdown(double speed) {
		AttributeInstance attribute = ((AbstractClientPlayer)(Object)this).getAttribute(Attributes.MOVEMENT_SPEED);
		AttributeModifier ads = attribute == null ? null : attribute.getModifier(ServerHandlers.ADS_SPEED);
		// The modifier is ADD_MULTIPLIED_TOTAL, so it scales the final value by (1 + amount).
		return ads == null ? speed : speed / (1 + ads.amount());
	}
}

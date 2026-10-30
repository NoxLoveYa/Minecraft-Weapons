package com.noxloveya.mcweapons.mixin;

import com.noxloveya.mcweapons.RegenDelay;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// Natural regeneration only heals a hurt player: one hit within the regen delay (RegenDelay) counts as unhurt here, so it waits.
// No API covers healing.
@Mixin(FoodData.class)
public class FoodDataMixin {
	@Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;isHurt()Z"))
	private boolean mcweapons$waitToHeal(ServerPlayer player) {
		return player.isHurt() && !RegenDelay.waiting(player);
	}
}

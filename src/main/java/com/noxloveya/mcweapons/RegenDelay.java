package com.noxloveya.mcweapons;

import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;

/**
 * Gamerule {@code mcweapons:regen_delay}, in seconds (0 by default, vanilla; the practice range's datapack sets it): natural regeneration
 * waits until a player has gone that long without taking damage (mixin.FoodDataMixin).
 */
public final class RegenDelay {
	public static final GameRule<Integer> REGEN_DELAY = GameRuleBuilder.forInteger(0).minValue(0).category(GameRuleCategory.PLAYER)
			.buildAndRegister(McWeapons.id("regen_delay"));

	private RegenDelay() {
	}

	public static void init() {
	}

	/** The player took damage less than the delay ago. */
	public static boolean waiting(ServerPlayer player) {
		int seconds = player.level().getGameRules().get(REGEN_DELAY);
		return seconds > 0 && player.getLastDamageSource(seconds * 20) != null;
	}
}

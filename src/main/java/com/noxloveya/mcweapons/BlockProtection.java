package com.noxloveya.mcweapons;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;

/**
 * Gamerule {@code mcweapons:protect_blocks} (off by default; the practice range's datapack turns it on): players who are not operators
 * cannot break, place or use blocks, creative or not, but still open doors, trapdoors and fence gates. Vanilla's spawn protection stops
 * them opening doors too, so the range does without it.
 */
public final class BlockProtection {
	public static final GameRule<Boolean> PROTECT_BLOCKS = GameRuleBuilder.forBoolean(false).category(GameRuleCategory.PLAYER)
			.buildAndRegister(McWeapons.id("protect_blocks"));

	private BlockProtection() {
	}

	static void register() {
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> !locked(player));
		// Using an item on a block (placing one, flint and steel, a spawn egg) goes through here as well as using the block itself.
		UseBlockCallback.EVENT.register((player, level, hand, hit) ->
				locked(player) && !opens(level.getBlockState(hit.getBlockPos()).getBlock()) ? InteractionResult.FAIL : InteractionResult.PASS);
		// A bucket places its fluid from the air, without using a block.
		UseItemCallback.EVENT.register((player, level, hand) ->
				locked(player) && player.getItemInHand(hand).getItem() instanceof BucketItem ? InteractionResult.FAIL : InteractionResult.PASS);
	}

	/** Server side only: the client's prediction is undone when the server refuses. */
	private static boolean locked(Player player) {
		return player instanceof ServerPlayer serverPlayer && serverPlayer.level().getGameRules().get(PROTECT_BLOCKS)
				&& !serverPlayer.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
	}

	private static boolean opens(Block block) {
		return block instanceof DoorBlock || block instanceof TrapDoorBlock || block instanceof FenceGateBlock;
	}
}

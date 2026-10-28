package com.noxloveya.mcweapons.gun;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;

/**
 * A gun loaded a round at a time when it is not empty (a Mosin's rounds thumbed in from the top): a reload opens up ({@code start_ticks}),
 * takes {@code round_ticks} per missing round, and closes ({@code end_ticks}). An empty gun still reloads in {@code reload_empty_ticks}.
 */
public record RoundReload(int startTicks, int roundTicks, int endTicks) {
	/** A round counts as in once this share of its own clip has played (the thumb pushes it down about then). */
	private static final float ROUND_IN = 0.6F;

	public static final Codec<RoundReload> CODEC = RecordCodecBuilder.create(i -> i.group(
			ExtraCodecs.NON_NEGATIVE_INT.fieldOf("start_ticks").forGetter(RoundReload::startTicks),
			ExtraCodecs.POSITIVE_INT.fieldOf("round_ticks").forGetter(RoundReload::roundTicks),
			ExtraCodecs.NON_NEGATIVE_INT.fieldOf("end_ticks").forGetter(RoundReload::endTicks)
	).apply(i, RoundReload::new));

	public int ticks(int rounds) {
		return startTicks + rounds * roundTicks + endTicks;
	}

	/** Rounds already in, {@code elapsed} ticks into a reload of {@code rounds}. */
	public int loaded(int rounds, float elapsed) {
		return Mth.clamp(Mth.floor((elapsed - startTicks) / roundTicks + (1 - ROUND_IN)), 0, rounds);
	}
}

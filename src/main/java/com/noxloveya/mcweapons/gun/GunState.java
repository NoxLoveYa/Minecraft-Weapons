package com.noxloveya.mcweapons.gun;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Ticks are server game time. {@code reloadingUntilTick} is 0 when no reload is going; a reload also keeps when it started and the rounds
 * the gun had then, so a round-at-a-time reload can count each round as it goes in (0 for both in states saved before that).
 */
public record GunState(long reloadingUntilTick, long lastShotTick, long reloadStartTick, int reloadFromAmmo) {
	public static final GunState EMPTY = new GunState(0, 0);

	public static final Codec<GunState> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.fieldOf("reloading_until_tick").forGetter(GunState::reloadingUntilTick),
			Codec.LONG.fieldOf("last_shot_tick").forGetter(GunState::lastShotTick),
			Codec.LONG.optionalFieldOf("reload_start_tick", 0L).forGetter(GunState::reloadStartTick),
			Codec.INT.optionalFieldOf("reload_from_ammo", 0).forGetter(GunState::reloadFromAmmo)
	).apply(i, GunState::new));

	public static final StreamCodec<ByteBuf, GunState> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, GunState::reloadingUntilTick,
			ByteBufCodecs.VAR_LONG, GunState::lastShotTick,
			ByteBufCodecs.VAR_LONG, GunState::reloadStartTick,
			ByteBufCodecs.VAR_INT, GunState::reloadFromAmmo,
			GunState::new);

	/** No reload going. */
	public GunState(long reloadingUntilTick, long lastShotTick) {
		this(reloadingUntilTick, lastShotTick, 0, 0);
	}

	public boolean reloading() {
		return reloadingUntilTick != 0;
	}

	/** A round-at-a-time reload of {@code definition} is going: rounds are counted as they go in. */
	public boolean roundReload(GunDefinition definition) {
		return reloading() && definition.roundReload() != null && reloadFromAmmo > 0 && reloadStartTick > 0;
	}
}

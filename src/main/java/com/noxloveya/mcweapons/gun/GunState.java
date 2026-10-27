package com.noxloveya.mcweapons.gun;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Ticks are server game time. {@code reloadingUntilTick} is 0 when no reload is going. */
public record GunState(long reloadingUntilTick, long lastShotTick) {
	public static final GunState EMPTY = new GunState(0, 0);

	public static final Codec<GunState> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.fieldOf("reloading_until_tick").forGetter(GunState::reloadingUntilTick),
			Codec.LONG.fieldOf("last_shot_tick").forGetter(GunState::lastShotTick)
	).apply(i, GunState::new));

	public static final StreamCodec<ByteBuf, GunState> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, GunState::reloadingUntilTick,
			ByteBufCodecs.VAR_LONG, GunState::lastShotTick,
			GunState::new);

	public boolean reloading() {
		return reloadingUntilTick != 0;
	}
}

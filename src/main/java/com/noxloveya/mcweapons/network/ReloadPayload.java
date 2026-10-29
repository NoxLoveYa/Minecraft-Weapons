package com.noxloveya.mcweapons.network;

import com.noxloveya.mcweapons.McWeapons;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: reload the gun in hotbar {@code slot} ({@code stopAfter} 0), or stop its round-at-a-time reload once {@code stopAfter}
 * rounds are in (the round its animation is pushing in).
 */
public record ReloadPayload(int slot, int stopAfter) implements CustomPacketPayload {
	public static final Type<ReloadPayload> TYPE = new Type<>(McWeapons.id("reload"));
	public static final StreamCodec<ByteBuf, ReloadPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, ReloadPayload::slot,
			ByteBufCodecs.VAR_INT, ReloadPayload::stopAfter,
			ReloadPayload::new);

	@Override
	public Type<ReloadPayload> type() {
		return TYPE;
	}
}

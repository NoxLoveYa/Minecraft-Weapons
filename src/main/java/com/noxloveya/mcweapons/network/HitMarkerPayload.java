package com.noxloveya.mcweapons.network;

import com.noxloveya.mcweapons.McWeapons;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to the shooter only: their shot hurt something, maybe in the head, maybe for the kill. Drives the hitmarker. */
public record HitMarkerPayload(boolean headshot, boolean kill) implements CustomPacketPayload {
	public static final Type<HitMarkerPayload> TYPE = new Type<>(McWeapons.id("hit_marker"));
	public static final StreamCodec<ByteBuf, HitMarkerPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, HitMarkerPayload::headshot,
			ByteBufCodecs.BOOL, HitMarkerPayload::kill,
			HitMarkerPayload::new);

	@Override
	public Type<HitMarkerPayload> type() {
		return TYPE;
	}
}

package com.noxloveya.mcweapons.network;

import com.noxloveya.mcweapons.McWeapons;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client to server: aimed, the eye sits {@code share} of {@code scope}'s own distance from it, on the gun in hotbar {@code slot}. */
public record EyeDistancePayload(int slot, Identifier scope, float share) implements CustomPacketPayload {
	public static final Type<EyeDistancePayload> TYPE = new Type<>(McWeapons.id("eye_distance"));
	public static final StreamCodec<ByteBuf, EyeDistancePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, EyeDistancePayload::slot,
			Identifier.STREAM_CODEC, EyeDistancePayload::scope,
			ByteBufCodecs.FLOAT, EyeDistancePayload::share,
			EyeDistancePayload::new);

	@Override
	public Type<EyeDistancePayload> type() {
		return TYPE;
	}
}

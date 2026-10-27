package com.noxloveya.mcweapons.network;

import com.noxloveya.mcweapons.McWeapons;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: fire the gun in hotbar {@code slot}. {@code seed} drives the spread cone. */
public record ShootPayload(int slot, long clientTick, long seed) implements CustomPacketPayload {
	public static final Type<ShootPayload> TYPE = new Type<>(McWeapons.id("shoot"));
	public static final StreamCodec<ByteBuf, ShootPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, ShootPayload::slot,
			ByteBufCodecs.VAR_LONG, ShootPayload::clientTick,
			ByteBufCodecs.VAR_LONG, ShootPayload::seed,
			ShootPayload::new);

	@Override
	public Type<ShootPayload> type() {
		return TYPE;
	}
}

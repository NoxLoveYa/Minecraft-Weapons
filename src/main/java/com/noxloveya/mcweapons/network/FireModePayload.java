package com.noxloveya.mcweapons.network;

import com.noxloveya.mcweapons.McWeapons;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: the fire mode key on the gun in hotbar {@code slot}, tapped (next mode) or {@code held} (safe); either leaves safe. */
public record FireModePayload(int slot, boolean held) implements CustomPacketPayload {
	public static final Type<FireModePayload> TYPE = new Type<>(McWeapons.id("fire_mode"));
	public static final StreamCodec<ByteBuf, FireModePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, FireModePayload::slot,
			ByteBufCodecs.BOOL, FireModePayload::held,
			FireModePayload::new);

	@Override
	public Type<FireModePayload> type() {
		return TYPE;
	}
}

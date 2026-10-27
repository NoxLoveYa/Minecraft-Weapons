package com.noxloveya.mcweapons.network;

import com.noxloveya.mcweapons.McWeapons;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: switch the gun in hotbar {@code slot} to its next fire mode. */
public record FireModePayload(int slot) implements CustomPacketPayload {
	public static final Type<FireModePayload> TYPE = new Type<>(McWeapons.id("fire_mode"));
	public static final StreamCodec<ByteBuf, FireModePayload> CODEC = ByteBufCodecs.VAR_INT.map(FireModePayload::new, FireModePayload::slot);

	@Override
	public Type<FireModePayload> type() {
		return TYPE;
	}
}

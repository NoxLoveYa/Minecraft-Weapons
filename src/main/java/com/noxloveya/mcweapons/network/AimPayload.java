package com.noxloveya.mcweapons.network;

import com.noxloveya.mcweapons.McWeapons;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: sent whenever aiming down sights starts or stops. */
public record AimPayload(boolean aiming) implements CustomPacketPayload {
	public static final Type<AimPayload> TYPE = new Type<>(McWeapons.id("aim"));
	public static final StreamCodec<ByteBuf, AimPayload> CODEC = ByteBufCodecs.BOOL.map(AimPayload::new, AimPayload::aiming);

	@Override
	public Type<AimPayload> type() {
		return TYPE;
	}
}

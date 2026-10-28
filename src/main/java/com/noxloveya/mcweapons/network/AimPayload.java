package com.noxloveya.mcweapons.network;

import com.noxloveya.mcweapons.McWeapons;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: sent whenever aiming down sights, or holding the breath while aiming, starts or stops. */
public record AimPayload(boolean aiming, boolean holdingBreath) implements CustomPacketPayload {
	public static final Type<AimPayload> TYPE = new Type<>(McWeapons.id("aim"));
	public static final StreamCodec<ByteBuf, AimPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, AimPayload::aiming,
			ByteBufCodecs.BOOL, AimPayload::holdingBreath,
			AimPayload::new);

	@Override
	public Type<AimPayload> type() {
		return TYPE;
	}
}

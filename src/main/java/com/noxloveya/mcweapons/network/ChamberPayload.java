package com.noxloveya.mcweapons.network;

import com.noxloveya.mcweapons.McWeapons;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: the draw_first of the gun in hotbar {@code slot}, never readied, reached its {@code chamber} keyframe: a round goes in. */
public record ChamberPayload(int slot) implements CustomPacketPayload {
	public static final Type<ChamberPayload> TYPE = new Type<>(McWeapons.id("chamber"));
	public static final StreamCodec<ByteBuf, ChamberPayload> CODEC = ByteBufCodecs.VAR_INT.map(ChamberPayload::new, ChamberPayload::slot);

	@Override
	public Type<ChamberPayload> type() {
		return TYPE;
	}
}

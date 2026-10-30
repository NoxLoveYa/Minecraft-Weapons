package com.noxloveya.mcweapons.network;

import com.noxloveya.mcweapons.McWeapons;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: the gun in hotbar {@code slot} was pulled out unchambered and draw_first chambered a round (clears ModComponents.UNCHAMBERED). */
public record ChamberPayload(int slot) implements CustomPacketPayload {
	public static final Type<ChamberPayload> TYPE = new Type<>(McWeapons.id("chamber"));
	public static final StreamCodec<ByteBuf, ChamberPayload> CODEC = ByteBufCodecs.VAR_INT.map(ChamberPayload::new, ChamberPayload::slot);

	@Override
	public Type<ChamberPayload> type() {
		return TYPE;
	}
}

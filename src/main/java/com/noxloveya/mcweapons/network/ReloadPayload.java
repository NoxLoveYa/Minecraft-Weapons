package com.noxloveya.mcweapons.network;

import com.noxloveya.mcweapons.McWeapons;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: reload the gun in hotbar {@code slot}. */
public record ReloadPayload(int slot) implements CustomPacketPayload {
	public static final Type<ReloadPayload> TYPE = new Type<>(McWeapons.id("reload"));
	public static final StreamCodec<ByteBuf, ReloadPayload> CODEC = ByteBufCodecs.VAR_INT.map(ReloadPayload::new, ReloadPayload::slot);

	@Override
	public Type<ReloadPayload> type() {
		return TYPE;
	}
}

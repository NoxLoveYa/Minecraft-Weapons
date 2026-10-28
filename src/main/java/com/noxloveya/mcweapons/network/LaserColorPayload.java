package com.noxloveya.mcweapons.network;

import com.noxloveya.mcweapons.McWeapons;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: the laser sight of the gun in hotbar {@code slot} shines {@code color} (0xRRGGBB). */
public record LaserColorPayload(int slot, int color) implements CustomPacketPayload {
	public static final Type<LaserColorPayload> TYPE = new Type<>(McWeapons.id("laser_color"));
	public static final StreamCodec<ByteBuf, LaserColorPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, LaserColorPayload::slot,
			ByteBufCodecs.INT, LaserColorPayload::color,
			LaserColorPayload::new);

	@Override
	public Type<LaserColorPayload> type() {
		return TYPE;
	}
}

package com.noxloveya.mcweapons.network;

import com.noxloveya.mcweapons.McWeapons;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client to server: the laser or flashlight {@code device} (item id) on the gun in hotbar {@code slot} is switched {@code on} or off. */
public record SwitchPayload(int slot, Identifier device, boolean on) implements CustomPacketPayload {
	public static final Type<SwitchPayload> TYPE = new Type<>(McWeapons.id("switch"));
	public static final StreamCodec<ByteBuf, SwitchPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, SwitchPayload::slot,
			Identifier.STREAM_CODEC, SwitchPayload::device,
			ByteBufCodecs.BOOL, SwitchPayload::on,
			SwitchPayload::new);

	@Override
	public Type<SwitchPayload> type() {
		return TYPE;
	}
}

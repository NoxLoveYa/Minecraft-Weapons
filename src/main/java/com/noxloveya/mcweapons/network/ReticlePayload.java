package com.noxloveya.mcweapons.network;

import com.noxloveya.mcweapons.McWeapons;
import com.noxloveya.mcweapons.gun.Reticle;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: draw the optic reticle of the gun in hotbar {@code slot} like {@code reticle}. */
public record ReticlePayload(int slot, Reticle reticle) implements CustomPacketPayload {
	public static final Type<ReticlePayload> TYPE = new Type<>(McWeapons.id("reticle"));
	public static final StreamCodec<ByteBuf, ReticlePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, ReticlePayload::slot,
			Reticle.STREAM_CODEC, ReticlePayload::reticle,
			ReticlePayload::new);

	@Override
	public Type<ReticlePayload> type() {
		return TYPE;
	}
}

package com.noxloveya.mcweapons.network;

import com.noxloveya.mcweapons.McWeapons;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * Client to server: fire the gun in hotbar {@code slot}. {@code seed} drives the spread cone. The shot leaves from {@code origin} (the
 * client's eye) along {@code xRot}/{@code yRot} (its aim before this shot's recoil), as the client saw them at its game time
 * {@code clientTick}; the server's copies of all three trail the client. {@code shotId} names the shot in the impact the server reports.
 */
public record ShootPayload(int slot, long clientTick, long seed, int shotId, Vec3 origin, float xRot, float yRot) implements CustomPacketPayload {
	public static final Type<ShootPayload> TYPE = new Type<>(McWeapons.id("shoot"));
	public static final StreamCodec<ByteBuf, ShootPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, ShootPayload::slot,
			ByteBufCodecs.VAR_LONG, ShootPayload::clientTick,
			ByteBufCodecs.VAR_LONG, ShootPayload::seed,
			ByteBufCodecs.VAR_INT, ShootPayload::shotId,
			Vec3.STREAM_CODEC, ShootPayload::origin,
			ByteBufCodecs.FLOAT, ShootPayload::xRot,
			ByteBufCodecs.FLOAT, ShootPayload::yRot,
			ShootPayload::new);

	@Override
	public Type<ShootPayload> type() {
		return TYPE;
	}
}

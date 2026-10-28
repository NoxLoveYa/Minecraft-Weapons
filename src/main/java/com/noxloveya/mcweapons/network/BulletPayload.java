package com.noxloveya.mcweapons.network;

import com.noxloveya.mcweapons.McWeapons;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * Server to players tracking the shooter: a bullet left their gun. It flies from {@code origin} with {@code velocity} per tick, losing
 * {@code gravity} of it downwards each tick, for at most {@code range} blocks (see Ballistics), exactly as the server steps it, so the
 * tracer drawn from this follows the bullet's real path. Also drives the shooter's third-person shot (flash, animation, smoke, casing).
 */
public record BulletPayload(int shooterId, int shotId, Vec3 origin, Vec3 velocity, float gravity, float range) implements CustomPacketPayload {
	public static final Type<BulletPayload> TYPE = new Type<>(McWeapons.id("bullet"));
	public static final StreamCodec<ByteBuf, BulletPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, BulletPayload::shooterId,
			ByteBufCodecs.VAR_INT, BulletPayload::shotId,
			Vec3.STREAM_CODEC, BulletPayload::origin,
			// The server rounds the velocity to floats before stepping (see Bullets), so this loses nothing.
			ByteBufCodecs.VECTOR3F.map(Vec3::new, Vec3::toVector3f), BulletPayload::velocity,
			ByteBufCodecs.FLOAT, BulletPayload::gravity,
			ByteBufCodecs.FLOAT, BulletPayload::range,
			BulletPayload::new);

	@Override
	public Type<BulletPayload> type() {
		return TYPE;
	}
}

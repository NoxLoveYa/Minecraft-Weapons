package com.noxloveya.mcweapons.network;

import com.noxloveya.mcweapons.McWeapons;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * Server to nearby players: the shooter's bullet {@code shotId} stopped at {@code pos}, in a living entity or else a block, while
 * flying along {@code direction}. Ends the tracer there and puffs the impact.
 */
public record BulletImpactPayload(int shooterId, int shotId, Vec3 pos, Vec3 direction, boolean entity) implements CustomPacketPayload {
	public static final Type<BulletImpactPayload> TYPE = new Type<>(McWeapons.id("bullet_impact"));
	public static final StreamCodec<ByteBuf, BulletImpactPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, BulletImpactPayload::shooterId,
			ByteBufCodecs.VAR_INT, BulletImpactPayload::shotId,
			Vec3.STREAM_CODEC, BulletImpactPayload::pos,
			ByteBufCodecs.VECTOR3F.map(Vec3::new, Vec3::toVector3f), BulletImpactPayload::direction,
			ByteBufCodecs.BOOL, BulletImpactPayload::entity,
			BulletImpactPayload::new);

	@Override
	public Type<BulletImpactPayload> type() {
		return TYPE;
	}
}

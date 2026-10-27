package com.noxloveya.mcweapons.network;

import com.noxloveya.mcweapons.McWeapons;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Server to nearby clients: a shot happened, for impact particles and later the muzzle flash and third-person shot. */
public record ShotFxPayload(int shooterId, Vec3 hitPos, HitResult.Type hitType) implements CustomPacketPayload {
	public static final Type<ShotFxPayload> TYPE = new Type<>(McWeapons.id("shot_fx"));
	public static final StreamCodec<ByteBuf, ShotFxPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, ShotFxPayload::shooterId,
			ByteBufCodecs.VECTOR3F.map(Vec3::new, Vec3::toVector3f), ShotFxPayload::hitPos,
			ByteBufCodecs.VAR_INT.map(i -> HitResult.Type.values()[i], Enum::ordinal), ShotFxPayload::hitType,
			ShotFxPayload::new);

	@Override
	public Type<ShotFxPayload> type() {
		return TYPE;
	}
}

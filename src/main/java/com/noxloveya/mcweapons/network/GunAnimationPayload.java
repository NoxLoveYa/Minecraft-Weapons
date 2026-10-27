package com.noxloveya.mcweapons.network;

import com.noxloveya.mcweapons.McWeapons;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * A gun animation other players should see. Client to server: the sender started an inspect ({@code entityId} is ignored). Server to
 * client: the player {@code entityId} started {@code action} (an inspect, or a reload the server accepted).
 */
public record GunAnimationPayload(int entityId, String action) implements CustomPacketPayload {
	public static final Type<GunAnimationPayload> TYPE = new Type<>(McWeapons.id("gun_animation"));
	public static final StreamCodec<ByteBuf, GunAnimationPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, GunAnimationPayload::entityId,
			ByteBufCodecs.stringUtf8(64), GunAnimationPayload::action,
			GunAnimationPayload::new);

	@Override
	public Type<GunAnimationPayload> type() {
		return TYPE;
	}
}

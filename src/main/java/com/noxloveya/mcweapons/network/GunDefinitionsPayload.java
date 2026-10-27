package com.noxloveya.mcweapons.network;

import com.noxloveya.mcweapons.McWeapons;
import com.noxloveya.mcweapons.gun.GunDefinition;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/** Server to client: every gun definition, sent on join and after /reload. */
public record GunDefinitionsPayload(Map<Identifier, GunDefinition> definitions) implements CustomPacketPayload {
	public static final Type<GunDefinitionsPayload> TYPE = new Type<>(McWeapons.id("gun_definitions"));
	public static final StreamCodec<ByteBuf, GunDefinitionsPayload> CODEC = ByteBufCodecs
			.<ByteBuf, Identifier, GunDefinition, Map<Identifier, GunDefinition>>map(HashMap::new, Identifier.STREAM_CODEC, ByteBufCodecs.fromCodec(GunDefinition.CODEC))
			.map(GunDefinitionsPayload::new, GunDefinitionsPayload::definitions);

	@Override
	public Type<GunDefinitionsPayload> type() {
		return TYPE;
	}
}

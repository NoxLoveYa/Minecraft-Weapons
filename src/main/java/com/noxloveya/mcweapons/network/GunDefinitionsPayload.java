package com.noxloveya.mcweapons.network;

import com.noxloveya.mcweapons.McWeapons;
import com.noxloveya.mcweapons.gun.GunDefinition;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/** Server to client: every gun definition, sent on join and after /reload. */
public record GunDefinitionsPayload(Map<ResourceLocation, GunDefinition> definitions) implements CustomPacketPayload {
	public static final Type<GunDefinitionsPayload> TYPE = new Type<>(McWeapons.id("gun_definitions"));
	public static final StreamCodec<ByteBuf, GunDefinitionsPayload> CODEC = ByteBufCodecs
			.<ByteBuf, ResourceLocation, GunDefinition, Map<ResourceLocation, GunDefinition>>map(HashMap::new, ResourceLocation.STREAM_CODEC, ByteBufCodecs.fromCodec(GunDefinition.CODEC))
			.map(GunDefinitionsPayload::new, GunDefinitionsPayload::definitions);

	@Override
	public Type<GunDefinitionsPayload> type() {
		return TYPE;
	}
}

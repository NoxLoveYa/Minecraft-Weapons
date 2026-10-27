package com.noxloveya.mcweapons.network;

import com.noxloveya.mcweapons.McWeapons;
import com.noxloveya.mcweapons.gun.GunDefinition;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client to server, from the ops-only gun tuner. RESET ignores {@code definition}. */
public record TuneGunPayload(Identifier id, GunDefinition definition, Action action) implements CustomPacketPayload {
	public static final Type<TuneGunPayload> TYPE = new Type<>(McWeapons.id("tune_gun"));
	public static final StreamCodec<ByteBuf, TuneGunPayload> CODEC = StreamCodec.composite(
			Identifier.STREAM_CODEC, TuneGunPayload::id,
			ByteBufCodecs.fromCodec(GunDefinition.CODEC), TuneGunPayload::definition,
			ByteBufCodecs.idMapper(i -> Action.values()[i], Action::ordinal), TuneGunPayload::action,
			TuneGunPayload::new);

	public enum Action {
		/** Use the values now, until the next /reload or restart. */
		APPLY,
		/** Apply, and write them into the world's tuning datapack. */
		SAVE,
		/** Delete the saved values and reload data packs, back to the gun's own file. */
		RESET
	}

	@Override
	public Type<TuneGunPayload> type() {
		return TYPE;
	}
}

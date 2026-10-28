package com.noxloveya.mcweapons.network;

import com.noxloveya.mcweapons.McWeapons;
import com.noxloveya.mcweapons.gun.AttachmentSlot;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/** Client to server: fit {@code attachment} (an attachment item id) into {@code part} of the gun in hotbar {@code slot}, or empty the part. */
public record AttachPayload(int slot, AttachmentSlot part, Optional<Identifier> attachment) implements CustomPacketPayload {
	public static final Type<AttachPayload> TYPE = new Type<>(McWeapons.id("attach"));
	public static final StreamCodec<ByteBuf, AttachPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, AttachPayload::slot,
			ByteBufCodecs.idMapper(i -> AttachmentSlot.values()[Math.floorMod(i, AttachmentSlot.values().length)], AttachmentSlot::ordinal), AttachPayload::part,
			ByteBufCodecs.optional(Identifier.STREAM_CODEC), AttachPayload::attachment,
			AttachPayload::new);

	@Override
	public Type<AttachPayload> type() {
		return TYPE;
	}
}

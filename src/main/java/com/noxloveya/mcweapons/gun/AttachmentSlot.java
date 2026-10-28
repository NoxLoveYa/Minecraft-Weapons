package com.noxloveya.mcweapons.gun;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** Where on a gun an attachment goes. Each gun takes one attachment per slot. */
public enum AttachmentSlot implements StringRepresentable {
	MUZZLE("muzzle"),
	OPTIC("optic"),
	UNDERBARREL("underbarrel"),
	MAGAZINE("magazine");

	public static final Codec<AttachmentSlot> CODEC = StringRepresentable.fromEnum(AttachmentSlot::values);

	private final String name;

	AttachmentSlot(String name) {
		this.name = name;
	}

	@Override
	public String getSerializedName() {
		return name;
	}

	/** The gun animations that put an attachment into this slot and take it out: {@code attach_<slot>} and {@code detach_<slot>}. */
	public String attachAction() {
		return "attach_" + name;
	}

	public String detachAction() {
		return "detach_" + name;
	}
}

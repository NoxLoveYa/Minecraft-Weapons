package com.noxloveya.mcweapons.gun;

import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Every attachment is an instance of this class: an item that fits one {@link AttachmentSlot} of the guns whose definition lists it.
 * On the gun model it is the bone named after its item id's path, under that slot's {@code attach_<slot>} bone. What it does to the
 * gun is set when it is registered.
 */
public class AttachmentItem extends Item {
	private static final List<AttachmentItem> ALL = new ArrayList<>();

	private final AttachmentSlot slot;
	private boolean silences;
	private int capacity;

	public AttachmentItem(AttachmentSlot slot, Properties properties) {
		super(properties);
		this.slot = slot;
		ALL.add(this);
	}

	/** Every attachment item, in registration order. */
	public static List<AttachmentItem> all() {
		return Collections.unmodifiableList(ALL);
	}

	public AttachmentSlot slot() {
		return slot;
	}

	/** A silencer: the gun fires its {@code fire_silenced} sound, heard about a third as far, and shows no muzzle flash. */
	public AttachmentItem silences() {
		this.silences = true;
		return this;
	}

	public boolean isSilencer() {
		return silences;
	}

	/** A magazine holding {@code rounds}, in place of the gun's own {@code mag_size}. */
	public AttachmentItem capacity(int rounds) {
		this.capacity = rounds;
		return this;
	}

	/** Rounds this magazine holds, or 0 when it is not a magazine. */
	public int capacity() {
		return capacity;
	}
}

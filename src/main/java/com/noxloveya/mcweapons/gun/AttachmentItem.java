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
	private float velocity = 1;
	private float recoil = 1;

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

	/** Bullets leave at {@code multiplier} times the gun's speed. */
	public AttachmentItem velocity(float multiplier) {
		this.velocity = multiplier;
		return this;
	}

	/** The gun kicks (pitch and yaw) {@code multiplier} times as much. */
	public AttachmentItem recoil(float multiplier) {
		this.recoil = multiplier;
		return this;
	}

	/** {@code definition} as this attachment changes it. */
	public GunDefinition applyTo(GunDefinition definition) {
		GunDefinition applied = capacity > 0 ? definition.withMagSize(capacity) : definition;
		if (velocity != 1) {
			applied = applied.withBullet(new BulletStats(applied.bullet().speed() * velocity, applied.bullet().gravity()));
		}
		if (recoil != 1) {
			applied = applied.withRecoil(applied.recoilPitchDeg() * recoil, applied.recoilYawDeg() * recoil);
		}
		return applied;
	}
}

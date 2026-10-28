package com.noxloveya.mcweapons.gun;

import com.noxloveya.mcweapons.registry.ModComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/** Reads and writes the attachments on a gun stack ({@link ModComponents#ATTACHMENTS}: slot name to attachment item id). */
public final class Attachments {
	private Attachments() {
	}

	public static Map<String, Identifier> of(ItemStack gun) {
		return gun.getOrDefault(ModComponents.ATTACHMENTS, Map.of());
	}

	/** The attachment in {@code slot}, or null for none (or an id that no longer names an attachment). */
	public static @Nullable AttachmentItem in(ItemStack gun, AttachmentSlot slot) {
		Identifier id = of(gun).get(slot.getSerializedName());
		return id != null && BuiltInRegistries.ITEM.getValue(id) instanceof AttachmentItem item ? item : null;
	}

	public static boolean has(ItemStack gun, Item attachment) {
		return attachment instanceof AttachmentItem item && in(gun, item.slot()) == item;
	}

	/** Puts {@code attachment} in {@code slot}, or empties it when null. */
	public static void set(ItemStack gun, AttachmentSlot slot, @Nullable AttachmentItem attachment) {
		Map<String, Identifier> attachments = new HashMap<>(of(gun));
		if (attachment == null) {
			attachments.remove(slot.getSerializedName());
		} else {
			attachments.put(slot.getSerializedName(), BuiltInRegistries.ITEM.getKey(attachment));
		}
		if (attachments.isEmpty()) {
			gun.remove(ModComponents.ATTACHMENTS);
		} else {
			gun.set(ModComponents.ATTACHMENTS, Map.copyOf(attachments));
		}
	}

	/** A silencer is fitted (see {@link AttachmentItem#silences}). */
	public static boolean silenced(ItemStack gun) {
		AttachmentItem muzzle = in(gun, AttachmentSlot.MUZZLE);
		return muzzle != null && muzzle.isSilencer();
	}

	/** {@code definition} as changed by what is fitted to {@code gun} (see {@link AttachmentItem#applyTo}). */
	public static GunDefinition apply(ItemStack gun, GunDefinition definition) {
		GunDefinition applied = definition;
		for (AttachmentSlot slot : AttachmentSlot.values()) {
			AttachmentItem attachment = in(gun, slot);
			if (attachment != null) {
				applied = attachment.applyTo(applied);
			}
		}
		return applied;
	}

	/** Whether {@code definition} lists {@code attachment} as one that fits. */
	public static boolean fits(GunDefinition definition, AttachmentItem attachment) {
		return definition.attachments().contains(BuiltInRegistries.ITEM.getKey(attachment));
	}
}

package com.noxloveya.mcweapons.gun;

import com.noxloveya.mcweapons.registry.ModComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Reads and writes the attachments on a gun stack ({@link ModComponents#ATTACHMENTS}: slot name to attachment item id). */
public final class Attachments {
	/** The eye distances the attachment menu steps through for a scope (see {@link #eyeDistance}), in blocks of the first-person view. */
	public static final List<Float> EYE_DISTANCES = List.of(0.065F, 0.0845F, 0.104F, 0.13F, 0.1625F, 0.195F, 0.26F, 0.325F, 0.39F);
	/** 100% in the menu; a scope's own default may differ ({@link AttachmentItem#eyeDistance}). */
	public static final float DEFAULT_EYE_DISTANCE = 0.13F;
	/**
	 * A 1x sight's eye distances: shares of its own ({@link AttachmentItem#defaultEyeDistance}, 1), nearer for a bigger window, further for
	 * more of the gun around it.
	 */
	public static final List<Float> SIGHT_EYE_DISTANCES = List.of(0.6F, 0.7F, 0.8F, 0.9F, 1.0F, 1.25F, 1.5F, 2.0F, 3.0F, 4.0F);

	private Attachments() {
	}

	/**
	 * How far the eye sits from {@code optic} when it is aimed on {@code gun}: behind a scope's eyepiece, in blocks; for a 1x sight, the
	 * share of its own distance from its window (see {@link #SIGHT_EYE_DISTANCES}; kept in their range, as shares saved before were of
	 * another distance).
	 */
	public static float eyeDistance(ItemStack gun, AttachmentItem optic) {
		Map<Identifier, Float> distances = gun.getOrDefault(ModComponents.EYE_DISTANCE, Map.of());
		Float set = distances.get(BuiltInRegistries.ITEM.getKey(optic));
		if (optic.isScope()) {
			return set != null ? set : optic.defaultEyeDistance();
		}
		return set != null ? Mth.clamp(set, SIGHT_EYE_DISTANCES.getFirst(), SIGHT_EYE_DISTANCES.getLast()) : 1F;
	}

	/** Blocks from the eye to {@code optic} when it is aimed on {@code gun}: a scope's eyepiece, or a 1x sight's window. */
	public static float eyeBlocks(ItemStack gun, AttachmentItem optic) {
		return optic.isScope() ? eyeDistance(gun, optic) : optic.defaultEyeDistance() * eyeDistance(gun, optic);
	}

	/** The choices for {@code optic}'s {@link #eyeDistance}. */
	public static List<Float> eyeDistances(AttachmentItem optic) {
		return optic.isScope() ? EYE_DISTANCES : SIGHT_EYE_DISTANCES;
	}

	/** Sets {@link #eyeDistance}, kept within the range of its {@link #eyeDistances}. Looks only, so whoever sends it may. */
	public static void setEyeDistance(ItemStack gun, AttachmentItem optic, float distance) {
		Map<Identifier, Float> distances = new HashMap<>(gun.getOrDefault(ModComponents.EYE_DISTANCE, Map.<Identifier, Float>of()));
		List<Float> range = eyeDistances(optic);
		distances.put(BuiltInRegistries.ITEM.getKey(optic), Float.isFinite(distance)
				? Mth.clamp(distance, range.getFirst(), range.getLast()) : optic.isScope() ? optic.defaultEyeDistance() : 1F);
		gun.set(ModComponents.EYE_DISTANCE, Map.copyOf(distances));
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

	/** How {@code gun}'s optic draws its reticle: as set in the attachment menu, or until then in the fitted optic's own shape. */
	public static Reticle reticle(ItemStack gun) {
		Reticle set = gun.get(ModComponents.RETICLE);
		AttachmentItem optic = in(gun, AttachmentSlot.OPTIC);
		if (set != null || optic == null) {
			return set != null ? set : Reticle.DEFAULT;
		}
		return new Reticle(Reticle.DEFAULT.color(), Reticle.DEFAULT.size(), optic.defaultReticle());
	}

	/** A silencer is fitted (see {@link AttachmentItem#silences}). */
	public static boolean silenced(ItemStack gun) {
		AttachmentItem muzzle = in(gun, AttachmentSlot.MUZZLE);
		return muzzle != null && muzzle.isSilencer();
	}

	/** The fitted attachments that switch on and off, lasers and flashlights, in slot order. */
	public static List<AttachmentItem> switchable(ItemStack gun) {
		List<AttachmentItem> devices = new ArrayList<>();
		for (AttachmentSlot slot : AttachmentSlot.values()) {
			AttachmentItem attachment = in(gun, slot);
			if (attachment != null && (attachment.isLaser() || attachment.isFlashlight())) {
				devices.add(attachment);
			}
		}
		return devices;
	}

	/** Whether {@code device} (a laser, a flashlight) shines on {@code gun}: they all do until switched off ({@link ModComponents#SWITCHED_OFF}). */
	public static boolean switchedOn(ItemStack gun, AttachmentItem device) {
		return !gun.getOrDefault(ModComponents.SWITCHED_OFF, List.of()).contains(BuiltInRegistries.ITEM.getKey(device));
	}

	/** Switches {@code device} on or off. Looks only (its stats stay), so whoever sends it may. */
	public static void switchOn(ItemStack gun, AttachmentItem device, boolean on) {
		Identifier id = BuiltInRegistries.ITEM.getKey(device);
		List<Identifier> off = new ArrayList<>(gun.getOrDefault(ModComponents.SWITCHED_OFF, List.of()));
		off.remove(id);
		if (!on) {
			off.add(id);
		}
		if (off.isEmpty()) {
			gun.remove(ModComponents.SWITCHED_OFF);
		} else {
			gun.set(ModComponents.SWITCHED_OFF, List.copyOf(off));
		}
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

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
	/** A scope's eye distance unless it sets its own ({@link AttachmentItem#eyeDistance}), in blocks of the first-person view. */
	public static final float DEFAULT_EYE_DISTANCE = 0.13F;
	/** The eye distance the attachment menu's slider spans, as shares of an optic's own: half to twice, 1 (its own) in the middle. */
	public static final float MIN_EYE_SHARE = 0.5F;
	public static final float MAX_EYE_SHARE = 2F;

	private Attachments() {
	}

	/** How far the eye sits from {@code optic} when it is aimed on {@code gun}, as a share of its own distance: 1 until the holder moves it. */
	public static float eyeShare(ItemStack gun, AttachmentItem optic) {
		return gun.getOrDefault(ModComponents.EYE_DISTANCE_SHARE, Map.<Identifier, Float>of()).getOrDefault(BuiltInRegistries.ITEM.getKey(optic), 1F);
	}

	/** Blocks from the eye to {@code optic} when it is aimed on {@code gun}: a scope's eyepiece, or a 1x sight's window. */
	public static float eyeBlocks(ItemStack gun, AttachmentItem optic) {
		return optic.defaultEyeDistance() * eyeShare(gun, optic);
	}

	/** Sets {@link #eyeShare}, kept within the slider's range. Looks only, so whoever sends it may. */
	public static void setEyeShare(ItemStack gun, AttachmentItem optic, float share) {
		Map<Identifier, Float> shares = new HashMap<>(gun.getOrDefault(ModComponents.EYE_DISTANCE_SHARE, Map.<Identifier, Float>of()));
		shares.put(BuiltInRegistries.ITEM.getKey(optic), Float.isFinite(share) ? Mth.clamp(share, MIN_EYE_SHARE, MAX_EYE_SHARE) : 1F);
		gun.set(ModComponents.EYE_DISTANCE_SHARE, Map.copyOf(shares));
		// The distances kept before the shares (in blocks for a scope, in shares of another distance for a sight) are not read any more.
		gun.remove(ModComponents.EYE_DISTANCE);
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

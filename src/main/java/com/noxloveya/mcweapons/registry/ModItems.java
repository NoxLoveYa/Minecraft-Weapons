package com.noxloveya.mcweapons.registry;

import com.noxloveya.mcweapons.McWeapons;
import com.noxloveya.mcweapons.gun.AttachmentItem;
import com.noxloveya.mcweapons.gun.AttachmentSlot;
import com.noxloveya.mcweapons.gun.GunItem;
import com.noxloveya.mcweapons.gun.Reticle;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.function.UnaryOperator;

public final class ModItems {
	public static final GunItem GLOCK17 = gun("glock17");
	public static final GunItem MOSIN_NAGANT = gun("mosin_nagant");
	public static final GunItem M4A1 = gun("m4a1");
	// Gas held back by the baffles: the bullet leaves a little slower and the gun kicks less.
	public static final AttachmentItem SILENCER = attachment("silencer", AttachmentSlot.MUZZLE, item -> item.silences().velocity(0.93F).recoil(0.85F));
	// On the model the trijicon_rmr_reticle locator becomes the sight (see GunRenderer.aimBone); the magnified ones are looked through (ScopeRenderer).
	// A 1x sight's eye distance is the user's 125% of the one that put its window (the RMR's 21 x 13 mm, the M4's about 41 x 26 mm, to the
	// front of the frame) as large as Black Ops 6's sights at their default settings, measured at FOV 70 (spec 01, "1x sights like recent Call
	// of Duty", corrected references): the window about 10% of the view across for the RMR and the EXPS3. The Coyote is at 9% by 11%.
	// The tube sights (the Aimpoints) sit further out, so the eye stays behind the tube's back end: their window is smaller, as a real one is.
	// The CompM4, CompM2 and EXPS3 are shorter and thinner-walled than the real ones, the Aimpoints' glass at the eye's end, so more of the
	// view is glass.
	public static final AttachmentItem TRIJICON_RMR = attachment("trijicon_rmr", AttachmentSlot.OPTIC, item -> item.zoom(1.25F).eyeDistance(0.28F));
	public static final AttachmentItem AIMPOINT_COMPM2 = attachment("aimpoint_compm2", AttachmentSlot.OPTIC, item -> item.zoom(1.25F).eyeDistance(0.75F));
	public static final AttachmentItem SCOUT_SCOPE = attachment("scout_scope", AttachmentSlot.OPTIC, item -> item.zoom(2).scope());
	public static final AttachmentItem PU_SCOPE = attachment("pu_scope", AttachmentSlot.OPTIC, item -> item.zoom(3).scope().eyeDistance(0.104F));
	public static final AttachmentItem ACOG = attachment("acog", AttachmentSlot.OPTIC, item -> item.zoom(4).scope().eyeDistance(0.104F));
	public static final AttachmentItem SNIPER_SCOPE = attachment("sniper_scope", AttachmentSlot.OPTIC, item -> item.zoom(4).scope());
	public static final AttachmentItem VARIABLE_SCOPE = attachment("variable_scope", AttachmentSlot.OPTIC, item -> item.zoom(6, 10).scope());
	public static final AttachmentItem LONG_RANGE_SCOPE = attachment("long_range_scope", AttachmentSlot.OPTIC, item -> item.zoom(10, 16).scope());
	public static final AttachmentItem EXTENDED_MAG = attachment("extended_mag", AttachmentSlot.MAGAZINE, item -> item.capacity(33));
	// On the dust cover rail: the dot shows where a hip shot goes, so hip fire tightens, and the sights come up faster.
	public static final AttachmentItem LASER = attachment("laser", AttachmentSlot.UNDERBARREL, item -> item.laser().spreadHip(0.6F).aimTime(0.67F));
	// Rifle attachments (the M4A1). A brake throws the gas sideways: less kick, and no quieter.
	public static final AttachmentItem MUZZLE_BRAKE = attachment("muzzle_brake", AttachmentSlot.MUZZLE, item -> item.recoil(0.75F));
	public static final AttachmentItem EXTENDED_MAG_45 = attachment("extended_mag_45", AttachmentSlot.MAGAZINE, item -> item.capacity(45));
	// A vertical grip holds the muzzle down; an angled one brings the gun up faster.
	public static final AttachmentItem VERTICAL_GRIP = attachment("vertical_grip", AttachmentSlot.GRIP, item -> item.recoil(0.8F).spreadHip(0.9F));
	public static final AttachmentItem ANGLED_GRIP = attachment("angled_grip", AttachmentSlot.GRIP, item -> item.recoil(0.9F).aimTime(0.8F));
	public static final AttachmentItem COYOTE_SIGHT = attachment("coyote_sight", AttachmentSlot.OPTIC, item -> item.zoom(1.25F).eyeDistance(0.640625F));
	public static final AttachmentItem AIMPOINT_COMPM4 = attachment("aimpoint_compm4", AttachmentSlot.OPTIC, item -> item.zoom(1.25F).eyeDistance(0.6F));
	public static final AttachmentItem EOTECH_EXPS3 = attachment("eotech_exps3", AttachmentSlot.OPTIC, item -> item.zoom(1.25F).reticle(Reticle.Shape.CIRCLE).eyeDistance(0.55F));
	public static final AttachmentItem PRISM_SCOPE = attachment("prism_scope", AttachmentSlot.OPTIC, item -> item.zoom(2.5F).scope().eyeDistance(0.104F));
	public static final AttachmentItem FLASHLIGHT = attachment("flashlight", AttachmentSlot.LIGHT, AttachmentItem::flashlight);

	public static final CreativeModeTab TAB = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, McWeapons.id("guns"),
			FabricCreativeModeTab.builder()
					.title(Component.translatable("itemGroup.mcweapons"))
					.icon(() -> new ItemStack(GLOCK17))
					.displayItems((parameters, output) -> BuiltInRegistries.ITEM.stream()
							.filter(item -> item instanceof GunItem || item instanceof AttachmentItem).forEach(output::accept))
					.build());

	private ModItems() {
	}

	public static void init() {
	}

	/** Registers a gun whose item id and definition id are both {@code mcweapons:<name>}. */
	private static GunItem gun(String name) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, McWeapons.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, new GunItem(McWeapons.id(name), new Item.Properties().setId(key).stacksTo(1)));
	}

	/** Registers an attachment for {@code slot} with what it does ({@code effects}); its model bone is {@code name}. */
	private static AttachmentItem attachment(String name, AttachmentSlot slot, UnaryOperator<AttachmentItem> effects) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, McWeapons.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, effects.apply(new AttachmentItem(slot, new Item.Properties().setId(key).stacksTo(16))));
	}
}

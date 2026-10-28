package com.noxloveya.mcweapons.registry;

import com.noxloveya.mcweapons.McWeapons;
import com.noxloveya.mcweapons.gun.AttachmentItem;
import com.noxloveya.mcweapons.gun.AttachmentSlot;
import com.noxloveya.mcweapons.gun.GunItem;
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
	// On the model the red_dot_reticle locator becomes the sight (see GunRenderer.aimBone); the magnified ones are looked through (ScopeRenderer).
	public static final AttachmentItem RED_DOT = attachment("red_dot", AttachmentSlot.OPTIC, item -> item.zoom(1.25F));
	public static final AttachmentItem SCOUT_SCOPE = attachment("scout_scope", AttachmentSlot.OPTIC, item -> item.zoom(2).scope());
	public static final AttachmentItem PU_SCOPE = attachment("pu_scope", AttachmentSlot.OPTIC, item -> item.zoom(3).scope().eyeDistance(0.104F));
	public static final AttachmentItem ACOG = attachment("acog", AttachmentSlot.OPTIC, item -> item.zoom(4).scope().eyeDistance(0.104F));
	public static final AttachmentItem SNIPER_SCOPE = attachment("sniper_scope", AttachmentSlot.OPTIC, item -> item.zoom(4).scope());
	public static final AttachmentItem VARIABLE_SCOPE = attachment("variable_scope", AttachmentSlot.OPTIC, item -> item.zoom(6, 10).scope());
	public static final AttachmentItem EXTENDED_MAG = attachment("extended_mag", AttachmentSlot.MAGAZINE, item -> item.capacity(33));
	// On the dust cover rail: the dot shows where a hip shot goes, so hip fire tightens, and the sights come up faster.
	public static final AttachmentItem LASER = attachment("laser", AttachmentSlot.UNDERBARREL, item -> item.laser().spreadHip(0.6F).aimTime(0.67F));

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

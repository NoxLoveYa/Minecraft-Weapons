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
	// Gas held back by the baffles: the bullet leaves a little slower and the gun kicks less.
	public static final AttachmentItem SILENCER = attachment("silencer", AttachmentSlot.MUZZLE, item -> item.silences().velocity(0.93F).recoil(0.85F));
	// Its effect is on the model: the red_dot_reticle locator becomes the sight (see GunRenderer.aimBone).
	public static final AttachmentItem RED_DOT = attachment("red_dot", AttachmentSlot.OPTIC, UnaryOperator.identity());
	public static final AttachmentItem EXTENDED_MAG = attachment("extended_mag", AttachmentSlot.MAGAZINE, item -> item.capacity(33));

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

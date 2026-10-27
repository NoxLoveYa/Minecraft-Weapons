package com.noxloveya.mcweapons.registry;

import com.noxloveya.mcweapons.McWeapons;
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

public final class ModItems {
	public static final GunItem GLOCK17 = gun("glock17");

	public static final CreativeModeTab TAB = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, McWeapons.id("guns"),
			FabricCreativeModeTab.builder()
					.title(Component.translatable("itemGroup.mcweapons"))
					.icon(() -> new ItemStack(GLOCK17))
					.displayItems((parameters, output) -> BuiltInRegistries.ITEM.stream().filter(item -> item instanceof GunItem).forEach(output::accept))
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
}

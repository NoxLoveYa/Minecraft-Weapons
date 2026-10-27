package com.noxloveya.mcweapons.registry;

import com.noxloveya.mcweapons.McWeapons;
import com.noxloveya.mcweapons.gun.GunItem;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class ModItems {
	public static final GunItem GLOCK17 = gun("glock17");

	public static final CreativeModeTab TAB = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, McWeapons.id("guns"),
			FabricItemGroup.builder()
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
		return Registry.register(BuiltInRegistries.ITEM, McWeapons.id(name), new GunItem(McWeapons.id(name), new Item.Properties().stacksTo(1)));
	}
}

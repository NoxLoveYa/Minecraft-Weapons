package com.noxloveya.mcweapons;

import com.noxloveya.mcweapons.gun.GunDefinitions;
import com.noxloveya.mcweapons.gun.GunItem;
import com.noxloveya.mcweapons.network.ServerHandlers;
import com.noxloveya.mcweapons.registry.ModComponents;
import com.noxloveya.mcweapons.registry.ModItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class McWeapons implements ModInitializer {
	public static final String MOD_ID = "mcweapons";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModComponents.init();
		ModItems.init();
		ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new GunDefinitions());
		GunItem.registerEvents();
		ServerHandlers.register();
	}

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}
}

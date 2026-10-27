package com.noxloveya.mcweapons.gun;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.noxloveya.mcweapons.McWeapons;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads {@code data/<namespace>/guns/*.json} on the server. Clients get the same map from
 * {@link com.noxloveya.mcweapons.network.GunDefinitionsPayload} on join and on /reload.
 */
public class GunDefinitions extends SimpleJsonResourceReloadListener implements IdentifiableResourceReloadListener {
	private static final ResourceLocation ID = McWeapons.id("guns");
	// Shared by the integrated server and the client in singleplayer; both write the same content.
	private static volatile Map<ResourceLocation, GunDefinition> definitions = Map.of();
	private static final Set<ResourceLocation> warned = ConcurrentHashMap.newKeySet();

	public GunDefinitions() {
		super(new Gson(), "guns");
	}

	@Override
	public ResourceLocation getFabricId() {
		return ID;
	}

	@Override
	protected void apply(Map<ResourceLocation, JsonElement> jsons, ResourceManager resourceManager, ProfilerFiller profiler) {
		Map<ResourceLocation, GunDefinition> loaded = new HashMap<>();
		jsons.forEach((id, json) -> GunDefinition.CODEC.parse(JsonOps.INSTANCE, json)
				.resultOrPartial(error -> McWeapons.LOGGER.error("Bad gun definition {}: {}", id, error))
				.ifPresent(definition -> loaded.put(id, definition)));
		set(loaded);
		McWeapons.LOGGER.info("Loaded {} gun definitions", loaded.size());
	}

	public static Map<ResourceLocation, GunDefinition> all() {
		return definitions;
	}

	public static void set(Map<ResourceLocation, GunDefinition> loaded) {
		definitions = Map.copyOf(loaded);
		warned.clear();
	}

	/** Returns null, and logs once per id, when the definition is missing. Stays quiet before anything is loaded or synced. */
	public static @Nullable GunDefinition get(ResourceLocation id) {
		Map<ResourceLocation, GunDefinition> current = definitions;
		GunDefinition definition = current.get(id);
		if (definition == null && !current.isEmpty() && warned.add(id)) {
			McWeapons.LOGGER.warn("No gun definition for {}, the gun will do nothing", id);
		}
		return definition;
	}
}

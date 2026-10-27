package com.noxloveya.mcweapons.gun;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.noxloveya.mcweapons.McWeapons;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
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
 * Files that fail to parse are logged and skipped by vanilla.
 */
public class GunDefinitions extends SimpleJsonResourceReloadListener<GunDefinition> {
	public static final Identifier ID = McWeapons.id("guns");
	private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().create();
	// Shared by the integrated server and the client in singleplayer; both write the same content.
	private static volatile Map<Identifier, GunDefinition> definitions = Map.of();
	private static final Set<Identifier> warned = ConcurrentHashMap.newKeySet();

	public GunDefinitions() {
		super(GunDefinition.CODEC, FileToIdConverter.json("guns"));
	}

	@Override
	protected void apply(Map<Identifier, GunDefinition> loaded, ResourceManager resourceManager, ProfilerFiller profiler) {
		set(loaded);
		McWeapons.LOGGER.info("Loaded {} gun definitions", loaded.size());
	}

	public static Map<Identifier, GunDefinition> all() {
		return definitions;
	}

	public static void set(Map<Identifier, GunDefinition> loaded) {
		definitions = Map.copyOf(loaded);
		warned.clear();
	}

	/** Swaps in one tuned definition. The caller syncs clients. */
	public static void put(Identifier id, GunDefinition definition) {
		Map<Identifier, GunDefinition> copy = new HashMap<>(definitions);
		copy.put(id, definition);
		definitions = Map.copyOf(copy);
	}

	/** The definition as its json file would hold it, with "item" first. */
	public static JsonObject toJson(Identifier id, GunDefinition definition) {
		JsonObject json = new JsonObject();
		BuiltInRegistries.ITEM.stream().filter(item -> item instanceof GunItem gun && gun.definitionId().equals(id)).findFirst()
				.ifPresent(item -> json.addProperty("item", BuiltInRegistries.ITEM.getKey(item).toString()));
		GunDefinition.CODEC.encodeStart(JsonOps.INSTANCE, definition).getOrThrow().getAsJsonObject().asMap().forEach(json::add);
		return json;
	}

	public static String toJsonText(Identifier id, GunDefinition definition) {
		return PRETTY.toJson(toJson(id, definition));
	}

	/** Returns null, and logs once per id, when the definition is missing. Stays quiet before anything is loaded or synced. */
	public static @Nullable GunDefinition get(Identifier id) {
		Map<Identifier, GunDefinition> current = definitions;
		GunDefinition definition = current.get(id);
		if (definition == null && !current.isEmpty() && warned.add(id)) {
			McWeapons.LOGGER.warn("No gun definition for {}, the gun will do nothing", id);
		}
		return definition;
	}
}

package com.noxloveya.mcweapons.gun;

import com.noxloveya.mcweapons.McWeapons;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.Nullable;

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

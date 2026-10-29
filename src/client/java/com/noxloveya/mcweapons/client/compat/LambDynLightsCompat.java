package com.noxloveya.mcweapons.client.compat;

import com.noxloveya.mcweapons.client.render.Lasers;
import com.noxloveya.mcweapons.client.render.MuzzleLight;
import dev.lambdaurora.lambdynlights.api.DynamicLightsContext;
import dev.lambdaurora.lambdynlights.api.DynamicLightsInitializer;
import dev.lambdaurora.lambdynlights.api.behavior.DynamicLightBehaviorManager;
import dev.lambdaurora.lambdynlights.api.behavior.LineLightBehavior;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The muzzle light through LambDynamicLights, whose entrypoint ({@code lambdynlights:initializer}) is the only way this class is
 * loaded, so the mod runs the same without it. A shot lights a point at its muzzle for a few ticks; firing again moves and renews the
 * shooter's one light instead of adding another, so full auto does not pile up light sources.
 * <p>
 * LambDynamicLights only relights the world by rebuilding chunk sections, and it does that for a light every 5 or 10 ticks in its
 * fast and fastest modes, and only for sections it has already tracked. A flash a few ticks long was drawn late, in the wrong place, and
 * often never cleared. So the sections the flash reaches are marked for rebuild here: once the light is in place, and again once it is
 * gone. Its light lookup is recomputed every level tick whatever the mode, so a rebuild always shows the current lights.
 * <p>
 * Flashlights the same way: one light per holder, kept where the beam lands (see {@link Lasers#lightSpots}).
 */
public final class LambDynLightsCompat implements DynamicLightsInitializer {
	// A gunshot flash lights a room, for a moment.
	private static final int LUMINANCE = 14;
	private static final int LIGHT_TICKS = 2;
	// LambDynamicLights fades light 15/7.75 per block, so luminance 14 reaches about 7.2 blocks; one more for smooth lighting's samples.
	private static final int REACH = 8;

	private static final class Light {
		final LineLightBehavior behavior;
		Vec3 pos;
		int ticksLeft;

		Light(LineLightBehavior behavior, Vec3 pos) {
			this.behavior = behavior;
			this.pos = pos;
		}
	}

	// A flashlight lights where its beam lands, less than a flash; it follows the beam every tick, moved once its spot shifts a tenth of a
	// block (each move rebuilds the sections it reaches, so a still beam costs nothing).
	private static final int SPOT_LUMINANCE = 12;
	private static final double SPOT_MOVE = 0.1;

	private final Map<Integer, Light> lights = new HashMap<>();
	/** Each flashlight's light, by holder entity id. */
	private final Map<Integer, Light> spots = new HashMap<>();
	/** Where lights were added, moved or removed: the sections around them are rebuilt at the end of the client tick. */
	private final List<Vec3> dirty = new ArrayList<>();

	@Override
	public void onInitializeDynamicLights(DynamicLightsContext context) {
		DynamicLightBehaviorManager manager = context.dynamicLightBehaviorManager();
		// Shots come in on the start of a client tick or in packets before it, so the light is in the level tick's lookup by the end.
		MuzzleLight.hook((shooterId, muzzle) -> {
			Light light = lights.get(shooterId);
			if (light == null) {
				// A line from a point to itself is a point light.
				light = new Light(new LineLightBehavior(new Vector3d(muzzle.x, muzzle.y, muzzle.z), new Vector3d(muzzle.x, muzzle.y, muzzle.z), LUMINANCE), muzzle);
				manager.add(light.behavior);
				lights.put(shooterId, light);
			} else {
				dirty.add(light.pos);
				light.behavior.setStartPoint(muzzle.x, muzzle.y, muzzle.z);
				light.behavior.setEndPoint(muzzle.x, muzzle.y, muzzle.z);
				light.pos = muzzle;
			}
			dirty.add(muzzle);
			light.ticksLeft = LIGHT_TICKS;
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			// Out of the world (or into another one) every light goes, as LambDynamicLights drops its sources there too.
			if (client.level == null) {
				lights.values().forEach(light -> manager.remove(light.behavior));
				lights.clear();
				spots.values().forEach(light -> manager.remove(light.behavior));
				spots.clear();
				dirty.clear();
				return;
			}
			// Paused, the level does not tick, so the lookup is not recomputed and the flash waits.
			if (client.isPaused()) {
				return;
			}
			dirty.forEach(pos -> rebuildAround(client, pos));
			dirty.clear();
			// Removed now, the light leaves the lookup on the next level tick, so its sections are rebuilt at the end of the next client tick.
			lights.values().removeIf(light -> {
				if (--light.ticksLeft > 0) {
					return false;
				}
				manager.remove(light.behavior);
				dirty.add(light.pos);
				return true;
			});
			// Flashlights change at the end of the tick, after this tick's lookup, so like a removed flash their sections are rebuilt next tick.
			Map<Integer, Vec3> wanted = Lasers.lightSpots(client);
			spots.entrySet().removeIf(entry -> {
				if (wanted.containsKey(entry.getKey())) {
					return false;
				}
				manager.remove(entry.getValue().behavior);
				dirty.add(entry.getValue().pos);
				return true;
			});
			wanted.forEach((id, pos) -> {
				Light light = spots.get(id);
				if (light == null) {
					light = new Light(new LineLightBehavior(new Vector3d(pos.x, pos.y, pos.z), new Vector3d(pos.x, pos.y, pos.z), SPOT_LUMINANCE), pos);
					manager.add(light.behavior);
					spots.put(id, light);
				} else if (light.pos.distanceToSqr(pos) > SPOT_MOVE * SPOT_MOVE) {
					dirty.add(light.pos);
					light.behavior.setStartPoint(pos.x, pos.y, pos.z);
					light.behavior.setEndPoint(pos.x, pos.y, pos.z);
					light.pos = pos;
				} else {
					return;
				}
				dirty.add(pos);
			});
		});
	}

	/** Marks every chunk section within {@link #REACH} of {@code pos} for rebuild, the way LambDynamicLights does. */
	private static void rebuildAround(Minecraft client, Vec3 pos) {
		int x0 = SectionPos.blockToSectionCoord(Mth.floor(pos.x) - REACH);
		int y0 = SectionPos.blockToSectionCoord(Mth.floor(pos.y) - REACH);
		int z0 = SectionPos.blockToSectionCoord(Mth.floor(pos.z) - REACH);
		int x1 = SectionPos.blockToSectionCoord(Mth.floor(pos.x) + REACH);
		int y1 = SectionPos.blockToSectionCoord(Mth.floor(pos.y) + REACH);
		int z1 = SectionPos.blockToSectionCoord(Mth.floor(pos.z) + REACH);
		for (int x = x0; x <= x1; x++) {
			for (int y = y0; y <= y1; y++) {
				for (int z = z0; z <= z1; z++) {
					client.levelExtractor.setSectionDirty(x, y, z);
				}
			}
		}
	}
}

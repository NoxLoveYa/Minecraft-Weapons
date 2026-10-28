package com.noxloveya.mcweapons.client.compat;

import com.noxloveya.mcweapons.client.render.MuzzleLight;
import dev.lambdaurora.lambdynlights.api.DynamicLightsContext;
import dev.lambdaurora.lambdynlights.api.DynamicLightsInitializer;
import dev.lambdaurora.lambdynlights.api.behavior.DynamicLightBehaviorManager;
import dev.lambdaurora.lambdynlights.api.behavior.LineLightBehavior;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Map;

/**
 * The muzzle light through LambDynamicLights, whose entrypoint ({@code lambdynlights:initializer}) is the only way this class is
 * loaded, so the mod runs the same without it. A shot lights a point at its muzzle for a few ticks; firing again moves and renews the
 * shooter's one light instead of adding another, so full auto does not pile up light sources or relight chunks for each round.
 */
public final class LambDynLightsCompat implements DynamicLightsInitializer {
	// A gunshot flash lights a room; a few ticks, a little longer than the drawn flash, so the light updates catch it.
	private static final int LUMINANCE = 14;
	private static final int LIGHT_TICKS = 3;

	private static final class Light {
		final LineLightBehavior behavior;
		int ticksLeft;

		Light(LineLightBehavior behavior) {
			this.behavior = behavior;
		}
	}

	private final Map<Integer, Light> lights = new HashMap<>();

	@Override
	public void onInitializeDynamicLights(DynamicLightsContext context) {
		DynamicLightBehaviorManager manager = context.dynamicLightBehaviorManager();
		MuzzleLight.hook((shooterId, muzzle) -> {
			Light light = lights.get(shooterId);
			if (light == null) {
				// A line from a point to itself is a point light.
				light = new Light(new LineLightBehavior(new Vector3d(muzzle.x, muzzle.y, muzzle.z), new Vector3d(muzzle.x, muzzle.y, muzzle.z), LUMINANCE));
				manager.add(light.behavior);
				lights.put(shooterId, light);
			} else {
				light.behavior.setStartPoint(muzzle.x, muzzle.y, muzzle.z);
				light.behavior.setEndPoint(muzzle.x, muzzle.y, muzzle.z);
			}
			light.ticksLeft = LIGHT_TICKS;
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			// Out of the world (or into another one) every light goes, as LambDynamicLights drops its sources there too.
			boolean inWorld = client.level != null;
			lights.values().removeIf(light -> {
				if (inWorld && --light.ticksLeft > 0) {
					return false;
				}
				manager.remove(light.behavior);
				return true;
			});
		});
	}
}

package com.noxloveya.mcweapons.client.render;

import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.function.BiConsumer;

/**
 * Lights the world around a gun's muzzle for a moment when it fires. It does nothing unless a dynamic lights mod has hooked in (see
 * compat.LambDynLightsCompat), so nothing here may refer to one.
 */
public final class MuzzleLight {
	private static @Nullable BiConsumer<Integer, Vec3> lights;

	private MuzzleLight() {
	}

	/** Called by the dynamic lights integration with what lights a shooter's muzzle at a point. */
	public static void hook(BiConsumer<Integer, Vec3> lights) {
		MuzzleLight.lights = lights;
	}

	/** {@code shooterId} fired with its muzzle at {@code muzzle}. Silenced guns have no flash, so callers leave those out. */
	public static void flash(int shooterId, Vec3 muzzle) {
		if (lights != null) {
			lights.accept(shooterId, muzzle);
		}
	}
}

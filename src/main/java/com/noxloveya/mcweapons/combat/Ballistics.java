package com.noxloveya.mcweapons.combat;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * A bullet's flight, shared by the server (which hits things along it) and clients (which draw the tracer along it), so both follow the
 * exact same path. A bullet moves in one straight step per tick, then gravity takes {@code gravity} off its vertical velocity.
 */
public final class Ballistics {
	private Ballistics() {
	}

	/** Where a shot goes: a uniform point in a cone around the aim whose half-angle is the spread. The same seed gives the same direction. */
	public static Vec3 direction(float xRot, float yRot, long seed, float spreadDeg) {
		RandomSource random = RandomSource.create(seed);
		float spread = spreadDeg * Mth.sqrt(random.nextFloat());
		float angle = random.nextFloat() * Mth.TWO_PI;
		return Vec3.directionFromRotation(xRot + spread * Mth.sin(angle), yRot + spread * Mth.cos(angle));
	}

	/** Where the bullet is after {@code step} ticks, in closed form so both sides land on the same point however they got there. */
	public static Vec3 position(Vec3 origin, Vec3 velocity, double gravity, int step) {
		return origin.add(velocity.x * step, velocity.y * step - gravity * step * (step - 1) / 2.0, velocity.z * step);
	}
}

package com.noxloveya.mcweapons.combat;

import com.noxloveya.mcweapons.gun.BulletStats;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BallisticsTest {
	@Test
	void closedFormMatchesSteppingTickByTick() {
		Vec3 origin = new Vec3(10.5, 64.62, -3.25);
		Vec3 velocity = new Vec3(3.0, 1.5, -18.0);
		double gravity = BulletStats.DEFAULT.gravityPerTick();
		Vec3 position = origin;
		Vec3 stepVelocity = velocity;
		for (int step = 0; step <= 40; step++) {
			assertEquals(0, position.distanceTo(Ballistics.position(origin, velocity, gravity, step)), 1.0E-9, "step " + step);
			position = position.add(stepVelocity);
			stepVelocity = stepVelocity.subtract(0, gravity, 0);
		}
	}

	@Test
	void aLevelShotDropsLikeAFallingBody() {
		// 9mm at 375 blocks/s fired level for 1 second (20 ticks): about 375 blocks out and half of g t^2 down, less half a tick's worth
		// because each tick's step is straight and gravity bends the velocity between steps.
		BulletStats bullet = BulletStats.DEFAULT;
		Vec3 end = Ballistics.position(Vec3.ZERO, new Vec3(0, 0, bullet.speedPerTick()), bullet.gravityPerTick(), 20);
		assertEquals(375, end.z, 1.0E-9);
		assertEquals(-0.5 * bullet.gravity() * (20 * 19) / 400.0, end.y, 1.0E-9);
		assertEquals(-4.66, end.y, 0.01);
	}

	@Test
	void spreadStaysInItsConeAndRepeatsForTheSameSeed() {
		Vec3 aim = Vec3.directionFromRotation(10, 45);
		assertEquals(0, Ballistics.direction(10, 45, 42, 0).distanceTo(aim), 1.0E-6, "no spread is the aim itself");
		for (long seed = 0; seed < 200; seed++) {
			Vec3 shot = Ballistics.direction(10, 45, seed, 2);
			double angle = Math.toDegrees(Math.acos(Math.min(1, shot.dot(aim))));
			assertTrue(angle <= 2.05, "seed " + seed + " leaves the cone: " + angle);
			assertEquals(0, shot.distanceTo(Ballistics.direction(10, 45, seed, 2)), 0, "the client and the server get the same direction");
		}
	}
}

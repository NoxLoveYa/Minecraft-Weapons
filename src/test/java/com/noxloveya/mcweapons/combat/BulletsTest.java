package com.noxloveya.mcweapons.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BulletsTest {
	@Test
	void headZoneIsTheTopOfTheBody() {
		// Player standing at y 0: eyes at 1.62 of 1.8, so the head zone starts at 1.30.
		assertTrue(Bullets.inHeadZone(1.5, 1.62, 1.8F));
		assertTrue(Bullets.inHeadZone(1.8, 1.62, 1.8F), "the top of the box is the head");
		assertFalse(Bullets.inHeadZone(1.2, 1.62, 1.8F), "the chest is not");
		// Zombie: eyes at 1.74 of 1.95, head zone from about 1.39.
		assertTrue(Bullets.inHeadZone(1.45, 1.74, 1.95F));
		assertFalse(Bullets.inHeadZone(1.3, 1.74, 1.95F));
		// Crouching player: eyes at 1.27 of 1.5, head zone from about 1.0, so a standing chest height is now the head.
		assertTrue(Bullets.inHeadZone(1.1, 1.27, 1.5F));
		assertFalse(Bullets.inHeadZone(0.9, 1.27, 1.5F));
	}

	@Test
	void shotsAreRewoundToWhatTheShooterSawWithinLimits() {
		// A client draws entities INTERP_TICKS behind its own game time.
		assertEquals(1000 - Bullets.INTERP_TICKS, Bullets.viewTick(1000, 1003));
		// A client clock ahead of the server (a world change, a forged packet) cannot see the future.
		assertEquals(1003, Bullets.viewTick(1100, 1003));
		// Nor can it claim a world older than the rewind limit.
		assertEquals(1003 - Bullets.MAX_REWIND_TICKS, Bullets.viewTick(0, 1003));
	}
}

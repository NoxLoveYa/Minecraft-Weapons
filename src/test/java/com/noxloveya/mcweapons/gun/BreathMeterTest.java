package com.noxloveya.mcweapons.gun;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BreathMeterTest {
	@Test
	void runsOutThenComesBackBeforeItCanBeHeldAgain() {
		BreathMeter meter = new BreathMeter();
		int held = 0;
		while (meter.tick(true)) {
			held++;
		}
		assertEquals(BreathMeter.HOLD_TICKS, held, "a full breath lasts its hold time");
		assertEquals(0, meter.breath());
		// Still wanted, but winded: it comes back instead, and is held again only once enough is back.
		int winded = 0;
		while (!meter.tick(true)) {
			winded++;
		}
		assertEquals(Math.round(BreathMeter.WINDED_UNTIL * BreathMeter.RECOVER_TICKS), winded, 1);
	}

	@Test
	void recoversToFullAndStops() {
		BreathMeter meter = new BreathMeter();
		for (int i = 0; i < 30; i++) {
			assertTrue(meter.tick(true));
		}
		for (int i = 0; i < BreathMeter.RECOVER_TICKS; i++) {
			assertFalse(meter.tick(false));
		}
		assertEquals(1, meter.breath());
	}
}

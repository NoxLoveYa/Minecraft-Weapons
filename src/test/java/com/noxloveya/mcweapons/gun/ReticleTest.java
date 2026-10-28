package com.noxloveya.mcweapons.gun;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReticleTest {
	@Test
	void keepsSentValuesInRange() {
		assertEquals(0x123456, new Reticle(0xFF123456, 1, Reticle.Shape.DOT).color(), "alpha is dropped");
		assertEquals(Reticle.SIZES.getLast(), new Reticle(0, 100, Reticle.Shape.DOT).size());
		assertEquals(Reticle.SIZES.getFirst(), new Reticle(0, -1, Reticle.Shape.DOT).size());
		assertEquals(Reticle.DEFAULT_SIZE, new Reticle(0, Float.NaN, Reticle.Shape.DOT).size());
	}

	@Test
	void cyclesWrapBothWays() {
		Reticle reticle = Reticle.DEFAULT;
		assertEquals(Reticle.COLORS.get(1), reticle.cycleColor(1).color());
		assertEquals(Reticle.COLORS.getLast(), reticle.cycleColor(-1).color());
		assertEquals(0.5F, reticle.size(), "half size by default");
		assertEquals(0.25F, reticle.cycleSize(-1).size());
		assertEquals(Reticle.SIZES.getLast(), reticle.cycleSize(-2).size(), "before the smallest wraps to the biggest");
		assertEquals(Reticle.Shape.CHEVRON, reticle.cycleShape(-1).shape());
		assertEquals(Reticle.COLORS.getFirst(), new Reticle(0x010203, 1, Reticle.Shape.DOT).cycleColor(1).color(), "an unknown color goes to the first");
	}
}

package com.noxloveya.mcweapons.gun;

/**
 * A held breath while aiming: a full one lasts {@link #HOLD_TICKS} held and comes back over {@link #RECOVER_TICKS}. Once used up it
 * cannot be held again until {@link #WINDED_UNTIL} of it is back, so holding the key does not flicker at empty.
 */
public final class BreathMeter {
	public static final int HOLD_TICKS = 120;
	public static final int RECOVER_TICKS = 80;
	public static final float WINDED_UNTIL = 0.3F;

	private float breath = 1;
	private boolean winded;

	/** One tick: the breath is held if {@code wanted} and there is some to hold. Returns whether it is held this tick. */
	public boolean tick(boolean wanted) {
		if (wanted && !winded) {
			if (breath > 1.0E-4F) {
				breath = Math.max(0, breath - 1.0F / HOLD_TICKS);
				return true;
			}
			breath = 0;
			winded = true;
			return false;
		}
		breath = Math.min(1, breath + 1.0F / RECOVER_TICKS);
		winded &= breath < WINDED_UNTIL;
		return false;
	}

	/** What is left, 1 full. */
	public float breath() {
		return breath;
	}
}

package com.noxloveya.mcweapons.gun;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** How a gun's spread (hip and aimed alike) grows or shrinks with the shooter's stance: multipliers for walking, sprinting and crouching. */
public record MovementSpread(float walk, float sprint, float crouch) {
	public static final MovementSpread DEFAULT = new MovementSpread(1.5F, 2.5F, 0.7F);

	public static final Codec<MovementSpread> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.floatRange(0.1F, 10).fieldOf("walk").forGetter(MovementSpread::walk),
			Codec.floatRange(0.1F, 10).fieldOf("sprint").forGetter(MovementSpread::sprint),
			Codec.floatRange(0.1F, 10).fieldOf("crouch").forGetter(MovementSpread::crouch)
	).apply(i, MovementSpread::new));

	public float multiplier(Stance stance) {
		return switch (stance) {
			case STILL -> 1;
			case WALK -> walk;
			case SPRINT -> sprint;
			case CROUCH -> crouch;
		};
	}

	/** What the shooter is doing when a shot leaves. Crouch-walking counts as crouching. */
	public enum Stance {
		STILL, WALK, SPRINT, CROUCH;

		public static Stance of(boolean sprinting, boolean crouching, boolean moving) {
			return sprinting ? SPRINT : crouching ? CROUCH : moving ? WALK : STILL;
		}
	}
}

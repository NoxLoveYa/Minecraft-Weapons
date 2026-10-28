package com.noxloveya.mcweapons.gun;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * How a gun's bullets fly, under {@code "bullet"} in the definition: muzzle speed in blocks (metres) per second, and the drop in blocks per
 * second squared. Shared by the server, which hits things along the path, and clients, which draw the tracer on the same path.
 */
public record BulletStats(float speed, float gravity) {
	// A 9x19mm round leaves a pistol at about 375 m/s, and falls like anything else.
	public static final BulletStats DEFAULT = new BulletStats(375, 9.81F);

	// The speed stays where a bullet still moves every tick and a tick's step stays short enough to raycast cheaply.
	public static final Codec<BulletStats> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.floatRange(20, 1000).fieldOf("speed").forGetter(BulletStats::speed),
			Codec.floatRange(0, 40).fieldOf("gravity").forGetter(BulletStats::gravity)
	).apply(i, BulletStats::new));

	/** Blocks travelled per tick at the muzzle. */
	public double speedPerTick() {
		return speed / 20.0;
	}

	/** Velocity lost downwards per tick, in blocks per tick. */
	public double gravityPerTick() {
		return gravity / 400.0;
	}
}

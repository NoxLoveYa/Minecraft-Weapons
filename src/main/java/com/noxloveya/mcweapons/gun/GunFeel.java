package com.noxloveya.mcweapons.gun;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.ExtraCodecs;

/** How the first-person gun moves, under {@code "feel"} in the definition. Only the client's viewmodel reads it. */
public record GunFeel(
		float swayGain,
		float swayMaxDeg,
		float bobScale,
		float recoilPushBack,
		float recoilSpringStiffness,
		float recoilSpringDamping,
		float recoilMaxKicks) {

	// Sway is a fifth of vanilla's hand sway and bob 30% of vanilla's view bob, which both swing a full-size gun far too much.
	// Recoil is a damped spring in "kicks" (1 = one shot) that settles in about 6 ticks with a small overshoot.
	public static final GunFeel DEFAULT = new GunFeel(0.02F, 0.75F, 0.3F, 0.04F, 0.35F, 0.6F, 2.5F);

	// Stiffness and damping stay within 0 to 1, where the spring cannot blow up.
	public static final Codec<GunFeel> CODEC = RecordCodecBuilder.create(i -> i.group(
			ExtraCodecs.NON_NEGATIVE_FLOAT.fieldOf("sway_gain").forGetter(GunFeel::swayGain),
			ExtraCodecs.NON_NEGATIVE_FLOAT.fieldOf("sway_max_deg").forGetter(GunFeel::swayMaxDeg),
			ExtraCodecs.NON_NEGATIVE_FLOAT.fieldOf("bob_scale").forGetter(GunFeel::bobScale),
			ExtraCodecs.NON_NEGATIVE_FLOAT.fieldOf("recoil_push_back").forGetter(GunFeel::recoilPushBack),
			Codec.floatRange(0, 1).fieldOf("recoil_spring_stiffness").forGetter(GunFeel::recoilSpringStiffness),
			Codec.floatRange(0, 1).fieldOf("recoil_spring_damping").forGetter(GunFeel::recoilSpringDamping),
			ExtraCodecs.NON_NEGATIVE_FLOAT.fieldOf("recoil_max_kicks").forGetter(GunFeel::recoilMaxKicks)
	).apply(i, GunFeel::new));
}

package com.noxloveya.mcweapons.gun;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.Optional;

/** Stats of one gun, loaded from {@code data/<namespace>/guns/<gun>.json}. */
public record GunDefinition(
		List<String> fireModes,
		int rpm,
		float damage,
		double range,
		int magSize,
		boolean chamber,
		float spreadHipDeg,
		float spreadAdsDeg,
		float recoilPitchDeg,
		float recoilYawDeg,
		int drawTicks,
		int reloadTicks,
		int reloadEmptyTicks,
		int adsTicks,
		float adsFovMultiplier,
		float moveSpeedAdsMultiplier,
		GunFeel feel) {

	// The json "item" field is not read: GunItem points at its definition id instead (see spec 01 open questions).
	// The ranges also guard the gun tuner: a typo there must not reach the server, like a 6400 block raycast or a zero FOV.
	private static final MapCodec<GunDefinition> STATS = RecordCodecBuilder.mapCodec(i -> i.group(
			Codec.STRING.listOf().fieldOf("fire_modes").forGetter(GunDefinition::fireModes),
			ExtraCodecs.POSITIVE_INT.fieldOf("rpm").forGetter(GunDefinition::rpm),
			ExtraCodecs.NON_NEGATIVE_FLOAT.fieldOf("damage").forGetter(GunDefinition::damage),
			Codec.doubleRange(0, 512).fieldOf("range").forGetter(GunDefinition::range),
			ExtraCodecs.POSITIVE_INT.fieldOf("mag_size").forGetter(GunDefinition::magSize),
			Codec.BOOL.fieldOf("chamber").forGetter(GunDefinition::chamber),
			ExtraCodecs.NON_NEGATIVE_FLOAT.fieldOf("spread_hip_deg").forGetter(GunDefinition::spreadHipDeg),
			ExtraCodecs.NON_NEGATIVE_FLOAT.fieldOf("spread_ads_deg").forGetter(GunDefinition::spreadAdsDeg),
			ExtraCodecs.NON_NEGATIVE_FLOAT.fieldOf("recoil_pitch_deg").forGetter(GunDefinition::recoilPitchDeg),
			ExtraCodecs.NON_NEGATIVE_FLOAT.fieldOf("recoil_yaw_deg").forGetter(GunDefinition::recoilYawDeg),
			ExtraCodecs.NON_NEGATIVE_INT.fieldOf("draw_ticks").forGetter(GunDefinition::drawTicks),
			ExtraCodecs.NON_NEGATIVE_INT.fieldOf("reload_ticks").forGetter(GunDefinition::reloadTicks),
			ExtraCodecs.NON_NEGATIVE_INT.fieldOf("reload_empty_ticks").forGetter(GunDefinition::reloadEmptyTicks),
			ExtraCodecs.NON_NEGATIVE_INT.fieldOf("ads_ticks").forGetter(GunDefinition::adsTicks),
			Codec.floatRange(0.1F, 1).fieldOf("ads_fov_multiplier").forGetter(GunDefinition::adsFovMultiplier),
			ExtraCodecs.NON_NEGATIVE_FLOAT.fieldOf("move_speed_ads_multiplier").forGetter(GunDefinition::moveSpeedAdsMultiplier)
	).apply(i, GunDefinition::new));

	// RecordCodecBuilder stops at 16 fields, so the stats are one map codec and "feel" sits beside them.
	// Feel is optional in the json but always written, so the tuner and saved files show every value.
	public static final Codec<GunDefinition> CODEC = RecordCodecBuilder.create(i -> i.group(
			STATS.forGetter(definition -> definition),
			GunFeel.CODEC.optionalFieldOf("feel").forGetter(definition -> Optional.of(definition.feel()))
	).apply(i, (stats, feel) -> stats.withFeel(feel.orElse(GunFeel.DEFAULT))));

	/** The stats alone, with the default feel. */
	public GunDefinition(List<String> fireModes, int rpm, float damage, double range, int magSize, boolean chamber, float spreadHipDeg, float spreadAdsDeg,
			float recoilPitchDeg, float recoilYawDeg, int drawTicks, int reloadTicks, int reloadEmptyTicks, int adsTicks, float adsFovMultiplier, float moveSpeedAdsMultiplier) {
		this(fireModes, rpm, damage, range, magSize, chamber, spreadHipDeg, spreadAdsDeg, recoilPitchDeg, recoilYawDeg, drawTicks, reloadTicks, reloadEmptyTicks,
				adsTicks, adsFovMultiplier, moveSpeedAdsMultiplier, GunFeel.DEFAULT);
	}

	public GunDefinition withFeel(GunFeel feel) {
		return new GunDefinition(fireModes, rpm, damage, range, magSize, chamber, spreadHipDeg, spreadAdsDeg, recoilPitchDeg, recoilYawDeg, drawTicks, reloadTicks,
				reloadEmptyTicks, adsTicks, adsFovMultiplier, moveSpeedAdsMultiplier, feel);
	}

	public int shotIntervalTicks() {
		return Mth.ceil(1200.0F / rpm);
	}

	/** Ticks between shots with the fraction kept: 590 RPM is 2.03 ticks. */
	public double shotInterval() {
		return 1200.0 / rpm;
	}

	/** Client fire timing. A due time more than one interval ahead means the clock jumped back (another world), so it may fire. */
	public boolean shotDue(double due, long now) {
		return now >= due || due - now > shotInterval();
	}

	/**
	 * When the next shot is due after one fires at {@code now}. Shots only happen on ticks, so a shot that waited less than a tick
	 * carries that wait: 590 RPM fires mostly every 2 ticks with a 3-tick gap now and then, averaging 590, instead of rounding
	 * every gap up to 3 ticks (400 RPM). A shot that waited longer was idle, and nothing is carried.
	 */
	public double nextShotDue(double due, long now) {
		double waited = now - due;
		return (waited >= 0 && waited < 1 ? due : now) + shotInterval();
	}

	/** Server check, in whole ticks. {@code now < lastShotTick} means the stack came from a world with a later clock, so it may fire. */
	public boolean readyToFire(long lastShotTick, long now, int allowanceTicks) {
		return now < lastShotTick || now - lastShotTick >= shotIntervalTicks() - allowanceTicks;
	}

	/** Rounds after a reload. Also the "mag is full" limit: a reload is pointless once {@code ammo >= reloadTarget(ammo)}. */
	public int reloadTarget(int ammo) {
		return ammo > 0 && chamber ? magSize + 1 : magSize;
	}
}

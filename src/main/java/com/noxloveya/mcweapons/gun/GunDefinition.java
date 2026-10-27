package com.noxloveya.mcweapons.gun;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;

import java.util.List;

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
		float moveSpeedAdsMultiplier) {

	// The json "item" field is not read: GunItem points at its definition id instead (see spec 01 open questions).
	public static final Codec<GunDefinition> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.listOf().fieldOf("fire_modes").forGetter(GunDefinition::fireModes),
			ExtraCodecs.POSITIVE_INT.fieldOf("rpm").forGetter(GunDefinition::rpm),
			Codec.FLOAT.fieldOf("damage").forGetter(GunDefinition::damage),
			Codec.DOUBLE.fieldOf("range").forGetter(GunDefinition::range),
			ExtraCodecs.POSITIVE_INT.fieldOf("mag_size").forGetter(GunDefinition::magSize),
			Codec.BOOL.fieldOf("chamber").forGetter(GunDefinition::chamber),
			Codec.FLOAT.fieldOf("spread_hip_deg").forGetter(GunDefinition::spreadHipDeg),
			Codec.FLOAT.fieldOf("spread_ads_deg").forGetter(GunDefinition::spreadAdsDeg),
			Codec.FLOAT.fieldOf("recoil_pitch_deg").forGetter(GunDefinition::recoilPitchDeg),
			Codec.FLOAT.fieldOf("recoil_yaw_deg").forGetter(GunDefinition::recoilYawDeg),
			ExtraCodecs.NON_NEGATIVE_INT.fieldOf("draw_ticks").forGetter(GunDefinition::drawTicks),
			ExtraCodecs.NON_NEGATIVE_INT.fieldOf("reload_ticks").forGetter(GunDefinition::reloadTicks),
			ExtraCodecs.NON_NEGATIVE_INT.fieldOf("reload_empty_ticks").forGetter(GunDefinition::reloadEmptyTicks),
			ExtraCodecs.NON_NEGATIVE_INT.fieldOf("ads_ticks").forGetter(GunDefinition::adsTicks),
			Codec.FLOAT.fieldOf("ads_fov_multiplier").forGetter(GunDefinition::adsFovMultiplier),
			Codec.FLOAT.fieldOf("move_speed_ads_multiplier").forGetter(GunDefinition::moveSpeedAdsMultiplier)
	).apply(i, GunDefinition::new));

	public int shotIntervalTicks() {
		return Mth.ceil(1200.0F / rpm);
	}

	/** {@code now < lastShotTick} means the stack came from a world with a later clock, so it may fire. */
	public boolean readyToFire(long lastShotTick, long now, int allowanceTicks) {
		return now < lastShotTick || now - lastShotTick >= shotIntervalTicks() - allowanceTicks;
	}

	/** Rounds after a reload. Also the "mag is full" limit: a reload is pointless once {@code ammo >= reloadTarget(ammo)}. */
	public int reloadTarget(int ammo) {
		return ammo > 0 && chamber ? magSize + 1 : magSize;
	}
}

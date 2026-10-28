package com.noxloveya.mcweapons.gun;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
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
		float headshotMultiplier,
		GunFeel feel,
		List<Identifier> attachments,
		BulletStats bullet,
		MovementSpread movementSpread,
		@Nullable RoundReload roundReload) {

	/** Damage multiplier for hits in the head zone (see Hitscan) when the json leaves it out. */
	public static final float DEFAULT_HEADSHOT_MULTIPLIER = 2;

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

	// RecordCodecBuilder stops at 16 fields, so the stats are one map codec and the newer fields sit beside them.
	// Those are optional in the json but always written, so the tuner and saved files show every value.
	public static final Codec<GunDefinition> CODEC = RecordCodecBuilder.create(i -> i.group(
			STATS.forGetter(definition -> definition),
			Codec.floatRange(1, 10).optionalFieldOf("headshot_multiplier").forGetter(definition -> Optional.of(definition.headshotMultiplier())),
			GunFeel.CODEC.optionalFieldOf("feel").forGetter(definition -> Optional.of(definition.feel())),
			// Attachment item ids that fit this gun; each goes in the slot its item names.
			Identifier.CODEC.listOf().optionalFieldOf("attachments").forGetter(definition -> Optional.of(definition.attachments())),
			BulletStats.CODEC.optionalFieldOf("bullet").forGetter(definition -> Optional.of(definition.bullet())),
			MovementSpread.CODEC.optionalFieldOf("movement_spread").forGetter(definition -> Optional.of(definition.movementSpread())),
			// Only guns loaded a round at a time have it, so it is written only when set.
			RoundReload.CODEC.optionalFieldOf("round_reload").forGetter(definition -> Optional.ofNullable(definition.roundReload()))
	).apply(i, (stats, headshot, feel, attachments, bullet, movementSpread, roundReload) -> stats.withHeadshotMultiplier(headshot.orElse(DEFAULT_HEADSHOT_MULTIPLIER))
			.withFeel(feel.orElse(GunFeel.DEFAULT)).withAttachments(attachments.orElse(List.of())).withBullet(bullet.orElse(BulletStats.DEFAULT))
			.withMovementSpread(movementSpread.orElse(MovementSpread.DEFAULT)).withRoundReload(roundReload.orElse(null))));

	/** The stats alone, with the default headshot multiplier, feel, bullet and movement spread, no attachments and a magazine reload. */
	public GunDefinition(List<String> fireModes, int rpm, float damage, double range, int magSize, boolean chamber, float spreadHipDeg, float spreadAdsDeg,
			float recoilPitchDeg, float recoilYawDeg, int drawTicks, int reloadTicks, int reloadEmptyTicks, int adsTicks, float adsFovMultiplier, float moveSpeedAdsMultiplier) {
		this(fireModes, rpm, damage, range, magSize, chamber, spreadHipDeg, spreadAdsDeg, recoilPitchDeg, recoilYawDeg, drawTicks, reloadTicks, reloadEmptyTicks,
				adsTicks, adsFovMultiplier, moveSpeedAdsMultiplier, DEFAULT_HEADSHOT_MULTIPLIER, GunFeel.DEFAULT, List.of(), BulletStats.DEFAULT, MovementSpread.DEFAULT, null);
	}

	public GunDefinition withFeel(GunFeel feel) {
		return new GunDefinition(fireModes, rpm, damage, range, magSize, chamber, spreadHipDeg, spreadAdsDeg, recoilPitchDeg, recoilYawDeg, drawTicks, reloadTicks,
				reloadEmptyTicks, adsTicks, adsFovMultiplier, moveSpeedAdsMultiplier, headshotMultiplier, feel, attachments, bullet, movementSpread, roundReload);
	}

	public GunDefinition withHeadshotMultiplier(float headshotMultiplier) {
		return new GunDefinition(fireModes, rpm, damage, range, magSize, chamber, spreadHipDeg, spreadAdsDeg, recoilPitchDeg, recoilYawDeg, drawTicks, reloadTicks,
				reloadEmptyTicks, adsTicks, adsFovMultiplier, moveSpeedAdsMultiplier, headshotMultiplier, feel, attachments, bullet, movementSpread, roundReload);
	}

	public GunDefinition withMagSize(int magSize) {
		return new GunDefinition(fireModes, rpm, damage, range, magSize, chamber, spreadHipDeg, spreadAdsDeg, recoilPitchDeg, recoilYawDeg, drawTicks, reloadTicks,
				reloadEmptyTicks, adsTicks, adsFovMultiplier, moveSpeedAdsMultiplier, headshotMultiplier, feel, attachments, bullet, movementSpread, roundReload);
	}

	public GunDefinition withAttachments(List<Identifier> attachments) {
		return new GunDefinition(fireModes, rpm, damage, range, magSize, chamber, spreadHipDeg, spreadAdsDeg, recoilPitchDeg, recoilYawDeg, drawTicks, reloadTicks,
				reloadEmptyTicks, adsTicks, adsFovMultiplier, moveSpeedAdsMultiplier, headshotMultiplier, feel, List.copyOf(attachments), bullet, movementSpread, roundReload);
	}

	public GunDefinition withRecoil(float recoilPitchDeg, float recoilYawDeg) {
		return new GunDefinition(fireModes, rpm, damage, range, magSize, chamber, spreadHipDeg, spreadAdsDeg, recoilPitchDeg, recoilYawDeg, drawTicks, reloadTicks,
				reloadEmptyTicks, adsTicks, adsFovMultiplier, moveSpeedAdsMultiplier, headshotMultiplier, feel, attachments, bullet, movementSpread, roundReload);
	}

	public GunDefinition withSpreadHip(float spreadHipDeg) {
		return new GunDefinition(fireModes, rpm, damage, range, magSize, chamber, spreadHipDeg, spreadAdsDeg, recoilPitchDeg, recoilYawDeg, drawTicks, reloadTicks,
				reloadEmptyTicks, adsTicks, adsFovMultiplier, moveSpeedAdsMultiplier, headshotMultiplier, feel, attachments, bullet, movementSpread, roundReload);
	}

	public GunDefinition withAdsTicks(int adsTicks) {
		return new GunDefinition(fireModes, rpm, damage, range, magSize, chamber, spreadHipDeg, spreadAdsDeg, recoilPitchDeg, recoilYawDeg, drawTicks, reloadTicks,
				reloadEmptyTicks, adsTicks, adsFovMultiplier, moveSpeedAdsMultiplier, headshotMultiplier, feel, attachments, bullet, movementSpread, roundReload);
	}

	public GunDefinition withAdsFovMultiplier(float adsFovMultiplier) {
		return new GunDefinition(fireModes, rpm, damage, range, magSize, chamber, spreadHipDeg, spreadAdsDeg, recoilPitchDeg, recoilYawDeg, drawTicks, reloadTicks,
				reloadEmptyTicks, adsTicks, adsFovMultiplier, moveSpeedAdsMultiplier, headshotMultiplier, feel, attachments, bullet, movementSpread, roundReload);
	}

	public GunDefinition withBullet(BulletStats bullet) {
		return new GunDefinition(fireModes, rpm, damage, range, magSize, chamber, spreadHipDeg, spreadAdsDeg, recoilPitchDeg, recoilYawDeg, drawTicks, reloadTicks,
				reloadEmptyTicks, adsTicks, adsFovMultiplier, moveSpeedAdsMultiplier, headshotMultiplier, feel, attachments, bullet, movementSpread, roundReload);
	}

	public GunDefinition withMovementSpread(MovementSpread movementSpread) {
		return new GunDefinition(fireModes, rpm, damage, range, magSize, chamber, spreadHipDeg, spreadAdsDeg, recoilPitchDeg, recoilYawDeg, drawTicks, reloadTicks,
				reloadEmptyTicks, adsTicks, adsFovMultiplier, moveSpeedAdsMultiplier, headshotMultiplier, feel, attachments, bullet, movementSpread, roundReload);
	}

	public GunDefinition withRoundReload(@Nullable RoundReload roundReload) {
		return new GunDefinition(fireModes, rpm, damage, range, magSize, chamber, spreadHipDeg, spreadAdsDeg, recoilPitchDeg, recoilYawDeg, drawTicks, reloadTicks,
				reloadEmptyTicks, adsTicks, adsFovMultiplier, moveSpeedAdsMultiplier, headshotMultiplier, feel, attachments, bullet, movementSpread, roundReload);
	}

	/** How far a shot may stray, in degrees: the hip or aimed spread, times the multiplier for the shooter's stance. */
	public float spread(boolean aiming, MovementSpread.Stance stance) {
		return (aiming ? spreadAdsDeg : spreadHipDeg) * movementSpread.multiplier(stance);
	}

	/** The fire mode that blocks the trigger. Every gun has it after its own modes, whatever its definition lists. */
	public static final String SAFE = "safe";
	/**
	 * A bolt action fires like semi, and its fire animation works the bolt, which throws the casing out (an {@code eject_casing}
	 * keyframe) rather than the shot. The bolt closes on an empty chamber, so nothing locks back.
	 */
	public static final String BOLT = "bolt";

	public boolean boltAction() {
		return fireModes.contains(BOLT);
	}

	/** What the fire mode key steps through: the gun's own modes, then safe. */
	public List<String> selectableModes() {
		List<String> modes = new ArrayList<>(fireModes);
		modes.remove(SAFE);
		modes.add(SAFE);
		return modes;
	}

	/** The mode a gun is in: {@code stored} when it is one of this gun's, otherwise its first mode (a fresh gun, or a definition edit). */
	public String fireMode(@Nullable String stored) {
		List<String> modes = selectableModes();
		return stored != null && modes.contains(stored) ? stored : modes.getFirst();
	}

	/** The mode after {@code current}, wrapping around. */
	public String nextFireMode(@Nullable String current) {
		List<String> modes = selectableModes();
		return modes.get((modes.indexOf(fireMode(current)) + 1) % modes.size());
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

	/** Ticks a reload from {@code ammo} rounds takes. */
	public int reloadDuration(int ammo) {
		if (ammo == 0) {
			return reloadEmptyTicks;
		}
		return roundReload != null ? roundReload.ticks(reloadTarget(ammo) - ammo) : reloadTicks;
	}

	/** The animation a reload from {@code ammo} rounds plays: the empty or tactical reload, or {@link #roundReloadAction} for the rounds missing. */
	public String reloadAction(int ammo) {
		if (ammo == 0) {
			return "reload_empty";
		}
		return roundReload != null ? roundReloadAction(reloadTarget(ammo) - ammo) : "reload";
	}

	/** A round-at-a-time reload of {@code rounds}: {@code reload_start}, {@code reload_round} that many times, then {@code reload_end}. */
	public static String roundReloadAction(int rounds) {
		return "reload_rounds_" + rounds;
	}

	/** {@code ammo} cut down to what this gun holds, after a smaller magazine goes in: a full mag, and the chambered round. */
	public int clampAmmo(int ammo) {
		return Math.min(ammo, reloadTarget(ammo));
	}
}

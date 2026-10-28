package com.noxloveya.mcweapons.gun;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.noxloveya.mcweapons.McWeapons;
import com.noxloveya.mcweapons.network.GunDefinitionsPayload;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GunDefinitionTest {
	@Test
	void glock17ParsesAndFollowsSpecRules() throws Exception {
		GunDefinition glock;
		try (Reader reader = new InputStreamReader(getClass().getResourceAsStream("/data/mcweapons/guns/glock17.json"), StandardCharsets.UTF_8)) {
			glock = GunDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader)).getOrThrow();
		}
		assertEquals(600, glock.rpm());
		assertEquals(17, glock.magSize());

		// ceil(1200 / 600) = 2 ticks, the server allows 1 tick less.
		assertEquals(2, glock.shotIntervalTicks());
		assertFalse(glock.readyToFire(100, 101, 0));
		assertTrue(glock.readyToFire(100, 102, 0));
		assertTrue(glock.readyToFire(100, 101, 1));
		assertTrue(glock.readyToFire(500, 10, 0), "a later clock from another world must not lock the gun");

		// Tactical reload ends at mag + 1, empty reload at mag, and a full gun cannot reload.
		assertEquals(18, glock.reloadTarget(1));
		assertEquals(17, glock.reloadTarget(0));
		assertEquals(18, glock.reloadTarget(17), "a fresh gun (17, chamber empty) may still chamber a round");
		assertEquals(18, glock.reloadTarget(18));

		// A 33-round magazine reloads to 34, and swapping back to the gun's own keeps a full mag and the chambered round.
		GunDefinition extended = glock.withMagSize(33);
		assertEquals(34, extended.reloadTarget(1));
		assertEquals(18, glock.clampAmmo(34));
		assertEquals(10, glock.clampAmmo(10));
		assertEquals(0, glock.clampAmmo(0));

		// Feel is optional in the json but always written back, so the tuner and its saved files show every value.
		JsonObject json = GunDefinition.CODEC.encodeStart(JsonOps.INSTANCE, glock).getOrThrow().getAsJsonObject();
		assertTrue(json.has("feel"));
		assertEquals(glock, GunDefinition.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
		json.remove("feel");
		assertEquals(glock.withFeel(GunFeel.DEFAULT), GunDefinition.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
		// breath_scale is newer than tuner files saved before it, so a feel block without it still loads, as 1.
		assertEquals(2.65F, glock.feel().breathScale());
		JsonObject olderFeel = GunFeel.CODEC.encodeStart(JsonOps.INSTANCE, glock.feel()).getOrThrow().getAsJsonObject();
		olderFeel.remove("breath_scale");
		assertEquals(1.0F, GunFeel.CODEC.parse(JsonOps.INSTANCE, olderFeel).getOrThrow().breathScale());
		// Headshot multiplier: read from the file, defaulted when missing, and at least 1.
		assertEquals(2.0F, glock.headshotMultiplier());
		JsonObject headshot = GunDefinition.CODEC.encodeStart(JsonOps.INSTANCE, glock).getOrThrow().getAsJsonObject();
		headshot.addProperty("headshot_multiplier", 3.5);
		assertEquals(3.5F, GunDefinition.CODEC.parse(JsonOps.INSTANCE, headshot).getOrThrow().headshotMultiplier());
		headshot.remove("headshot_multiplier");
		assertEquals(GunDefinition.DEFAULT_HEADSHOT_MULTIPLIER, GunDefinition.CODEC.parse(JsonOps.INSTANCE, headshot).getOrThrow().headshotMultiplier());
		headshot.addProperty("headshot_multiplier", 0.5);
		assertTrue(GunDefinition.CODEC.parse(JsonOps.INSTANCE, headshot).isError(), "a headshot must not do less than a body shot");
		// Stance multiplies the spread, hip and aimed: from the file, and the defaults when a file has none.
		assertEquals(0.4F * 1.5F, glock.spread(false, MovementSpread.Stance.WALK), 1.0E-6);
		assertEquals(0.05F * 2.5F, glock.spread(true, MovementSpread.Stance.SPRINT), 1.0E-6);
		assertEquals(0.4F * 0.7F, glock.spread(false, MovementSpread.Stance.CROUCH), 1.0E-6);
		assertEquals(0.4F, glock.spread(false, MovementSpread.Stance.STILL), 1.0E-6);
		JsonObject noStance = GunDefinition.CODEC.encodeStart(JsonOps.INSTANCE, glock).getOrThrow().getAsJsonObject();
		noStance.remove("movement_spread");
		assertEquals(MovementSpread.DEFAULT, GunDefinition.CODEC.parse(JsonOps.INSTANCE, noStance).getOrThrow().movementSpread());
		assertEquals(MovementSpread.Stance.SPRINT, MovementSpread.Stance.of(true, false, true));
		assertEquals(MovementSpread.Stance.CROUCH, MovementSpread.Stance.of(false, true, true), "crouch-walking counts as crouching");
		assertEquals(MovementSpread.Stance.WALK, MovementSpread.Stance.of(false, false, true));
		assertEquals(MovementSpread.Stance.STILL, MovementSpread.Stance.of(false, false, false));
		// The ranges that keep tuner typos off the server.
		json.addProperty("ads_fov_multiplier", 0);
		assertTrue(GunDefinition.CODEC.parse(JsonOps.INSTANCE, json).isError(), "a zero FOV multiplier must be rejected");

		// Singleplayer never encodes payloads, so this is the only check of the multiplayer sync codec.
		ByteBuf buf = Unpooled.buffer();
		GunDefinitionsPayload.CODEC.encode(buf, new GunDefinitionsPayload(Map.of(McWeapons.id("glock17"), glock)));
		assertEquals(glock, GunDefinitionsPayload.CODEC.decode(buf).definitions().get(McWeapons.id("glock17")));
	}

	@Test
	void mosinIsABoltActionLoadedARoundAtATime() throws Exception {
		GunDefinition mosin;
		try (Reader reader = new InputStreamReader(getClass().getResourceAsStream("/data/mcweapons/guns/mosin_nagant.json"), StandardCharsets.UTF_8)) {
			mosin = GunDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader)).getOrThrow();
		}
		// 85% of a 20-health player on the body, and a headshot always kills.
		assertEquals(17, mosin.damage());
		assertTrue(mosin.damage() * mosin.headshotMultiplier() >= 20);
		assertTrue(mosin.boltAction());
		assertEquals(List.of("bolt", "safe"), mosin.selectableModes());
		// The fire animation works the bolt in 1.05 s, inside one shot interval (DESIGN_RULES 3.6).
		assertTrue(mosin.shotInterval() / 20 > 1.05);
		// Five in the rifle, the chambered one among them: a reload always tops it up to five.
		assertEquals(5, mosin.reloadTarget(0));
		assertEquals(5, mosin.reloadTarget(3));
		assertEquals(55, mosin.reloadDuration(0));
		assertEquals("reload_empty", mosin.reloadAction(0));
		assertEquals(10 + 2 * 12 + 10, mosin.reloadDuration(3));
		assertEquals("reload_rounds_2", mosin.reloadAction(3));
		assertEquals(10 + 4 * 12 + 10, mosin.reloadDuration(1));
		// Rounds go in 60% through each round's clip.
		RoundReload rounds = mosin.roundReload();
		assertEquals(0, rounds.loaded(2, 10 + 7));
		assertEquals(1, rounds.loaded(2, 10 + 8));
		assertEquals(2, rounds.loaded(2, 10 + 12 + 8));
		assertEquals(2, rounds.loaded(2, 100));
		// round_reload round-trips, and a gun without it writes none.
		assertEquals(mosin, GunDefinition.CODEC.parse(JsonOps.INSTANCE, GunDefinition.CODEC.encodeStart(JsonOps.INSTANCE, mosin).getOrThrow()).getOrThrow());
		assertFalse(GunDefinition.CODEC.encodeStart(JsonOps.INSTANCE, mosin.withRoundReload(null)).getOrThrow().getAsJsonObject().has("round_reload"));
		assertEquals("reload", mosin.withRoundReload(null).reloadAction(3));
	}

	@Test
	void fireModesCycleThroughSafe() {
		GunDefinition glock = new GunDefinition(List.of("semi"), 600, 5, 64, 17, true, 0, 0, 0, 0, 10, 40, 50, 5, 0.8F, 0.7F);
		assertEquals(List.of("semi", "safe"), glock.selectableModes());
		assertEquals("semi", glock.fireMode(null), "a fresh gun starts in its first mode");
		assertEquals("safe", glock.nextFireMode(null));
		assertEquals("semi", glock.nextFireMode("safe"));
		assertEquals("semi", glock.fireMode("auto"), "a mode the gun no longer has falls back to its first");
		GunDefinition rifle = new GunDefinition(List.of("semi", "auto", "safe"), 600, 5, 64, 30, true, 0, 0, 0, 0, 10, 40, 50, 5, 0.8F, 0.7F);
		assertEquals(List.of("semi", "auto", "safe"), rifle.selectableModes(), "safe listed in the json is not doubled");
		assertEquals("auto", rifle.nextFireMode("semi"));
		assertEquals("safe", rifle.nextFireMode("auto"));
	}

	@Test
	void clientFireRateAveragesTheRpm() {
		// Firing on every tick a shot is due for one minute (1200 ticks) must give the RPM, not the RPM of the gap rounded up
		// to whole ticks (590 used to fire at 400).
		for (int rpm : new int[] {590, 600, 500, 450, 700, 400}) {
			GunDefinition gun = new GunDefinition(List.of("semi"), rpm, 5, 64, 17, true, 0, 0, 0, 0, 10, 40, 50, 5, 0.8F, 0.7F);
			double due = 0;
			int shots = 0;
			for (long tick = 0; tick < 1200; tick++) {
				if (gun.shotDue(due, tick)) {
					due = gun.nextShotDue(due, tick);
					shots++;
				}
			}
			assertEquals(rpm, shots, 1, "shots in a minute at " + rpm + " RPM");
		}
		GunDefinition gun = new GunDefinition(List.of("semi"), 590, 5, 64, 17, true, 0, 0, 0, 0, 10, 40, 50, 5, 0.8F, 0.7F);
		// An idle gun carries nothing into the next shot, and a clock that jumped back (another world) does not lock the gun.
		assertEquals(100 + gun.shotInterval(), gun.nextShotDue(10, 100), 1.0E-9);
		assertTrue(gun.shotDue(5000, 10));
	}
}

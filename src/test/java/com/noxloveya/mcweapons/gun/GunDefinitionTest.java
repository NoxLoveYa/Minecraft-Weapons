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

		// Feel is optional in the json but always written back, so the tuner and its saved files show every value.
		JsonObject json = GunDefinition.CODEC.encodeStart(JsonOps.INSTANCE, glock).getOrThrow().getAsJsonObject();
		assertTrue(json.has("feel"));
		assertEquals(glock, GunDefinition.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
		json.remove("feel");
		assertEquals(glock.withFeel(GunFeel.DEFAULT), GunDefinition.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
		// Headshot multiplier: read from the file, defaulted when missing, and at least 1.
		assertEquals(2.0F, glock.headshotMultiplier());
		JsonObject headshot = GunDefinition.CODEC.encodeStart(JsonOps.INSTANCE, glock).getOrThrow().getAsJsonObject();
		headshot.addProperty("headshot_multiplier", 3.5);
		assertEquals(3.5F, GunDefinition.CODEC.parse(JsonOps.INSTANCE, headshot).getOrThrow().headshotMultiplier());
		headshot.remove("headshot_multiplier");
		assertEquals(GunDefinition.DEFAULT_HEADSHOT_MULTIPLIER, GunDefinition.CODEC.parse(JsonOps.INSTANCE, headshot).getOrThrow().headshotMultiplier());
		headshot.addProperty("headshot_multiplier", 0.5);
		assertTrue(GunDefinition.CODEC.parse(JsonOps.INSTANCE, headshot).isError(), "a headshot must not do less than a body shot");
		// The ranges that keep tuner typos off the server.
		json.addProperty("ads_fov_multiplier", 0);
		assertTrue(GunDefinition.CODEC.parse(JsonOps.INSTANCE, json).isError(), "a zero FOV multiplier must be rejected");

		// Singleplayer never encodes payloads, so this is the only check of the multiplayer sync codec.
		ByteBuf buf = Unpooled.buffer();
		GunDefinitionsPayload.CODEC.encode(buf, new GunDefinitionsPayload(Map.of(McWeapons.id("glock17"), glock)));
		assertEquals(glock, GunDefinitionsPayload.CODEC.decode(buf).definitions().get(McWeapons.id("glock17")));
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

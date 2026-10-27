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
		// The ranges that keep tuner typos off the server.
		json.addProperty("ads_fov_multiplier", 0);
		assertTrue(GunDefinition.CODEC.parse(JsonOps.INSTANCE, json).isError(), "a zero FOV multiplier must be rejected");

		// Singleplayer never encodes payloads, so this is the only check of the multiplayer sync codec.
		ByteBuf buf = Unpooled.buffer();
		GunDefinitionsPayload.CODEC.encode(buf, new GunDefinitionsPayload(Map.of(McWeapons.id("glock17"), glock)));
		assertEquals(glock, GunDefinitionsPayload.CODEC.decode(buf).definitions().get(McWeapons.id("glock17")));
	}
}

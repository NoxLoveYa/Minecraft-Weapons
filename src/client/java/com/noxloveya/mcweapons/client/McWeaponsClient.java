package com.noxloveya.mcweapons.client;

import com.noxloveya.mcweapons.McWeapons;
import com.noxloveya.mcweapons.client.input.GunInput;
import com.noxloveya.mcweapons.client.render.AmmoHud;
import com.noxloveya.mcweapons.gun.GunDefinitions;
import com.noxloveya.mcweapons.network.GunDefinitionsPayload;
import com.noxloveya.mcweapons.network.ShotFxPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class McWeaponsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		GunInput.register();
		HudElementRegistry.addLast(McWeapons.id("ammo"), AmmoHud::render);
		ClientPlayNetworking.registerGlobalReceiver(GunDefinitionsPayload.TYPE, (payload, context) -> GunDefinitions.set(payload.definitions()));
		ClientPlayNetworking.registerGlobalReceiver(ShotFxPayload.TYPE, (payload, context) -> impact(context.client().level, payload));
	}

	// shortcut: vanilla particles only, 01c adds the muzzle flash and the third-person shot.
	private static void impact(ClientLevel level, ShotFxPayload fx) {
		if (level == null || fx.hitType() == HitResult.Type.MISS) {
			return;
		}
		ParticleOptions particle = fx.hitType() == HitResult.Type.ENTITY ? ParticleTypes.CRIT : ParticleTypes.SMOKE;
		Vec3 pos = fx.hitPos();
		for (int i = 0; i < 4; i++) {
			level.addParticle(particle, pos.x, pos.y, pos.z, 0.0, 0.02, 0.0);
		}
	}
}

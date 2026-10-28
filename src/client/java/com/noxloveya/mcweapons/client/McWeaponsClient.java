package com.noxloveya.mcweapons.client;

import com.geckolib.animatable.client.GeoRenderProvider;
import com.geckolib.animation.state.KeyFrameEvent;
import com.geckolib.cache.animation.keyframeevent.CustomInstructionKeyframeData;
import com.geckolib.cache.animation.keyframeevent.SoundKeyframeData;
import com.geckolib.constant.DataTickets;
import com.geckolib.renderer.GeoItemRenderer;
import com.noxloveya.mcweapons.McWeapons;
import com.noxloveya.mcweapons.client.input.GunInput;
import com.noxloveya.mcweapons.client.render.Casings;
import com.noxloveya.mcweapons.client.render.GunHud;
import com.noxloveya.mcweapons.client.render.GunRenderer;
import com.noxloveya.mcweapons.client.render.Lasers;
import com.noxloveya.mcweapons.client.render.MuzzleLight;
import com.noxloveya.mcweapons.client.render.ScopeView;
import com.noxloveya.mcweapons.client.render.ThirdPersonGuns;
import com.noxloveya.mcweapons.client.render.Tracers;
import com.noxloveya.mcweapons.client.screen.GunTunerScreen;
import com.noxloveya.mcweapons.gun.Attachments;
import com.noxloveya.mcweapons.gun.GunDefinition;
import com.noxloveya.mcweapons.gun.GunDefinitions;
import com.noxloveya.mcweapons.gun.GunItem;
import com.noxloveya.mcweapons.network.BulletImpactPayload;
import com.noxloveya.mcweapons.network.BulletPayload;
import com.noxloveya.mcweapons.network.GunDefinitionsPayload;
import com.noxloveya.mcweapons.network.HitMarkerPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class McWeaponsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		GunItem.renderProviderFactory = gun -> new GeoRenderProvider() {
			private @Nullable GunRenderer renderer;

			@Override
			public GeoItemRenderer<?> getGeoItemRenderer() {
				if (this.renderer == null) {
					this.renderer = new GunRenderer(gun);
				}
				return this.renderer;
			}
		};
		GunItem.soundKeyframes = McWeaponsClient::playKeyframeSound;
		GunItem.instructionKeyframes = McWeaponsClient::runKeyframeInstruction;
		GunInput.register();
		Tracers.register();
		Lasers.register();
		Casings.register();
		ThirdPersonGuns.register();
		// With vanilla's own overlays (the spyglass's among them), under the crosshair, hotbar and ammo panel.
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, McWeapons.id("scope"), ScopeView::render);
		HudElementRegistry.addLast(McWeapons.id("ammo"), (graphics, deltaTracker) -> {
			if (!GunTunerScreen.isOpen()) {
				GunHud.renderAmmo(graphics, deltaTracker);
			}
		});
		HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, vanilla -> (HudElement)(graphics, deltaTracker) -> {
			if (!GunTunerScreen.isOpen() && !GunHud.renderCrosshair(graphics, deltaTracker)) {
				vanilla.extractRenderState(graphics, deltaTracker);
			}
		});
		// The tuner's panels fill the screen around the showcased gun, so the HUD under them is hidden while it is open.
		for (Identifier element : List.of(VanillaHudElements.HOTBAR, VanillaHudElements.CHAT, VanillaHudElements.HEALTH_BAR, VanillaHudElements.ARMOR_BAR,
				VanillaHudElements.FOOD_BAR, VanillaHudElements.AIR_BAR, VanillaHudElements.MOUNT_HEALTH, VanillaHudElements.INFO_BAR,
				VanillaHudElements.EXPERIENCE_LEVEL, VanillaHudElements.HELD_ITEM_TOOLTIP, VanillaHudElements.MOB_EFFECTS)) {
			HudElementRegistry.replaceElement(element, vanilla -> (HudElement)(graphics, deltaTracker) -> {
				if (!GunTunerScreen.isOpen()) {
					vanilla.extractRenderState(graphics, deltaTracker);
				}
			});
		}
		ClientPlayNetworking.registerGlobalReceiver(GunDefinitionsPayload.TYPE, (payload, context) -> GunDefinitions.set(payload.definitions()));
		ClientPlayNetworking.registerGlobalReceiver(BulletPayload.TYPE, (payload, context) -> bulletFired(context.client().level, payload));
		ClientPlayNetworking.registerGlobalReceiver(BulletImpactPayload.TYPE, (payload, context) -> bulletImpact(context.client().level, payload));
		ClientPlayNetworking.registerGlobalReceiver(HitMarkerPayload.TYPE, (payload, context) -> GunHud.hitmarker(payload.headshot(), payload.kill()));
	}

	/** Plays the sound keyframes from the gun animations (mag out, mag in, slide) at whoever holds the gun. */
	private static void playKeyframeSound(KeyFrameEvent<GunItem, SoundKeyframeData> event) {
		ClientLevel level = Minecraft.getInstance().level;
		Long instanceId = event.renderState().getGeckolibData(DataTickets.ANIMATABLE_INSTANCE_ID);
		Entity holder = level == null || instanceId == null ? null : GunRenderer.holder(level, instanceId);
		Identifier sound = Identifier.tryParse(event.keyframeData().getSound());
		if (holder != null && sound != null) {
			level.playLocalSound(holder.getX(), holder.getEyeY(), holder.getZ(), SoundEvent.createVariableRangeEvent(sound), SoundSource.PLAYERS, 1.0F, 1.0F, false);
		}
	}

	/** Instruction keyframes in the gun animations: {@code eject_casing}, where a bolt action's bolt throws the spent casing out. */
	private static void runKeyframeInstruction(KeyFrameEvent<GunItem, CustomInstructionKeyframeData> event) {
		ClientLevel level = Minecraft.getInstance().level;
		Long instanceId = event.renderState().getGeckolibData(DataTickets.ANIMATABLE_INSTANCE_ID);
		Entity holder = level == null || instanceId == null ? null : GunRenderer.holder(level, instanceId);
		if (holder != null && event.keyframeData().getInstructions().contains("eject_casing")) {
			Casings.eject(holder, event.animatable());
		}
	}

	/**
	 * Someone else's shot (ours were drawn when we fired): their third-person gun fires, with flash, smoke and a casing, and the tracer
	 * starts on the bullet's path from their muzzle.
	 */
	private static void bulletFired(@Nullable ClientLevel level, BulletPayload bullet) {
		if (level == null) {
			return;
		}
		Entity shooter = level.getEntity(bullet.shooterId());
		if (shooter == Minecraft.getInstance().player) {
			return;
		}
		ThirdPersonGuns.flash(bullet.shooterId());
		Vec3 muzzle = shooter != null ? Tracers.muzzle(shooter) : bullet.origin();
		if (shooter instanceof LivingEntity living && living.getMainHandItem().getItem() instanceof GunItem gun) {
			gun.getAnimatableInstanceCache().getManagerForId(GunRenderer.instanceId(shooter)).tryTriggerAnimation(GunItem.MAIN_CONTROLLER, "fire");
			level.addParticle(ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z, 0.0, 0.02, 0.0);
			GunDefinition definition = GunItem.definition(living.getMainHandItem());
			if (definition == null || !definition.boltAction()) {
				Casings.eject(shooter, gun);
			}
			if (!Attachments.silenced(living.getMainHandItem())) {
				MuzzleLight.flash(bullet.shooterId(), muzzle);
			}
		}
		Tracers.fire(bullet.shooterId(), bullet.shotId(), bullet.origin(), bullet.velocity(), bullet.gravity(), bullet.range(), muzzle, false);
	}

	/** Where a bullet stopped, by the server: its tracer ends there, and the impact puffs (with chips of the block it hit). */
	private static void bulletImpact(@Nullable ClientLevel level, BulletImpactPayload impact) {
		if (level == null) {
			return;
		}
		Tracers.impact(impact.shooterId(), impact.shotId(), impact.pos());
		Vec3 pos = impact.pos();
		RandomSource random = level.getRandom();
		ParticleOptions puff = impact.entity() ? ParticleTypes.CRIT : ParticleTypes.SMOKE;
		for (int i = 0; i < 4; i++) {
			level.addParticle(puff, pos.x, pos.y, pos.z, 0.0, 0.02, 0.0);
		}
		if (!impact.entity()) {
			// The hit point sits on the block face, so step a hair along the shot to find the block that was hit.
			Vec3 direction = impact.direction();
			BlockState block = level.getBlockState(BlockPos.containing(pos.add(direction.scale(0.01))));
			if (!block.isAir()) {
				for (int i = 0; i < 6; i++) {
					level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, block), pos.x, pos.y, pos.z,
							(random.nextDouble() - 0.5) * 0.2 - direction.x * 0.1, random.nextDouble() * 0.15, (random.nextDouble() - 0.5) * 0.2 - direction.z * 0.1);
				}
			}
		}
	}
}

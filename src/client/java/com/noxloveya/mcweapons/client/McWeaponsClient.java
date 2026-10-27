package com.noxloveya.mcweapons.client;

import com.geckolib.animatable.client.GeoRenderProvider;
import com.geckolib.animation.state.KeyFrameEvent;
import com.geckolib.cache.animation.keyframeevent.SoundKeyframeData;
import com.geckolib.constant.DataTickets;
import com.geckolib.renderer.GeoItemRenderer;
import com.noxloveya.mcweapons.McWeapons;
import com.noxloveya.mcweapons.client.input.GunInput;
import com.noxloveya.mcweapons.client.render.GunHud;
import com.noxloveya.mcweapons.client.render.GunRenderer;
import com.noxloveya.mcweapons.client.render.Tracers;
import com.noxloveya.mcweapons.client.render.Viewmodel;
import com.noxloveya.mcweapons.client.screen.GunTunerScreen;
import com.noxloveya.mcweapons.gun.GunDefinitions;
import com.noxloveya.mcweapons.gun.GunItem;
import com.noxloveya.mcweapons.network.GunDefinitionsPayload;
import com.noxloveya.mcweapons.network.ShotFxPayload;
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
import net.minecraft.world.phys.HitResult;
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
		GunInput.register();
		Tracers.register();
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
				VanillaHudElements.EXPERIENCE_LEVEL, VanillaHudElements.INFO_BAR_FOREGROUND, VanillaHudElements.HELD_ITEM_TOOLTIP, VanillaHudElements.MOB_EFFECTS)) {
			HudElementRegistry.replaceElement(element, vanilla -> (HudElement)(graphics, deltaTracker) -> {
				if (!GunTunerScreen.isOpen()) {
					vanilla.extractRenderState(graphics, deltaTracker);
				}
			});
		}
		ClientPlayNetworking.registerGlobalReceiver(GunDefinitionsPayload.TYPE, (payload, context) -> GunDefinitions.set(payload.definitions()));
		ClientPlayNetworking.registerGlobalReceiver(ShotFxPayload.TYPE, (payload, context) -> shotFx(context.client().level, payload));
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

	/** Rough muzzle position of a gun held in someone's right hand, seen from outside. */
	private static Vec3 heldMuzzle(Entity holder) {
		Vec3 look = holder.getLookAngle();
		Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
		return holder.getEyePosition().add(look.scale(0.7)).add(right.scale(0.35)).add(0, -0.45, 0);
	}

	private static void shotFx(@Nullable ClientLevel level, ShotFxPayload fx) {
		if (level == null) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		Entity shooter = level.getEntity(fx.shooterId());
		// Someone else's shot: play the fire animation on their third-person gun and puff smoke at its muzzle.
		if (shooter instanceof LivingEntity living && shooter != minecraft.player && living.getMainHandItem().getItem() instanceof GunItem gun) {
			gun.getAnimatableInstanceCache().getManagerForId(GunRenderer.instanceId(shooter)).tryTriggerAnimation(GunItem.MAIN_CONTROLLER, "fire");
			Vec3 muzzle = heldMuzzle(shooter);
			level.addParticle(ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z, 0.0, 0.02, 0.0);
		}
		if (shooter != null) {
			// Our own shot in first person starts where the viewmodel's muzzle is drawn; everyone else's from their hand.
			Vec3 from = shooter == minecraft.player && minecraft.options.getCameraType().isFirstPerson()
					&& GeoRenderProvider.of(minecraft.player.getMainHandItem()).getGeoItemRenderer() instanceof GunRenderer renderer
					? Viewmodel.muzzlePosition(renderer, minecraft.gameRenderer.mainCamera(), minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false))
					: heldMuzzle(shooter);
			Tracers.spawn(from, fx.hitPos());
		}
		if (fx.hitType() == HitResult.Type.MISS) {
			return;
		}
		Vec3 pos = fx.hitPos();
		RandomSource random = level.getRandom();
		ParticleOptions puff = fx.hitType() == HitResult.Type.ENTITY ? ParticleTypes.CRIT : ParticleTypes.SMOKE;
		for (int i = 0; i < 4; i++) {
			level.addParticle(puff, pos.x, pos.y, pos.z, 0.0, 0.02, 0.0);
		}
		if (fx.hitType() == HitResult.Type.BLOCK && shooter != null) {
			// The hit point sits on the block face, so step a hair along the shot to find the block that was hit.
			Vec3 direction = pos.subtract(shooter.getEyePosition()).normalize();
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

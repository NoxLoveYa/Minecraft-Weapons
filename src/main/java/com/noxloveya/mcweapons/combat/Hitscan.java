package com.noxloveya.mcweapons.combat;

import com.noxloveya.mcweapons.McWeapons;
import com.noxloveya.mcweapons.gun.GunDefinition;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class Hitscan {
	public static final ResourceKey<DamageType> BULLET = ResourceKey.create(Registries.DAMAGE_TYPE, McWeapons.id("bullet"));
	// How far under the eyes the head zone reaches, per block of body height: the top half block on a player (eyes at 1.62 of 1.8).
	private static final float HEAD_BELOW_EYES = 0.32F / 1.8F;

	private Hitscan() {
	}

	/** What a shot did: what it hit and, for a living target, whether the damage landed, was a headshot, and killed it. */
	public record Shot(HitResult hit, boolean hurt, boolean headshot, boolean killed) {
	}

	/** Casts one shot from the eyes and damages the first living entity before any block, more in the head. */
	public static Shot fire(ServerPlayer player, ItemStack gun, GunDefinition definition, long seed, boolean aiming) {
		// Uniform point in a cone whose half-angle is the spread.
		RandomSource random = RandomSource.create(seed);
		float spread = (aiming ? definition.spreadAdsDeg() : definition.spreadHipDeg()) * Mth.sqrt(random.nextFloat());
		float angle = random.nextFloat() * Mth.TWO_PI;
		Vec3 direction = Vec3.directionFromRotation(player.getXRot() + spread * Mth.sin(angle), player.getYRot() + spread * Mth.cos(angle));

		Vec3 eye = player.getEyePosition();
		HitResult blockHit = player.level().clip(new ClipContext(eye, eye.add(direction.scale(definition.range())),
				ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		Vec3 stop = blockHit.getLocation();
		EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(player, eye, stop, new AABB(eye, stop).inflate(1.0),
				entity -> entity instanceof LivingEntity && entity.isPickable() && !entity.isSpectator(), eye.distanceToSqr(stop));
		if (entityHit == null) {
			return new Shot(blockHit, false, false, false);
		}
		LivingEntity target = (LivingEntity)entityHit.getEntity();
		boolean headshot = inHeadZone(entityHit.getLocation().y, target.getEyeY(), target.getBbHeight());
		float damage = definition.damage() * (headshot ? definition.headshotMultiplier() : 1);
		boolean hurt = target.hurtServer(player.level(), bulletDamage(player, gun), damage);
		return new Shot(entityHit, hurt, headshot, hurt && target.isDeadOrDying());
	}

	/**
	 * The head zone is the top of the body down to a little under the eyes, scaled with the body so babies and small mobs get a smaller one.
	 * A shot from above that lands on top of the box counts too.
	 */
	public static boolean inHeadZone(double hitY, double eyeY, float height) {
		return hitY >= eyeY - HEAD_BELOW_EYES * height;
	}

	/** Vanilla only names the weapon when it has a custom name, so the death message is built here to always name the gun. */
	private static DamageSource bulletDamage(ServerPlayer player, ItemStack gun) {
		Component gunName = gun.getDisplayName();
		return new DamageSource(player.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(BULLET), player) {
			@Override
			public Component getLocalizedDeathMessage(LivingEntity victim) {
				return Component.translatable("death.attack." + type().msgId(), victim.getDisplayName(), player.getDisplayName(), gunName);
			}
		};
	}
}

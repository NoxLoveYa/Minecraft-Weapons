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

	private Hitscan() {
	}

	/** Casts one shot from the eyes, damages the first living entity before any block, and returns what it hit. */
	public static HitResult fire(ServerPlayer player, ItemStack gun, GunDefinition definition, long seed, boolean aiming) {
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
			return blockHit;
		}
		entityHit.getEntity().hurt(bulletDamage(player, gun), definition.damage());
		return entityHit;
	}

	/** Vanilla only names the weapon when it has a custom name, so the death message is built here to always name the gun. */
	private static DamageSource bulletDamage(ServerPlayer player, ItemStack gun) {
		Component gunName = gun.getDisplayName();
		return new DamageSource(player.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(BULLET), player) {
			@Override
			public Component getLocalizedDeathMessage(LivingEntity victim) {
				return Component.translatable("death.attack." + type().msgId(), victim.getDisplayName(), player.getDisplayName(), gunName);
			}
		};
	}
}

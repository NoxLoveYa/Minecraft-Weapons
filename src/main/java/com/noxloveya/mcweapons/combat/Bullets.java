package com.noxloveya.mcweapons.combat;

import com.noxloveya.mcweapons.McWeapons;
import com.noxloveya.mcweapons.gun.GunDefinition;
import com.noxloveya.mcweapons.gun.GunItem;
import com.noxloveya.mcweapons.network.BulletImpactPayload;
import com.noxloveya.mcweapons.network.BulletPayload;
import com.noxloveya.mcweapons.network.HitMarkerPayload;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Bullets in flight, on the server. A bullet is not an entity: each tick it moves one straight step along its path (see Ballistics),
 * and only that step is raycast against blocks and living entities. Travel time and drop are real, for one short ray per bullet per tick.
 * <p>
 * Lag compensation: the shooter aimed at the world their client drew, which trails the server. A bullet remembers the server tick of
 * the world the shooter saw and is stepped against living entities' boxes as they were then (each level keeps the last {@link #HISTORY}
 * ticks of them while anyone in it holds a gun), so a shot that lands on the shooter's screen lands on the server.
 */
public final class Bullets {
	public static final ResourceKey<DamageType> BULLET = ResourceKey.create(Registries.DAMAGE_TYPE, McWeapons.id("bullet"));
	// How far under the eyes the head zone reaches, per block of body height: the top half block on a player (eyes at 1.62 of 1.8).
	private static final float HEAD_BELOW_EYES = 0.32F / 1.8F;
	/** Ticks of living entities' boxes kept per level, more than a shot can be rewound. */
	private static final int HISTORY = 16;
	/** The most a shot is rewound, 0.5 s: a client claiming to have seen an older world is held to this. */
	static final int MAX_REWIND_TICKS = 10;
	/**
	 * How far behind its own game time a client draws other players. The client's game time is set by the server and so already trails
	 * it by the network delay, as do the positions it receives; on top of that entities step through those positions over a few ticks.
	 * Measured in game against the boxes recorded below: a client at game time t draws other players where they were at t - 1.
	 */
	static final int INTERP_TICKS = 1;
	/** Mobs are drawn one tick further behind than players (measured the same way): their positions are sent less often. */
	private static final int MOB_EXTRA_TICKS = 1;
	/** A bullet still in range is dropped after this long anyway (a slow, steep shot). */
	private static final int MAX_FLIGHT_TICKS = 200;
	/** Impacts are sent to players this close, as well as to the shooter and everyone who saw the shot. */
	private static final double IMPACT_AUDIENCE = 64;

	private static final Map<ServerLevel, LevelBullets> LEVELS = new IdentityHashMap<>();

	private Bullets() {
	}

	/** One level's bullets in flight, and its living entities' recent boxes by entity id. */
	private static final class LevelBullets {
		final List<Bullet> flying = new ArrayList<>();
		final Int2ObjectMap<Track> tracks = new Int2ObjectOpenHashMap<>();
	}

	/** An entity's box at the end of each of the last ticks, in a ring indexed by game time. */
	private static final class Track {
		final AABB[] boxes = new AABB[HISTORY];
		final long first;
		/** Ticks this entity is drawn behind other players: 0 for a player, {@link #MOB_EXTRA_TICKS} for anything else. */
		final int behind;
		long last;

		Track(long first, boolean player) {
			this.first = first;
			this.behind = player ? 0 : MOB_EXTRA_TICKS;
		}

		/** The box at {@code tick}, or the nearest one kept. Boxes are immutable, so these are the entity's own, not copies. */
		AABB at(long tick) {
			long kept = Mth.clamp(tick, Math.max(first, last - HISTORY + 1), last);
			return boxes[(int)Math.floorMod(kept, HISTORY)];
		}
	}

	private static final class Bullet {
		final ServerPlayer shooter;
		final int shotId;
		final Vec3 origin;
		final Vec3 velocity;
		final double gravity;
		final double range;
		/** The server tick whose world the shooter saw when firing: step k is checked against entities as they were at viewTick + k. */
		final long viewTick;
		final float damage;
		final float headshotMultiplier;
		final DamageSource source;
		int step;
		double travelled;

		Bullet(ServerPlayer shooter, int shotId, Vec3 origin, Vec3 velocity, double gravity, double range, long viewTick, float damage, float headshotMultiplier,
				DamageSource source) {
			this.shooter = shooter;
			this.shotId = shotId;
			this.origin = origin;
			this.velocity = velocity;
			this.gravity = gravity;
			this.range = range;
			this.viewTick = viewTick;
			this.damage = damage;
			this.headshotMultiplier = headshotMultiplier;
			this.source = source;
		}
	}

	public static void register() {
		// Boxes are recorded before the levels tick, which is what each level sends its players early in that tick (positions go out before
		// entities move: a mob's from the last tick, a player's from the packets since). Bullets fly once the level has ticked.
		ServerTickEvents.START_SERVER_TICK.register(server -> server.getAllLevels().forEach(Bullets::record));
		ServerTickEvents.END_LEVEL_TICK.register(Bullets::fly);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> LEVELS.clear());
	}

	/**
	 * Fires a bullet for {@code shooter} from {@code origin} along {@code direction}, as they saw the world at their game time
	 * {@code clientTick}. Players watching the shooter get its path to draw the tracer; it starts flying at the end of this tick.
	 */
	public static void fire(ServerPlayer shooter, int shotId, Vec3 origin, Vec3 direction, GunDefinition definition, ItemStack gun, long clientTick) {
		ServerLevel level = shooter.level();
		long now = level.getGameTime();
		// Rounded to floats, as BulletPayload sends them, so clients step the very same path.
		Vec3 velocity = new Vec3(direction.scale(definition.bullet().speedPerTick()).toVector3f());
		float gravity = (float)definition.bullet().gravityPerTick();
		float range = (float)definition.range();
		long viewTick = viewTick(clientTick, now);
		LEVELS.computeIfAbsent(level, key -> new LevelBullets()).flying.add(new Bullet(shooter, shotId, origin, velocity, gravity, range, viewTick,
				definition.damage(), definition.headshotMultiplier(), bulletDamage(shooter, gun)));
		BulletPayload fired = new BulletPayload(shooter.getId(), shotId, origin, velocity, gravity, range);
		PlayerLookup.tracking(shooter).forEach(player -> ServerPlayNetworking.send(player, fired));
	}

	/** The server tick a client at game time {@code clientTick} was looking at, never older than {@link #MAX_REWIND_TICKS}. */
	static long viewTick(long clientTick, long now) {
		return Mth.clamp(clientTick - INTERP_TICKS, now - MAX_REWIND_TICKS, now);
	}

	/** Keeps every living entity's box under the game time the level is about to tick to, while anyone in it could shoot. */
	private static void record(ServerLevel level) {
		LevelBullets bullets = LEVELS.get(level);
		// By the time someone fires, the gun has been out longer than a shot rewinds, so nothing older is ever missing.
		boolean armed = false;
		for (ServerPlayer player : level.players()) {
			armed |= GunItem.holdsGun(player);
		}
		if (!armed && (bullets == null || bullets.flying.isEmpty())) {
			LEVELS.remove(level);
			return;
		}
		if (bullets == null) {
			bullets = new LevelBullets();
			LEVELS.put(level, bullets);
		}
		long tick = level.getGameTime() + 1;
		int slot = (int)Math.floorMod(tick, HISTORY);
		for (Entity entity : level.getAllEntities()) {
			if (entity instanceof LivingEntity living && living.isAlive()) {
				Track track = bullets.tracks.computeIfAbsent(entity.getId(), id -> new Track(tick, entity instanceof Player));
				track.boxes[slot] = entity.getBoundingBox();
				track.last = tick;
			}
		}
		// Gone or dead since the last tick: nothing left to hit.
		bullets.tracks.values().removeIf(track -> track.last != tick);
	}

	private static void fly(ServerLevel level) {
		LevelBullets bullets = LEVELS.get(level);
		if (bullets != null && !bullets.flying.isEmpty()) {
			long now = level.getGameTime();
			bullets.flying.removeIf(bullet -> advance(level, bullets, bullet, now));
		}
	}

	/** Steps a bullet up to the shooter's view of now: several steps at once when it was just fired, one a tick after. True once it stops. */
	private static boolean advance(ServerLevel level, LevelBullets bullets, Bullet bullet, long now) {
		while (bullet.viewTick + bullet.step <= now) {
			if (step(level, bullets, bullet)) {
				return true;
			}
		}
		return false;
	}

	private static boolean step(ServerLevel level, LevelBullets bullets, Bullet bullet) {
		int k = bullet.step++;
		long seenTick = bullet.viewTick + k;
		Vec3 from = Ballistics.position(bullet.origin, bullet.velocity, bullet.gravity, k);
		Vec3 to = Ballistics.position(bullet.origin, bullet.velocity, bullet.gravity, k + 1);
		double length = from.distanceTo(to);
		boolean last = k + 1 >= MAX_FLIGHT_TICKS;
		if (bullet.travelled + length >= bullet.range) {
			to = from.add(to.subtract(from).scale((bullet.range - bullet.travelled) / length));
			last = true;
		}
		bullet.travelled += length;
		// A step never loads chunks: a bullet leaving the loaded world just stops there.
		if (!level.hasChunksAt(Mth.floor(Math.min(from.x, to.x)), Mth.floor(Math.min(from.z, to.z)), Mth.floor(Math.max(from.x, to.x)), Mth.floor(Math.max(from.z, to.z)))) {
			return true;
		}
		BlockHitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, bullet.shooter));
		Vec3 end = block.getType() == HitResult.Type.MISS ? to : block.getLocation();

		// The nearest living entity before the block, where it was in the world the shooter saw.
		AABB sweep = new AABB(from, end);
		LivingEntity target = null;
		Vec3 targetHit = null;
		AABB targetBox = null;
		double nearest = Double.MAX_VALUE;
		for (Int2ObjectMap.Entry<Track> entry : bullets.tracks.int2ObjectEntrySet()) {
			AABB box = entry.getValue().at(seenTick - entry.getValue().behind);
			if (entry.getIntKey() == bullet.shooter.getId() || !box.intersects(sweep)) {
				continue;
			}
			Optional<Vec3> hit = box.contains(from) ? Optional.of(from) : box.clip(from, end);
			if (hit.isEmpty() || from.distanceToSqr(hit.get()) >= nearest) {
				continue;
			}
			if (level.getEntity(entry.getIntKey()) instanceof LivingEntity living && living.isAlive() && living.isPickable() && !living.isSpectator()) {
				target = living;
				targetHit = hit.get();
				targetBox = box;
				nearest = from.distanceToSqr(targetHit);
			}
		}
		Vec3 direction = to.subtract(from).normalize();
		if (target != null) {
			hit(level, bullet, target, targetHit, targetBox);
			impact(level, bullet, targetHit, direction, true);
			return true;
		}
		if (block.getType() != HitResult.Type.MISS) {
			impact(level, bullet, end, direction, false);
			return true;
		}
		return last;
	}

	/** Damages {@code target}, more in the head, and tells the shooter. Every bullet counts: the damage type bypasses the hurt cooldown. */
	private static void hit(ServerLevel level, Bullet bullet, LivingEntity target, Vec3 hitPos, AABB box) {
		// The head zone is measured on the box the bullet was checked against: where the head was when the shooter saw it.
		boolean headshot = inHeadZone(hitPos.y, box.minY + target.getEyeHeight(), target.getBbHeight());
		float damage = bullet.damage * (headshot ? bullet.headshotMultiplier : 1);
		boolean hurt = target.hurtServer(level, bullet.source, damage);
		if (hurt && !bullet.shooter.hasDisconnected()) {
			ServerPlayNetworking.send(bullet.shooter, new HitMarkerPayload(headshot, target.isDeadOrDying()));
		}
	}

	/** Tells the shooter, everyone who saw the shot and anyone near where it landed. */
	private static void impact(ServerLevel level, Bullet bullet, Vec3 pos, Vec3 direction, boolean entity) {
		BulletImpactPayload payload = new BulletImpactPayload(bullet.shooter.getId(), bullet.shotId, pos, direction, entity);
		Set<ServerPlayer> audience = new HashSet<>(PlayerLookup.around(level, pos, IMPACT_AUDIENCE));
		audience.addAll(PlayerLookup.tracking(bullet.shooter));
		if (!bullet.shooter.hasDisconnected()) {
			audience.add(bullet.shooter);
		}
		audience.forEach(player -> ServerPlayNetworking.send(player, payload));
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

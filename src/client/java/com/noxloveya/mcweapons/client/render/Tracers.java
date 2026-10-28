package com.noxloveya.mcweapons.client.render;

import com.geckolib.animatable.client.GeoRenderProvider;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.noxloveya.mcweapons.combat.Ballistics;
import com.noxloveya.mcweapons.gun.Attachments;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Bullet tracers: a glowing streak on each bullet's real path. The path comes from our own shot or from the server (anyone else's), and
 * is stepped here exactly as the server steps the bullet (see Ballistics): one segment per tick, drawn between ticks like an entity.
 * A tracer stops at a block, where the server says the bullet hit, at the end of its range, and for our own shots at an entity we see
 * it reach (the server's word on those comes a round trip later). Visual only.
 */
public final class Tracers {
	/** Over this many blocks a tracer slides from where the gun's muzzle is drawn onto the bullet's path, which starts at the eye. */
	private static final double CONVERGE = 6;
	private static final double MAX_LENGTH = 8;
	// Half widths in blocks near the camera, and per block of distance further out, so the streak never thins below a few pixels.
	private static final float CORE_WIDTH = 0.018F;
	private static final float CORE_WIDTH_PER_BLOCK = 0.0028F;
	private static final float GLOW_WIDTH = 0.06F;
	private static final float GLOW_WIDTH_PER_BLOCK = 0.009F;
	private static final List<Tracer> ACTIVE = new ArrayList<>();

	private Tracers() {
	}

	private static final class Tracer {
		final int shooterId;
		final int shotId;
		final boolean own;
		final Vec3 origin;
		final Vec3 velocity;
		final double gravity;
		final double range;
		/** From the bullet's start to where the gun is drawn, faded out over {@link #CONVERGE} blocks of flight. */
		final Vec3 offset;
		final double length;
		int step;
		double travelled;
		// The head's move during the last tick, and the path distance at both ends of it.
		Vec3 headFrom;
		Vec3 headTo;
		double distanceFrom;
		double distanceTo;
		Vec3 direction;
		@Nullable Vec3 serverEnd;
		boolean stopped;
		int ticksStopped;

		Tracer(int shooterId, int shotId, boolean own, Vec3 origin, Vec3 velocity, double gravity, double range, Vec3 muzzle) {
			this.shooterId = shooterId;
			this.shotId = shotId;
			this.own = own;
			this.origin = origin;
			this.velocity = velocity;
			this.gravity = gravity;
			this.range = range;
			this.offset = muzzle.subtract(origin);
			this.length = Math.min(MAX_LENGTH, velocity.length() * 0.6);
			this.headFrom = origin;
			this.headTo = origin;
			this.direction = velocity.normalize();
		}
	}

	public static void register() {
		LevelRenderEvents.COLLECT_SUBMITS.register(Tracers::submit);
		ClientTickEvents.END_CLIENT_TICK.register(Tracers::tick);
	}

	/** Starts a tracer on a bullet's path (see BulletPayload), drawn from {@code muzzle} at first. */
	public static void fire(int shooterId, int shotId, Vec3 origin, Vec3 velocity, double gravity, double range, Vec3 muzzle, boolean own) {
		ACTIVE.add(new Tracer(shooterId, shotId, own, origin, velocity, gravity, range, muzzle));
	}

	/** The server says the bullet stopped at {@code pos}: its tracer ends there when it gets there. */
	public static void impact(int shooterId, int shotId, Vec3 pos) {
		for (Tracer tracer : ACTIVE) {
			if (tracer.shooterId == shooterId && tracer.shotId == shotId && !tracer.stopped) {
				tracer.serverEnd = pos;
			}
		}
	}

	/** Where {@code shooter}'s gun muzzle is drawn: ours in first person is the viewmodel's, anyone else's is in their hand. */
	public static Vec3 muzzle(Entity shooter) {
		Minecraft minecraft = Minecraft.getInstance();
		ItemStack gun = shooter instanceof LivingEntity living ? living.getMainHandItem() : ItemStack.EMPTY;
		if (shooter == minecraft.player && minecraft.options.getCameraType().isFirstPerson()
				&& GeoRenderProvider.of(gun).getGeoItemRenderer() instanceof GunRenderer renderer) {
			return Viewmodel.bonePosition(renderer, renderer.muzzleBone(gun), minecraft.gameRenderer.mainCamera(), minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false));
		}
		// How far ahead of the eye a held gun's muzzle is (see ThirdPersonGuns.heldPoint): further with a silencer on it.
		return ThirdPersonGuns.heldPoint(shooter, Attachments.silenced(gun) ? 1.37 : 0.97);
	}

	private static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null) {
			ACTIVE.clear();
			return;
		}
		// Bullets hold still with the server's: paused, or under /tick freeze.
		if (!ACTIVE.isEmpty() && !minecraft.isPaused() && level.tickRateManager().runsNormally()) {
			ACTIVE.removeIf(tracer -> advance(minecraft, level, tracer));
		}
	}

	/** Moves the head one step along the path, stopping it at whatever it reaches. True once the tail has caught up and it is gone. */
	private static boolean advance(Minecraft minecraft, ClientLevel level, Tracer tracer) {
		tracer.headFrom = tracer.headTo;
		tracer.distanceFrom = tracer.distanceTo;
		if (tracer.stopped) {
			return ++tracer.ticksStopped > 1;
		}
		int k = tracer.step++;
		Vec3 from = Ballistics.position(tracer.origin, tracer.velocity, tracer.gravity, k);
		Vec3 to = Ballistics.position(tracer.origin, tracer.velocity, tracer.gravity, k + 1);
		double segment = from.distanceTo(to);
		boolean stop = false;
		if (tracer.travelled + segment >= tracer.range) {
			to = from.add(to.subtract(from).scale((tracer.range - tracer.travelled) / segment));
			stop = true;
		}
		BlockHitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
		Vec3 end = to;
		if (block.getType() != HitResult.Type.MISS) {
			end = block.getLocation();
			stop = true;
		}
		if (tracer.serverEnd != null && onSegment(tracer.serverEnd, from, end)) {
			end = tracer.serverEnd;
			stop = true;
		}
		if (tracer.own && minecraft.player != null) {
			EntityHitResult entity = ProjectileUtil.getEntityHitResult(minecraft.player, from, end, new AABB(from, end).inflate(1.0),
					candidate -> candidate instanceof LivingEntity && candidate.isPickable() && !candidate.isSpectator(), from.distanceToSqr(end));
			if (entity != null) {
				end = entity.getLocation();
				stop = true;
			}
		}
		tracer.headTo = end;
		tracer.distanceTo = tracer.travelled + from.distanceTo(end);
		tracer.travelled += segment;
		if (segment > 1.0E-6) {
			tracer.direction = to.subtract(from).scale(1 / segment);
		}
		tracer.stopped = stop;
		return false;
	}

	/** Whether {@code point} lies on the segment, give or take a little (the server's impact is on the same path). */
	private static boolean onSegment(Vec3 point, Vec3 from, Vec3 to) {
		Vec3 segment = to.subtract(from);
		double lengthSqr = segment.lengthSqr();
		double t = lengthSqr < 1.0E-9 ? 0 : point.subtract(from).dot(segment) / lengthSqr;
		return t >= 0 && t <= 1 && from.add(segment.scale(t)).distanceToSqr(point) < 0.25;
	}

	private static void submit(LevelRenderContext context) {
		if (ACTIVE.isEmpty()) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 camera = minecraft.gameRenderer.mainCamera().position();
		List<Vector3f[]> streaks = new ArrayList<>();
		for (Tracer tracer : ACTIVE) {
			double distance = Mth.lerp(partial, tracer.distanceFrom, tracer.distanceTo);
			// Once the head has stopped, the tail catches up with it over a tick.
			double tail = Math.min(tracer.length, distance) * (tracer.ticksStopped > 0 ? 1 - partial : 1);
			if (tail < 0.05) {
				continue;
			}
			Vec3 head = tracer.headFrom.lerp(tracer.headTo, partial);
			Vec3 back = head.subtract(tracer.direction.scale(tail));
			// Positions relative to the camera, which is where level geometry is submitted from.
			Vector3f front = head.add(drawnOffset(tracer, distance)).subtract(camera).toVector3f();
			Vector3f rear = back.add(drawnOffset(tracer, distance - tail)).subtract(camera).toVector3f();
			// Widen the streak sideways to the view so it always faces the camera.
			Vector3f side = new Vector3f(front).sub(rear).cross(new Vector3f(rear).add(front).mul(0.5F));
			if (side.lengthSquared() > 1.0E-8F) {
				streaks.add(new Vector3f[] {rear, front, side.normalize()});
			}
		}
		// An additive glow, then a normally blended core on top: additive alone washes out to white against sand and sky.
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.lightning(), (pose, buffer) -> streaks.forEach(streak ->
				ribbon(buffer, pose, streak, GLOW_WIDTH, GLOW_WIDTH_PER_BLOCK, 0x00FF7A10, 0xD0FF9A28)));
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.debugQuads(), (pose, buffer) -> streaks.forEach(streak ->
				ribbon(buffer, pose, streak, CORE_WIDTH, CORE_WIDTH_PER_BLOCK, 0x00FFB040, 0xF0FFC64A)));
	}

	/** The muzzle offset left at {@code distance} along the path: all of it at the gun, none past {@link #CONVERGE} blocks. */
	private static Vec3 drawnOffset(Tracer tracer, double distance) {
		return tracer.offset.scale(Math.max(0, 1 - distance / CONVERGE));
	}

	/** A camera-facing quad from back to front, fading in from a transparent tail. Both windings, so culling never hides it. */
	private static void ribbon(VertexConsumer buffer, PoseStack.Pose pose, Vector3f[] streak, float width, float widthPerBlock, int tailColor, int headColor) {
		Vector3f back = streak[0];
		Vector3f front = streak[1];
		Vector3f b = new Vector3f(streak[2]).mul(Math.max(width, back.length() * widthPerBlock));
		Vector3f f = new Vector3f(streak[2]).mul(Math.max(width, front.length() * widthPerBlock));
		buffer.addVertex(pose, back.x - b.x, back.y - b.y, back.z - b.z).setColor(tailColor);
		buffer.addVertex(pose, back.x + b.x, back.y + b.y, back.z + b.z).setColor(tailColor);
		buffer.addVertex(pose, front.x + f.x, front.y + f.y, front.z + f.z).setColor(headColor);
		buffer.addVertex(pose, front.x - f.x, front.y - f.y, front.z - f.z).setColor(headColor);
		buffer.addVertex(pose, front.x - f.x, front.y - f.y, front.z - f.z).setColor(headColor);
		buffer.addVertex(pose, front.x + f.x, front.y + f.y, front.z + f.z).setColor(headColor);
		buffer.addVertex(pose, back.x + b.x, back.y + b.y, back.z + b.z).setColor(tailColor);
		buffer.addVertex(pose, back.x - b.x, back.y - b.y, back.z - b.z).setColor(tailColor);
	}
}

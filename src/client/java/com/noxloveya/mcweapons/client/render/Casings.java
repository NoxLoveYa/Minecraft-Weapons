package com.noxloveya.mcweapons.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.noxloveya.mcweapons.McWeapons;
import com.noxloveya.mcweapons.gun.GunItem;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Matrix3f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Spent casings: thrown out of the ejection port on every shot, they tumble, bounce with a tink and then lie where they stopped. Each
 * client simulates its own (they are only looks), and a casing goes once it is old or once no player is near it.
 */
public final class Casings {
	private static final int LIFETIME_TICKS = 60 * 20;
	private static final int SHRINK_TICKS = 20;
	private static final double KEEP_RANGE = 32;
	// Oldest go first past this, so a long full-auto fight cannot pile up without bound.
	private static final int MAX = 256;
	private static final float HALF_WIDTH = 0.0125F;
	private static final float HALF_LENGTH = 0.035F;
	private static final double GRAVITY = 0.04;
	private static final double DRAG = 0.98;
	private static final double BOUNCE = 0.35;
	private static final double FRICTION = 0.6;
	/** Below this speed (blocks per tick) a casing that hits the floor stays there. */
	private static final double REST_SPEED = 0.06;
	private static final Identifier TEXTURE = McWeapons.id("textures/entity/casing.png");
	/** Ticks our first-person casings are drawn with the gun instead of the world: long enough to clear it on screen. */
	private static final int HAND_TICKS = 5;
	/** A drawn port older than this is not trusted to still be where the gun is. */
	private static final long PORT_NANOS = 250_000_000L;
	private static final List<Casing> ACTIVE = new ArrayList<>();
	/** Where each holder's gun last had its ejection port drawn, by entity id. */
	private static final Map<Integer, Port> HELD_PORTS = new HashMap<>();
	private static @Nullable ClientLevel level;
	private static long handPassNanos;

	private Casings() {
	}

	/** An ejection port in the world: where it is, and the gun's right, up and forward (toward the muzzle) there. */
	public record Port(Vec3 pos, Vec3 right, Vec3 up, Vec3 forward, long nanos) {
		public Port(Vec3 pos, Vec3 right, Vec3 up, Vec3 forward) {
			this(pos, right, up, forward, System.nanoTime());
		}
	}

	private static final class Casing {
		Vec3 pos;
		Vec3 prevPos;
		Vec3 velocity;
		final Quaternionf rotation;
		final Quaternionf prevRotation;
		/** Tumble per tick, in radians about the casing's own x and y axes. */
		float spinX;
		float spinY;
		int age;
		boolean resting;
		final SoundEvent landing;
		/** Thrown out of our own first-person gun. */
		final boolean firstPerson;

		Casing(Vec3 pos, Vec3 velocity, Quaternionf rotation, float spinX, float spinY, SoundEvent landing, boolean firstPerson) {
			this.pos = this.prevPos = pos;
			this.velocity = velocity;
			this.rotation = rotation;
			this.prevRotation = new Quaternionf(rotation);
			this.spinX = spinX;
			this.spinY = spinY;
			this.landing = landing;
			this.firstPerson = firstPerson;
		}

		boolean inHand() {
			return firstPerson && age < HAND_TICKS;
		}
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(Casings::tick);
		LevelRenderEvents.COLLECT_SUBMITS.register(Casings::submit);
	}

	/**
	 * A casing flies out of the shooter's ejection port, where their gun was last drawn, up and to the right of the gun and carried along
	 * by their movement.
	 */
	public static void eject(Entity shooter, GunItem gun) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null) {
			return;
		}
		boolean firstPerson = shooter == minecraft.player && minecraft.options.getCameraType().isFirstPerson();
		Port port = firstPerson ? Viewmodel.ejectionPort(minecraft.gameRenderer.mainCamera()) : HELD_PORTS.get(shooter.getId());
		if (port == null || System.nanoTime() - port.nanos() > PORT_NANOS) {
			// Not drawn lately (out of view): roughly where a held gun's port is, along the look.
			Vec3 look = shooter.getLookAngle();
			Vec3 right = look.cross(new Vec3(0, 1, 0));
			right = right.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : right.normalize();
			port = new Port(ThirdPersonGuns.heldPoint(shooter, 0.68), right, right.cross(look).normalize(), look);
			firstPerson = false;
		}
		RandomSource random = minecraft.level.getRandom();
		// Up, to the right and a little back, so they land beside the shooter (the first-person port is drawn well out in front of the eye).
		Vec3 velocity = port.right().scale(0.13 + random.nextDouble() * 0.05).add(port.up().scale(0.12 + random.nextDouble() * 0.06))
				.add(port.forward().scale(-0.04 - random.nextDouble() * 0.03)).add(shooter.position().subtract(shooter.xo, shooter.yo, shooter.zo));
		// The head (+Z of the casing) faces back at the shooter as it leaves the chamber.
		Quaternionf rotation = new Quaternionf().rotationTo(new Vector3f(0, 0, 1), port.forward().scale(-1).toVector3f());
		if (ACTIVE.size() >= MAX) {
			ACTIVE.removeFirst();
		}
		ACTIVE.add(new Casing(port.pos(), velocity, rotation, 0.5F + random.nextFloat() * 0.4F, (random.nextFloat() - 0.5F) * 0.6F, gun.sound("casing"), firstPerson));
	}

	/** Called by {@link BonePoseLayer} with the pose a third-person gun's ejection port is drawn with, relative to the camera. */
	static void recordHeldPort(Entity holder, Matrix4fc pose) {
		Vec3 camera = Minecraft.getInstance().gameRenderer.mainCamera().position();
		HELD_PORTS.put(holder.getId(), new Port(camera.add(new Vec3(pose.transformPosition(new Vector3f()))), direction(pose, 1, 0, 0), direction(pose, 0, 1, 0),
				direction(pose, 0, 0, -1)));
	}

	private static Vec3 direction(Matrix4fc pose, float x, float y, float z) {
		return new Vec3(pose.transformDirection(new Vector3f(x, y, z))).normalize();
	}

	private static void tick(Minecraft minecraft) {
		if (minecraft.level != level) {
			ACTIVE.clear();
			HELD_PORTS.clear();
			level = minecraft.level;
		}
		// Frozen with the rest of the world by /tick freeze.
		if (level == null || minecraft.isPaused() || !level.tickRateManager().runsNormally()) {
			return;
		}
		long now = System.nanoTime();
		HELD_PORTS.values().removeIf(port -> now - port.nanos() > PORT_NANOS);
		if (ACTIVE.isEmpty()) {
			return;
		}
		ClientLevel current = level;
		boolean checkRange = current.getGameTime() % 20 == 0;
		ACTIVE.removeIf(casing -> ++casing.age > LIFETIME_TICKS || checkRange && current.players().stream().noneMatch(player -> player.distanceToSqr(casing.pos) < KEEP_RANGE * KEEP_RANGE));
		for (Casing casing : ACTIVE) {
			move(current, casing);
		}
	}

	private static void move(ClientLevel level, Casing casing) {
		casing.prevPos = casing.pos;
		casing.prevRotation.set(casing.rotation);
		if (casing.resting) {
			// The block under it may have been broken since.
			if (casing.age % 10 == 0 && level.noCollision(new AABB(casing.pos.x - HALF_WIDTH, casing.pos.y - HALF_WIDTH - 0.02, casing.pos.z - HALF_WIDTH,
					casing.pos.x + HALF_WIDTH, casing.pos.y - HALF_WIDTH, casing.pos.z + HALF_WIDTH))) {
				casing.resting = false;
			}
			return;
		}
		casing.velocity = casing.velocity.add(0, -GRAVITY, 0).scale(DRAG);
		casing.rotation.rotateX(casing.spinX).rotateY(casing.spinY);
		Vec3 to = casing.pos.add(casing.velocity);
		BlockHitResult hit = level.clip(new ClipContext(casing.pos, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
		if (hit.getType() == HitResult.Type.MISS) {
			casing.pos = to;
			return;
		}
		if (hit.isInside()) {
			// Thrown out inside a block, against a wall: nothing to show.
			casing.age = LIFETIME_TICKS;
			return;
		}
		Vec3 normal = hit.getDirection().getUnitVec3();
		double into = -casing.velocity.dot(normal);
		Vec3 along = casing.velocity.add(normal.scale(into));
		if (into > REST_SPEED) {
			RandomSource random = level.getRandom();
			level.playLocalSound(hit.getLocation().x, hit.getLocation().y, hit.getLocation().z, casing.landing, SoundSource.PLAYERS,
					(float)Math.min(1, into * 3), 0.9F + random.nextFloat() * 0.3F, false);
		}
		casing.velocity = along.scale(FRICTION).add(normal.scale(into * BOUNCE));
		casing.pos = hit.getLocation().add(normal.scale(HALF_WIDTH));
		casing.spinX *= 0.5F;
		casing.spinY *= 0.5F;
		if (hit.getDirection() == Direction.UP && casing.velocity.length() < REST_SPEED) {
			casing.velocity = Vec3.ZERO;
			casing.resting = true;
			// Lying on its side, along the way it was pointing.
			Vector3f axis = casing.rotation.transform(new Vector3f(0, 0, 1));
			casing.rotation.rotationY((float)Mth.atan2(axis.x, axis.z));
		}
	}

	private static void submit(LevelRenderContext context) {
		if (ACTIVE.isEmpty() || level == null) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		Vec3 camera = minecraft.gameRenderer.mainCamera().position();
		// Our newest casings are drawn with the gun while it is being drawn (submitInHand), and here otherwise, so they never go missing.
		boolean handPass = System.nanoTime() - handPassNanos < 100_000_000L;
		draw(context.submitNodeCollector(), context.poseStack(), minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false), casing -> !handPass || !casing.inHand(),
				(poseStack, pos, rotation) -> {
					poseStack.translate(pos.x - camera.x, pos.y - camera.y, pos.z - camera.z);
					poseStack.rotate(rotation);
				});
	}

	/**
	 * Our casings that have just left the first-person gun, drawn in the hand's view space with it: the hand is drawn over the world,
	 * so in the world they would show only once clear of the gun on screen, as if thrown through it. Here the slide hides them while
	 * they are inside it, and they show through the open port.
	 */
	public static void submitInHand(PoseStack poseStack, SubmitNodeCollector collector, float partialTick) {
		handPassNanos = System.nanoTime();
		if (level == null || ACTIVE.stream().noneMatch(Casing::inHand)) {
			return;
		}
		Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();
		Vec3 eye = camera.position();
		float scale = Viewmodel.fovScale(camera);
		Vector3fc left = camera.leftVector();
		Vector3fc up = camera.upVector();
		Vector3fc forward = camera.forwardVector();
		// World to view space (right, up, back); positions on screen stay put by undoing the hand's FOV sideways.
		Matrix3f toView = new Matrix3f(-left.x(), up.x(), -forward.x(), -left.y(), up.y(), -forward.y(), -left.z(), up.z(), -forward.z());
		Quaternionf toViewRotation = toView.getNormalizedRotation(new Quaternionf());
		draw(collector, poseStack, partialTick, Casing::inHand, (stack, pos, rotation) -> {
			Vector3f view = toView.transform(new Vector3f((float)(pos.x - eye.x), (float)(pos.y - eye.y), (float)(pos.z - eye.z)));
			stack.translate(view.x / scale, view.y / scale, view.z);
			stack.rotate(new Quaternionf(toViewRotation).mul(rotation));
			stack.scale(1 / scale, 1 / scale, 1 / scale);
		});
	}

	private interface Placement {
		void place(PoseStack poseStack, Vec3 pos, Quaternionf rotation);
	}

	private static void draw(SubmitNodeCollector collector, PoseStack poseStack, float partialTick, Predicate<Casing> which, Placement placement) {
		List<PoseStack.Pose> poses = new ArrayList<>(ACTIVE.size());
		List<Integer> lights = new ArrayList<>(ACTIVE.size());
		for (Casing casing : ACTIVE) {
			if (!which.test(casing)) {
				continue;
			}
			Vec3 pos = casing.prevPos.lerp(casing.pos, partialTick);
			float shrink = Math.min(1, (LIFETIME_TICKS - casing.age + 1 - partialTick) / SHRINK_TICKS);
			poseStack.pushPose();
			placement.place(poseStack, pos, new Quaternionf(casing.prevRotation).slerp(casing.rotation, partialTick));
			poseStack.scale(shrink, shrink, shrink);
			poses.add(poseStack.last().copy());
			poseStack.popPose();
			lights.add(LightCoordsUtil.getLightCoords(level, BlockPos.containing(pos)));
		}
		if (!poses.isEmpty()) {
			collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(TEXTURE), (pose, buffer) -> {
				for (int i = 0; i < poses.size(); i++) {
					box(buffer, poses.get(i), lights.get(i));
				}
			});
		}
	}

	/** The casing as a long box: brass sides, the primer on the head (+Z) and the open mouth (-Z), on a 16x4 texture. */
	private static void box(VertexConsumer buffer, PoseStack.Pose pose, int light) {
		float w = HALF_WIDTH;
		float l = HALF_LENGTH;
		// Sides run from the mouth (u 0) to the head (u 0.5).
		face(buffer, pose, light, 0, 1, 0, new float[] {-w, w, -l, w, w, -l, w, w, l, -w, w, l}, 0, 0.5F);
		face(buffer, pose, light, 0, -1, 0, new float[] {-w, -w, l, w, -w, l, w, -w, -l, -w, -w, -l}, 0.5F, 0);
		face(buffer, pose, light, 1, 0, 0, new float[] {w, w, -l, w, -w, -l, w, -w, l, w, w, l}, 0, 0.5F);
		face(buffer, pose, light, -1, 0, 0, new float[] {-w, w, l, -w, -w, l, -w, -w, -l, -w, w, -l}, 0.5F, 0);
		end(buffer, pose, light, 1, new float[] {-w, w, l, w, w, l, w, -w, l, -w, -w, l}, 0.5F);
		end(buffer, pose, light, -1, new float[] {w, w, -l, -w, w, -l, -w, -w, -l, w, -w, -l}, 0.75F);
	}

	/** A side face; its first two corners are at u0 and its last two at u1, top to bottom of the texture. */
	private static void face(VertexConsumer buffer, PoseStack.Pose pose, int light, float nx, float ny, float nz, float[] corners, float u0, float u1) {
		float[] u = {u0, u0, u1, u1};
		float[] v = {0, 1, 1, 0};
		for (int i = 0; i < 4; i++) {
			vertex(buffer, pose, light, corners[i * 3], corners[i * 3 + 1], corners[i * 3 + 2], u[i], v[i], nx, ny, nz);
		}
	}

	private static void end(VertexConsumer buffer, PoseStack.Pose pose, int light, float nz, float[] corners, float u0) {
		float[] u = {u0, u0 + 0.25F, u0 + 0.25F, u0};
		float[] v = {0, 0, 1, 1};
		for (int i = 0; i < 4; i++) {
			vertex(buffer, pose, light, corners[i * 3], corners[i * 3 + 1], corners[i * 3 + 2], u[i], v[i], 0, 0, nz);
		}
	}

	private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, int light, float x, float y, float z, float u, float v, float nx, float ny, float nz) {
		buffer.addVertex(pose, x, y, z).setColor(-1).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
	}
}

package com.noxloveya.mcweapons.client.render;

import com.geckolib.animatable.client.GeoRenderProvider;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.noxloveya.mcweapons.McWeapons;
import com.noxloveya.mcweapons.gun.GunItem;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
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
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

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
	private static final List<Casing> ACTIVE = new ArrayList<>();
	private static @Nullable ClientLevel level;

	private Casings() {
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

		Casing(Vec3 pos, Vec3 velocity, Quaternionf rotation, float spinX, float spinY, SoundEvent landing) {
			this.pos = this.prevPos = pos;
			this.velocity = velocity;
			this.rotation = rotation;
			this.prevRotation = new Quaternionf(rotation);
			this.spinX = spinX;
			this.spinY = spinY;
			this.landing = landing;
		}
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(Casings::tick);
		LevelRenderEvents.COLLECT_SUBMITS.register(Casings::submit);
	}

	/** A casing flies out of the shooter's ejection port, up and to the right of where the gun points, carried along by their movement. */
	public static void eject(Entity shooter, GunItem gun) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null) {
			return;
		}
		// Our own gun in first person ejects where the viewmodel's chamber is drawn; anyone else's from their hand.
		Vec3 port = shooter == minecraft.player && minecraft.options.getCameraType().isFirstPerson()
				&& GeoRenderProvider.of(minecraft.player.getMainHandItem()).getGeoItemRenderer() instanceof GunRenderer renderer
				? Viewmodel.bonePosition(renderer, "chamber_round", minecraft.gameRenderer.mainCamera(), minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false))
				: ThirdPersonGuns.heldPoint(shooter, 0.4);
		Vec3 look = shooter.getLookAngle();
		Vec3 right = look.cross(new Vec3(0, 1, 0));
		right = right.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : right.normalize();
		Vec3 up = right.cross(look).normalize();
		RandomSource random = minecraft.level.getRandom();
		// Up, to the right and a little back, so they land beside the shooter (the first-person port is drawn well out in front of the eye).
		Vec3 velocity = right.scale(0.13 + random.nextDouble() * 0.05).add(up.scale(0.12 + random.nextDouble() * 0.06)).add(look.scale(-0.04 - random.nextDouble() * 0.03))
				.add(shooter.position().subtract(shooter.xo, shooter.yo, shooter.zo));
		// The head (+Z of the casing) faces back at the shooter as it leaves the chamber.
		Quaternionf rotation = new Quaternionf().rotationTo(new Vector3f(0, 0, 1), look.scale(-1).toVector3f());
		if (ACTIVE.size() >= MAX) {
			ACTIVE.removeFirst();
		}
		ACTIVE.add(new Casing(port, velocity, rotation, 0.5F + random.nextFloat() * 0.4F, (random.nextFloat() - 0.5F) * 0.6F, gun.sound("casing")));
	}

	private static void tick(Minecraft minecraft) {
		if (minecraft.level != level) {
			ACTIVE.clear();
			level = minecraft.level;
		}
		if (level == null || minecraft.isPaused() || ACTIVE.isEmpty()) {
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
		float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 camera = minecraft.gameRenderer.mainCamera().position();
		PoseStack poseStack = context.poseStack();
		List<PoseStack.Pose> poses = new ArrayList<>(ACTIVE.size());
		List<Integer> lights = new ArrayList<>(ACTIVE.size());
		for (Casing casing : ACTIVE) {
			Vec3 pos = casing.prevPos.lerp(casing.pos, partialTick);
			float scale = Math.min(1, (LIFETIME_TICKS - casing.age + 1 - partialTick) / SHRINK_TICKS);
			poseStack.pushPose();
			poseStack.translate(pos.x - camera.x, pos.y - camera.y, pos.z - camera.z);
			poseStack.rotate(new Quaternionf(casing.prevRotation).slerp(casing.rotation, partialTick));
			poseStack.scale(scale, scale, scale);
			poses.add(poseStack.last().copy());
			poseStack.popPose();
			lights.add(LightCoordsUtil.getLightCoords(level, BlockPos.containing(pos)));
		}
		context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.entityCutout(TEXTURE), (pose, buffer) -> {
			for (int i = 0; i < poses.size(); i++) {
				box(buffer, poses.get(i), lights.get(i));
			}
		});
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

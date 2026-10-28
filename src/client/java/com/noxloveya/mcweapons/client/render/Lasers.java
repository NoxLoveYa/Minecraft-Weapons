package com.noxloveya.mcweapons.client.render;

import com.geckolib.animatable.client.GeoRenderProvider;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.noxloveya.mcweapons.gun.AttachmentItem;
import com.noxloveya.mcweapons.gun.AttachmentSlot;
import com.noxloveya.mcweapons.gun.Attachments;
import com.noxloveya.mcweapons.gun.GunItem;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.registries.BuiltInRegistries;
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
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Laser sights: a thin red beam from each fitted laser's emitter, where the gun is drawn (first person, or in someone's hand), along the
 * gun to the first block or body it meets, with a glowing dot there. Every client draws everyone's from what it already has (attachments,
 * the gun's drawn pose, where players look). Looks only.
 */
public final class Lasers {
	private static final double RANGE = 48;
	/** Holders further away than this from the camera are not drawn, beam or dot. */
	private static final double DRAW_DISTANCE = 96;
	private static final long STALE_NANOS = 200_000_000L;
	private static final int RED = 0xFF2A1A;
	/** Lasers are zeroed to the sights at this many blocks: the dot crosses the sight line there, just under it closer, just over it further. */
	private static final float ZERO = 10;
	/** How each holder's laser emitter was last drawn in third person, relative to the camera, by entity id. */
	private static final Map<Integer, Held> HELD = new HashMap<>();

	public record Emitter(Vec3 pos, Vec3 forward) {
	}

	private record Held(Matrix4f pose, Vec3 camera, long nanos) {
	}

	private record Beam(Vec3 from, Vec3 to, boolean hit, boolean lens) {
	}

	private Lasers() {
	}

	public static void register() {
		LevelRenderEvents.COLLECT_SUBMITS.register(Lasers::submit);
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			long now = System.nanoTime();
			HELD.values().removeIf(held -> minecraft.level == null || now - held.nanos > 1_000_000_000L);
		});
	}

	/** Called by {@link BonePoseLayer} with the pose a held gun's laser emitter is drawn with, relative to the camera. */
	static void recordHeld(Entity holder, Matrix4fc pose) {
		HELD.put(holder.getId(), new Held(new Matrix4f(pose), Minecraft.getInstance().gameRenderer.mainCamera().position(), System.nanoTime()));
	}

	/**
	 * Which way the beam leaves the emitter, in the emitter bone's frame (the gun's at rest): toward the point {@link #ZERO} blocks out
	 * along the sight line (the fitted optic's reticle, or the iron sights). Straight ahead when the model cannot say.
	 */
	private static Vector3f zeroed(ItemStack stack) {
		if (GeoRenderProvider.of(stack).getGeoItemRenderer() instanceof GunRenderer renderer) {
			for (AttachmentSlot slot : AttachmentSlot.values()) {
				AttachmentItem attachment = Attachments.in(stack, slot);
				if (attachment != null && attachment.isLaser()) {
					Vector3f emitter = renderer.restPivot(BuiltInRegistries.ITEM.getKey(attachment).getPath() + "_emitter");
					Vector3f zero = renderer.restPivot(renderer.aimBone(stack)).add(0, 0, -ZERO);
					return zero.sub(emitter).normalize();
				}
			}
		}
		return new Vector3f(0, 0, -1);
	}

	/** A laser is fitted to the gun in {@code holder}'s hand, and it is not being fitted or taken off (it is switched on once it is on the rail). */
	private static boolean shining(LivingEntity holder) {
		ItemStack stack = holder.getMainHandItem();
		if (!(stack.getItem() instanceof GunItem gun) || holder.isInvisible() || holder.isSpectator()) {
			return false;
		}
		for (AttachmentSlot slot : AttachmentSlot.values()) {
			AttachmentItem attachment = Attachments.in(stack, slot);
			if (attachment != null && attachment.isLaser()) {
				var main = gun.getAnimatableInstanceCache().getManagerForId(GunRenderer.instanceId(holder)).getAnimationControllers().get(GunItem.MAIN_CONTROLLER);
				// A finished triggered animation stays the controller's current one until the next is triggered, hence the finished check.
				return main == null || main.hasAnimationFinished() || !main.isTriggeredAnimation(slot.attachAction()) && !main.isTriggeredAnimation(slot.detachAction());
			}
		}
		return false;
	}

	/** Where {@code holder}'s laser leaves from and points: as last drawn, or, when it has not been drawn lately, about there along the look. */
	private static Emitter emitter(Minecraft minecraft, Camera camera, LivingEntity holder, float partialTick) {
		Vector3f zeroed = zeroed(holder.getMainHandItem());
		if (holder == minecraft.player && minecraft.options.getCameraType().isFirstPerson()) {
			Emitter drawn = Viewmodel.laserEmitter(camera, zeroed);
			if (drawn != null) {
				return drawn;
			}
		} else {
			Held held = HELD.get(holder.getId());
			if (held != null && System.nanoTime() - held.nanos < STALE_NANOS) {
				Vec3 pos = held.camera.add(new Vec3(held.pose.transformPosition(new Vector3f())));
				// Aiming (or just fired), the third-person arms only roughly line the gun up with the look, so the beam goes where they aim.
				return new Emitter(pos, ThirdPersonGuns.pose(holder) == ThirdPersonGuns.Pose.AIM ? holder.getViewVector(partialTick)
						: new Vec3(held.pose.transformDirection(zeroed)).normalize());
			}
		}
		return new Emitter(ThirdPersonGuns.heldPoint(holder, 0.9).add(0, -0.08, 0), holder.getViewVector(partialTick));
	}

	private static Beam cast(ClientLevel level, LivingEntity holder, Emitter emitter, boolean lens) {
		Vec3 from = emitter.pos();
		Vec3 end = from.add(emitter.forward().scale(RANGE));
		BlockHitResult block = level.clip(new ClipContext(from, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
		boolean hit = block.getType() != HitResult.Type.MISS;
		Vec3 to = hit ? block.getLocation() : end;
		EntityHitResult entity = ProjectileUtil.getEntityHitResult(holder, from, to, new AABB(from, to).inflate(1.0),
				candidate -> candidate != holder && candidate.isPickable() && !candidate.isSpectator(), from.distanceToSqr(to));
		if (entity != null) {
			to = entity.getLocation();
			hit = true;
		}
		return new Beam(from, to, hit, lens);
	}

	private static void submit(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null) {
			return;
		}
		float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Camera camera = minecraft.gameRenderer.mainCamera();
		Vec3 cameraPos = camera.position();
		List<Beam> beams = new ArrayList<>();
		for (AbstractClientPlayer player : level.players()) {
			if (player.position().distanceToSqr(cameraPos) > DRAW_DISTANCE * DRAW_DISTANCE || !shining(player)) {
				continue;
			}
			boolean ownFirstPerson = player == minecraft.player && minecraft.options.getCameraType().isFirstPerson();
			// While the tuner shows the gun off sideways, its beam would only sweep the room.
			if (ownFirstPerson && Viewmodel.showcasing()) {
				continue;
			}
			beams.add(cast(level, player, emitter(minecraft, camera, player, partialTick), !ownFirstPerson));
		}
		if (beams.isEmpty()) {
			return;
		}
		float seconds = (level.getGameTime() + partialTick) / 20;
		// The dot where it lands, and the lens seen from outside: x, y, z from the camera, radius, strength.
		List<float[]> glows = new ArrayList<>();
		for (Beam beam : beams) {
			float flicker = 1 + 0.07F * Mth.sin(seconds * 31) * Mth.sin(seconds * 17 + beam.from.hashCode());
			if (beam.hit) {
				// Pulled toward the camera a little, so the surface it lies on does not cut through it.
				Vec3 at = beam.to.add(cameraPos.subtract(beam.to).normalize().scale(0.03)).subtract(cameraPos);
				float radius = Math.max(0.02F, (float)at.length() * 0.0035F) * flicker;
				glows.add(new float[] {(float)at.x, (float)at.y, (float)at.z, radius, 1});
			}
			if (beam.lens) {
				// Brightest when the laser points at the camera.
				Vec3 toCamera = cameraPos.subtract(beam.from).normalize();
				float facing = Math.max(0, (float)toCamera.dot(beam.to.subtract(beam.from).normalize()));
				Vec3 at = beam.from.subtract(cameraPos);
				float radius = Math.max(0.015F, (float)at.length() * 0.003F) * (1 + 2 * facing * facing) * flicker;
				glows.add(new float[] {(float)at.x, (float)at.y, (float)at.z, radius, 0.6F + 0.4F * facing});
			}
		}
		Vector3fc left = camera.leftVector();
		Vector3fc up = camera.upVector();
		// One submit per render type (a second one in the same frame is not drawn). An additive glow and halo, then a thin normally blended
		// core and dot on top, so it stays red against sand and sky (as tracers do).
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.lightning(), (pose, buffer) -> {
			beams.forEach(beam -> beam(buffer, pose, beam, cameraPos, 0.0035F, 0.002F, 0.32F, seconds));
			glows.forEach(glow -> disc(buffer, pose, glow, left, up, glow[3] * 4, ((int)(0x90 * glow[4]) << 24) | RED));
		});
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.debugQuads(), (pose, buffer) -> {
			beams.forEach(beam -> beam(buffer, pose, beam, cameraPos, 0.0008F, 0.0006F, 0.85F, seconds));
			glows.forEach(glow -> disc(buffer, pose, glow, left, up, glow[3], ((int)(0xFF * glow[4]) << 24) | 0xFF5A48));
		});
	}

	/**
	 * The beam as camera-facing segments about half a block long, so it can fade with distance and shimmer like dust drifting through
	 * it. Widths grow with distance from the camera, so it keeps the same thin look on screen near and far. Both windings, so culling never hides it.
	 */
	private static void beam(VertexConsumer buffer, PoseStack.Pose pose, Beam beam, Vec3 camera, float widthPerBlock, float minWidth, float alpha, float seconds) {
		double length = beam.from.distanceTo(beam.to);
		if (length < 0.01) {
			return;
		}
		Vec3 direction = beam.to.subtract(beam.from).scale(1 / length);
		int segments = Mth.clamp(Mth.ceil(length / 0.5), 1, 96);
		Vector3f back = beam.from.subtract(camera).toVector3f();
		int backColor = color(alpha, 0, seconds);
		for (int i = 1; i <= segments; i++) {
			double distance = length * i / segments;
			Vector3f front = beam.from.add(direction.scale(distance)).subtract(camera).toVector3f();
			Vector3f side = new Vector3f(front).sub(back).cross(new Vector3f(back).add(front).mul(0.5F));
			int frontColor = color(alpha, distance, seconds);
			if (side.lengthSquared() > 1.0E-12F) {
				side.normalize();
				Vector3f b = new Vector3f(side).mul(Math.max(minWidth, back.length() * widthPerBlock));
				Vector3f f = new Vector3f(side).mul(Math.max(minWidth, front.length() * widthPerBlock));
				buffer.addVertex(pose, back.x - b.x, back.y - b.y, back.z - b.z).setColor(backColor);
				buffer.addVertex(pose, back.x + b.x, back.y + b.y, back.z + b.z).setColor(backColor);
				buffer.addVertex(pose, front.x + f.x, front.y + f.y, front.z + f.z).setColor(frontColor);
				buffer.addVertex(pose, front.x - f.x, front.y - f.y, front.z - f.z).setColor(frontColor);
				buffer.addVertex(pose, front.x - f.x, front.y - f.y, front.z - f.z).setColor(frontColor);
				buffer.addVertex(pose, front.x + f.x, front.y + f.y, front.z + f.z).setColor(frontColor);
				buffer.addVertex(pose, back.x + b.x, back.y + b.y, back.z + b.z).setColor(backColor);
				buffer.addVertex(pose, back.x - b.x, back.y - b.y, back.z - b.z).setColor(backColor);
			}
			back = front;
			backColor = frontColor;
		}
	}

	/** Red at {@code alpha}, fading to a quarter over the first dozen blocks, with a slow shimmer drifting outward. */
	private static int color(float alpha, double distance, float seconds) {
		float fade = 0.25F + 0.75F * (float)Math.exp(-distance / 12);
		float shimmer = 0.8F + 0.2F * Mth.sin((float)distance * 2.7F - seconds * 5) * Mth.sin((float)distance * 1.3F + seconds * 1.7F);
		return ((int)(Mth.clamp(alpha * fade * shimmer, 0, 1) * 255) << 24) | RED;
	}

	/**
	 * A camera-facing disc at {@code glow} (x, y, z relative to the camera), {@code color} at the center fading to nothing at the rim: a
	 * fan of 12 wedges, each a quad with two corners at the center. Both windings, so culling never hides it.
	 */
	private static void disc(VertexConsumer buffer, PoseStack.Pose pose, float[] glow, Vector3fc left, Vector3fc up, float radius, int color) {
		int rim = color & 0x00FFFFFF;
		for (int i = 0; i < 12; i++) {
			float a0 = i * Mth.TWO_PI / 12;
			float a1 = (i + 1) * Mth.TWO_PI / 12;
			float x0 = Mth.cos(a0) * radius, y0 = Mth.sin(a0) * radius, x1 = Mth.cos(a1) * radius, y1 = Mth.sin(a1) * radius;
			float ex0 = glow[0] + left.x() * x0 + up.x() * y0, ey0 = glow[1] + left.y() * x0 + up.y() * y0, ez0 = glow[2] + left.z() * x0 + up.z() * y0;
			float ex1 = glow[0] + left.x() * x1 + up.x() * y1, ey1 = glow[1] + left.y() * x1 + up.y() * y1, ez1 = glow[2] + left.z() * x1 + up.z() * y1;
			buffer.addVertex(pose, glow[0], glow[1], glow[2]).setColor(color);
			buffer.addVertex(pose, ex0, ey0, ez0).setColor(rim);
			buffer.addVertex(pose, ex1, ey1, ez1).setColor(rim);
			buffer.addVertex(pose, glow[0], glow[1], glow[2]).setColor(color);
			buffer.addVertex(pose, glow[0], glow[1], glow[2]).setColor(color);
			buffer.addVertex(pose, ex1, ey1, ez1).setColor(rim);
			buffer.addVertex(pose, ex0, ey0, ez0).setColor(rim);
			buffer.addVertex(pose, glow[0], glow[1], glow[2]).setColor(color);
		}
	}
}

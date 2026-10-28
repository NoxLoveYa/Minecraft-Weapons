package com.noxloveya.mcweapons.client.render;

import com.geckolib.animatable.client.GeoRenderProvider;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.noxloveya.mcweapons.gun.AttachmentItem;
import com.noxloveya.mcweapons.gun.AttachmentSlot;
import com.noxloveya.mcweapons.gun.Attachments;
import com.noxloveya.mcweapons.gun.GunItem;
import com.noxloveya.mcweapons.gun.Reticle;
import com.noxloveya.mcweapons.registry.ModComponents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.ARGB;
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
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Laser sights: a thin beam, in the color set on the gun, from each fitted laser's emitter, where the gun is drawn (first person, or in someone's hand), along the
 * gun to the first block or body it meets, with a glowing dot there. Every client draws everyone's from what it already has (attachments,
 * the gun's drawn pose, where players look). Looks only.
 */
public final class Lasers {
	private static final double RANGE = 48;
	/** Holders further away than this from the camera are not drawn, beam or dot. */
	private static final double DRAW_DISTANCE = 96;
	private static final long STALE_NANOS = 200_000_000L;
	/** At rest a laser points at the sight line this many blocks out; from there it settles on whatever the holder aims at (see {@link #emitter}). */
	private static final float ZERO = 10;
	/** Within this angle (degrees) of what the holder aims at, the first-person beam lands on it; it lets go fully by {@link #LOCK_FADE_DEG}. */
	private static final double LOCK_DEG = 10;
	private static final double LOCK_FADE_DEG = 18;
	/**
	 * On a mob or player the dot is the hitbox's face, but arms and heads stick out of the hitbox toward the shooter (a zombie's arms by
	 * almost half a block) and hid it. So the dot and the beam's last {@link #ON_BODY_STRETCH} blocks are drawn this far toward the camera,
	 * along each point's own line of sight: the same pixels, in front of the model.
	 */
	private static final float BODY_PULL = 0.8F;
	private static final double ON_BODY_STRETCH = 1.5;
	/** How each holder's laser emitter was last drawn in third person, relative to the camera, by entity id. */
	private static final Map<Integer, Held> HELD = new HashMap<>();

	public record Emitter(Vec3 pos, Vec3 forward) {
	}

	private record Held(Matrix4f pose, Vec3 camera, long nanos) {
	}

	private record Beam(Vec3 from, Vec3 to, boolean hit, boolean onBody, boolean lens, int color) {
	}

	/** Where a ray stopped, and whether on a body rather than a block. */
	private record Hit(Vec3 at, boolean body) {
	}

	/** A glow at {@code at} (from the camera): the dot where a beam lands, or a lens seen from outside. */
	private record Glow(Vector3f at, float radius, float strength, int color) {
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

	/**
	 * Where {@code holder}'s laser leaves from and points. It leaves from the emitter as last drawn, or about there along the look when
	 * that has not been drawn lately. It points along the gun, except that it lands on whatever the holder aims at: in first person while
	 * the gun points within a few degrees of it (so sway and recoil still move it, and reloads and inspects swing it away), in third
	 * person while they aim, since the arms there only roughly line the gun up. Straight along the bore, the dot would sit beside a narrow
	 * target the crosshair is on, or behind the gun when aiming close up.
	 */
	private static Emitter emitter(Minecraft minecraft, ClientLevel level, Camera camera, LivingEntity holder, float partialTick) {
		Vector3f zeroed = zeroed(holder.getMainHandItem());
		Vec3 aimed = firstHitOr(level, holder, holder.getEyePosition(partialTick), holder.getViewVector(partialTick));
		if (holder == minecraft.player && minecraft.options.getCameraType().isFirstPerson()) {
			Emitter drawn = Viewmodel.laserEmitter(camera, zeroed);
			if (drawn != null) {
				Vec3 toAim = aimed.subtract(drawn.pos()).normalize();
				double off = Math.toDegrees(Math.acos(Mth.clamp(drawn.forward().dot(toAim), -1, 1)));
				double lock = Mth.clamp((LOCK_FADE_DEG - off) / (LOCK_FADE_DEG - LOCK_DEG), 0, 1);
				return new Emitter(drawn.pos(), drawn.forward().scale(1 - lock).add(toAim.scale(lock)).normalize());
			}
		} else {
			Held held = HELD.get(holder.getId());
			if (held != null && System.nanoTime() - held.nanos < STALE_NANOS) {
				Vec3 pos = held.camera.add(new Vec3(held.pose.transformPosition(new Vector3f())));
				return new Emitter(pos, ThirdPersonGuns.pose(holder) == ThirdPersonGuns.Pose.AIM ? aimed.subtract(pos).normalize()
						: new Vec3(held.pose.transformDirection(zeroed)).normalize());
			}
		}
		Vec3 pos = ThirdPersonGuns.heldPoint(holder, 0.9).add(0, -0.08, 0);
		return new Emitter(pos, aimed.subtract(pos).normalize());
	}

	/** The first block or body (not {@code holder}) along {@code direction} from {@code from} within {@link #RANGE}, or null. */
	private static @Nullable Hit firstHit(ClientLevel level, LivingEntity holder, Vec3 from, Vec3 direction) {
		Vec3 end = from.add(direction.scale(RANGE));
		BlockHitResult block = level.clip(new ClipContext(from, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
		Vec3 to = block.getType() != HitResult.Type.MISS ? block.getLocation() : end;
		EntityHitResult entity = ProjectileUtil.getEntityHitResult(holder, from, to, new AABB(from, to).inflate(1.0),
				candidate -> candidate != holder && candidate.isPickable() && !candidate.isSpectator(), from.distanceToSqr(to));
		return entity != null ? new Hit(entity.getLocation(), true) : block.getType() != HitResult.Type.MISS ? new Hit(to, false) : null;
	}

	private static Vec3 firstHitOr(ClientLevel level, LivingEntity holder, Vec3 from, Vec3 direction) {
		Hit hit = firstHit(level, holder, from, direction);
		return hit != null ? hit.at : from.add(direction.scale(RANGE));
	}

	private static Beam cast(ClientLevel level, LivingEntity holder, Emitter emitter, boolean lens) {
		int color = holder.getMainHandItem().getOrDefault(ModComponents.LASER_COLOR, Reticle.COLORS.getFirst());
		Vec3 from = emitter.pos();
		Hit hit = firstHit(level, holder, from, emitter.forward());
		Vec3 to = hit != null ? hit.at : from.add(emitter.forward().scale(RANGE));
		return new Beam(from, to, hit != null, hit != null && hit.body, lens, color);
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
			beams.add(cast(level, player, emitter(minecraft, level, camera, player, partialTick), !ownFirstPerson));
		}
		if (beams.isEmpty()) {
			return;
		}
		float seconds = (level.getGameTime() + partialTick) / 20;
		List<Glow> glows = new ArrayList<>();
		for (Beam beam : beams) {
			float flicker = 1 + 0.07F * Mth.sin(seconds * 31) * Mth.sin(seconds * 17 + beam.from.hashCode());
			if (beam.hit) {
				// Pulled toward the camera a little, so the surface it lies on does not cut through it; on a body, past its arms and head.
				Vector3f at = beam.to.subtract(cameraPos).toVector3f();
				float radius = Math.max(0.025F, at.length() * 0.006F) * flicker;
				float scale = pull(at, beam.onBody ? BODY_PULL : 0.03F);
				glows.add(new Glow(at.mul(scale), radius * scale, 1, beam.color));
			}
			if (beam.lens) {
				// Brightest when the laser points at the camera.
				Vec3 toCamera = cameraPos.subtract(beam.from).normalize();
				float facing = Math.max(0, (float)toCamera.dot(beam.to.subtract(beam.from).normalize()));
				Vec3 at = beam.from.subtract(cameraPos);
				float radius = Math.max(0.01F, (float)at.length() * 0.002F) * (1 + 1.2F * facing * facing) * flicker;
				glows.add(new Glow(at.toVector3f(), radius, 0.5F + 0.4F * facing, beam.color));
			}
		}
		Vector3fc left = camera.leftVector();
		Vector3fc up = camera.upVector();
		// One submit per render type (a second one in the same frame is not drawn). An additive glow and a small halo, then a thin normally
		// blended core and dot on top, so the color holds against sand and sky (as tracers do).
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.lightning(), (pose, buffer) -> {
			beams.forEach(beam -> beam(buffer, pose, beam, cameraPos, 0.005F, 0.003F, 0.42F, seconds));
			glows.forEach(glow -> disc(buffer, pose, glow.at, left, up, glow.radius * 2, ((int)(0x70 * glow.strength) << 24) | glow.color));
		});
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.debugQuads(), (pose, buffer) -> {
			beams.forEach(beam -> beam(buffer, pose, beam, cameraPos, 0.0013F, 0.001F, 0.9F, seconds));
			// The dot: the color, and a hot whiter center on top, as a laser spot looks.
			glows.forEach(glow -> {
				disc(buffer, pose, glow.at, left, up, glow.radius, ((int)(0xFF * glow.strength) << 24) | glow.color);
				disc(buffer, pose, glow.at, left, up, glow.radius * 0.55F, ((int)(0xFF * glow.strength) << 24) | ARGB.srgbLerp(0.5F, glow.color, 0xFFFFFF));
			});
		});
	}

	/**
	 * The beam as camera-facing segments about half a block long, so it can fade with distance and shimmer like dust drifting through
	 * it. Widths grow with distance from the camera, so it keeps the same thin look on screen near and far (pulled points keep it too, as
	 * their width follows their pulled distance). Both windings, so culling never hides it.
	 */
	private static void beam(VertexConsumer buffer, PoseStack.Pose pose, Beam beam, Vec3 camera, float widthPerBlock, float minWidth, float alpha, float seconds) {
		double length = beam.from.distanceTo(beam.to);
		if (length < 0.01) {
			return;
		}
		Vec3 direction = beam.to.subtract(beam.from).scale(1 / length);
		int segments = Mth.clamp(Mth.ceil(length / 0.5), 1, 96);
		Vector3f back = onBody(beam, beam.from.subtract(camera).toVector3f(), length, 0);
		int backColor = color(beam.color, alpha, 0, seconds);
		for (int i = 1; i <= segments; i++) {
			double distance = length * i / segments;
			Vector3f front = onBody(beam, beam.from.add(direction.scale(distance)).subtract(camera).toVector3f(), length, distance);
			Vector3f side = new Vector3f(front).sub(back).cross(new Vector3f(back).add(front).mul(0.5F));
			int frontColor = color(beam.color, alpha, distance, seconds);
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

	/** A beam point {@code distance} along it, pulled toward the camera over the last stretch of a beam that ends on a body (see {@link #BODY_PULL}). */
	private static Vector3f onBody(Beam beam, Vector3f point, double length, double distance) {
		if (!beam.onBody) {
			return point;
		}
		float near = (float)Mth.clamp(1 - (length - distance) / ON_BODY_STRETCH, 0, 1);
		return point.mul(pull(point, BODY_PULL * near));
	}

	/** The scale that brings {@code point} (from the camera) {@code by} blocks closer along its line of sight, never past halfway. */
	private static float pull(Vector3f point, float by) {
		float distance = point.length();
		return distance < 1.0E-4F ? 1 : Math.max(0.5F, 1 - by / distance);
	}

	/** {@code rgb} at {@code alpha}, fading to 40% over the first dozen blocks, with a slow shimmer drifting outward. */
	private static int color(int rgb, float alpha, double distance, float seconds) {
		float fade = 0.4F + 0.6F * (float)Math.exp(-distance / 12);
		float shimmer = 0.8F + 0.2F * Mth.sin((float)distance * 2.7F - seconds * 5) * Mth.sin((float)distance * 1.3F + seconds * 1.7F);
		return ((int)(Mth.clamp(alpha * fade * shimmer, 0, 1) * 255) << 24) | rgb;
	}

	/**
	 * A camera-facing disc at {@code center} (relative to the camera), {@code color} at the center fading to nothing at the rim: a fan of
	 * 12 wedges, each a quad with two corners at the center. Both windings, so culling never hides it.
	 */
	private static void disc(VertexConsumer buffer, PoseStack.Pose pose, Vector3f center, Vector3fc left, Vector3fc up, float radius, int color) {
		float[] glow = {center.x, center.y, center.z};
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

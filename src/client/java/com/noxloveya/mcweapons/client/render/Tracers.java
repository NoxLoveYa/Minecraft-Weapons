package com.noxloveya.mcweapons.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/** Bullet tracers: a glowing streak that flies from the muzzle to where the server says the shot landed. Visual only. */
public final class Tracers {
	private static final float SPEED = 120; // blocks per second, slow enough to follow, fast enough to read as a bullet
	private static final float LENGTH = 8;
	// Half widths in blocks near the camera, and per block of distance further out, so the streak never thins below a few pixels.
	private static final float CORE_WIDTH = 0.018F;
	private static final float CORE_WIDTH_PER_BLOCK = 0.0028F;
	private static final float GLOW_WIDTH = 0.06F;
	private static final float GLOW_WIDTH_PER_BLOCK = 0.009F;
	private static final List<Tracer> ACTIVE = new ArrayList<>();

	private Tracers() {
	}

	private record Tracer(Vec3 from, Vec3 direction, double length, long startNanos) {
	}

	public static void register() {
		LevelRenderEvents.COLLECT_SUBMITS.register(Tracers::submit);
	}

	public static void spawn(Vec3 from, Vec3 to) {
		Vec3 path = to.subtract(from);
		double length = path.length();
		if (length > 0.5) {
			ACTIVE.add(new Tracer(from, path.scale(1 / length), length, System.nanoTime()));
		}
	}

	private static void submit(LevelRenderContext context) {
		if (ACTIVE.isEmpty()) {
			return;
		}
		long now = System.nanoTime();
		ACTIVE.removeIf(tracer -> travelled(tracer, now) - LENGTH > tracer.length);
		Vec3 camera = Minecraft.getInstance().gameRenderer.mainCamera().position();
		List<Vector3f[]> streaks = new ArrayList<>();
		for (Tracer tracer : ACTIVE) {
			double travelled = travelled(tracer, now);
			double head = Math.min(travelled, tracer.length);
			double tail = Math.max(0, travelled - LENGTH);
			if (head <= tail) {
				continue;
			}
			// Positions relative to the camera, which is where level geometry is submitted from.
			Vector3f back = tracer.from.add(tracer.direction.scale(tail)).subtract(camera).toVector3f();
			Vector3f front = tracer.from.add(tracer.direction.scale(head)).subtract(camera).toVector3f();
			// Widen the streak sideways to the view so it always faces the camera.
			Vector3f side = tracer.direction.toVector3f().cross(new Vector3f(back).add(front).mul(0.5F));
			if (side.lengthSquared() > 1.0E-8F) {
				streaks.add(new Vector3f[] {back, front, side.normalize()});
			}
		}
		// An additive glow, then a normally blended core on top: additive alone washes out to white against sand and sky.
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.lightning(), (pose, buffer) -> streaks.forEach(streak ->
				ribbon(buffer, pose, streak, GLOW_WIDTH, GLOW_WIDTH_PER_BLOCK, 0x00FF7A10, 0xD0FF9A28)));
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.debugQuads(), (pose, buffer) -> streaks.forEach(streak ->
				ribbon(buffer, pose, streak, CORE_WIDTH, CORE_WIDTH_PER_BLOCK, 0x00FFB040, 0xF0FFC64A)));
	}

	private static double travelled(Tracer tracer, long now) {
		return (now - tracer.startNanos) / 1.0E9 * SPEED;
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

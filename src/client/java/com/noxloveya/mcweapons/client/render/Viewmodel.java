package com.noxloveya.mcweapons.client.render;

import com.geckolib.animatable.client.GeoRenderProvider;
import com.geckolib.cache.model.GeoBone;
import com.geckolib.constant.DataTickets;
import com.geckolib.renderer.GeoItemRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.noxloveya.mcweapons.client.input.GunInput;
import com.noxloveya.mcweapons.gun.AttachmentItem;
import com.noxloveya.mcweapons.gun.AttachmentSlot;
import com.noxloveya.mcweapons.gun.Attachments;
import com.noxloveya.mcweapons.gun.GunDefinition;
import com.noxloveya.mcweapons.gun.GunFeel;
import com.noxloveya.mcweapons.gun.GunItem;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.ClientAvatarState;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/**
 * First-person gun. Draws the GeckoLib model straight in view space and adds, on top of the animation and in this
 * order: ADS lerp, sway, bob, recoil (DESIGN_RULES 4). State is ticked by {@link com.noxloveya.mcweapons.client.input.GunInput}.
 */
public final class Viewmodel {
	private static final float ADS_PUSH_FORWARD = 0.1F;
	private static final long MUZZLE_FLASH_NANOS = 45_000_000L;
	private static final float SHOWCASE_STEP = 0.15F;
	/** Seconds per breath of the idle layer, about a calm resting rate. */
	private static final float BREATH_PERIOD = 3.7F;
	/** Ticks the running pose takes to come up, and to go back down; aiming out of a sprint waits for it (see GunInput). */
	private static final int RUN_IN_TICKS = 5;
	private static final int RUN_OUT_TICKS = 4;
	/** Ticks a scope takes to come up to the eye, or back off it while the gun is busy or its bolt is worked. */
	private static final int SCOPE_TICKS = 3;
	private static final Matrix4f inverseBase = new Matrix4f();
	private static final Matrix4f gunPose = new Matrix4f();
	private static long gunPoseNanos;
	/** The ejection port's drawn pose in view space, for casings to leave from (see {@link #ejectionPort}). */
	private static final Matrix4f portPose = new Matrix4f();
	private static long portPoseNanos;
	/** A fitted laser's emitter as drawn in view space, for the beam to leave from (see {@link #laserEmitter}). */
	private static final Matrix4f emitterPose = new Matrix4f();
	private static long emitterPoseNanos;
	/** A fitted scope's eyepiece as drawn in view space, and its lens's radius in blocks, for its view (see {@link #lens}). */
	private static final Matrix4f lensPose = new Matrix4f();
	private static float lensRadius;
	private static long lensPoseNanos;
	/**
	 * The muzzle (a fitted silencer's tip, or the barrel's) as last drawn, in view space: sway, bob, breathing, the kick, the running pose
	 * and animations all in it. Shots leave along its line (see {@link #barrelLine}).
	 */
	private static final Matrix4f muzzlePose = new Matrix4f();
	private static long muzzlePoseNanos;
	private static boolean showcaseOpen;

	/** Sway, bob and recoil spring values of the held gun (its definition's "feel"). */
	private static GunFeel feel = GunFeel.DEFAULT;
	private static float ads;
	private static float adsO;
	private static float adsFovMultiplier = 1;
	/** A fitted scope's magnification, eased so a variable one's steps zoom smoothly; the view zooms by it at the eye. */
	private static float magnification = 1;
	private static float magnificationO = 1;
	/** 0 to 1 as a fitted scope comes to the eye: aimed, the gun then sits with its eyepiece its eye distance from the eye (Attachments.eyeDistance). */
	private static float scope;
	private static float scopeO;
	private static float recoilPitch;
	private static float recoil;
	private static float recoilO;
	private static float recoilVelocity;
	private static long muzzleFlashUntil;
	private static float muzzleFlashRoll;
	/** Where the gun points: it trails the view rotation, and the gap is the sway. */
	private static float swayYaw;
	private static float swayYawO;
	private static float swayPitch;
	private static float swayPitchO;
	private static boolean swaySnap = true;
	/** 0 in the hand, 1 turned sideways in the middle of the screen while the gun tuner is open. */
	private static float showcase;
	private static float showcaseO;
	/** 0 in the hand, 1 in the running pose while sprinting. */
	private static float run;
	private static float runO;
	/** 0 to 1 as the breath is held while aiming (GunInput.holdingBreath): the gun steadies and the view closes in a little. */
	private static float steady;
	private static float steadyO;
	/** With the breath held: how much of the sway and walk bob goes (the breathing motion goes entirely), and how much the view closes in. */
	private static final float BREATH_STEADY = 0.85F;
	private static final float BREATH_ZOOM = 0.08F;
	/** Looking up or down, the gun trails this many times further than turning sideways by as much: nods are smaller and slower than turns. */
	private static final float PITCH_SWAY = 2.5F;

	private Viewmodel() {
	}

	/**
	 * {@code scopeReady} says a fitted scope can be looked through (the gun is not busy): aiming then brings it to the eye, and the view
	 * zooms by {@code scopeMagnification}.
	 */
	public static void tick(LocalPlayer player, GunDefinition definition, boolean aiming, boolean running, boolean showcasing, boolean scopeReady, float scopeMagnification) {
		runO = run;
		run = Mth.clamp(run + (running ? 1.0F / RUN_IN_TICKS : -1.0F / RUN_OUT_TICKS), 0, 1);
		if (showcasing != showcaseOpen) {
			// Played here, as the showcase animation loops and would repeat a sound keyframe every second.
			player.playSound(((GunItem)player.getMainHandItem().getItem()).sound("handling"), 1.0F, 1.0F);
		}
		showcaseOpen = showcasing;
		showcaseO = showcase;
		showcase = Mth.clamp(showcase + (showcasing ? SHOWCASE_STEP : -SHOWCASE_STEP), 0, 1);
		if (swaySnap) {
			swayYaw = player.getYRot();
			swayPitch = player.getXRot();
			swaySnap = false;
		}
		swayYawO = swayYaw;
		swayPitchO = swayPitch;
		// Yaw is not wrapped by the game, but a teleport can still jump it by whole turns.
		swayYaw += Mth.wrapDegrees(player.getYRot() - swayYaw) * definition.feel().swayFollow();
		swayPitch += (player.getXRot() - swayPitch) * definition.feel().swayFollow();
		adsO = ads;
		float step = 1.0F / Math.max(1, definition.adsTicks());
		ads = Mth.clamp(ads + (aiming ? step : -step), 0, 1);
		adsFovMultiplier = definition.adsFovMultiplier();
		magnificationO = magnification;
		magnification += (scopeMagnification - magnification) * 0.5F;
		scopeO = scope;
		scope = Mth.clamp(scope + (scopeReady ? 1.0F : -1.0F) / SCOPE_TICKS, 0, 1);
		steadyO = steady;
		steady += ((GunInput.holdingBreath() ? 1 : 0) - steady) * 0.3F;
		recoilPitch = definition.recoilPitchDeg();
		feel = definition.feel();
		recoilO = recoil;
		recoilVelocity += -feel.recoilSpringStiffness() * recoil - feel.recoilSpringDamping() * recoilVelocity;
		recoil += recoilVelocity;
	}

	public static void reset() {
		ads = adsO = 0;
		scope = scopeO = 0;
		showcase = showcaseO = 0;
		showcaseOpen = false;
		run = runO = 0;
		steady = steadyO = 0;
		recoil = recoilO = recoilVelocity = 0;
		muzzleFlashUntil = 0;
		swaySnap = true;
		muzzlePoseNanos = 0;
	}

	/** A local shot: model kick, a camera pitch kick that does not recover by itself, and the muzzle flash. */
	public static void onShot(LocalPlayer player, GunDefinition definition) {
		recoil = Math.min(recoil + 1, definition.feel().recoilMaxKicks());
		player.setXRot(Mth.clamp(player.getXRot() - definition.recoilPitchDeg(), -90, 90));
		player.setYRot(player.getYRot() + (player.getRandom().nextFloat() * 2 - 1) * definition.recoilYawDeg());
		muzzleFlashUntil = System.nanoTime() + MUZZLE_FLASH_NANOS;
		muzzleFlashRoll = player.getRandom().nextFloat() * Mth.TWO_PI;
	}

	/** Aim progress from 0 (hip) to 1 (sights), eased. */
	public static float ads(float partialTick) {
		float t = Mth.lerp(partialTick, adsO, ads);
		return t * t * (3 - 2 * t);
	}

	/** True until the running pose is all the way down. */
	public static boolean running() {
		return run > 0;
	}

	public static float recoil(float partialTick) {
		return Mth.lerp(partialTick, recoilO, recoil);
	}

	/** How much of the camera's walk bob to keep: the held gun's bob_scale, so view and gun bob together, or all of it without a gun. */
	public static float viewBobScale() {
		LocalPlayer player = Minecraft.getInstance().player;
		return player != null && GunItem.holdsGun(player) ? feel.bobScale() * (1 - BREATH_STEADY * steady(1)) : 1;
	}

	/**
	 * How much aiming narrows the view: the gun's own zoom, a scope's magnification once it is at the eye (the lens pass, ScopeRenderer),
	 * and a little more with the breath held.
	 */
	public static float fovMultiplier(float partialTick) {
		float scoped = Mth.lerp(scopeView(partialTick), adsFovMultiplier, 1 / Mth.lerp(partialTick, magnificationO, magnification));
		return Mth.lerp(ads(partialTick), 1, scoped) * (1 - BREATH_ZOOM * steady(partialTick));
	}

	/** How far the breath is held, 0 to 1 (see {@link #steady}). */
	public static float steady(float partialTick) {
		float t = Mth.lerp(partialTick, steadyO, steady);
		return t * t * (3 - 2 * t);
	}

	/** How much slower the mouse turns the view: as much as aiming zooms in, so what is aimed at moves across the sights at about the usual speed. */
	public static float turnScale() {
		LocalPlayer player = Minecraft.getInstance().player;
		return player != null && GunItem.holdsGun(player) ? fovMultiplier(1) : 1;
	}

	/** How far a fitted scope has come to the eye, 0 to 1 (see {@link #scope}). */
	public static float scopeView(float partialTick) {
		float t = Mth.lerp(partialTick, scopeO, scope);
		return t * t * (3 - 2 * t);
	}

	/**
	 * Where aiming moves the model to (blocks, view space), {@code aim} of the way: so the sight line lands on the view's axis, and pushed a
	 * little further out; a 1x sight is brought to its eye distance from the eye (its window then fills as much of the view on any gun), and
	 * a scope comes in, its eyepiece its eye distance from the eye once it is at the eye.
	 */
	private static Vector3f adsShift(GunRenderer renderer, ItemStack stack, float aim, float partialTick) {
		Vector3f anchor = renderer.restPivot(renderer.aimBone(stack));
		AttachmentItem optic = Attachments.in(stack, AttachmentSlot.OPTIC);
		float push;
		if (optic != null && !optic.isScope()) {
			push = -anchor.z - Attachments.eyeBlocks(stack, optic);
		} else {
			float eye = optic != null ? Attachments.eyeDistance(stack, optic) : Attachments.DEFAULT_EYE_DISTANCE;
			push = Mth.lerp(scopeAt(stack, partialTick), -ADS_PUSH_FORWARD, -anchor.z - eye);
		}
		return new Vector3f(-anchor.x, -anchor.y, push).mul(aim);
	}

	/** {@link #scopeView} for a gun with a scope fitted, 0 for one without. */
	private static float scopeAt(ItemStack stack, float partialTick) {
		AttachmentItem optic = Attachments.in(stack, AttachmentSlot.OPTIC);
		return optic != null && optic.isScope() ? scopeView(partialTick) : 0;
	}

	/**
	 * The hand is drawn with a fixed FOV, so a view-space offset is scaled sideways by this to land on the same pixel at the world FOV.
	 * Going the other way, from the world into the hand's view space, divides by it.
	 */
	public static float fovScale(Camera camera) {
		return (float)(Math.tan(Math.toRadians(camera.getFov() / 2)) / Math.tan(Math.toRadians(Camera.BASE_HUD_FOV / 2)));
	}

	/**
	 * The line the first-person barrel is drawn along, as last drawn (sway, bob, breathing, the kick, the running pose and animations: a
	 * gun swung away shoots away), carried into the world at the world FOV so it covers the drawn barrel on screen; shots leave along it.
	 * The view is {@code eye}, {@code xRot} and {@code yRot}; null when the gun has not been drawn lately.
	 */
	public static @Nullable BarrelLine barrelLine(LocalPlayer player, Vec3 eye, float xRot, float yRot, Camera camera, float partialTick) {
		if (System.nanoTime() - muzzlePoseNanos > 200_000_000L) {
			return null;
		}
		Vector3f muzzle = muzzlePose.transformPosition(new Vector3f());
		Vector3f along = muzzlePose.transformDirection(new Vector3f(0, 0, -1)).normalize();
		// From the line's nearest point to the eye: the viewmodel is not to scale (a rifle's muzzle is drawn blocks out), and nothing
		// nearer than that may be skipped. Aiming, the sights take over and it comes in to the eye: shots go to the reticle, not a little
		// under it where the bore is (which a scope would magnify).
		Vector3f start = new Vector3f(muzzle).fma(-muzzle.dot(along), along).mul(1 - ads(partialTick));
		Vec3 forward = Vec3.directionFromRotation(xRot, yRot);
		Vec3 up = Vec3.directionFromRotation(xRot - 90, yRot);
		float scale = fovScale(camera);
		Vec3 from = eye.add(toWorld(start, scale, up.cross(forward), up, forward));
		Vec3 direction = toWorld(along, scale, up.cross(forward), up, forward).normalize();
		// Up against a wall, that start beside the eye can be inside it.
		if (player.level().clip(new ClipContext(eye, from, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty())).getType() != HitResult.Type.MISS) {
			from = eye;
		}
		return new BarrelLine(from, direction);
	}

	/** Where a first-person shot leaves from and the way it goes (see {@link #barrelLine}). */
	public record BarrelLine(Vec3 from, Vec3 direction) {
	}

	/** Where {@link #barrelLine} meets the first block or body within {@code range}, or the point that far along it: where a laser's dot is. */
	public static @Nullable Vec3 barrelTarget(LocalPlayer player, Vec3 eye, float xRot, float yRot, Camera camera, float partialTick, double range) {
		BarrelLine line = barrelLine(player, eye, xRot, yRot, camera, partialTick);
		return line == null ? null : Lasers.firstHitOr(player.level(), player, line.from(), line.direction(), range);
	}

	/** Called by {@link BonePoseLayer} with the pose the muzzle (a fitted attachment's {@code <item>_muzzle}, or {@code muzzle}) is drawn with. */
	static void recordMuzzlePose(Matrix4fc bonePose) {
		muzzlePose.set(inverseBase).mul(bonePose);
		muzzlePoseNanos = System.nanoTime();
	}

	/** Where the first-person muzzle was last drawn, in the world, or null when it has not been drawn lately: tracers and the flash start there. */
	public static @Nullable Vec3 muzzle(Camera camera) {
		return System.nanoTime() - muzzlePoseNanos > 200_000_000L ? null : camera.position().add(viewToWorld(camera, muzzlePose.transformPosition(new Vector3f())));
	}

	/** A view-space offset (right, up, back) as a world offset from the camera, at the world FOV. */
	private static Vec3 viewToWorld(Camera camera, Vector3fc offset) {
		return toWorld(offset, fovScale(camera), new Vec3(camera.leftVector()), new Vec3(camera.upVector()), new Vec3(camera.forwardVector()));
	}

	/** A view-space offset (right, up, back), scaled sideways by {@code scale}, along the view's world axes. */
	private static Vec3 toWorld(Vector3fc offset, float scale, Vec3 left, Vec3 up, Vec3 forward) {
		return left.scale(-offset.x() * scale).add(up.scale(offset.y() * scale)).add(forward.scale(-offset.z()));
	}

	/** Called by {@link BonePoseLayer} with the pose the {@code ejection_port} bone is drawn with this frame. */
	static void recordPortPose(Matrix4fc bonePose) {
		portPose.set(inverseBase).mul(bonePose);
		portPoseNanos = System.nanoTime();
	}

	/** Where the ejection port of the first-person gun was last drawn, in the world, or null when it has not been drawn lately. */
	public static Casings.@Nullable Port ejectionPort(Camera camera) {
		if (System.nanoTime() - portPoseNanos > 200_000_000L) {
			return null;
		}
		Vec3 right = viewToWorld(camera, portPose.transformDirection(new Vector3f(1, 0, 0))).normalize();
		Vec3 up = viewToWorld(camera, portPose.transformDirection(new Vector3f(0, 1, 0))).normalize();
		Vec3 forward = viewToWorld(camera, portPose.transformDirection(new Vector3f(0, 0, -1))).normalize();
		return new Casings.Port(camera.position().add(viewToWorld(camera, portPose.transformPosition(new Vector3f()))), right, up, forward);
	}

	/** Called by {@link BonePoseLayer} with the pose a fitted laser's {@code <item>_emitter} bone is drawn with this frame. */
	static void recordEmitterPose(Matrix4fc bonePose) {
		emitterPose.set(inverseBase).mul(bonePose);
		emitterPoseNanos = System.nanoTime();
	}

	/**
	 * Where the first-person gun's laser emitter was last drawn in the world, and the way {@code beam} (a direction in the emitter bone's
	 * frame) points from it, or null when it has not been drawn lately.
	 */
	public static Lasers.@Nullable Emitter laserEmitter(Camera camera, Vector3fc beam) {
		if (System.nanoTime() - emitterPoseNanos > 200_000_000L) {
			return null;
		}
		Vec3 forward = viewToWorld(camera, emitterPose.transformDirection(new Vector3f(beam))).normalize();
		return new Lasers.Emitter(camera.position().add(viewToWorld(camera, emitterPose.transformPosition(new Vector3f()))), forward);
	}

	public static boolean muzzleFlash() {
		return System.nanoTime() < muzzleFlashUntil;
	}

	public static float muzzleFlashRoll() {
		return muzzleFlashRoll;
	}

	/** True while the tuner is open: the showcase animation then holds the gun up sideways in the right hand. */
	public static boolean showcasing() {
		return showcaseOpen;
	}

	/** Called by {@link ShowcasePoseLayer} with the pose the gun bone is drawn with, at its pivot. */
	static void recordGunPose(Matrix4fc bonePose, GeoBone bone) {
		gunPose.set(inverseBase).mul(bonePose).translate(-bone.pivotX() / 16, -bone.pivotY() / 16, -bone.pivotZ() / 16);
		gunPoseNanos = System.nanoTime();
	}

	/**
	 * Gun model space to view space (eye at the origin, looking down -Z) as last drawn, once the showcase has settled, so the tuner can lay
	 * its panels out around the gun. Null while it is still moving in or has not been drawn lately.
	 */
	public static @Nullable Matrix4f showcaseGunPose() {
		return showcaseOpen && showcaseO >= 1 && System.nanoTime() - gunPoseNanos < 200_000_000L ? new Matrix4f(gunPose) : null;
	}

	/** About {@code value} while small, easing towards {@code max} as it grows. */
	private static float softLimit(float value, float max) {
		return max <= 0 ? 0 : max * (float)Math.tanh(value / max);
	}

	/**
	 * Replaces vanilla's first-person hands and items while a gun is in the main hand, or while the gun just switched off is put away.
	 * Returns false to let vanilla render.
	 */
	public static boolean submit(float partialTick, PoseStack poseStack, SubmitNodeCollector renderTasks, PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState hands) {
		Minecraft minecraft = Minecraft.getInstance();
		ItemStack stack = GunInput.puttingAway().isEmpty() ? hands.mainHandItem : GunInput.puttingAway();
		if (!(stack.getItem() instanceof GunItem gun) || minecraft.player == null || playerState.avatarRenderState == null
				|| !(GeoRenderProvider.of(gun).getGeoItemRenderer() instanceof GunRenderer renderer)) {
			return false;
		}
		LocalPlayer player = minecraft.player;
		GeoItemRenderer.RenderData renderData = new GeoItemRenderer.RenderData(stack, hands.mainHandRenderState, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, minecraft.level, player);
		GeoRenderState renderState = renderer.fillRenderState(gun, renderData, renderer.createRenderState(gun, renderData), partialTick);
		renderState.addGeckolibData(DataTickets.PACKED_LIGHT, playerState.avatarRenderState.lightCoords);
		renderState.addGeckolibData(DataTickets.HAS_GLINT, false);

		float aim = ads(partialTick);
		float show = Mth.lerp(partialTick, showcaseO, showcase);
		show = show * show * (3 - 2 * show);
		// Sway and bob drop to 20% at full ADS, most of the rest goes with the breath held, and all of it while the gun is showcased.
		float held = steady(partialTick);
		float loose = (1 - 0.8F * aim) * (1 - BREATH_STEADY * held) * (1 - show);
		poseStack.pushPose();
		// Everything below is in view space relative to this; the tuner reads the gun's pose against it.
		inverseBase.set(poseStack.last().pose()).invert();
		// Our casings while still at the gun, drawn with it so the slide hides them until they clear the port.
		Casings.submitInHand(poseStack, renderTasks, partialTick);

		// 1. ADS: slide the model so ads_anchor (or a fitted optic's reticle) lands on the view axis, at the center of the screen,
		// and a little further out so the slide does not fill the view; a scope comes up to the eye instead.
		Vector3f shift = adsShift(renderer, stack, aim, partialTick);
		poseStack.translate(shift.x, shift.y, shift.z);

		// 2. Sway: the model trails the view a little and eases back when it stops (swayYaw/swayPitch), with a soft limit so flicks
		// lean it a bit further instead of stopping dead.
		float lagPitch = hands.viewXRot - Mth.lerp(partialTick, swayPitchO, swayPitch);
		float lagYaw = Mth.wrapDegrees(hands.viewYRot - Mth.lerp(partialTick, swayYawO, swayYaw));
		float swayPitchDeg = softLimit(lagPitch * feel.swayGain() * PITCH_SWAY, feel.swayMaxDeg()) * loose;
		float swayYawDeg = softLimit(lagYaw * feel.swayGain(), feel.swayMaxDeg()) * loose;
		poseStack.rotateDegrees(Axis.XP, swayPitchDeg);
		poseStack.rotateDegrees(Axis.YP, swayYawDeg);

		// 3. Bob from walking. Vanilla's hand bob is skipped while a gun is held (GameRendererMixin), so this is the only one.
		if (minecraft.options.bobView().get()) {
			ClientAvatarState avatar = player.avatarState();
			float walk = avatar.getBackwardsInterpolatedWalkDistance(partialTick) * Mth.PI;
			float bob = avatar.getInterpolatedBob(partialTick) * feel.bobScale() * loose;
			poseStack.translate(Mth.sin(walk) * bob * 0.5F, -Math.abs(Mth.cos(walk) * bob), 0);
			poseStack.rotateDegrees(Axis.ZP, Mth.sin(walk) * bob * 3);
			poseStack.rotateDegrees(Axis.XP, Math.abs(Mth.cos(walk - 0.2F) * bob) * 5);
		}

		// 3a. Running: the gun comes in close, turned and canted across the body, and bounces with the stride (unless view
		// bobbing is off). Pivots on the grip. Arms and gun move as one, so nothing can pass through anything.
		Vector3f grip = renderer.restPivot("gun");
		float running = Mth.lerp(partialTick, runO, run);
		running = running * running * (3 - 2 * running);
		if (running > 0) {
			poseStack.translate(-0.02F * running, -0.03F * running, 0.03F * running);
			poseStack.rotateAround(Axis.YP.rotationDegrees(30 * running), grip.x, grip.y, grip.z);
			poseStack.rotateAround(Axis.XP.rotationDegrees(5 * running), grip.x, grip.y, grip.z);
			poseStack.rotateAround(Axis.ZP.rotationDegrees(30 * running), grip.x, grip.y, grip.z);
			if (minecraft.options.bobView().get()) {
				ClientAvatarState avatar = player.avatarState();
				float stride = avatar.getBackwardsInterpolatedWalkDistance(partialTick) * Mth.PI;
				// Vanilla's bob is 0.1 running on the ground and 0 in the air, so the bounce pauses mid-jump.
				float bounce = running * avatar.getInterpolatedBob(partialTick) * 10;
				poseStack.translate(Mth.sin(stride) * 0.025F * bounce, -Math.abs(Mth.cos(stride)) * 0.03F * bounce, 0);
				poseStack.rotateAround(Axis.ZP.rotationDegrees(Mth.sin(stride) * 4 * bounce), grip.x, grip.y, grip.z);
				poseStack.rotateAround(Axis.XP.rotationDegrees(-Math.abs(Mth.cos(stride - 0.3F)) * 3 * bounce), grip.x, grip.y, grip.z);
			}
		}

		// 3b. Breathing, under everything (reloads, inspects and the tuner too), because a held gun is never quite still: a slow rise
		// and fall that tips the muzzle up on the inhale, and a sideways drift and roll from waves at unrelated periods so it never
		// visibly loops. Pivots on the grip, drops to 20% while aiming like sway, and stops with the breath held.
		float breath = feel.breathScale() * (1 - 0.8F * aim) * (1 - held);
		if (breath > 0) {
			float seconds = (player.tickCount + partialTick) / 20;
			float inhale = Mth.sin(seconds * Mth.TWO_PI / BREATH_PERIOD);
			float drift = 0.6F * Mth.sin(seconds * Mth.TWO_PI / 5.3F + 1.1F) + 0.4F * Mth.sin(seconds * Mth.TWO_PI / 2.3F + 2.4F);
			poseStack.translate(drift * 0.003F * breath, inhale * 0.006F * breath, 0);
			poseStack.rotateAround(Axis.XP.rotationDegrees(inhale * 0.35F * breath), grip.x, grip.y, grip.z);
			poseStack.rotateAround(Axis.ZP.rotationDegrees(drift * 0.25F * breath), grip.x, grip.y, grip.z);
		}

		// 4. Recoil: kick the muzzle up and push the gun back, pivoting on the grip. A scope at the eye barely comes back, or it would
		// hit the eye.
		float kick = recoil(partialTick);
		float pushBack = feel.recoilPushBack() * (1 - 0.85F * aim * scopeAt(stack, partialTick));
		poseStack.translate(grip.x, grip.y, grip.z + kick * pushBack);
		poseStack.rotateDegrees(Axis.XP, kick * recoilPitch);
		poseStack.translate(-grip.x, -grip.y, -grip.z);

		renderer.submit(renderState, poseStack, renderTasks, 0);
		poseStack.popPose();
		return true;
	}

	/**
	 * Called by {@link BonePoseLayer} with the pose a fitted scope's eyepiece ({@code <item>_reticle}) is drawn with, and its lens's radius.
	 * Taken to view space as the hand pass does: vanilla puts the inverse camera rotation in the hand's poses and the camera rotation in
	 * the model-view matrix, so the poses alone turn with the camera.
	 */
	static void recordLensPose(Matrix4fc bonePose, float radius) {
		lensPose.set(RenderSystem.getModelViewStack()).mul(bonePose);
		lensRadius = radius;
		lensPoseNanos = System.nanoTime();
	}

	/**
	 * A fitted scope's eyepiece as drawn in the hand's view space this frame (eye at the origin): {@code pose} takes the eyepiece's frame
	 * (lens in its x-y plane, the scope looking down its -z) to view space; {@code radius} is the lens's, in blocks. Null when not drawn lately.
	 */
	public static @Nullable Lens lens() {
		return System.nanoTime() - lensPoseNanos > 200_000_000L ? null : new Lens(new Matrix4f(lensPose), lensRadius);
	}

	public record Lens(Matrix4f pose, float radius) {
	}
}

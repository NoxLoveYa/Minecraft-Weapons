package com.noxloveya.mcweapons.client.render;

import com.geckolib.animatable.client.GeoRenderProvider;
import com.geckolib.cache.model.GeoBone;
import com.geckolib.constant.DataTickets;
import com.geckolib.renderer.GeoItemRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
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
import net.minecraft.world.phys.Vec3;
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
	private static final Matrix4f inverseBase = new Matrix4f();
	private static final Matrix4f gunPose = new Matrix4f();
	private static long gunPoseNanos;
	/** The ejection port's drawn pose in view space, for casings to leave from (see {@link #ejectionPort}). */
	private static final Matrix4f portPose = new Matrix4f();
	private static long portPoseNanos;
	/** A fitted laser's emitter as drawn in view space, for the beam to leave from (see {@link #laserEmitter}). */
	private static final Matrix4f emitterPose = new Matrix4f();
	private static long emitterPoseNanos;
	/**
	 * How far the barrel was last drawn tipped off the view, in the hand's view space (degrees, muzzle up and muzzle left), from sway,
	 * walk bob, breathing and the recoil kick. Shots go this way (see {@link #barrelXRot}). Not the running pose or animations.
	 */
	private static float barrelPitch;
	private static float barrelYaw;
	private static long barrelNanos;
	private static boolean showcaseOpen;

	/** Sway, bob and recoil spring values of the held gun (its definition's "feel"). */
	private static GunFeel feel = GunFeel.DEFAULT;
	private static float ads;
	private static float adsO;
	private static float adsFovMultiplier = 1;
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

	private Viewmodel() {
	}

	public static void tick(LocalPlayer player, GunDefinition definition, boolean aiming, boolean running, boolean showcasing) {
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
		recoilPitch = definition.recoilPitchDeg();
		feel = definition.feel();
		recoilO = recoil;
		recoilVelocity += -feel.recoilSpringStiffness() * recoil - feel.recoilSpringDamping() * recoilVelocity;
		recoil += recoilVelocity;
	}

	public static void reset() {
		ads = adsO = 0;
		showcase = showcaseO = 0;
		showcaseOpen = false;
		run = runO = 0;
		recoil = recoilO = recoilVelocity = 0;
		muzzleFlashUntil = 0;
		swaySnap = true;
		barrelPitch = barrelYaw = 0;
	}

	/** Degrees to add to the view's xRot for where the first-person barrel points (see {@link #barrelPitch}); 0 when it is not drawn. */
	public static float barrelXRot(Camera camera) {
		return -barrelAngle(barrelPitch, camera);
	}

	/** Degrees to add to the view's yRot for where the first-person barrel points; 0 when it is not drawn. */
	public static float barrelYRot(Camera camera) {
		return -barrelAngle(barrelYaw, camera);
	}

	/** A hand view-space angle as the world angle that lands on the same pixel, like {@link #viewToWorld}. */
	private static float barrelAngle(float degrees, Camera camera) {
		if (System.nanoTime() - barrelNanos > 200_000_000L) {
			return 0;
		}
		return (float)Math.toDegrees(Math.atan(Math.tan(Math.toRadians(degrees)) * fovScale(camera)));
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
		return player != null && GunItem.holdsGun(player) ? feel.bobScale() : 1;
	}

	public static float fovMultiplier(float partialTick) {
		return Mth.lerp(ads(partialTick), 1, adsFovMultiplier);
	}

	/** World position where a first-person gun bone's pivot (the muzzle, the chamber) appears on screen, so effects can start from it. */
	public static Vec3 bonePosition(GunRenderer renderer, String bone, Camera camera, float partialTick) {
		float aim = ads(partialTick);
		Vector3f anchor = renderer.restPivot(renderer.aimBone(Minecraft.getInstance().player.getMainHandItem()));
		Vector3f muzzle = renderer.restPivot(bone).add(-anchor.x * aim, -anchor.y * aim, -ADS_PUSH_FORWARD * aim);
		return camera.position().add(viewToWorld(camera, muzzle));
	}

	/**
	 * The hand is drawn with a fixed FOV, so a view-space offset is scaled sideways by this to land on the same pixel at the world FOV.
	 * Going the other way, from the world into the hand's view space, divides by it.
	 */
	public static float fovScale(Camera camera) {
		return (float)(Math.tan(Math.toRadians(camera.getFov() / 2)) / Math.tan(Math.toRadians(Camera.BASE_HUD_FOV / 2)));
	}

	/** A view-space offset (right, up, back) as a world offset from the camera, at the world FOV. */
	private static Vec3 viewToWorld(Camera camera, Vector3fc offset) {
		float scale = fovScale(camera);
		Vector3fc left = camera.leftVector();
		Vector3fc up = camera.upVector();
		Vector3fc forward = camera.forwardVector();
		float x = -offset.x() * scale;
		float y = offset.y() * scale;
		float z = -offset.z();
		return new Vec3(left.x() * x + up.x() * y + forward.x() * z, left.y() * x + up.y() * y + forward.y() * z, left.z() * x + up.z() * y + forward.z() * z);
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

	/** Replaces vanilla's first-person hands and items while a gun is in the main hand. Returns false to let vanilla render. */
	public static boolean submit(float partialTick, PoseStack poseStack, SubmitNodeCollector renderTasks, PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState hands) {
		Minecraft minecraft = Minecraft.getInstance();
		ItemStack stack = hands.mainHandItem;
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
		// Sway and bob drop to 20% at full ADS, and go away while the gun is showcased.
		float loose = (1 - 0.8F * aim) * (1 - show);
		poseStack.pushPose();
		// Everything below is in view space relative to this; the tuner reads the gun's pose against it.
		inverseBase.set(poseStack.last().pose()).invert();
		// Our casings while still at the gun, drawn with it so the slide hides them until they clear the port.
		Casings.submitInHand(poseStack, renderTasks, partialTick);

		// 1. ADS: slide the model so ads_anchor (or a fitted optic's reticle) lands on the view axis, at the center of the screen,
		// and a little further out so the slide does not fill the view.
		Vector3f anchor = renderer.restPivot(renderer.aimBone(stack));
		poseStack.translate(-anchor.x * aim, -anchor.y * aim, -ADS_PUSH_FORWARD * aim);

		// 2. Sway: the model trails the view a little and eases back when it stops (swayYaw/swayPitch), with a soft limit so flicks
		// lean it a bit further instead of stopping dead.
		float lagPitch = hands.viewXRot - Mth.lerp(partialTick, swayPitchO, swayPitch);
		float lagYaw = Mth.wrapDegrees(hands.viewYRot - Mth.lerp(partialTick, swayYawO, swayYaw));
		float swayPitchDeg = softLimit(lagPitch * feel.swayGain(), feel.swayMaxDeg()) * loose;
		float swayYawDeg = softLimit(lagYaw * feel.swayGain(), feel.swayMaxDeg()) * loose;
		poseStack.rotateDegrees(Axis.XP, swayPitchDeg);
		poseStack.rotateDegrees(Axis.YP, swayYawDeg);
		float pitch = swayPitchDeg;

		// 3. Bob from walking. Vanilla's hand bob is skipped while a gun is held (GameRendererMixin), so this is the only one.
		if (minecraft.options.bobView().get()) {
			ClientAvatarState avatar = player.avatarState();
			float walk = avatar.getBackwardsInterpolatedWalkDistance(partialTick) * Mth.PI;
			float bob = avatar.getInterpolatedBob(partialTick) * feel.bobScale() * loose;
			poseStack.translate(Mth.sin(walk) * bob * 0.5F, -Math.abs(Mth.cos(walk) * bob), 0);
			poseStack.rotateDegrees(Axis.ZP, Mth.sin(walk) * bob * 3);
			poseStack.rotateDegrees(Axis.XP, Math.abs(Mth.cos(walk - 0.2F) * bob) * 5);
			pitch += Math.abs(Mth.cos(walk - 0.2F) * bob) * 5;
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
		// visibly loops. Pivots on the grip, and drops to 20% while aiming like sway.
		float breath = feel.breathScale() * (1 - 0.8F * aim);
		if (breath > 0) {
			float seconds = (player.tickCount + partialTick) / 20;
			float inhale = Mth.sin(seconds * Mth.TWO_PI / BREATH_PERIOD);
			float drift = 0.6F * Mth.sin(seconds * Mth.TWO_PI / 5.3F + 1.1F) + 0.4F * Mth.sin(seconds * Mth.TWO_PI / 2.3F + 2.4F);
			poseStack.translate(drift * 0.003F * breath, inhale * 0.006F * breath, 0);
			poseStack.rotateAround(Axis.XP.rotationDegrees(inhale * 0.35F * breath), grip.x, grip.y, grip.z);
			pitch += inhale * 0.35F * breath;
			poseStack.rotateAround(Axis.ZP.rotationDegrees(drift * 0.25F * breath), grip.x, grip.y, grip.z);
		}

		// 4. Recoil: kick the muzzle up and push the gun back, pivoting on the grip.
		float kick = recoil(partialTick);
		poseStack.translate(grip.x, grip.y, grip.z + kick * feel.recoilPushBack());
		poseStack.rotateDegrees(Axis.XP, kick * recoilPitch);
		poseStack.translate(-grip.x, -grip.y, -grip.z);
		barrelPitch = pitch + kick * recoilPitch;
		barrelYaw = swayYawDeg;
		barrelNanos = System.nanoTime();

		renderer.submit(renderState, poseStack, renderTasks, 0);
		poseStack.popPose();
		return true;
	}
}

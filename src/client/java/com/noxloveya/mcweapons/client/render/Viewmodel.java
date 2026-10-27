package com.noxloveya.mcweapons.client.render;

import com.geckolib.animatable.client.GeoRenderProvider;
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
import org.joml.Vector3f;
import org.joml.Vector3fc;

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
	/** How far in front of the eye the showcased gun floats. The tuner sets it so its panels fit around the gun. */
	private static float showcaseDistance = 1.1F;

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

	private Viewmodel() {
	}

	public static void tick(LocalPlayer player, GunDefinition definition, boolean aiming, boolean showcasing) {
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
		recoil = recoilO = recoilVelocity = 0;
		muzzleFlashUntil = 0;
		swaySnap = true;
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

	/** World position where the first-person muzzle appears on screen, so effects can start from it. */
	public static Vec3 muzzlePosition(GunRenderer renderer, Camera camera, float partialTick) {
		float aim = ads(partialTick);
		Vector3f anchor = renderer.restPivot("ads_anchor");
		Vector3f muzzle = renderer.restPivot("muzzle").add(-anchor.x * aim, -anchor.y * aim, -ADS_PUSH_FORWARD * aim);
		// The hand is drawn with a fixed FOV, so scale the sideways offset until the point lands on the same pixel at the world FOV.
		float scale = (float)(Math.tan(Math.toRadians(camera.getFov() / 2)) / Math.tan(Math.toRadians(Camera.BASE_HUD_FOV / 2)));
		Vector3fc left = camera.leftVector();
		Vector3fc up = camera.upVector();
		Vector3fc forward = camera.forwardVector();
		float x = -muzzle.x * scale;
		float y = muzzle.y * scale;
		float z = -muzzle.z;
		return camera.position().add(left.x() * x + up.x() * y + forward.x() * z, left.y() * x + up.y() * y + forward.y() * z, left.z() * x + up.z() * y + forward.z() * z);
	}

	public static boolean muzzleFlash() {
		return System.nanoTime() < muzzleFlashUntil;
	}

	public static float muzzleFlashRoll() {
		return muzzleFlashRoll;
	}

	/** True while the gun is shown for the tuner or still moving in or out of it. */
	public static boolean showcasing() {
		return showcase > 0 || showcaseO > 0;
	}

	public static void setShowcaseDistance(float distance) {
		showcaseDistance = distance;
	}

	/**
	 * Model to view space for the showcase: the middle of the gun floats to the center of the view, turned so its left side faces the
	 * eye and tilted a little so the top and the muzzle read as 3D. {@code show} eases it in from the hand (0) to fully shown (1).
	 */
	public static Matrix4f showcaseTransform(GunRenderer renderer, float show) {
		float[] bounds = renderer.gunBounds();
		Vector3f middle = new Vector3f(bounds[0] + bounds[3], bounds[1] + bounds[4], bounds[2] + bounds[5]).mul(0.5F);
		return new Matrix4f()
				.translate(-middle.x * show, -middle.y * show, (-showcaseDistance - middle.z) * show)
				.translate(middle)
				.rotateZ(-6 * show * Mth.DEG_TO_RAD)
				.rotateX(14 * show * Mth.DEG_TO_RAD)
				.rotateY(90 * show * Mth.DEG_TO_RAD)
				.translate(-middle.x, -middle.y, -middle.z);
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

		// 0. Showcase (gun tuner): present the gun sideways in the middle of the screen. The showcase controller poses the arms holding it.
		if (show > 0) {
			poseStack.mulPose(showcaseTransform(renderer, show));
		}

		// 1. ADS: slide the model so ads_anchor lands on the view axis, at the center of the screen,
		// and a little further out so the slide does not fill the view.
		Vector3f anchor = renderer.restPivot("ads_anchor");
		poseStack.translate(-anchor.x * aim, -anchor.y * aim, -ADS_PUSH_FORWARD * aim);

		// 2. Sway: the model trails the view a little and eases back when it stops (swayYaw/swayPitch), with a soft limit so flicks
		// lean it a bit further instead of stopping dead.
		float lagPitch = hands.viewXRot - Mth.lerp(partialTick, swayPitchO, swayPitch);
		float lagYaw = Mth.wrapDegrees(hands.viewYRot - Mth.lerp(partialTick, swayYawO, swayYaw));
		poseStack.rotateDegrees(Axis.XP, softLimit(lagPitch * feel.swayGain(), feel.swayMaxDeg()) * loose);
		poseStack.rotateDegrees(Axis.YP, softLimit(lagYaw * feel.swayGain(), feel.swayMaxDeg()) * loose);

		// 3. Bob from walking. Vanilla's hand bob is skipped while a gun is held (GameRendererMixin), so this is the only one.
		if (minecraft.options.bobView().get()) {
			ClientAvatarState avatar = player.avatarState();
			float walk = avatar.getBackwardsInterpolatedWalkDistance(partialTick) * Mth.PI;
			float bob = avatar.getInterpolatedBob(partialTick) * feel.bobScale() * loose;
			poseStack.translate(Mth.sin(walk) * bob * 0.5F, -Math.abs(Mth.cos(walk) * bob), 0);
			poseStack.rotateDegrees(Axis.ZP, Mth.sin(walk) * bob * 3);
			poseStack.rotateDegrees(Axis.XP, Math.abs(Mth.cos(walk - 0.2F) * bob) * 5);
		}

		// 3b. Breathing, under everything (reloads, inspects and the tuner too), because a held gun is never quite still: a slow rise
		// and fall that tips the muzzle up on the inhale, and a sideways drift and roll from waves at unrelated periods so it never
		// visibly loops. Pivots on the grip, and drops to 20% while aiming like sway.
		Vector3f grip = renderer.restPivot("gun");
		float breath = feel.breathScale() * (1 - 0.8F * aim);
		if (breath > 0) {
			float seconds = (player.tickCount + partialTick) / 20;
			float inhale = Mth.sin(seconds * Mth.TWO_PI / BREATH_PERIOD);
			float drift = 0.6F * Mth.sin(seconds * Mth.TWO_PI / 5.3F + 1.1F) + 0.4F * Mth.sin(seconds * Mth.TWO_PI / 2.3F + 2.4F);
			poseStack.translate(drift * 0.003F * breath, inhale * 0.006F * breath, 0);
			poseStack.rotateAround(Axis.XP.rotationDegrees(inhale * 0.35F * breath), grip.x, grip.y, grip.z);
			poseStack.rotateAround(Axis.ZP.rotationDegrees(drift * 0.25F * breath), grip.x, grip.y, grip.z);
		}

		// 4. Recoil: kick the muzzle up and push the gun back, pivoting on the grip.
		float kick = recoil(partialTick);
		poseStack.translate(grip.x, grip.y, grip.z + kick * feel.recoilPushBack());
		poseStack.rotateDegrees(Axis.XP, kick * recoilPitch);
		poseStack.translate(-grip.x, -grip.y, -grip.z);

		renderer.submit(renderState, poseStack, renderTasks, 0);
		poseStack.popPose();
		return true;
	}
}

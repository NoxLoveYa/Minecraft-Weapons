package com.noxloveya.mcweapons.client.render;

import com.noxloveya.mcweapons.client.input.GunInput;
import com.noxloveya.mcweapons.gun.GunItem;
import com.noxloveya.mcweapons.gun.GunState;
import com.noxloveya.mcweapons.network.GunAnimationPayload;
import com.noxloveya.mcweapons.network.ServerHandlers;
import com.noxloveya.mcweapons.registry.ModComponents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Guns as seen in third person, by the holder (F5) and everyone else: arm poses that follow what the gun is doing, the muzzle flash,
 * and the gun animations other players start. Their fire comes with the shot effects, reloads and inspects from the server, and draws
 * are seen here when they switch to a gun.
 */
public final class ThirdPersonGuns {
	/** Aiming, both arms reach forward following the head: the gun arm turned in by this (radians), the other further, so the hands meet at the gun. */
	public static final float AIM_GUN_ARM_IN = 0.36F;
	private static final float AIM_SUPPORT_ARM_IN = 0.7F;
	/** Set on an avatar's render state while it aims a gun, for {@link #poseAimHold}. */
	public static final RenderStateDataKey<Boolean> AIM_HOLD = RenderStateDataKey.create(() -> "mcweapons:aim_hold");
	private static final long FLASH_NANOS = 60_000_000L;
	/** After a shot the gun stays up this long, so firing from the hip does not show it pointing at the ground. */
	private static final long RAISED_NANOS = 1_500_000_000L;
	private static final Map<Integer, Long> flashUntil = new HashMap<>();
	private static final Map<Integer, Long> raisedUntil = new HashMap<>();
	/** What each other player held last tick, to see them switch to a gun. */
	private static final Map<Integer, Item> heldItems = new HashMap<>();

	/** What the holder's arms do. Anything but HIP raises the gun arm forward. */
	public enum Pose { HIP, AIM, RELOAD, INSPECT }

	private ThirdPersonGuns() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(ThirdPersonGuns::tick);
		ClientPlayNetworking.registerGlobalReceiver(GunAnimationPayload.TYPE, (payload, context) -> othersAnimation(context.client().level, payload));
	}

	private static void tick(Minecraft minecraft) {
		if (minecraft.level == null) {
			heldItems.clear();
			flashUntil.clear();
			raisedUntil.clear();
			return;
		}
		Set<Integer> present = new HashSet<>();
		for (AbstractClientPlayer other : minecraft.level.players()) {
			if (other == minecraft.player) {
				continue;
			}
			present.add(other.getId());
			Item held = other.getMainHandItem().getItem();
			Item before = heldItems.put(other.getId(), held);
			// Not on first sight: a player who walks into view already holding a gun did not just draw it.
			if (before != null && before != held && held instanceof GunItem gun) {
				play(other, gun, "draw");
			}
		}
		heldItems.keySet().retainAll(present);
		long now = System.nanoTime();
		flashUntil.values().removeIf(until -> until < now);
		raisedUntil.values().removeIf(until -> until < now);
	}

	private static void othersAnimation(@Nullable ClientLevel level, GunAnimationPayload payload) {
		Entity entity = level == null ? null : level.getEntity(payload.entityId());
		if (entity instanceof LivingEntity holder && holder != Minecraft.getInstance().player && GunItem.TRIGGERED_ACTIONS.contains(payload.action())
				&& holder.getMainHandItem().getItem() instanceof GunItem gun) {
			play(holder, gun, payload.action());
		}
	}

	private static void play(LivingEntity holder, GunItem gun, String action) {
		gun.getAnimatableInstanceCache().getManagerForId(GunRenderer.instanceId(holder)).tryTriggerAnimation(GunItem.MAIN_CONTROLLER, action);
	}

	/**
	 * Rough position of a point on a gun held up in the two-handed aim (which a shot always brings it to), seen from outside:
	 * {@code forward} blocks out from the eye along the look, 0.97 for the muzzle and 0.68 for the ejection port.
	 */
	public static Vec3 heldPoint(Entity holder, double forward) {
		Vec3 look = holder.getLookAngle();
		Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
		return holder.getEyePosition().add(look.scale(forward)).add(right.scale(0.14)).add(0, -0.11, 0);
	}

	/** Someone fired: flash at the muzzle of their gun as drawn in third person, and hold the gun up for a moment. */
	public static void flash(int shooterId) {
		long now = System.nanoTime();
		flashUntil.put(shooterId, now + FLASH_NANOS);
		raisedUntil.put(shooterId, now + RAISED_NANOS);
	}

	public static boolean flashing(Entity holder) {
		Long until = flashUntil.get(holder.getId());
		return until != null && System.nanoTime() < until;
	}

	public static Pose pose(LivingEntity holder) {
		ItemStack stack = holder.getMainHandItem();
		if (!(stack.getItem() instanceof GunItem gun) || holder.isSpectator()) {
			return Pose.HIP;
		}
		// The holder's own client predicts its reload and aim; everyone else goes by what the server synced (gun_state, the ADS modifier).
		LocalPlayer self = holder == Minecraft.getInstance().player ? (LocalPlayer)holder : null;
		boolean reloading = self != null ? GunInput.reloading(self, stack) : stack.getOrDefault(ModComponents.GUN_STATE, GunState.EMPTY).reloading();
		if (reloading) {
			return Pose.RELOAD;
		}
		var main = gun.getAnimatableInstanceCache().getManagerForId(GunRenderer.instanceId(holder)).getAnimationControllers().get(GunItem.MAIN_CONTROLLER);
		if (main != null && GunItem.INSPECT_ACTIONS.stream().anyMatch(main::isTriggeredAnimation)) {
			return Pose.INSPECT;
		}
		AttributeInstance speed = holder.getAttribute(Attributes.MOVEMENT_SPEED);
		boolean aiming = self != null ? GunInput.aiming() : speed != null && speed.hasModifier(ServerHandlers.ADS_SPEED);
		Long raised = raisedUntil.get(holder.getId());
		return aiming || raised != null && System.nanoTime() < raised ? Pose.AIM : Pose.HIP;
	}

	/**
	 * The two-handed aim, after vanilla has posed the model: the gun arm points ahead turned in a little, the support arm turned in more
	 * so its hand cups the gun hand, both following where the head looks. {@link GunRenderer} turns the gun back by the gun arm's turn.
	 */
	public static void poseAimHold(HumanoidModel<?> model, HumanoidArm mainArm) {
		ModelPart gunArm = mainArm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
		ModelPart supportArm = mainArm == HumanoidArm.RIGHT ? model.leftArm : model.rightArm;
		// Positive yaw turns the left arm in, negative the right one.
		float in = mainArm == HumanoidArm.RIGHT ? 1 : -1;
		gunArm.xRot = -Mth.HALF_PI + model.head.xRot;
		gunArm.yRot = model.head.yRot - in * AIM_GUN_ARM_IN;
		gunArm.zRot = 0;
		supportArm.xRot = -Mth.HALF_PI + model.head.xRot;
		supportArm.yRot = model.head.yRot + in * AIM_SUPPORT_ARM_IN;
		supportArm.zRot = 0;
	}

	/**
	 * Vanilla arm pose for a gun in the main hand, or null to keep vanilla's: both arms out to aim (then made a two-handed hold by
	 * {@link #poseAimHold}), the support hand at the gun while reloading, and the gun brought up in front of the face to inspect.
	 */
	public static HumanoidModel.@Nullable ArmPose armPose(LivingEntity holder, ItemStack stack, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND || !(stack.getItem() instanceof GunItem)) {
			return null;
		}
		return switch (pose(holder)) {
			case AIM -> HumanoidModel.ArmPose.BOW_AND_ARROW;
			case RELOAD -> HumanoidModel.ArmPose.CROSSBOW_HOLD;
			case INSPECT -> HumanoidModel.ArmPose.TOOT_HORN;
			case HIP -> null;
		};
	}
}

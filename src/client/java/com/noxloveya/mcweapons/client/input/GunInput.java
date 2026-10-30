package com.noxloveya.mcweapons.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import com.noxloveya.mcweapons.McWeapons;
import com.noxloveya.mcweapons.client.render.Casings;
import com.noxloveya.mcweapons.client.render.GunRenderer;
import com.noxloveya.mcweapons.client.render.MuzzleLight;
import com.noxloveya.mcweapons.client.render.ThirdPersonGuns;
import com.noxloveya.mcweapons.client.render.Tracers;
import com.noxloveya.mcweapons.client.render.Viewmodel;
import com.noxloveya.mcweapons.client.screen.AttachmentScreen;
import com.noxloveya.mcweapons.client.screen.GunTunerScreen;
import com.noxloveya.mcweapons.combat.Ballistics;
import com.noxloveya.mcweapons.gun.AttachmentItem;
import com.noxloveya.mcweapons.gun.AttachmentSlot;
import com.noxloveya.mcweapons.gun.Attachments;
import com.noxloveya.mcweapons.gun.BreathMeter;
import com.noxloveya.mcweapons.gun.GunDefinition;
import com.noxloveya.mcweapons.gun.GunDefinitions;
import com.noxloveya.mcweapons.gun.GunFeel;
import com.noxloveya.mcweapons.gun.GunItem;
import com.noxloveya.mcweapons.gun.GunState;
import com.noxloveya.mcweapons.gun.MovementSpread;
import com.noxloveya.mcweapons.gun.Reticle;
import com.noxloveya.mcweapons.gun.RoundReload;
import com.noxloveya.mcweapons.client.render.GunHud;
import com.noxloveya.mcweapons.network.AimPayload;
import com.noxloveya.mcweapons.network.AttachPayload;
import com.noxloveya.mcweapons.network.EyeDistancePayload;
import com.noxloveya.mcweapons.network.ChamberPayload;
import com.noxloveya.mcweapons.network.FireModePayload;
import com.noxloveya.mcweapons.network.LaserColorPayload;
import com.noxloveya.mcweapons.network.GunAnimationPayload;
import com.noxloveya.mcweapons.network.ReloadPayload;
import com.noxloveya.mcweapons.network.ReticlePayload;
import com.noxloveya.mcweapons.network.ShootPayload;
import com.noxloveya.mcweapons.network.SwitchPayload;
import com.noxloveya.mcweapons.registry.ModComponents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Turns attack, use and the mod keys into gun intents while a gun is in the main hand, and predicts their animations. */
public final class GunInput {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(McWeapons.id("main"));
	public static final KeyMapping RELOAD = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.mcweapons.reload", InputConstants.KEY_R, CATEGORY));
	public static final KeyMapping INSPECT = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.mcweapons.inspect", InputConstants.KEY_V, CATEGORY));
	/** Opens the gun tuner for ops. The server checks the permission again on every change. */
	public static final KeyMapping TUNER = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.mcweapons.tuner", InputConstants.KEY_F7, CATEGORY));
	/** Tapped, steps through the gun's fire modes; held, puts it on safe; either takes it off safe, back to the mode it was on. */
	public static final KeyMapping FIRE_MODE = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.mcweapons.fire_mode", InputConstants.KEY_B, CATEGORY));
	/** Opens the attachment menu for the held gun. */
	public static final KeyMapping ATTACHMENTS = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.mcweapons.attachments", InputConstants.KEY_G, CATEGORY));
	/** Raises or lowers the sights with a press, beside holding use. Unbound by default; bound to use's button it replaces the hold. */
	public static final KeyMapping AIM_TOGGLE = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.mcweapons.aim_toggle", InputConstants.UNKNOWN.getValue(), CATEGORY));
	/** Held while aiming, holds the breath: steadier, a little closer, a little slower, for as long as it lasts. On sneak's key by default. */
	public static final KeyMapping HOLD_BREATH = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.mcweapons.hold_breath", InputConstants.KEY_LSHIFT, CATEGORY));
	/** Holds the breath with a press, until pressed again, the sights come down or the breath runs out. Unbound by default. */
	public static final KeyMapping BREATH_TOGGLE = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.mcweapons.breath_toggle", InputConstants.UNKNOWN.getValue(), CATEGORY));
	/** Switches the selected laser or flashlight on or off with a tap; held, steps the selection through them (the HUD shows them). */
	public static final KeyMapping SWITCH = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.mcweapons.switch", InputConstants.KEY_H, CATEGORY));
	/** Holding reload this long (0.4 s) checks the mag instead of reloading. */
	private static final int MAG_CHECK_HOLD_TICKS = 8;
	/** Holding the fire mode key this long (0.4 s) puts the gun on safe instead of stepping to the next mode. */
	private static final int SAFE_HOLD_TICKS = 8;
	/** Holding the switch key this long (0.4 s) selects the next laser or flashlight, and again every as long while it is held. */
	private static final int SWITCH_HOLD_TICKS = 8;

	/** A shot keeps the gun up this long (0.5 s) before a held sprint key runs again. */
	private static final int SHOT_NO_SPRINT_TICKS = 10;

	private static boolean aiming;
	/** The aim key is held with a gun in hand. It ends a sprint at once; the sights come up once the running pose is down. */
	private static boolean aimHeld;
	/** The toggle aim key turned aiming on. Off again when the gun leaves the hand or a screen opens, as a held key is let go then. */
	private static boolean aimToggled;
	/** Game time until which the last shot keeps the holder from sprinting. */
	private static long noSprintUntil;
	/** Client game time, in fractional ticks, when the next shot is due (see GunDefinition.nextShotDue). */
	private static double nextShotDue;
	/** Shots still to come in the burst the last click started; each fires as it comes due. */
	private static int burstLeft;
	/** Predicted end of the reload we asked for, until the server's gun_state arrives. */
	private static long predictedReloadUntil;
	/** When that reload started (its animation with it) and the rounds the gun had then. */
	private static long predictedReloadStart;
	private static int predictedReloadFrom;
	/** A round-at-a-time reload stopped early: when its animation cuts to the bolt closing (once the last round kept is in), or 0. */
	private static long stopReloadAt;
	/** After firing the last round, the slide locks back before the server's empty ammo count arrives. */
	private static long predictedEmptyUntil;
	private static String lastInspect = "";
	/** Mod keys that were down last tick, to tell a fresh press from a held key's repeats. */
	private static final Set<KeyMapping> HELD_KEYS = new HashSet<>();
	/** Ticks the reload key has been held, or -1 when no press is being timed. */
	private static int reloadHeldTicks = -1;
	/** The same for the switch key. */
	private static int switchHeldTicks = -1;
	/** The same for the fire mode key. */
	private static int fireModeHeldTicks = -1;
	/** The laser or flashlight the switch key acts on (see {@link #switchSelected}). */
	private static @Nullable AttachmentItem switchSelected;
	private static int drawnSlot = -1;
	private static @Nullable Item drawnItem;
	/** The gun in hand last tick, to put away once it leaves the hand. */
	private static ItemStack drawnStack = ItemStack.EMPTY;
	/** The gun being put away, drawn instead of what is now in hand until {@link #putAwayUntil}. */
	private static ItemStack puttingAway = ItemStack.EMPTY;
	private static long putAwayUntil;
	/** The gun now in hand waits for the put-away to finish before it is drawn. */
	private static boolean drawDue;
	/** Gun types already pulled out once in this world: the first pull-out also readies the gun (draw_first). */
	private static final Set<Item> READIED = new HashSet<>();
	private static @Nullable ClientLevel readiedIn;
	/** The fire mode we switched to, used until the server's fire_mode arrives. */
	private static @Nullable String predictedMode;
	private static long predictedModeUntil;
	/** Names our shots, so the server's impact for one can end its tracer. */
	private static int lastShotId;
	/** End of the attach or detach animation we started; the gun does nothing else until then. */
	private static long attachingUntil;
	/** What we asked to fit into each slot, shown in the hand during the attach animation until the server's attachments arrive. */
	private static final Map<AttachmentSlot, AttachmentItem> PENDING = new EnumMap<>(AttachmentSlot.class);
	/** The power a variable optic is turned to with the scroll wheel (kept within each optic's range), or 0 before it is first turned. */
	private static float zoom;
	private static final BreathMeter BREATH = new BreathMeter();
	private static float breathO = 1;
	private static boolean holdingBreath;
	private static boolean breathToggled;

	private GunInput() {
	}

	public static void register() {
		ClientTickEvents.START_CLIENT_TICK.register(GunInput::tick);
	}

	public static boolean aiming() {
		return aiming;
	}

	public static boolean holdingBreath() {
		return holdingBreath;
	}

	/** What is left of the breath, 1 full, between the last tick and this one. */
	public static float breath(float partialTick) {
		return Mth.lerp(partialTick, breathO, BREATH.breath());
	}

	/** What the player is doing for the spread: sprinting, crouching, walking (moving more than a crawl since last tick) or standing still. */
	public static MovementSpread.Stance stance(LocalPlayer player) {
		return MovementSpread.Stance.of(player.isSprinting(), player.isCrouching(), player.getDeltaMovement().horizontalDistanceSqr() > 4.0E-4);
	}

	/** Aiming or a recent shot: no sprinting, as the gun cannot be up and in the running pose at once (see LocalPlayerMixin). */
	public static boolean blocksSprint(LocalPlayer player) {
		return aimHeld || player.level().getGameTime() < noSprintUntil;
	}

	/** True while the held gun is reloading, by the server's state or by our own prediction. */
	public static boolean reloading(LocalPlayer player, ItemStack stack) {
		return stack.getOrDefault(ModComponents.GUN_STATE, GunState.EMPTY).reloading() || player.level().getGameTime() < predictedReloadUntil;
	}

	/** An empty gun that is not reloading has its slide locked back. A bolt action's bolt closes on the empty chamber instead. */
	public static boolean slideLocked(LocalPlayer player, ItemStack stack, GunDefinition definition) {
		return !definition.boltAction() && !reloading(player, stack) && (GunItem.ammo(stack, definition) == 0 || player.level().getGameTime() < predictedEmptyUntil);
	}

	/** The held gun's fire mode, by our own switch until the server's arrives. */
	public static String fireMode(LocalPlayer player, ItemStack stack, GunDefinition definition) {
		return predictedMode != null && player.level().getGameTime() < predictedModeUntil ? definition.fireMode(predictedMode) : GunItem.fireMode(stack, definition);
	}

	/** Reloading, fitting an attachment, or the gun before it still being put away: the gun takes no other action. */
	public static boolean busy(LocalPlayer player, ItemStack stack) {
		return reloading(player, stack) || player.level().getGameTime() < attachingUntil || !puttingAway.isEmpty();
	}

	/** The gun being put away, which the viewmodel draws instead of the item now in hand, or empty. */
	public static ItemStack puttingAway() {
		return puttingAway;
	}

	/**
	 * Fits {@code attachment} into {@code slot} of the held gun (null takes the slot's attachment off): plays the attach or detach
	 * animation straight away and asks the server, which checks it fits and swaps the items with the inventory.
	 */
	public static void attach(LocalPlayer player, AttachmentSlot slot, @Nullable AttachmentItem attachment) {
		ItemStack stack = player.getMainHandItem();
		if (!(stack.getItem() instanceof GunItem gun) || busy(player, stack)) {
			return;
		}
		attachingUntil = player.level().getGameTime() + GunItem.ATTACHMENT_TICKS;
		if (attachment != null) {
			PENDING.put(slot, attachment);
		}
		play(player, gun, attachment != null ? slot.attachAction() : slot.detachAction());
		ClientPlayNetworking.send(new AttachPayload(player.getInventory().getSelectedSlot(), slot, Optional.ofNullable(attachment).map(BuiltInRegistries.ITEM::getKey)));
	}

	/** Draws the held gun's optic reticle like {@code reticle}: here straight away, and on the server, which syncs it to everyone. */
	public static void reticle(LocalPlayer player, Reticle reticle) {
		ItemStack stack = player.getMainHandItem();
		if (stack.getItem() instanceof GunItem) {
			stack.set(ModComponents.RETICLE, reticle);
			ClientPlayNetworking.send(new ReticlePayload(player.getInventory().getSelectedSlot(), reticle));
		}
	}

	/** Sets how far the eye sits from {@code optic} when aimed (a share of its own distance), on the held gun: here straight away, and on the server. */
	public static void eyeShare(LocalPlayer player, AttachmentItem optic, float share) {
		ItemStack stack = player.getMainHandItem();
		if (stack.getItem() instanceof GunItem) {
			Attachments.setEyeShare(stack, optic, share);
			ClientPlayNetworking.send(new EyeDistancePayload(player.getInventory().getSelectedSlot(), BuiltInRegistries.ITEM.getKey(optic), share));
		}
	}

	/** Colors the held gun's laser sight: here straight away, and on the server, which syncs it to everyone. */
	public static void laserColor(LocalPlayer player, int color) {
		ItemStack stack = player.getMainHandItem();
		if (stack.getItem() instanceof GunItem) {
			stack.set(ModComponents.LASER_COLOR, color);
			ClientPlayNetworking.send(new LaserColorPayload(player.getInventory().getSelectedSlot(), color));
		}
	}

	/** The laser or flashlight on {@code stack} the switch key acts on: the last one selected if it is fitted, else the first; null for none. */
	public static @Nullable AttachmentItem switchSelected(ItemStack stack) {
		List<AttachmentItem> devices = Attachments.switchable(stack);
		return devices.contains(switchSelected) ? switchSelected : devices.isEmpty() ? null : devices.getFirst();
	}

	/** Switches a laser or flashlight on the held gun: here straight away, and on the server, which syncs it to everyone. */
	private static void switchOn(LocalPlayer player, ItemStack stack, GunItem gun, AttachmentItem device, boolean on) {
		Attachments.switchOn(stack, device, on);
		player.playSound(gun.sound("select"), 0.7F, on ? 1.3F : 1.1F);
		ClientPlayNetworking.send(new SwitchPayload(player.getInventory().getSelectedSlot(), BuiltInRegistries.ITEM.getKey(device), on));
	}

	/** The power {@code optic} is turned to: its own for a fixed one, the scroll wheel's pick for a variable one. */
	public static float zoom(AttachmentItem optic) {
		return Mth.clamp(zoom, optic.minZoom(), optic.maxZoom());
	}

	/**
	 * The scroll wheel moved {@code steps} notches (up is positive). Aiming with a variable optic, it turns the power a step per notch
	 * instead of changing the hotbar slot; returns whether it did.
	 */
	public static boolean scrollZoom(int steps) {
		LocalPlayer player = Minecraft.getInstance().player;
		AttachmentItem optic = player != null && aiming ? Attachments.in(player.getMainHandItem(), AttachmentSlot.OPTIC) : null;
		if (optic == null || optic.maxZoom() <= optic.minZoom()) {
			return false;
		}
		float before = zoom(optic);
		zoom = Mth.clamp(before + steps, optic.minZoom(), optic.maxZoom());
		if (zoom != before) {
			player.playSound(((GunItem)player.getMainHandItem().getItem()).sound("optic_click"), 0.35F, 1.6F);
		}
		return true;
	}

	/** The attachment we asked to fit into {@code slot}, for the attach animation to show before the server's attachments arrive. */
	public static @Nullable AttachmentItem pending(AttachmentSlot slot) {
		return PENDING.get(slot);
	}

	/** Game time the current reload ends, preferring the server's timer over our prediction. */
	public static long reloadEnd(ItemStack stack) {
		GunState state = stack.getOrDefault(ModComponents.GUN_STATE, GunState.EMPTY);
		return state.reloading() ? state.reloadingUntilTick() : predictedReloadUntil;
	}

	/** Game time the current reload started, the same way (0 from a server state that does not keep it). */
	public static long reloadStart(ItemStack stack) {
		GunState state = stack.getOrDefault(ModComponents.GUN_STATE, GunState.EMPTY);
		return state.reloading() ? state.reloadStartTick() : predictedReloadStart;
	}

	/** The rounds the gun had when the current reload started, the same way. */
	public static int reloadFrom(ItemStack stack) {
		GunState state = stack.getOrDefault(ModComponents.GUN_STATE, GunState.EMPTY);
		return state.reloading() ? state.reloadFromAmmo() : predictedReloadFrom;
	}

	// Runs before vanilla reads the keys, so consuming the clicks here stops vanilla attacking or using with a gun in hand.
	// Held-key repeats that vanilla still reads are blocked by the callbacks in GunItem.registerEvents.
	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		boolean holding = player != null && GunItem.holdsGun(player);
		if (minecraft.level != readiedIn) {
			readiedIn = minecraft.level;
			READIED.clear();
			drawnStack = puttingAway = ItemStack.EMPTY;
		}
		putAway(player, holding);

		if (freshPress(AIM_TOGGLE)) {
			aimToggled = !aimToggled;
		}
		if (!holding || minecraft.gui.screen() != null) {
			aimToggled = false;
		}
		// With the toggle on use's own button, holding it must not aim too, or toggling off would only end on release.
		boolean useHeld = minecraft.options.keyUse.isDown() && !AIM_TOGGLE.same(minecraft.options.keyUse);
		// The attachment menu aims the gun behind it while a fitted optic's settings are open, so changing them can be watched.
		boolean previewing = AttachmentScreen.previewing(minecraft) && !busy(player, player.getMainHandItem());
		aimHeld = holding && (useHeld || aimToggled || previewing);
		// Out of a sprint the running pose comes down first, so the sights rise a little later.
		boolean aim = aimHeld && !Viewmodel.running();
		// The breath is held only while aiming, and comes back otherwise; the toggle lets go when the sights come down or it runs out.
		if (freshPress(BREATH_TOGGLE) && aim) {
			breathToggled = !breathToggled;
		}
		breathO = BREATH.breath();
		boolean breath = BREATH.tick(aim && (HOLD_BREATH.isDown() || breathToggled));
		breathToggled &= breath;
		if ((aim != aiming || breath != holdingBreath) && player != null) {
			ClientPlayNetworking.send(new AimPayload(aim, breath));
		}
		if (breath != holdingBreath && player != null) {
			breathSound(minecraft, player, breath);
		}
		if (aim != aiming && player != null && holding) {
			player.playSound(((GunItem)player.getMainHandItem().getItem()).sound(aim ? "aim_in" : "aim_out"), 1.0F, 1.0F);
		}
		aiming = aim;
		holdingBreath = breath;

		if (!holding) {
			// Still read every tick, to keep their held state right.
			freshPress(RELOAD);
			freshPress(INSPECT);
			freshPress(TUNER);
			freshPress(FIRE_MODE);
			freshPress(ATTACHMENTS);
			freshPress(SWITCH);
			predictedMode = null;
			attachingUntil = 0;
			PENDING.clear();
			reloadHeldTicks = -1;
			switchHeldTicks = -1;
			drawnSlot = -1;
			drawnItem = null;
			drawnStack = ItemStack.EMPTY;
			drawDue = false;
			predictedReloadUntil = 0;
			stopReloadAt = 0;
			noSprintUntil = 0;
			burstLeft = 0;
			Viewmodel.reset();
			return;
		}

		ItemStack stack = player.getMainHandItem();
		GunItem gun = (GunItem)stack.getItem();
		GunDefinition definition = GunItem.definition(stack);
		int slot = player.getInventory().getSelectedSlot();
		if (slot != drawnSlot || gun != drawnItem) {
			drawnSlot = slot;
			drawnItem = gun;
			aimToggled = false;
			predictedReloadUntil = 0;
			stopReloadAt = 0;
			reloadHeldTicks = -1;
			switchHeldTicks = -1;
			predictedMode = null;
			attachingUntil = 0;
			burstLeft = 0;
			PENDING.clear();
			Viewmodel.reset();
			drawDue = true;
		}
		drawnStack = stack;
		if (drawDue && puttingAway.isEmpty()) {
			drawDue = false;
			play(player, gun, READIED.add(gun) | !GunItem.readied(stack) ? "draw_first" : "draw");
		} else if (!drawDue && puttingAway.isEmpty() && !GunItem.readied(stack) && !busy(player, stack) && !playing(player, gun, null)) {
			// Never readied while already in hand (the arena refilled it on respawn), or a draw_first cut short: it is readied once nothing
			// else is playing. Its chamber keyframe chambers a round.
			play(player, gun, "draw_first");
		}
		if (definition != null) {
			AttachmentItem optic = Attachments.in(stack, AttachmentSlot.OPTIC);
			// Off the scope while the gun is busy, and while a bolt is worked between shots, so those can be seen.
			boolean scopeReady = optic != null && optic.isScope() && !busy(player, stack)
					&& (!definition.boltAction() || definition.shotDue(nextShotDue, player.level().getGameTime()));
			Viewmodel.tick(player, definition, aim, player.isSprinting() && !blocksSprint(player), minecraft.gui.screen() instanceof GunTunerScreen, scopeReady,
					optic != null && optic.isScope() ? zoom(optic) : 1);
		}

		// A tap reloads when the key is let go; holding it checks the mag instead, empty or not, so ammo can be read without the HUD.
		// Only a fresh press starts the timer: the key repeats while held, and a repeat restarting it made the release reload.
		if (freshPress(RELOAD)) {
			reloadHeldTicks = 0;
		}
		if (reloadHeldTicks >= 0) {
			if (!RELOAD.isDown()) {
				if (reloadHeldTicks < MAG_CHECK_HOLD_TICKS) {
					reload(player, stack, gun, definition);
				}
				reloadHeldTicks = -1;
			} else if (++reloadHeldTicks == MAG_CHECK_HOLD_TICKS && canInspect(player, stack, definition)) {
				inspect(player, gun, "inspect_mag_check");
			}
		}
		if (freshPress(INSPECT) && canInspect(player, stack, definition)) {
			inspect(player, gun, nextInspect(player, slideLocked(player, stack, definition)));
		}
		if (freshPress(TUNER) && definition != null && player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
			// The plain definition, not the one with attachments applied: the tuner edits the gun's own values.
			minecraft.gui.setScreen(new GunTunerScreen(gun.definitionId(), GunDefinitions.get(gun.definitionId()), stack.getHoverName()));
		}
		if (freshPress(ATTACHMENTS) && definition != null && !definition.attachments().isEmpty()) {
			minecraft.gui.setScreen(new AttachmentScreen());
		}
		// A tap steps to the next fire mode when the key is let go; holding puts the gun on safe, and either takes it off safe, back to
		// the mode it was on (as reload's tap and hold).
		if (freshPress(FIRE_MODE)) {
			fireModeHeldTicks = 0;
		}
		if (fireModeHeldTicks >= 0) {
			boolean held = FIRE_MODE.isDown() && ++fireModeHeldTicks == SAFE_HOLD_TICKS;
			if (!FIRE_MODE.isDown() && fireModeHeldTicks < SAFE_HOLD_TICKS || held) {
				switchFireMode(player, stack, gun, definition, slot, held);
			}
			if (!FIRE_MODE.isDown()) {
				fireModeHeldTicks = -1;
			}
		}
		// A tap switches the selected laser or flashlight when the key is let go; holding steps the selection instead, a step every
		// SWITCH_HOLD_TICKS (as reload's tap and hold).
		if (freshPress(SWITCH)) {
			switchHeldTicks = 0;
		}
		if (switchHeldTicks >= 0) {
			AttachmentItem device = switchSelected(stack);
			if (!SWITCH.isDown()) {
				if (switchHeldTicks < SWITCH_HOLD_TICKS && device != null) {
					switchOn(player, stack, gun, device, !Attachments.switchedOn(stack, device));
				}
				switchHeldTicks = -1;
			} else if (++switchHeldTicks % SWITCH_HOLD_TICKS == 0) {
				List<AttachmentItem> devices = Attachments.switchable(stack);
				if (devices.size() > 1) {
					switchSelected = devices.get((devices.indexOf(device) + 1) % devices.size());
					player.playSound(gun.sound("select"), 0.4F, 1.6F);
				}
			}
		}
		if (stopReloadAt != 0 && player.level().getGameTime() >= stopReloadAt) {
			stopReloadAt = 0;
			play(player, gun, GunItem.RELOAD_STOP);
		}
		while (minecraft.options.keyAttack.consumeClick()) {
			if (stopReload(player, stack, definition)) {
				continue;
			}
			// Clicks during a burst do nothing, as on the real trigger.
			if (burstLeft == 0 && shoot(player, stack, gun, definition) && GunDefinition.BURST.equals(fireMode(player, stack, definition))) {
				burstLeft = GunDefinition.BURST_ROUNDS - 1;
			}
		}
		if (definition != null && "auto".equals(fireMode(player, stack, definition)) && minecraft.options.keyAttack.isDown()) {
			shoot(player, stack, gun, definition);
		}
		if (burstLeft > 0 && definition != null && definition.shotDue(nextShotDue, player.level().getGameTime())) {
			// A gun that runs dry, or a shot refused (a reload started, the mode switched to safe), ends the burst.
			burstLeft--;
			if (slideLocked(player, stack, definition) || !shoot(player, stack, gun, definition)) {
				burstLeft = 0;
			}
		}
		while (minecraft.options.keyUse.consumeClick()) {
			// Aiming reads keyUse.isDown() above, the clicks only need to be kept from vanilla.
		}
		while (minecraft.options.keySwapOffhand.consumeClick()) {
			// Guns stay in the main hand; the server also moves any that reach the offhand another way.
		}
	}

	/** A breath in as it is held; out as it is let go, cutting a quick tap's breath in short, or a gasp when it ran out. Only the holder hears it. */
	private static void breathSound(Minecraft minecraft, LocalPlayer player, boolean held) {
		Identifier hold = McWeapons.id("breath.hold");
		if (!held) {
			minecraft.getSoundManager().stop(hold, SoundSource.PLAYERS);
		}
		Identifier sound = held ? hold : McWeapons.id(BREATH.winded() ? "breath.gasp" : "breath.release");
		player.playSound(SoundEvent.createVariableRangeEvent(sound), 1.0F, 1.0F);
	}

	/**
	 * A gun that left the hand (another slot, another item, dropped) is put away: it lowers out of view, drawn by the viewmodel in place
	 * of the new item, which comes up once it is gone (vanilla's raise for an item, the draw for a gun). The switch itself is not held
	 * up, so scrolling the hotbar stays as fast as ever.
	 */
	private static void putAway(@Nullable LocalPlayer player, boolean holding) {
		if (player == null) {
			drawnStack = puttingAway = ItemStack.EMPTY;
			return;
		}
		long now = player.level().getGameTime();
		boolean left = !drawnStack.isEmpty() && (!holding || player.getInventory().getSelectedSlot() != drawnSlot || player.getMainHandItem().getItem() != drawnItem);
		if (left) {
			puttingAway = drawnStack;
			GunDefinition definition = GunItem.definition(drawnStack);
			putAwayUntil = now + (definition != null ? definition.feel() : GunFeel.DEFAULT).putAwayTicks();
			drawnStack = ItemStack.EMPTY;
			play(player, (GunItem)puttingAway.getItem(), "put_away");
		} else if (!puttingAway.isEmpty() && now >= putAwayUntil) {
			puttingAway = ItemStack.EMPTY;
			if (!holding) {
				player.firstPersonHandsAndItems().itemUsed(InteractionHand.MAIN_HAND);
			}
		}
	}

	/** Pulls the trigger if a shot is due; true when a round fired (not a dry click or the safe). */
	private static boolean shoot(LocalPlayer player, ItemStack stack, GunItem gun, @Nullable GunDefinition definition) {
		long now = player.level().getGameTime();
		if (definition == null || busy(player, stack) || !definition.shotDue(nextShotDue, now)) {
			return false;
		}
		// On safe the trigger is blocked: it clicks like an empty gun and nothing fires (the server checks too).
		boolean safe = GunDefinition.SAFE.equals(fireMode(player, stack, definition));
		if (safe) {
			GunHud.safeBlocked();
		}
		// Nothing chambered behind a full magazine (a gun never readied): the trigger works the bolt instead of clicking, as reloading does.
		if (!safe && !GunItem.readied(stack)) {
			chamber(player, gun);
			return false;
		}
		nextShotDue = definition.nextShotDue(nextShotDue, now);
		noSprintUntil = now + SHOT_NO_SPRINT_TICKS;
		int ammo = GunItem.ammo(stack, definition);
		boolean fires = !safe && GunItem.chambered(stack, definition);
		// The shot leaves along the barrel as it was last drawn (from before this shot's recoil kick), wherever the gun points: a gun swung
		// away shoots away. A laser's dot is on the same line; the server fires the same bullet from these. Without the viewmodel drawn
		// (third person), from the eye along the look.
		Vec3 origin = player.getEyePosition();
		float xRot = player.getXRot();
		float yRot = player.getYRot();
		Viewmodel.BarrelLine barrel = Viewmodel.barrelLine(player, origin, xRot, yRot, Minecraft.getInstance().gameRenderer.mainCamera(), 1);
		if (barrel != null) {
			origin = barrel.from();
			xRot = (float)Math.toDegrees(-Math.atan2(barrel.direction().y, barrel.direction().horizontalDistance()));
			yRot = player.getYRot() + Mth.wrapDegrees((float)Math.toDegrees(Math.atan2(-barrel.direction().x, barrel.direction().z)) - player.getYRot());
		}
		long seed = player.getRandom().nextLong();
		MovementSpread.Stance stance = stance(player);
		int shotId = ++lastShotId;
		player.playSound(gun.sound(!fires ? "dry" : Attachments.silenced(stack) ? "fire_silenced" : "fire"), 1.0F, 1.0F);
		if (fires) {
			play(player, gun, "fire");
			// Before the recoil kick moves the view. A bolt action's fire animation throws it out as the bolt comes back.
			if (!definition.boltAction()) {
				Casings.eject(player, gun);
			}
			ThirdPersonGuns.flash(player.getId());
			Vec3 direction = Ballistics.direction(xRot, yRot, seed, definition.spread(aiming, stance));
			// Rounded like the server's bullet (see Bullets.fire), so this tracer and the bullet follow one path.
			Vec3 velocity = new Vec3(direction.scale(definition.bullet().speedPerTick()).toVector3f());
			Vec3 muzzle = Tracers.muzzle(player);
			Tracers.fire(player.getId(), shotId, origin, velocity, (float)definition.bullet().gravityPerTick(), (float)definition.range(), muzzle, true);
			if (!Attachments.silenced(stack)) {
				MuzzleLight.flash(player.getId(), muzzle);
			}
			Viewmodel.onShot(player, definition);
			if (ammo == 1) {
				// Covers the round trip until the server says the gun is empty.
				predictedEmptyUntil = now + 10;
			}
		}
		ClientPlayNetworking.send(new ShootPayload(player.getInventory().getSelectedSlot(), now, seed, shotId, origin, xRot, yRot, stance));
		return fires;
	}

	/** Predicts and asks for the fire mode key's switch (see {@link GunDefinition#nextFireMode}). Same check as the server: not mid-reload (and not while fitting an attachment). */
	private static void switchFireMode(LocalPlayer player, ItemStack stack, GunItem gun, @Nullable GunDefinition definition, int slot, boolean held) {
		if (definition == null || busy(player, stack)) {
			return;
		}
		predictedMode = definition.nextFireMode(fireMode(player, stack, definition), stack.get(ModComponents.MODE_BEFORE_SAFE), held);
		predictedModeUntil = player.level().getGameTime() + 20;
		play(player, gun, "fire_mode");
		ClientPlayNetworking.send(new FireModePayload(slot, held));
	}

	/**
	 * Same checks as the server, so the animation only starts for a reload the server will accept. Not until the last shot is done
	 * either, which on a bolt action means its bolt worked.
	 */
	private static void reload(LocalPlayer player, ItemStack stack, GunItem gun, @Nullable GunDefinition definition) {
		long now = player.level().getGameTime();
		if (definition == null || busy(player, stack) || !definition.shotDue(nextShotDue, now)) {
			return;
		}
		// Nothing chambered behind a full magazine (a gun never readied): reloading chambers a round instead.
		if (!GunItem.readied(stack)) {
			chamber(player, gun);
			return;
		}
		int ammo = GunItem.ammo(stack, definition);
		if (ammo >= definition.reloadTarget(ammo)) {
			return;
		}
		predictedReloadUntil = now + definition.reloadDuration(ammo);
		predictedReloadStart = now;
		predictedReloadFrom = ammo;
		stopReloadAt = 0;
		predictedEmptyUntil = 0;
		play(player, gun, definition.reloadAction(ammo));
		ClientPlayNetworking.send(new ReloadPayload(player.getInventory().getSelectedSlot(), 0));
	}

	/**
	 * A click during our round-at-a-time reload stops it: the round being pushed in goes in, then the bolt closes and the rounds already
	 * in stay. The server is told how many, so its count is what the animation showed. True when the click went to that.
	 */
	private static boolean stopReload(LocalPlayer player, ItemStack stack, @Nullable GunDefinition definition) {
		RoundReload rounds = definition == null ? null : definition.roundReload();
		long now = player.level().getGameTime();
		if (rounds == null || predictedReloadFrom == 0 || now >= predictedReloadUntil) {
			return false;
		}
		int planned = rounds.rounds(predictedReloadStart, predictedReloadUntil);
		int kept = rounds.keptWhenStopped(planned, now - predictedReloadStart);
		if (stopReloadAt == 0 && kept < planned) {
			stopReloadAt = predictedReloadStart + rounds.startTicks() + (long)kept * rounds.roundTicks();
			predictedReloadUntil = stopReloadAt + rounds.endTicks();
			ClientPlayNetworking.send(new ReloadPayload(player.getInventory().getSelectedSlot(), kept));
		}
		return true;
	}

	/**
	 * Consumes a key's clicks and says whether it was freshly pressed this tick. The OS repeats a held key, and vanilla counts each repeat
	 * as a click, so without this, holding a key would act again about 30 times a second.
	 */
	private static boolean freshPress(KeyMapping key) {
		boolean clicked = false;
		while (key.consumeClick()) {
			clicked = true;
		}
		boolean fresh = clicked && !HELD_KEYS.contains(key);
		if (key.isDown()) {
			HELD_KEYS.add(key);
		} else {
			HELD_KEYS.remove(key);
		}
		return fresh;
	}

	/** Inspects are cosmetic, so they wait for reloads and shots to finish. */
	private static boolean canInspect(LocalPlayer player, ItemStack stack, @Nullable GunDefinition definition) {
		return definition != null && !busy(player, stack) && definition.shotDue(nextShotDue, player.level().getGameTime());
	}

	/**
	 * A random inspect variant for the inspect key, never the same one twice in a row. The mag check is not one: it is holding reload. A
	 * slide locked back on an empty gun cannot be press checked.
	 */
	private static String nextInspect(LocalPlayer player, boolean slideLocked) {
		List<String> options = GunItem.INSPECT_ACTIONS.stream()
				.filter(action -> !action.equals("inspect_mag_check") && !(slideLocked && action.equals("inspect_press_check"))).toList();
		int last = options.indexOf(lastInspect);
		int pick = player.getRandom().nextInt(last < 0 ? options.size() : options.size() - 1);
		if (last >= 0 && pick >= last) {
			pick++;
		}
		lastInspect = options.get(pick);
		return lastInspect;
	}

	/** Inspects are picked here, so the server is told which one to show everyone else. */
	private static void inspect(LocalPlayer player, GunItem gun, String action) {
		play(player, gun, action);
		ClientPlayNetworking.send(new GunAnimationPayload(player.getId(), action));
	}

	private static void play(LocalPlayer player, GunItem gun, String action) {
		gun.getAnimatableInstanceCache().getManagerForId(GunRenderer.instanceId(player)).tryTriggerAnimation(GunItem.MAIN_CONTROLLER, action);
	}

	/**
	 * {@code action} is playing in our hands, or with null any triggered animation is (a finished triggered animation stays the current one
	 * until the next, hence the finished check).
	 */
	private static boolean playing(LocalPlayer player, GunItem gun, @Nullable String action) {
		var main = gun.getAnimatableInstanceCache().getManagerForId(GunRenderer.instanceId(player)).getAnimationControllers().get(GunItem.MAIN_CONTROLLER);
		return main != null && !main.hasAnimationFinished() && (action == null ? main.isPlayingTriggeredAnimation() : main.isTriggeredAnimation(action));
	}

	/** Works the action of a gun never readied: its draw_first, whose chamber keyframe chambers a round. Not again while it plays. */
	private static void chamber(LocalPlayer player, GunItem gun) {
		if (!playing(player, gun, "draw_first")) {
			play(player, gun, "draw_first");
		}
	}

	/** The draw_first in our hands reached its {@code chamber} keyframe: a gun never readied has a round chambered (the server moves it in). */
	public static void chamberKeyframe(LocalPlayer player, GunItem gun) {
		ItemStack stack = player.getMainHandItem();
		if (stack.getItem() == gun && !GunItem.readied(stack)) {
			ClientPlayNetworking.send(new ChamberPayload(player.getInventory().getSelectedSlot()));
		}
	}
}

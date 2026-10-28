package com.noxloveya.mcweapons.client.render;

import com.geckolib.animation.state.BoneSnapshot;
import com.geckolib.cache.model.BakedGeoModel;
import com.geckolib.cache.model.GeoBone;
import com.geckolib.cache.model.GeoQuad;
import com.geckolib.cache.model.GeoVertex;
import com.geckolib.cache.model.cuboid.CuboidGeoBone;
import com.geckolib.cache.model.cuboid.GeoCube;
import com.geckolib.constant.DataTickets;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.model.DefaultedItemGeoModel;
import com.geckolib.renderer.GeoItemRenderer;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.noxloveya.mcweapons.client.input.GunInput;
import com.noxloveya.mcweapons.gun.AttachmentItem;
import com.noxloveya.mcweapons.gun.AttachmentSlot;
import com.noxloveya.mcweapons.gun.Attachments;
import com.noxloveya.mcweapons.gun.GunDefinition;
import com.noxloveya.mcweapons.gun.GunItem;
import com.noxloveya.mcweapons.gun.GunState;
import com.noxloveya.mcweapons.gun.Reticle;
import com.noxloveya.mcweapons.gun.RoundReload;
import com.noxloveya.mcweapons.registry.ModComponents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** One renderer for every gun: geo, texture and animations are found by gun id through GeckoLib's defaulted item paths. */
public class GunRenderer extends GeoItemRenderer<GunItem> {
	public static final DataTicket<Identifier> SKIN = DataTicket.create("mcweapons_skin", Identifier.class);
	public static final DataTicket<Boolean> MUZZLE_FLASH = DataTicket.create("mcweapons_muzzle_flash", Boolean.class);
	/** Share of the mag still loaded, 0 to 1. The {@code mag_round_N} bones (1 at the top) show that many rounds, so a mag check reads the count. */
	public static final DataTicket<Float> MAG_FILL = DataTicket.create("mcweapons_mag_fill", Float.class);
	/** A round is in the chamber: the {@code chamber_round} bone shows through the ejection port whenever the slide is back. */
	public static final DataTicket<Boolean> CHAMBERED = DataTicket.create("mcweapons_chambered", Boolean.class);
	/** A reload animation is playing: the gun's {@code prop_} bones (a stripper clip, a round in the other hand) are drawn only then. */
	public static final DataTicket<Boolean> RELOADING = DataTicket.create("mcweapons_reloading", Boolean.class);
	/** Furthest a magazine or attachment strays from its place on a gun in someone's hand, in model pixels (see keepInReach). */
	private static final float THIRD_PERSON_REACH = 6;
	/** Attachment item ids to draw: the ones fitted, and one on its way on or off in an attach or detach animation. */
	@SuppressWarnings("unchecked")
	public static final DataTicket<Set<Identifier>> ATTACHMENTS = (DataTicket<Set<Identifier>>)(DataTicket<?>)DataTicket.create("mcweapons_attachments", Set.class);
	/** How a fitted optic draws its reticle (see {@link ReticleLayer}). */
	public static final DataTicket<Reticle> RETICLE = DataTicket.create("mcweapons_reticle", Reticle.class);
	/** What each holder last had fitted per slot, so a detach animation can still show the attachment the server already took off. */
	private static final Map<Long, Map<AttachmentSlot, Identifier>> LAST_FITTED = new HashMap<>();
	/** Third person: the holder aims with both hands, the gun arm turned in (see {@link ThirdPersonGuns#poseAimHold}). */
	public static final DataTicket<Boolean> THIRD_PERSON_AIM = DataTicket.create("mcweapons_third_person_aim", Boolean.class);
	// Far above any stack hash or GeckoLib stack id, so holder-based ids never collide with them.
	private static final long HOLDER_ID_BASE = 1L << 40;
	// Third person: the model is built around the eye, so it is shrunk and moved until the grip sits in the hand.
	private static final float THIRD_PERSON_SCALE = 0.75F;
	/** A long gun is shrunk further in a hand, to at most this many blocks long: a rifle about as long as a person is tall looks wrong. */
	private static final float THIRD_PERSON_MAX_LENGTH = 1.25F;
	/** Outside a hand (the inventory, dropped, in a frame or on a display) the gun lies still: one shared instance, never animated. */
	private static final long STILL_ID = HOLDER_ID_BASE - 1;
	/** A gun this many times longer than tall (bare) is shown on the diagonal outside a hand, like a sword, where it has the most room. */
	private static final float LONG_GUN = 3.5F;
	/** Dropped, a gun is this much of its size in a hand. */
	private static final float GROUND_SCALE = 0.6F;
	/** In the inventory a gun spans 14 of the slot's 16 pixels, clear of its frame like vanilla's icons. */
	private static final float ICON_FILL = 14 / 16.0F;

	private final Identifier modelId;
	private float thirdPersonScale = -1;
	private @Nullable List<String> propBones;
	/** Rest-pose boxes around the gun with each set of fitted attachments, for framing it outside a hand. */
	private final Map<Set<Identifier>, float[]> stillBounds = new HashMap<>();

	public GunRenderer(GunItem gun) {
		super(new DefaultedItemGeoModel<>(gun.definitionId()));
		this.modelId = gun.definitionId().withPrefix("item/");
		withRenderLayer(new ArmSkinLayer(this, "arm_right"));
		withRenderLayer(new ArmSkinLayer(this, "arm_left"));
		withRenderLayer(new MuzzleFlashLayer(this));
		withRenderLayer(new ReticleLayer(this));
		withRenderLayer(new ScopeLensLayer(this));
		withRenderLayer(new ShowcasePoseLayer(this));
		withRenderLayer(new BonePoseLayer(this));
	}

	/** Animation state lives per holder, not per stack: the client gets a new stack every time the server syncs ammo, which would restart a running reload. */
	public static long instanceId(Entity holder) {
		return HOLDER_ID_BASE + holder.getId();
	}

	public static @Nullable Entity holder(Level level, long instanceId) {
		long entityId = instanceId - HOLDER_ID_BASE;
		return entityId >= 0 && entityId <= Integer.MAX_VALUE ? level.getEntity((int)entityId) : null;
	}

	public static boolean firstPerson(GeoRenderState state) {
		ItemDisplayContext context = state.getGeckolibData(DataTickets.ITEM_RENDER_PERSPECTIVE);
		return context != null && context.firstPerson();
	}

	/** In first person or in someone's hand; anywhere else the gun is drawn still (see {@link #frameStill}). */
	private static boolean inHand(@Nullable ItemDisplayContext context) {
		return context != null && (context.firstPerson() || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND);
	}

	/** Rest position of a bone's pivot in model space, in blocks. For this model that is view space (eye at the origin, looking down -Z). */
	public Vector3f restPivot(String boneName) {
		BakedGeoModel model = getGeoModel().getBakedModel(this.modelId);
		return model.getBone(boneName).map(bone -> new Vector3f(bone.pivotX(), bone.pivotY(), bone.pivotZ()).div(16)).orElseGet(Vector3f::new);
	}

	/** A scope's lens radius in blocks: from its eyepiece ({@code <scope>_reticle}) to the rim ({@code <scope>_lens}). */
	public float lensRadius(Identifier scope) {
		return restPivot(scope.getPath() + "_lens").distance(restPivot(scope.getPath() + "_reticle"));
	}

	/** Rest-pose box around the gun (the {@code gun} bone and everything under it) in model space, in blocks: min xyz, then max xyz. */
	public float[] gunBounds() {
		return gunBounds(Set.of());
	}

	/** {@link #gunBounds()} with the {@code fitted} attachments (bones named after their items). */
	private float[] gunBounds(Set<String> fitted) {
		float[] bounds = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
		getGeoModel().getBakedModel(this.modelId).getBone("gun").ifPresent(bone -> growBounds(bone, bounds, fitted));
		return bounds;
	}

	private static void growBounds(GeoBone bone, float[] bounds, Set<String> fitted) {
		if (bone instanceof CuboidGeoBone cuboid) {
			for (GeoCube cube : cuboid.cubes) {
				for (GeoQuad quad : cube.quads()) {
					for (GeoVertex vertex : quad.vertices()) {
						bounds[0] = Math.min(bounds[0], vertex.posX());
						bounds[1] = Math.min(bounds[1], vertex.posY());
						bounds[2] = Math.min(bounds[2], vertex.posZ());
						bounds[3] = Math.max(bounds[3], vertex.posX());
						bounds[4] = Math.max(bounds[4], vertex.posY());
						bounds[5] = Math.max(bounds[5], vertex.posZ());
					}
				}
			}
		}
		for (GeoBone child : bone.children()) {
			// Attachments are hidden unless fitted, so only the fitted ones count towards the gun's size.
			if (!child.name().startsWith("attach_")) {
				growBounds(child, bounds, fitted);
			} else {
				for (GeoBone attachment : child.children()) {
					if (fitted.contains(attachment.name())) {
						growBounds(attachment, bounds, fitted);
					}
				}
			}
		}
	}

	@Override
	public long getInstanceId(GunItem gun, RenderData renderData) {
		if (!inHand(renderData.renderPerspective())) {
			return STILL_ID;
		}
		LivingEntity holder = renderData.itemOwner() == null ? null : renderData.itemOwner().asLivingEntity();
		return holder != null ? instanceId(holder) : super.getInstanceId(gun, renderData);
	}

	@Override
	public void addRenderData(GunItem gun, @Nullable RenderData renderData, GeoRenderState renderState, float partialTick) {
		if (renderData != null && renderData.itemOwner() != null && renderData.itemOwner().asLivingEntity() instanceof AbstractClientPlayer player) {
			renderState.addGeckolibData(SKIN, player.getSkin().body().texturePath());
		}
		// A gun in the hotbar is the player's too, but it is not in their hands: it does not flash, reload or animate with the held one.
		LivingEntity holder = renderData == null || renderData.itemOwner() == null || !inHand(renderData.renderPerspective())
				? null : renderData.itemOwner().asLivingEntity();
		boolean firstPerson = renderData != null && renderData.renderPerspective().firstPerson();
		boolean silenced = renderData != null && Attachments.silenced(renderData.itemStack());
		renderState.addGeckolibData(MUZZLE_FLASH, !silenced && (firstPerson ? Viewmodel.muzzleFlash() : holder != null && ThirdPersonGuns.flashing(holder)));
		renderState.addGeckolibData(ATTACHMENTS, renderData != null ? shownAttachments(gun, renderData.itemStack(), holder) : Set.of());
		renderState.addGeckolibData(RETICLE, renderData != null ? renderData.itemStack().getOrDefault(ModComponents.RETICLE, Reticle.DEFAULT) : Reticle.DEFAULT);
		renderState.addGeckolibData(THIRD_PERSON_AIM, !firstPerson && holder != null && ThirdPersonGuns.pose(holder) == ThirdPersonGuns.Pose.AIM);
		renderState.addGeckolibData(GunItem.SLIDE_LOCKED, renderData != null && slideLocked(renderData));
		renderState.addGeckolibData(MAG_FILL, renderData != null ? magFill(renderData, partialTick) : 1);
		renderState.addGeckolibData(CHAMBERED, renderData != null && chambered(renderData));
		renderState.addGeckolibData(RELOADING, holder != null && reloadPlaying(gun, holder));
		renderState.addGeckolibData(GunItem.SHOWCASE, renderData != null && renderData.renderPerspective().firstPerson() && Viewmodel.showcasing());
	}

	/**
	 * The fitted attachments, plus the one in the hand during an attach or detach animation: an attachment being fitted shows before the
	 * server's attachments arrive (our own pick, see GunInput, or for someone else the only one that fits that slot), and one being taken
	 * off still shows after they have.
	 */
	private static Set<Identifier> shownAttachments(GunItem gun, ItemStack stack, @Nullable LivingEntity holder) {
		Map<String, Identifier> fitted = Attachments.of(stack);
		if (holder == null) {
			return Set.copyOf(fitted.values());
		}
		var main = gun.getAnimatableInstanceCache().getManagerForId(instanceId(holder)).getAnimationControllers().get(GunItem.MAIN_CONTROLLER);
		// A finished triggered animation stays the controller's current one until the next is triggered: only a playing one counts, or
		// an attachment taken off would come back on the gun once its detach ended.
		boolean animating = main != null && !main.hasAnimationFinished();
		Map<AttachmentSlot, Identifier> last = LAST_FITTED.computeIfAbsent(instanceId(holder), id -> new EnumMap<>(AttachmentSlot.class));
		Set<Identifier> shown = new HashSet<>();
		for (AttachmentSlot slot : AttachmentSlot.values()) {
			Identifier id = fitted.get(slot.getSerializedName());
			if (id != null) {
				last.put(slot, id);
			}
			if (animating && main.isTriggeredAnimation(slot.detachAction())) {
				id = last.get(slot);
			} else if (id == null && animating && main.isTriggeredAnimation(slot.attachAction())) {
				// Someone else's animation can arrive a tick before their gun's new attachments.
				AttachmentItem pending = holder == Minecraft.getInstance().player ? GunInput.pending(slot) : onlyFitting(stack, slot);
				id = pending == null ? null : BuiltInRegistries.ITEM.getKey(pending);
			}
			if (id != null) {
				shown.add(id);
			}
		}
		return shown;
	}

	/** The attachment for {@code slot} when the gun's definition lists exactly one, else null (which one is coming cannot be known yet). */
	private static @Nullable AttachmentItem onlyFitting(ItemStack stack, AttachmentSlot slot) {
		GunDefinition definition = GunItem.definition(stack);
		AttachmentItem only = null;
		for (Identifier id : definition == null ? List.<Identifier>of() : definition.attachments()) {
			if (BuiltInRegistries.ITEM.getValue(id) instanceof AttachmentItem item && item.slot() == slot) {
				if (only != null) {
					return null;
				}
				only = item;
			}
		}
		return only;
	}

	/** The bone shots leave from: a fitted attachment's own {@code <attachment>_muzzle} locator (a silencer's tip), or the gun's {@code muzzle}. */
	public String muzzleBone(ItemStack stack) {
		return attachmentBone(stack, "_muzzle", "muzzle");
	}

	/** The bone aiming lines up with the eye: a fitted optic's {@code <attachment>_reticle}, or the gun's {@code ads_anchor} (the iron sights). */
	public String aimBone(ItemStack stack) {
		return attachmentBone(stack, "_reticle", "ads_anchor");
	}

	private String attachmentBone(ItemStack stack, String suffix, String fallback) {
		BakedGeoModel model = getGeoModel().getBakedModel(this.modelId);
		for (Identifier id : Attachments.of(stack).values()) {
			String bone = id.getPath() + suffix;
			if (model.getBone(bone).isPresent()) {
				return bone;
			}
		}
		return fallback;
	}

	/** Scales a bone's translation down to at most {@link #THIRD_PERSON_REACH} pixels from its rest place. */
	private static void keepInReach(BoneSnapshot snapshot) {
		float x = snapshot.getTranslateX();
		float y = snapshot.getTranslateY();
		float z = snapshot.getTranslateZ();
		float length = Mth.sqrt(x * x + y * y + z * z);
		if (length > THIRD_PERSON_REACH) {
			float scale = THIRD_PERSON_REACH / length;
			snapshot.setTranslation(x * scale, y * scale, z * scale);
		}
	}

	private static boolean slideLocked(RenderData renderData) {
		ItemStack stack = renderData.itemStack();
		GunDefinition definition = GunItem.definition(stack);
		if (definition == null || definition.boltAction()) {
			return false;
		}
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null && renderData.itemOwner() != null && renderData.itemOwner().asLivingEntity() == player) {
			return GunInput.slideLocked(player, stack, definition);
		}
		return GunItem.ammo(stack, definition) == 0 && !stack.getOrDefault(ModComponents.GUN_STATE, GunState.EMPTY).reloading();
	}

	/**
	 * Any round at all sits in the chamber (a locked-back slide means the last one has gone, even before the server says so). Not
	 * during a reload: an open bolt has taken it out, and a slide that locked back has none.
	 */
	private static boolean chambered(RenderData renderData) {
		ItemStack stack = renderData.itemStack();
		GunDefinition definition = GunItem.definition(stack);
		return definition != null && GunItem.ammo(stack, definition) > 0 && !slideLocked(renderData) && !reloading(renderData);
	}

	/**
	 * A reload animation is playing in {@code holder}'s hands. Not the reload's timer: the animation keeps its own clock, and once it ends
	 * the gun is back at rest, where the props would show standing in the gun until the timer runs out.
	 */
	private static boolean reloadPlaying(GunItem gun, LivingEntity holder) {
		var main = gun.getAnimatableInstanceCache().getManagerForId(instanceId(holder)).getAnimationControllers().get(GunItem.MAIN_CONTROLLER);
		// A finished triggered animation stays the controller's current one until the next is triggered, hence the finished check.
		if (main == null || main.hasAnimationFinished()) {
			return false;
		}
		if (main.isTriggeredAnimation("reload") || main.isTriggeredAnimation("reload_empty")) {
			return true;
		}
		for (int rounds = 1; rounds <= GunItem.MAX_ROUND_RELOAD; rounds++) {
			if (main.isTriggeredAnimation(GunDefinition.roundReloadAction(rounds))) {
				return true;
			}
		}
		return false;
	}

	private static boolean reloading(RenderData renderData) {
		ItemStack stack = renderData.itemStack();
		LocalPlayer player = Minecraft.getInstance().player;
		return player != null && renderData.itemOwner() != null && renderData.itemOwner().asLivingEntity() == player
				? GunInput.reloading(player, stack) : stack.getOrDefault(ModComponents.GUN_STATE, GunState.EMPTY).reloading();
	}

	/**
	 * Rounds in the mag over its size; the chambered round is not in it. A magazine reload brings a full one (its animation hides the
	 * rounds until they go in), and a round-at-a-time reload adds them as the thumb pushes each one down.
	 */
	private static float magFill(RenderData renderData, float partialTick) {
		ItemStack stack = renderData.itemStack();
		GunDefinition definition = GunItem.definition(stack);
		if (definition == null) {
			return 1;
		}
		int ammo = GunItem.ammo(stack, definition);
		int inMag = Math.max(0, ammo - 1);
		if (reloading(renderData)) {
			RoundReload rounds = definition.roundReload();
			Level level = Minecraft.getInstance().level;
			if (rounds == null || ammo == 0 || level == null) {
				return 1;
			}
			LocalPlayer player = Minecraft.getInstance().player;
			long end = player != null && renderData.itemOwner() != null && renderData.itemOwner().asLivingEntity() == player
					? GunInput.reloadEnd(stack) : stack.getOrDefault(ModComponents.GUN_STATE, GunState.EMPTY).reloadingUntilTick();
			int missing = definition.reloadTarget(ammo) - ammo;
			float elapsed = definition.reloadDuration(ammo) - (end - level.getGameTime() - partialTick);
			// The bolt is open, so the chambered round is out and back in the mag's count.
			inMag = ammo + rounds.loaded(missing, elapsed);
		}
		return Math.min(1, inMag / (float)definition.magSize());
	}

	/** Names of the model's {@code prop_} bones, found once. */
	private List<String> propBones() {
		if (this.propBones == null) {
			List<String> names = new ArrayList<>();
			for (GeoBone bone : getGeoModel().getBakedModel(this.modelId).topLevelBones()) {
				collectProps(bone, names);
			}
			this.propBones = List.copyOf(names);
		}
		return this.propBones;
	}

	private static void collectProps(GeoBone bone, List<String> names) {
		if (bone.name().startsWith("prop_")) {
			names.add(bone.name());
		}
		for (GeoBone child : bone.children()) {
			collectProps(child, names);
		}
	}

	/** How much smaller the gun is in a hand than in first person: {@link #THIRD_PERSON_SCALE}, or less for a long gun. */
	private float thirdPersonScale() {
		if (this.thirdPersonScale < 0) {
			float[] bounds = gunBounds();
			float length = bounds[5] - bounds[2];
			this.thirdPersonScale = length > 0 ? Math.min(THIRD_PERSON_SCALE, THIRD_PERSON_MAX_LENGTH / length) : THIRD_PERSON_SCALE;
		}
		return this.thirdPersonScale;
	}

	@Override
	public void adjustRenderPose(RenderPassInfo<GeoRenderState> renderPassInfo) {
		if (firstPerson(renderPassInfo.renderState())) {
			return;
		}
		ItemDisplayContext context = renderPassInfo.renderState().getGeckolibData(DataTickets.ITEM_RENDER_PERSPECTIVE);
		if (!inHand(context)) {
			frameStill(renderPassInfo.poseStack(), context, renderPassInfo.renderState().getGeckolibData(ATTACHMENTS));
			return;
		}
		super.adjustRenderPose(renderPassInfo);
		PoseStack poseStack = renderPassInfo.poseStack();
		Vector3f grip = restPivot("gun");
		float scale = thirdPersonScale();
		poseStack.scale(scale, scale, scale);
		// The hand's -Z runs along the arm, out of the fist, which is the way the model's barrel points: the gun lies along the arm in every
		// pose, down at the side and out ahead when the arm is raised.
		if (Boolean.TRUE.equals(renderPassInfo.renderState().getGeckolibData(THIRD_PERSON_AIM))) {
			// The gun arm is turned in to meet the other hand; turn the gun back so it points where the holder looks.
			boolean leftHand = renderPassInfo.renderState().getGeckolibData(DataTickets.ITEM_RENDER_PERSPECTIVE) == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
			poseStack.rotate(Axis.YP, leftHand ? ThirdPersonGuns.AIM_GUN_ARM_IN : -ThirdPersonGuns.AIM_GUN_ARM_IN);
		}
		poseStack.translate(-grip.x, -grip.y, -grip.z);
	}

	/**
	 * Outside a hand: side on, the gun's right side to the viewer and the muzzle to the right (a long gun on the diagonal, muzzle up, like
	 * a sword), framed by its size with what is fitted, so a scope or a silencer always fits. In the inventory, in frames and on displays
	 * it fills the block the flat icon did, its back where the icon's was (frames and displays turn it round, as vanilla's flat items, by
	 * the base model's fixed display); dropped it is a little over half its size in a hand, resting on its underside.
	 */
	private void frameStill(PoseStack poseStack, @Nullable ItemDisplayContext context, @Nullable Set<Identifier> shown) {
		boolean dropped = context == ItemDisplayContext.GROUND;
		float[] bare = gunBounds();
		boolean diagonal = !dropped && bare[5] - bare[2] > LONG_GUN * (bare[4] - bare[1]);
		// The barrel (model -Z) to +X, and the gun's right side (+X) to the viewer (+Z).
		Matrix4f turn = new Matrix4f().rotateZ(diagonal ? Mth.PI / 4 : 0).rotateY(-Mth.HALF_PI);
		float[] box = stillBounds.computeIfAbsent(shown == null ? Set.of() : shown, fitted -> {
			Set<String> names = new HashSet<>();
			fitted.forEach(id -> names.add(id.getPath()));
			return gunBounds(names);
		});
		float[] turned = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
		for (int corner = 0; corner < 8; corner++) {
			Vector3f point = turn.transformPosition(new Vector3f(box[(corner & 1) * 3], box[1 + (corner >> 1 & 1) * 3], box[2 + (corner >> 2) * 3]));
			turned[0] = Math.min(turned[0], point.x);
			turned[1] = Math.min(turned[1], point.y);
			turned[2] = Math.min(turned[2], point.z);
			turned[3] = Math.max(turned[3], point.x);
			turned[4] = Math.max(turned[4], point.y);
			turned[5] = Math.max(turned[5], point.z);
		}
		float fill = context == ItemDisplayContext.GUI ? ICON_FILL : 1;
		float scale = dropped ? thirdPersonScale() * GROUND_SCALE : fill / Math.max(turned[3] - turned[0], turned[4] - turned[1]);
		float centerY = dropped ? turned[1] : (turned[1] + turned[4]) / 2;
		float centerZ = context == ItemDisplayContext.GUI || dropped ? (turned[2] + turned[5]) / 2 : turned[2] + 1 / 32.0F / scale;
		// GeckoLib gives the item's extents as the block's corner, so vanilla lifts a dropped one by half a block: its underside goes there.
		poseStack.translate(0.5F, dropped ? 0 : 0.5F, 0.5F);
		poseStack.scale(scale, scale, scale);
		poseStack.translate(-(turned[0] + turned[3]) / 2, -centerY, -centerZ);
		poseStack.mulPose(turn);
	}

	/** Every bone back to its rest pose, whatever the shared animation state is doing. */
	private static void rest(GeoBone bone, BoneSnapshots snapshots) {
		BoneSnapshot rest = BoneSnapshot.create(bone);
		snapshots.get(bone).setRotation(rest.getRotX(), rest.getRotY(), rest.getRotZ())
				.setTranslation(rest.getTranslateX(), rest.getTranslateY(), rest.getTranslateZ())
				.setScale(rest.getScaleX(), rest.getScaleY(), rest.getScaleZ());
		for (GeoBone child : bone.children()) {
			rest(child, snapshots);
		}
	}

	@Override
	public void adjustModelBonesForRender(RenderPassInfo<GeoRenderState> renderPassInfo, BoneSnapshots snapshots) {
		if (!inHand(renderPassInfo.renderState().getGeckolibData(DataTickets.ITEM_RENDER_PERSPECTIVE))) {
			for (GeoBone bone : getGeoModel().getBakedModel(this.modelId).topLevelBones()) {
				rest(bone, snapshots);
			}
		}
		if (!firstPerson(renderPassInfo.renderState())) {
			snapshots.get("arm_right").ifPresent(snapshot -> snapshot.skipRender(true));
			snapshots.get("arm_left").ifPresent(snapshot -> snapshot.skipRender(true));
			// Root and gun translations frame the gun for the first-person camera, and in a hand they would pull it out of the grip.
			// Their rotations pivot on the grip and stay: the draw flick, the fire kick, the reload tilt and the inspect turns.
			snapshots.get("root").ifPresent(snapshot -> snapshot.setTranslation(0, 0, 0));
			snapshots.get("gun").ifPresent(snapshot -> snapshot.setTranslation(0, 0, 0));
			// Parts set aside below the first-person view (a magazine pulled out, an attachment on its way) end up a block from the gun, out
			// of an arm's reach: in a hand they stay close enough for the other hand to hold them.
			snapshots.get("magazine").ifPresent(GunRenderer::keepInReach);
			for (AttachmentSlot slot : AttachmentSlot.values()) {
				snapshots.get("attach_" + slot.getSerializedName()).ifPresent(GunRenderer::keepInReach);
			}
		}
		// The follower pushes the stack up, so rounds go from the bottom: hide every round bone below the share still loaded.
		Float fill = renderPassInfo.renderState().getGeckolibData(MAG_FILL);
		if (fill != null) {
			int slots = 0;
			while (snapshots.get("mag_round_" + (slots + 1)).isPresent()) {
				slots++;
			}
			for (int round = (int)Math.ceil(fill * slots) + 1; round <= slots; round++) {
				snapshots.get("mag_round_" + round).ifPresent(snapshot -> snapshot.skipRender(true));
			}
		}
		if (Boolean.FALSE.equals(renderPassInfo.renderState().getGeckolibData(CHAMBERED))) {
			snapshots.get("chamber_round").ifPresent(snapshot -> snapshot.skipRender(true));
		}
		if (!Boolean.TRUE.equals(renderPassInfo.renderState().getGeckolibData(RELOADING))) {
			for (String prop : propBones()) {
				snapshots.get(prop).ifPresent(snapshot -> snapshot.skipRender(true));
			}
		}
		// Each attachment is a bone named after its item; only the ones to show are drawn.
		Set<Identifier> shown = renderPassInfo.renderState().getGeckolibData(ATTACHMENTS);
		for (AttachmentItem attachment : AttachmentItem.all()) {
			Identifier id = BuiltInRegistries.ITEM.getKey(attachment);
			snapshots.get(id.getPath()).ifPresent(snapshot -> snapshot.skipRender(shown == null || !shown.contains(id)));
		}
	}
}

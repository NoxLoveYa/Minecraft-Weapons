package com.noxloveya.mcweapons.client.render;

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
import com.noxloveya.mcweapons.gun.GunDefinition;
import com.noxloveya.mcweapons.gun.GunItem;
import com.noxloveya.mcweapons.gun.GunState;
import com.noxloveya.mcweapons.registry.ModComponents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/** One renderer for every gun: geo, texture and animations are found by gun id through GeckoLib's defaulted item paths. */
public class GunRenderer extends GeoItemRenderer<GunItem> {
	public static final DataTicket<Identifier> SKIN = DataTicket.create("mcweapons_skin", Identifier.class);
	public static final DataTicket<Boolean> MUZZLE_FLASH = DataTicket.create("mcweapons_muzzle_flash", Boolean.class);
	/** Share of the mag still loaded, 0 to 1. The {@code mag_round_N} bones (1 at the top) show that many rounds, so a mag check reads the count. */
	public static final DataTicket<Float> MAG_FILL = DataTicket.create("mcweapons_mag_fill", Float.class);
	// Far above any stack hash or GeckoLib stack id, so holder-based ids never collide with them.
	private static final long HOLDER_ID_BASE = 1L << 40;
	// Third person: the model is built around the eye, so it is shrunk and moved until the grip sits in the hand.
	private static final float THIRD_PERSON_SCALE = 0.75F;

	private final Identifier modelId;

	public GunRenderer(GunItem gun) {
		super(new DefaultedItemGeoModel<>(gun.definitionId()));
		this.modelId = gun.definitionId().withPrefix("item/");
		withRenderLayer(new ArmSkinLayer(this, "arm_right"));
		withRenderLayer(new ArmSkinLayer(this, "arm_left"));
		withRenderLayer(new MuzzleFlashLayer(this));
		withRenderLayer(new ShowcasePoseLayer(this));
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

	/** Rest position of a bone's pivot in model space, in blocks. For this model that is view space (eye at the origin, looking down -Z). */
	public Vector3f restPivot(String boneName) {
		BakedGeoModel model = getGeoModel().getBakedModel(this.modelId);
		return model.getBone(boneName).map(bone -> new Vector3f(bone.pivotX(), bone.pivotY(), bone.pivotZ()).div(16)).orElseGet(Vector3f::new);
	}

	/** Rest-pose box around the gun (the {@code gun} bone and everything under it) in model space, in blocks: min xyz, then max xyz. */
	public float[] gunBounds() {
		float[] bounds = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
		getGeoModel().getBakedModel(this.modelId).getBone("gun").ifPresent(bone -> growBounds(bone, bounds));
		return bounds;
	}

	private static void growBounds(GeoBone bone, float[] bounds) {
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
			growBounds(child, bounds);
		}
	}

	@Override
	public long getInstanceId(GunItem gun, RenderData renderData) {
		LivingEntity holder = renderData.itemOwner() == null ? null : renderData.itemOwner().asLivingEntity();
		return holder != null ? instanceId(holder) : super.getInstanceId(gun, renderData);
	}

	@Override
	public void addRenderData(GunItem gun, @Nullable RenderData renderData, GeoRenderState renderState, float partialTick) {
		if (renderData != null && renderData.itemOwner() != null && renderData.itemOwner().asLivingEntity() instanceof AbstractClientPlayer player) {
			renderState.addGeckolibData(SKIN, player.getSkin().body().texturePath());
		}
		renderState.addGeckolibData(MUZZLE_FLASH, renderData != null && renderData.renderPerspective().firstPerson() && Viewmodel.muzzleFlash());
		renderState.addGeckolibData(GunItem.SLIDE_LOCKED, renderData != null && slideLocked(renderData));
		renderState.addGeckolibData(MAG_FILL, renderData != null ? magFill(renderData) : 1);
		renderState.addGeckolibData(GunItem.SHOWCASE, renderData != null && renderData.renderPerspective().firstPerson() && Viewmodel.showcasing());
	}

	private static boolean slideLocked(RenderData renderData) {
		ItemStack stack = renderData.itemStack();
		GunDefinition definition = GunItem.definition(stack);
		if (definition == null) {
			return false;
		}
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null && renderData.itemOwner() != null && renderData.itemOwner().asLivingEntity() == player) {
			return GunInput.slideLocked(player, stack, definition);
		}
		return GunItem.ammo(stack, definition) == 0 && !stack.getOrDefault(ModComponents.GUN_STATE, GunState.EMPTY).reloading();
	}

	/** Rounds in the mag over its size. The chambered round is not in the mag, and a reload brings a full one. */
	private static float magFill(RenderData renderData) {
		ItemStack stack = renderData.itemStack();
		GunDefinition definition = GunItem.definition(stack);
		if (definition == null) {
			return 1;
		}
		LocalPlayer player = Minecraft.getInstance().player;
		boolean reloading = player != null && renderData.itemOwner() != null && renderData.itemOwner().asLivingEntity() == player
				? GunInput.reloading(player, stack) : stack.getOrDefault(ModComponents.GUN_STATE, GunState.EMPTY).reloading();
		if (reloading) {
			return 1;
		}
		int inMag = Math.max(0, GunItem.ammo(stack, definition) - (definition.chamber() ? 1 : 0));
		return Math.min(1, inMag / (float)definition.magSize());
	}

	@Override
	public void adjustRenderPose(RenderPassInfo<GeoRenderState> renderPassInfo) {
		if (firstPerson(renderPassInfo.renderState())) {
			return;
		}
		super.adjustRenderPose(renderPassInfo);
		PoseStack poseStack = renderPassInfo.poseStack();
		Vector3f grip = restPivot("gun");
		poseStack.scale(THIRD_PERSON_SCALE, THIRD_PERSON_SCALE, THIRD_PERSON_SCALE);
		// In the right hand, item -X points forward, +Y to the holder's right and +Z up: barrel (-Z) forward, sights up.
		poseStack.rotateDegrees(Axis.ZP, 90);
		poseStack.rotateDegrees(Axis.XP, 90);
		poseStack.translate(-grip.x, -grip.y, -grip.z);
	}

	@Override
	public void adjustModelBonesForRender(RenderPassInfo<GeoRenderState> renderPassInfo, BoneSnapshots snapshots) {
		if (!firstPerson(renderPassInfo.renderState())) {
			snapshots.get("arm_right").ifPresent(snapshot -> snapshot.skipRender(true));
			snapshots.get("arm_left").ifPresent(snapshot -> snapshot.skipRender(true));
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
	}
}

package com.noxloveya.mcweapons.client.render;

import com.geckolib.cache.model.BakedGeoModel;
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
	/** Set by the viewmodel while the gun is showcased for the tuner, where it floats without hands. */
	public static final DataTicket<Boolean> HIDE_ARMS = DataTicket.create("mcweapons_hide_arms", Boolean.class);
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
		if (!firstPerson(renderPassInfo.renderState()) || Boolean.TRUE.equals(renderPassInfo.renderState().getGeckolibData(HIDE_ARMS))) {
			snapshots.get("arm_right").ifPresent(snapshot -> snapshot.skipRender(true));
			snapshots.get("arm_left").ifPresent(snapshot -> snapshot.skipRender(true));
		}
	}
}

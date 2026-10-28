package com.noxloveya.mcweapons.client.render;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.noxloveya.mcweapons.McWeapons;
import com.noxloveya.mcweapons.client.input.GunInput;
import com.noxloveya.mcweapons.client.mixin.CameraAccessor;
import com.noxloveya.mcweapons.client.mixin.GameRendererAccessor;
import com.noxloveya.mcweapons.gun.AttachmentItem;
import com.noxloveya.mcweapons.gun.AttachmentSlot;
import com.noxloveya.mcweapons.gun.Attachments;
import com.noxloveya.mcweapons.gun.Reticle;
import com.noxloveya.mcweapons.registry.ModComponents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Scopes looked through like real ones. While a scope is aimed, the level is drawn a second time each frame, from the eye along the
 * scope's axis as drawn and at its magnification, into a texture. A lens pass then refracts that picture (a little barrel distortion,
 * colour fringes towards the rim, the exit pupil's shadow when the eye is off the axis or at the wrong distance, a darker rim) and etches
 * the scope's reticle in, with the gun's {@link Reticle} lit in the middle; the scope's eyepiece shows the result ({@link ScopeLensLayer}).
 * The view around the scope is not zoomed.
 */
public final class ScopeRenderer {
	/** The picture through the scope is this many pixels square. */
	private static final int SIZE = 512;
	/** The finished lens picture, as a texture for the eyepiece. */
	public static final Identifier LENS_TEXTURE = McWeapons.id("dynamic/scope_lens");
	/** How quickly the exit pupil closes as the eye moves off the eye relief, per block of the hand's view space. */
	private static final float PUPIL_FALLOFF = 1.2F;
	/** The lit reticle's half-size at size 1, as a share of the lens's radius. */
	private static final float LIT_SIZE = 0.3F;
	private static final RenderPipeline LENS_PIPELINE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
			.withLocation(McWeapons.id("pipeline/scope_lens"))
			.withVertexShader("core/screenquad")
			.withFragmentShader(McWeapons.id("core/scope_lens"))
			.withBindGroupLayout(BindGroupLayout.builder()
					.withUniform("ScopeLens", UniformType.UNIFORM_BUFFER)
					.withUniform("InSampler", UniformType.COMBINED_IMAGE_SAMPLER)
					.withUniform("ReticleSampler", UniformType.COMBINED_IMAGE_SAMPLER)
					.withUniform("LitSampler", UniformType.COMBINED_IMAGE_SAMPLER)
					.build())
			.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
			.withColorTargetState(ColorTargetState.DEFAULT)
			.build());
	private static final int LENS_INFO_SIZE = new Std140SizeCalculator().putVec4().putVec2().putFloat().putFloat().get();

	/** The level through the scope, then the lens's picture of it. */
	private static @Nullable RenderTarget world;
	private static @Nullable RenderTarget lens;
	/** Set while the level renders into the scope's target (see GameRendererMixin). */
	private static @Nullable RenderTarget renderingInto;
	private static @Nullable ProjectionMatrixBuffer projection;
	private static @Nullable MappableRingBuffer lensInfo;
	private static @Nullable ScopeCamera camera;
	private static long renderedNanos;
	private static boolean sodiumLooked;
	private static @Nullable Method sodiumGetter;

	private ScopeRenderer() {
	}

	public static void register() {
		// The scope's camera reads the sky and fog from where it is, which its probe works out each tick like the main camera's.
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			if (camera != null && minecraft.level != null && camera.entity() != null) {
				camera.tick();
			}
		});
	}

	/** The target the level is being drawn into for the scope, or null for the screen's own. */
	public static @Nullable RenderTarget renderingInto() {
		return renderingInto;
	}

	/** The eyepiece has a picture from the last few frames to show. */
	public static boolean ready() {
		return System.nanoTime() - renderedNanos < 200_000_000L;
	}

	/** Draws the view through the held gun's scope, if it has one and is aimed. Called once the frame's level and hand are drawn. */
	public static void render(GameRenderer gameRenderer) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		Viewmodel.Lens drawn = Viewmodel.lens();
		if (player == null || minecraft.level == null || drawn == null || !minecraft.options.getCameraType().isFirstPerson() || Viewmodel.ads(1) <= 0) {
			return;
		}
		ItemStack stack = player.getMainHandItem();
		AttachmentItem optic = Attachments.in(stack, AttachmentSlot.OPTIC);
		if (optic == null || !optic.isScope()) {
			return;
		}
		setUp();
		Camera main = gameRenderer.mainCamera();
		DeltaTracker deltaTracker = minecraft.getDeltaTracker();
		float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);

		Matrix4f pose = drawn.pose();
		Vector3f center = pose.transformPosition(new Vector3f());
		Vector3f axis = pose.transformDirection(new Vector3f(0, 0, -1)).normalize();
		// The scope's true field: what its lens covers seen from where the eye is set to sit, over its magnification against the
		// unzoomed view, so it magnifies the same however far the eye is set.
		float eyeDistance = Attachments.eyeDistance(stack, optic);
		double unzoomedTan = Math.tan(Math.toRadians(main.getFov() / Viewmodel.fovMultiplier(partialTick) / 2));
		double tan = drawn.radius() / eyeDistance / Math.tan(Math.toRadians(Camera.BASE_HUD_FOV / 2)) * unzoomedTan / GunInput.zoom(optic);
		float fov = (float)Math.toDegrees(2 * Math.atan(tan));
		Vec3 look = Viewmodel.worldDirection(main, axis);
		float yRot = (float)Math.toDegrees(Math.atan2(-look.x, look.z));
		float xRot = (float)Math.toDegrees(-Math.asin(Mth.clamp(look.y, -1, 1)));
		camera.aim(main, minecraft.level, player, xRot, yRot, fov);

		// The level as that camera sees it: extracted for it (the frame's own state is used up by now), then drawn into the scope's target.
		LevelRenderState state = gameRenderer.gameRenderState().levelRenderState;
		camera.extractRenderState(state.cameraRenderState, deltaTracker);
		minecraft.levelExtractor.extract(deltaTracker, camera, partialTick);
		state.shouldShowEntityOutlines = false;
		RenderSystem.setProjectionMatrix(projection.getBuffer(state.cameraRenderState.projectionMatrix), ProjectionType.PERSPECTIVE);
		GameRendererAccessor access = (GameRendererAccessor)gameRenderer;
		Matrix4f sodium = sodiumProjection(gameRenderer);
		Matrix4f mainProjection = sodium == null ? null : new Matrix4f(sodium);
		if (sodium != null) {
			sodium.set(state.cameraRenderState.projectionMatrix);
		}
		renderingInto = world;
		try {
			minecraft.levelRenderer.render(access.mcweapons$resourcePool(), false, state.cameraRenderState,
					access.mcweapons$fogRenderer().getBuffer(FogRenderer.FogMode.WORLD), state.cameraRenderState.fogData.color, true, false);
		} finally {
			renderingInto = null;
			if (sodium != null) {
				sodium.set(mainProjection);
			}
		}

		// Where the eye is against the eyepiece: off its axis, in lens radii, and how far behind it.
		Vector3f eye = new Vector3f(center).negate();
		float offsetX = eye.dot(pose.transformDirection(new Vector3f(1, 0, 0)).normalize()) / drawn.radius();
		float offsetY = eye.dot(pose.transformDirection(new Vector3f(0, 1, 0)).normalize()) / drawn.radius();
		float pupil = Mth.clamp(1 - Math.abs(center.dot(axis) - eyeDistance) * PUPIL_FALLOFF, 0.12F, 1);
		lensPass(optic, stack.getOrDefault(ModComponents.RETICLE, Reticle.DEFAULT), offsetX, offsetY, pupil);
		renderedNanos = System.nanoTime();
	}

	/**
	 * Sodium draws terrain with the projection it caught as the frame's own level was drawn (GameRenderer.renderLevel), not the one set
	 * for this pass, and hands out that very matrix: it is swapped for the scope's while the scope's view is drawn. Null without Sodium.
	 */
	private static @Nullable Matrix4f sodiumProjection(GameRenderer gameRenderer) {
		if (!sodiumLooked) {
			sodiumLooked = true;
			try {
				sodiumGetter = GameRenderer.class.getMethod("sodium$getProjectionMatrix");
			} catch (NoSuchMethodException e) {
				sodiumGetter = null;
			}
		}
		try {
			return sodiumGetter != null && sodiumGetter.invoke(gameRenderer) instanceof Matrix4f matrix ? matrix : null;
		} catch (ReflectiveOperationException e) {
			return null;
		}
	}

	private static void setUp() {
		if (world != null) {
			return;
		}
		world = new TextureTarget("mcweapons scope", SIZE, SIZE, GpuFormat.RGBA8_UNORM, GpuFormat.D32_FLOAT);
		lens = new TextureTarget("mcweapons scope lens", SIZE, SIZE, GpuFormat.RGBA8_UNORM, null);
		projection = new ProjectionMatrixBuffer("mcweapons scope");
		lensInfo = new MappableRingBuffer(() -> "mcweapons scope lens", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, LENS_INFO_SIZE);
		camera = new ScopeCamera();
		Minecraft.getInstance().getTextureManager().register(LENS_TEXTURE, new LensTexture(lens));
	}

	/** Refracts the level's picture into the lens's, with the reticle etched in (shaders/core/scope_lens.fsh). */
	private static void lensPass(AttachmentItem optic, Reticle reticle, float offsetX, float offsetY, float pupil) {
		TextureManager textures = Minecraft.getInstance().getTextureManager();
		AbstractTexture etched = textures.getTexture(BuiltInRegistries.ITEM.getKey(optic).withPath(path -> "textures/gui/scope/" + path + ".png"));
		AbstractTexture lit = textures.getTexture(McWeapons.id("textures/effect/reticle_" + reticle.shape().getSerializedName() + ".png"));
		try (GpuBufferSlice.MappedView view = lensInfo.currentBuffer().map(false, true)) {
			Std140Builder.intoBuffer(view.data())
					.putVec4((reticle.color() >> 16 & 0xFF) / 255.0F, (reticle.color() >> 8 & 0xFF) / 255.0F, (reticle.color() & 0xFF) / 255.0F, LIT_SIZE * reticle.size())
					.putVec2(offsetX, offsetY)
					.putFloat(pupil)
					.putFloat(0);
		}
		GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
		try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
				.createRenderPass(() -> "mcweapons scope lens", lens.getColorTextureView(), Optional.empty(), null, OptionalDouble.empty())) {
			pass.setPipeline(RenderSystem.getCompiledPipeline(LENS_PIPELINE));
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("ScopeLens", lensInfo.currentBuffer());
			pass.setUniform("InSampler", world.getColorTextureView(), linear);
			pass.setUniform("ReticleSampler", etched.getTextureView(), linear);
			pass.setUniform("LitSampler", lit.getTextureView(), linear);
			pass.draw(3, 1, 0, 0);
		}
		lensInfo.rotate();
	}

	/** A camera set up by hand: at the eye, looking along the scope, at its field of view, square. */
	private static final class ScopeCamera extends Camera {
		void aim(Camera main, ClientLevel level, Entity entity, float xRot, float yRot, float fov) {
			setLevel(level);
			if (entity() != entity) {
				setEntity(entity);
			}
			setPosition(main.position());
			setRotation(yRot, xRot);
			CameraAccessor access = (CameraAccessor)(Object)this;
			float depthFar = ((CameraAccessor)main).mcweapons$getDepthFar();
			access.mcweapons$setDepthFar(depthFar);
			access.mcweapons$setFov(fov);
			access.mcweapons$setupPerspective(PROJECTION_Z_NEAR, depthFar, fov, SIZE, SIZE);
			// The scope looks within the view, so what the view culls in is enough, and the view's own culling stays as it was.
			access.mcweapons$setCullFrustum(main.getCullFrustum());
			access.mcweapons$setInitialized(true);
		}
	}

	/** The lens target's colour as a texture the eyepiece's render type can bind; the target owns it. */
	private static final class LensTexture extends AbstractTexture {
		LensTexture(RenderTarget target) {
			this.texture = target.getColorTexture();
			this.textureView = target.getColorTextureView();
			this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
		}

		@Override
		public void close() {
		}
	}
}

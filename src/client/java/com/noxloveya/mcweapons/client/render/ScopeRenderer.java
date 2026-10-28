package com.noxloveya.mcweapons.client.render;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.noxloveya.mcweapons.McWeapons;
import com.noxloveya.mcweapons.gun.AttachmentItem;
import com.noxloveya.mcweapons.gun.AttachmentSlot;
import com.noxloveya.mcweapons.gun.Attachments;
import com.noxloveya.mcweapons.gun.Reticle;
import com.noxloveya.mcweapons.registry.ModComponents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Scopes looked through, drawn once. While a scope is aimed the camera zooms by its magnification (Viewmodel.fovMultiplier), so the level
 * is drawn a single time. Before the hand is drawn, a lens pass (shaders/core/scope_lens.fsh) goes over that picture: inside the lens it
 * refracts it (barrel distortion, colour fringes, the exit pupil's shadow) and etches the reticle in, with the gun's {@link Reticle} lit in
 * the middle; outside, where the eye is not focused, it blurs and dims the zoomed level. The scope is then drawn sharp over it, and its
 * eyepiece shows the lens part of the result ({@link ScopeLensLayer}).
 */
public final class ScopeRenderer {
	/** The screen after the lens pass, as a texture for the eyepiece. */
	public static final Identifier LENS_TEXTURE = McWeapons.id("dynamic/scope_lens");
	/** How quickly the exit pupil closes as the eye moves off its set distance, per block of the hand's view space. */
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
	private static final @Nullable MethodHandle SHADER_PACK_IN_USE = irisShaderPackInUse();
	private static final int LENS_INFO_SIZE = new Std140SizeCalculator().putVec4().putVec2().putVec2().putFloat().putFloat().putFloat().putFloat().get();

	/** The level before the hand, then the screen after the lens pass. */
	private static @Nullable RenderTarget level;
	private static @Nullable RenderTarget lens;
	private static @Nullable MappableRingBuffer lensInfo;
	private static long renderedNanos;

	private ScopeRenderer() {
	}

	/** The lens pass ran this frame or the last: the eyepiece has its picture to show. */
	public static boolean ready() {
		return System.nanoTime() - renderedNanos < 100_000_000L;
	}

	/** Where a point in the hand's view space lands on the screen, 0 to 1 from the bottom left (as the lens texture is laid out). */
	public static Vector2f screenUv(Vector3fc viewPos) {
		Minecraft minecraft = Minecraft.getInstance();
		float aspect = (float)minecraft.getWindow().getWidth() / Math.max(1, minecraft.getWindow().getHeight());
		float tanHalf = (float)Math.tan(Math.toRadians(Camera.BASE_HUD_FOV / 2));
		float depth = Math.max(1.0E-4F, -viewPos.z());
		return new Vector2f(0.5F + 0.5F * viewPos.x() / depth / (aspect * tanHalf), 0.5F + 0.5F * viewPos.y() / depth / tanHalf);
	}

	/** How far the held gun's scope is up at the eye this frame, 0 to 1: 0 without one, or out of first person. */
	private static float strength() {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || !minecraft.options.getCameraType().isFirstPerson()) {
			return 0;
		}
		AttachmentItem optic = Attachments.in(player.getMainHandItem(), AttachmentSlot.OPTIC);
		float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		return optic != null && optic.isScope() ? Viewmodel.ads(partialTick) * Viewmodel.scopeView(partialTick) : 0;
	}

	/**
	 * A shader pack draws the hand inside its own level pipeline, so it would be in the picture the lens pass refracts and blurs: while a
	 * scope is up the gun is left out there and drawn after the lens pass instead, the vanilla way (GameRendererMixin).
	 */
	public static boolean gunAfterLens() {
		return strength() > 0.01F && shaderPackInUse();
	}

	private static boolean shaderPackInUse() {
		try {
			return SHADER_PACK_IN_USE != null && (boolean)SHADER_PACK_IN_USE.invokeExact();
		} catch (Throwable e) {
			return false;
		}
	}

	/** Iris's IrisApi.isShaderPackInUse, bound and ready, or null without Iris. Looked up by name: Iris is not a dependency. */
	private static @Nullable MethodHandle irisShaderPackInUse() {
		try {
			Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
			return MethodHandles.publicLookup().findVirtual(api, "isShaderPackInUse", MethodType.methodType(boolean.class))
					.bindTo(api.getMethod("getInstance").invoke(null));
		} catch (ReflectiveOperationException | LinkageError e) {
			return null;
		}
	}

	/** Runs the lens pass over the level if the held gun's scope is aimed. Called once the level is drawn, before the hand. */
	public static void processLevel(GameRenderer gameRenderer) {
		Minecraft minecraft = Minecraft.getInstance();
		Viewmodel.Lens drawn = Viewmodel.lens();
		float strength = strength();
		if (drawn == null || strength <= 0.01F) {
			return;
		}
		ItemStack stack = minecraft.player.getMainHandItem();
		AttachmentItem optic = Attachments.in(stack, AttachmentSlot.OPTIC);
		// The eyepiece as drawn last frame (this frame's hand is not drawn yet).
		Matrix4f pose = drawn.pose();
		Vector3f center = pose.transformPosition(new Vector3f());
		if (center.z > -0.01F) {
			return;
		}
		Vector3f axis = pose.transformDirection(new Vector3f(0, 0, -1)).normalize();
		RenderTarget main = gameRenderer.mainRenderTarget();
		setUp(main.width, main.height);
		Vector2f onScreen = screenUv(center);
		float radius = 0.5F * drawn.radius() / -center.z / (float)Math.tan(Math.toRadians(Camera.BASE_HUD_FOV / 2));
		// Where the eye is against the eyepiece: off its axis, in lens radii, and how far behind it.
		Vector3f eye = new Vector3f(center).negate();
		float offsetX = eye.dot(pose.transformDirection(new Vector3f(1, 0, 0)).normalize()) / drawn.radius();
		float offsetY = eye.dot(pose.transformDirection(new Vector3f(0, 1, 0)).normalize()) / drawn.radius();
		float pupil = Mth.clamp(1 - Math.abs(center.dot(axis) - Attachments.eyeDistance(stack, optic)) * PUPIL_FALLOFF, 0.12F, 1);
		Reticle reticle = stack.getOrDefault(ModComponents.RETICLE, Reticle.DEFAULT);

		TextureManager textures = minecraft.getTextureManager();
		AbstractTexture etched = textures.getTexture(BuiltInRegistries.ITEM.getKey(optic).withPath(path -> "textures/gui/scope/" + path + ".png"));
		AbstractTexture lit = textures.getTexture(McWeapons.id("textures/effect/reticle_" + reticle.shape().getSerializedName() + ".png"));
		try (GpuBufferSlice.MappedView view = lensInfo.currentBuffer().map(false, true)) {
			Std140Builder.intoBuffer(view.data())
					.putVec4((reticle.color() >> 16 & 0xFF) / 255.0F, (reticle.color() >> 8 & 0xFF) / 255.0F, (reticle.color() & 0xFF) / 255.0F, LIT_SIZE * reticle.size())
					.putVec2(onScreen.x, onScreen.y)
					.putVec2(offsetX, offsetY)
					.putFloat(radius)
					.putFloat((float)main.width / main.height)
					.putFloat(pupil)
					.putFloat(strength);
		}
		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		// The pass reads the level from a copy, as it writes the screen, and the eyepiece then shows a copy of what it wrote.
		encoder.copyTextureToTexture(main.getColorTexture(), level.getColorTexture(), 0, 0, 0, 0, 0, main.width, main.height);
		GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
		try (RenderPass pass = encoder.createRenderPass(() -> "mcweapons scope lens", main.getColorTextureView(), Optional.empty(), null, OptionalDouble.empty())) {
			pass.setPipeline(RenderSystem.getCompiledPipeline(LENS_PIPELINE));
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("ScopeLens", lensInfo.currentBuffer());
			pass.setUniform("InSampler", level.getColorTextureView(), linear);
			pass.setUniform("ReticleSampler", etched.getTextureView(), linear);
			pass.setUniform("LitSampler", lit.getTextureView(), linear);
			pass.draw(3, 1, 0, 0);
		}
		lensInfo.rotate();
		encoder.copyTextureToTexture(main.getColorTexture(), lens.getColorTexture(), 0, 0, 0, 0, 0, main.width, main.height);
		renderedNanos = System.nanoTime();
	}

	/** Copies the size of the screen, made or remade as it changes. */
	private static void setUp(int width, int height) {
		if (lensInfo == null) {
			lensInfo = new MappableRingBuffer(() -> "mcweapons scope lens", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, LENS_INFO_SIZE);
		}
		if (level != null && level.width == width && level.height == height) {
			return;
		}
		if (level == null) {
			level = new TextureTarget("mcweapons scope level", width, height, GpuFormat.RGBA8_UNORM, null);
			lens = new TextureTarget("mcweapons scope lens", width, height, GpuFormat.RGBA8_UNORM, null);
		} else {
			level.resize(width, height);
			lens.resize(width, height);
		}
		Minecraft.getInstance().getTextureManager().register(LENS_TEXTURE, new LensTexture(lens));
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

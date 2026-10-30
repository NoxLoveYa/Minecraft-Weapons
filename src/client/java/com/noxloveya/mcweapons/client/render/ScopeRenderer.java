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
import com.mojang.renderpearl.api.textures.GpuTextureView;
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
 * eyepiece shows the lens part of the result ({@link ScopeLensLayer}). A 1x sight's window gets the same glass once the hand is drawn
 * ({@link #processSight}).
 */
public final class ScopeRenderer {
	/** The screen after the lens pass, as a texture for the eyepiece. */
	public static final Identifier LENS_TEXTURE = McWeapons.id("dynamic/scope_lens");
	/** How quickly the exit pupil closes as the eye moves off its set distance, per block of the hand's view space. */
	private static final float PUPIL_FALLOFF = 1.2F;
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
	private static final RenderPipeline SIGHT_PIPELINE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
			.withLocation(McWeapons.id("pipeline/sight_glass"))
			.withVertexShader("core/screenquad")
			.withFragmentShader(McWeapons.id("core/sight_glass"))
			.withBindGroupLayout(BindGroupLayout.builder()
					.withUniform("SightGlass", UniformType.UNIFORM_BUFFER)
					.withUniform("InSampler", UniformType.COMBINED_IMAGE_SAMPLER)
					.withUniform("DepthSampler", UniformType.COMBINED_IMAGE_SAMPLER)
					.build())
			.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
			.withColorTargetState(ColorTargetState.DEFAULT)
			.build());
	private static final @Nullable MethodHandle SHADER_PACK_IN_USE = irisShaderPackInUse();
	private static final int LENS_INFO_SIZE = new Std140SizeCalculator().putVec4().putVec2().putVec2().putFloat().putFloat().putFloat().putFloat().get();
	/** Corners a 1x sight's window can have (sight_glass.fsh takes them two to a vec4). */
	private static final int MAX_CORNERS = 8;
	private static final int SIGHT_INFO_SIZE = new Std140SizeCalculator().putVec4().putVec4().putVec4().putVec4().putVec4().putVec2().putVec2()
			.putFloat().putFloat().putFloat().putFloat().putFloat().putFloat().putFloat().get();

	/** The level before the hand, then the screen after the lens pass; for a 1x sight, the screen before its glass pass. */
	private static @Nullable RenderTarget level;
	private static @Nullable RenderTarget lens;
	private static @Nullable MappableRingBuffer lensInfo;
	private static @Nullable MappableRingBuffer sightInfo;
	private static long renderedNanos;
	/** The screen was kept in {@link #level} before a gun drawn for its depth alone, to be put back (see {@link #sightDepthOnly}). */
	private static boolean colourSaved;

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

	/**
	 * With a shader pack and a 1x sight fitted, the gun the pack drew (lit by it) is not in the hand's depth, which the glass pass needs to
	 * keep off a rear sight in front of the window: the vanilla hand pass draws it again for its depth alone, its colour taken back off
	 * ({@link #saveBeforeHand}, then {@link #processSight}).
	 */
	public static boolean sightDepthOnly() {
		return sightStrength() > 0 && !gunAfterLens() && shaderPackInUse();
	}

	/** Keeps the screen as the shader pack left it, before the vanilla hand pass draws the gun for its depth (see {@link #sightDepthOnly}). */
	public static void saveBeforeHand(GameRenderer gameRenderer) {
		RenderTarget main = gameRenderer.mainRenderTarget();
		setUp(main.width, main.height);
		RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(main.getColorTexture(), level.getColorTexture(), 0, 0, 0, 0, 0, main.width, main.height);
		colourSaved = true;
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
		float pupil = Mth.clamp(1 - Math.abs(center.dot(axis) - Attachments.eyeBlocks(stack, optic)) * PUPIL_FALLOFF, 0.12F, 1);
		Reticle reticle = Attachments.reticle(stack);

		TextureManager textures = minecraft.getTextureManager();
		AbstractTexture etched = textures.getTexture(BuiltInRegistries.ITEM.getKey(optic).withPath(path -> "textures/gui/scope/" + path + ".png"));
		AbstractTexture lit = textures.getTexture(McWeapons.id("textures/effect/reticle_" + reticle.shape().getSerializedName() + ".png"));
		try (GpuBufferSlice.MappedView view = lensInfo.currentBuffer().map(false, true)) {
			Std140Builder.intoBuffer(view.data())
					.putVec4((reticle.color() >> 16 & 0xFF) / 255.0F, (reticle.color() >> 8 & 0xFF) / 255.0F, (reticle.color() & 0xFF) / 255.0F,
							ReticleLayer.SCREEN_RADIUS * reticle.size() / radius)
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

	/** The glass of the held gun's 1x sight: 1 with one fitted in first person, aimed or not (it is glass either way), 0 otherwise. */
	private static float sightStrength() {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || !minecraft.options.getCameraType().isFirstPerson()) {
			return 0;
		}
		AttachmentItem optic = Attachments.in(player.getMainHandItem(), AttachmentSlot.OPTIC);
		return optic != null && !optic.isScope() ? 1 : 0;
	}

	/**
	 * Runs the glass pass (shaders/core/sight_glass.fsh) over the held gun's 1x sight's window, inside its outline, aimed or not: the
	 * lens pass's tint, fringes, shading and glint over the level and the parts of the gun seen through it, the middle left at 1x, nothing
	 * outside the window touched, nor any part of the gun nearer the eye than the window (a rear sight in front of it). Called once the
	 * hand is drawn, with the hand's depth and projection (with a shader pack, once its level pipeline has drawn it).
	 */
	public static void processSight(GameRenderer gameRenderer, GpuTextureView handDepth, Matrix4f handProjection) {
		if (colourSaved) {
			// The gun drawn for its depth alone comes back off: the shader pack's own is under it.
			RenderTarget main = gameRenderer.mainRenderTarget();
			RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(level.getColorTexture(), main.getColorTexture(), 0, 0, 0, 0, 0, main.width, main.height);
			colourSaved = false;
		}
		Viewmodel.Sight sight = Viewmodel.takeSight();
		float strength = sightStrength();
		if (sight == null || strength <= 0.01F || sight.outline().size() < 3 || sight.outline().size() > MAX_CORNERS) {
			return;
		}
		Vector3f center = sight.pose().transformPosition(new Vector3f());
		if (center.z > -0.01F) {
			return;
		}
		float[] corners = new float[MAX_CORNERS * 2];
		float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
		RenderTarget main = gameRenderer.mainRenderTarget();
		float aspect = (float)main.width / main.height;
		// The glass's plane, through the window's corners and square to the sight; the shader finds its depth under each pixel.
		Vector3f normal = sight.pose().transformDirection(new Vector3f(0, 0, 1)).normalize();
		float planeD = normal.dot(sight.pose().transformPosition(new Vector3f(sight.outline().getFirst())));
		for (int i = 0; i < sight.outline().size(); i++) {
			Vector3f corner = sight.pose().transformPosition(new Vector3f(sight.outline().get(i)));
			if (corner.z > -0.01F) {
				return;
			}
			// In screen heights from the bottom left, so the shader's distances are the same across and up.
			Vector2f onScreen = screenUv(corner);
			corners[i * 2] = onScreen.x * aspect;
			corners[i * 2 + 1] = onScreen.y;
			minX = Math.min(minX, corners[i * 2]);
			maxX = Math.max(maxX, corners[i * 2]);
			minY = Math.min(minY, corners[i * 2 + 1]);
			maxY = Math.max(maxY, corners[i * 2 + 1]);
		}
		// Twice the outline's signed area on the screen, and its centroid.
		int count = sight.outline().size();
		float area2 = 0, centroidX = 0, centroidY = 0;
		for (int i = 0; i < count; i++) {
			int j = (i + 1) % count;
			float cross = corners[i * 2] * corners[j * 2 + 1] - corners[j * 2] * corners[i * 2 + 1];
			area2 += cross;
			centroidX += (corners[i * 2] + corners[j * 2]) * cross;
			centroidY += (corners[i * 2 + 1] + corners[j * 2 + 1]) * cross;
		}
		if (Math.abs(area2) * 0.5F * main.height * main.height < 4) {
			return; // seen edge on
		}
		// The shader tells each side's inside from the middle, so the middle must be in the window. Aimed, the reticle is; seen from the
		// side (the gun turned over in an inspect) the reticle, deeper than the window, lands outside its outline: its centroid then.
		Vector2f reticle = screenUv(center);
		float middleX = reticle.x * aspect, middleY = reticle.y;
		for (int i = 0; i < count; i++) {
			int j = (i + 1) % count;
			float side = (corners[j * 2] - corners[i * 2]) * (middleY - corners[i * 2 + 1]) - (corners[j * 2 + 1] - corners[i * 2 + 1]) * (middleX - corners[i * 2]);
			if (side * area2 <= 0) {
				middleX = centroidX / (3 * area2);
				middleY = centroidY / (3 * area2);
				break;
			}
		}
		setUp(main.width, main.height);
		try (GpuBufferSlice.MappedView view = sightInfo.currentBuffer().map(false, true)) {
			Std140Builder builder = Std140Builder.intoBuffer(view.data());
			for (int i = 0; i < MAX_CORNERS * 2; i += 4) {
				builder.putVec4(corners[i], corners[i + 1], corners[i + 2], corners[i + 3]);
			}
			builder.putVec4(normal.x, normal.y, normal.z, planeD)
					.putVec2(middleX, middleY)
					.putVec2((maxX - minX) / 2, (maxY - minY) / 2)
					.putFloat(sight.outline().size())
					.putFloat(strength)
					.putFloat(aspect)
					.putFloat((float)Math.tan(Math.toRadians(Camera.BASE_HUD_FOV / 2)))
					.putFloat(handProjection.m22())
					.putFloat(handProjection.m32())
					.putFloat(RenderSystem.getDevice().getDeviceInfo().isZZeroToOne() ? 1 : 0);
		}
		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		// The pass reads the screen from a copy, as it writes it; outside the window it writes nothing.
		encoder.copyTextureToTexture(main.getColorTexture(), level.getColorTexture(), 0, 0, 0, 0, 0, main.width, main.height);
		try (RenderPass pass = encoder.createRenderPass(() -> "mcweapons sight glass", main.getColorTextureView(), Optional.empty(), null, OptionalDouble.empty())) {
			pass.setPipeline(RenderSystem.getCompiledPipeline(SIGHT_PIPELINE));
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("SightGlass", sightInfo.currentBuffer());
			pass.setUniform("InSampler", level.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
			pass.setUniform("DepthSampler", handDepth, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
			pass.draw(3, 1, 0, 0);
		}
		sightInfo.rotate();
	}

	/** Copies the size of the screen, made or remade as it changes. */
	private static void setUp(int width, int height) {
		if (lensInfo == null) {
			lensInfo = new MappableRingBuffer(() -> "mcweapons scope lens", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, LENS_INFO_SIZE);
			sightInfo = new MappableRingBuffer(() -> "mcweapons sight glass", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, SIGHT_INFO_SIZE);
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

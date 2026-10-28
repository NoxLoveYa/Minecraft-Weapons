package com.noxloveya.mcweapons.client.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.culling.Frustum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

// A scope's camera (ScopeRenderer) is set up by hand, not from its entity: Camera keeps its projection, frustum and range private.
@Mixin(Camera.class)
public interface CameraAccessor {
	@Invoker("setupPerspective")
	void mcweapons$setupPerspective(float zNear, float zFar, float fov, float width, float height);

	@Accessor("cullFrustum")
	void mcweapons$setCullFrustum(Frustum frustum);

	@Accessor("depthFar")
	float mcweapons$getDepthFar();

	@Accessor("depthFar")
	void mcweapons$setDepthFar(float depthFar);

	@Accessor("fov")
	void mcweapons$setFov(float fov);

	@Accessor("initialized")
	void mcweapons$setInitialized(boolean initialized);
}

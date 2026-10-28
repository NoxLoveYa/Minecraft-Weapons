package com.noxloveya.mcweapons.client.mixin;

import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// The scope's second level pass (ScopeRenderer) needs the frame's fog and resource pool, which GameRenderer keeps private.
@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
	@Accessor("fogRenderer")
	FogRenderer mcweapons$fogRenderer();

	@Accessor("resourcePool")
	CrossFrameResourcePool mcweapons$resourcePool();
}

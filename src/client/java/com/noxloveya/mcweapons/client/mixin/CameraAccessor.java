package com.noxloveya.mcweapons.client.mixin;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Camera.class)
public interface CameraAccessor {
	/** The FOV the hand is drawn with this frame: vanilla's 70, narrowed when dying or under water, and by a zoom mod that zooms the hand. */
	@Accessor("hudFov")
	float mcweapons$hudFov();
}

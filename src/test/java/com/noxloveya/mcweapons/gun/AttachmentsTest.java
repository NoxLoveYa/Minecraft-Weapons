package com.noxloveya.mcweapons.gun;

import com.noxloveya.mcweapons.registry.ModComponents;
import com.noxloveya.mcweapons.registry.ModItems;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AttachmentsTest {
	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		// An item's own components are bound when the game loads its data, which a test does not: none are needed here.
		ModItems.M4A1.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
	}

	private static ItemStack gun() {
		return new ItemStack(ModItems.M4A1);
	}

	@Test
	void eyeShareStaysOnTheSliderAndScalesTheOpticsOwnDistance() {
		ItemStack gun = gun();
		assertEquals(1, Attachments.eyeShare(gun, ModItems.COYOTE_SIGHT), "a fresh gun is at 100%");
		assertEquals(0.405F, Attachments.eyeBlocks(gun, ModItems.COYOTE_SIGHT), 1.0E-6);

		Attachments.setEyeShare(gun, ModItems.COYOTE_SIGHT, 1.5F);
		assertEquals(0.6075F, Attachments.eyeBlocks(gun, ModItems.COYOTE_SIGHT), 1.0E-6);
		assertEquals(0.104F, Attachments.eyeBlocks(gun, ModItems.ACOG), 1.0E-6, "each optic keeps its own share");

		Attachments.setEyeShare(gun, ModItems.ACOG, 10);
		assertEquals(Attachments.MAX_EYE_SHARE, Attachments.eyeShare(gun, ModItems.ACOG));
		Attachments.setEyeShare(gun, ModItems.ACOG, 0.1F);
		assertEquals(Attachments.MIN_EYE_SHARE, Attachments.eyeShare(gun, ModItems.ACOG));
		assertEquals(0.052F, Attachments.eyeBlocks(gun, ModItems.ACOG), 1.0E-6);
		Attachments.setEyeShare(gun, ModItems.ACOG, Float.NaN);
		assertEquals(1, Attachments.eyeShare(gun, ModItems.ACOG));
		assertEquals(1.5F, Attachments.eyeShare(gun, ModItems.COYOTE_SIGHT));
	}

	@Test
	void eyeDistancesSavedBeforeAreNotRead() {
		ItemStack gun = gun();
		gun.set(ModComponents.EYE_DISTANCE, Map.of(BuiltInRegistries.ITEM.getKey(ModItems.RED_DOT), 1.5F));
		assertEquals(0.2175F, Attachments.eyeBlocks(gun, ModItems.RED_DOT), 1.0E-6);

		Attachments.setEyeShare(gun, ModItems.RED_DOT, 1);
		assertFalse(gun.has(ModComponents.EYE_DISTANCE), "dropped once a share is set");
	}
}

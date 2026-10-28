package com.noxloveya.mcweapons.gun;

import com.noxloveya.mcweapons.McWeapons;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class AttachmentItemTest {
	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	private static AttachmentItem attachment(String name, AttachmentSlot slot) {
		return new AttachmentItem(slot, new Item.Properties().setId(ResourceKey.create(Registries.ITEM, McWeapons.id(name))));
	}

	@Test
	void effectsScaleOnlyWhatTheyName() {
		GunDefinition glock = new GunDefinition(List.of("semi"), 600, 5, 64, 17, true, 0.4F, 0.05F, 0.8F, 0.3F, 10, 40, 50, 3, 0.8F, 1.0F);

		GunDefinition silenced = attachment("test_silencer", AttachmentSlot.MUZZLE).velocity(0.9F).recoil(0.5F).applyTo(glock);
		assertEquals(BulletStats.DEFAULT.speed() * 0.9F, silenced.bullet().speed(), 1.0E-3);
		assertEquals(BulletStats.DEFAULT.gravity(), silenced.bullet().gravity(), "drop is gravity, a silencer does not change it");
		assertEquals(0.4F, silenced.recoilPitchDeg(), 1.0E-6);
		assertEquals(0.15F, silenced.recoilYawDeg(), 1.0E-6);
		assertEquals(17, silenced.magSize());
		assertEquals(glock.spreadHipDeg(), silenced.spreadHipDeg());

		assertSame(glock, attachment("test_optic", AttachmentSlot.OPTIC).applyTo(glock), "an attachment with no effects leaves the gun as it is");
		assertEquals(33, attachment("test_mag", AttachmentSlot.MAGAZINE).capacity(33).applyTo(glock).magSize());
	}

	@Test
	void laserTightensHipFireAndSpeedsUpTheAim() {
		GunDefinition glock = new GunDefinition(List.of("semi"), 600, 5, 64, 17, true, 0.4F, 0.05F, 0.8F, 0.3F, 10, 40, 50, 3, 0.8F, 1.0F);
		AttachmentItem laser = attachment("test_laser", AttachmentSlot.UNDERBARREL).laser().spreadHip(0.6F).aimTime(0.67F);

		GunDefinition fitted = laser.applyTo(glock);
		assertEquals(0.24F, fitted.spreadHipDeg(), 1.0E-6);
		assertEquals(glock.spreadAdsDeg(), fitted.spreadAdsDeg(), "aimed shots keep their spread");
		assertEquals(2, fitted.adsTicks(), "3 ticks become 2");
		assertEquals(1, laser.applyTo(glock.withAdsTicks(1)).adsTicks(), "never below a tick");
		assertEquals(0, laser.applyTo(glock.withAdsTicks(0)).adsTicks(), "an instant aim stays instant");
	}

	@Test
	void opticZoomReplacesTheGunsOwn() {
		GunDefinition mosin = new GunDefinition(List.of("bolt"), 55, 17, 256, 5, false, 2.5F, 0.02F, 4, 0.8F, 18, 32, 55, 6, 0.75F, 0.75F);

		assertEquals(0.25F, attachment("test_acog", AttachmentSlot.OPTIC).zoom(4).scope().applyTo(mosin).adsFovMultiplier(), 1.0E-6);
		AttachmentItem variable = attachment("test_variable", AttachmentSlot.OPTIC).zoom(6, 10);
		assertEquals(1 / 6F, variable.applyTo(mosin).adsFovMultiplier(), 1.0E-6, "a variable optic starts at its lowest power");
		assertEquals(10, variable.maxZoom());
	}
}

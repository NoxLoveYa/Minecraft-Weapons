package com.noxloveya.mcweapons.registry;

import com.mojang.serialization.Codec;
import com.noxloveya.mcweapons.McWeapons;
import com.noxloveya.mcweapons.gun.GunState;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.util.ExtraCodecs;

public final class ModComponents {
	/** Rounds loaded. Absent means a fresh gun with a full mag, since mag size comes from data. */
	public static final DataComponentType<Integer> AMMO = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, McWeapons.id("ammo"),
			DataComponentType.<Integer>builder().persistent(ExtraCodecs.NON_NEGATIVE_INT).networkSynchronized(ByteBufCodecs.VAR_INT).build());

	public static final DataComponentType<GunState> GUN_STATE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, McWeapons.id("gun_state"),
			DataComponentType.<GunState>builder().persistent(GunState.CODEC).networkSynchronized(GunState.STREAM_CODEC).build());

	/** The selected fire mode, one of the gun's modes or safe. Absent means the gun's first mode. */
	public static final DataComponentType<String> FIRE_MODE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, McWeapons.id("fire_mode"),
			DataComponentType.<String>builder().persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8).build());

	private ModComponents() {
	}

	public static void init() {
	}
}

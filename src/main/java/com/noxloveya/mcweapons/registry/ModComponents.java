package com.noxloveya.mcweapons.registry;

import com.mojang.serialization.Codec;
import com.noxloveya.mcweapons.McWeapons;
import com.noxloveya.mcweapons.gun.GunState;
import com.noxloveya.mcweapons.gun.Reticle;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;

import java.util.HashMap;
import java.util.Map;

public final class ModComponents {
	/** Rounds loaded. Absent means a fresh gun with a full mag, since mag size comes from data. */
	public static final DataComponentType<Integer> AMMO = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, McWeapons.id("ammo"),
			DataComponentType.<Integer>builder().persistent(ExtraCodecs.NON_NEGATIVE_INT).networkSynchronized(ByteBufCodecs.VAR_INT).build());

	public static final DataComponentType<GunState> GUN_STATE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, McWeapons.id("gun_state"),
			DataComponentType.<GunState>builder().persistent(GunState.CODEC).networkSynchronized(GunState.STREAM_CODEC).build());

	/** The selected fire mode, one of the gun's modes or safe. Absent means the gun's first mode. */
	public static final DataComponentType<String> FIRE_MODE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, McWeapons.id("fire_mode"),
			DataComponentType.<String>builder().persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8).build());

	/** What is fitted to a gun: attachment slot name to attachment item id (see {@link com.noxloveya.mcweapons.gun.Attachments}). Absent means none. */
	public static final DataComponentType<Map<String, Identifier>> ATTACHMENTS = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, McWeapons.id("attachments"),
			DataComponentType.<Map<String, Identifier>>builder().persistent(Codec.unboundedMap(Codec.STRING, Identifier.CODEC))
					.networkSynchronized(ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, Identifier.STREAM_CODEC)).build());

	/** How the gun's optic draws its reticle (see {@link Reticle}). Absent means the default red dot. */
	public static final DataComponentType<Reticle> RETICLE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, McWeapons.id("reticle"),
			DataComponentType.<Reticle>builder().persistent(Reticle.CODEC).networkSynchronized(Reticle.STREAM_CODEC).build());

	/** The color (0xRRGGBB) of the gun's laser sight. Absent means the first of {@link Reticle#COLORS}, red. */
	public static final DataComponentType<Integer> LASER_COLOR = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, McWeapons.id("laser_color"),
			DataComponentType.<Integer>builder().persistent(Codec.INT).networkSynchronized(ByteBufCodecs.INT).build());

	/** How far the eye sits from each optic when aimed, optic item id to blocks behind a scope's eyepiece or a 1x sight's share of its distance (see Attachments.eyeDistance). Absent means the default. */
	public static final DataComponentType<Map<Identifier, Float>> EYE_DISTANCE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, McWeapons.id("eye_distance"),
			DataComponentType.<Map<Identifier, Float>>builder().persistent(Codec.unboundedMap(Identifier.CODEC, Codec.FLOAT))
					.networkSynchronized(ByteBufCodecs.map(HashMap::new, Identifier.STREAM_CODEC, ByteBufCodecs.FLOAT)).build());

	private ModComponents() {
	}

	public static void init() {
	}
}

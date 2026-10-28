package com.noxloveya.mcweapons.gun;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;

import java.util.Arrays;
import java.util.List;

/**
 * How a gun's optic draws its reticle: {@code color} (0xRRGGBB), {@code size} (times the default) and {@code shape}. Looks only, so the
 * values are just kept in range, whoever sends them.
 */
public record Reticle(int color, float size, Shape shape) {
	/** The colors and sizes the attachment menu steps through. */
	public static final List<Integer> COLORS = List.of(0xFF3223, 0x3CFF50, 0x30E0FF, 0xFFE030, 0xFF40E0, 0xFFFFFF);
	public static final List<Float> SIZES = List.of(0.25F, 0.5F, 0.75F, 1.0F, 1.25F);
	public static final Reticle DEFAULT = new Reticle(COLORS.getFirst(), 1.0F, Shape.DOT);

	public static final Codec<Reticle> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("color").forGetter(Reticle::color),
			Codec.FLOAT.fieldOf("size").forGetter(Reticle::size),
			Shape.CODEC.fieldOf("shape").forGetter(Reticle::shape)
	).apply(i, Reticle::new));

	public static final StreamCodec<ByteBuf, Reticle> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.INT, Reticle::color,
			ByteBufCodecs.FLOAT, Reticle::size,
			ByteBufCodecs.idMapper(i -> Shape.values()[Math.floorMod(i, Shape.values().length)], Shape::ordinal), Reticle::shape,
			Reticle::new);

	public Reticle {
		color &= 0xFFFFFF;
		size = Float.isFinite(size) ? Mth.clamp(size, SIZES.getFirst(), SIZES.getLast()) : 1.0F;
	}

	/** The next color in {@link #COLORS} ({@code step} -1 for the previous one); a color not in the list goes to the first. */
	public Reticle cycleColor(int step) {
		return new Reticle(cycle(COLORS, color, step), size, shape);
	}

	public Reticle cycleSize(int step) {
		return new Reticle(color, cycle(SIZES, size, step), shape);
	}

	public Reticle cycleShape(int step) {
		return new Reticle(color, size, cycle(Arrays.asList(Shape.values()), shape, step));
	}

	private static <T> T cycle(List<T> options, T current, int step) {
		int index = options.indexOf(current);
		return options.get(index < 0 ? 0 : Math.floorMod(index + step, options.size()));
	}

	/** Each shape is drawn from {@code textures/effect/reticle_<name>.png}. */
	public enum Shape implements StringRepresentable {
		DOT("dot"),
		CIRCLE("circle"),
		CROSS("cross"),
		CHEVRON("chevron");

		public static final Codec<Shape> CODEC = StringRepresentable.fromEnum(Shape::values);

		private final String name;

		Shape(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return name;
		}
	}
}

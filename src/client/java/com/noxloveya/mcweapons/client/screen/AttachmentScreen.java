package com.noxloveya.mcweapons.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.noxloveya.mcweapons.client.input.GunInput;
import com.noxloveya.mcweapons.gun.AttachmentItem;
import com.noxloveya.mcweapons.gun.AttachmentSlot;
import com.noxloveya.mcweapons.gun.Attachments;
import com.noxloveya.mcweapons.gun.GunDefinition;
import com.noxloveya.mcweapons.gun.GunItem;
import com.noxloveya.mcweapons.gun.Reticle;
import com.noxloveya.mcweapons.registry.ModComponents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.noxloveya.mcweapons.client.screen.GunTunerScreen.DIVIDER;
import static com.noxloveya.mcweapons.client.screen.GunTunerScreen.EDGE;
import static com.noxloveya.mcweapons.client.screen.GunTunerScreen.FILL;
import static com.noxloveya.mcweapons.client.screen.GunTunerScreen.MARGIN;
import static com.noxloveya.mcweapons.client.screen.GunTunerScreen.MUTED;
import static com.noxloveya.mcweapons.client.screen.GunTunerScreen.PANEL;
import static com.noxloveya.mcweapons.client.screen.GunTunerScreen.ROW_HOVER;
import static com.noxloveya.mcweapons.client.screen.GunTunerScreen.TEXT;
import static com.noxloveya.mcweapons.client.screen.GunTunerScreen.TRACK;
import static com.noxloveya.mcweapons.client.screen.GunTunerScreen.icon;
import static com.noxloveya.mcweapons.client.screen.GunTunerScreen.medium;
import static com.noxloveya.mcweapons.client.screen.GunTunerScreen.rounded;
import static com.noxloveya.mcweapons.client.screen.GunTunerScreen.roundedOutline;
import static com.noxloveya.mcweapons.client.screen.GunTunerScreen.ui;

/**
 * Fits attachments to the held gun, one section per part (slot) it has attachments for: none, or one of those that fit. It does not
 * pause the game or move the gun, so the fitting animation plays behind it. Drawn like the gun tuner.
 */
public class AttachmentScreen extends Screen {
	// Lucide codepoints (font/lucide.ttf).
	private static final int ICON_TITLE = 0xE1B1;
	private static final int ICON_FITTED = 0xE226;
	private static final int ICON_OPTION = 0xE076;
	private static final int ICON_NONE = 0xE402;
	private static final int ICON_KEYS = 0xE24A;
	private static final Map<AttachmentSlot, Integer> SLOT_ICONS = Map.of(AttachmentSlot.MUZZLE, 0xE626, AttachmentSlot.OPTIC, 0xE29E,
			AttachmentSlot.UNDERBARREL, 0xE0D3, AttachmentSlot.MAGAZINE, 0xE58A);
	private static final int ICON_COLLAPSED = 0xE06F;
	private static final int ICON_EXPANDED = 0xE06D;
	// Room for an optic's name with its zoom ("Variable Scope (6-10x)"), its options arrow and the note on the right.
	private static final int WIDTH = 200;
	private static final int HEADER = 16;
	private static final int ROW = 15;
	private static final int SECTION_GAP = 6;
	/** The reticle size slider's track, from under the option's name to the end of its value. */
	private static final int SLIDER_X0 = MARGIN + 36;
	private static final int SLIDER_X1 = MARGIN + WIDTH - 9;
	/** Lang key suffixes of {@link Reticle#COLORS}, in the same order. */
	private static final List<String> COLOR_NAMES = List.of("red", "green", "cyan", "yellow", "magenta", "white");

	/**
	 * A setting of an attachment, shown under its row once its arrow is opened (right click): an optic's reticle color, size and shape,
	 * a scope's eye distance, or a laser's color. Looks only, so they are stored on the gun and change any time.
	 */
	private enum Option {
		COLOR(0xE1DD), SIZE(0xE2EC), SHAPE(0xE4B3), EYE_DISTANCE(0xE536), LASER_COLOR(0xE1DD);

		private static final List<Option> RETICLE = List.of(COLOR, SIZE, SHAPE);
		private static final List<Option> SCOPE = List.of(COLOR, SIZE, SHAPE, EYE_DISTANCE);
		private static final List<Option> LASER = List.of(LASER_COLOR);

		final int icon;

		Option(int icon) {
			this.icon = icon;
		}

		static List<Option> of(@Nullable AttachmentItem attachment) {
			return attachment == null ? List.of() : attachment.isLaser() ? LASER : attachment.isScope() ? SCOPE
					: attachment.slot() == AttachmentSlot.OPTIC ? RETICLE : List.of();
		}

		/** Moves this setting of {@code attachment} on the held gun {@code step} choices on (-1 back). The size is a slider instead. */
		void step(LocalPlayer player, ItemStack gun, AttachmentItem attachment, int step) {
			Reticle reticle = gun.getOrDefault(ModComponents.RETICLE, Reticle.DEFAULT);
			switch (this) {
				case COLOR -> GunInput.reticle(player, reticle.cycleColor(step));
				case SHAPE -> GunInput.reticle(player, reticle.cycleShape(step));
				case EYE_DISTANCE -> {
					// Further or nearer, stopping at the ends rather than jumping across while it is watched.
					List<Float> distances = Attachments.EYE_DISTANCES;
					int at = nearest(distances, Attachments.eyeDistance(gun, attachment));
					GunInput.eyeDistance(player, attachment, distances.get(Mth.clamp(at + step, 0, distances.size() - 1)));
				}
				case LASER_COLOR -> GunInput.laserColor(player, Reticle.nextColor(laserColor(gun), step));
			}
		}

		Component value(ItemStack gun, AttachmentItem attachment) {
			Reticle reticle = gun.getOrDefault(ModComponents.RETICLE, Reticle.DEFAULT);
			return switch (this) {
				case COLOR, LASER_COLOR -> colorName(swatch(gun));
				case SIZE -> Component.literal(Math.round(reticle.size() * 100) + "%");
				case SHAPE -> Component.translatable("gui.mcweapons.reticle.shape." + reticle.shape().getSerializedName());
				case EYE_DISTANCE -> Component.literal(Math.round(Attachments.eyeDistance(gun, attachment) / Attachments.DEFAULT_EYE_DISTANCE * 100) + "%");
			};
		}

		private static int nearest(List<Float> options, float value) {
			int best = 0;
			for (int i = 1; i < options.size(); i++) {
				if (Math.abs(options.get(i) - value) < Math.abs(options.get(best) - value)) {
					best = i;
				}
			}
			return best;
		}

		/** The color this setting shows a swatch of, or -1 for none. */
		int swatch(ItemStack gun) {
			return switch (this) {
				case COLOR -> gun.getOrDefault(ModComponents.RETICLE, Reticle.DEFAULT).color();
				case LASER_COLOR -> laserColor(gun);
				default -> -1;
			};
		}

		private static int laserColor(ItemStack gun) {
			return gun.getOrDefault(ModComponents.LASER_COLOR, Reticle.COLORS.getFirst());
		}

		private static Component colorName(int color) {
			int index = Reticle.COLORS.indexOf(color);
			return index < 0 ? Component.literal(String.format("#%06X", color)) : Component.translatable("gui.mcweapons.reticle.color." + COLOR_NAMES.get(index));
		}
	}

	/** One clickable line: fit {@code attachment} (null for none) into {@code slot}, or, with an {@code option}, change that setting of it. */
	private record Row(AttachmentSlot slot, @Nullable AttachmentItem attachment, @Nullable Option option, int y) {
	}

	private final List<Row> rows = new ArrayList<>();
	private final Map<AttachmentSlot, Integer> sectionTops = new EnumMap<>(AttachmentSlot.class);
	/** Attachments whose options are open. */
	private final Set<AttachmentItem> expanded = new HashSet<>();
	private int top;
	private int bottom;
	// Row metrics: the full ones, or tighter ones when the panel would not fit the screen otherwise (four parts and an open option list
	// do not fit 720p at the auto GUI scale).
	private int rowHeight = ROW;
	private int headerHeight = HEADER;
	private int sectionGap = SECTION_GAP;
	/** From a row's top to its text. */
	private int pad = 4;
	/** The key opened this screen; it only closes it again once that press has been let go. */
	private boolean keyReleased;
	/** The reticle size slider is held down. */
	private boolean draggingSize;

	public AttachmentScreen() {
		super(Component.translatable("gui.mcweapons.attachments"));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	protected void init() {
		GunTunerScreen.useFontScale(minecraft);
		layout();
	}

	/** Sections from the held gun's definition, centered on the left. Recomputed each frame in case the gun or the data changed. */
	private void layout() {
		rows.clear();
		sectionTops.clear();
		GunDefinition definition = GunItem.definition(held());
		Map<AttachmentSlot, List<AttachmentItem>> fitting = new EnumMap<>(AttachmentSlot.class);
		if (definition != null) {
			for (var id : definition.attachments()) {
				if (BuiltInRegistries.ITEM.getValue(id) instanceof AttachmentItem item) {
					fitting.computeIfAbsent(item.slot(), slot -> new ArrayList<>()).add(item);
				}
			}
		}
		int lines = 0;
		for (List<AttachmentItem> items : fitting.values()) {
			lines += items.size() + 1;
			for (AttachmentItem item : items) {
				lines += open(item) ? Option.of(item).size() : 0;
			}
		}
		// Room for the hint line under the panel.
		boolean compact = 34 + fitting.size() * (HEADER + SECTION_GAP) + lines * ROW + 18 > this.height - MARGIN;
		rowHeight = compact ? 11 : ROW;
		headerHeight = compact ? 12 : HEADER;
		sectionGap = compact ? 3 : SECTION_GAP;
		pad = compact ? 2 : 4;
		int height = 34 + fitting.size() * (headerHeight + sectionGap) + lines * rowHeight;
		top = Math.max(MARGIN, (this.height - height - 18) / 2);
		int y = top + 34;
		for (Map.Entry<AttachmentSlot, List<AttachmentItem>> section : fitting.entrySet()) {
			sectionTops.put(section.getKey(), y);
			y += headerHeight;
			rows.add(new Row(section.getKey(), null, null, y));
			y += rowHeight;
			for (AttachmentItem item : section.getValue()) {
				rows.add(new Row(section.getKey(), item, null, y));
				y += rowHeight;
				if (open(item)) {
					for (Option option : Option.of(item)) {
						rows.add(new Row(section.getKey(), item, option, y));
						y += rowHeight;
					}
				}
			}
			y += sectionGap;
		}
		bottom = y;
	}

	private ItemStack held() {
		return minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getMainHandItem();
	}

	/** The menu is open with the fitted optic's settings showing: the gun is aimed behind it, so the changes can be seen. */
	public static boolean previewing(Minecraft minecraft) {
		return minecraft.gui.screen() instanceof AttachmentScreen screen && screen.expanded.contains(Attachments.in(screen.held(), AttachmentSlot.OPTIC));
	}

	private static boolean hasOptions(@Nullable AttachmentItem attachment) {
		return !Option.of(attachment).isEmpty();
	}

	private boolean open(AttachmentItem attachment) {
		return hasOptions(attachment) && expanded.contains(attachment);
	}

	@Override
	public void tick() {
		// The gun was put away or swapped: nothing left to fit.
		if (!(held().getItem() instanceof GunItem)) {
			onClose();
		}
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		layout();
		LocalPlayer player = minecraft.player;
		ItemStack gun = held();
		int x0 = MARGIN;
		int x1 = x0 + WIDTH;
		rounded(graphics, x0, top, x1, bottom, PANEL);
		roundedOutline(graphics, x0, top, x1, bottom, EDGE);
		graphics.text(font, icon(ICON_TITLE), x0 + 8, top + 8, TEXT, false);
		graphics.text(font, medium(Component.translatable("gui.mcweapons.attachments")), x0 + 21, top + 8, TEXT, false);
		graphics.text(font, ui(gun.getHoverName()), x0 + 21, top + 19, MUTED, false);
		boolean busy = player == null || GunInput.busy(player, gun);
		for (Map.Entry<AttachmentSlot, Integer> section : sectionTops.entrySet()) {
			int y = section.getValue();
			graphics.fill(x0 + 7, y, x1 - 7, y + 1, DIVIDER);
			graphics.text(font, icon(SLOT_ICONS.get(section.getKey())), x0 + 8, y + (headerHeight - 6) / 2, MUTED, false);
			graphics.text(font, ui(Component.translatable("gui.mcweapons.attachments.slot." + section.getKey().getSerializedName())), x0 + 21, y + (headerHeight - 6) / 2, MUTED, false);
		}
		for (Row row : rows) {
			if (row.option() != null) {
				// Looks only: changeable any time, even mid-reload or before the attachment is fitted.
				if (hovered(row, mouseX, mouseY)) {
					rounded(graphics, x0 + 4, row.y(), x1 - 4, row.y() + rowHeight, ROW_HOVER);
				}
				graphics.text(font, icon(row.option().icon), x0 + 23, row.y() + pad, MUTED, false);
				graphics.text(font, ui(Component.translatable("gui.mcweapons.reticle." + row.option().name().toLowerCase(Locale.ROOT))), x0 + 36, row.y() + pad, 0xD9FFFFFF, false);
				Component value = ui(row.option().value(gun, row.attachment()));
				int valueX = x1 - 9 - font.width(value);
				graphics.text(font, value, valueX, row.y() + pad, TEXT, false);
				int swatch = row.option().swatch(gun);
				if (swatch >= 0) {
					graphics.fill(valueX - 10, row.y() + pad, valueX - 3, row.y() + pad + 7, 0xFF000000 | swatch);
				}
				if (row.option() == Option.SIZE) {
					// A hairline track along the bottom filled up to the size, like the tuner's sliders.
					float size = gun.getOrDefault(ModComponents.RETICLE, Reticle.DEFAULT).size();
					int trackY = row.y() + rowHeight - 2;
					int at = SLIDER_X0 + Math.round((size - Reticle.MIN_SIZE) / (Reticle.MAX_SIZE - Reticle.MIN_SIZE) * (SLIDER_X1 - SLIDER_X0));
					boolean active = draggingSize || hovered(row, mouseX, mouseY);
					graphics.fill(SLIDER_X0, trackY, SLIDER_X1, trackY + 1, TRACK);
					graphics.fill(SLIDER_X0, trackY, at, trackY + 1, active ? TEXT : FILL);
					if (active) {
						graphics.fill(at - 1, trackY - 1, at + 2, trackY + 2, TEXT);
					}
				}
				continue;
			}
			AttachmentItem fitted = Attachments.in(gun, row.slot());
			boolean current = fitted == row.attachment();
			boolean available = row.attachment() == null || owned(player, row.attachment()) > 0 || player != null && player.hasInfiniteMaterials();
			boolean hot = !busy && !current && available && hovered(row, mouseX, mouseY);
			if (hot) {
				rounded(graphics, x0 + 4, row.y(), x1 - 4, row.y() + rowHeight, ROW_HOVER);
			}
			int color = current ? TEXT : available && !busy ? 0xD9FFFFFF : MUTED;
			int glyph = current ? ICON_FITTED : row.attachment() == null ? ICON_NONE : ICON_OPTION;
			graphics.text(font, icon(glyph), x0 + 10, row.y() + pad, color, false);
			Component name = row.attachment() == null ? Component.translatable("gui.mcweapons.attachments.none") : new ItemStack(row.attachment()).getHoverName();
			graphics.text(font, ui(name), x0 + 23, row.y() + pad, color, false);
			if (hasOptions(row.attachment())) {
				graphics.text(font, icon(open(row.attachment()) ? ICON_EXPANDED : ICON_COLLAPSED), x0 + 26 + font.width(ui(name)), row.y() + pad, MUTED, false);
			}
			Component note = note(player, row, current);
			if (note != null) {
				Component text = ui(note);
				graphics.text(font, text, x1 - 9 - font.width(text), row.y() + pad, MUTED, false);
			}
		}
		Component hint = ui(Component.translatable(busy ? "gui.mcweapons.attachments.busy" : "gui.mcweapons.attachments.hint"));
		graphics.text(font, icon(ICON_KEYS), x0 + 2, bottom + 6, MUTED, false);
		graphics.text(font, hint, x0 + 15, bottom + 6, MUTED, false);
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	/** Right-hand note on a row: how many the player has to fit, or that it is the one fitted. */
	private static @Nullable Component note(@Nullable LocalPlayer player, Row row, boolean current) {
		if (current) {
			return row.attachment() == null ? null : Component.translatable("gui.mcweapons.attachments.fitted");
		}
		if (row.attachment() == null || player == null) {
			return null;
		}
		if (player.hasInfiniteMaterials()) {
			return Component.translatable("gui.mcweapons.attachments.creative");
		}
		int owned = owned(player, row.attachment());
		return owned > 0 ? Component.literal("×" + owned) : Component.translatable("gui.mcweapons.attachments.missing");
	}

	private static int owned(@Nullable LocalPlayer player, AttachmentItem attachment) {
		if (player == null) {
			return 0;
		}
		int count = 0;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(attachment)) {
				count += stack.getCount();
			}
		}
		return count;
	}

	private boolean hovered(Row row, double x, double y) {
		return x >= MARGIN + 4 && x < MARGIN + WIDTH - 4 && y >= row.y() && y < row.y() + rowHeight;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		LocalPlayer player = minecraft.player;
		ItemStack gun = held();
		boolean right = event.button() == InputConstants.MOUSE_BUTTON_RIGHT;
		for (Row row : rows) {
			if (player == null || !hovered(row, event.x(), event.y())) {
				continue;
			}
			if (row.option() == Option.SIZE) {
				draggingSize = true;
				slideSize(player, gun, event.x());
				return true;
			}
			if (row.option() != null) {
				// Left steps forward, right steps back.
				row.option().step(player, gun, row.attachment(), right ? -1 : 1);
				return true;
			}
			if (right && hasOptions(row.attachment())) {
				if (!expanded.remove(row.attachment())) {
					expanded.add(row.attachment());
				}
				return true;
			}
			if (!right && !GunInput.busy(player, gun) && Attachments.in(gun, row.slot()) != row.attachment()
					&& (row.attachment() == null || player.hasInfiniteMaterials() || owned(player, row.attachment()) > 0)) {
				GunInput.attach(player, row.slot(), row.attachment());
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (draggingSize && minecraft.player != null) {
			slideSize(minecraft.player, held(), event.x());
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		draggingSize = false;
		return super.mouseReleased(event);
	}

	/** Sets the reticle's size from where the mouse is along the slider, in 5% steps; sent only when the step changes. */
	private static void slideSize(LocalPlayer player, ItemStack gun, double mouseX) {
		Reticle reticle = gun.getOrDefault(ModComponents.RETICLE, Reticle.DEFAULT);
		float along = Mth.clamp((float)(mouseX - SLIDER_X0) / (SLIDER_X1 - SLIDER_X0), 0, 1);
		float size = Math.round(Mth.lerp(along, Reticle.MIN_SIZE, Reticle.MAX_SIZE) * 20) / 20F;
		if (size != reticle.size()) {
			GunInput.reticle(player, reticle.withSize(size));
		}
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (GunInput.ATTACHMENTS.matches(event) && keyReleased) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean keyReleased(KeyEvent event) {
		if (GunInput.ATTACHMENTS.matches(event)) {
			keyReleased = true;
		}
		return super.keyReleased(event);
	}
}

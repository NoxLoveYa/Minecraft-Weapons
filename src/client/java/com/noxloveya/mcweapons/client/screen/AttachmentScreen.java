package com.noxloveya.mcweapons.client.screen;

import com.noxloveya.mcweapons.client.input.GunInput;
import com.noxloveya.mcweapons.gun.AttachmentItem;
import com.noxloveya.mcweapons.gun.AttachmentSlot;
import com.noxloveya.mcweapons.gun.Attachments;
import com.noxloveya.mcweapons.gun.GunDefinition;
import com.noxloveya.mcweapons.gun.GunItem;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static com.noxloveya.mcweapons.client.screen.GunTunerScreen.DIVIDER;
import static com.noxloveya.mcweapons.client.screen.GunTunerScreen.EDGE;
import static com.noxloveya.mcweapons.client.screen.GunTunerScreen.MARGIN;
import static com.noxloveya.mcweapons.client.screen.GunTunerScreen.MUTED;
import static com.noxloveya.mcweapons.client.screen.GunTunerScreen.PANEL;
import static com.noxloveya.mcweapons.client.screen.GunTunerScreen.ROW_HOVER;
import static com.noxloveya.mcweapons.client.screen.GunTunerScreen.TEXT;
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
	private static final int WIDTH = 160;
	private static final int HEADER = 16;
	private static final int ROW = 15;

	/** One clickable line: fit {@code attachment} (null for none) into {@code slot}. */
	private record Row(AttachmentSlot slot, @Nullable AttachmentItem attachment, int y) {
	}

	private final List<Row> rows = new ArrayList<>();
	private final Map<AttachmentSlot, Integer> sectionTops = new EnumMap<>(AttachmentSlot.class);
	private int top;
	private int bottom;
	/** The key opened this screen; it only closes it again once that press has been let go. */
	private boolean keyReleased;

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
		int height = 34;
		for (List<AttachmentItem> items : fitting.values()) {
			height += HEADER + (items.size() + 1) * ROW + 6;
		}
		top = Math.max(MARGIN, (this.height - height) / 2);
		int y = top + 34;
		for (Map.Entry<AttachmentSlot, List<AttachmentItem>> section : fitting.entrySet()) {
			sectionTops.put(section.getKey(), y);
			y += HEADER;
			rows.add(new Row(section.getKey(), null, y));
			y += ROW;
			for (AttachmentItem item : section.getValue()) {
				rows.add(new Row(section.getKey(), item, y));
				y += ROW;
			}
			y += 6;
		}
		bottom = y;
	}

	private ItemStack held() {
		return minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getMainHandItem();
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
			graphics.text(font, icon(SLOT_ICONS.get(section.getKey())), x0 + 8, y + 5, MUTED, false);
			graphics.text(font, ui(Component.translatable("gui.mcweapons.attachments.slot." + section.getKey().getSerializedName())), x0 + 21, y + 5, MUTED, false);
		}
		for (Row row : rows) {
			AttachmentItem fitted = Attachments.in(gun, row.slot());
			boolean current = fitted == row.attachment();
			boolean available = row.attachment() == null || owned(player, row.attachment()) > 0 || player != null && player.hasInfiniteMaterials();
			boolean hot = !busy && !current && available && hovered(row, mouseX, mouseY);
			if (hot) {
				rounded(graphics, x0 + 4, row.y(), x1 - 4, row.y() + ROW, ROW_HOVER);
			}
			int color = current ? TEXT : available && !busy ? 0xD9FFFFFF : MUTED;
			int glyph = current ? ICON_FITTED : row.attachment() == null ? ICON_NONE : ICON_OPTION;
			graphics.text(font, icon(glyph), x0 + 10, row.y() + 4, color, false);
			Component name = row.attachment() == null ? Component.translatable("gui.mcweapons.attachments.none") : new ItemStack(row.attachment()).getHoverName();
			graphics.text(font, ui(name), x0 + 23, row.y() + 4, color, false);
			Component note = note(player, row, current);
			if (note != null) {
				Component text = ui(note);
				graphics.text(font, text, x1 - 9 - font.width(text), row.y() + 4, MUTED, false);
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
		return x >= MARGIN + 4 && x < MARGIN + WIDTH - 4 && y >= row.y() && y < row.y() + ROW;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		LocalPlayer player = minecraft.player;
		ItemStack gun = held();
		if (player != null && !GunInput.busy(player, gun)) {
			for (Row row : rows) {
				if (hovered(row, event.x(), event.y()) && Attachments.in(gun, row.slot()) != row.attachment()
						&& (row.attachment() == null || player.hasInfiniteMaterials() || owned(player, row.attachment()) > 0)) {
					GunInput.attach(player, row.slot(), row.attachment());
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
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

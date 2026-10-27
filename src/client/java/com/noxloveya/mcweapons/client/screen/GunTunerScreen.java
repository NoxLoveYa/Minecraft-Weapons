package com.noxloveya.mcweapons.client.screen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.noxloveya.mcweapons.client.input.GunInput;
import com.noxloveya.mcweapons.gun.GunDefinition;
import com.noxloveya.mcweapons.gun.GunDefinitions;
import com.noxloveya.mcweapons.network.TuneGunPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Ops-only tuner for the held gun's definition, opened and closed with the tuner key. While it is open the viewmodel turns the gun
 * sideways in the middle of the screen, and the values sit in panels around it like the arms of a plus, each next to the part it is
 * about. Apply uses the values live for everyone, Save also writes them into the world, Reset goes back to the gun's own file.
 */
public class GunTunerScreen extends Screen {
	private static final int PANEL = 0xD0101318;
	private static final int PANEL_HEADER = 0xF01A1E25;
	private static final int FIELD = 0xFF0A0C10;
	private static final int FIELD_FOCUSED = 0xFF262B34;
	private static final int ACCENT = 0xFFE8A33A;
	private static final int LABEL = 0xFFB4B9C2;
	private static final int TEXT = 0xFFFFFFFF;
	private static final int INVALID_COLOR = 0xFFFF5555;
	private static final int MARGIN = 6;
	private static final int PANEL_WIDTH = 132;
	private static final int HEADER = 14;
	private static final int ROW = 13;
	private static final int BOX_WIDTH = 40;
	private static final int STACK_GAP = 6;
	private static final int ARM = 8; // accent line from a panel towards the gun
	private static final int BUTTON_WIDTH = 56;
	private static final int BUTTON_HEIGHT = 16;

	/** Which arm of the plus a panel sits on: sights up, magazine down, muzzle left (the left side faces you), grip right. */
	private enum Side { TOP, BOTTOM, LEFT, RIGHT }

	/** A titled panel of json values, by path ("rpm", or "feel.sway_gain" inside the feel object). */
	private record Group(String name, Side side, List<String> paths) {
	}

	private static final List<Group> GROUPS = List.of(
			new Group("aim", Side.TOP, List.of("spread_hip_deg", "spread_ads_deg", "ads_ticks", "ads_fov_multiplier", "move_speed_ads_multiplier")),
			new Group("magazine", Side.BOTTOM, List.of("mag_size", "chamber", "draw_ticks", "reload_ticks", "reload_empty_ticks")),
			new Group("fire", Side.LEFT, List.of("fire_modes", "rpm", "damage", "range")),
			new Group("handling", Side.LEFT, List.of("feel.sway_gain", "feel.sway_max_deg", "feel.sway_follow", "feel.bob_scale")),
			new Group("recoil", Side.RIGHT, List.of("recoil_pitch_deg", "recoil_yaw_deg", "feel.recoil_push_back", "feel.recoil_spring_stiffness",
					"feel.recoil_spring_damping", "feel.recoil_max_kicks")));

	private final Identifier id;
	private final Component gunName;
	private final List<Row> rows = new ArrayList<>();
	private final List<Panel> panels = new ArrayList<>();
	/** Last definition seen in {@link GunDefinitions}; a new one (from Apply, Reset or another op) refills the rows. */
	private GunDefinition synced;
	/** Last valid values. Each row is checked on its own against these, so one bad row does not mark the others. */
	private GunDefinition definition;

	/** One json value: {@code key} at the top level, or inside the {@code group} object. Arrays are typed comma separated. */
	private record Row(@Nullable String group, String key, boolean array, Component label, int labelX, EditBox box) {
		String path() {
			return group == null ? key : group + "." + key;
		}
	}

	private record Panel(Component title, Side side, int x, int y, int height) {
	}

	public GunTunerScreen(Identifier id, GunDefinition definition, Component gunName) {
		super(Component.translatable("gui.mcweapons.tuner.title", gunName));
		this.id = id;
		this.gunName = gunName;
		this.synced = definition;
		this.definition = definition;
	}

	public static boolean isOpen() {
		return Minecraft.getInstance().gui.screen() instanceof GunTunerScreen;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	protected void init() {
		// init() runs again on resize and after the reset confirm; keep what was typed but not applied yet.
		Map<String, String> typed = new HashMap<>();
		rows.forEach(row -> typed.put(row.path(), row.box().getValue()));
		rows.clear();
		panels.clear();

		JsonObject json = values(definition);
		List<Group> groups = new ArrayList<>(GROUPS);
		// Values no group knows about (a newer definition field) still get a panel instead of vanishing.
		Set<String> other = new LinkedHashSet<>(paths(json));
		GROUPS.forEach(group -> group.paths().forEach(other::remove));
		if (!other.isEmpty()) {
			groups.add(new Group("other", Side.RIGHT, List.copyOf(other)));
		}

		for (Side side : Side.values()) {
			List<Group> stack = groups.stream().filter(group -> group.side() == side).toList();
			int total = stack.stream().mapToInt(group -> HEADER + group.paths().size() * ROW).sum() + STACK_GAP * Math.max(0, stack.size() - 1);
			int x = switch (side) {
				case TOP, BOTTOM -> (width - PANEL_WIDTH) / 2;
				case LEFT -> MARGIN;
				case RIGHT -> width - MARGIN - PANEL_WIDTH;
			};
			int y = switch (side) {
				case TOP -> MARGIN;
				case BOTTOM -> height - MARGIN - total;
				case LEFT, RIGHT -> (height - total) / 2;
			};
			for (Group group : stack) {
				int panelHeight = HEADER + group.paths().size() * ROW;
				panels.add(new Panel(Component.translatable("gui.mcweapons.tuner.group." + group.name()), side, x, y, panelHeight));
				for (int i = 0; i < group.paths().size(); i++) {
					addRow(json, group.paths().get(i), x, y + HEADER + i * ROW, typed);
				}
				y += panelHeight + STACK_GAP;
			}
		}

		// The corners are free in a plus: buttons in the bottom ones.
		int bottom = height - MARGIN - BUTTON_HEIGHT;
		int right = width - MARGIN - BUTTON_WIDTH;
		addRenderableWidget(button("done", right, bottom, this::onClose));
		addRenderableWidget(button("save", right, bottom - BUTTON_HEIGHT - 3, () -> send(TuneGunPayload.Action.SAVE, "gui.mcweapons.tuner.saved")));
		addRenderableWidget(button("apply", right, bottom - 2 * (BUTTON_HEIGHT + 3), () -> send(TuneGunPayload.Action.APPLY, "gui.mcweapons.tuner.applied")));
		addRenderableWidget(button("reset", MARGIN, bottom, this::confirmReset));
		addRenderableWidget(button("copy", MARGIN, bottom - BUTTON_HEIGHT - 3, this::copy));
	}

	private void addRow(JsonObject json, String path, int panelX, int rowY, Map<String, String> typed) {
		int dot = path.indexOf('.');
		String group = dot < 0 ? null : path.substring(0, dot);
		String key = path.substring(dot + 1);
		JsonElement value = (group == null ? json : json.getAsJsonObject(group)).get(key);
		EditBox box = new EditBox(font, panelX + PANEL_WIDTH - BOX_WIDTH - 4, rowY + 3, BOX_WIDTH - 4, 9, Component.literal(key));
		box.setBordered(false);
		box.setMaxLength(64);
		box.setTooltip(Tooltip.create(Component.literal(path)));
		Row row = new Row(group, key, value.isJsonArray(), Component.translatableWithFallback("gui.mcweapons.tuner.field." + key, key), panelX + 5, box);
		box.setResponder(text -> box.setTextColor(GunDefinition.CODEC.parse(JsonOps.INSTANCE, with(values(definition), row, text)).isSuccess() ? TEXT : INVALID_COLOR));
		box.setValue(typed.getOrDefault(row.path(), text(value)));
		rows.add(row);
		addRenderableWidget(box);
	}

	private Button button(String name, int x, int y, Runnable action) {
		Component label = name.equals("done") ? CommonComponents.GUI_DONE : Component.translatable("gui.mcweapons.tuner." + name);
		return Button.builder(label, button -> action.run()).bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build();
	}

	/** No blur or dim: the world and the showcased gun stay in view behind the panels. */
	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		graphics.text(font, Component.translatable("gui.mcweapons.tuner"), MARGIN, MARGIN, ACCENT, true);
		graphics.text(font, gunName, MARGIN, MARGIN + 11, TEXT, true);
		for (Panel panel : panels) {
			int x0 = panel.x();
			int y0 = panel.y();
			int x1 = x0 + PANEL_WIDTH;
			int y1 = y0 + panel.height();
			graphics.fill(x0, y0, x1, y1, PANEL);
			graphics.fill(x0, y0, x1, y0 + HEADER, PANEL_HEADER);
			graphics.text(font, panel.title(), x0 + 5, y0 + 3, ACCENT, false);
			// The edge facing the gun is lit, with a short arm reaching towards it.
			switch (panel.side()) {
				case TOP -> {
					graphics.fill(x0, y1 - 1, x1, y1, ACCENT);
					graphics.fill((x0 + x1) / 2, y1, (x0 + x1) / 2 + 1, y1 + ARM, ACCENT);
				}
				case BOTTOM -> {
					graphics.fill(x0, y0, x1, y0 + 1, ACCENT);
					graphics.fill((x0 + x1) / 2, y0 - ARM, (x0 + x1) / 2 + 1, y0, ACCENT);
				}
				case LEFT -> {
					graphics.fill(x1 - 1, y0, x1, y1, ACCENT);
					graphics.fill(x1, y0 + HEADER / 2, x1 + ARM, y0 + HEADER / 2 + 1, ACCENT);
				}
				case RIGHT -> {
					graphics.fill(x0, y0, x0 + 1, y1, ACCENT);
					graphics.fill(x0 - ARM, y0 + HEADER / 2, x0, y0 + HEADER / 2 + 1, ACCENT);
				}
			}
		}
		for (Row row : rows) {
			EditBox box = row.box();
			graphics.fill(box.getX() - 3, box.getY() - 2, box.getX() + BOX_WIDTH - 3, box.getY() + 10, box.isFocused() ? FIELD_FOCUSED : FIELD);
			graphics.text(font, font.plainSubstrByWidth(row.label().getString(), box.getX() - 7 - row.labelX()), row.labelX(), box.getY(), LABEL, false);
		}
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	@Override
	public void tick() {
		GunDefinition current = GunDefinitions.get(id);
		if (current != null && current != synced) {
			synced = current;
			definition = current;
			JsonObject json = values(current);
			rows.forEach(row -> row.box().setValue(text(holder(json, row).get(row.key()))));
		}
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		// The tuner key closes it again, unless it is being typed into a field.
		if (GunInput.TUNER.matches(event) && rows.stream().noneMatch(row -> row.box().isFocused())) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	private void send(TuneGunPayload.Action action, String doneKey) {
		GunDefinition tuned = readAll();
		if (tuned != null) {
			definition = tuned;
			ClientPlayNetworking.send(new TuneGunPayload(id, tuned, action));
			toast(Component.translatable(doneKey, gunName));
		}
	}

	private void copy() {
		GunDefinition tuned = readAll();
		if (tuned != null) {
			minecraft.keyboardHandler.setClipboard(GunDefinitions.toJsonText(id, tuned));
			toast(Component.translatable("gui.mcweapons.tuner.copied"));
		}
	}

	private void confirmReset() {
		minecraft.gui.setScreen(new ConfirmScreen(yes -> {
			if (yes) {
				ClientPlayNetworking.send(new TuneGunPayload(id, definition, TuneGunPayload.Action.RESET));
			}
			minecraft.gui.setScreen(this);
		}, Component.translatable("gui.mcweapons.tuner.reset.title", gunName), Component.translatable("gui.mcweapons.tuner.reset.message")));
	}

	/** Every row as a definition, or null after showing why not. */
	private @Nullable GunDefinition readAll() {
		JsonObject json = values(definition);
		rows.forEach(row -> with(json, row, row.box().getValue()));
		DataResult<GunDefinition> result = GunDefinition.CODEC.parse(JsonOps.INSTANCE, json);
		result.ifError(error -> toast(Component.translatable("gui.mcweapons.tuner.invalid", error.message())));
		return result.result().orElse(null);
	}

	private void toast(Component message) {
		SystemToast.addOrUpdate(minecraft.gui.toastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION, Component.translatable("gui.mcweapons.tuner"), message);
	}

	private static JsonObject values(GunDefinition definition) {
		return GunDefinition.CODEC.encodeStart(JsonOps.INSTANCE, definition).getOrThrow().getAsJsonObject();
	}

	/** Every value's path, one level into nested objects. */
	private static List<String> paths(JsonObject json) {
		List<String> paths = new ArrayList<>();
		json.asMap().forEach((key, value) -> {
			if (value.isJsonObject()) {
				value.getAsJsonObject().keySet().forEach(inner -> paths.add(key + "." + inner));
			} else {
				paths.add(key);
			}
		});
		return paths;
	}

	private static JsonObject holder(JsonObject json, Row row) {
		return row.group() == null ? json : json.getAsJsonObject(row.group());
	}

	private static JsonObject with(JsonObject json, Row row, String text) {
		holder(json, row).add(row.key(), row.array() ? list(text) : value(text));
		return json;
	}

	private static JsonElement value(String text) {
		try {
			return JsonParser.parseString(text);
		} catch (JsonParseException e) {
			return new JsonPrimitive(text);
		}
	}

	private static JsonArray list(String text) {
		JsonArray array = new JsonArray();
		for (String part : text.split(",")) {
			if (!part.isBlank()) {
				array.add(part.trim());
			}
		}
		return array;
	}

	private static String text(JsonElement value) {
		return value.isJsonArray() ? value.getAsJsonArray().asList().stream().map(JsonElement::getAsString).collect(Collectors.joining(", ")) : value.getAsString();
	}
}

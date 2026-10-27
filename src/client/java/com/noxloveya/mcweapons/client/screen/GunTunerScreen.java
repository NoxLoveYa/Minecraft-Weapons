package com.noxloveya.mcweapons.client.screen;

import com.geckolib.animatable.client.GeoRenderProvider;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.noxloveya.mcweapons.client.input.GunInput;
import com.noxloveya.mcweapons.client.render.GunRenderer;
import com.noxloveya.mcweapons.client.render.Viewmodel;
import com.noxloveya.mcweapons.gun.GunDefinition;
import com.noxloveya.mcweapons.gun.GunDefinitions;
import com.noxloveya.mcweapons.gun.GunItem;
import com.noxloveya.mcweapons.network.TuneGunPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Ops-only tuner for the held gun's definition, opened and closed with the tuner key. While it is open the viewmodel presents the gun
 * sideways in the middle of the screen, and the values sit in panels around it like the arms of a plus, each wired to the part it is
 * about. The layout follows the gun's real size on screen. Apply uses the values live for everyone, Save also writes them into the world,
 * Reset goes back to the gun's own file.
 */
public class GunTunerScreen extends Screen {
	private static final int PANEL = 0xD0101318;
	private static final int PANEL_HEADER = 0xF01A1E25;
	private static final int FIELD = 0xFF0A0C10;
	private static final int FIELD_HOVER = 0xFF262B34;
	private static final int ACCENT = 0xFFE8A33A;
	private static final int TEXT = 0xFFFFFFFF;
	private static final int INVALID_COLOR = 0xFFFF5555;
	private static final int MARGIN = 6;
	private static final int CORNER = 22; // rows kept free at the top and bottom for the title and the buttons
	private static final int GAP = 10; // between a panel and the gun
	private static final int BUTTON_HEIGHT = 16;
	private static final List<String> FIRE_MODES = List.of("semi", "auto", "semi, auto");

	/** Which arm of the plus a panel sits on. The gun shows its left side, so the muzzle points left. */
	private enum Side { TOP, BOTTOM, LEFT, RIGHT }

	/** A titled panel of json values, by path ("rpm", or "feel.sway_gain" inside the feel object), wired to a bone of the gun. */
	private record Group(String name, Side side, @Nullable String anchorBone, List<String> paths) {
	}

	/** Slider range and step of a number. Ranges stay inside what the definition codec accepts. */
	private record Range(double min, double max, double step) {
	}

	private static final List<Group> GROUPS = List.of(
			new Group("aim", Side.TOP, "ads_anchor", List.of("spread_hip_deg", "spread_ads_deg", "ads_ticks", "ads_fov_multiplier", "move_speed_ads_multiplier")),
			new Group("magazine", Side.BOTTOM, "magazine", List.of("mag_size", "chamber", "draw_ticks", "reload_ticks", "reload_empty_ticks")),
			new Group("fire", Side.LEFT, "muzzle", List.of("fire_modes", "rpm", "damage", "range")),
			new Group("handling", Side.LEFT, "trigger", List.of("feel.sway_gain", "feel.sway_max_deg", "feel.sway_follow", "feel.bob_scale")),
			// No anchor bone: wired to the back of the slide.
			new Group("recoil", Side.RIGHT, null, List.of("recoil_pitch_deg", "recoil_yaw_deg", "feel.recoil_push_back", "feel.recoil_spring_stiffness",
					"feel.recoil_spring_damping", "feel.recoil_max_kicks")));

	private static final Map<String, Range> RANGES = Map.ofEntries(
			Map.entry("rpm", new Range(60, 1200, 10)),
			Map.entry("damage", new Range(0, 40, 0.5)),
			Map.entry("range", new Range(8, 256, 1)),
			Map.entry("mag_size", new Range(1, 50, 1)),
			Map.entry("draw_ticks", new Range(0, 40, 1)),
			Map.entry("reload_ticks", new Range(5, 100, 1)),
			Map.entry("reload_empty_ticks", new Range(5, 120, 1)),
			Map.entry("spread_hip_deg", new Range(0, 10, 0.05)),
			Map.entry("spread_ads_deg", new Range(0, 5, 0.05)),
			Map.entry("ads_ticks", new Range(0, 20, 1)),
			Map.entry("ads_fov_multiplier", new Range(0.1, 1, 0.01)),
			Map.entry("move_speed_ads_multiplier", new Range(0, 1, 0.05)),
			Map.entry("recoil_pitch_deg", new Range(0, 10, 0.1)),
			Map.entry("recoil_yaw_deg", new Range(0, 5, 0.1)),
			Map.entry("feel.recoil_push_back", new Range(0, 0.2, 0.005)),
			Map.entry("feel.recoil_spring_stiffness", new Range(0.01, 1, 0.01)),
			Map.entry("feel.recoil_spring_damping", new Range(0, 1, 0.01)),
			Map.entry("feel.recoil_max_kicks", new Range(0, 10, 0.1)),
			Map.entry("feel.sway_gain", new Range(0, 0.2, 0.001)),
			Map.entry("feel.sway_max_deg", new Range(0, 5, 0.005)),
			Map.entry("feel.sway_follow", new Range(0.01, 1, 0.01)),
			Map.entry("feel.bob_scale", new Range(0, 2, 0.05)));

	private final Identifier id;
	private final Component gunName;
	private final List<Panel> panels = new ArrayList<>();
	private final List<EditBox> textFields = new ArrayList<>();
	/** Last definition seen in {@link GunDefinitions}; a new one (from Apply, Reset or another op) refills the widgets. */
	private GunDefinition synced;
	/** The values being edited, as the definition json. Every widget writes into it straight away. */
	private JsonObject working;
	private @Nullable Dropdown openDropdown;
	// Panel metrics, compact on short screens (1080p at the default GUI scale is only 270 GUI pixels tall).
	private int panelWidth;
	private int header;
	private int row;
	private int fieldHeight;

	private record Panel(Component title, Side side, int x, int y, int height, int anchorX, int anchorY) {
	}

	public GunTunerScreen(Identifier id, GunDefinition definition, Component gunName) {
		super(Component.translatable("gui.mcweapons.tuner.title", gunName));
		this.id = id;
		this.gunName = gunName;
		this.synced = definition;
		this.working = values(definition);
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
		panels.clear();
		textFields.clear();
		openDropdown = null;
		boolean compact = height < 360;
		panelWidth = compact ? 112 : 128;
		header = compact ? 11 : 13;
		row = compact ? 12 : 16;
		fieldHeight = compact ? 11 : 14;

		List<Group> groups = new ArrayList<>(GROUPS);
		// Values no group knows about (a newer definition field) still get a panel instead of vanishing.
		Set<String> other = new LinkedHashSet<>(paths(working));
		GROUPS.forEach(group -> group.paths().forEach(other::remove));
		if (!other.isEmpty()) {
			groups.add(new Group("other", Side.RIGHT, null, List.copyOf(other)));
		}
		int[] sideWidth = new int[Side.values().length];
		int[] sideHeight = new int[Side.values().length];
		for (Group group : groups) {
			int side = group.side().ordinal();
			sideWidth[side] = panelWidth + GAP;
			sideHeight[side] += (sideHeight[side] > 0 ? GAP : 0) + panelHeight(group);
		}

		GunRenderer renderer = renderer();
		float[] bounds = renderer != null ? renderer.gunBounds() : null;
		Matrix4f showcase = renderer != null ? fitShowcase(renderer, bounds, sideWidth, sideHeight) : null;
		float[] box = showcase != null ? screenBox(showcase, bounds) : new float[] {width / 3F, height / 3F, width * 2 / 3F, height * 2 / 3F};

		// Side order puts the top and bottom panels first; a side column that would overlap them moves out to the screen edge.
		for (Side side : Side.values()) {
			List<Group> stack = groups.stream().filter(group -> group.side() == side).toList();
			if (stack.isEmpty()) {
				continue;
			}
			int[] firstAnchor = anchor(stack.getFirst(), renderer, showcase, bounds, box);
			int total = sideHeight[side.ordinal()];
			int x = switch (side) {
				case TOP, BOTTOM -> Mth.clamp(firstAnchor[0] - panelWidth / 2, MARGIN, width - MARGIN - panelWidth);
				case LEFT -> Math.max(MARGIN, Math.round(box[0]) - GAP - panelWidth);
				case RIGHT -> Math.min(width - MARGIN - panelWidth, Math.round(box[2]) + GAP);
			};
			int y = switch (side) {
				case TOP -> Math.max(MARGIN, Math.round(box[1]) - GAP - total);
				case BOTTOM -> Math.min(height - MARGIN - total, Math.round(box[3]) + GAP);
				case LEFT, RIGHT -> Mth.clamp(firstAnchor[1] - panelHeight(stack.getFirst()) / 2, MARGIN + CORNER, Math.max(MARGIN + CORNER, height - MARGIN - CORNER - total));
			};
			if ((side == Side.LEFT || side == Side.RIGHT) && overlapsPanel(x, y, total)) {
				x = side == Side.LEFT ? MARGIN : width - MARGIN - panelWidth;
			}
			for (Group group : stack) {
				int[] anchor = anchor(group, renderer, showcase, bounds, box);
				panels.add(new Panel(Component.translatable("gui.mcweapons.tuner.group." + group.name()), side, x, y, panelHeight(group), anchor[0], anchor[1]));
				for (int i = 0; i < group.paths().size(); i++) {
					addField(group.paths().get(i), x + 4, y + header + 1 + i * row);
				}
				y += panelHeight(group) + GAP;
			}
		}

		// The corners are free in a plus: buttons top right and bottom left.
		int right = width - MARGIN;
		addRenderableWidget(button("done", right - 44, MARGIN, 44, this::onClose));
		addRenderableWidget(button("save", right - 92, MARGIN, 44, () -> send(TuneGunPayload.Action.SAVE, "gui.mcweapons.tuner.saved")));
		addRenderableWidget(button("apply", right - 140, MARGIN, 44, () -> send(TuneGunPayload.Action.APPLY, "gui.mcweapons.tuner.applied")));
		int bottom = height - MARGIN - BUTTON_HEIGHT;
		addRenderableWidget(button("copy", MARGIN, bottom, 60, this::copy));
		addRenderableWidget(button("reset", MARGIN + 64, bottom, 44, this::confirmReset));
	}

	private boolean overlapsPanel(int x, int y, int columnHeight) {
		return panels.stream().anyMatch(panel -> x < panel.x() + panelWidth && panel.x() < x + panelWidth && y < panel.y() + panel.height() && panel.y() < y + columnHeight);
	}

	private int panelHeight(Group group) {
		return header + group.paths().size() * row + 2;
	}

	/** One widget per value: a slider for numbers, a toggle for on/off, a dropdown for the fire modes, a text box for anything else. */
	private void addField(String path, int x, int y) {
		JsonElement value = get(path);
		Component label = Component.translatableWithFallback("gui.mcweapons.tuner.field." + key(path), key(path));
		int fieldWidth = panelWidth - 8;
		Range range = RANGES.get(path);
		AbstractWidget widget;
		if (path.equals("fire_modes")) {
			widget = new Dropdown(x, y, fieldWidth, label, path, text(value));
		} else if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) {
			widget = CycleButton.onOffBuilder(value.getAsBoolean()).create(x, y, fieldWidth, fieldHeight, label, (button, on) -> set(path, new JsonPrimitive(on)));
		} else if (range != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
			widget = new NumberSlider(x, y, fieldWidth, label, path, range, value.getAsDouble());
		} else {
			EditBox box = new EditBox(font, x, y, fieldWidth, fieldHeight, label);
			box.setMaxLength(64);
			box.setValue(text(value));
			box.setResponder(text -> {
				JsonElement typed = value.isJsonArray() ? list(text) : value(text);
				JsonObject attempt = working.deepCopy();
				holder(attempt, path).add(key(path), typed);
				boolean valid = GunDefinition.CODEC.parse(JsonOps.INSTANCE, attempt).isSuccess();
				box.setTextColor(valid ? TEXT : INVALID_COLOR);
				set(path, typed);
			});
			textFields.add(box);
			widget = box;
		}
		widget.setTooltip(Tooltip.create(Component.literal(path)));
		addRenderableWidget(widget);
	}

	private Button button(String name, int x, int y, int width, Runnable action) {
		Component label = name.equals("done") ? CommonComponents.GUI_DONE : Component.translatable("gui.mcweapons.tuner." + name);
		return Button.builder(label, button -> action.run()).bounds(x, y, width, BUTTON_HEIGHT).build();
	}

	private static @Nullable GunRenderer renderer() {
		LocalPlayer player = Minecraft.getInstance().player;
		return player != null && player.getMainHandItem().getItem() instanceof GunItem gun
				&& GeoRenderProvider.of(gun).getGeoItemRenderer() instanceof GunRenderer renderer ? renderer : null;
	}

	/**
	 * Picks the showcase distance that leaves room for the panels on every side, so the gun is as big as the screen allows, and returns
	 * the showcase transform at that distance. Size goes roughly as 1/distance, so a few rounds settle it. It stays at least a block
	 * away, where the hands holding it (nearer the eye) do not fill the screen.
	 */
	private Matrix4f fitShowcase(GunRenderer renderer, float[] bounds, int[] sideWidth, int[] sideHeight) {
		float freeWidth = width - 2 * MARGIN - sideWidth[Side.LEFT.ordinal()] - sideWidth[Side.RIGHT.ordinal()];
		float freeHeight = height - 2 * MARGIN - sideHeight[Side.TOP.ordinal()] - sideHeight[Side.BOTTOM.ordinal()] - 2 * GAP;
		float distance = 1;
		for (int i = 0; i < 3; i++) {
			Viewmodel.setShowcaseDistance(distance);
			float[] box = screenBox(Viewmodel.showcaseTransform(renderer, 1), bounds);
			float scale = Math.min(Math.max(freeWidth, 40) / (box[2] - box[0]), Math.max(freeHeight, 30) / (box[3] - box[1]));
			distance = Mth.clamp(distance / scale, 1, 4);
		}
		Viewmodel.setShowcaseDistance(distance);
		return Viewmodel.showcaseTransform(renderer, 1);
	}

	/** Screen box (x0, y0, x1, y1 in GUI pixels) around the showcased gun. */
	private float[] screenBox(Matrix4f showcase, float[] bounds) {
		float[] box = {Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
		for (int corner = 0; corner < 8; corner++) {
			float[] point = project(showcase, new Vector3f(bounds[(corner & 1) * 3], bounds[1 + (corner >> 1 & 1) * 3], bounds[2 + (corner >> 2 & 1) * 3]));
			box[0] = Math.min(box[0], point[0]);
			box[1] = Math.min(box[1], point[1]);
			box[2] = Math.max(box[2], point[0]);
			box[3] = Math.max(box[3], point[1]);
		}
		return box;
	}

	/** Where a panel's wire meets the gun: its anchor bone, or the back of the slide. */
	private int[] anchor(Group group, @Nullable GunRenderer renderer, @Nullable Matrix4f showcase, float @Nullable [] bounds, float[] box) {
		if (renderer == null || showcase == null || bounds == null) {
			return new int[] {Math.round((box[0] + box[2]) / 2), Math.round((box[1] + box[3]) / 2)};
		}
		Vector3f point = group.anchorBone() != null ? renderer.restPivot(group.anchorBone())
				: new Vector3f((bounds[0] + bounds[3]) / 2, renderer.restPivot("ads_anchor").y, bounds[5]);
		float[] screen = project(showcase, point);
		return new int[] {Math.round(screen[0]), Math.round(screen[1])};
	}

	/** GUI position of a model-space point under the showcase. The hand is drawn with the fixed HUD field of view. */
	private float[] project(Matrix4f showcase, Vector3f modelPoint) {
		Vector3f view = showcase.transformPosition(modelPoint, new Vector3f());
		float focal = 1 / (float)Math.tan(Math.toRadians(Camera.BASE_HUD_FOV / 2));
		float depth = Math.max(-view.z, 0.01F);
		float ndcX = view.x * focal * height / width / depth;
		float ndcY = view.y * focal / depth;
		return new float[] {(ndcX + 1) / 2 * width, (1 - ndcY) / 2 * height};
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
			int x1 = x0 + panelWidth;
			int y1 = y0 + panel.height();
			graphics.fill(x0, y0, x1, y1, PANEL);
			graphics.fill(x0, y0, x1, y0 + header, PANEL_HEADER);
			graphics.text(font, panel.title(), x0 + 5, y0 + (header - 8) / 2, ACCENT, false);
			wire(graphics, panel);
		}
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		if (openDropdown != null) {
			openDropdown.extractList(graphics, mouseX, mouseY);
		}
	}

	/** The lit edge of a panel and a wire from it to the part of the gun it is about, ending in a dot. */
	private void wire(GuiGraphicsExtractor graphics, Panel panel) {
		int x0 = panel.x();
		int y0 = panel.y();
		int x1 = x0 + panelWidth;
		int y1 = y0 + panel.height();
		int ax = panel.anchorX();
		int ay = panel.anchorY();
		switch (panel.side()) {
			case TOP, BOTTOM -> {
				int edge = panel.side() == Side.TOP ? y1 : y0;
				int sx = Mth.clamp(ax, x0 + 4, x1 - 4);
				graphics.fill(x0, edge - (panel.side() == Side.TOP ? 1 : 0), x1, edge + (panel.side() == Side.TOP ? 0 : 1), ACCENT);
				graphics.fill(sx, Math.min(edge, ay), sx + 1, Math.max(edge, ay), ACCENT);
				graphics.fill(Math.min(sx, ax), ay, Math.max(sx, ax) + 1, ay + 1, ACCENT);
			}
			case LEFT, RIGHT -> {
				int edge = panel.side() == Side.LEFT ? x1 : x0;
				int sy = Mth.clamp(ay, y0 + header / 2, y1 - 4);
				graphics.fill(edge - (panel.side() == Side.LEFT ? 1 : 0), y0, edge + (panel.side() == Side.LEFT ? 0 : 1), y1, ACCENT);
				graphics.fill(Math.min(edge, ax), sy, Math.max(edge, ax), sy + 1, ACCENT);
				graphics.fill(ax, Math.min(sy, ay), ax + 1, Math.max(sy, ay) + 1, ACCENT);
			}
		}
		graphics.fill(ax - 1, ay - 1, ax + 2, ay + 2, ACCENT);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		// An open dropdown sits above everything, so it takes the click first; a click anywhere else closes it.
		if (openDropdown != null) {
			Dropdown dropdown = openDropdown;
			int option = dropdown.optionAt(event.x(), event.y());
			if (option >= 0) {
				dropdown.select(option);
				return true;
			}
			if (!dropdown.isMouseOver(event.x(), event.y())) {
				openDropdown = null;
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public void tick() {
		GunDefinition current = GunDefinitions.get(id);
		if (current != null && current != synced) {
			synced = current;
			working = values(current);
			init(width, height);
		}
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		// The tuner key closes it again, unless it is being typed into a text box.
		if (GunInput.TUNER.matches(event) && textFields.stream().noneMatch(EditBox::isFocused)) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	private void send(TuneGunPayload.Action action, String doneKey) {
		GunDefinition tuned = parsed();
		if (tuned != null) {
			ClientPlayNetworking.send(new TuneGunPayload(id, tuned, action));
			toast(Component.translatable(doneKey, gunName));
		}
	}

	private void copy() {
		GunDefinition tuned = parsed();
		if (tuned != null) {
			minecraft.keyboardHandler.setClipboard(GunDefinitions.toJsonText(id, tuned));
			toast(Component.translatable("gui.mcweapons.tuner.copied"));
		}
	}

	private void confirmReset() {
		minecraft.gui.setScreen(new ConfirmScreen(yes -> {
			if (yes) {
				ClientPlayNetworking.send(new TuneGunPayload(id, synced, TuneGunPayload.Action.RESET));
			}
			minecraft.gui.setScreen(this);
		}, Component.translatable("gui.mcweapons.tuner.reset.title", gunName), Component.translatable("gui.mcweapons.tuner.reset.message")));
	}

	/** The working values as a definition, or null after showing why not. */
	private @Nullable GunDefinition parsed() {
		DataResult<GunDefinition> result = GunDefinition.CODEC.parse(JsonOps.INSTANCE, working);
		result.ifError(error -> toast(Component.translatable("gui.mcweapons.tuner.invalid", error.message())));
		return result.result().orElse(null);
	}

	private void toast(Component message) {
		SystemToast.addOrUpdate(minecraft.gui.toastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION, Component.translatable("gui.mcweapons.tuner"), message);
	}

	private JsonElement get(String path) {
		return holder(working, path).get(key(path));
	}

	private void set(String path, JsonElement value) {
		holder(working, path).add(key(path), value);
	}

	private static JsonObject holder(JsonObject json, String path) {
		int dot = path.indexOf('.');
		return dot < 0 ? json : json.getAsJsonObject(path.substring(0, dot));
	}

	private static String key(String path) {
		return path.substring(path.indexOf('.') + 1);
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

	/** A number on a slider, snapped to its step. Drag or click it, or use the arrow keys for one step at a time. */
	private final class NumberSlider extends AbstractSliderButton {
		private final Component label;
		private final String path;
		private final double min;
		private final double max;
		private final double step;
		private final int decimals;

		NumberSlider(int x, int y, int width, Component label, String path, Range range, double current) {
			super(x, y, width, fieldHeight, Component.empty(), 0);
			this.label = label;
			this.path = path;
			this.step = range.step();
			// A value already outside the usual range widens it rather than being clamped.
			this.min = Math.min(range.min(), current);
			this.max = Math.max(range.max(), current);
			this.decimals = Math.max(0, BigDecimal.valueOf(range.step()).stripTrailingZeros().scale());
			this.value = (current - this.min) / (this.max - this.min);
			updateMessage();
		}

		private BigDecimal number() {
			double snapped = Math.min(max, min + Math.round(value * (max - min) / step) * step);
			return BigDecimal.valueOf(snapped).setScale(decimals, RoundingMode.HALF_UP);
		}

		@Override
		protected void updateMessage() {
			String unit = path.endsWith("_deg") ? "°" : path.endsWith("_ticks") ? "t" : "";
			setMessage(Component.empty().append(label).append(": " + number().toPlainString() + unit));
		}

		@Override
		protected void applyValue() {
			set(path, new JsonPrimitive(number()));
		}

		@Override
		public boolean keyPressed(KeyEvent event) {
			if (event.isLeft() || event.isRight()) {
				double next = Mth.clamp(number().doubleValue() + (event.isLeft() ? -step : step), min, max);
				setValue((next - min) / (max - min));
				return true;
			}
			return super.keyPressed(event);
		}
	}

	/** A value picked from a short list that opens under it. The open list is drawn and clicked by the screen, above everything. */
	private final class Dropdown extends AbstractWidget {
		private final Component label;
		private final String path;
		private final List<String> options;
		private String current;

		Dropdown(int x, int y, int width, Component label, String path, String current) {
			super(x, y, width, fieldHeight, label);
			this.label = label;
			this.path = path;
			this.current = current;
			List<String> options = new ArrayList<>(FIRE_MODES);
			if (!options.contains(current)) {
				options.add(current);
			}
			this.options = List.copyOf(options);
		}

		@Override
		public void onClick(MouseButtonEvent event, boolean doubleClick) {
			openDropdown = openDropdown == this ? null : this;
		}

		int optionAt(double mouseX, double mouseY) {
			int index = (int)Math.floor((mouseY - getY() - height) / fieldHeight);
			return mouseX >= getX() && mouseX < getX() + width && mouseY >= getY() + height && index < options.size() ? index : -1;
		}

		void select(int index) {
			current = options.get(index);
			set(path, list(current));
			openDropdown = null;
		}

		@Override
		protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
			graphics.fill(getX(), getY(), getX() + width, getY() + height, isHoveredOrFocused() || openDropdown == this ? FIELD_HOVER : FIELD);
			graphics.outline(getX(), getY(), width, height, openDropdown == this ? ACCENT : 0xFF3A404A);
			int textY = getY() + (height - 8) / 2;
			graphics.text(font, font.plainSubstrByWidth(label.getString() + ": " + current, width - 16), getX() + 4, textY, TEXT, false);
			graphics.text(font, openDropdown == this ? "▲" : "▼", getX() + width - 10, textY, ACCENT, false);
		}

		void extractList(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
			int top = getY() + height;
			graphics.fill(getX(), top, getX() + width, top + options.size() * fieldHeight, 0xFF0A0C10);
			graphics.outline(getX(), top, width, options.size() * fieldHeight, ACCENT);
			int hovered = optionAt(mouseX, mouseY);
			for (int i = 0; i < options.size(); i++) {
				int y = top + i * fieldHeight;
				if (i == hovered) {
					graphics.fill(getX() + 1, y, getX() + width - 1, y + fieldHeight, FIELD_HOVER);
				}
				graphics.text(font, options.get(i), getX() + 6, y + (fieldHeight - 8) / 2, options.get(i).equals(current) ? ACCENT : TEXT, false);
			}
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			defaultButtonNarrationText(output);
		}
	}
}

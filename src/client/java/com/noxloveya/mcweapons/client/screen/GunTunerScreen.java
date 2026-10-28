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
import com.noxloveya.mcweapons.McWeapons;
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
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
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
 * Ops-only tuner for the held gun's definition, opened and closed with the tuner key. While it is open the right hand holds the gun up
 * sideways, and the values sit in panels around it like the arms of a plus, each wired to the part it is about. Apply uses the values
 * live for everyone, Save also writes them into the world, Reset goes back to the gun's own file.
 * <p>
 * Drawn flat and monochrome in the mod's own fonts (Inter for text, Lucide for the white icons) rather than vanilla's widgets.
 */
public class GunTunerScreen extends Screen {
	// Font glyphs are sampled without smoothing, so they only look clean when rasterized at exactly the GUI scale: each font comes in
	// variants _x1 to _x10 with that oversample, and init() picks the one for the current scale.
	private static final int MAX_FONT_SCALE = 10;
	private static FontDescription uiFont = fontAt("ui", 2);
	private static FontDescription uiMedium = fontAt("ui_medium", 2);
	private static FontDescription iconFont = fontAt("icons", 2);

	// Lucide codepoints (font/lucide.ttf).
	private static final int ICON_TUNER = 0xE29A;
	private static final int ICON_AIM = 0xE0AC;
	private static final int ICON_FIRE = 0xE0D2;
	private static final int ICON_HANDLING = 0xE1D7;
	private static final int ICON_RECOIL = 0xE074;
	private static final int ICON_MAGAZINE = 0xE58A;
	private static final int ICON_OTHER = 0xE0B6;
	private static final int ICON_APPLY = 0xE06C;
	private static final int ICON_SAVE = 0xE14D;
	private static final int ICON_COPY = 0xE09E;
	private static final int ICON_PASTE = 0xE3E8;
	private static final int ICON_RESET = 0xE148;
	private static final int ICON_CLOSE = 0xE1B2;
	private static final int ICON_DOWN = 0xE06D;
	private static final int ICON_UP = 0xE070;
	private static final int ICON_OK = 0xE226;
	private static final int ICON_ERROR = 0xE077;
	private static final int ICON_KEYS = 0xE24A;

	static final int PANEL = 0xD90C0E12;
	static final int CARD = 0xF50C0E12;
	static final int EDGE = 0x1AFFFFFF;
	static final int DIVIDER = 0x14FFFFFF;
	static final int ROW_HOVER = 0x0FFFFFFF;
	private static final int ROW_FOCUS = 0x1FFFFFFF;
	private static final int TRACK = 0x26FFFFFF;
	private static final int FILL = 0xB3FFFFFF;
	static final int TEXT = 0xFFF4F5F7;
	static final int LABEL = 0xB3FFFFFF;
	static final int MUTED = 0x73FFFFFF;
	private static final int WIRE = 0x59FFFFFF;
	static final int DARK = 0xFF0C0E12;
	static final int DANGER = 0xFFFF6B6B;
	static final int MARGIN = 6;
	private static final int CORNER = 24; // rows kept free at the top and bottom for the title and the buttons
	private static final int GAP = 10; // between a panel and the gun
	private static final int BUTTON_HEIGHT = 16;
	private static final long TOOLTIP_DELAY = 600;
	private static final List<String> FIRE_MODES = List.of("semi", "auto", "semi, auto");

	/** Which arm of the plus a panel sits on. The gun shows its left side, so the muzzle points left. */
	private enum Side { TOP, BOTTOM, LEFT, RIGHT }

	/** A titled panel of json values, by path ("rpm", or "feel.sway_gain" inside the feel object), wired to a bone of the gun. */
	private record Group(String name, int icon, Side side, @Nullable String anchorBone, List<String> paths) {
	}

	/** Slider range and step of a number. Ranges stay inside what the definition codec accepts. */
	private record Range(double min, double max, double step) {
	}

	private static final List<Group> GROUPS = List.of(
			new Group("aim", ICON_AIM, Side.TOP, "ads_anchor", List.of("spread_hip_deg", "spread_ads_deg", "ads_ticks", "ads_fov_multiplier", "move_speed_ads_multiplier")),
			new Group("magazine", ICON_MAGAZINE, Side.BOTTOM, "magazine", List.of("mag_size", "chamber", "draw_ticks", "reload_ticks", "reload_empty_ticks")),
			new Group("fire", ICON_FIRE, Side.LEFT, "muzzle", List.of("fire_modes", "rpm", "damage", "headshot_multiplier", "range")),
			new Group("handling", ICON_HANDLING, Side.LEFT, "trigger", List.of("feel.sway_gain", "feel.sway_max_deg", "feel.sway_follow", "feel.bob_scale", "feel.breath_scale")),
			// No anchor bone: wired to the back of the slide.
			new Group("recoil", ICON_RECOIL, Side.RIGHT, null, List.of("recoil_pitch_deg", "recoil_yaw_deg", "feel.recoil_push_back", "feel.recoil_spring_stiffness",
					"feel.recoil_spring_damping", "feel.recoil_max_kicks")));

	private static final Map<String, Range> RANGES = Map.ofEntries(
			Map.entry("rpm", new Range(60, 1200, 10)),
			Map.entry("damage", new Range(0, 40, 0.5)),
			Map.entry("headshot_multiplier", new Range(1, 5, 0.1)),
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
			Map.entry("feel.bob_scale", new Range(0, 2, 0.05)),
			Map.entry("feel.breath_scale", new Range(0, 3, 0.05)));

	private final Identifier id;
	private final Component gunName;
	private final List<Panel> panels = new ArrayList<>();
	private final List<Field> fields = new ArrayList<>();
	private final List<EditBox> textFields = new ArrayList<>();
	/** Last definition seen in {@link GunDefinitions}; a new one (from Apply, Reset or another op) refills the widgets. */
	private GunDefinition synced;
	/** The values being edited, as the definition json. Every widget writes into it straight away. */
	private JsonObject working;
	private @Nullable Dropdown openDropdown;
	/** False until the gun has settled in the hand and the panels have been placed around it. */
	private boolean laidOut;
	private boolean confirmingReset;
	/** The tuner key opened this screen; it only closes it again once that press has been let go. */
	private boolean tunerKeyReleased;
	/** Right edge of the Copy, Apply JSON and Reset row, so the status card can keep clear of it. */
	private int bottomButtonsRight;
	private @Nullable Component status;
	private boolean statusError;
	private long statusUntil;
	private @Nullable Field hoveredField;
	private long hoveredSince;
	// Panel metrics, compact on short screens (1080p at the default GUI scale is only 270 GUI pixels tall).
	private int panelWidth;
	private int header;
	private int row;
	private int fieldHeight;

	private record Panel(Component title, int icon, Side side, int x, int y, int height, int anchorX, int anchorY) {
	}

	/** A value's widget and its json path, for the hover hint. */
	private record Field(AbstractWidget widget, String path) {
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
		useFontScale(minecraft);
		panels.clear();
		fields.clear();
		textFields.clear();
		openDropdown = null;
		hoveredField = null;
		boolean compact = height < 360;
		panelWidth = compact ? 116 : 132;
		header = compact ? 14 : 16;
		row = compact ? 13 : 16;
		fieldHeight = compact ? 12 : 14;

		List<Group> groups = new ArrayList<>(GROUPS);
		// Values no group knows about (a newer definition field) still get a panel instead of vanishing.
		Set<String> other = new LinkedHashSet<>(paths(working));
		GROUPS.forEach(group -> group.paths().forEach(other::remove));
		if (!other.isEmpty()) {
			groups.add(new Group("other", ICON_OTHER, Side.RIGHT, null, List.copyOf(other)));
		}
		int[] sideHeight = new int[Side.values().length];
		for (Group group : groups) {
			int side = group.side().ordinal();
			sideHeight[side] += (sideHeight[side] > 0 ? GAP : 0) + panelHeight(group);
		}

		// The corners are free in a plus: actions top right, file tools bottom left.
		int x = width - MARGIN - BUTTON_HEIGHT;
		addRenderableWidget(new FlatButton(x, MARGIN, CommonComponents.GUI_DONE, ICON_CLOSE, ButtonStyle.ICON, this::onClose));
		x = addLeftOf(x, Component.translatable("gui.mcweapons.tuner.save"), ICON_SAVE, ButtonStyle.GHOST, () -> send(TuneGunPayload.Action.SAVE, "gui.mcweapons.tuner.saved"));
		addLeftOf(x, Component.translatable("gui.mcweapons.tuner.apply"), ICON_APPLY, ButtonStyle.PRIMARY, () -> send(TuneGunPayload.Action.APPLY, "gui.mcweapons.tuner.applied"));
		int bottom = height - MARGIN - BUTTON_HEIGHT;
		FlatButton copy = addRenderableWidget(new FlatButton(MARGIN, bottom, Component.translatable("gui.mcweapons.tuner.copy"), ICON_COPY, ButtonStyle.GHOST, this::copy));
		FlatButton paste = addRenderableWidget(new FlatButton(copy.getX() + copy.getWidth() + 4, bottom, Component.translatable("gui.mcweapons.tuner.paste"), ICON_PASTE,
				ButtonStyle.GHOST, this::applyClipboard));
		FlatButton reset = addRenderableWidget(new FlatButton(paste.getX() + paste.getWidth() + 4, bottom, Component.translatable("gui.mcweapons.tuner.reset"), ICON_RESET,
				ButtonStyle.DANGER, () -> confirmingReset = true));
		bottomButtonsRight = reset.getX() + reset.getWidth();

		// The panels go around the gun where the hand actually holds it, so they wait until the showcase animation has settled.
		GunRenderer renderer = renderer();
		Matrix4f showcase = Viewmodel.showcaseGunPose();
		laidOut = renderer != null && showcase != null;
		if (!laidOut) {
			return;
		}
		float[] bounds = renderer.gunBounds();
		float[] box = screenBox(showcase, bounds);

		// The side columns go first, so a bottom panel with no room under the gun (it is held up big) can move under one of them.
		for (Side side : List.of(Side.LEFT, Side.RIGHT, Side.TOP, Side.BOTTOM)) {
			List<Group> stack = groups.stream().filter(group -> group.side() == side).toList();
			if (stack.isEmpty()) {
				continue;
			}
			int[] firstAnchor = anchor(stack.getFirst(), renderer, showcase, bounds, box);
			int total = sideHeight[side.ordinal()];
			Side wiredFrom = side;
			int px = switch (side) {
				case TOP, BOTTOM -> Mth.clamp(firstAnchor[0] - panelWidth / 2, MARGIN, width - MARGIN - panelWidth);
				case LEFT -> Math.max(MARGIN, Math.round(box[0]) - GAP - panelWidth);
				case RIGHT -> Math.min(width - MARGIN - panelWidth, Math.round(box[2]) + GAP);
			};
			int py = switch (side) {
				case TOP -> Math.max(MARGIN, Math.round(box[1]) - GAP - total);
				case BOTTOM -> Math.min(height - MARGIN - total, Math.round(box[3]) + GAP);
				case LEFT, RIGHT -> Mth.clamp(firstAnchor[1] - panelHeight(stack.getFirst()) / 2, MARGIN + CORNER, Math.max(MARGIN + CORNER, height - MARGIN - CORNER - total));
			};
			if ((side == Side.LEFT || side == Side.RIGHT) && overlapsPanel(px, py, total)) {
				px = side == Side.LEFT ? MARGIN : width - MARGIN - panelWidth;
			}
			if (side == Side.BOTTOM && Math.round(box[3]) + GAP + total > height - MARGIN) {
				for (Side column : List.of(Side.RIGHT, Side.LEFT)) {
					int columnX = panels.stream().filter(panel -> panel.side() == column).mapToInt(Panel::x).findFirst().orElse(-1);
					int under = panels.stream().filter(panel -> panel.side() == column).mapToInt(panel -> panel.y() + panel.height()).max().orElse(-1) + GAP;
					// The bottom left corner holds the Copy and Reset buttons.
					int floor = height - MARGIN - (column == Side.LEFT ? BUTTON_HEIGHT + GAP : 0);
					if (columnX >= 0 && under + total <= floor) {
						px = columnX;
						py = under;
						wiredFrom = column;
						break;
					}
				}
			}
			for (Group group : stack) {
				int[] anchor = anchor(group, renderer, showcase, bounds, box);
				panels.add(new Panel(Component.translatable("gui.mcweapons.tuner.group." + group.name()), group.icon(), wiredFrom, px, py, panelHeight(group), anchor[0], anchor[1]));
				for (int i = 0; i < group.paths().size(); i++) {
					addField(group.paths().get(i), px + 3, py + header + 2 + i * row);
				}
				py += panelHeight(group) + GAP;
			}
		}
	}

	/** Adds a button ending just left of {@code right}, and returns where the next one should end. */
	private int addLeftOf(int right, Component label, int icon, ButtonStyle style, Runnable action) {
		FlatButton button = new FlatButton(0, MARGIN, label, icon, style, action);
		button.setX(right - 4 - button.getWidth());
		addRenderableWidget(button);
		return button.getX();
	}

	private boolean overlapsPanel(int x, int y, int columnHeight) {
		return panels.stream().anyMatch(panel -> x < panel.x() + panelWidth && panel.x() < x + panelWidth && y < panel.y() + panel.height() && panel.y() < y + columnHeight);
	}

	private int panelHeight(Group group) {
		return header + 2 + group.paths().size() * row + 3;
	}

	/** One widget per value: a slider for numbers, a switch for on/off, a dropdown for the fire modes, a text box for anything else. */
	private void addField(String path, int x, int y) {
		JsonElement value = get(path);
		Component label = ui(Component.translatableWithFallback("gui.mcweapons.tuner.field." + key(path), key(path)));
		int fieldWidth = panelWidth - 6;
		Range range = RANGES.get(path);
		AbstractWidget widget;
		if (path.equals("fire_modes")) {
			widget = new Dropdown(x, y, fieldWidth, label, path, text(value));
		} else if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) {
			widget = new Toggle(x, y, fieldWidth, label, path, value.getAsBoolean());
		} else if (range != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
			widget = new NumberSlider(x, y, fieldWidth, label, path, range, value.getAsDouble());
		} else {
			EditBox box = new EditBox(font, x + 4, y + (fieldHeight - 8) / 2, fieldWidth - 8, fieldHeight, label);
			box.setBordered(false);
			box.setMaxLength(64);
			box.setValue(text(value));
			box.setResponder(text -> {
				JsonElement typed = value.isJsonArray() ? list(text) : value(text);
				JsonObject attempt = working.deepCopy();
				holder(attempt, path).add(key(path), typed);
				boolean valid = GunDefinition.CODEC.parse(JsonOps.INSTANCE, attempt).isSuccess();
				box.setTextColor(valid ? TEXT : DANGER);
				set(path, typed);
			});
			textFields.add(box);
			widget = box;
		}
		fields.add(new Field(widget, path));
		addRenderableWidget(widget);
	}

	private static @Nullable GunRenderer renderer() {
		LocalPlayer player = Minecraft.getInstance().player;
		return player != null && player.getMainHandItem().getItem() instanceof GunItem gun
				&& GeoRenderProvider.of(gun).getGeoItemRenderer() instanceof GunRenderer renderer ? renderer : null;
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
	private int[] anchor(Group group, GunRenderer renderer, Matrix4f showcase, float[] bounds, float[] box) {
		Vector3f point = group.anchorBone() != null ? renderer.restPivot(group.anchorBone())
				: new Vector3f((bounds[0] + bounds[3]) / 2, renderer.restPivot("ads_anchor").y, bounds[5]);
		float[] screen = project(showcase, point);
		return new int[] {Math.round(screen[0]), Math.round(screen[1])};
	}

	/** GUI position of a gun model-space point under its drawn pose. The hand is drawn with the fixed HUD field of view. */
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
		// While the confirm card is up, nothing underneath reacts to the mouse.
		int pointerX = confirmingReset ? -1 : mouseX;
		int pointerY = confirmingReset ? -1 : mouseY;

		graphics.text(font, icon(ICON_TUNER), MARGIN, MARGIN + 1, TEXT, false);
		graphics.text(font, medium(Component.translatable("gui.mcweapons.tuner")), MARGIN + 13, MARGIN + 1, TEXT, false);
		graphics.text(font, ui(gunName), MARGIN + 13, MARGIN + 12, MUTED, false);

		// The bottom right corner is the one the plus leaves free; on small screens a panel can reach it, and then the hint is left out.
		Component hint = ui(Component.translatable("gui.mcweapons.tuner.hint"));
		int hintX = width - MARGIN - font.width(hint);
		int hintY = height - MARGIN - 11;
		if (panels.stream().noneMatch(panel -> hintX - 14 < panel.x() + panelWidth && panel.y() + panel.height() > hintY)) {
			graphics.text(font, icon(ICON_KEYS), hintX - 13, hintY, MUTED, false);
			graphics.text(font, hint, hintX, hintY, MUTED, false);
		}

		for (Panel panel : panels) {
			wire(graphics, panel);
		}
		for (Panel panel : panels) {
			int x0 = panel.x();
			int y0 = panel.y();
			int x1 = x0 + panelWidth;
			int y1 = y0 + panel.height();
			rounded(graphics, x0, y0, x1, y1, PANEL);
			roundedOutline(graphics, x0, y0, x1, y1, EDGE);
			int titleY = y0 + (header - 8) / 2;
			graphics.text(font, icon(panel.icon()), x0 + 7, titleY, TEXT, false);
			graphics.text(font, medium(panel.title()), x0 + 20, titleY, TEXT, false);
			graphics.fill(x0 + 7, y0 + header, x1 - 7, y0 + header + 1, DIVIDER);
		}
		super.extractRenderState(graphics, pointerX, pointerY, partialTick);
		// Each overlay starts a new stratum: within one, text is drawn after every fill, so the rows' text would show through.
		graphics.nextStratum();
		if (openDropdown != null) {
			openDropdown.extractList(graphics, pointerX, pointerY);
		} else {
			hoverHint(graphics, pointerX, pointerY);
		}
		extractStatus(graphics);
		if (confirmingReset) {
			graphics.nextStratum();
			extractConfirm(graphics, mouseX, mouseY);
		}
	}

	/** A thin line from the panel's edge to the part of the gun it is about, ending in a soft dot. */
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
				int sx = Mth.clamp(ax, x0 + 8, x1 - 8);
				graphics.fill(sx, Math.min(edge, ay), sx + 1, Math.max(edge, ay), WIRE);
				graphics.fill(Math.min(sx, ax), ay, Math.max(sx, ax) + 1, ay + 1, WIRE);
			}
			case LEFT, RIGHT -> {
				int edge = panel.side() == Side.LEFT ? x1 : x0;
				int sy = Mth.clamp(ay, y0 + header / 2, y1 - 6);
				graphics.fill(Math.min(edge, ax), sy, Math.max(edge, ax), sy + 1, WIRE);
				graphics.fill(ax, Math.min(sy, ay), ax + 1, Math.max(sy, ay) + 1, WIRE);
			}
		}
		graphics.fill(ax - 2, ay - 2, ax + 3, ay + 3, 0x40FFFFFF);
		graphics.fill(ax - 1, ay - 1, ax + 2, ay + 2, TEXT);
	}

	/** After a short hover: what the value under the mouse changes, with its name and json key. */
	private void hoverHint(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		Field hovered = fields.stream().filter(field -> field.widget().isMouseOver(mouseX, mouseY)).findFirst().orElse(null);
		long now = Util.getMillis();
		if (hovered != hoveredField) {
			hoveredField = hovered;
			hoveredSince = now;
		}
		if (hovered == null || now - hoveredSince < TOOLTIP_DELAY) {
			return;
		}
		String key = key(hovered.path());
		Component title = medium(Component.translatableWithFallback("gui.mcweapons.tuner.field." + key, key));
		// A value without a description (a newer definition field) still gets its name and key.
		String descriptionKey = "gui.mcweapons.tuner.field." + key + ".desc";
		List<FormattedCharSequence> lines = Language.getInstance().has(descriptionKey) ? font.split(ui(Component.translatable(descriptionKey)), 170) : List.of();
		Component path = ui(Component.literal(hovered.path()));
		int w = 10 + Math.max(Math.max(font.width(title), font.width(path)), lines.stream().mapToInt(font::width).max().orElse(0));
		int h = 8 + 10 + lines.size() * 10 + 4 + 10;
		int x = Math.min(mouseX + 8, width - MARGIN - w);
		int y = mouseY + 12 + h <= height - MARGIN ? mouseY + 12 : Math.max(MARGIN, mouseY - 4 - h);
		rounded(graphics, x, y, x + w, y + h, CARD);
		roundedOutline(graphics, x, y, x + w, y + h, EDGE);
		graphics.text(font, title, x + 5, y + 5, TEXT, false);
		for (int i = 0; i < lines.size(); i++) {
			graphics.text(font, lines.get(i), x + 5, y + 16 + i * 10, LABEL, false);
		}
		graphics.text(font, path, x + 5, y + h - 12, MUTED, false);
	}

	/** The last Apply, Save, Copy or error, as a small card at the bottom that fades out. */
	private void extractStatus(GuiGraphicsExtractor graphics) {
		long left = statusUntil - Util.getMillis();
		if (status == null || left <= 0) {
			return;
		}
		float alpha = Math.min(1, left / 300F);
		Component text = fit(ui(status), width / 2);
		Component mark = icon(statusError ? ICON_ERROR : ICON_OK);
		int w = font.width(text) + 26;
		// On the bottom line, centered or just right of the buttons; only when it cannot fit there, above them.
		int x = Math.max((width - w) / 2, bottomButtonsRight + 6);
		int y = height - MARGIN - 18;
		if (x + w > width - MARGIN) {
			x = (width - w) / 2;
			y -= BUTTON_HEIGHT + 4;
		}
		rounded(graphics, x, y, x + w, y + 18, ARGB.multiplyAlpha(CARD, alpha));
		roundedOutline(graphics, x, y, x + w, y + 18, ARGB.multiplyAlpha(statusError ? 0x66FF6B6B : EDGE, alpha));
		graphics.text(font, mark, x + 7, y + 5, ARGB.multiplyAlpha(statusError ? DANGER : TEXT, alpha), false);
		graphics.text(font, text, x + 20, y + 5, ARGB.multiplyAlpha(TEXT, alpha), false);
	}

	private void status(Component message, boolean error) {
		status = message;
		statusError = error;
		statusUntil = Util.getMillis() + (error ? 4500 : 2200);
	}

	/** Card bounds and its two buttons: x0, y0, x1, y1, cancel x0, cancel x1, reset x0, reset x1, buttons y0. */
	private int[] confirmLayout(List<FormattedCharSequence> body) {
		int cardWidth = 200;
		int cardHeight = 10 + 11 + body.size() * 10 + 10 + BUTTON_HEIGHT + 10;
		int x0 = (width - cardWidth) / 2;
		int y0 = (height - cardHeight) / 2;
		int buttonsY = y0 + cardHeight - 10 - BUTTON_HEIGHT;
		int resetWidth = FlatButton.widthOf(font, ui(Component.translatable("gui.mcweapons.tuner.reset")), true);
		int cancelWidth = FlatButton.widthOf(font, ui(Component.translatable("gui.mcweapons.tuner.cancel")), false);
		int resetX1 = x0 + cardWidth - 10;
		int resetX0 = resetX1 - resetWidth;
		int cancelX1 = resetX0 - 4;
		return new int[] {x0, y0, x0 + cardWidth, y0 + cardHeight, cancelX1 - cancelWidth, cancelX1, resetX0, resetX1, buttonsY};
	}

	private List<FormattedCharSequence> confirmBody() {
		return font.split(ui(Component.translatable("gui.mcweapons.tuner.reset.message")), 180);
	}

	/** Reset asks first, on a card over a dimmed screen instead of vanilla's confirm screen. */
	private void extractConfirm(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		List<FormattedCharSequence> body = confirmBody();
		int[] c = confirmLayout(body);
		graphics.fill(0, 0, width, height, 0x99000000);
		rounded(graphics, c[0], c[1], c[2], c[3], CARD);
		roundedOutline(graphics, c[0], c[1], c[2], c[3], EDGE);
		graphics.text(font, medium(Component.translatable("gui.mcweapons.tuner.reset.title", gunName)), c[0] + 10, c[1] + 10, TEXT, false);
		for (int i = 0; i < body.size(); i++) {
			graphics.text(font, body.get(i), c[0] + 10, c[1] + 23 + i * 10, LABEL, false);
		}
		boolean cancelHot = inside(mouseX, mouseY, c[4], c[8], c[5], c[8] + BUTTON_HEIGHT);
		boolean resetHot = inside(mouseX, mouseY, c[6], c[8], c[7], c[8] + BUTTON_HEIGHT);
		FlatButton.paint(graphics, font, c[4], c[8], c[5] - c[4], ui(Component.translatable("gui.mcweapons.tuner.cancel")), 0, ButtonStyle.GHOST, cancelHot);
		FlatButton.paint(graphics, font, c[6], c[8], c[7] - c[6], ui(Component.translatable("gui.mcweapons.tuner.reset")), ICON_RESET, ButtonStyle.DANGER_FILLED, resetHot);
	}

	private static boolean inside(double x, double y, int x0, int y0, int x1, int y1) {
		return x >= x0 && x < x1 && y >= y0 && y < y1;
	}

	private void resetToDefaults() {
		confirmingReset = false;
		ClientPlayNetworking.send(new TuneGunPayload(id, synced, TuneGunPayload.Action.RESET));
		status(Component.translatable("gui.mcweapons.tuner.reset.done", gunName), false);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (confirmingReset) {
			int[] c = confirmLayout(confirmBody());
			if (inside(event.x(), event.y(), c[6], c[8], c[7], c[8] + BUTTON_HEIGHT)) {
				resetToDefaults();
			} else if (inside(event.x(), event.y(), c[4], c[8], c[5], c[8] + BUTTON_HEIGHT) || !inside(event.x(), event.y(), c[0], c[1], c[2], c[3])) {
				confirmingReset = false;
			}
			return true;
		}
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
		} else if (!laidOut && Viewmodel.showcaseGunPose() != null) {
			init(width, height);
		}
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (confirmingReset) {
			if (event.isEscape()) {
				confirmingReset = false;
			} else if (event.isConfirmation()) {
				resetToDefaults();
			}
			return true;
		}
		// The tuner key closes it again, unless it is being typed into a text box, or is still the press that opened it (repeating).
		if (GunInput.TUNER.matches(event) && tunerKeyReleased && textFields.stream().noneMatch(EditBox::isFocused)) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean keyReleased(KeyEvent event) {
		if (GunInput.TUNER.matches(event)) {
			tunerKeyReleased = true;
		}
		return super.keyReleased(event);
	}

	private void send(TuneGunPayload.Action action, String doneKey) {
		GunDefinition tuned = parsed();
		if (tuned != null) {
			ClientPlayNetworking.send(new TuneGunPayload(id, tuned, action));
			status(Component.translatable(doneKey, gunName), false);
		}
	}

	private void copy() {
		GunDefinition tuned = parsed();
		if (tuned != null) {
			minecraft.keyboardHandler.setClipboard(GunDefinitions.toJsonText(id, tuned));
			status(Component.translatable("gui.mcweapons.tuner.copied"), false);
		}
	}

	/**
	 * Loads gun json from the clipboard (Copy JSON's format, or the gun's data file) into every row and applies it. Json for another
	 * gun, by its "item" field, is refused rather than applied to this one.
	 */
	private void applyClipboard() {
		JsonObject json;
		try {
			json = JsonParser.parseString(minecraft.keyboardHandler.getClipboard()).getAsJsonObject();
		} catch (RuntimeException e) {
			status(Component.translatable("gui.mcweapons.tuner.paste.none"), true);
			return;
		}
		JsonElement item = json.get("item");
		JsonElement ownItem = GunDefinitions.toJson(id, synced).get("item");
		if (item != null && ownItem != null && !item.equals(ownItem)) {
			status(Component.translatable("gui.mcweapons.tuner.paste.other", item.getAsString()), true);
			return;
		}
		DataResult<GunDefinition> result = GunDefinition.CODEC.parse(JsonOps.INSTANCE, json);
		if (result.isError()) {
			result.ifError(error -> status(Component.translatable("gui.mcweapons.tuner.invalid", error.message()), true));
			return;
		}
		GunDefinition pasted = result.getOrThrow();
		working = values(pasted);
		init(width, height);
		ClientPlayNetworking.send(new TuneGunPayload(id, pasted, TuneGunPayload.Action.APPLY));
		status(Component.translatable("gui.mcweapons.tuner.pasted", gunName), false);
	}

	/** The working values as a definition, or null after showing why not. */
	private @Nullable GunDefinition parsed() {
		DataResult<GunDefinition> result = GunDefinition.CODEC.parse(JsonOps.INSTANCE, working);
		result.ifError(error -> status(Component.translatable("gui.mcweapons.tuner.invalid", error.message()), true));
		return result.result().orElse(null);
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

	/** Picks the font variants rasterized at the current GUI scale, for every screen drawn in the mod's fonts. */
	static void useFontScale(Minecraft minecraft) {
		int scale = Mth.clamp(minecraft.getWindow().getGuiScale(), 1, MAX_FONT_SCALE);
		uiFont = fontAt("ui", scale);
		uiMedium = fontAt("ui_medium", scale);
		iconFont = fontAt("icons", scale);
	}

	private static FontDescription fontAt(String name, int scale) {
		return new FontDescription.Resource(McWeapons.id(name + "_x" + scale));
	}

	static Component ui(Component text) {
		return text.copy().withStyle(style -> style.withFont(uiFont));
	}

	static Component ui(String text) {
		return ui(Component.literal(text));
	}

	static Component medium(Component text) {
		return text.copy().withStyle(style -> style.withFont(uiMedium));
	}

	static Component icon(int codepoint) {
		return Component.literal(Character.toString(codepoint)).withStyle(style -> style.withFont(iconFont));
	}

	/** {@code text} cut down with an ellipsis until it fits {@code maxWidth}. */
	private Component fit(Component text, int maxWidth) {
		if (font.width(text) <= maxWidth) {
			return text;
		}
		String plain = text.getString();
		Style style = text.getStyle();
		while (!plain.isEmpty() && font.width(Component.literal(plain + "…").withStyle(style)) > maxWidth) {
			plain = plain.substring(0, plain.length() - 1);
		}
		return Component.literal(plain + "…").withStyle(style);
	}

	/** A rectangle with its corner pixels left out, which reads as slightly rounded. */
	static void rounded(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int color) {
		graphics.fill(x0 + 1, y0, x1 - 1, y1, color);
		graphics.fill(x0, y0 + 1, x0 + 1, y1 - 1, color);
		graphics.fill(x1 - 1, y0 + 1, x1, y1 - 1, color);
	}

	static void roundedOutline(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int color) {
		graphics.fill(x0 + 1, y0, x1 - 1, y0 + 1, color);
		graphics.fill(x0 + 1, y1 - 1, x1 - 1, y1, color);
		graphics.fill(x0, y0 + 1, x0 + 1, y1 - 1, color);
		graphics.fill(x1 - 1, y0 + 1, x1, y1 - 1, color);
	}

	/** Row background: nothing at rest, a faint highlight on hover, a stronger one when focused. */
	private static void rowBackground(GuiGraphicsExtractor graphics, AbstractWidget widget) {
		if (widget.isHoveredOrFocused()) {
			rounded(graphics, widget.getX(), widget.getY(), widget.getX() + widget.getWidth(), widget.getY() + widget.getHeight(),
					widget.isFocused() ? ROW_FOCUS : ROW_HOVER);
		}
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

		private String valueText() {
			return number().toPlainString() + (path.endsWith("_deg") ? "°" : path.endsWith("_ticks") ? "t" : "");
		}

		@Override
		protected void updateMessage() {
			setMessage(Component.empty().append(label).append(": " + valueText()));
		}

		/** Label left, value right, and a hairline track along the bottom filled up to the value. */
		@Override
		public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
			rowBackground(graphics, this);
			int textY = getY() + (height - 9) / 2 - 1;
			Component value = ui(valueText());
			int valueWidth = font.width(value);
			graphics.text(font, fit(label, width - 14 - valueWidth), getX() + 4, textY, LABEL, false);
			graphics.text(font, value, getX() + width - 4 - valueWidth, textY, TEXT, false);
			// The vanilla slider maps clicks across x + 4 to x + width - 4, so the track spans the same.
			int x0 = getX() + 4;
			int x1 = getX() + width - 4;
			int trackY = getY() + height - 2;
			int at = x0 + (int)Math.round(this.value * (x1 - x0));
			graphics.fill(x0, trackY, x1, trackY + 1, TRACK);
			graphics.fill(x0, trackY, at, trackY + 1, isHoveredOrFocused() ? TEXT : FILL);
			if (isHoveredOrFocused()) {
				graphics.fill(at - 1, trackY - 1, at + 2, trackY + 2, TEXT);
			}
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
	private final class Dropdown extends AbstractButton {
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
		public void onPress(InputWithModifiers input) {
			openDropdown = openDropdown == this ? null : this;
		}

		int optionAt(double mouseX, double mouseY) {
			int index = (int)Math.floor((mouseY - listTop() - 2) / fieldHeight);
			return mouseX >= getX() && mouseX < getX() + width && index >= 0 && index < options.size() ? index : -1;
		}

		private int listTop() {
			return getY() + height + 2;
		}

		void select(int index) {
			current = options.get(index);
			set(path, list(current));
			openDropdown = null;
		}

		/** Like a slider row: label left, the picked value right with a chevron. */
		@Override
		protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
			rowBackground(graphics, this);
			if (openDropdown == this) {
				rounded(graphics, getX(), getY(), getX() + width, getY() + height, ROW_FOCUS);
			}
			int textY = getY() + (height - 9) / 2;
			Component value = ui(current);
			int valueRight = getX() + width - 15;
			graphics.text(font, fit(label, width - 24 - font.width(value)), getX() + 4, textY, LABEL, false);
			graphics.text(font, value, valueRight - font.width(value), textY, TEXT, false);
			graphics.text(font, icon(openDropdown == this ? ICON_UP : ICON_DOWN), getX() + width - 12, textY, LABEL, false);
		}

		void extractList(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
			int top = listTop();
			int bottom = top + options.size() * fieldHeight + 4;
			// Fully opaque: it lies over the rows below, whose white text shows through even a slightly see-through card.
			rounded(graphics, getX(), top, getX() + width, bottom, 0xFF0C0E12);
			roundedOutline(graphics, getX(), top, getX() + width, bottom, EDGE);
			int hovered = optionAt(mouseX, mouseY);
			for (int i = 0; i < options.size(); i++) {
				int y = top + 2 + i * fieldHeight;
				if (i == hovered) {
					rounded(graphics, getX() + 2, y, getX() + width - 2, y + fieldHeight, ROW_FOCUS);
				}
				boolean picked = options.get(i).equals(current);
				int textY = y + (fieldHeight - 9) / 2;
				graphics.text(font, ui(options.get(i)), getX() + 7, textY, picked || i == hovered ? TEXT : LABEL, false);
				if (picked) {
					graphics.text(font, icon(ICON_APPLY), getX() + width - 13, textY, TEXT, false);
				}
			}
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			defaultButtonNarrationText(output);
		}
	}

	/** An on/off value as a small pill switch: the knob slides right and the pill turns white when on. */
	private final class Toggle extends AbstractButton {
		private final Component label;
		private final String path;
		private boolean on;

		Toggle(int x, int y, int width, Component label, String path, boolean on) {
			super(x, y, width, fieldHeight, label);
			this.label = label;
			this.path = path;
			this.on = on;
			updateMessage();
		}

		private void updateMessage() {
			setMessage(Component.empty().append(label).append(": ").append(on ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF));
		}

		@Override
		public void onPress(InputWithModifiers input) {
			on = !on;
			set(path, new JsonPrimitive(on));
			updateMessage();
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
			rowBackground(graphics, this);
			graphics.text(font, label, getX() + 4, getY() + (height - 9) / 2, LABEL, false);
			int trackWidth = 15;
			int trackHeight = 8;
			int x = getX() + width - 4 - trackWidth;
			int y = getY() + (height - trackHeight) / 2;
			rounded(graphics, x, y, x + trackWidth, y + trackHeight, on ? TEXT : TRACK);
			int knob = on ? x + trackWidth - trackHeight + 1 : x + 1;
			rounded(graphics, knob, y + 1, knob + trackHeight - 2, y + trackHeight - 1, on ? DARK : LABEL);
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			defaultButtonNarrationText(output);
		}
	}

	private enum ButtonStyle { PRIMARY, GHOST, DANGER, DANGER_FILLED, ICON }

	/** A flat button: an icon and a label (or the icon alone), white filled for the main action and outlined for the rest. */
	private final class FlatButton extends AbstractButton {
		private final int icon;
		private final ButtonStyle style;
		private final Runnable action;

		FlatButton(int x, int y, Component label, int icon, ButtonStyle style, Runnable action) {
			super(x, y, style == ButtonStyle.ICON ? BUTTON_HEIGHT : widthOf(Minecraft.getInstance().font, ui(label), true), BUTTON_HEIGHT, label);
			this.icon = icon;
			this.style = style;
			this.action = action;
		}

		static int widthOf(net.minecraft.client.gui.Font font, Component label, boolean withIcon) {
			return font.width(label) + (withIcon ? 25 : 14);
		}

		@Override
		public void onPress(InputWithModifiers input) {
			action.run();
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
			paint(graphics, font, getX(), getY(), width, style == ButtonStyle.ICON ? Component.empty() : ui(getMessage()), icon, style, isHoveredOrFocused());
		}

		static void paint(GuiGraphicsExtractor graphics, net.minecraft.client.gui.Font font, int x, int y, int width, Component label, int icon, ButtonStyle style, boolean hot) {
			int background = switch (style) {
				case PRIMARY -> hot ? 0xFFFFFFFF : 0xFFE4E6EA;
				case DANGER_FILLED -> hot ? 0xFFFF8585 : DANGER;
				// Dark glass like the panels, so they stay readable over a bright sky or sand.
				case DANGER -> hot ? 0xE62A1618 : PANEL;
				case GHOST, ICON -> hot ? 0xE6262A31 : PANEL;
			};
			int content = switch (style) {
				case PRIMARY -> DARK;
				case DANGER_FILLED -> 0xFFFFFFFF;
				case DANGER -> DANGER;
				case GHOST, ICON -> TEXT;
			};
			rounded(graphics, x, y, x + width, y + BUTTON_HEIGHT, background);
			if (style == ButtonStyle.GHOST || style == ButtonStyle.ICON || style == ButtonStyle.DANGER) {
				roundedOutline(graphics, x, y, x + width, y + BUTTON_HEIGHT, style == ButtonStyle.DANGER ? 0x40FF6B6B : EDGE);
			}
			int textY = y + (BUTTON_HEIGHT - 9) / 2;
			if (label.getString().isEmpty()) {
				Component glyph = icon(icon);
				graphics.text(font, glyph, x + (width - font.width(glyph)) / 2, textY, content, false);
				return;
			}
			int textX = x + 7;
			if (icon != 0) {
				graphics.text(font, icon(icon), textX, textY, content, false);
				textX += 11;
			}
			graphics.text(font, label, textX, textY, content, false);
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			defaultButtonNarrationText(output);
		}
	}
}

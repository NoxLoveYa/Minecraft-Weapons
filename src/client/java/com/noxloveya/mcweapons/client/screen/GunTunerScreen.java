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
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Ops-only tuner for the held gun's definition, opened and closed with the tuner key. Each json value is one row.
 * Apply uses the values live for everyone, Save also writes them into the world, Reset goes back to the gun's own file.
 */
public class GunTunerScreen extends OptionsSubScreen {
	private static final int INVALID_COLOR = 0xFFFF5555;
	private static final int BUTTON_WIDTH = 74;

	private final Identifier id;
	private final Component gunName;
	private final List<Row> rows = new ArrayList<>();
	/** Last definition seen in {@link GunDefinitions}; a new one (from Apply, Reset or another op) refills the rows. */
	private GunDefinition synced;
	/** Last valid values. Each row is checked on its own against these, so one bad row does not mark the others. */
	private GunDefinition definition;

	/** One json value: {@code key} at the top level, or inside the {@code group} object. Arrays are typed comma separated. */
	private record Row(@Nullable String group, String key, boolean array, EditBox box) {
		String path() {
			return group == null ? key : group + "." + key;
		}
	}

	public GunTunerScreen(Identifier id, GunDefinition definition, Component gunName) {
		super(null, Minecraft.getInstance().options, Component.translatable("gui.mcweapons.tuner.title", gunName));
		this.id = id;
		this.gunName = gunName;
		this.synced = definition;
		this.definition = definition;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	protected void addOptions() {
		// init() runs again on resize; keep what was typed but not applied yet.
		Map<String, String> typed = new HashMap<>();
		rows.forEach(row -> typed.put(row.path(), row.box().getValue()));
		rows.clear();
		JsonObject json = values(definition);
		list.addHeader(Component.translatable("gui.mcweapons.tuner.stats"));
		json.asMap().forEach((key, value) -> {
			if (!value.isJsonObject()) {
				addRow(null, key, value, typed);
			}
		});
		json.asMap().forEach((group, value) -> {
			if (value.isJsonObject()) {
				list.addHeader(Component.literal(group));
				value.getAsJsonObject().asMap().forEach((key, inner) -> addRow(group, key, inner, typed));
			}
		});
	}

	private void addRow(@Nullable String group, String key, JsonElement value, Map<String, String> typed) {
		EditBox box = new EditBox(font, 150, 20, Component.literal(key));
		box.setMaxLength(64);
		Row row = new Row(group, key, value.isJsonArray(), box);
		box.setResponder(text -> box.setTextColor(GunDefinition.CODEC.parse(JsonOps.INSTANCE, with(values(definition), row, text)).isSuccess()
				? EditBox.DEFAULT_TEXT_COLOR : INVALID_COLOR));
		box.setValue(typed.getOrDefault(row.path(), text(value)));
		rows.add(row);
		list.addSmall(new StringWidget(150, 20, Component.literal(key), font), box);
	}

	@Override
	protected void addFooter() {
		LinearLayout footer = layout.addToFooter(LinearLayout.horizontal().spacing(4));
		footer.addChild(button("apply", () -> send(TuneGunPayload.Action.APPLY, "gui.mcweapons.tuner.applied")));
		footer.addChild(button("save", () -> send(TuneGunPayload.Action.SAVE, "gui.mcweapons.tuner.saved")));
		footer.addChild(button("copy", this::copy));
		footer.addChild(button("reset", this::confirmReset));
		footer.addChild(Button.builder(CommonComponents.GUI_DONE, button -> onClose()).width(BUTTON_WIDTH).build());
	}

	private Button button(String name, Runnable action) {
		return Button.builder(Component.translatable("gui.mcweapons.tuner." + name), button -> action.run()).width(BUTTON_WIDTH).build();
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

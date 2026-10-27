package com.noxloveya.mcweapons.gun;

import com.geckolib.animatable.GeoItem;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.cache.animation.keyframeevent.SoundKeyframeData;
import com.geckolib.util.GeckoLibUtil;
import com.noxloveya.mcweapons.registry.ModComponents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/** Every gun is an instance of this class. Its stats live in the definition it points to. */
public class GunItem extends Item implements GeoItem {
	public static final String MAIN_CONTROLLER = "main";
	/** Inspect variants; the client picks one at random each time. */
	public static final List<String> INSPECT_ACTIONS = List.of("inspect", "inspect_press_check", "inspect_mag_check");
	public static final List<String> TRIGGERED_ACTIONS = List.of("draw", "fire", "reload", "reload_empty", "inspect", "inspect_press_check", "inspect_mag_check");
	/** Set by the renderer when the gun is empty and not reloading, so the slide stays locked back. */
	public static final DataTicket<Boolean> SLIDE_LOCKED = DataTicket.create("mcweapons_slide_locked", Boolean.class);

	// Both hooks are filled in by the client entrypoint, because this class also loads on a dedicated server.
	// The render provider is a GeckoLib GeoRenderProvider, typed as Object since its class pulls in client-only types.
	public static Function<GunItem, @Nullable Object> renderProviderFactory = gun -> null;
	public static AnimationController.KeyframeEventHandler<GunItem, SoundKeyframeData> soundKeyframes = event -> {};

	private final Identifier definitionId;
	private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
	private @Nullable Object renderProvider;

	public GunItem(Identifier definitionId, Properties properties) {
		super(properties);
		this.definitionId = definitionId;
	}

	public Identifier definitionId() {
		return this.definitionId;
	}

	/** Animation names follow DESIGN_RULES 3.6: {@code animation.<gun>.<action>}. */
	public String animationName(String action) {
		return "animation." + this.definitionId.getPath() + "." + action;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		RawAnimation idle = RawAnimation.begin().thenLoop(animationName("idle"));
		// No transition ticks: every animation starts and ends at the rest pose, and a blend would delay the fire snap.
		AnimationController<GunItem> main = new AnimationController<>(MAIN_CONTROLLER, 0, test -> test.setAndContinue(idle));
		for (String action : TRIGGERED_ACTIONS) {
			main.triggerableAnim(action, RawAnimation.begin().thenPlay(animationName(action)));
		}
		main.setSoundKeyframeHandler(event -> soundKeyframes.handle(event));
		// Registered after main so it wins on the slide, barrel and slide stop while the gun is empty, whatever main is playing.
		RawAnimation locked = RawAnimation.begin().thenLoop(animationName("slide_locked"));
		AnimationController<GunItem> slideLock = new AnimationController<>("slide_lock", 0, test -> {
			if (Boolean.TRUE.equals(test.getData(SLIDE_LOCKED))) {
				return test.setAndContinue(locked);
			}
			// STOP alone keeps holding the last pose; reset hands the slide back to the main controller.
			test.controller().reset();
			return PlayState.STOP;
		});
		controllers.add(main, slideLock);
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return this.animationCache;
	}

	@Override
	public Object getRenderProvider() {
		if (this.renderProvider == null) {
			this.renderProvider = renderProviderFactory.apply(this);
		}
		return this.renderProvider;
	}

	public static @Nullable GunDefinition definition(ItemStack stack) {
		return stack.getItem() instanceof GunItem gun ? GunDefinitions.get(gun.definitionId) : null;
	}

	public static int ammo(ItemStack stack, GunDefinition definition) {
		return stack.getOrDefault(ModComponents.AMMO, definition.magSize());
	}

	public static boolean holdsGun(Player player) {
		return !player.isSpectator() && player.getMainHandItem().getItem() instanceof GunItem;
	}

	/** Sound ids follow the gun: {@code <definition id>.<action>}, for example {@code mcweapons:glock17.fire}. */
	public SoundEvent sound(String action) {
		return SoundEvent.createVariableRangeEvent(definitionId.withSuffix("." + action));
	}

	/** While a gun is in the main hand, vanilla attack and use do nothing, on either side. */
	public static void registerEvents() {
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> holdsGun(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> holdsGun(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> holdsGun(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> holdsGun(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseItemCallback.EVENT.register((player, level, hand) -> holdsGun(player) ? InteractionResult.FAIL : InteractionResult.PASS);
	}

	/** Finishes or cancels a reload. Only called on the server. */
	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
		GunState state = stack.getOrDefault(ModComponents.GUN_STATE, GunState.EMPTY);
		if (!state.reloading() || !(entity instanceof Player player)) {
			return;
		}
		GunDefinition definition = GunDefinitions.get(definitionId);
		if (definition == null || player.getMainHandItem() != stack) {
			stack.set(ModComponents.GUN_STATE, new GunState(0, state.lastShotTick()));
		} else if (level.getGameTime() >= state.reloadingUntilTick()) {
			int ammo = ammo(stack, definition);
			stack.set(ModComponents.AMMO, ammo + AmmoSource.take(player, stack, definition.reloadTarget(ammo) - ammo));
			stack.set(ModComponents.GUN_STATE, new GunState(0, state.lastShotTick()));
		}
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> lines, TooltipFlag flag) {
		GunDefinition definition = GunDefinitions.get(definitionId);
		if (definition == null) {
			return;
		}
		lines.accept(Component.translatable("gui.mcweapons.ammo", ammo(stack, definition), definition.magSize()).withStyle(ChatFormatting.GRAY));
		lines.accept(Component.literal("9x19mm").withStyle(ChatFormatting.GRAY)); // shortcut: caliber is hardcoded, phase 3 moves it into the definition
	}

	// Ammo and state change on every shot; without this vanilla replays the re-equip animation each time.
	@Override
	public boolean allowComponentsUpdateAnimation(Player player, InteractionHand hand, ItemStack oldStack, ItemStack newStack) {
		return false;
	}
}

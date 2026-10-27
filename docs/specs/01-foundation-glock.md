# Spec 01: Foundation and the Glock

**Status:** phase 1 done (pending review)
**Prompts:** `docs/prompts/01a-scaffold-and-gun-framework.md`, `01b-glock-blockbench.md`, `01c-viewmodel-and-rendering.md`
Prompts 01a and 01b don't depend on each other, so they can run in parallel. 01c needs both of them.

## Goal

One gun, the Glock 17, that looks and feels right in first person and is built on a framework that later guns, attachments and ammo can plug into without a rewrite.

## 1. Scaffold

- Fabric mod: id `mcweapons`, display name "Minecraft Weapons", package `com.noxloveya.mcweapons`.
- Minecraft 1.21.1, Java 21, Loom with Mojang mappings and Parchment, and split client and main source sets.
- Dependencies are Fabric API and GeckoLib for Fabric 1.21.1 (latest 4.x). Use the latest stable versions for 1.21.1 from fabricmc.net/develop and the GeckoLib maven, and pin them in `gradle.properties`.
- Gradle wrapper committed, `.gitignore` for Gradle and IntelliJ, and `README.md` with build and run steps.
- A creative tab called "Minecraft Weapons" that holds every gun.

## 2. Gun definition (data)

`data/mcweapons/guns/glock17.json`, loaded by a server data reload listener and synced to clients when they join and on `/reload`:

```json
{
  "item": "mcweapons:glock17",
  "fire_modes": ["semi"],
  "rpm": 400,
  "damage": 5.0,
  "range": 64,
  "mag_size": 17,
  "chamber": true,
  "spread_hip_deg": 2.5,
  "spread_ads_deg": 0.4,
  "recoil_pitch_deg": 2.0,
  "recoil_yaw_deg": 0.6,
  "draw_ticks": 10,
  "reload_ticks": 40,
  "reload_empty_ticks": 50,
  "ads_ticks": 5,
  "ads_fov_multiplier": 0.8,
  "move_speed_ads_multiplier": 0.7
}
```

- With `chamber: true`, a tactical reload leaves you at `mag_size + 1` rounds and an empty reload at `mag_size`.
- An unknown or missing definition is logged once. The item then does nothing, and it must never crash.
- In phase 1, reloading is free, so no ammo items are needed (phase 3 adds them). Leave a single method, `AmmoSource.take(player, gun, wanted) -> int`, which in phase 1 just returns `wanted`.

## 3. Gun item and state

- `GunItem` is a single class. Each gun is a registered instance of it pointing to a definition id.
- Data components:
  - `mcweapons:ammo`, an int holding the rounds currently loaded.
  - `mcweapons:gun_state`, a record of `reloading_until_tick` (long, with 0 for none) and `last_shot_tick` (long).
- The item cannot stack, has no durability, and sets no vanilla attack damage.
- Its tooltip shows the rounds in the mag over the mag size and the caliber line "9x19mm", which is hardcoded for now.

## 4. Controls

| input | action |
|---|---|
| Attack (left mouse) | fire. Holding it repeats only when the gun has an `auto` mode, so the Glock is click-to-fire. |
| Use (right mouse), held | aim down sights |
| `R` (rebindable, category "Minecraft Weapons") | reload |
| `V` (rebindable) | inspect |

While a gun is in the main hand, the attack key must not break blocks or hit entities, and the use key must not do anything vanilla. Swapping off the gun cancels a reload.

## 5. Shooting (server)

The client sends `ShootPayload(slot, client_tick, seed)`. The server checks:

1. the item in that slot is a `GunItem` with a definition
2. the gun is not reloading and has more than 0 rounds
3. the time since `last_shot_tick` is at least the shot interval, which is `ceil(1200 / rpm)` ticks, with a 1-tick allowance for latency

If all three pass, the server:

- takes one round and updates `last_shot_tick`
- raycasts from the eyes along the look vector, with a random spread cone (seeded with `seed`) and ADS spread if the player is aiming, up to `range`. Blocks stop the shot.
- deals `damage` to the first living entity hit, using a mod damage type `mcweapons:bullet` that names the gun in the death message
- plays the gunshot sound at the player for nearby players and sends nearby clients `ShotFxPayload(shooter_id, hit_pos, hit_type)` for muzzle flash, impact particles, and the third-person shot
- at 0 rounds, plays a dry click instead and does not fire

## 6. Reload (server)

`ReloadPayload(slot)`. Reloading is blocked if the mag is already full or a reload is already going. The server sets `reloading_until_tick` to `now + reload_ticks`, or `reload_empty_ticks` when ammo is 0. Rounds are added when the timer finishes, and only if the gun is still in that slot. Any shot while reloading is ignored.

## 7. Aim state

`AimPayload(aiming)` is sent whenever the state changes. The server keeps the aiming state per player and uses it for spread and the movement speed modifier, which is applied as an attribute modifier and removed when the player stops aiming or switches item.

## 8. Rendering and viewmodel (client)

Follows DESIGN_RULES section 4.

- GeckoLib `GeoItem` with a `GeoItemRenderer`. The model, texture and animation are resolved by gun id.
- In first person with a gun in the main hand, vanilla's hand and item rendering is replaced by the GeckoLib model, drawn with arms. Bones that start with `arm_` render using the local player's skin, and the Steve layout is fine for phase 1.
- Animation triggers use GeckoLib triggerable animations on a `main` controller:
  - `draw` when the gun is selected
  - `fire` when a local shot happens (client-predicted)
  - `reload` or `reload_empty` when the reload key is pressed and accepted (client-predicted, since it is the same check as on the server)
  - `inspect` when V is pressed and the gun is not reloading or firing
  - `idle` loops otherwise
- Procedural layers, in this order: ADS lerp (with `ads_anchor` landing at screen center), mouse sway, walk bob, then recoil. Recoil is an impulse of `recoil_pitch_deg` with spring decay over about 6 ticks, applied to the model, plus a real camera pitch kick of the same amount that does not recover on its own.
- FOV is multiplied by `ads_fov_multiplier`, lerped over `ads_ticks`.
- The muzzle flash is a short additive quad or particle at the `muzzle` locator, shown for 1 to 2 frames.
- The crosshair is hidden while aiming. When hip-firing, it shows a simple dynamic spread crosshair, either the vanilla one or the vanilla one scaled by spread.
- HUD: a small ammo counter in the bottom right, "17 / 17", while a gun is held.

## 9. The Glock asset

Follows DESIGN_RULES section 3. The Glock 17 is a polymer-frame pistol in black or dark grey, with a slide that has visible serrations, a square trigger guard, and a flush 17-round mag. Animation notes:

- **fire:** the slide snaps back and returns in about 0.1s, with a small upward kick of about 2°.
- **reload (2.0s):** tilt the gun, drop the mag out, pull a new mag up from below the screen with the left arm, seat it, and return.
- **reload_empty (2.5s):** same as reload, then the left hand racks the slide or the thumb hits the slide release.
- **draw (0.5s):** comes up from below the right side of the screen.
- **inspect (about 3s):** rotate to show the left side, then the right side, then return.
- **idle:** slow breathing.

## Acceptance checks for phase 1

- [ ] `./gradlew build` and `./gradlew runClient` work from a clean clone.
- [ ] You can get the Glock from the creative tab, and it shows "17 / 17" when picked up (chamber starts empty).
- [ ] Clicking fires once per click and never faster than 400 RPM, and holding the button does not auto-fire.
- [ ] Shots damage a zombie at 30 blocks while ADS, and are stopped by a wall.
- [ ] Reloading with a round left ends at 18, and an empty reload ends at 17. Switching items during a reload cancels it.
- [ ] Every animation plays at the right moment, and reload animations finish when the ammo count changes.
- [ ] While aiming, the sights are centered, FOV zooms, sway is reduced and you move slower.
- [ ] The arms use the player's skin.
- [ ] In multiplayer (two dev clients plus a dedicated server), the other player hears shots and sees impacts, and the server log has no errors.

## Open questions

(The builder adds questions here.)

### From 01b (Glock assets)

- **Viewmodel space.** Nothing said where the model sits, so the Glock is built in camera space: the origin is the eye, forward is -Z (north), +X is right, +Y is up, and 16 units are 1 block. 01c can draw it straight in first-person view space with no vanilla item transforms, and the gun lands lower right. In Blockbench coordinates, `ads_anchor` rests at (6, -2, -14) and `muzzle` at (6, -3.75, -20). The geo file stores X negated (Bedrock convention), and GeckoLib flips it back.
- **Root pivot.** `root` and `gun` pivot at the top of the grip, (6, -5.5, -11), so draw, kick and reload tilt rotate around the right hand.
- **Arms during reload.** Because the arms are not under `gun`, the reloads key `arm_right` to follow the grip and `arm_left` to carry the mag and rack the slide. Both are solved to the animated positions, so the hands stay on the gun.
- **Fire.** The Glock is striker-fired and has no hammer, so `fire` moves the slide 1.6 units back, pulls the trigger, and adds a 1.5° kick on `root` so the arms stay attached.
- **Sound keyframes.** A tactical reload has no slide action, so `reload` only has `mag_out` (0.32 s) and `mag_in` (1.48 s). `reload_empty` adds `slide_back` (1.84 s) and `slide_release` (1.94 s).
- **No slide lock.** Phase 1 has no locked-back state, so `reload_empty` starts with the slide forward and racks it by hand.
- **Thin parts.** The sights, trigger and trigger guard are thinner than one texel, so they use per-face UV on small solid-color swatches. Everything 1 unit or thicker uses box UV.
- **Arm texture areas.** The arm UV areas of `glock17.png`, (40,16) and (32,48), hold a Steve-like placeholder for the Blockbench preview only. In game the renderer replaces them with the player's skin.
- **Item model.** No item model was exported. On 26.3, items take their model from `assets/mcweapons/items/glock17.json`, so 01c points that file at GeckoLib 5's item renderer when it adds rendering. The geo (format 1.12.0) and animation file (`geckolib_format_version` 2) are the formats GeckoLib 5 reads.

### From 01a (scaffold and gun framework)

Decisions made where the spec was silent. Each one is the simplest option, and can be changed if planning disagrees.

- **Stack moved to 26.3.** The decision is in PLAN.md (2026-10-27). Section 1 still says 1.21.1, Java 21, Parchment and GeckoLib 4.x, so planning needs to update it. Pinned now: Minecraft 26.3, Loader 0.19.5 (the oldest the mod declares, matching bombo's install), Loom 1.19.3, Fabric API 0.165.0+26.3, GeckoLib 5.5.7 (`com.geckolib:geckolib-fabric-26.3`), and Java 25. There are no mappings: 26.x ships unobfuscated, so Parchment has nothing to layer onto. The Gradle wrapper is still 9.8.2, the same as the Fabric example mod's 26.3 branch.
- **The `item` field in the gun JSON is not read.** `GunItem` points at its definition id, which is the file name (`guns/glock17.json` gives `mcweapons:glock17`), so `item` repeats information the code already has. It is also field 17, one more than DFU's `RecordCodecBuilder` takes. It stays in the file because the spec lists it. Either drop it from the spec, or make it the link and stop passing an id to `GunItem`.
- **When the mag counts as full.** Reload is blocked when `ammo >= target`, where the target is `mag_size + 1` with a round left (and `chamber: true`) and `mag_size` when empty. So a fresh 17/17 Glock can still reload to 18, which chambers a round. If full should mean `ammo >= mag_size`, that is a one-line change in `GunDefinition.reloadTarget`.
- **A fresh gun has no `mcweapons:ammo` component, which means a full mag.** Items are registered before data loads, so mag size can't be a default component. The component is written on the first shot.
- **Sounds come from the definition id and are not registered in Java.** `mcweapons:<gun>.fire` and `.dry` are played as direct sound holders, so a new gun needs only `sounds.json` entries. That means there's no `ModSounds` class, although DESIGN_RULES lists one. The placeholders point at vanilla `entity.firework_rocket.blast` and `block.dispenser.fail`. Volume is 1, which carries 16 blocks.
- **Sound prediction.** The shooter's client plays fire or dry as soon as the click happens, and the server plays the sound to everyone except the shooter. The client predicts from the synced ammo count, so with high ping at 1 round, the shooter can hear "fire" while others hear "dry".
- **The dry click is also limited by the shot interval** and updates `last_shot_tick`, so spam-clicking an empty gun doesn't machine-gun clicks.
- **`slot` must be the selected hotbar slot.** The server ignores shoot and reload payloads for any other slot. This also keeps out-of-range indexes away from the inventory.
- **`client_tick` is sent but not used.** The server times everything by its own game time, and there's no lag compensation in phase 1.
- **The spread `seed` comes from the client, as the spec says.** A modified client could send seeds that give no spread. A later fix is to mix the seed with a server salt, but then the client can't predict spread.
- **Spread degrees are the cone's half-angle** (largest deviation), with shots spread evenly over the cone's disc.
- **The aiming state is the movement speed modifier itself:** `mcweapons:ads_speed`, `ADD_MULTIPLIED_TOTAL`, `move_speed_ads_multiplier - 1`. It's removed when aiming stops, when a server tick sees no gun in the main hand, and on death or relog, because it's transient.
- **Damage type `mcweapons:bullet` is tagged `minecraft:bypasses_cooldown`.** Otherwise vanilla's hurt cooldown would swallow most shots at 400 RPM. It is not tagged `is_projectile`, because endermen would dodge it. Armor and knockback work as in vanilla.
- **Death message:** "%1$s was shot by %2$s using %3$s". Vanilla only names the weapon when it has a custom name, so the bullet damage source overrides the message to always name the gun.
- **"First living entity hit" ignores non-living entities.** Shots pass through item frames, boats, dropped items and the like.
- **Vanilla attack and use are suppressed without mixins.** Fabric's `AttackBlockCallback`, `AttackEntityCallback`, `UseBlockCallback`, `UseEntityCallback` and `UseItemCallback` return FAIL on both sides while a gun is in the main hand, which also blocks the offhand item. Attack and use clicks are consumed in `START_CLIENT_TICK`, before vanilla reads them. Spectators are left alone.
- **No re-equip animation on every shot.** `GunItem` returns false from Fabric's `allowComponentsUpdateAnimation`. Otherwise every ammo change would replay the equip bob.
- **Reload cancel.** A reloading gun that isn't the main-hand stack is cancelled on its next inventory tick (`GunItem.inventoryTick`). A gun that leaves the inventory mid-reload (dropped or put in a chest) keeps its timer, and the reload resolves the next time it ticks in a player's inventory.
- **The HUD and tooltip show `ammo / mag_size`,** so a chambered Glock shows "18 / 17".
- **The inspect key (V) is registered but does nothing until 01c.**
- **Placeholder model.** `models/item/glock17.json` is a flat `item/generated` model that uses `textures/item/glock17_icon.png`, leaving `glock17.png` free for the 01b GeckoLib texture. 01c decides whether to keep the flat icon for the inventory.
- **Added a JUnit test** (`fabric-loader-junit`). It parses `glock17.json` and checks the fire-rate gate, the reload targets, and a round trip of the definitions sync payload. Singleplayer never encodes payloads, so without the test that codec would only run on a dedicated server.

### From 01c (viewmodel and rendering)

- **GeckoLib 5 asset paths.** GeckoLib 5 only loads `assets/<ns>/geckolib/models/` and `assets/<ns>/geckolib/animations/`, so the Glock files moved to `geckolib/models/item/glock17.geo.json` and `geckolib/animations/item/glock17.animation.json`. The texture stays at `textures/item/glock17.png`. The paths in DESIGN_RULES section 1 are the GeckoLib 4 ones, so planning needs to update them.
- **No separate `GunModel` class.** GeckoLib's `DefaultedItemGeoModel` already finds the geo, texture and animation by gun id. `GunRenderer` is the only per-gun rendering class, and it is shared by every gun.
- **Animation state is per holder, not per stack.** The client gets a new ItemStack every time the server syncs ammo, and GeckoLib's default per-stack id would restart a running reload. The instance id is the holding entity's id, so the same state drives first and third person.
- **Mixins (four, client only, each with a one-line reason):**
  - `FirstPersonHandsAndItemsRenderer` swaps vanilla hands and items for the viewmodel.
  - `GameRenderer` skips vanilla's hand bob while a gun is held, because the viewmodel applies its own.
  - `Camera` applies the ADS FOV after vanilla's smoothing.
  - `AbstractClientPlayer` stops the ADS slowdown from also narrowing the FOV through vanilla's speed effect. Without it, aiming would zoom twice.
- **ADS.** The model moves so `ads_anchor` sits on the view axis, and it also moves 0.1 blocks forward so the slide doesn't fill the view. The rear sight is now a U-notch (two posts and a bridge, changed in the `.bbmodel` and geo), so the white front post shows in the notch. Sway and bob drop to 20%. With the View Bobbing option off, there is no bob at all. For a more realistic feel (bombo's request), sway uses vanilla's smoothed view lag at a fifth of vanilla's hand strength (0.02 instead of 0.1), capped at 0.75°, and the walk bob is 30% of vanilla's view bob.
- **Recoil.** A damped spring on the model settles in about 6 ticks with a small overshoot. The camera kick is `recoil_pitch_deg` up, plus a random yaw of up to `recoil_yaw_deg`, and neither recovers on its own.
- **Third person.** `items/glock17.json` uses GeckoLib's special model only in the third-person hand contexts. The GUI, the ground, item frames and a gun in the off hand show the flat icon. The model is scaled to 0.75 and turned so the barrel points forward, with the arm bones hidden. The holder uses vanilla's normal item-holding arm pose.
- **Other players.** `ShotFxPayload` plays the shooter's `fire` animation on their third-person gun and puffs smoke near the muzzle. Their draw and reload animations are not synced.
- **Reload sync.** The reload animation starts on the client's prediction at the key press, and the server's timer starts when the payload arrives. So the animation can finish up to one ping before the ammo count changes.
- **Sounds.** All six Glock sounds (`fire`, `dry`, `mag_out`, `mag_in`, `slide_back`, `slide_release`) are real recordings from Pixabay (Pixabay Content License, no attribution required; sources are in `CREDITS.md`). They live in `assets/mcweapons/sounds/glock17/` as mono Ogg Vorbis, so Minecraft positions them in 3D, and are trimmed so they start without a gap. `sounds.json` keeps the reload and dry-fire sounds quieter than the shot. The four reload sounds come from one creator's matched set and play at the holder from the animation's sound keyframes. The server plays the gunshot at volume 3, which in vanilla only widens its range, so other players hear it from about 48 blocks instead of 16.
- **Muzzle flash.** First person only: additive textured quads at the `muzzle` bone for 45 ms, with a random roll per shot (`textures/effect/muzzle_flash.png`).
- **Impacts.** Smoke on blocks and crit particles on entities, plus debris particles taken from the block that was hit.
- **Bullet tracers (bombo asked for them).** Every shot gets a 2.5-block glowing streak that flies at 220 blocks/s from the muzzle to the server's hit point (from `ShotFxPayload`). It is visual only. Your own first-person shot starts where the viewmodel's muzzle is drawn, corrected for the hand's fixed 70° FOV. Other players' shots, and yours in third person, start at an estimated right-hand muzzle. While aiming, the tracer runs straight down the line of sight, so it mostly hides behind the sights.
- **HUD (bombo asked for a custom one).**
  - A bottom-right panel shows the gun name, fire mode, a large round count (amber at a quarter mag or less, red at 0) over the mag size, and a reload progress bar. When the gun is empty and not reloading, a blinking "RELOAD [R]" hint appears.
  - The crosshair is four ticks spaced to match the hip spread cone at the current FOV, widening while the recoil settles. It fades out while aiming, and vanilla's crosshair is used whenever no gun is held.
- **Not handled.** A left-handed main arm still draws the gun on the right. With full ADS the left forearm fills the lower left of the view, which is a candidate for an arm-pose pass.
- **Glock rebuilt to match a Gen 6 reference photo (bombo's request).**
  - At 1 unit = 16.9 mm, the slide is 11 × 1.6 × 1.5 units, with a beveled top, front and rear serrations, the left-side engraving, and the ejection port and extractor on the right.
  - The frame has a dust cover with a rail slot, a square trigger guard with front grooves, and an 18°-raked stippled grip with a beavertail and the Glock logo.
  - The takedown lever, slide stop and mag release are separate parts, the mag base is flared with a visible round, and the rear sight has a white U-outline with a white front post.
- **Texture 128×128 with per-face UV.** The parts are fractional sizes, and box UV can't map those, so DESIGN_RULES 3.3's "box UV where possible" doesn't apply here. Faces are packed at about 4 texels per unit (5.8 on the slide). The arm bones keep box UV at (40,16) and (32,48), and the arm layer rescales them to the 64×64 skin.
- **Extra bones:** `barrel`, `slide_stop` and `mag_release`, all under `gun`. The guide rod is a static cube on `gun`.
- **Moving parts.**
  - `fire` drives the slide 2.2 units back (about a real Glock's stroke), which exposes the barrel crown and guide rod. The barrel follows 0.6 units with a 3° tilt, and the trigger pulls and resets.
  - Both reloads press the mag release.
  - `reload_empty` starts with the slide locked back. The left hand pulls it slightly further and lets it go, and the slide stop drops.
- **Slide lock (beyond the spec, bombo asked for the moving parts).** An empty gun that isn't reloading holds its slide back, with the slide stop up. A second controller, `slide_lock`, plays `animation.glock17.slide_locked` on top of `main`.
  - For our own gun, the lock is predicted for about half a second after the last shot, until the server confirms the gun is empty. Other players' guns use their synced ammo count.
  - GeckoLib 5 holds a stopped controller's last pose, so the lock controller resets itself whenever the gun isn't locked.
- **Spread changed from the section 2 values (bombo's request).** `glock17.json` now uses `spread_hip_deg` 0.4 (was 2.5) and `spread_ads_deg` 0.1 (was 0.4), so hip fire is close to point of aim. Section 2 should be updated to match.
- **Inspect has three random variants (bombo's request), beyond section 9.** V picks at random, never the same one twice in a row, between the section 9 look-over (`inspect`), a press check (`inspect_press_check`, 2.6s: the left hand pulls the slide partway back and lets it go) and a mag check (`inspect_mag_check`, 3.0s: the mag release is pressed, the left hand pulls the mag, shows it, and seats it). An empty gun with its slide locked back always gets the look-over, since the other two show a chambered slide and a loaded mag. Fire and aim stay on vanilla Attack and Use Item; only Reload and Inspect have their own entries, in the "Minecraft Weapons" key bind category.
- **Gun tuner (bombo's request, beyond phase 1).** F7 (rebindable, "Gun Tuner (ops only)") opens a screen with every value of the held gun's definition, one row each. It only opens for ops (permission level 2, which includes singleplayer with cheats on), and the server checks again on every change. Apply uses the values live for every player (re-sent with `GunDefinitionsPayload`). Save also writes them to `<world>/datapacks/mcweapons_tuning/data/<namespace>/guns/<gun>.json`, which /reload and the next world load pick up. Copy JSON puts the file on the clipboard, and Reset (with a confirm) deletes the saved file and reloads data packs. The screen lives in a new `client/screen/` package, which DESIGN_RULES section 1 does not list yet.
- **New optional `feel` object in gun definitions.** The viewmodel's sway, walk bob and recoil spring values moved from constants in `Viewmodel` into `feel` (`sway_gain`, `sway_max_deg`, `bob_scale`, `recoil_push_back`, `recoil_spring_stiffness`, `recoil_spring_damping`, `recoil_max_kicks`), so each gun can have its own. Leaving it out gives today's values; if present, all seven are required. Section 2 should list it.
- **Definition values now have ranges**, so a typo in the tuner cannot reach the server: `damage`, both spreads, both recoils and `move_speed_ads_multiplier` are 0 or more, `range` is 0 to 512, `ads_fov_multiplier` is 0.1 to 1, and spring stiffness and damping are 0 to 1. Section 2 should list them.
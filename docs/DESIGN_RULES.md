# Design rules

These apply to every change. If a rule gets in the way, raise it as an open question instead of quietly breaking it.

## 1. Project layout

```
src/main/java/com/noxloveya/mcweapons/
  McWeapons.java              common entrypoint (registration only)
  registry/                   ModItems, ModComponents, ModSounds, ModEntities...
  gun/                        GunItem, GunDefinition, GunDefinitions (loader), GunState
  network/                    payload records + server handlers
  combat/                     hitscan, damage, spread
src/client/java/com/noxloveya/mcweapons/client/
  McWeaponsClient.java        client entrypoint
  render/                     GeckoLib renderers and models, viewmodel transforms
  input/                      keybinds, client input handling
  mixin/                      client mixins (keep them few and small)
src/main/resources/
  assets/mcweapons/geo/item/<gun>.geo.json
  assets/mcweapons/animations/item/<gun>.animation.json
  assets/mcweapons/textures/item/<gun>.png
  assets/mcweapons/sounds/<gun>/*.ogg
  data/mcweapons/guns/<gun>.json                gun definition (stats)
blockbench/<gun>.bbmodel     source file for each model, always committed
```

Use Loom's split source sets (`splitEnvironmentSourceSets()`), so client-only code never loads on a server.

## 2. Code rules

- **Guns are data, not classes.** Each gun is one `GunItem` instance plus a JSON gun definition, which holds damage, RPM, mag size, reload time, spread, recoil, ADS zoom and fire modes. A new gun should take a model, a definition and a registry line, and no new Java class.
- **Item state uses data components** (1.21 style), not NBT. In phase 1 those are `mcweapons:ammo` (int) and `mcweapons:gun_state` (for example reloading and the last shot tick). Attachments get their own component in phase 2.
- **Client vs server.** The server owns ammo, fire rate, reloads, damage and hit detection. The client sends intents (`ShootPayload`, `ReloadPayload`, `AimPayload`) and plays effects and animations straight away, so firing feels instant. The server checks each intent against the definition (fire rate, ammo, not reloading) and ignores invalid ones.
- **Hitscan in phase 1.** The server raycasts from the eye along the look vector, with spread applied, up to `range`. It checks blocks first and the nearest entity second. No projectile entities.
- **Names.** Use Mojang names everywhere, ids in `snake_case` (`mcweapons:glock17`), and Java classes in PascalCase. No abbreviations other than `ads`, `rpm` and `fov`.
- **Mixins are a last resort.** Use Fabric API events and GeckoLib hooks first. Every mixin gets a one-line comment saying why there was no API way to do it.
- **Timing is in ticks** (20 per second) in definitions. Animations are in seconds. Rule 3.6 says how the two line up.

## 3. Blockbench and asset rules

These are for whoever builds the models. Making them through a Blockbench MCP or by writing `.bbmodel` or geo JSON directly are both fine, as long as the output follows these rules.

1. **Format.** Use the GeckoLib Animated Model format with the GeckoLib Blockbench plugin, and choose the *Item* model type. Export `geo.json` and `animation.json` to the paths in section 1, and save the `.bbmodel` to `blockbench/`.
2. **Scale.** 16 Blockbench units are 1 block. A pistol is about 10 to 12 units long, so it reads at roughly real size next to the player's arms.
3. **Texture.** Use 64x64 per gun if it fits and 128x128 at most. Use box UV where possible and a flat, readable palette that matches vanilla, with no photo textures. Arm bones do not use this texture (see 3.5).
4. **Required bones.** Names are lowercase `snake_case`, and the hierarchy is fixed:
   ```
   root
   ├── gun                 everything that moves with the gun
   │   ├── slide           (or bolt / charging_handle on other guns)
   │   ├── trigger
   │   ├── magazine        must be able to detach for reloads
   │   ├── muzzle          empty locator at the barrel tip, for flash and smoke
   │   └── ads_anchor      empty locator on the sight line, for aiming
   ├── arm_right
   └── arm_left
   ```
   Extra bones are fine, but these names cannot change, because the code looks them up by name. Phase 2 will add `attach_*` slot bones under `gun`.
5. **Arms.** The `arm_left` and `arm_right` bones each hold one 4x12x4 cube, using the vanilla player arm UV layout on a 64x64 sheet (Steve/wide). The renderer draws any bone that starts with `arm_` using the player's own skin. Pose the arms so the gun sits lower right in view, as in a typical FPS.
6. **Animations.** Names follow `animation.<gun>.<action>`. The required actions are:
   | action | loop | rule |
   |---|---|---|
   | `idle` | loop | subtle breathing, at most 0.3 units of movement |
   | `draw` | once | must match `draw_ticks` in the definition |
   | `fire` | once | must be shorter than one shot interval (60 / RPM seconds) |
   | `reload` | once | tactical reload with a round chambered. Must match `reload_ticks` |
   | `reload_empty` | once | includes racking the slide. Must match `reload_empty_ticks` |
   | `inspect` | once | about 2 to 4 seconds, cosmetic only |

   Animation length in seconds times 20 must equal the tick value in the definition, to within 1 tick. The code times reloads by the definition, so a mismatch shows up as a visual desync.
7. **Sounds and events.** Put sounds on animation keyframes using GeckoLib sound keyframes (`mcweapons:<gun>.mag_out`, `.mag_in`, `.slide_back`, `.slide_release`). The gunshot sound is played by the code, not the animation.
8. **Do not bake recoil or sway into animations.** The code adds those procedurally (section 4). The `fire` animation only moves the slide, flicks the hammer, and adds a small kick of at most 2 degrees.

## 4. Viewmodel rules

- The gun and arms are one GeckoLib model, drawn in place of the vanilla first-person hand when a gun is held.
- The code adds these on top of the animation, in this order: **ADS lerp, then sway (from mouse movement), then bob (from walking), then recoil (procedural, with spring decay)**.
- With ADS fully on, `ads_anchor` must sit at the center of the screen at the camera's height, and sway and bob drop to 20%.
- The FOV change from ADS comes from `ads_fov_multiplier` in the definition and lerps over `ads_ticks`.
- Third person is out of scope for phase 1. Use the same model scaled down in the right hand with the arm bones hidden, which is good enough.

## 5. Definition of done (every task)

- `./gradlew build` passes, and the game launches with `./gradlew runClient`.
- No errors in the log when you hold, fire, and reload.
- The acceptance checks in the prompt are each reported as done or not done, with any deviations written up in the spec's Open questions.

# Spec 01: Foundation and the Glock

**Status:** not started
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
- **Item model.** No `models/item/glock17.json` was exported. When 01c switches to `GeoItemRenderer`, the item model needs the parent `builtin/entity`.

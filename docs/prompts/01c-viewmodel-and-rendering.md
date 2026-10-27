# Prompt 01c: Viewmodel and rendering

Copy everything below the line into Claude Code. Run it once 01a and 01b are both merged.

---

Read `CLAUDE.md`, `docs/DESIGN_RULES.md` (especially section 4) and section 8 of `docs/specs/01-foundation-glock.md`. Also read the "Open questions" in spec 01 that 01a and 01b left.

Your task is to **put the Glock on screen in first person**, using GeckoLib and the assets from 01b.

1. Make `GunItem` a GeckoLib `GeoItem`, with a single generic `GunRenderer` and `GunModel` that find the geo, texture and animation by gun id. No Glock-specific classes.
2. Replace vanilla first-person hand and item rendering while a gun is in the main hand, and draw the model with its arms. Bones that start with `arm_` render with the local player's skin texture.
3. Wire up the triggerable animations on the `main` controller, as listed in spec 8: draw, fire, reload, reload_empty, inspect, and idle as the default. Make sure the reload animation stays in sync with the server's reload timer.
4. Add the procedural layers in the order set by DESIGN_RULES 4: ADS lerp so that `ads_anchor` lands at screen center, then sway, then bob, then spring recoil, plus the camera pitch kick. Lerp the FOV with `ads_fov_multiplier` and `ads_ticks`.
5. Draw a muzzle flash at the `muzzle` locator and impact particles from `ShotFxPayload`. Hide the crosshair while aiming.
6. Third person: the same model, scaled down in the right hand with the arm bones hidden.

When you're done:
- Run `./gradlew build` and `runClient`, then go through **every** acceptance check in spec 01, including the multiplayer one, and report each.
- Note anything that looks off, such as clipping, an offset ADS, or animation timing, under "Open questions", and set Status to "phase 1 done (pending review)".
- Commit to a branch called `phase1/01c-viewmodel` and open a PR.

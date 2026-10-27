# Prompt 01a: Scaffold and gun framework

Copy everything below the line into Claude Code, running in the repo root.

---

Read `CLAUDE.md`, `docs/PLAN.md`, `docs/DESIGN_RULES.md` and `docs/specs/01-foundation-glock.md` before writing any code.

Your task is **sections 1 to 7 of spec 01**: the Fabric scaffold and the gun framework, with no custom rendering yet.

1. Scaffold the Fabric 1.21.1 mod as described in spec section 1. Look up the current stable versions of Loom, Fabric Loader, Fabric API, Parchment and GeckoLib Fabric 1.21.1 (4.x), and pin them in `gradle.properties`. Use the project layout in DESIGN_RULES section 1.
2. Add the GeckoLib dependency now, even though rendering comes in prompt 01c.
3. Build the definition loader (spec section 2), including syncing to clients, and add `data/mcweapons/guns/glock17.json` with the values from the spec.
4. Build `GunItem`, the `ammo` and `gun_state` data components, the tooltip, and the creative tab (section 3). Until 01c, give the Glock a plain flat item texture as a placeholder.
5. Add the keybinds and suppress the vanilla attack and use actions (section 4).
6. Build shooting, reloading and aiming on the server, along with the payloads (sections 5 to 7). Keep `AmmoSource.take` as the only place that will consume ammo later. Add the `mcweapons:bullet` damage type and its death message, and placeholder sounds (any short vanilla sounds re-registered under `mcweapons:glock17.fire` and `mcweapons:glock17.dry` are fine).
7. On the client, fire `ShootPayload` on click with the RPM gate predicted locally, and draw the HUD ammo counter. For now, use vanilla particles for impacts and skip the muzzle flash.

Constraints: there should be no Glock-specific Java classes, mixins only where Fabric API has no hook (each with a comment saying why), and no rendering work beyond the placeholder.

When you're done:
- Run `./gradlew build` and confirm `./gradlew runClient` starts.
- Go through the acceptance checks in spec 01 that don't involve animations or the viewmodel, and report each one as done, not done, or not tested.
- Write anything you had to decide that the spec didn't cover under "Open questions" in spec 01, and set Status to "01a done".
- Commit to a branch called `phase1/01a-framework` and open a PR.

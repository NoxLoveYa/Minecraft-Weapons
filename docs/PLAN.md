# Roadmap

Fabric gun mod for Minecraft 26.3. Guns are animated GeckoLib models made in Blockbench, shown in a custom first-person viewmodel.

**Current phase: 1 (Foundation and the Glock)**

## Roles

- **Planning (Claude in the project thread):** writes the roadmap, design rules, specs and prompts in `docs/`.
- **Building (bombo's Claude Code):** writes the code and makes the Blockbench models and animations from those prompts.
- **bombo:** decides, reviews, and playtests.

## Phase 1: Foundation and the Glock

Spec: `docs/specs/01-foundation-glock.md`

- Fabric project scaffold with GeckoLib, building and launching in dev.
- A reusable gun framework: gun item, gun definition data, server-side shooting, reload, and networking.
- First-person viewmodel: the gun and arms are drawn from one GeckoLib model, with sway, bob, recoil, and aiming down sights.
- The Glock 17: Blockbench model, texture, and the animations idle, draw, fire, reload, reload_empty, and inspect.

Done when you can hold the Glock, aim, fire, run the mag dry, and reload in a dev world, with every animation playing and the shots hitting what the crosshair is on.

## Phase 2: Attachments

Attachment slots on each gun (optic, muzzle, underbarrel, magazine), shown on the model by toggling bones, with stats that change by attachment and a screen for fitting them. A spec will be written when phase 1 is done.

## Phase 3: Ammo and crafting

Ammo items for each caliber, reloads that use up ammo from your inventory, and recipes for ammo, guns and attachments (probably a gunsmith workbench). A spec will be written when phase 2 is done.

## Phase 4: Extend

More guns built on the framework (for example a rifle, an SMG and a shotgun), a config file, balance passes, and sounds. Optionally multiplayer polish and support for other loaders.

## Decisions log

- 2026-10-27: Fabric over NeoForge. bombo's call.
- 2026-10-27: Minecraft 1.21.1 with GeckoLib 4.x. This is the most stable Fabric target for GeckoLib. GeckoLib 5 on 1.21.4 and later reworks the rendering API, so we can move once phase 1 is solid if we want to.
- 2026-10-27: Moved to Minecraft **26.3**, Fabric Loader 0.19.5 or later, Java 25 and **GeckoLib 5** (5.5.7), to match the game bombo plays on. This replaces the entry above. Minecraft 26.x ships unobfuscated with Mojang names, so there is no mappings or Parchment layer anymore. bombo's call.

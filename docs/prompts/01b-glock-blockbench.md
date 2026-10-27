# Prompt 01b: Glock 17 model and animations

Copy everything below the line into Claude Code. This prompt does not depend on 01a, so it can run at the same time.

---

Read `CLAUDE.md`, `docs/DESIGN_RULES.md` (especially section 3) and section 9 of `docs/specs/01-foundation-glock.md`.

Your task is to make the **Glock 17 first-person model, texture and animations** for GeckoLib. Use Blockbench through whatever Blockbench tooling you have. If you have none, write the `.bbmodel` and the exported GeckoLib JSON by hand, following the GeckoLib geo and animation format.

Deliverables:
- `blockbench/glock17.bbmodel`, the source file
- `src/main/resources/assets/mcweapons/geo/item/glock17.geo.json`
- `src/main/resources/assets/mcweapons/animations/item/glock17.animation.json`
- `src/main/resources/assets/mcweapons/textures/item/glock17.png` (64x64)

Requirements:
1. The model type is a GeckoLib *Item* model, at the scale in DESIGN_RULES 3.2, with box UV.
2. Use the bone hierarchy in DESIGN_RULES 3.4 exactly: `root`, then `gun` (holding `slide`, `trigger`, `magazine`, `muzzle`, `ads_anchor`), then `arm_right` and `arm_left`. `muzzle` and `ads_anchor` are empty locator bones. `ads_anchor` sits on the line between the rear and front sights, and `muzzle` sits at the barrel tip.
3. The arms each have one 4x12x4 cube with the vanilla player arm UV layout (DESIGN_RULES 3.5). Pose them holding the gun, lower right of view, with a two-handed grip.
4. Animations: `animation.glock17.idle` (looping), `draw` (0.5s), `fire` (0.1s, which must be under 0.15s), `reload` (exactly 2.0s), `reload_empty` (exactly 2.5s) and `inspect` (about 3s), following the notes in spec section 9 and the rules in DESIGN_RULES 3.6 to 3.8. Do not bake recoil or sway into them.
5. Add sound keyframes for `mcweapons:glock17.mag_out`, `mag_in`, `slide_back` and `slide_release` at the right moments in both reload animations.
6. Texture: dark polymer frame, slightly lighter slide with serration lines, a readable flat palette, and no noise.

When you're done:
- Validate the JSON (it must parse, with no duplicate bone names), and confirm each animation's length matches the table in DESIGN_RULES 3.6.
- Render or describe front, side and top previews, and list every bone with its pivot so the code side can rely on them.
- Commit to a branch called `phase1/01b-glock-assets` and open a PR.

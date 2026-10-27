# Minecraft-Weapons: rules for Claude Code

This is a Fabric gun mod for Minecraft. Planning and design live in `docs/`. Read these before doing any task:

1. `docs/PLAN.md` is the roadmap and shows the current phase.
2. `docs/DESIGN_RULES.md` has the conventions every change must follow, for code and for Blockbench assets.
3. `docs/specs/` has one spec per phase. The spec for the phase you are working on is the source of truth for what to build.

Tasks usually arrive as prompts copied from `docs/prompts/`. Each prompt names its spec and its acceptance checks.

## Ground rules

- Stack is fixed: Fabric, Minecraft **1.21.1**, Java 21, Fabric Loom with **Mojang mappings** (plus Parchment), Fabric API, and **GeckoLib 4.x for Fabric 1.21.1**. Do not change it without a decision recorded in `docs/PLAN.md`.
- Mod id is `mcweapons` and the base package is `com.noxloveya.mcweapons`.
- Gameplay logic runs on the server. The client only predicts and presents (see DESIGN_RULES, "Client vs server").
- Do not invent features outside the current spec. If something in the spec is unclear or does not work, write it down under "Open questions" at the bottom of that spec and choose the simplest option.
- Each task ends with `./gradlew build` passing and the acceptance checks in the prompt reported as done or not done.
- Do not edit `docs/PLAN.md`, `docs/DESIGN_RULES.md` or the specs except for the "Open questions" and "Status" sections. The planning side owns those files.

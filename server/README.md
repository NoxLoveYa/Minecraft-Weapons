# Test server practice range

`practice_range/` is a datapack for the superflat test server. It builds a walled shooting range, facing south from the firing line at z 2:

- A roofed, lit lobby behind the firing line (z -8 to 1), with doors out at the back (the entry) and on both sides. The range itself has no roof.
- The world spawn is just inside the entry (0 -60 -7, with `respawn_radius` 0), and the gun wall is on the back wall just east of the entry: right-click a gun on it (Glock 17, M4A1, Mosin-Nagant) for one of it, one of each per player (advancements `practice:take_<gun>`).
- Static targets, two rows at 12 and 30 blocks: green husk 5 HP (one shot), yellow vindicator 20 HP, red iron golem 60 HP.
- Moving targets: four lanes, strafing 20 blocks at 2, 4 or 5 m/s.
- A killed target drops and stands back up 3 seconds later.
- Bullet drop lane: boards at 10, 25, 40, 60 and 100 m. Stand on a green spot and aim at the black block at eye height.
- A 1v1 arena through the lobby's east door (inside x 40 to 60, z -18 to 10), with a red and a blue spawn pad at its two ends and cover that looks the same from either one. Stepping in puts you in survival and makes the arena your respawn point. After a death you come back on the pad furthest from the other player in the arena (either pad if you are alone). Leaving puts you back in creative with the world spawn as respawn. Building it turns on gamerules `keep_inventory` and `pvp`.

## Install or rebuild

1. Copy `practice_range/` into `world/datapacks/` and run `reload` (or restart).
2. Load the area, build, and unload it again:

   ```
   forceload add -29 -20 62 105
   function practice:build
   forceload remove -29 -20 62 105
   ```

   Wait a few seconds after `forceload add` so the chunks are loaded before building. `practice:build` clears and rebuilds everything it placed, so it is safe to run again. To rebuild only the arena, use `forceload add 31 -20 62 12` and `function practice:arena_build`.

The server also runs with `gamemode=creative`, `force-gamemode=true` and gamerule `mob_drops false`; the datapack does not set those.

## Ready-made world

`practice-world.zip` is a copy of the test server's world (2026-10-28) with the range and the arena already built and the datapack inside. Unzip it into `.minecraft/saves/` (or use its folder as a server's `world`) and play it with the mod. It leaves out the players' data and the server's gun tuning overrides. The datapack source is `practice_range/`: after changing it, rebuild as above rather than editing the copy in the zip.

`mc.sh <file>` sends each line of a file to the server console (lines starting with `sleep` just wait) and prints the log since then. The file needs LF line endings.

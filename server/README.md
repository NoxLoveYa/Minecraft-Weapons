# Test server practice range

`practice_range/` is a datapack for the superflat test server. It builds a walled shooting range, facing south from the firing line at z 2:

- A roofed, lit lobby behind the firing line (z -8 to 1), with doors out at the back (the entry) and on both sides. The range itself has no roof.
- The world spawn is just inside the entry (0 -60 -7, with `respawn_radius` 0), and the gun table is in front of it: right-click it for a Glock 17, one per player (advancement `practice:take_gun`). A longer table to its right gives a Mosin-Nagant the same way (`practice:take_mosin`).
- Static targets, two rows at 12 and 30 blocks: green husk 5 HP (one shot), yellow vindicator 20 HP, red iron golem 60 HP.
- Moving targets: four lanes, strafing 20 blocks at 2, 4 or 5 m/s.
- A killed target drops and stands back up 3 seconds later.
- Bullet drop lane: boards at 10, 25, 40, 60 and 100 m. Stand on a green spot and aim at the black block at eye height.

## Install or rebuild

1. Copy `practice_range/` into `world/datapacks/` and run `reload` (or restart).
2. Load the area, build, and unload it again:

   ```
   forceload add -29 -9 33 105
   function practice:build
   forceload remove -29 -9 33 105
   ```

   Wait a few seconds after `forceload add` so the chunks are loaded before building. `practice:build` clears and rebuilds everything it placed, so it is safe to run again.

The server also runs with `gamemode=creative`, `force-gamemode=true` and gamerule `mob_drops false`; the datapack does not set those.

`mc.sh <file>` sends each line of a file to the server console (lines starting with `sleep` just wait) and prints the log since then. The file needs LF line endings.

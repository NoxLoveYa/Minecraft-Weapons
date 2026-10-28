# The 1v1 arena, east of the range: a corridor from the lobby's east door (x 33, z -4) into a walled arena (inside x 40 to 60, z -18 to
# 10) with two spawn pads facing each other at its ends and cover that is the same seen from either. Inside it a player plays in survival
# and respawns there, at the pad furthest from the other player (tick, arena_enter, arena_respawn). Safe to run again: it rebuilds from
# scratch. The chunks must be loaded first (forceload add 33 -20 62 12); practice:build runs it too.
kill @e[tag=pr_arena_part]
fill 34 -61 -19 61 -61 11 smooth_stone
fill 34 -60 -19 61 -50 11 air

# The corridor, roofed, lit at its end.
fill 34 -60 -6 38 -56 -6 stone_bricks
fill 34 -60 -2 38 -56 -2 stone_bricks
fill 34 -56 -5 38 -56 -3 stone_brick_slab[type=bottom]
setblock 36 -56 -4 sea_lantern

# The arena: walls as high as the range's, a lit band round the top, a polished floor, the door in the middle of the west wall.
fill 40 -61 -18 60 -61 10 polished_andesite
fill 39 -60 -19 61 -55 -19 stone_bricks
fill 39 -60 11 61 -55 11 stone_bricks
fill 39 -60 -19 39 -55 11 stone_bricks
fill 61 -60 -19 61 -55 11 stone_bricks
fill 39 -56 -19 61 -56 -19 sea_lantern
fill 39 -56 11 61 -56 11 sea_lantern
fill 39 -56 -18 39 -56 10 sea_lantern
fill 61 -56 -18 61 -56 10 sea_lantern
setblock 39 -60 -4 spruce_door[facing=east,half=lower]
setblock 39 -59 -4 spruce_door[facing=east,half=upper]

# Spawn pads: red at the north end facing south, blue at the south end facing north.
fill 49 -61 -18 51 -61 -16 red_concrete
fill 49 -61 8 51 -61 10 blue_concrete
summon marker 50.5 -60 -16.5 {Tags:["pr","pr_arena_part","pr_arena_spawn"],Rotation:[0f,0f]}
summon marker 50.5 -60 9.5 {Tags:["pr","pr_arena_part","pr_arena_spawn"],Rotation:[180f,0f]}

# Cover, the same turned half round about the middle (50.5, -3.5): a pillar in the middle, a pillar by each long wall, four low walls,
# and two crates by each pad.
fill 49 -60 -5 51 -58 -3 stone_bricks
fill 42 -60 -5 42 -58 -3 stone_bricks
fill 58 -60 -5 58 -58 -3 stone_bricks
fill 44 -60 -10 46 -59 -10 cobblestone_wall
fill 54 -60 -10 56 -59 -10 cobblestone_wall
fill 44 -60 2 46 -59 2 cobblestone_wall
fill 54 -60 2 56 -59 2 cobblestone_wall
setblock 47 -60 -14 barrel
setblock 53 -60 -14 barrel
setblock 47 -60 6 barrel
setblock 53 -60 6 barrel

# The sign hangs in the lobby, in front of its east door.
summon text_display 31.5 -57.4 -3.5 {Tags:["pr","pr_arena_part"],billboard:"center",transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[0.5f,0.5f,0.5f]},text:{text:"1v1 Arena\n",color:"gold",extra:[{text:"survival inside\nyou respawn on the pad\nfurthest from the other player",color:"white"}]}}

# Dying there keeps what you carry, and players can hurt each other.
gamerule keep_inventory true
gamerule pvp true

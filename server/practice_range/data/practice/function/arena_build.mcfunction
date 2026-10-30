# The 1v1 arena, east of the range: a corridor from the lobby's east door (x 33, z -4) into a walled arena (inside x 40 to 100, z -34 to
# 26) with two spawn pads facing each other at its ends and cover that is the same seen from either. Inside it a player plays in survival
# and respawns there, at the pad furthest from the other player (tick, arena_enter, arena_respawn). Safe to run again: it rebuilds from
# scratch. The chunks must be loaded first (forceload add 31 -36 102 28: from x 31, as the sign stands in the lobby); practice:build
# runs it too.
kill @e[tag=pr_arena_part]
fill 34 -61 -35 101 -61 27 smooth_stone
# In two parts, as one fill may change at most 32768 blocks.
fill 34 -60 -35 101 -50 -4 air
fill 34 -60 -3 101 -50 27 air

# The corridor, roofed, lit at its end.
fill 34 -60 -6 38 -56 -6 stone_bricks
fill 34 -60 -2 38 -56 -2 stone_bricks
fill 34 -56 -5 38 -56 -3 stone_brick_slab[type=bottom]
setblock 36 -56 -4 sea_lantern

# The arena: walls as high as the range's, a lit band round the top, a polished floor, the door in the middle of the west wall.
fill 40 -61 -34 100 -61 26 polished_andesite
fill 39 -60 -35 101 -55 -35 stone_bricks
fill 39 -60 27 101 -55 27 stone_bricks
fill 39 -60 -35 39 -55 27 stone_bricks
fill 101 -60 -35 101 -55 27 stone_bricks
fill 39 -56 -35 101 -56 -35 sea_lantern
fill 39 -56 27 101 -56 27 sea_lantern
fill 39 -56 -34 39 -56 26 sea_lantern
fill 101 -56 -34 101 -56 26 sea_lantern
setblock 39 -60 -4 spruce_door[facing=east,half=lower]
setblock 39 -59 -4 spruce_door[facing=east,half=upper]

# Spawn pads: red at the north end facing south, blue at the south end facing north.
fill 69 -61 -34 71 -61 -32 red_concrete
fill 69 -61 24 71 -61 26 blue_concrete
summon marker 70.5 -60 -32.5 {Tags:["pr","pr_arena_part","pr_arena_spawn"],Rotation:[0f,0f]}
summon marker 70.5 -60 25.5 {Tags:["pr","pr_arena_part","pr_arena_spawn"],Rotation:[180f,0f]}

# Cover, the same turned half round about the middle (70.5, -3.5), so each line below has its twin (x, z -> 140 - x, -8 - z).
# A lit pillar in the middle and one by each long wall.
fill 69 -60 -5 71 -58 -3 stone_bricks
setblock 70 -57 -4 sea_lantern
fill 44 -60 -5 46 -58 -3 stone_bricks
fill 94 -60 -5 96 -58 -3 stone_bricks
# Wall stubs off the long walls.
fill 40 -60 -12 43 -58 -12 stone_bricks
fill 97 -60 4 100 -58 4 stone_bricks
fill 97 -60 -12 100 -58 -12 stone_bricks
fill 40 -60 4 43 -58 4 stone_bricks
# Tall pillars toward the corners.
fill 48 -60 -21 49 -57 -20 stone_bricks
fill 91 -60 12 92 -57 13 stone_bricks
fill 91 -60 -21 92 -57 -20 stone_bricks
fill 48 -60 12 49 -57 13 stone_bricks
# Walls either side of the middle lane.
fill 60 -60 -16 66 -58 -16 stone_bricks
fill 74 -60 8 80 -58 8 stone_bricks
fill 74 -60 -16 80 -58 -16 stone_bricks
fill 60 -60 8 66 -58 8 stone_bricks
# Low walls: one across the lane in front of the middle pillar, two out from each pad.
fill 68 -60 -11 72 -59 -11 cobblestone_wall
fill 68 -60 3 72 -59 3 cobblestone_wall
fill 52 -60 -26 56 -59 -26 cobblestone_wall
fill 84 -60 18 88 -59 18 cobblestone_wall
fill 84 -60 -26 88 -59 -26 cobblestone_wall
fill 52 -60 18 56 -59 18 cobblestone_wall
# Crates: two by each pad, two off the middle.
setblock 66 -60 -30 barrel
setblock 74 -60 22 barrel
setblock 74 -60 -30 barrel
setblock 66 -60 22 barrel
setblock 58 -60 -8 barrel
setblock 82 -60 0 barrel
setblock 82 -60 -8 barrel
setblock 58 -60 0 barrel

# The sign hangs in the lobby, in front of its east door.
summon text_display 31.5 -57.4 -3.5 {Tags:["pr","pr_arena_part"],billboard:"center",transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[0.5f,0.5f,0.5f]},text:{text:"1v1 Arena\n",color:"gold",extra:[{text:"survival inside\nyou respawn on the pad\nfurthest from the other player",color:"white"}]}}

# Dying there keeps what you carry, and players can hurt each other.
gamerule keep_inventory true
gamerule pvp true

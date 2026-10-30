# Lays the range out, shooting south (+Z) from the firing line at z 2: a roofed lobby behind it (z -8 to 1) with the world spawn at
# its back door and the gun wall beside the door, and the range itself walled in under the open sky. Safe to run again: it rebuilds
# from scratch, the 1v1 arena east of it too (practice:arena_build). The chunks must be loaded first (forceload add -29 -20 62 105).
kill @e[tag=pr]
fill -29 -61 -9 33 -61 105 smooth_stone
# In three parts, as one fill may change at most 32768 blocks.
fill -29 -60 -9 33 -50 30 air
fill -29 -60 31 33 -50 70 air
fill -29 -60 71 33 -50 105 air
fill -28 -61 2 32 -61 2 yellow_concrete
fill -8 -61 3 -8 -61 104 gray_concrete
fill 16 -61 3 16 -61 104 gray_concrete

# Walls all round, a roof over the lobby only, lit, and doors out: the entry at the back, one on each side.
fill -29 -60 -9 33 -55 -9 stone_bricks
fill -29 -60 105 33 -55 105 stone_bricks
fill -29 -60 -9 -29 -55 105 stone_bricks
fill 33 -60 -9 33 -55 105 stone_bricks
fill -29 -55 -9 33 -55 1 stone_brick_slab[type=bottom]
fill -28 -55 -6 32 -55 -6 sea_lantern
fill -28 -55 -1 32 -55 -1 sea_lantern
setblock 0 -60 -9 spruce_door[facing=north,half=lower]
setblock 0 -59 -9 spruce_door[facing=north,half=upper]
setblock -29 -60 -4 spruce_door[facing=west,half=lower]
setblock -29 -59 -4 spruce_door[facing=west,half=upper]
setblock 33 -60 -4 spruce_door[facing=east,half=lower]
setblock 33 -59 -4 spruce_door[facing=east,half=upper]
setworldspawn 0 -60 -7
gamerule respawn_radius 0

# The gun wall, on the back wall just east of the entry: right-click a gun for one of it (advancements practice:take_<gun>).
# A long gun's icon stands on the diagonal; it is turned back level here.
fill 2 -60 -8 12 -57 -8 spruce_planks
summon text_display 7 -57 -6.95 {Tags:["pr"],text:{text:"Gun wall\n",color:"gold",extra:[{text:"right-click a gun to take one",color:"white"}]}}
summon item_display 3.5 -58.4 -6.95 {Tags:["pr"],item:{id:"mcweapons:glock17",count:1},item_display:"fixed",transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[1f,1f,1f]}}
summon interaction 3.5 -59 -6.5 {Tags:["pr","pr_rack_glock17"],width:1.2f,height:1.2f,response:1b}
summon text_display 3.5 -59.4 -6.95 {Tags:["pr"],transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[0.6f,0.6f,0.6f]},text:{text:"Glock 17",color:"white"}}
summon item_display 6.75 -58.4 -6.95 {Tags:["pr"],item:{id:"mcweapons:m4a1",count:1},item_display:"fixed",transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,-0.3827f,0.9239f],translation:[0f,0f,0f],scale:[1.7f,1.7f,1.7f]}}
summon interaction 6.75 -59 -6.5 {Tags:["pr","pr_rack_m4a1"],width:2.2f,height:1.2f,response:1b}
summon text_display 6.75 -59.4 -6.95 {Tags:["pr"],transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[0.6f,0.6f,0.6f]},text:{text:"M4A1",color:"white"}}
summon item_display 10.25 -58.4 -6.95 {Tags:["pr"],item:{id:"mcweapons:mosin_nagant",count:1},item_display:"fixed",transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,-0.3827f,0.9239f],translation:[0f,0f,0f],scale:[1.9f,1.9f,1.9f]}}
summon interaction 10.25 -59 -6.5 {Tags:["pr","pr_rack_mosin_nagant"],width:2.6f,height:1.2f,response:1b}
summon text_display 10.25 -59.4 -6.95 {Tags:["pr"],transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[0.6f,0.6f,0.6f]},text:{text:"Mosin-Nagant",color:"white"}}

# Static targets, two rows (12 and 30 blocks out): green dies in one shot, yellow takes 4 body / 2 head, red 12 body / 6 head (Glock).
summon marker -23.5 -60 12.5 {Tags:["pr","pr_slot","pr_1shot"]}
summon marker -18.5 -60 12.5 {Tags:["pr","pr_slot","pr_std"]}
summon marker -13.5 -60 12.5 {Tags:["pr","pr_slot","pr_tank"]}
summon marker -23.5 -60 30.5 {Tags:["pr","pr_slot","pr_1shot"]}
summon marker -18.5 -60 30.5 {Tags:["pr","pr_slot","pr_std"]}
summon marker -13.5 -60 30.5 {Tags:["pr","pr_slot","pr_tank"]}

# Moving targets, strafing 20 blocks across their lane at walk (2 m/s), run (4 m/s) and sprint (5 m/s).
summon marker -5.5 -60 14.5 {Tags:["pr","pr_slot","pr_mover","pr_slow","pr_1shot"],Rotation:[-90f,0f]}
summon marker -5.5 -60 22.5 {Tags:["pr","pr_slot","pr_mover","pr_mid","pr_std"],Rotation:[-90f,0f]}
summon marker -5.5 -60 32.5 {Tags:["pr","pr_slot","pr_mover","pr_fast","pr_1shot"],Rotation:[-90f,0f]}
summon marker -5.5 -60 44.5 {Tags:["pr","pr_slot","pr_mover","pr_mid","pr_tank"],Rotation:[-90f,0f]}
fill -6 -61 14 14 -61 14 light_gray_concrete
fill -6 -61 22 14 -61 22 light_gray_concrete
fill -6 -61 32 14 -61 32 light_gray_concrete
fill -6 -61 44 14 -61 44 light_gray_concrete

# Bullet drop lane: stand on a green spot, aim at the black block at eye height, see where the shot lands on its board.
function practice:board {x:19,z:12,lx:18,rx:20,end:11,label:"10 m"}
function practice:board {x:22,z:27,lx:21,rx:23,end:26,label:"25 m"}
function practice:board {x:25,z:42,lx:24,rx:26,end:41,label:"40 m"}
function practice:board {x:28,z:62,lx:27,rx:29,end:61,label:"60 m"}
function practice:board {x:31,z:102,lx:30,rx:32,end:101,label:"100 m"}

summon text_display -18.5 -57 3.5 {Tags:["pr"],billboard:"center",text:{text:"Static targets\n",color:"gold",extra:[{text:"green 5 HP · yellow 20 HP · red 60 HP\nthey drop when killed and stand back up 3 s later",color:"white"}]}}
summon text_display 4.5 -57 3.5 {Tags:["pr"],billboard:"center",text:{text:"Moving targets\n",color:"gold",extra:[{text:"walk · run · sprint speed",color:"white"}]}}
summon text_display 25 -57 3.5 {Tags:["pr"],billboard:"center",text:{text:"Bullet drop lane\n",color:"gold",extra:[{text:"stand on a green spot, aim at the black block",color:"white"}]}}

function practice:arena_build

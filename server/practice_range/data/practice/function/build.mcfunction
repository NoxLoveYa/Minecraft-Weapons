# Lays the range out in front of the world spawn (0 -60 0), shooting south (+Z). Safe to run again: it rebuilds from scratch.
# The chunks must be loaded first (forceload add -28 -4 32 104).
kill @e[tag=pr]
fill -28 -61 -4 32 -61 104 smooth_stone
fill -28 -60 -4 32 -50 104 air
fill -28 -61 2 32 -61 2 yellow_concrete
fill -8 -61 3 -8 -61 104 gray_concrete
fill 16 -61 3 16 -61 104 gray_concrete
setworldspawn 0 -60 0

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

# A player has left the arena: creative again, and they respawn at the world spawn.
tag @s remove pr_in_arena
team leave @s
gamemode creative @s
spawnpoint @s 0 -60 -7 0 0
title @s actionbar {text:"Left the arena",color:"gray"}

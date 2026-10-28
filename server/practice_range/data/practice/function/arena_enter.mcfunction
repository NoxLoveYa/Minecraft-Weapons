# A player has stepped into the arena: survival, and they now respawn in it (on the red pad, moved to the safer one by arena_respawn).
tag @s add pr_in_arena
scoreboard players reset @s pr_deaths
gamemode survival @s
spawnpoint @s 50 -60 -17 0 0
title @s actionbar {text:"1v1 Arena: survival, you respawn here",color:"gold"}

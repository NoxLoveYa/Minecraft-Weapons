# Food and saturation always full, in survival too, so natural regeneration runs at its fastest. Hidden (no particles, no icon);
# given every tick as dying clears it (a no-op while it is on).
effect give @a minecraft:saturation infinite 0 true
execute as @e[type=marker,tag=pr_mover] at @s run function practice:move
execute as @e[type=marker,tag=pr_slot] at @s run function practice:slot

# The 1v1 arena (inside x 40 to 100, z -34 to 26): survival inside and respawning there; creative and the world spawn once out again.
# Kept in survival while in it, as joining puts everyone in creative (force-gamemode).
execute as @e[type=player,tag=!pr_in_arena,x=40,y=-61,z=-34,dx=60,dy=9,dz=60] run function practice:arena_enter
execute as @e[type=player,tag=pr_in_arena] unless entity @s[x=40,y=-61,z=-34,dx=60,dy=9,dz=60] run function practice:arena_leave
execute as @e[type=player,tag=pr_in_arena,scores={pr_deaths=1..}] run function practice:arena_respawn
gamemode survival @e[type=player,tag=pr_in_arena,gamemode=!survival]
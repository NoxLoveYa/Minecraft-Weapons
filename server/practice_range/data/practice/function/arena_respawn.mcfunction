# Run as a player who has just respawned in the arena: onto the pad furthest from the nearest other player there, or either pad alone.
scoreboard players reset @s pr_deaths
tag @s add pr_respawning
execute unless entity @e[type=player,tag=pr_in_arena,tag=!pr_respawning] as @e[type=marker,tag=pr_arena_spawn,sort=random,limit=1] at @s rotated as @s run tp @a[tag=pr_respawning,limit=1] ~ ~ ~ ~ ~
execute at @e[type=player,tag=pr_in_arena,tag=!pr_respawning,sort=nearest,limit=1] as @e[type=marker,tag=pr_arena_spawn,sort=furthest,limit=1] at @s rotated as @s run tp @a[tag=pr_respawning,limit=1] ~ ~ ~ ~ ~
tag @s remove pr_respawning

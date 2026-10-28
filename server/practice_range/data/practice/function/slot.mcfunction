# A slot keeps its target pinned on it; once the target is dead, a new one stands up 3 seconds later.
execute if entity @e[tag=pr_target,distance=..1.5] run scoreboard players set @s pr_cd 0
execute unless entity @e[tag=pr_target,distance=..1.5] run scoreboard players add @s pr_cd 1
execute if score @s pr_cd matches 60.. run function practice:spawn
tp @e[tag=pr_target,distance=..1.5,limit=1,sort=nearest] ~ ~ ~

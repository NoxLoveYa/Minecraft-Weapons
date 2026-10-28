# Each mover walks 20 blocks along its lane, then turns around.
scoreboard players add @s pr_t 1
execute if entity @s[tag=pr_slow] run tp @s ^ ^ ^0.1
execute if entity @s[tag=pr_mid] run tp @s ^ ^ ^0.2
execute if entity @s[tag=pr_fast] run tp @s ^ ^ ^0.25
execute if entity @s[tag=pr_slow,scores={pr_t=200..}] run function practice:turn
execute if entity @s[tag=pr_mid,scores={pr_t=100..}] run function practice:turn
execute if entity @s[tag=pr_fast,scores={pr_t=80..}] run function practice:turn

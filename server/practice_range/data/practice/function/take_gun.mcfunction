# Run by advancement practice:take_gun when a player right-clicks the gun table: one Glock each, unless they already carry one.
advancement revoke @s only practice:take_gun
execute if items entity @s container.* mcweapons:glock17 run title @s actionbar {text:"You already have a Glock 17",color:"yellow"}
execute unless items entity @s container.* mcweapons:glock17 run give @s mcweapons:glock17

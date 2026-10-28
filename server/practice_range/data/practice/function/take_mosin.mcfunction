# Run by advancement practice:take_mosin when a player right-clicks the Mosin table: one each, unless they already carry one.
advancement revoke @s only practice:take_mosin
execute if items entity @s container.* mcweapons:mosin_nagant run title @s actionbar {text:"You already have a Mosin-Nagant",color:"yellow"}
execute unless items entity @s container.* mcweapons:mosin_nagant run give @s mcweapons:mosin_nagant

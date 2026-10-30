# One of a gun from the gun wall each, unless they already carry one. Arguments: id (item path in mcweapons) and name.
$execute if items entity @s container.* mcweapons:$(id) run title @s actionbar {text:"$(name): you already have one",color:"yellow"}
$execute unless items entity @s container.* mcweapons:$(id) run give @s mcweapons:$(id)

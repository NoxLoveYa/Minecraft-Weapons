scoreboard objectives add pr_cd dummy
scoreboard objectives add pr_t dummy
scoreboard objectives add pr_deaths deathCount
# Kept on at every start: nobody drops their guns on dying (the arena), the world spawn is exact, and players who are not operators
# cannot break or place blocks, creative or not, but open doors (the mod's gamerule; the server runs with spawn-protection 0, as
# vanilla's spawn protection would lock the doors too). Health regenerates only after 5 s without taking damage (the mod's gamerule).
gamerule keep_inventory true
gamerule respawn_radius 0
gamerule mcweapons:protect_blocks true
gamerule mcweapons:regen_delay 5

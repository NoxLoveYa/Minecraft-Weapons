# Minecraft Weapons

Fabric gun mod for Minecraft 26.3 (Fabric Loader 0.19.5 or later), built on GeckoLib 5. Planning lives in `docs/` (start with `docs/PLAN.md`).

## Requirements

- JDK 25. Gradle downloads everything else through the wrapper.

## Build and run

```sh
./gradlew build        # mod jar in build/libs/, also runs the tests
./gradlew runClient    # dev client
./gradlew runServer    # dev dedicated server
```

On Windows use `gradlew.bat`. If your default `java` is not 25, set `JAVA_HOME` to a JDK 25 first, for example in PowerShell:

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-25'; .\gradlew.bat runClient
```

Versions are pinned in `gradle.properties`.

## Releases

A push to `main` with `[production]` anywhere in a commit message builds and tests the mod and publishes a GitHub release (`.github/workflows/release.yml`), tagged `v<mod_version>-r<run number>`, with the jar attached and the merged pull requests listed. To attach more files later (a resource pack, a datapack), add them to the `assets` list in that workflow.

## Install in a normal launcher

Put these in the `mods` folder of a Minecraft 26.3 Fabric profile (Loader 0.19.5 or later):

- `mcweapons-<version>.jar`, from the latest [release](https://github.com/NoxLoveYa/Minecraft-Weapons/releases) or `build/libs/`
- [Fabric API](https://modrinth.com/mod/fabric-api) for 26.3
- [GeckoLib](https://modrinth.com/mod/geckolib) 5.5.7 or later for Fabric 26.3

## Controls

| input | action |
|---|---|
| left mouse | fire |
| right mouse (hold) | aim down sights |
| `R` | reload |
| `V` | inspect |

`R` and `V` can be rebound under Options > Controls > Key Binds > Minecraft Weapons.

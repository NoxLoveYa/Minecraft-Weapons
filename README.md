# Minecraft Weapons

Fabric gun mod for Minecraft 1.21.1, built on GeckoLib. Planning lives in `docs/` (start with `docs/PLAN.md`).

## Requirements

- JDK 21. Gradle downloads everything else through the wrapper.

## Build and run

```sh
./gradlew build        # mod jar in build/libs/, also runs the tests
./gradlew runClient    # dev client
./gradlew runServer    # dev dedicated server
```

On Windows use `gradlew.bat`. If your default `java` is not 21, set `JAVA_HOME` to a JDK 21 first, for example in PowerShell:

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-21'; .\gradlew.bat runClient
```

Versions are pinned in `gradle.properties`.

## Controls

| input | action |
|---|---|
| left mouse | fire |
| right mouse (hold) | aim down sights |
| `R` | reload |
| `V` | inspect |

`R` and `V` can be rebound under Options > Controls > Key Binds > Minecraft Weapons.

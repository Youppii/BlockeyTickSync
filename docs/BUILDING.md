# Tick Sync builds

Tick Sync has two Fabric builds. The root project targets Minecraft 1.20.4 and Java 17. The `minecraft-26.2` project targets Minecraft 26.2 and Java 25. Each project has its own Gradle wrapper and should be built separately.

From the repository root, build Minecraft 1.20.4 using a JDK supported by its Gradle 8.14.2 wrapper (JDK 21 is suitable):

```powershell
.\gradlew.bat build
```

Build Minecraft 26.2 after selecting a JDK 25:

```powershell
.\minecraft-26.2\gradlew.bat -p .\minecraft-26.2 build
```

The distributable jars are:

- `build/libs/Tick Sync-1.2.7.jar` — Fabric, Minecraft 1.20.4.
- `minecraft-26.2/build/libs/Tick Sync-1.2.7+26.2-fabric.jar` — Fabric, Minecraft 26.2.

The 26.2 project uses official Minecraft names, Fabric Loader 0.19.3, Fabric API 0.161.0+26.2, Loom 1.17.21, and optional Mod Menu 20.0.2. The existing 1.20.4 project remains on its current dependencies and Java 17 target.

For client validation, run the 26.2 Fabric development client in isolation, with and without Mod Menu. Compare automatic and fixed margins, sync enabled and disabled, the FPS cutoff, sparse movement, pause and resume, disconnect and reconnect, settings save and reset, HUD positions and toggles, and telemetry events and shutdown. Check `latest.log` for mixin or telemetry-writer errors. A successful Gradle build verifies compilation and packaging; it does not verify in-game timing behavior.

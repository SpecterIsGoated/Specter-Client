# Specter Client 0.93.5 for Minecraft 26.3

This is a port of Specter Client 0.93.5 from Minecraft 1.21.11 to Minecraft 26.3 (Fabric).
It was rebuilt from the decompiled 1.21.11 release jar. Specter is a fork of
[Meteor Client](https://github.com/MeteorDevelopment/meteor-client) and is licensed **GPL-3.0-only** (see `LICENSE`).

## Requirements
- Minecraft 26.3, Fabric Loader 0.19.5 or newer
- Java 25

## Building
You need **JDK 25 installed**. Fabric Loom 1.19 only runs on Java 25. If your default `java` is older, Gradle picks
up an installed JDK 25 by itself (see `gradle/gradle-daemon-jvm.properties`), so it only has to be installed, not
made the default. Get it from https://adoptium.net (Temurin 25).

```
./gradlew build        # Windows: gradlew.bat build
```
The output is `build/libs/specter-0.93.5+26.3.jar`. The wrapper downloads Gradle 9.8.1.

If the build fails with `Dependency requires at least JVM runtime version 25`, no JDK 25 was found. Install one, or
set `JAVA_HOME` to it.

## How the port was done
1. **Decompiling.** The 1.21.11 jar was remapped from Fabric intermediary names to Mojang names with Loom, then
   decompiled with Vineflower. This is the first git commit.
2. **Core rebase.** `com.jfant.specter.core` is a renamed copy of Meteor Client. It was replaced with Meteor's
   26.3 source (commit `e35e6966`). Specter's own changes to Meteor were found by diffing the decompiled Meteor
   1.21.11 build against the decompiled Specter jar, and were then applied again on top: the module, HUD-element,
   command and tab sets, addon removal, the Specter config and HUD editor, branding, `getSafeFile`, and the
   section colours.
3. **API migration.** About 1,750 compile errors in Specter's own code were fixed. The main 26.x changes:
   `GuiGraphics` became `GuiGraphicsExtractor`, `render*` became `extract*`, screens and toasts moved to `mc.gui`,
   GLFW was replaced by SDL3, `ChunkPos` and many packets became records, entity and block-entity types moved to
   `EntityTypes`/`BlockEntityTypes`, colored items became `ColorCollection`s, swings and container clicks changed,
   time is now a world clock, and environment attributes now use vector types.
4. **Renderers.** The sky, world effects (bioluminescence, marine layer, colour grading) and sky lyrics were ported
   to the new `renderpearl` GPU API, which uses bind-group layouts, `DepthStencilState`, pass and draw rules, and
   `GpuFormat`. Shared helpers are in `render/GpuKit`. The shaders were updated to the 26.x conventions: `#include`,
   explicit locations, and vanilla uniform blocks. They were also changed for **reversed-Z depth**: the near plane
   is 1, the far plane is 0 or -1, and depth samples are flipped back. All shaders compile to SPIR-V with shaderc
   using Minecraft's options, for both depth conventions.
5. **Mixins.** Every mixin was checked against the 26.3 bytecode with a static validator. It checks target classes
   and methods, `@At` targets and their ordinals, handler signatures, `@Local` names, and static targets. The hook
   for "solid world done, before water" now splits LevelRenderer's main pass (`LevelRendererSolidMixin`), because
   26.x draws solid and translucent geometry in one pass.
6. **Runtime test.** The client was launched headless (Xvfb, with Mesa lavapipe on the Vulkan backend). It started
   with the dev-mode mixin audit, opened the Specter GUI and created a world. Atmosphere (sky, clouds and cloud
   shadows), Nebula Night, Time Set, Color Grading, Bioluminescence, Marine Layer and Xray were then switched on,
   and no errors were logged. This test found and fixed: SDL3 key, mouse and modifier codes (they were inlined as
   GLFW numbers), a `MouseHandler` overload, environment-attribute value types, and GPU work that ran while a pass
   was open.

## Not verified
- Compatibility with **Sodium, Lithium, Iris or Baritone**. These were compiled against their 26.3 builds and the
  mixins check out statically, but none of them was installed during the runtime test.
- The **OpenGL backend**. The headless machine could only create a Vulkan device.
- **Sky Lyrics**, which needs Spotify, and server-specific features (AH macros, staff and anticheat helpers,
  Donut modules). These compile and their mixins validate, but they were not run.
- ViaFabricPlus compat was dropped. Upstream Meteor dropped it too, and it only changed one setting's default.

## Dependency notes
- The Java Spotify API (`com.github.labystudio:java-spotify-api:1.4.7`) is published only on JitPack. The jar from
  the original release is kept in `libs/repo`.
- WaybackAuthLib was moved to the `de.florianreuth` 1.1.0 coordinates and netty to 4.2.16, matching Meteor 26.3.

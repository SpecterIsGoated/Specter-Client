# Anubis 1.0 — security scan + source/JAR verification (2026-11-23)

Files (both in `~/Downloads`, nothing was executed or built):

| file | size | sha256 |
|---|---|---|
| `Anubis Client 1.0.jar` | 21,032,265 | `e60968d360d57e8cfcd68bb127f6f56c7801ddebcff331827102596a209b15f1` |
| `anubis-1.0-src.zip` | 10,994,713 | `a8bce468071eee48b830ef0d39c999cfd729f2ca01a4209cb32fc0a105667139` |

The JAR is the same 1.0 build scanned on 2026-11-08 (same sha256). The source zip is new (downloaded 2026-11-23 01:43).

## 1. Security scan

Verdict: **no malware found** in either file. Static analysis plus Windows Defender (custom scan of the jar, the zip,
and the fully extracted trees) — Defender reported no threats. That result alone is not treated as proof; the checks below are.

| check | result |
|---|---|
| Session/token theft | No `getAccessToken` / `method_1674` / `field_1983` anywhere in source or decompiled jar. No reads of launcher account files, browser stores, Discord `leveldb`. |
| Network hosts | github.com, discord.gg, donutbets.net, i.imgur.com, youtube/tiktok (links), sessionserver/api.mojang.com, lrclib.net. Identical host set in source and jar. |
| Webhooks | Only `CoordSnapper` and `PlayerDetection` → **user-typed** webhook URL, off by default, marked private. No hardcoded webhook. |
| Process exec | `security/TamperStop` (relaunches java on *its own jar* to show a "build modified" window) and `spotify/ScriptedPlayer` (osascript / dbus-send). Same in jar and source. |
| Class loading | No `URLClassLoader`, `defineClass`, `Unsafe`. `MethodHandles` only in its event bus + an Iris shader check; `Proxy` only in seed worldgen. |
| Base64 | Profile share codes, skin JSON, glTF data URIs — all data, no code. |
| Nested jars | 7 Fabric API modules **byte-identical** to maven.fabricmc.net (sha1). 5 imgui-java jars identical to Maven Central **except** an added `fabric.mod.json` (what Loom's `include` does to plain jars) — every class CRC matches. |
| Build scripts | `build.gradle.kts` / `settings.gradle.kts`: Fabric + Modrinth maven, Loom 1.16.3, generates `BuildConfig` (debug flag + signing-cert fingerprint parts). Nothing downloads or runs code beyond normal Gradle. |
| Gradle wrapper | `gradle-wrapper.jar` sha256 `cb0da675…` = the **official Gradle 8.8 wrapper** (verified against services.gradle.org). The properties then download Gradle 9.4.1 from services.gradle.org. |
| Signing / anti-tamper | `EarlyBuildCheck` (preLaunch) verifies its own jar signature; `PayGuard` blocks `/pay` not typed by the user. Defensive, not malicious. |

## 2. Does the source match the JAR?

**Yes — the source is the 1.0 release, and it is a cleaned-up decompile, not the author's original source.**

- Class sets: 740 source files vs 741 jar classes. The only jar-only class is `BuildConfig`, which `build.gradle.kts` generates. Nothing is missing either way.
- Implementation check (string literals + declared method names, per class): **542 of 589** compared classes are identical. All 47 "differences" are decompiler artefacts: MC override names that are intermediary in the jar (`method_xxxx`), record/enum constructors, mixin target strings (Mojang name in source vs intermediary in jar), one string-concat constant. No logic differs.
- Decompile evidence: 0 comments and 0 javadoc in 740 files, chars written as ints (`indexOf(44)` 24×). So it was produced from the jar, which is why it matches so closely.
- Is it the newest Anubis? Cannot be verified: `github.com/4ldenz/Anubis-client` now returns 404 (private or deleted). 1.0 is the newest build we have.

**Reliability for study: high.** Every feature below was read in the source and checked against the decompiled jar.

## 3. Features studied

| feature | Anubis class(es) | how it works | Specter status |
|---|---|---|---|
| Sus Chunk Finder | `render/SusChunkFinderModule` | Scans each chunk for *grown* growth (amethyst clusters, max-age kelp/cave vines, vines grown down, bamboo, ripe cocoa, full bee nests); adds the count to every chunk within the sim distance ("heat"); *ungrown* growth of the same kind vetoes those chunks; only local heat peaks ≥ Sensitivity, with ≥3 scanned neighbours, are drawn. ANBS+ variant counts glowing amethyst via block light. Optional mined-geode detector (large, unlit, exposed amethyst lining). | Already ported as **Sus Chunks** (Locate = Peaks). |
| Better Sus Chunk | `render/BetterSusChunkModule` | Opposite logic: *overdue* growth (should have grown if a player were near) "clears" every chunk within sim distance. A chunk is flagged when it is NOT cleared but cleared strips surround it on ≥3 sides and enough overdue weight is nearby (support ≥ Sensitivity). Hole filter drops chunks near dug shafts. Background thread + generation counter. | Already ported as **Sus Chunks V2** (clear-strength, shaft filter). |
| Sus storage/display | both | In-memory only, pruned past render distance, drawn as flat chunk overlays + tracers. **No persistence, no list, no map, no confidence levels.** | New **Sus Finds** system adds all of these. |
| Region map | `gui/RegionMapRenderer`, `util/donut/DonutGoliaths` | 36×36 grid of 12,500-block cells (±225,000). Each goliath is a rectangle of 1–8 cells; 7 regions with 13–52 goliaths each; goliaths numbered per region in table order. | Specter's map (`Where Are You`) used an outdated 9×9 / 50k-square table → rebuilt on the goliath geometry. |
| Floating Relics | `render/FloatingRelicsModule` | Up to 32 orbs with physics (gravity, buoyancy, drag, orbit pull, swirl, follow, wander, block + orb bounces), Catmull-Rom trails, concentric low-alpha glow disks, styles. | New original **Relics** (cards / sakura / chips) on a shared particle system. |
| Relic Particles | `render/RelicParticlesModule` | Glyph swarm for totem pops, projectile trails, footsteps. | Idea reused in the particle system. |
| Spear Swap / Lunge | `combat/SpearLungeModule` + `melee/SlotReturn` | Key press → pick the hotbar spear with the highest Lunge level (skips ones about to break) → select it → `gameMode.piercingAttack(weapon)` → swing → switch back next tick. Reach Poke stabs with a spear when an enemy is past normal reach. Needs food (or flight), not in water/elytra/vehicle. | New **Spear Lunge**. |
| Attribute swapping | (no separate module in 1.0) | Done inside Mace/Spear via `SlotReturn`: take a slot, act, return it. | Spear Lunge uses the same take/act/return pattern; nothing is modified permanently. |
| Legit Auto Tool | `misc/LegitAutoToolModule` | While attack is held on a block, pick the hotbar slot with the best break delta (tool speed + efficiency attribute + haste/fatigue + air/water penalties), 2-tick human delay, skip tools about to break, return to the original slot afterwards. | New **Legit Auto Tool**. |
| Elytra Swap | `movement/ElytraSwapModule` | Chest slot ⇄ elytra swaps via inventory clicks. | Specter's own inventory sync used for **Elytra Saver**. |
| Player Bypass | `misc/PlayerBypassModule` | (studied 2026-10-12; light/entity heuristics) | Specter+ has its own, stronger version; reworked here for grouping. |

Not copied: no Anubis code was pasted. Specter's versions are written against Specter's own module/settings/render code.

# Specter feature expansion — plan and status (2026-11-23)

Branch `expansion` in `~/rel263/specter-client` (local only, not pushed). Specter+ work in `~/rel263/specterplus`
`master` (local only). Security + source/JAR report: `docs/ANUBIS_REPORT.md`.

## Installed for testing (diddyman profile, game was closed)
- `SpecterV2.1+26.3-TEST.jar` (sha256 2e82899d…) — previous TEST kept as `SpecterV2.1+26.3-TEST-prev.jar.disabled`
- `SpecterPlus-0.2.3+26.3-TEST.jar` (sha256 a2a9b4f6…, release + ProGuard, mapping committed) — 0.2.2 kept `.disabled`
- Copies in `~/Downloads/Specter-26.3-RELEASE/test/`

## Done
| area | what | where |
|---|---|---|
| Region map | Where Are You rebuilt on the real goliath layout (36×36 grid of 12.5k cells, rectangular goliaths, per-region numbers, region borders, `map-detail` size). Old tags still looked up by world position. | `modules/WhereAreYou`, `region/DonutGoliaths` |
| Sus Chunks system | **Sus Finds**: one remembered list fed by Sus Chunks + V2; grouping + merge radius (no duplicates); Confirmed / Likely / Uncertain; world beams/outlines/labels; full-screen map (`.sus-finds map` or key) with list + details; `.sus-finds list/forget/note/clear`. 7 unit tests. | `sus/*`, `modules/SusFinds`, `commands/SusFindsCommand` |
| Player Bypass (Specter+) | `merge-radius` (default 20 chunks) groups finds into one entry updated in place; "Go around the area to pinpoint the location." + % pinpointed + "pinpointed" message; location rings; amber possible vs green confirmed; label shows distance / grouped spots. | `specterplus/bypass/PlayerBypass` |
| Cosmetics core | Particle pool (flat arrays, no per-frame allocation), vector shapes, two new world renderers (opaque depth-writing + additive glow). | `cosmetic/*`, `core/renderer`, `GameRendererMixin` |
| Relics | Playing Cards / Sakura / Game Chips; 4 animation presets; count, size, speed, spread, glow, particles, custom colours. | `modules/Relics` |
| Halo | Classic / Double / Radiant / Segmented; Static / Rainbow / Pulse; height, size, thickness, spin, glow, follow-head. | `modules/Halo` |
| Wings | Angel / Fallen Angel; replace the elytra (or always / only flying); fold, idle breath, flutter, glide + climb beats; ash/motes. | `modules/AngelWings`, `WingsLayerMixin` |
| Death animations | Ice Spike / Disintegrate / Water Dissolve / Random, built from the dying player's real skin; self/others/everyone, duration, density, colours. | `modules/DeathAnimations`, `cosmetic/SkinVoxels`, `CosmeticLivingRendererMixin` |
| Items | Item Physics, Item ESP (presets: netherite, elytras, totems, diamonds, shulkers, armour, weapons, custom list; constant-width tracers; glow), Item Nametags. | `modules/Item*`, `render/ItemTargets` |
| Gameplay | Elytra Saver, Legit Auto Tool, Spear Lunge, Auto Cliper. | `modules/*` |
| Token Switcher | Account list + switch/remove/add token; tokens AES-GCM encrypted at rest (key in `~/.specter/vault.key`), legacy plaintext still loads; token accounts never display the token. 3 unit tests. | `gui/screen/TokenSwitcherScreen`, `core/systems/accounts/TokenVault` |
| Optimisation | Reused per-frame matrix in the core render hook; Sus Finds caches list + merged outlines per change; idle early-outs. New renderers cost nothing when empty. | |

## Verification
- `./gradlew test`: 36/36 client tests, 33/33 Specter+ tests.
- Dev client (smoke world): every new module switched on, items spawned, all three death animations on a second (fake) player, Token Switcher + Sus Finds map opened — no exceptions; screenshots checked (wings pose fixed after the first look).
- NOT tested: DonutSMP, real elytra crash-guard swap, spear lunge on a real spear, token switching with a real token, spread wings in flight, Player Bypass grouping on a real find.

## Skipped — needs your answer
- **Auto Mine Baser**: unclear what it should do; the closest thing (auto-mining base finder) is the paid Specter+ Tunnel Base Finder.

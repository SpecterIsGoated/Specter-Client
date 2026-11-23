# Specter feature expansion — plan (2026-11-23)

Branch `expansion` in `~/rel263/specter-client` (local only), Specter+ changes in `~/rel263/specterplus` (local only).

## Architecture found
- Meteor-fork core (`core/`): `Module(Category,name,desc)`, `SettingGroup.add(new XSetting.Builder()...)`, orbit `@EventHandler`,
  `Render3DEvent` (`renderer` = no depth, `depthRenderer` = depth-tested, coloured lines/triangles), `Render2DEvent`.
- Entity-pass custom geometry (Jet Mech): `LivingEntityRenderer.submit` HEAD → `collector.submitCustomGeometry(pose, RenderType, renderer)`;
  textures made at tick time (`DynamicTexture`), render types via `RenderLayerInvoker.specter$of`.
- Item entity hook: `RenderItemEntityEvent` (cancellable) already fired from `ItemEntityRendererMixin`.
- Accounts: `core/systems/accounts` (Microsoft/Session/Cracked/TheAltening) + Meteor `AccountsScreen`.
- Chunk visuals: `render/ChunkRects`, `ChunkOutline`, `ChunkAreas`; persistence helpers `Memory`, `ChunkMemory`, `ScoreStore`.
- Player Bypass is Specter+ only (`specterplus/bypass/PlayerBypass`).

## Work order
1. Region map: rebuild **Where Are You** on `DonutGoliaths` (36×36 grid of 12.5k cells, rectangular goliaths, per-region numbering).
2. **Sus Finds** (new): one persistent list of suspicious areas fed by Sus Chunks + Sus Chunks V2; merges by distance, no duplicates;
   Confirmed / Likely / Uncertain; world markers; full-screen map with list (`.sus map` / keybind).
3. Player Bypass (Specter+): merge observations within a configurable 20-chunk radius into one entry, update in place,
   "go around the area to pinpoint" hint, cleaner markers.
4. Cosmetics core: particle pool (no per-frame allocation), vector shapes + glow, player-attached render hook.
   Relics (cards / sakura / chips), Halo, Angel / Fallen wings, Death animations (ice spike / head-down disintegration / water dissolve).
5. Items: Item Physics, Item ESP (+ tracers, presets, categories), Item Nametags — shared `render/ItemTargets` utilities.
6. Gameplay: Elytra Saver, Legit Auto Tool, Spear Lunge, Auto Cliper (orbit camera), Token Switcher (on the existing accounts system).
7. Optimisation pass, build, unit tests, dev-client smoke run.

## Skipped (unclear) — ask in the morning
- **Auto Mine Baser**: not clear what it should do. The closest thing, an auto-mining base finder, already exists as the
  Specter+ paid **Tunnel Base Finder**; building it into free Specter would undercut Specter+.

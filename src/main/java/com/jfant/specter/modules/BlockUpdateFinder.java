package com.jfant.specter.modules;

import com.jfant.specter.ChunkMemory;
import com.jfant.specter.Scope;
import com.jfant.specter.ScoreStore;
import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.events.game.GameLeftEvent;
import com.jfant.specter.core.events.render.Render2DEvent;
import com.jfant.specter.core.events.render.Render3DEvent;
import com.jfant.specter.core.events.world.BlockUpdateEvent;
import com.jfant.specter.core.events.world.TickEvent;
import com.jfant.specter.core.renderer.ShapeMode;
import com.jfant.specter.core.renderer.text.TextRenderer;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.ColorSetting;
import com.jfant.specter.core.settings.IntSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.core.utils.render.NametagUtils;
import com.jfant.specter.core.utils.render.color.Color;
import com.jfant.specter.core.utils.render.color.SettingColor;
import com.jfant.specter.render.ChunkOutline;
import com.jfant.specter.scan.AntiXray;
import com.jfant.specter.scan.BlockKinds;
import com.jfant.specter.scan.ChangeBursts;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;

public class BlockUpdateFinder extends Module {
   private static final long OWN_TRAIL_MS = 4000L;
   private static final int MAX_EVENTS = 20000;
   private static final int MAX_UPDATE_BOXES = 2000;
   private static final int LOG_TICKS = 1200;
   private static final int LEAK_MARGIN = 2;
   private static final int MOVE_TO_TELL = 3;
   private static final String[] KIND_NAMES = new String[]{"kelp", "vines", "bees", "crops", "snow", "grass", "leaves", "fire"};
   private static final int REAL = 0;
   private static final int YOURS = 1;
   private static final int UNSURE = 2;
   private static final String[] VERDICT_NAMES = new String[]{"real", "yours", "unsure"};
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgIndicators = this.settings.createGroup("Indicators");
   private final SettingGroup sgRender = this.settings.createGroup("Render");
   private final Setting<Integer> simDistance = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("sim-distance")
            .description(
               "The server's simulation distance in chunks (4 on DonutSMP). Sets both the area around you that is ignored and how far from a change the player can be."
            )
            .defaultValue(4)
            .range(1, 32)
            .sliderRange(1, 12)
            .onChanged(v -> this.dirty = true)
            .build()
      );
   private final Setting<Integer> memoryMinutes = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("memory-minutes")
            .description("Forget changes older than this.")
            .defaultValue(10)
            .range(1, 120)
            .sliderRange(1, 60)
            .build()
      );
   private final Setting<Boolean> chat = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("chat")
            .description("Say in chat where a player is once an area is sure not to be you, and again when it narrows to half the size or less.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> kelp = this.indicator(
      "kelp", "Kelp growing: a new top in the water above, the old top turning into stem. One of Sus Chunks' signs."
   );
   private final Setting<Boolean> vines = this.indicator("vines", "Vines creeping into the air. One of Sus Chunks' signs.");
   private final Setting<Boolean> bees = this.indicator(
      "bees",
      "A bee nest's or beehive's honey changing. Bees only work in chunks the server fully ticks (no extra ring), so these pin the player one chunk tighter. One of Sus Chunks' signs."
   );
   private final Setting<Boolean> crops = this.indicator("crops", "Crops, stems, sugar cane, cactus, bamboo, berries, cocoa, nether wart and saplings growing.");
   private final Setting<Boolean> snow = this.indicator("snow-and-ice", "Snow settling or melting, water freezing and ice melting.");
   private final Setting<Boolean> grass = this.indicator(
      "grass",
      "Grass or mycelium spreading onto dirt, or dying back to dirt. Off by default: DonutSMP's anti-xray swaps dirt and grass back and forth, so nearly every one is fake.",
      false
   );
   private final Setting<Boolean> leaves = this.indicator("leaves", "Leaves decaying, or updating after a log next to them changed.");
   private final Setting<Boolean> fire = this.indicator("fire", "Fire spreading or burning out.");
   private final List<Setting<Boolean>> kinds = List.of(this.kelp, this.vines, this.bees, this.crops, this.snow, this.grass, this.leaves, this.fire);
   private final Setting<SettingColor> areaColor = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("area-color")
            .description("Where the player must be: the chunks every change in a burst agrees on. The likely chunk is outlined.")
            .defaultValue(new SettingColor(255, 90, 0, 70))
            .build()
      );
   private final Setting<Boolean> labels = this.sgRender
      .add(
         new BoolSetting.Builder()
            .name("labels")
            .description("Label each area: how tightly the player is pinned, what was seen there, distance and how long ago.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> showUpdates = this.sgRender
      .add(new BoolSetting.Builder().name("show-updates").description("Also box each changed block where it really is.").defaultValue(true).build());
   private final Setting<SettingColor> updateColor = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("update-color")
            .description("Box colour for changed blocks.")
            .defaultValue(new SettingColor(255, 220, 0, 90))
            .visible(this.showUpdates::get)
            .build()
      );
   private final Setting<Integer> fixedY = this.sgRender
      .add(new IntSetting.Builder().name("y").description("The height the area overlay is drawn at.").defaultValue(63).sliderRange(-64, 320).build());
   private final Setting<Integer> renderDistance = this.sgRender
      .add(
         new IntSetting.Builder()
            .name("render-distance")
            .description("Only draw within this many chunks of you.")
            .defaultValue(32)
            .min(1)
            .sliderRange(8, 64)
            .build()
      );
   private final ArrayDeque<BlockUpdateFinder.Update> updates = new ArrayDeque<>();
   private final ArrayDeque<BlockUpdateFinder.Held> held = new ArrayDeque<>();
   private final ArrayDeque<long[]> trail = new ArrayDeque<>();
   private final List<BlockUpdateFinder.Announced> announced = new ArrayList<>();
   private int playerChunkX;
   private int playerChunkZ;
   private boolean inWorld;
   private ClientLevel lastWorld;
   private boolean dirty;
   private List<BlockUpdateFinder.Area> areas = List.of();
   private long changes;
   private long swaps;
   private long terrainSwaps;
   private long own;
   private long notGrowth;
   private long unsettled;
   private long bursts;
   private long counted;
   private long loggedChanges = -1L;
   private final long[] countedByKind = new long[KIND_NAMES.length];
   private int ticks;
   private final ChunkMemory found = new ChunkMemory("block-update-finder-finds", ".csv");
   private final LongOpenHashSet drawn = new LongOpenHashSet();
   private final Color outline = new Color();
   private final Vector3d labelPos = new Vector3d();
   private static final Color LABEL_TITLE = new Color(255, 255, 255);
   private static final Color LABEL_TEXT = new Color(235, 235, 235);

   private Setting<Boolean> indicator(String name, String description) {
      return this.indicator(name, description, true);
   }

   private Setting<Boolean> indicator(String name, String description, boolean on) {
      return this.sgIndicators.add(new BoolSetting.Builder().name(name).description(description).defaultValue(on).onChanged(v -> this.dirty = true).build());
   }

   public BlockUpdateFinder() {
      super(
         SpecterClientAddon.BASES,
         "block-update-finder",
         "Finds players from far-off growth - kelp, vines, bees, crops, snow - that only happens in chunks someone is ticking."
      );
   }

   @Override
   public void onActivate() {
      ChangeBursts.blockUpdateFinder = true;
      this.clear();
      this.resetFunnel();
   }

   @Override
   public void onDeactivate() {
      ChangeBursts.blockUpdateFinder = false;
      this.logFunnel();
      this.clear();
   }

   @EventHandler
   private void onGameLeft(GameLeftEvent event) {
      this.logFunnel();
      this.clear();
      this.inWorld = false;
   }

   private void clear() {
      this.found.flush();
      this.found.select(null);
      this.updates.clear();
      this.held.clear();
      this.trail.clear();
      this.announced.clear();
      this.areas = List.of();
      this.lastWorld = null;
   }

   private void resetFunnel() {
      this.changes = this.swaps = this.terrainSwaps = this.own = this.notGrowth = this.unsettled = this.bursts = this.counted = 0L;
      this.loggedChanges = -1L;
      Arrays.fill(this.countedByKind, 0L);
   }

   @EventHandler
   private void onBlockUpdate(BlockUpdateEvent event) {
      if (this.inWorld) {
         BlockState old = event.oldState;
         BlockState state = event.newState;
         if (old != null && state != null && old != state) {
            this.changes++;
            if (AntiXray.isSwap(old, state)) {
               if (AntiXray.isTerrainSwap(old, state)) {
                  this.terrainSwaps++;
               } else {
                  this.swaps++;
               }
            } else {
               BlockPos pos = event.pos;
               long now = System.currentTimeMillis();
               if (this.inOwnArea(pos.getX() >> 4, pos.getZ() >> 4, now)) {
                  this.own++;
               } else {
                  int kind = BlockKinds.growthKind(old, state);
                  if (kind < 0) {
                     this.notGrowth++;
                  } else {
                     long nanos = ChangeBursts.now();
                     if (!ChangeBursts.settled(pos, nanos)) {
                        this.unsettled++;
                     } else {
                        this.held
                           .addLast(
                              new BlockUpdateFinder.Held(
                                 pos.immutable(),
                                 nanos,
                                 now,
                                 (byte)kind,
                                 ScoreStore.key(this.playerChunkX, this.playerChunkZ),
                                 old.getBlock() != state.getBlock()
                              )
                           );
                        if (this.held.size() > 20000) {
                           this.held.removeFirst();
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private void releaseHeld() {
      long nanos = ChangeBursts.now();

      while (!this.held.isEmpty() && nanos - this.held.peekFirst().nanos() >= 1100000000L) {
         BlockUpdateFinder.Held h = this.held.removeFirst();
         if (ChangeBursts.fake(h.pos(), h.nanos(), h.typeChange())) {
            this.bursts++;
         } else {
            this.counted++;
            this.countedByKind[h.kind()]++;
            this.updates.addLast(new BlockUpdateFinder.Update(h.pos().asLong(), h.time(), h.kind(), h.you()));
            if (this.updates.size() > 20000) {
               this.updates.removeFirst();
            }

            this.dirty = true;
         }
      }
   }

   private boolean inOwnArea(int chunkX, int chunkZ, long now) {
      int reach = this.simDistance.get() + 1;
      if (Math.abs(chunkX - this.playerChunkX) <= reach && Math.abs(chunkZ - this.playerChunkZ) <= reach) {
         return true;
      } else {
         for (long[] t : this.trail) {
            if (now - t[0] <= 4000L && Math.abs(chunkX - (int)t[1]) <= reach && Math.abs(chunkZ - (int)t[2]) <= reach) {
               return true;
            }
         }

         return false;
      }
   }

   private int reach(int kind) {
      return kind == 2 ? this.simDistance.get() : this.simDistance.get() + 1;
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      if (this.mc.level != null && this.mc.player != null) {
         if (this.mc.level != this.lastWorld) {
            this.clear();
            this.lastWorld = this.mc.level;
            this.found.select(Scope.current(this.mc));
         }

         this.found.tickAutosave();
         long now = System.currentTimeMillis();
         int chunkX = this.mc.player.getBlockX() >> 4;
         int chunkZ = this.mc.player.getBlockZ() >> 4;
         if (this.inWorld && (chunkX != this.playerChunkX || chunkZ != this.playerChunkZ)) {
            this.trail.addLast(new long[]{now, this.playerChunkX, this.playerChunkZ});
         }

         while (!this.trail.isEmpty() && now - ((long[])this.trail.peekFirst())[0] > 4000L) {
            this.trail.removeFirst();
         }

         this.playerChunkX = chunkX;
         this.playerChunkZ = chunkZ;
         this.inWorld = true;
         this.releaseHeld();

         long cutoff;
         for (cutoff = now - this.memoryMinutes.get().intValue() * 60000L;
            !this.updates.isEmpty() && this.updates.peekFirst().time() < cutoff;
            this.dirty = true
         ) {
            this.updates.removeFirst();
         }

         this.announced.removeIf(a -> a.seen < cutoff);
         this.ticks++;
         if (this.dirty && this.ticks % 10 == 0) {
            this.dirty = false;
            this.rebuild(now);
         }

         if (this.ticks % 1200 == 0) {
            this.logFunnel();
         }
      } else {
         this.inWorld = false;
      }
   }

   private void rebuild(long now) {
      List<BlockUpdateFinder.Builder> open = new ArrayList<>();
      Iterator<BlockUpdateFinder.Update> it = this.updates.descendingIterator();

      while (it.hasNext()) {
         BlockUpdateFinder.Update u = it.next();
         if (this.kinds.get(u.kind()).get()) {
            int cx = BlockPos.getX(u.pos()) >> 4;
            int cz = BlockPos.getZ(u.pos()) >> 4;
            int r = this.reach(u.kind());
            int x1 = cx - r;
            int z1 = cz - r;
            int x2 = cx + r;
            int z2 = cz + r;
            BlockUpdateFinder.Builder hit = null;

            for (BlockUpdateFinder.Builder b : open) {
               if (b.overlaps(x1, z1, x2, z2)) {
                  hit = b;
                  break;
               }
            }

            if (hit == null) {
               hit = new BlockUpdateFinder.Builder(x1, z1, x2, z2, u.time());
               open.add(hit);
            } else {
               hit.clip(x1, z1, x2, z2);
            }

            hit.counts[u.kind()]++;
            hit.chunks.add(ScoreStore.key(cx, cz));
            hit.saw(cx, cz, this.simDistance.get() + 1, u.you());
         }
      }

      List<BlockUpdateFinder.Area> built = new ArrayList<>(open.size());

      for (BlockUpdateFinder.Builder bx : open) {
         long sumX = 0L;
         long sumZ = 0L;
         LongIterator var23 = bx.chunks.iterator();

         while (var23.hasNext()) {
            long k = (Long)var23.next();
            sumX += ScoreStore.chunkX(k);
            sumZ += ScoreStore.chunkZ(k);
         }

         int likelyX = clamp((int)Math.round((double)sumX / bx.chunks.size()), bx.x1, bx.x2);
         int likelyZ = clamp((int)Math.round((double)sumZ / bx.chunks.size()), bx.z1, bx.z2);
         built.add(new BlockUpdateFinder.Area(bx.x1, bx.z1, bx.x2, bx.z2, likelyX, likelyZ, bx.counts, bx.newest, bx.verdict()));
      }

      this.areas = built;

      for (BlockUpdateFinder.Area area : built) {
         this.announce(area, now);
      }
   }

   private static int clamp(int v, int min, int max) {
      return Math.max(min, Math.min(max, v));
   }

   private void announce(BlockUpdateFinder.Area area, long now) {
      BlockUpdateFinder.Announced match = null;

      for (BlockUpdateFinder.Announced a : this.announced) {
         if (area.overlaps(a.x1, a.z1, a.x2, a.z2)) {
            match = a;
            break;
         }
      }

      if (match == null) {
         match = new BlockUpdateFinder.Announced();
         this.announced.add(match);
      }

      match.x1 = area.x1();
      match.z1 = area.z1();
      match.x2 = area.x2();
      match.z2 = area.z2();
      match.seen = now;
      if (area.verdict() == 0) {
         boolean fresh = !match.said;
         if (fresh || area.cells() * 2 <= match.cells) {
            match.said = true;
            match.cells = area.cells();
            this.found.put(ChunkPos.pack(area.likelyX(), area.likelyZ()), 0);
            if (this.chat.get() && this.mc.player != null) {
               int x = area.likelyX() * 16 + 8;
               int z = area.likelyZ() * 16 + 8;
               double dx = x - this.mc.player.getX();
               double dz = z - this.mc.player.getZ();
               this.info(
                  "%s near %d, %d (%d blocks %s) - %s - %s",
                  new Object[]{
                     fresh ? "Player" : "Narrowed: player", x, z, (int)Math.sqrt(dx * dx + dz * dz), compass(dx, dz), size(area), seen(area.counts())
                  }
               );
            }
         }
      }
   }

   private static String size(BlockUpdateFinder.Area area) {
      return area.cells() == 1 ? "pinned to one chunk" : "within " + (area.x2() - area.x1() + 1) + "x" + (area.z2() - area.z1() + 1) + " chunks";
   }

   private static String seen(int[] counts) {
      StringBuilder s = new StringBuilder();

      for (int k = 0; k < counts.length; k++) {
         if (counts[k] != 0) {
            if (!s.isEmpty()) {
               s.append(", ");
            }

            s.append(KIND_NAMES[k]).append(' ').append(counts[k]);
         }
      }

      return s.toString();
   }

   private static String compass(double dx, double dz) {
      int i = (int)Math.round(Math.toDegrees(Math.atan2(dx, -dz)) / 45.0) & 7;
      return new String[]{"N", "NE", "E", "SE", "S", "SW", "W", "NW"}[i];
   }

   private static String ago(long millis) {
      long s = Math.max(0L, millis / 1000L);
      return s < 60L ? s + "s ago" : s / 60L + "m ago";
   }

   private void logFunnel() {
      if (this.changes != this.loggedChanges) {
         this.loggedChanges = this.changes;
         SpecterClientAddon.LOG.info(this.funnel());
      }
   }

   public String funnel() {
      StringBuilder byKind = new StringBuilder();

      for (int k = 0; k < KIND_NAMES.length; k++) {
         if (this.countedByKind[k] != 0L) {
            if (!byKind.isEmpty()) {
               byKind.append(", ");
            }

            byKind.append(KIND_NAMES[k]).append(' ').append(this.countedByKind[k]);
         }
      }

      int[] verdicts = new int[VERDICT_NAMES.length];

      for (BlockUpdateFinder.Area a : this.areas) {
         verdicts[a.verdict()]++;
      }

      return String.format(
         "Block Update Finder: %d block changes, %d anti-xray swaps, %d anti-xray terrain copies, %d in your own area, %d not growth, %d just loaded or teleported, %d in anti-xray bursts, %d counted (%s), %d areas (real %d, yours %d, unsure %d)",
         this.changes,
         this.swaps,
         this.terrainSwaps,
         this.own,
         this.notGrowth,
         this.unsettled,
         this.bursts,
         this.counted,
         byKind,
         this.areas.size(),
         verdicts[0],
         verdicts[1],
         verdicts[2]
      );
   }

   public List<String> areaLines() {
      List<String> lines = new ArrayList<>(this.areas.size());

      for (BlockUpdateFinder.Area a : this.areas) {
         lines.add(
            String.format(
               "chunks %d,%d..%d,%d likely %d,%d - %s - %s",
               a.x1(),
               a.z1(),
               a.x2(),
               a.z2(),
               a.likelyX(),
               a.likelyZ(),
               seen(a.counts()),
               VERDICT_NAMES[a.verdict()]
            )
         );
      }

      return lines;
   }

   @EventHandler
   private void onRender3D(Render3DEvent event) {
      if (this.mc.player != null) {
         int chunkX = this.mc.player.getBlockX() >> 4;
         int chunkZ = this.mc.player.getBlockZ() >> 4;
         int distance = this.renderDistance.get();
         SettingColor fill = this.areaColor.get();
         this.outline.set(fill.r, fill.g, fill.b, 255);
         double y = this.fixedY.get().intValue();

         for (BlockUpdateFinder.Area a : this.areas) {
            if (a.verdict() == 0 && nearby(a, chunkX, chunkZ, distance)) {
               event.renderer.quadHorizontal(a.x1() << 4, y, a.z1() << 4, a.x2() + 1 << 4, a.z2() + 1 << 4, fill);
               if (a.cells() > 1) {
                  ChunkOutline.square(event.renderer, a.likelyX(), a.likelyZ(), y, this.outline);
               }
            }
         }

         LongIterator var20 = this.found.all().keySet().iterator();

         while (var20.hasNext()) {
            long key = (Long)var20.next();
            int fx = ChunkPos.getX(key);
            int fz = ChunkPos.getZ(key);
            if (Math.abs(fx - chunkX) <= distance && Math.abs(fz - chunkZ) <= distance && !this.covered(fx, fz)) {
               ChunkOutline.square(event.renderer, fx, fz, y, this.outline);
            }
         }

         if (this.showUpdates.get()) {
            SettingColor c = this.updateColor.get();
            double maxSq = distance * 16.0 * distance * 16.0;
            this.drawn.clear();
            Iterator<BlockUpdateFinder.Update> it = this.updates.descendingIterator();

            while (it.hasNext() && this.drawn.size() < 2000) {
               BlockUpdateFinder.Update u = it.next();
               if (this.kinds.get(u.kind()).get() && this.farFromYou(u) && this.drawn.add(u.pos())) {
                  int x = BlockPos.getX(u.pos());
                  int by = BlockPos.getY(u.pos());
                  int z = BlockPos.getZ(u.pos());
                  double dx = x + 0.5 - this.mc.player.getX();
                  double dz = z + 0.5 - this.mc.player.getZ();
                  if (!(dx * dx + dz * dz > maxSq)) {
                     event.renderer.box(x, by, z, x + 1, by + 1, z + 1, c, c, ShapeMode.Both, 0);
                  }
               }
            }
         }
      }
   }

   @EventHandler
   private void onRender2D(Render2DEvent event) {
      if (this.mc.player != null && this.labels.get() && !this.areas.isEmpty()) {
         int chunkX = this.mc.player.getBlockX() >> 4;
         int chunkZ = this.mc.player.getBlockZ() >> 4;
         int distance = this.renderDistance.get();
         long now = System.currentTimeMillis();
         TextRenderer text = TextRenderer.get();

         for (BlockUpdateFinder.Area a : this.areas) {
            if (a.verdict() == 0 && nearby(a, chunkX, chunkZ, distance)) {
               double x = (a.likelyX() << 4) + 8;
               double z = (a.likelyZ() << 4) + 8;
               this.labelPos.set(x, this.fixedY.get() + 3, z);
               if (NametagUtils.to2D(this.labelPos, 2.2)) {
                  double dx = x - this.mc.player.getX();
                  double dz = z - this.mc.player.getZ();
                  String[] lines = new String[]{
                     a.cells() == 1 ? "Player here" : "Player " + size(a),
                     seen(a.counts()),
                     (int)Math.sqrt(dx * dx + dz * dz) + " blocks - " + ago(now - a.newest())
                  };
                  NametagUtils.begin(this.labelPos);
                  text.begin();
                  double h = text.getHeight(true);

                  for (int i = 0; i < lines.length; i++) {
                     text.render(lines[i], -text.getWidth(lines[i], true) / 2.0, -h * (lines.length - i), i == 0 ? LABEL_TITLE : LABEL_TEXT, true);
                  }

                  text.end();
                  NametagUtils.end();
               }
            }
         }
      }
   }

   private boolean covered(int chunkX, int chunkZ) {
      for (BlockUpdateFinder.Area a : this.areas) {
         if (a.verdict() == 0 && a.overlaps(chunkX, chunkZ, chunkX, chunkZ)) {
            return true;
         }
      }

      return false;
   }

   private boolean farFromYou(BlockUpdateFinder.Update u) {
      int cx = BlockPos.getX(u.pos()) >> 4;
      int cz = BlockPos.getZ(u.pos()) >> 4;
      int yx = ScoreStore.chunkX(u.you());
      int yz = ScoreStore.chunkZ(u.you());
      return Math.max(Math.abs(cx - yx), Math.abs(cz - yz)) > this.simDistance.get() + 1 + 2;
   }

   private static boolean nearby(BlockUpdateFinder.Area a, int chunkX, int chunkZ, int distance) {
      int dx = Math.max(0, Math.max(a.x1() - chunkX, chunkX - a.x2()));
      int dz = Math.max(0, Math.max(a.z1() - chunkZ, chunkZ - a.z2()));
      return dx <= distance && dz <= distance;
   }

   private static final class Announced {
      int x1;
      int z1;
      int x2;
      int z2;
      int cells;
      long seen;
      boolean said;
   }

   private record Area(int x1, int z1, int x2, int z2, int likelyX, int likelyZ, int[] counts, long newest, int verdict) {
      int cells() {
         return (this.x2 - this.x1 + 1) * (this.z2 - this.z1 + 1);
      }

      boolean overlaps(int ax1, int az1, int ax2, int az2) {
         return this.x1 <= ax2 && ax1 <= this.x2 && this.z1 <= az2 && az1 <= this.z2;
      }
   }

   private static final class Builder {
      int x1;
      int z1;
      int x2;
      int z2;
      final long newest;
      final int[] counts = new int[BlockUpdateFinder.KIND_NAMES.length];
      final LongOpenHashSet chunks = new LongOpenHashSet();
      boolean beyondYou;
      int youX1 = 2147483647;
      int youZ1 = 2147483647;
      int youX2 = -2147483648;
      int youZ2 = -2147483648;

      Builder(int x1, int z1, int x2, int z2, long newest) {
         this.x1 = x1;
         this.z1 = z1;
         this.x2 = x2;
         this.z2 = z2;
         this.newest = newest;
      }

      void saw(int cx, int cz, int reach, long you) {
         int yx = ScoreStore.chunkX(you);
         int yz = ScoreStore.chunkZ(you);
         if (Math.max(Math.abs(cx - yx), Math.abs(cz - yz)) > reach + 2) {
            this.beyondYou = true;
         }

         this.youX1 = Math.min(this.youX1, yx);
         this.youZ1 = Math.min(this.youZ1, yz);
         this.youX2 = Math.max(this.youX2, yx);
         this.youZ2 = Math.max(this.youZ2, yz);
      }

      int verdict() {
         if (this.beyondYou) {
            return 0;
         } else {
            return Math.max(this.youX2 - this.youX1, this.youZ2 - this.youZ1) >= 3 ? 1 : 2;
         }
      }

      boolean overlaps(int ax1, int az1, int ax2, int az2) {
         return this.x1 <= ax2 && ax1 <= this.x2 && this.z1 <= az2 && az1 <= this.z2;
      }

      void clip(int ax1, int az1, int ax2, int az2) {
         this.x1 = Math.max(this.x1, ax1);
         this.z1 = Math.max(this.z1, az1);
         this.x2 = Math.min(this.x2, ax2);
         this.z2 = Math.min(this.z2, az2);
      }
   }

   private record Held(BlockPos pos, long nanos, long time, byte kind, long you, boolean typeChange) {
   }

   private record Update(long pos, long time, byte kind, long you) {
   }
}

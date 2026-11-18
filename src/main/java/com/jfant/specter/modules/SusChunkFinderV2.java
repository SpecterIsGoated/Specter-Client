package com.jfant.specter.modules;

import com.jfant.specter.ChunkMemory;
import com.jfant.specter.Scope;
import com.jfant.specter.ScoreStore;
import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.events.game.GameJoinedEvent;
import com.jfant.specter.core.events.game.GameLeftEvent;
import com.jfant.specter.core.events.render.Render3DEvent;
import com.jfant.specter.core.events.world.BlockUpdateEvent;
import com.jfant.specter.core.events.world.ChunkDataEvent;
import com.jfant.specter.core.events.world.TickEvent;
import com.jfant.specter.core.renderer.ShapeMode;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.ColorSetting;
import com.jfant.specter.core.settings.DoubleSetting;
import com.jfant.specter.core.settings.IntSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.core.systems.modules.Modules;
import com.jfant.specter.core.utils.render.RenderUtils;
import com.jfant.specter.core.utils.render.color.Color;
import com.jfant.specter.core.utils.render.color.SettingColor;
import com.jfant.specter.render.ChunkAreas;
import com.jfant.specter.render.ChunkOutline;
import com.jfant.specter.render.ChunkRects;
import com.jfant.specter.scan.ChunkScanner;
import com.jfant.specter.scan.OverdueScanner;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2IntMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

public class SusChunkFinderV2 extends Module {
   private static final int REFRESH_TICKS = 20;
   private static final long SCAN_BUDGET_NANOS = 2000000L;
   private static final int ENCLOSED_SIDES = 3;
   private static final int[][] SIDES = new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
   private static final int MAX_RENDER = 4096;
   private static final long[] EMPTY = new long[0];
   private static final int QUARTERS = 4;
   private static final int MAX_SAVED_QUARTERS = 4095;
   private static final int MAX_SAVED_WEIGHT = 8191;
   private static final double[] TIER_MIX = new double[]{0.0, 0.5, 1.0};
   private static final int OVERLAP_TIER = TIER_MIX.length;
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgFlags = this.settings.createGroup("Flags");
   private final SettingGroup sgRender = this.settings.createGroup("Render");
   private final Setting<Integer> simDistance = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("sim-distance")
            .description(
               "The server's simulation distance, in chunks. Growth only moves this close to a player, so overdue growth clears everything this close to it."
            )
            .defaultValue(4)
            .range(2, 16)
            .sliderRange(2, 16)
            .onChanged(v -> this.dirty = true)
            .build()
      );
   private final Setting<Integer> sensitivity = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("sensitivity")
            .description("How many overdue things have to lie around a chunk before a gap there is trusted. Lower marks more.")
            .defaultValue(40)
            .range(1, 500)
            .sliderRange(1, 200)
            .build()
      );
   private final Setting<Double> clearStrength = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("clear-strength")
            .description(
               "How much overdue growth a chunk needs before it rules out the land around it. Things even a short visit would have fixed count 1 (leaves, grass and dirt, vines, bamboo, eyeblossoms); cane, cactus, kelp, cocoa, wart, berries, cave and weeping vines, propagules, amethyst and ice 0.5; crops, stems and snow 0.25. 0.25 is the old way: any one thing clears."
            )
            .defaultValue(2.0)
            .range(0.25, 20.0)
            .sliderRange(0.25, 10.0)
            .decimalPlaces(2)
            .onChanged(v -> this.dirty = true)
            .build()
      );
   private final Setting<Boolean> holeFilter = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("shaft-filter")
            .description("Leave out chunks near shafts dug straight down (1 wide, or a 3-wide trench, with a floor).")
            .defaultValue(false)
            .onChanged(v -> {
               if (v) {
                  this.dirty = true;
               }
            })
            .build()
      );
   private final Setting<Integer> holeDistance = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("shaft-distance")
            .description("How many chunks from a shaft still count as near it.")
            .defaultValue(2)
            .range(0, 8)
            .sliderRange(0, 8)
            .visible(this.holeFilter::get)
            .build()
      );
   private final Setting<Integer> holeSensitivity = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("shaft-sensitivity")
            .description(
               "How many chunks with a shaft have to be within shaft-distance before a chunk is left out. 1 lets a single shaft leave out everything around it."
            )
            .defaultValue(2)
            .range(1, 20)
            .sliderRange(1, 10)
            .visible(this.holeFilter::get)
            .build()
      );
   private final Setting<Boolean> customFlags = this.sgFlags
      .add(
         new BoolSetting.Builder()
            .name("custom-flags")
            .description("Pick which kinds of overdue growth count. Off counts them all.")
            .defaultValue(false)
            .onChanged(v -> this.refreshFlags())
            .build()
      );
   private final Setting<Boolean>[] flagEnabled = new Setting[OverdueScanner.Flag.VALUES.length];
   private final Setting<SettingColor> sideColor;
   private final Setting<SettingColor> lineColor;
   private final Setting<Boolean> shade;
   private final Setting<SettingColor> strongSideColor;
   private final Setting<SettingColor> strongLineColor;
   private final Setting<Boolean> overlap;
   private final Setting<SettingColor> overlapSideColor;
   private final Setting<SettingColor> overlapLineColor;
   private final Setting<Integer> fixedY;
   private final Setting<Boolean> tracers;
   private final Long2ObjectOpenHashMap<SusChunkFinderV2.Record> records;
   private final ChunkMemory memory;
   private String scope;
   private long recalledAt;
   private final Long2IntOpenHashMap clearCoverage;
   private final Long2IntOpenHashMap support;
   private final LongLinkedOpenHashSet pending;
   private final LongOpenHashSet kelpChunks;
   private final LongOpenHashSet seenKelp;
   private final LongArrayList distant;
   private boolean[] counted;
   private boolean dirty;
   private int tickCounter;
   private ResourceKey<Level> worldKey;
   private int clearQuarters;
   private long[] marked;
   private long[][] tiers;
   private long[] lastMarked;
   private boolean lastShade;
   private int lastPlayerX;
   private int lastPlayerZ;
   private final LongArrayList visibleCells;
   private final IntArrayList[] renderRects;
   private final IntArrayList[] outlines;
   private final LongArrayList[] tierCells;
   private final ChunkOutline outline;
   private long[] lastRed;
   private final LongOpenHashSet redKeys;
   private final Color tierSide;
   private final Color tierLine;
   private final ChunkAreas areas;
   private long[] areasSnapshot;
   private final IntArrayList tracerPick;
   private final Color tracerColor;

   public SusChunkFinderV2() {
      super(
         SpecterClientAddon.BASES,
         "sus-chunk-finder-v2",
         "Sus Chunks V2",
         "Marks chunks a player could have been in: overdue natural growth (crops, kelp, vines, leaves, snow, ice and more) rules out everywhere nobody has been near.",
         new String[0]
      );

      for (OverdueScanner.Flag flag : OverdueScanner.Flag.VALUES) {
         this.flagEnabled[flag.ordinal()] = this.sgFlags
            .add(
               new BoolSetting.Builder()
                  .name(flag.setting)
                  .description(flag.description)
                  .defaultValue(true)
                  .visible(this.customFlags::get)
                  .onChanged(v -> this.refreshFlags())
                  .build()
            );
      }

      this.sideColor = this.sgRender
         .add(new ColorSetting.Builder().name("side-color").description("The fill of marked chunks.").defaultValue(new SettingColor(88, 24, 140, 110)).build());
      this.lineColor = this.sgRender
         .add(
            new ColorSetting.Builder()
               .name("line-color")
               .description("The outline of marked chunks.")
               .defaultValue(new SettingColor(110, 34, 170, 255))
               .build()
         );
      this.shade = this.sgRender
         .add(
            new BoolSetting.Builder()
               .name("shade-by-evidence")
               .description(
                  "Draw better-backed chunks pinker: closed in by cleared land on all four sides, and with at least twice sensitivity of overdue growth around. Plain purple only just makes it, full pink has both."
               )
               .defaultValue(true)
               .build()
         );
      this.strongSideColor = this.sgRender
         .add(
            new ColorSetting.Builder()
               .name("strong-side-color")
               .description("The fill of the best-backed marked chunks; the ones between are halfway from side-color to this.")
               .defaultValue(new SettingColor(255, 70, 190, 140))
               .visible(this.shade::get)
               .build()
         );
      this.strongLineColor = this.sgRender
         .add(
            new ColorSetting.Builder()
               .name("strong-line-color")
               .description("The outline of the best-backed marked chunks.")
               .defaultValue(new SettingColor(255, 120, 220, 255))
               .visible(this.shade::get)
               .build()
         );
      this.overlap = this.sgRender
         .add(
            new BoolSetting.Builder()
               .name("gold-on-sus-chunks")
               .description(
                  "Draw marked chunks that Sus Chunks flags too in their own colour - two finders that work nothing alike agreeing. Needs Sus Chunks on."
               )
               .defaultValue(true)
               .build()
         );
      this.overlapSideColor = this.sgRender
         .add(
            new ColorSetting.Builder()
               .name("gold-side-color")
               .description("The fill of marked chunks Sus Chunks flags too.")
               .defaultValue(new SettingColor(255, 190, 40, 140))
               .visible(this.overlap::get)
               .build()
         );
      this.overlapLineColor = this.sgRender
         .add(
            new ColorSetting.Builder()
               .name("gold-line-color")
               .description("The outline of marked chunks Sus Chunks flags too.")
               .defaultValue(new SettingColor(255, 215, 90, 255))
               .visible(this.overlap::get)
               .build()
         );
      this.fixedY = this.sgRender
         .add(new IntSetting.Builder().name("y").description("The height the overlay is drawn at.").defaultValue(63).sliderRange(-64, 320).build());
      this.tracers = this.sgRender
         .add(new BoolSetting.Builder().name("tracers").description("Draw a line from you to the nearest few marked areas.").defaultValue(false).build());
      this.records = new Long2ObjectOpenHashMap();
      this.memory = new ChunkMemory("sus-chunk-finder-v2-points", ".csv");
      this.recalledAt = -9223372036854775808L;
      this.clearCoverage = new Long2IntOpenHashMap();
      this.support = new Long2IntOpenHashMap();
      this.pending = new LongLinkedOpenHashSet();
      this.kelpChunks = new LongOpenHashSet();
      this.seenKelp = new LongOpenHashSet();
      this.distant = new LongArrayList();
      this.counted = new boolean[OverdueScanner.Flag.VALUES.length];
      this.clearQuarters = 8;
      this.marked = EMPTY;
      this.tiers = new long[][]{EMPTY, EMPTY, EMPTY};
      this.lastMarked = EMPTY;
      this.lastPlayerX = -2147483648;
      this.lastPlayerZ = -2147483648;
      this.visibleCells = new LongArrayList();
      this.renderRects = new IntArrayList[]{new IntArrayList(), new IntArrayList(), new IntArrayList(), new IntArrayList()};
      this.outlines = new IntArrayList[]{new IntArrayList(), new IntArrayList(), new IntArrayList(), new IntArrayList()};
      this.tierCells = new LongArrayList[]{new LongArrayList(), new LongArrayList(), new LongArrayList(), new LongArrayList()};
      this.outline = new ChunkOutline();
      this.lastRed = EMPTY;
      this.redKeys = new LongOpenHashSet();
      this.tierSide = new Color();
      this.tierLine = new Color();
      this.areas = new ChunkAreas();
      this.areasSnapshot = EMPTY;
      this.tracerPick = new IntArrayList();
      this.tracerColor = new Color();
      this.refreshFlags();
   }

   private void refreshFlags() {
      boolean custom = this.customFlags.get();
      boolean[] next = new boolean[OverdueScanner.Flag.VALUES.length];

      for (int i = 0; i < next.length; i++) {
         next[i] = !custom || this.flagEnabled[i].get();
      }

      this.counted = next;
      this.dirty = true;
   }

   @Override
   public void onActivate() {
      this.resetState();
      this.worldKey = this.mc.level == null ? null : this.mc.level.dimension();
      this.dirty = false;
      this.rescanLoaded();
   }

   @Override
   public void onDeactivate() {
      this.resetState();
      this.worldKey = null;
      this.leaveMemory();
   }

   @EventHandler
   private void onGameJoined(GameJoinedEvent event) {
      this.resetState();
      this.worldKey = null;
   }

   @EventHandler
   private void onGameLeft(GameLeftEvent event) {
      this.resetState();
      this.worldKey = null;
      this.leaveMemory();
   }

   private void leaveMemory() {
      this.memory.flush();
      this.memory.select(null);
      this.scope = null;
   }

   private void resetState() {
      this.records.clear();
      this.recalledAt = -9223372036854775808L;
      this.clearCoverage.clear();
      this.support.clear();
      this.pending.clear();
      this.kelpChunks.clear();
      this.seenKelp.clear();
      this.marked = EMPTY;
      this.tiers = new long[][]{EMPTY, EMPTY, EMPTY};
      this.tickCounter = 0;
      this.clearQuarters = (int)Math.ceil(this.clearStrength.get() * 4.0);
   }

   private void rescanLoaded() {
      if (this.mc.level != null && this.mc.player != null) {
         this.resetState();
         int radius = this.mc.options.getEffectiveRenderDistance() + 3;
         ChunkPos centre = this.mc.player.chunkPosition();

         for (int x = centre.x - radius; x <= centre.x + radius; x++) {
            for (int z = centre.z - radius; z <= centre.z + radius; z++) {
               if (this.mc.level.getChunkSource().hasChunk(x, z)) {
                  this.pending.add(ChunkPos.asLong(x, z));
               }
            }
         }
      }
   }

   @EventHandler
   private void onChunkData(ChunkDataEvent event) {
      this.pending.add(event.chunk().getPos().toLong());
   }

   @EventHandler
   private void onBlockUpdate(BlockUpdateEvent event) {
      if (this.mc.level != null && this.counted[OverdueScanner.Flag.KELP.ordinal()] && event.newState.is(Blocks.KELP) && this.donut()) {
         int age = event.newState.getValue(BlockStateProperties.AGE_25);
         if (this.seenKelp.add(event.pos.asLong()) && age > 0 && age < 25) {
            if (this.mc.level.getBlockState(event.pos.above()).is(Blocks.WATER)) {
               long key = ChunkPos.asLong(event.pos.getX() >> 4, event.pos.getZ() >> 4);
               if (this.kelpChunks.add(key)) {
                  this.pending.add(key);
               }
            }
         }
      }
   }

   private boolean donut() {
      return this.mc.getConnection() != null && OverdueScanner.donut(this.mc.getConnection().serverBrand());
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      if (this.mc.level != null && this.mc.player != null) {
         ResourceKey<Level> dimension = this.mc.level.dimension();
         if (dimension != this.worldKey) {
            this.resetState();
            this.worldKey = dimension;
         }

         String place = Scope.current(this.mc);
         if (!place.equals(this.scope)) {
            this.memory.flush();
            this.scope = place;
            this.memory.select(place);
            this.recalledAt = -9223372036854775808L;
         }

         this.memory.tickAutosave();
         if (this.dirty) {
            this.dirty = false;
            this.rescanLoaded();
         }

         this.pruneDistant();
         this.recall();
         this.runScans();
         if (++this.tickCounter >= 20) {
            this.tickCounter = 0;
            this.rebuildMarked();
         }
      }
   }

   private void runScans() {
      long end = System.nanoTime() + 2000000L;
      boolean donut = this.donut();
      LongIterator it = this.pending.iterator();

      while (it.hasNext() && System.nanoTime() < end) {
         long key = it.nextLong();
         int cx = ChunkPos.getX(key);
         int cz = ChunkPos.getZ(key);
         LevelChunk chunk = this.mc.level.getChunkSource().getChunkNow(cx, cz);
         if (chunk == null) {
            it.remove();
         } else if (ChunkScanner.isLightReady(this.mc.level, cx, cz)) {
            it.remove();
            this.rescan(key, chunk, donut);
         }
      }
   }

   private void rescan(long key, LevelChunk chunk, boolean donut) {
      SusChunkFinderV2.Record old = this.removeRecord(key);
      if (old == null && this.memory.contains(key)) {
         old = unpack(this.memory.get(key, 0));
      }

      int[] byFlag = new int[OverdueScanner.Flag.VALUES.length];
      int weight = OverdueScanner.count(this.mc.level, chunk, this.counted, donut, byFlag);
      if (donut && this.counted[OverdueScanner.Flag.KELP.ordinal()] && this.kelpChunks.contains(key)) {
         weight++;
         byFlag[OverdueScanner.Flag.KELP.ordinal()]++;
      }

      boolean hole = this.holeFilter.get() && OverdueScanner.hasShaft(this.mc.level, chunk.getPos());
      SusChunkFinderV2.Record merged = new SusChunkFinderV2.Record(
         Math.max(quarters(byFlag), old == null ? 0 : old.quarters()),
         this.simDistance.get(),
         hole || old != null && old.hole(),
         Math.max(weight, old == null ? 0 : old.weight())
      );
      this.records.put(key, merged);
      this.spread(key, merged, 1);
      if (merged.quarters() > 0 || merged.hole()) {
         this.memory.put(key, pack(merged));
      }
   }

   private static int quarters(int[] byFlag) {
      double points = 0.0;

      for (OverdueScanner.Flag flag : OverdueScanner.Flag.VALUES) {
         points += byFlag[flag.ordinal()] * flag.points;
      }

      return (int)Math.round(points * 4.0);
   }

   private boolean clears(SusChunkFinderV2.Record record) {
      return record.quarters() > 0 && record.quarters() >= this.clearQuarters;
   }

   private void recall() {
      long here = ChunkPos.asLong(this.mc.player.getBlockX() >> 4, this.mc.player.getBlockZ() >> 4);
      if (here != this.recalledAt && this.memory.size() != 0) {
         this.recalledAt = here;
         int reach = this.mc.options.getEffectiveRenderDistance() + 2 * this.simDistance.get() + 2;
         int px = ChunkPos.getX(here);
         int pz = ChunkPos.getZ(here);
         ObjectIterator var6 = this.memory.all().long2IntEntrySet().iterator();

         while (var6.hasNext()) {
            Entry entry = (Entry)var6.next();
            long key = entry.getLongKey();
            if (!this.records.containsKey(key) && Math.abs(ChunkPos.getX(key) - px) <= reach && Math.abs(ChunkPos.getZ(key) - pz) <= reach) {
               SusChunkFinderV2.Record record = unpack(entry.getIntValue());
               this.records.put(key, record);
               this.spread(key, record, 1);
            }
         }
      }
   }

   private static int pack(SusChunkFinderV2.Record record) {
      return (record.hole() ? 1 : 0) | Math.min(record.radius(), 63) << 1 | Math.min(record.quarters(), 4095) << 7 | Math.min(record.weight(), 8191) << 19;
   }

   private static SusChunkFinderV2.Record unpack(int value) {
      return new SusChunkFinderV2.Record(value >>> 7 & 4095, value >>> 1 & 63, (value & 1) != 0, value >>> 19);
   }

   private SusChunkFinderV2.Record removeRecord(long key) {
      SusChunkFinderV2.Record old = (SusChunkFinderV2.Record)this.records.remove(key);
      if (old != null) {
         this.spread(key, old, -1);
      }

      return old;
   }

   private void spread(long key, SusChunkFinderV2.Record record, int sign) {
      int cx = ChunkPos.getX(key);
      int cz = ChunkPos.getZ(key);
      int r = record.radius();
      int reach = 2 * r + 2;
      if (this.clears(record)) {
         for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
               add(this.clearCoverage, ChunkPos.asLong(cx + dx, cz + dz), sign);
            }
         }
      }

      if (record.weight() > 0) {
         for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
               add(this.support, ChunkPos.asLong(cx + dx, cz + dz), sign * record.weight());
            }
         }
      }
   }

   private static void add(Long2IntOpenHashMap map, long key, int amount) {
      int value = map.get(key) + amount;
      if (value <= 0) {
         map.remove(key);
      } else {
         map.put(key, value);
      }
   }

   private void pruneDistant() {
      int reach = this.mc.options.getEffectiveRenderDistance() + 2 * this.simDistance.get() + 2;
      int px = this.mc.player.getBlockX() >> 4;
      int pz = this.mc.player.getBlockZ() >> 4;
      this.distant.clear();
      LongIterator it = this.records.keySet().iterator();

      while (it.hasNext()) {
         long key = it.nextLong();
         if (Math.abs(ChunkPos.getX(key) - px) > reach || Math.abs(ChunkPos.getZ(key) - pz) > reach) {
            this.distant.add(key);
         }
      }

      for (int i = 0; i < this.distant.size(); i++) {
         this.removeRecord(this.distant.getLong(i));
      }

      this.kelpChunks.removeIf(keyx -> Math.abs(ChunkPos.getX(keyx) - px) > reach || Math.abs(ChunkPos.getZ(keyx) - pz) > reach);
      this.seenKelp.removeIf(pos -> Math.abs((BlockPos.getX(pos) >> 4) - px) > reach || Math.abs((BlockPos.getZ(pos) >> 4) - pz) > reach);
   }

   private void rebuildMarked() {
      boolean holes = this.holeFilter.get();
      int threshold = this.sensitivity.get();
      int holeReach = this.holeDistance.get();
      int holeMin = this.holeSensitivity.get();
      int reach = 2 * this.simDistance.get() + 2;
      LongArrayList next = new LongArrayList();
      LongArrayList[] byTier = new LongArrayList[]{new LongArrayList(), new LongArrayList(), new LongArrayList()};
      LongIterator it = this.records.keySet().iterator();

      while (it.hasNext()) {
         long key = it.nextLong();
         int cx = ChunkPos.getX(key);
         int cz = ChunkPos.getZ(key);
         if (this.mc.level.getChunkSource().hasChunk(cx, cz)
            && !this.clearCoverage.containsKey(key)
            && (!holes || this.shaftsNearby(cx, cz, holeReach) < holeMin)) {
            int around = this.support.get(key);
            if (around >= threshold) {
               int sides = this.coveredSides(cx, cz, reach);
               if (sides >= 3) {
                  next.add(key);
                  int tier = (sides == SIDES.length ? 1 : 0) + (around >= 2 * threshold ? 1 : 0);
                  byTier[tier].add(key);
               }
            }
         }
      }

      this.marked = next.toLongArray();
      this.tiers = new long[][]{byTier[0].toLongArray(), byTier[1].toLongArray(), byTier[2].toLongArray()};
   }

   private int shaftsNearby(int cx, int cz, int distance) {
      int shafts = 0;

      for (int dx = -distance; dx <= distance; dx++) {
         for (int dz = -distance; dz <= distance; dz++) {
            SusChunkFinderV2.Record record = (SusChunkFinderV2.Record)this.records.get(ChunkPos.asLong(cx + dx, cz + dz));
            if (record != null && record.hole()) {
               shafts++;
            }
         }
      }

      return shafts;
   }

   private int coveredSides(int cx, int cz, int reach) {
      int sides = 0;

      for (int[] side : SIDES) {
         if (this.stripCleared(cx, cz, side[0], side[1], reach)) {
            sides++;
         }
      }

      return sides;
   }

   private boolean stripCleared(int cx, int cz, int dx, int dz, int reach) {
      for (int depth = 1; depth <= reach; depth++) {
         for (int lateral = -reach; lateral <= reach; lateral++) {
            if (this.clearCoverage.containsKey(ChunkPos.asLong(cx + dx * depth + dz * lateral, cz + dz * depth + dx * lateral))) {
               return true;
            }
         }
      }

      return false;
   }

   @EventHandler
   private void onRender3D(Render3DEvent event) {
      if (this.mc.player != null) {
         long[] cells = this.marked;
         if (cells.length != 0) {
            int px = this.mc.player.getBlockX() >> 4;
            int pz = this.mc.player.getBlockZ() >> 4;
            boolean shaded = this.shade.get();
            long[] red = this.susChunksFlagged();
            if (cells != this.lastMarked || shaded != this.lastShade || red != this.lastRed || px != this.lastPlayerX || pz != this.lastPlayerZ) {
               this.lastMarked = cells;
               this.lastShade = shaded;
               this.lastPlayerX = px;
               this.lastPlayerZ = pz;
               if (red != this.lastRed) {
                  this.lastRed = red;
                  this.redKeys.clear();

                  for (long key : red) {
                     this.redKeys.add(key);
                  }
               }

               long[][] split = this.tiers;

               for (LongArrayList list : this.tierCells) {
                  list.clear();
               }

               for (int tier = 0; tier < TIER_MIX.length; tier++) {
                  long[] source = shaded ? split[tier] : (tier == TIER_MIX.length - 1 ? cells : EMPTY);

                  for (long chunk : source) {
                     long key = ScoreStore.key(ChunkPos.getX(chunk), ChunkPos.getZ(chunk));
                     this.tierCells[this.redKeys.contains(key) ? OVERLAP_TIER : tier].add(key);
                  }
               }

               this.outline.clear();

               for (int tier = 0; tier < this.renderRects.length; tier++) {
                  ChunkRects.rebuild(this.tierCells[tier].toLongArray(), px, pz, 1073741823, 4096, true, this.visibleCells, this.renderRects[tier]);
                  this.outline.add(this.visibleCells, tier);
               }

               for (int tier = 0; tier < this.outlines.length; tier++) {
                  this.outline.edges(tier, this.outlines[tier]);
               }
            }

            double y = this.fixedY.get().intValue();
            SettingColor side = this.sideColor.get();
            SettingColor line = this.lineColor.get();

            for (int tier = 0; tier < this.renderRects.length; tier++) {
               IntArrayList rects = this.renderRects[tier];
               if (!rects.isEmpty()) {
                  if (tier == OVERLAP_TIER) {
                     this.tierSide.set(this.overlapSideColor.get());
                     this.tierLine.set(this.overlapLineColor.get());
                  } else {
                     double mix = shaded ? TIER_MIX[tier] : 0.0;
                     mix(side, this.strongSideColor.get(), mix, this.tierSide);
                     mix(line, this.strongLineColor.get(), mix, this.tierLine);
                  }

                  ChunkOutline.draw(event.renderer, rects, this.outlines[tier], y, this.tierSide, this.tierLine, ShapeMode.Both);
               }
            }

            if (this.tracers.get()) {
               Vec3 from = RenderUtils.center;
               if (from == null) {
                  return;
               }

               if (cells != this.areasSnapshot) {
                  this.areasSnapshot = cells;
                  this.areas.find(toScoreKeys(cells));
               }

               this.tracerColor.set(line.r, line.g, line.b, 255);
               this.areas.nearest(this.mc.player.getX(), this.mc.player.getZ(), 5, this.tracerPick);

               for (int i = 0; i < this.tracerPick.size(); i++) {
                  int area = this.tracerPick.getInt(i);
                  event.renderer.line(from.x, from.y, from.z, this.areas.x(area), y, this.areas.z(area), this.tracerColor);
               }
            }
         }
      }
   }

   private long[] susChunksFlagged() {
      if (!this.overlap.get()) {
         return EMPTY;
      } else {
         SusChunks susChunks = Modules.get().get(SusChunks.class);
         return susChunks != null && susChunks.isActive() ? susChunks.flaggedSnapshot() : EMPTY;
      }
   }

   private static void mix(Color from, Color to, double t, Color into) {
      into.set(
         (int)Math.round(from.r + (to.r - from.r) * t),
         (int)Math.round(from.g + (to.g - from.g) * t),
         (int)Math.round(from.b + (to.b - from.b) * t),
         (int)Math.round(from.a + (to.a - from.a) * t)
      );
   }

   private static long[] toScoreKeys(long[] chunkPosKeys) {
      long[] out = new long[chunkPosKeys.length];

      for (int i = 0; i < out.length; i++) {
         out[i] = ScoreStore.key(ChunkPos.getX(chunkPosKeys[i]), ChunkPos.getZ(chunkPosKeys[i]));
      }

      return out;
   }

   private record Record(int quarters, int radius, boolean hole, int weight) {
   }
}

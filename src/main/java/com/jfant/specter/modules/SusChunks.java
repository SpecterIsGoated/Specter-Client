package com.jfant.specter.modules;

import com.jfant.specter.Memory;
import com.jfant.specter.Scope;
import com.jfant.specter.ScoreStore;
import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.bypass.SimulationSignals;
import com.jfant.specter.core.SpecterCore;
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
import com.jfant.specter.core.settings.EnumSetting;
import com.jfant.specter.core.settings.IntSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.core.systems.modules.Modules;
import com.jfant.specter.core.utils.Utils;
import com.jfant.specter.core.utils.render.RenderUtils;
import com.jfant.specter.core.utils.render.color.Color;
import com.jfant.specter.core.utils.render.color.SettingColor;
import com.jfant.specter.render.ChunkAreas;
import com.jfant.specter.render.ChunkOutline;
import com.jfant.specter.render.ChunkRects;
import com.jfant.specter.scan.AntiXray;
import com.jfant.specter.scan.ChunkScanner;
import com.jfant.specter.scan.DripstoneScanner;
import com.jfant.specter.scan.Indicator;
import com.jfant.specter.scan.LightArrivals;
import com.jfant.specter.scan.OverdueScanner;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2LongMaps;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

public class SusChunks extends Module {
   private static final long RESCAN_DEBOUNCE_MS = 2000L;
   private static final int REBUILD_INTERVAL_TICKS = 10;
   private static final int GLOW_COPIES_PER_TICK = 16;
   private static final int MAX_GLOW_JOBS = 4096;
   private static final int MIN_OWN_SIM_DISTANCE = 4;
   private static final long[] EMPTY_SNAPSHOT = new long[0];
   private static final int MIN_VINE_LENGTH = 20;
   private static final int MIN_CAVE_VINE_LENGTH = 23;
   private static final int MIN_DRIPSTONE_LENGTH = 4;
   private static final int CLUSTER_RANGE = 10;
   private static final int MIN_CLUSTER_SOURCES = 2;
   private static final int AREA_BORDER = 1;
   private static final int LONE_RADIUS = 1;
   private static final int MAX_SOURCES = 5000;
   private static final int MAX_RENDER = 4096;
   private final ScoreStore store = new ScoreStore();
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private static final double SCORE_PER_CERTAINTY = 10.0;
   private final Setting<Double> certainty = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("certainty")
            .description(
               "How sure it has to be, 1-5. Higher flags fewer, surer chunks. For geodes: 4 is a geode with 30 clusters (one seen fully grown read 36), 1 about 8 - more than most natural geodes have."
            )
            .defaultValue(4.0)
            .min(1.0)
            .max(5.0)
            .sliderRange(1.0, 5.0)
            .decimalPlaces(1)
            .onChanged(v -> this.store.markDirty())
            .build()
      );
   private final Setting<Boolean> triangulation = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("triangulation")
            .description("Use finds to narrow down where the base is, from the server's simulation distance. Off just marks the flagged chunks themselves.")
            .defaultValue(true)
            .onChanged(v -> this.store.markDirty())
            .build()
      );
   private final Setting<SusChunks.Locate> locate = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("locate"))
                        .description(
                           "How the base is worked out. Peaks: every find heats up the chunks a player could have stood in to grow it, partial buds cancel the spots near them, and only the hottest spots are drawn. Areas: Specter's older way, the cells most finds in a group agree on."
                        ))
                     .defaultValue(SusChunks.Locate.Peaks))
                  .visible(this.triangulation::get))
               .onChanged(v -> this.store.markDirty()))
            .build()
      );
   private final Setting<Integer> locateSensitivity = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("locate-sensitivity")
            .description(
               "How much growth a spot needs before it is drawn, counted in fully grown amethyst clusters (12 glowing cells make one, other finds count by their weight). Lower shows more spots."
            )
            .defaultValue(6)
            .range(1, 40)
            .sliderRange(1, 20)
            .visible(() -> this.triangulation.get() && this.locate.get() == SusChunks.Locate.Peaks)
            .onChanged(v -> this.store.markDirty())
            .build()
      );
   private final Setting<Integer> simDistance = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("sim-distance")
            .description(
               "The server's simulation distance, in chunks. Things only grow near a player, so every find narrows down where the base can be. 0 turns that off and just marks the chunks themselves."
            )
            .defaultValue(4)
            .min(0)
            .sliderRange(0, 32)
            .visible(this.triangulation::get)
            .onChanged(v -> this.store.markDirty())
            .build()
      );
   private final Setting<Boolean> discountTrialChambers = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("discount-trial-chambers")
            .description(
               "Ignore every find within sim distance of a trial chamber - players visiting it grow things too. Those finds are neither drawn nor used to triangulate."
            )
            .defaultValue(false)
            .onChanged(v -> this.store.markDirty())
            .build()
      );
   private final Setting<Boolean> shaftFilter = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("shaft-filter")
            .description(
               "Ignore finds near shafts dug straight down (1 wide, or a 3-wide trench, with a floor) - the player digging it grew things on the way. Those finds are neither drawn nor used to triangulate."
            )
            .defaultValue(false)
            .onChanged(v -> {
               this.store.markDirty();
               if (v && this.isActive()) {
                  this.rescanLoadedChunks();
               }
            })
            .build()
      );
   private final Setting<Integer> shaftDistance = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("shaft-distance")
            .description("How many chunks from a shaft still count as near it.")
            .defaultValue(2)
            .range(0, 8)
            .sliderRange(0, 8)
            .visible(this.shaftFilter::get)
            .onChanged(v -> this.store.markDirty())
            .build()
      );
   private final Setting<Integer> shaftSensitivity = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("shaft-sensitivity")
            .description(
               "How many chunks with a shaft have to be within shaft-distance before a find there is ignored. 1 lets a single shaft wipe out everything around it."
            )
            .defaultValue(2)
            .range(1, 20)
            .sliderRange(1, 10)
            .visible(this.shaftFilter::get)
            .onChanged(v -> this.store.markDirty())
            .build()
      );
   private final Setting<SettingColor> color = this.sgGeneral
      .add(new ColorSetting.Builder().name("color").description("The chunk overlay color.").defaultValue(new SettingColor(230, 20, 20, 60)).build());
   private final Setting<Integer> fixedY = this.sgGeneral
      .add(new IntSetting.Builder().name("y").description("The height the overlay is drawn at.").defaultValue(63).sliderRange(-64, 320).build());
   private final Setting<Integer> renderDistance = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("render-distance")
            .description("Only draw chunks within this many chunks of you.")
            .defaultValue(30)
            .min(1)
            .sliderRange(8, 128)
            .build()
      );
   private final Setting<Boolean> remember = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("remember")
            .description("Save findings per server and dimension, and reload them when you come back.")
            .defaultValue(true)
            .build()
      );
   private final SettingGroup sgIndicators = this.settings.createGroup("Indicators");
   private final Setting<Boolean>[] indicatorEnabled = new Setting[Indicator.COUNT];
   private final SettingGroup sgExtras;
   private final Setting<Boolean> followServerSim;
   private final Setting<Boolean> tracers;
   private final Setting<Integer> tracerCount;
   private ExecutorService scanner;
   private final ChunkScanner chunkScanner;
   private volatile long[] flaggedSnapshot;
   private volatile boolean rebuilding;
   private int rebuildCooldown;
   private int lastBaseRadius;
   private final Long2LongOpenHashMap rescanDue;
   private final LongArrayList dueKeys;
   private long[] lastSnapshot;
   private int lastPlayerChunkX;
   private int lastPlayerChunkZ;
   private int lastRenderDistance;
   private final LongArrayList visibleCells;
   private final IntArrayList renderRects;
   private final ChunkOutline outline;
   private final IntArrayList outlineEdges;
   private final Color outlineColor;
   private final ChunkAreas areas;
   private long[] areasSnapshot;
   private final IntArrayList tracerPick;
   private final Color tracerColor;
   private final ConcurrentLinkedQueue<SusChunks.GlowJob> glowIncoming;
   private final Long2ObjectLinkedOpenHashMap<SusChunks.GlowJob> pendingGlow;
   private String scope;
   private final LightArrivals.Listener lightArrived;

   private boolean enabled(Indicator indicator) {
      Setting<Boolean> setting = this.indicatorEnabled[indicator.ordinal()];
      return setting == null || setting.get();
   }

   public SusChunks() {
      super(
         SpecterClientAddon.BASES, "sus-chunks", "Flags chunks with player-activity growth signals: grown amethyst, crafted beehives, surface kelp and more."
      );

      for (Indicator indicator : Indicator.VALUES) {
         if (!indicator.hidden) {
            this.indicatorEnabled[indicator.ordinal()] = this.sgIndicators
               .add(new BoolSetting.Builder().name(indicator.csvName).description(indicator.description).defaultValue(indicator.defaultOn).onChanged(v -> {
                  this.store.markDirty();
                  if (v && indicator == Indicator.DRIPSTONE && this.isActive()) {
                     this.rescanLoadedChunks();
                  }
               }).build());
         }
      }

      this.sgExtras = this.settings.createGroup("Extras");
      this.followServerSim = this.sgExtras
         .add(
            new BoolSetting.Builder()
               .name("follow-server-sim-distance")
               .description(
                  "Use the simulation distance the server announced when you joined, instead of the number in sim-distance. If the server never said, or announced more than 12 (DonutSMP announces 24 but ticks 4), sim-distance is used. Follows the server if it changes it."
               )
               .defaultValue(false)
               .visible(this.triangulation::get)
               .onChanged(v -> this.store.markDirty())
               .build()
         );
      this.tracers = this.sgExtras
         .add(
            new BoolSetting.Builder()
               .name("tracers")
               .description("Draw a line from you to each flagged area, however far away - the nearest few. A pile of touching chunks is one area.")
               .defaultValue(false)
               .build()
         );
      this.tracerCount = this.sgExtras
         .add(
            new IntSetting.Builder()
               .name("tracer-count")
               .description("How many areas to draw a line to, nearest first.")
               .defaultValue(5)
               .range(1, 32)
               .sliderRange(1, 16)
               .visible(this.tracers::get)
               .build()
         );
      this.chunkScanner = new ChunkScanner(20, 23, true);
      this.flaggedSnapshot = EMPTY_SNAPSHOT;
      this.lastBaseRadius = -1;
      this.rescanDue = new Long2LongOpenHashMap();
      this.dueKeys = new LongArrayList();
      this.lastSnapshot = EMPTY_SNAPSHOT;
      this.lastPlayerChunkX = -2147483648;
      this.lastPlayerChunkZ = -2147483648;
      this.lastRenderDistance = -1;
      this.visibleCells = new LongArrayList();
      this.renderRects = new IntArrayList();
      this.outline = new ChunkOutline();
      this.outlineEdges = new IntArrayList();
      this.outlineColor = new Color();
      this.areas = new ChunkAreas();
      this.areasSnapshot = EMPTY_SNAPSHOT;
      this.tracerPick = new IntArrayList();
      this.tracerColor = new Color();
      this.glowIncoming = new ConcurrentLinkedQueue<>();
      this.pendingGlow = new Long2ObjectLinkedOpenHashMap();
      this.lightArrived = this::onLightApplied;
      Memory.folder(new File(SpecterCore.FOLDER, "suschunks"));
   }

   public ScoreStore.ScoringConfig scoringConfig() {
      return this.buildConfig();
   }

   public boolean indicatorEnabled(Indicator indicator) {
      return this.enabled(indicator);
   }

   public boolean discountingTrialChambers() {
      return this.discountTrialChambers.get();
   }

   public boolean discountingShafts() {
      return this.shaftFilter.get();
   }

   public double target() {
      return this.certainty.get() * 10.0;
   }

   public double certainty() {
      return this.certainty.get();
   }

   public int baseRadius() {
      if (this.followServerSim.get() && this.triangulation.get()) {
         int announced = SimulationSignals.serverSimulation();
         if (announced > 0 && !SimulationSignals.announcedSimulationFake()) {
            return announced;
         }
      }

      return this.simDistance.get();
   }

   private ScoreStore.ScoringConfig buildConfig() {
      int base = this.baseRadius();
      double[] weightArr = new double[Indicator.COUNT];
      boolean[] triangulateArr = new boolean[Indicator.COUNT];
      int[] radiusArr = new int[Indicator.COUNT];

      for (Indicator indicator : Indicator.VALUES) {
         int i = indicator.ordinal();
         boolean on = this.enabled(indicator);
         weightArr[i] = on ? indicator.weight : 0.0;
         triangulateArr[i] = on && indicator.triangulates;
         radiusArr[i] = base + (indicator.entityTick ? 0 : 1);
      }

      return new ScoreStore.ScoringConfig(
         weightArr,
         triangulateArr,
         radiusArr,
         this.target(),
         5000,
         this.triangulation.get() && base > 0,
         10,
         2,
         1,
         1,
         this.discountTrialChambers.get() ? base : -1,
         this.locate.get() == SusChunks.Locate.Peaks,
         base,
         this.locateSensitivity.get().intValue() * Indicator.AMETHYST_CLUSTER.weight,
         this.shaftFilter.get() ? this.shaftDistance.get() : -1,
         this.shaftSensitivity.get()
      );
   }

   @Override
   public void onActivate() {
      this.scope = null;
      this.store.clear();
      this.flaggedSnapshot = EMPTY_SNAPSHOT;
      this.rescanDue.clear();
      this.clearGlowJobs();
      this.rebuilding = false;
      this.rebuildCooldown = 0;
      this.lastBaseRadius = -1;
      this.scanner = Executors.newSingleThreadExecutor(runnable -> {
         Thread thread = new Thread(runnable, "suschunks-scanner");
         thread.setDaemon(true);
         return thread;
      });
      LightArrivals.listen(this.lightArrived);
      if (this.mc.level != null) {
         this.rescanLoadedChunks();
      }
   }

   @Override
   public void onDeactivate() {
      LightArrivals.stop(this.lightArrived);
      this.saveIfRemembering();
      if (this.scanner != null) {
         this.scanner.shutdown();
         this.scanner = null;
      }

      this.rescanDue.clear();
      this.clearGlowJobs();
      this.flaggedSnapshot = EMPTY_SNAPSHOT;
      this.scope = null;
   }

   @EventHandler
   private void onGameJoined(GameJoinedEvent event) {
      this.scope = null;
      this.submitTask(this.store::clear);
      this.rescanDue.clear();
      this.clearGlowJobs();
      this.flaggedSnapshot = EMPTY_SNAPSHOT;
   }

   @EventHandler
   private void onGameLeft(GameLeftEvent event) {
      this.saveIfRemembering();
      this.submitTask(this.store::clear);
      this.scope = null;
      this.rescanDue.clear();
      this.clearGlowJobs();
      this.flaggedSnapshot = EMPTY_SNAPSHOT;
   }

   @EventHandler
   private void onChunkData(ChunkDataEvent event) {
      this.submitScan(event.chunk());
   }

   @EventHandler
   private void onBlockUpdate(BlockUpdateEvent event) {
      BlockState old = event.oldState;
      BlockState state = event.newState;
      if (old != null && state != null && old != state && this.mc.player != null) {
         int chunkX = event.pos.getX() >> 4;
         int chunkZ = event.pos.getZ() >> 4;
         if (!AntiXray.isSwap(old, state)) {
            int reach = Math.max(this.baseRadius(), 4) + 1;
            if (Math.abs(chunkX - (this.mc.player.getBlockX() >> 4)) <= reach && Math.abs(chunkZ - (this.mc.player.getBlockZ() >> 4)) <= reach) {
               return;
            }
         }

         this.rescanDue.putIfAbsent(ScoreStore.key(chunkX, chunkZ), System.currentTimeMillis() + 2000L);
      }
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      if (this.mc.level != null && this.mc.player != null) {
         String current = Scope.current(this.mc);
         if (!current.equals(this.scope)) {
            this.saveIfRemembering();
            this.submitTask(this.store::clear);
            this.rescanDue.clear();
            this.clearGlowJobs();
            this.flaggedSnapshot = EMPTY_SNAPSHOT;
            this.scope = current;
            this.loadIfRemembering();
            this.rescanLoadedChunks();
         }

         int base = this.baseRadius();
         if (base != this.lastBaseRadius) {
            this.lastBaseRadius = base;
            this.store.markDirty();
         }

         this.drainGlowQueue();
         this.processGlowJobs();
         if (!this.rescanDue.isEmpty()) {
            long now = System.currentTimeMillis();
            this.dueKeys.clear();
            ObjectIterator i = Long2LongMaps.fastIterable(this.rescanDue).iterator();

            while (i.hasNext()) {
               it.unimi.dsi.fastutil.longs.Long2LongMap.Entry entry = (it.unimi.dsi.fastutil.longs.Long2LongMap.Entry)i.next();
               if (entry.getLongValue() <= now) {
                  this.dueKeys.add(entry.getLongKey());
               }
            }

            for (int ix = 0; ix < this.dueKeys.size(); ix++) {
               long key = this.dueKeys.getLong(ix);
               this.rescanDue.remove(key);
               LevelChunk chunk = this.mc.level.getChunkSource().getChunkNow(ScoreStore.chunkX(key), ScoreStore.chunkZ(key));
               if (chunk != null) {
                  this.submitScan(chunk);
               }
            }
         }

         if (this.rebuildCooldown > 0) {
            this.rebuildCooldown--;
         }

         if (this.rebuildCooldown == 0 && !this.rebuilding && this.store.consumeDirty()) {
            ScoreStore.ScoringConfig config = this.buildConfig();
            this.rebuilding = true;
            this.rebuildCooldown = 10;
            boolean submitted = this.submitTask(() -> {
               try {
                  this.flaggedSnapshot = this.store.computeSnapshot(config);
               } finally {
                  this.rebuilding = false;
               }
            });
            if (!submitted) {
               this.rebuilding = false;
               this.store.markDirty();
            }
         }
      }
   }

   public void rescanLoadedChunks() {
      if (this.mc.level != null) {
         for (ChunkAccess chunk : Utils.chunks()) {
            if (chunk instanceof LevelChunk worldChunk) {
               this.submitScan(worldChunk);
            }
         }
      }
   }

   private void submitScan(LevelChunk chunk) {
      if (this.mc.level != null) {
         ChunkScanner activeScanner = this.chunkScanner;
         int chunkX = chunk.getPos().x;
         int chunkZ = chunk.getPos().z;
         long key = ScoreStore.key(chunkX, chunkZ);
         boolean dripstone = this.enabled(Indicator.DRIPSTONE);
         ChunkSource chunks = this.mc.level.getChunkSource();
         LevelChunk west = dripstone ? chunks.getChunkNow(chunkX - 1, chunkZ) : null;
         LevelChunk east = dripstone ? chunks.getChunkNow(chunkX + 1, chunkZ) : null;
         LevelChunk north = dripstone ? chunks.getChunkNow(chunkX, chunkZ - 1) : null;
         LevelChunk south = dripstone ? chunks.getChunkNow(chunkX, chunkZ + 1) : null;
         DripstoneScanner.Rule dripstoneRule = dripstone ? Modules.get().get(DrownedDripstone.class).rule().atLeast(4) : null;
         ClientLevel shaftWorld = this.shaftFilter.get() ? this.mc.level : null;
         this.submitTask(() -> {
            ChunkScanner.ScanResult result = activeScanner.scan(chunk, west, east, north, south, dripstoneRule);
            if (shaftWorld != null && OverdueScanner.hasShaft(shaftWorld, chunk.getPos())) {
               result.counts()[Indicator.SHAFT.ordinal()] = 1;
            }

            this.store.merge(key, result.counts());
            if (result.glow()) {
               this.glowIncoming.add(new SusChunks.GlowJob(key));
            }
         });
      }
   }

   private boolean submitTask(Runnable task) {
      ExecutorService executor = this.scanner;
      if (executor == null) {
         return false;
      } else {
         try {
            executor.submit(task);
            return true;
         } catch (RejectedExecutionException var4) {
            return false;
         }
      }
   }

   private void onLightApplied(int chunkX, int chunkZ, ClientboundLightUpdatePacketData data) {
      if (this.mc.level != null && this.enabled(Indicator.HIDDEN_GLOW)) {
         if (LightArrivals.blockLightIn(data, this.mc.level.getMinSectionY(), this.mc.level.getMinY(), 48)) {
            this.glowIncoming.add(new SusChunks.GlowJob(ScoreStore.key(chunkX, chunkZ)));
            this.glowIncoming.add(new SusChunks.GlowJob(ScoreStore.key(chunkX - 1, chunkZ)));
            this.glowIncoming.add(new SusChunks.GlowJob(ScoreStore.key(chunkX + 1, chunkZ)));
            this.glowIncoming.add(new SusChunks.GlowJob(ScoreStore.key(chunkX, chunkZ - 1)));
            this.glowIncoming.add(new SusChunks.GlowJob(ScoreStore.key(chunkX, chunkZ + 1)));
         }
      }
   }

   private void clearGlowJobs() {
      this.glowIncoming.clear();
      this.pendingGlow.clear();
   }

   private void drainGlowQueue() {
      SusChunks.GlowJob job;
      while ((job = this.glowIncoming.poll()) != null) {
         this.pendingGlow.putAndMoveToLast(job.chunkKey(), job);

         while (this.pendingGlow.size() > 4096) {
            this.pendingGlow.remove(this.pendingGlow.firstLongKey());
         }
      }
   }

   private void processGlowJobs() {
      int copies = 0;
      ObjectIterator<Entry<SusChunks.GlowJob>> iterator = this.pendingGlow.long2ObjectEntrySet().fastIterator();

      while (copies < 16 && iterator.hasNext()) {
         Entry<SusChunks.GlowJob> entry = (Entry<SusChunks.GlowJob>)iterator.next();
         long key = entry.getLongKey();
         int chunkX = ScoreStore.chunkX(key);
         int chunkZ = ScoreStore.chunkZ(key);
         if (this.mc.level.getChunkSource().getChunkNow(chunkX, chunkZ) == null) {
            iterator.remove();
         } else if (ChunkScanner.isLightReady(this.mc.level, chunkX, chunkZ)) {
            int minY = this.mc.level.getMinY();
            int maxY = 45;
            ChunkScanner.LightCopy light = ChunkScanner.LightCopy.capture(this.mc.level, chunkX, chunkZ, minY, maxY);
            iterator.remove();
            copies++;
            this.submitTask(() -> this.store.raiseIndicatorCount(key, Indicator.HIDDEN_GLOW, ChunkScanner.countGlowClusters(light, chunkX, chunkZ, minY, maxY)));
         }
      }
   }

   @EventHandler
   private void onRender3D(Render3DEvent event) {
      if (this.mc.player != null) {
         long[] snapshot = this.flaggedSnapshot;
         if (snapshot.length != 0) {
            int playerChunkX = this.mc.player.getBlockX() >> 4;
            int playerChunkZ = this.mc.player.getBlockZ() >> 4;
            int maxDistance = this.renderDistance.get();
            if (snapshot != this.lastSnapshot
               || playerChunkX != this.lastPlayerChunkX
               || playerChunkZ != this.lastPlayerChunkZ
               || maxDistance != this.lastRenderDistance) {
               this.lastSnapshot = snapshot;
               this.lastPlayerChunkX = playerChunkX;
               this.lastPlayerChunkZ = playerChunkZ;
               this.lastRenderDistance = maxDistance;
               ChunkRects.rebuild(snapshot, playerChunkX, playerChunkZ, maxDistance, 4096, true, this.visibleCells, this.renderRects);
               this.outline.rebuild(this.visibleCells, this.outlineEdges);
            }

            double y = this.fixedY.get().intValue();
            SettingColor c = this.color.get();
            this.outlineColor.set(c.r, c.g, c.b, 255);
            ChunkOutline.draw(event.renderer, this.renderRects, this.outlineEdges, y, c, this.outlineColor, ShapeMode.Both);
            if (this.tracers.get()) {
               this.drawTracers(event, snapshot, y, c);
            }
         }
      }
   }

   private void drawTracers(Render3DEvent event, long[] snapshot, double y, SettingColor c) {
      Vec3 from = RenderUtils.center;
      if (from != null) {
         if (snapshot != this.areasSnapshot) {
            this.areasSnapshot = snapshot;
            this.areas.find(snapshot);
         }

         this.tracerColor.set(c.r, c.g, c.b, 255);
         this.areas.nearest(this.mc.player.getX(), this.mc.player.getZ(), this.tracerCount.get(), this.tracerPick);

         for (int i = 0; i < this.tracerPick.size(); i++) {
            int area = this.tracerPick.getInt(i);
            event.renderer.line(from.x, from.y, from.z, this.areas.x(area), y, this.areas.z(area), this.tracerColor);
         }
      }
   }

   public File file() {
      return this.scope == null ? null : new File(new File(SpecterCore.FOLDER, "suschunks"), this.scope + ".csv");
   }

   public void saveIfRemembering() {
      if (this.remember.get()) {
         this.save();
      }
   }

   private void loadIfRemembering() {
      if (this.remember.get()) {
         this.load(false);
      }
   }

   public int save() {
      File file = this.file();
      if (file == null) {
         return -1;
      } else {
         int size = this.store.size();
         this.submitTask(() -> {
            try {
               this.store.save(file);
            } catch (IOException var3) {
               this.mc.execute(() -> this.error("Could not save chunk data: %s", new Object[]{var3.getMessage()}));
            }
         });
         return size;
      }
   }

   public boolean load(boolean announce) {
      File file = this.file();
      return file == null ? false : this.submitTask(() -> {
         try {
            int loaded = this.store.load(file);
            if (announce) {
               this.mc.execute(() -> this.info("Merged %d chunks from disk.", new Object[]{loaded}));
            }
         } catch (IOException var4) {
            this.mc.execute(() -> this.error("Could not load chunk data: %s", new Object[]{var4.getMessage()}));
         }
      });
   }

   public void reset() {
      if (!this.submitTask(this.store::clear)) {
         this.store.clear();
      }

      this.rescanDue.clear();
      this.clearGlowJobs();
      this.flaggedSnapshot = EMPTY_SNAPSHOT;
   }

   public ScoreStore store() {
      return this.store;
   }

   public String scope() {
      return this.scope;
   }

   public long[] flaggedSnapshot() {
      return this.flaggedSnapshot;
   }

   public int flaggedCount() {
      return this.flaggedSnapshot.length;
   }

   public int drawnCells() {
      return this.visibleCells.size();
   }

   public int drawnBoxes() {
      return this.renderRects.size() / 4;
   }

   public int renderBudget() {
      return 4096;
   }

   public int sourceCount() {
      return this.store.sourceCount();
   }

   public int clusterCount() {
      return this.store.clusterCount();
   }

   private record GlowJob(long chunkKey) {
   }

   public static enum Locate {
      Peaks,
      Areas;
   }
}

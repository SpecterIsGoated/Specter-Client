package com.jfant.specter.modules;

import com.jfant.specter.Scope;
import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.events.game.GameLeftEvent;
import com.jfant.specter.core.events.render.Render3DEvent;
import com.jfant.specter.core.events.world.BlockUpdateEvent;
import com.jfant.specter.core.events.world.ChunkDataEvent;
import com.jfant.specter.core.events.world.TickEvent;
import com.jfant.specter.core.renderer.ShapeMode;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.ColorSetting;
import com.jfant.specter.core.settings.EnumSetting;
import com.jfant.specter.core.settings.IntSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.core.utils.render.RenderUtils;
import com.jfant.specter.core.utils.render.color.Color;
import com.jfant.specter.core.utils.render.color.SettingColor;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

public class DebrisFinder extends Module {
   static final int FILLER_CHUNKS = 8;
   private static final int SWEEP_CHUNKS = 32;
   private static final int NOTIFY_TICKS = 40;
   private static final long SETTLE_MS = 3000L;
   private static final int LOG_TICKS = 1200;
   private static final int SAMPLE_LISTS = 3;
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgRender = this.settings.createGroup("Render");
   private final Setting<Integer> chunkRange = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("chunk-range")
            .description(
               "Show finds in chunks this far from you. The server only sends the real underground close to you; filler further out is skipped either way."
            )
            .defaultValue(8)
            .range(1, 16)
            .sliderRange(1, 16)
            .build()
      );
   private final Setting<Integer> minY = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("min-y")
            .description("Lowest height to look at. Debris generates from 8 to 119, most of it below 24.")
            .defaultValue(8)
            .range(-64, 320)
            .sliderRange(0, 127)
            .onChanged(v -> this.resweep = true)
            .build()
      );
   private final Setting<Integer> maxY = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("max-y")
            .description("Highest height to look at.")
            .defaultValue(119)
            .range(-64, 320)
            .sliderRange(0, 127)
            .onChanged(v -> this.resweep = true)
            .build()
      );
   private final Setting<Boolean> narrow = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("narrow")
            .description(
               "Shrink each hidden box to the solid blocks walled in on all six sides - the only ones the anti-xray may paint over. Off draws the whole 16x16x16 section."
            )
            .defaultValue(true)
            .onChanged(v -> this.resweep = true)
            .build()
      );
   private final Setting<Boolean> fillerGuard = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("filler-guard")
            .description(
               "Skip sections whose block list is an exact copy of the one in 8 or more other chunks: the made-up filler the server sends for far chunks."
            )
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> notify = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("notify")
            .description("Say new finds in chat, one line every 2 seconds at most. Off by default: in the nether nearly every chunk has some.")
            .defaultValue(false)
            .build()
      );
   private final Setting<Boolean> render = this.sgRender
      .add(new BoolSetting.Builder().name("render").description("Draw the boxes.").defaultValue(true).build());
   private final Setting<ShapeMode> shapeMode = this.sgRender
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("shape-mode"))
                     .description("How the boxes are drawn."))
                  .defaultValue(ShapeMode.Both))
               .visible(this.render::get))
            .build()
      );
   private final Setting<Integer> fillOpacity = this.sgRender
      .add(
         new IntSetting.Builder()
            .name("fill-opacity")
            .description("How see-through the box fill is, in percent: 100 is solid, 0 is no fill.")
            .defaultValue(15)
            .range(0, 100)
            .sliderRange(0, 100)
            .visible(this.render::get)
            .build()
      );
   private final Setting<SettingColor> hiddenColor = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("hidden-color")
            .description("Areas the block list gave away: the debris is somewhere inside.")
            .defaultValue(new SettingColor(255, 150, 40, 255))
            .build()
      );
   private final Setting<SettingColor> exactColor = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("exact-color")
            .description("Debris blocks the server actually sent.")
            .defaultValue(new SettingColor(255, 70, 110, 255))
            .build()
      );
   private final Setting<Boolean> tracers = this.sgRender
      .add(new BoolSetting.Builder().name("tracers").description("Draw a line from you to each find.").defaultValue(false).build());
   private final Setting<Boolean> chunkTracers = this.sgRender
      .add(
         new BoolSetting.Builder()
            .name("chunk-tracers")
            .description(
               "Draw a line from you to the hidden debris in each of the nearest few chunks that have some - a short list to fly to, where tracers draws a line to every find."
            )
            .defaultValue(false)
            .build()
      );
   private final Setting<Integer> chunkTracerCount = this.sgRender
      .add(
         new IntSetting.Builder()
            .name("chunk-tracer-count")
            .description("How many chunks to draw a line to, nearest first.")
            .defaultValue(3)
            .range(1, 8)
            .sliderRange(1, 8)
            .visible(this.chunkTracers::get)
            .build()
      );
   private final Setting<Integer> tracerOpacity = this.sgRender
      .add(
         new IntSetting.Builder()
            .name("tracer-opacity")
            .description("How see-through the tracers are, in percent: 100 is solid, 0 is invisible.")
            .defaultValue(100)
            .range(0, 100)
            .sliderRange(0, 100)
            .visible(() -> this.tracers.get() || this.chunkTracers.get())
            .build()
      );
   private final Setting<Boolean> distanceFade = this.sgRender
      .add(
         new BoolSetting.Builder()
            .name("distance-fade")
            .description(
               "Fade the colours from hidden-color and exact-color to far-color as a find gets further away, so the close ones stand out. Off draws every find in its own colour."
            )
            .defaultValue(false)
            .build()
      );
   private final Setting<SettingColor> farColor = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("far-color")
            .description("The colour a find fades to at fade-distance and beyond. Only the colour is used; the see-through amount stays as set above.")
            .defaultValue(new SettingColor(77, 163, 255, 255))
            .visible(this.distanceFade::get)
            .build()
      );
   private final Setting<Integer> fadeDistance = this.sgRender
      .add(
         new IntSetting.Builder()
            .name("fade-distance")
            .description("How far away, in blocks, a find has faded all the way to far-color.")
            .defaultValue(192)
            .range(8, 1024)
            .sliderRange(32, 512)
            .visible(this.distanceFade::get)
            .build()
      );
   private final Setting<DebrisFinder.Sort> sort = this.sgRender
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("sort"))
                  .description("Which finds max-render keeps. Deepest puts the low ones first: most debris sits below y 24, in bigger veins."))
               .defaultValue(DebrisFinder.Sort.Nearest))
            .build()
      );
   private final Setting<Integer> maxRender = this.sgRender
      .add(new IntSetting.Builder().name("max-render").description("Draw at most this many finds.").defaultValue(200).min(1).sliderRange(10, 1000).build());
   private final ConcurrentLinkedQueue<DebrisFinder.Result> results = new ConcurrentLinkedQueue<>();
   private ExecutorService scanner;
   private volatile int generation;
   private final Long2ObjectOpenHashMap<DebrisFinder.Result> chunks = new Long2ObjectOpenHashMap();
   private final Long2IntOpenHashMap seen = new Long2IntOpenHashMap();
   private final LongOpenHashSet revealed = new LongOpenHashSet();
   private final LongOpenHashSet gone = new LongOpenHashSet();
   private final LongOpenHashSet announced = new LongOpenHashSet();
   private final LongOpenHashSet announcedChunks = new LongOpenHashSet();
   private final LongOpenHashSet loggedFiller = new LongOpenHashSet();
   private List<DebrisFinder.Draw> draws = List.of();
   private List<DebrisFinder.Draw> chunkTargets = List.of();
   private String scope;
   private boolean resweep;
   private boolean drawStale;
   private int ticks;
   private int sampled;
   private double builtX;
   private double builtZ;
   private long analysed;
   private long listed;
   private long exactSections;
   private long hiddenSections;
   private long noRoom;
   private long fromUpdates;
   private int drawnHidden;
   private int drawnExact;
   private int fillerSkipped;
   private String lastLogged = "";
   private final Color hiddenSide = new Color();
   private final Color hiddenLine = new Color();
   private final Color exactSide = new Color();
   private final Color exactLine = new Color();
   private final Color hiddenTracer = new Color();
   private final Color exactTracer = new Color();
   private final Color fadeSide = new Color();
   private final Color fadeLine = new Color();
   private final Color fadeTracer = new Color();

   public DebrisFinder() {
      super(
         SpecterClientAddon.DONUT,
         "debris-finder",
         "Finds ancient debris: draws the debris the server sends, and boxes the spots whose block list still names debris after the anti-xray painted it over."
      );
   }

   @Override
   public void onActivate() {
      this.clear();
      this.scope = null;
      this.scanner = Executors.newSingleThreadExecutor(runnable -> {
         Thread thread = new Thread(runnable, "specter-debris-finder");
         thread.setDaemon(true);
         return thread;
      });
   }

   @Override
   public void onDeactivate() {
      this.logFunnel();
      if (this.scanner != null) {
         this.scanner.shutdownNow();
         this.scanner = null;
      }

      this.clear();
      this.scope = null;
   }

   @EventHandler
   private void onGameLeft(GameLeftEvent event) {
      this.logFunnel();
      this.clear();
      this.scope = null;
   }

   private void clear() {
      this.generation++;
      this.results.clear();
      this.chunks.clear();
      this.seen.clear();
      this.revealed.clear();
      this.gone.clear();
      this.announced.clear();
      this.announcedChunks.clear();
      this.loggedFiller.clear();
      this.draws = List.of();
      this.chunkTargets = List.of();
      this.analysed = this.listed = this.exactSections = this.hiddenSections = this.noRoom = this.fromUpdates = 0L;
      this.drawnHidden = this.drawnExact = this.fillerSkipped = 0;
      this.sampled = 0;
      this.lastLogged = "";
   }

   @EventHandler
   private void onChunkData(ChunkDataEvent event) {
      this.submit(event.chunk());
   }

   private void submit(LevelChunk chunk) {
      if (this.scanner != null) {
         int low = this.minY.get();
         int high = this.maxY.get();
         int gen = this.generation;
         boolean shrink = this.narrow.get();

         try {
            this.scanner.submit(() -> {
               List<DebrisFinder.Spot> spots = analyze(chunk, low, high, shrink);
               this.results.add(new DebrisFinder.Result(gen, chunk.getPos().toLong(), System.currentTimeMillis(), spots));
            });
         } catch (RejectedExecutionException var7) {
         }
      }
   }

   @EventHandler
   private void onBlockUpdate(BlockUpdateEvent event) {
      long pos = event.pos.asLong();
      if (event.newState.is(Blocks.ANCIENT_DEBRIS)) {
         this.gone.remove(pos);
         if (this.revealed.add(pos)) {
            this.fromUpdates++;
         }

         this.drawStale = true;
      } else if (event.oldState.is(Blocks.ANCIENT_DEBRIS) || this.revealed.contains(pos)) {
         this.revealed.remove(pos);
         this.gone.add(pos);
         this.drawStale = true;
      }
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      if (this.mc.level != null && this.mc.player != null) {
         String current = Scope.current(this.mc);
         if (!current.equals(this.scope) || this.resweep) {
            this.logFunnel();
            this.clear();
            this.scope = current;
            this.resweep = false;
            this.sweep();
         }

         DebrisFinder.Result r;
         while ((r = this.results.poll()) != null) {
            this.apply(r);
         }

         this.ticks++;
         if (this.ticks % 40 == 0) {
            this.prune();
         }

         if (this.drawStale || this.ticks % 10 == 0 || this.mc.player.distanceToSqr(this.builtX, this.mc.player.getY(), this.builtZ) > 64.0) {
            this.buildDraws();
         }

         if (this.ticks % 40 == 0 && this.notify.get()) {
            this.announce();
         }

         if (this.ticks % 1200 == 0) {
            this.logFunnel();
         }
      }
   }

   private void sweep() {
      ChunkPos center = this.mc.player.chunkPosition();

      for (int dx = -32; dx <= 32; dx++) {
         for (int dz = -32; dz <= 32; dz++) {
            LevelChunk chunk = this.mc.level.getChunkSource().getChunkNow(center.x + dx, center.z + dz);
            if (chunk != null) {
               this.submit(chunk);
            }
         }
      }
   }

   private void apply(DebrisFinder.Result r) {
      if (r.generation() == this.generation) {
         int cx = ChunkPos.getX(r.chunk());
         int cz = ChunkPos.getZ(r.chunk());
         if (this.mc.level.getChunkSource().getChunkNow(cx, cz) != null) {
            DebrisFinder.Result old = (DebrisFinder.Result)this.chunks.put(r.chunk(), r);
            if (old != null) {
               for (DebrisFinder.Spot s : old.spots()) {
                  this.unsee(s);
               }
            }

            for (DebrisFinder.Spot s : r.spots()) {
               this.see(s);
            }

            if (!this.gone.isEmpty()) {
               this.gone.removeIf(pos -> BlockPos.getX(pos) >> 4 == cx && BlockPos.getZ(pos) >> 4 == cz);
            }

            this.analysed++;

            for (DebrisFinder.Spot s : r.spots()) {
               this.listed++;
               if (!s.hidden()) {
                  this.exactSections++;
               } else if (s.candidates() == 0) {
                  this.noRoom++;
               } else {
                  this.hiddenSections++;
               }
            }

            this.drawStale = true;
         }
      }
   }

   private void see(DebrisFinder.Spot s) {
      if (s.signature() != 0L) {
         int n = this.seen.addTo(s.signature(), 1) + 1;
         if (n == 8 && this.loggedFiller.add(s.signature())) {
            SpecterClientAddon.LOG
               .info("Debris Finder: block list repeated in {} chunks, taken for filler (section y {}): {}", new Object[]{n, s.sectionY() * 16, s.list()});
         }
      }
   }

   private void unsee(DebrisFinder.Spot s) {
      if (s.signature() != 0L) {
         if (this.seen.addTo(s.signature(), -1) <= 1) {
            this.seen.remove(s.signature());
         }
      }
   }

   private boolean filler(DebrisFinder.Spot s) {
      return this.fillerGuard.get() && s.signature() != 0L && this.seen.get(s.signature()) >= 8;
   }

   private void prune() {
      ObjectIterator<Entry<DebrisFinder.Result>> it = this.chunks.long2ObjectEntrySet().fastIterator();

      while (it.hasNext()) {
         Entry<DebrisFinder.Result> entry = (Entry<DebrisFinder.Result>)it.next();
         long key = entry.getLongKey();
         if (this.mc.level.getChunkSource().getChunkNow(ChunkPos.getX(key), ChunkPos.getZ(key)) == null) {
            for (DebrisFinder.Spot s : ((DebrisFinder.Result)entry.getValue()).spots()) {
               this.unsee(s);
            }

            it.remove();
            this.drawStale = true;
         }
      }

      this.revealed.removeIf(pos -> this.mc.level.getChunkSource().getChunkNow(BlockPos.getX(pos) >> 4, BlockPos.getZ(pos) >> 4) == null);
      this.gone.removeIf(pos -> this.mc.level.getChunkSource().getChunkNow(BlockPos.getX(pos) >> 4, BlockPos.getZ(pos) >> 4) == null);
   }

   static List<DebrisFinder.Spot> analyze(LevelChunk chunk, int low, int high, boolean shrink) {
      List<DebrisFinder.Spot> spots = new ArrayList<>();
      LevelChunkSection[] sections = chunk.getSections();
      ChunkPos pos = chunk.getPos();
      List<BlockState> list = new ArrayList<>();
      boolean[] solid = new boolean[4608];

      for (int i = 0; i < sections.length; i++) {
         LevelChunkSection section = sections[i];
         if (section != null && !section.hasOnlyAir()) {
            int sectionY = chunk.getSectionYFromSectionIndex(i);
            int baseY = sectionY << 4;
            if (baseY + 15 >= low && baseY <= high) {
               list.clear();
               section.getStates().maybeHas(state -> {
                  list.add(state);
                  return false;
               });
               boolean global = list.isEmpty();
               if (global || !list.stream().noneMatch(state -> state.is(Blocks.ANCIENT_DEBRIS))) {
                  LongArrayList exact = new LongArrayList();

                  for (int y = 0; y < 16; y++) {
                     for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                           if (section.getBlockState(x, y, z).is(Blocks.ANCIENT_DEBRIS)) {
                              exact.add(BlockPos.asLong(pos.getMinBlockX() + x, baseY + y, pos.getMinBlockZ() + z));
                           }
                        }
                     }
                  }

                  if (!global || !exact.isEmpty()) {
                     long signature = global ? 0L : signature(sectionY, list);
                     String names = global ? "" : names(list);
                     if (!exact.isEmpty()) {
                        spots.add(new DebrisFinder.Spot(pos.x, sectionY, pos.z, signature, exact.toLongArray(), 0, 0, 0, 0, 0, 0, 0, names));
                     } else {
                        spots.add(hiddenSpot(sections, i, sectionY, pos, low, high, shrink, signature, names, solid));
                     }
                  }
               }
            }
         }
      }

      return spots;
   }

   private static DebrisFinder.Spot hiddenSpot(
      LevelChunkSection[] sections, int i, int sectionY, ChunkPos pos, int low, int high, boolean shrink, long signature, String names, boolean[] solid
   ) {
      int baseY = sectionY << 4;
      int yFrom = Math.max(0, low - baseY);
      int yTo = Math.min(15, high - baseY);
      int minX = 15;
      int minY = 15;
      int minZ = 15;
      int maxX = 0;
      int maxY = 0;
      int maxZ = 0;
      int candidates = 0;
      if (!shrink) {
         minZ = 0;
         minX = 0;
         maxZ = 15;
         maxX = 15;
         minY = yFrom;
         maxY = yTo;
         candidates = 256 * (yTo - yFrom + 1);
      } else {
         for (int y = -1; y <= 16; y++) {
            LevelChunkSection from = y < 0 ? (i > 0 ? sections[i - 1] : null) : (y > 15 ? (i + 1 < sections.length ? sections[i + 1] : null) : sections[i]);
            boolean outside = y < 0 ? i == 0 : y > 15 && i + 1 >= sections.length;
            int fy = y < 0 ? 15 : (y > 15 ? 0 : y);

            for (int z = 0; z < 16; z++) {
               for (int x = 0; x < 16; x++) {
                  boolean s;
                  if (outside) {
                     s = true;
                  } else if (from != null && !from.hasOnlyAir()) {
                     s = from.getBlockState(x, fy, z).isSolidRender();
                  } else {
                     s = false;
                  }

                  solid[((y + 1) * 16 + z) * 16 + x] = s;
               }
            }
         }

         for (int y = yFrom; y <= yTo; y++) {
            for (int z = 0; z < 16; z++) {
               for (int x = 0; x < 16; x++) {
                  if (solid(solid, x, y, z)
                     && solid(solid, x, y - 1, z)
                     && solid(solid, x, y + 1, z)
                     && solid(solid, x - 1, y, z)
                     && solid(solid, x + 1, y, z)
                     && solid(solid, x, y, z - 1)
                     && solid(solid, x, y, z + 1)) {
                     candidates++;
                     minX = Math.min(minX, x);
                     minY = Math.min(minY, y);
                     minZ = Math.min(minZ, z);
                     maxX = Math.max(maxX, x);
                     maxY = Math.max(maxY, y);
                     maxZ = Math.max(maxZ, z);
                  }
               }
            }
         }
      }

      return new DebrisFinder.Spot(
         pos.x,
         sectionY,
         pos.z,
         signature,
         new long[0],
         candidates,
         pos.getMinBlockX() + minX,
         baseY + minY,
         pos.getMinBlockZ() + minZ,
         pos.getMinBlockX() + maxX,
         baseY + maxY,
         pos.getMinBlockZ() + maxZ,
         names
      );
   }

   private static boolean solid(boolean[] solid, int x, int y, int z) {
      return x >= 0 && x <= 15 && z >= 0 && z <= 15 ? solid[((y + 1) * 16 + z) * 16 + x] : true;
   }

   private static long signature(int sectionY, List<BlockState> list) {
      long h = -3750763034362895579L ^ sectionY;

      for (BlockState state : list) {
         h ^= Block.getId(state);
         h *= 1099511628211L;
      }

      return h == 0L ? 1L : h;
   }

   private static String names(List<BlockState> list) {
      StringJoiner out = new StringJoiner(", ", "[", "]");

      for (BlockState state : list) {
         out.add(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath());
      }

      return out.toString();
   }

   private void buildDraws() {
      this.drawStale = false;
      this.builtX = this.mc.player.getX();
      this.builtZ = this.mc.player.getZ();
      double eyeY = this.mc.player.getEyeY();
      ChunkPos center = this.mc.player.chunkPosition();
      int range = this.chunkRange.get();
      List<DebrisFinder.Draw> out = new ArrayList<>();
      LongOpenHashSet exactSeen = new LongOpenHashSet();
      int filler = 0;
      int hidden = 0;
      ObjectIterator order = this.chunks.values().iterator();

      while (order.hasNext()) {
         DebrisFinder.Result r = (DebrisFinder.Result)order.next();
         int cx = ChunkPos.getX(r.chunk());
         int cz = ChunkPos.getZ(r.chunk());
         if (Math.max(Math.abs(cx - center.x), Math.abs(cz - center.z)) <= range) {
            for (DebrisFinder.Spot s : r.spots()) {
               if (this.filler(s)) {
                  filler++;
               } else if (s.hidden()) {
                  if (s.candidates() != 0) {
                     hidden++;
                     out.add(
                        new DebrisFinder.Draw(
                           s.key(),
                           true,
                           s.minX(),
                           s.minY(),
                           s.minZ(),
                           s.maxX() + 1,
                           s.maxY() + 1,
                           s.maxZ() + 1,
                           this.distSq((s.minX() + s.maxX() + 1) / 2.0, (s.minY() + s.maxY() + 1) / 2.0, (s.minZ() + s.maxZ() + 1) / 2.0, eyeY)
                        )
                     );
                  }
               } else {
                  for (long pos : s.exact()) {
                     if (!this.gone.contains(pos) && exactSeen.add(pos)) {
                        out.add(this.block(pos, eyeY));
                     }
                  }
               }
            }
         }
      }

      LongIterator var20 = this.revealed.iterator();

      while (var20.hasNext()) {
         long posx = (Long)var20.next();
         int cx = BlockPos.getX(posx) >> 4;
         int cz = BlockPos.getZ(posx) >> 4;
         if (Math.max(Math.abs(cx - center.x), Math.abs(cz - center.z)) <= range && exactSeen.add(posx)) {
            out.add(this.block(posx, eyeY));
         }
      }

      this.chunkTargets = this.nearestChunks(out);
      Comparator<DebrisFinder.Draw> orderx = Comparator.comparingDouble(DebrisFinder.Draw::distSq);
      if (this.sort.get() == DebrisFinder.Sort.Deepest) {
         orderx = Comparator.<DebrisFinder.Draw>comparingInt(d -> d.minY()).thenComparing(orderx);
      }

      out.sort(orderx);
      if (out.size() > this.maxRender.get()) {
         out = new ArrayList<>(out.subList(0, this.maxRender.get()));
      }

      this.draws = out;
      this.fillerSkipped = filler;
      this.drawnHidden = hidden;
      this.drawnExact = exactSeen.size();
   }

   private List<DebrisFinder.Draw> nearestChunks(List<DebrisFinder.Draw> all) {
      if (!this.chunkTracers.get()) {
         return List.of();
      } else {
         Long2ObjectOpenHashMap<DebrisFinder.Draw> best = new Long2ObjectOpenHashMap();

         for (DebrisFinder.Draw d : all) {
            if (d.hidden()) {
               long chunk = ChunkPos.asLong(BlockPos.getX(d.key()), BlockPos.getZ(d.key()));
               DebrisFinder.Draw have = (DebrisFinder.Draw)best.get(chunk);
               if (have == null || d.distSq() < have.distSq()) {
                  best.put(chunk, d);
               }
            }
         }

         List<DebrisFinder.Draw> nearest = new ArrayList<>(best.values());
         nearest.sort(Comparator.comparingDouble(DebrisFinder.Draw::distSq));
         return nearest.size() > this.chunkTracerCount.get() ? List.copyOf(nearest.subList(0, this.chunkTracerCount.get())) : List.copyOf(nearest);
      }
   }

   private DebrisFinder.Draw block(long pos, double eyeY) {
      int x = BlockPos.getX(pos);
      int y = BlockPos.getY(pos);
      int z = BlockPos.getZ(pos);
      return new DebrisFinder.Draw(pos, false, x, y, z, x + 1, y + 1, z + 1, this.distSq(x + 0.5, y + 0.5, z + 0.5, eyeY));
   }

   private double distSq(double x, double y, double z, double eyeY) {
      double dx = x - this.builtX;
      double dy = y - eyeY;
      double dz = z - this.builtZ;
      return dx * dx + dy * dy + dz * dz;
   }

   private void announce() {
      long settled = System.currentTimeMillis() - 3000L;
      int hidden = 0;
      int exact = 0;
      LongOpenHashSet newChunks = new LongOpenHashSet();
      DebrisFinder.Draw nearest = null;

      for (DebrisFinder.Draw d : this.draws) {
         if (d.hidden()) {
            DebrisFinder.Result r = (DebrisFinder.Result)this.chunks.get(ChunkPos.asLong(BlockPos.getX(d.key()), BlockPos.getZ(d.key())));
            if (r == null || r.time() > settled) {
               continue;
            }
         }

         if (this.announced.add(d.hidden() ? ~d.key() : d.key())) {
            if (d.hidden()) {
               hidden++;
               long chunk = ChunkPos.asLong(BlockPos.getX(d.key()), BlockPos.getZ(d.key()));
               if (this.announcedChunks.add(chunk)) {
                  newChunks.add(chunk);
               }
            } else {
               exact++;
            }

            if (nearest == null || d.distSq() < nearest.distSq()) {
               nearest = d;
            }
         }
      }

      if (nearest != null) {
         StringJoiner what = new StringJoiner(" and ");
         if (!newChunks.isEmpty()) {
            what.add(newChunks.size() + (newChunks.size() == 1 ? " new hidden-debris chunk" : " new hidden-debris chunks"));
         } else if (hidden > 0) {
            what.add(hidden + (hidden == 1 ? " more hidden debris spot" : " more hidden debris spots"));
         }

         if (exact > 0) {
            what.add(exact + (exact == 1 ? " new debris block" : " new debris blocks"));
         }

         int x = (nearest.minX() + nearest.maxX()) / 2;
         int y = (nearest.minY() + nearest.maxY()) / 2;
         int z = (nearest.minZ() + nearest.maxZ()) / 2;
         this.info("Found %s - nearest at %d, %d, %d (%d blocks away).", new Object[]{what, x, y, z, Math.round(Math.sqrt(nearest.distSq()))});
      }
   }

   @EventHandler
   private void onRender3D(Render3DEvent event) {
      List<DebrisFinder.Draw> list = this.draws;
      if (!list.isEmpty() && (this.render.get() || this.tracers.get() || this.chunkTracers.get())) {
         SettingColor h = this.hiddenColor.get();
         SettingColor e = this.exactColor.get();
         this.hiddenLine.set((Color)h);
         this.hiddenSide.set((Color)h);
         this.hiddenSide.a = h.a * this.fillOpacity.get() / 100;
         this.exactLine.set((Color)e);
         this.exactSide.set((Color)e);
         this.exactSide.a = e.a * this.fillOpacity.get() / 100;
         this.hiddenTracer.set((Color)h);
         this.hiddenTracer.a = h.a * this.tracerOpacity.get() / 100;
         this.exactTracer.set((Color)e);
         this.exactTracer.a = e.a * this.tracerOpacity.get() / 100;
         ShapeMode mode = this.shapeMode.get();
         if (this.fillOpacity.get() == 0 && mode == ShapeMode.Both) {
            mode = ShapeMode.Lines;
         }

         Vec3 from = RenderUtils.center;
         boolean drawTracers = this.tracers.get() && from != null && this.tracerOpacity.get() > 0;
         boolean fade = this.distanceFade.get();
         SettingColor far = this.farColor.get();
         double fadeAt = this.fadeDistance.get().intValue();

         for (DebrisFinder.Draw d : list) {
            Color side = d.hidden() ? this.hiddenSide : this.exactSide;
            Color edge = d.hidden() ? this.hiddenLine : this.exactLine;
            Color trace = d.hidden() ? this.hiddenTracer : this.exactTracer;
            if (fade) {
               SettingColor near = d.hidden() ? h : e;
               float t = (float)Math.min(1.0, Math.sqrt(d.distSq()) / fadeAt);
               shade(near, far, t, this.fillOpacity.get(), this.fadeSide);
               shade(near, far, t, 100, this.fadeLine);
               shade(near, far, t, this.tracerOpacity.get(), this.fadeTracer);
               side = this.fadeSide;
               edge = this.fadeLine;
               trace = this.fadeTracer;
            }

            if (this.render.get()) {
               event.renderer.box(d.minX(), d.minY(), d.minZ(), d.maxX(), d.maxY(), d.maxZ(), side, edge, mode, 0);
            }

            if (drawTracers) {
               event.renderer.line(from.x, from.y, from.z, (d.minX() + d.maxX()) / 2.0, (d.minY() + d.maxY()) / 2.0, (d.minZ() + d.maxZ()) / 2.0, trace);
            }
         }

         if (this.chunkTracers.get() && from != null && this.tracerOpacity.get() > 0) {
            for (DebrisFinder.Draw d : this.chunkTargets) {
               float t = fade ? (float)Math.min(1.0, Math.sqrt(d.distSq()) / fadeAt) : 0.0F;
               shade(h, fade ? far : h, t, this.tracerOpacity.get(), this.fadeTracer);
               event.renderer
                  .line(from.x, from.y, from.z, (d.minX() + d.maxX()) / 2.0, (d.minY() + d.maxY()) / 2.0, (d.minZ() + d.maxZ()) / 2.0, this.fadeTracer);
            }
         }
      }
   }

   private static void shade(SettingColor near, SettingColor far, float t, int opacity, Color out) {
      out.set(
         Math.round(near.r + (far.r - near.r) * t),
         Math.round(near.g + (far.g - near.g) * t),
         Math.round(near.b + (far.b - near.b) * t),
         near.a * opacity / 100
      );
   }

   private void logFunnel() {
      String line = String.format(
         "%d chunks read, %d sections list debris (%d with real debris, %d hidden, %d hidden with no walled-in room), %d debris from block updates; in range now: %d hidden spots, %d debris blocks, %d filler sections skipped",
         this.analysed,
         this.listed,
         this.exactSections,
         this.hiddenSections,
         this.noRoom,
         this.fromUpdates,
         this.drawnHidden,
         this.drawnExact,
         this.fillerSkipped
      );
      if (this.analysed != 0L && !line.equals(this.lastLogged)) {
         this.lastLogged = line;
         SpecterClientAddon.LOG.info("Debris Finder ({}): {}", this.scope, line);
         ObjectIterator var2 = this.chunks.values().iterator();

         while (var2.hasNext()) {
            DebrisFinder.Result r = (DebrisFinder.Result)var2.next();
            if (this.sampled >= 3) {
               break;
            }

            for (DebrisFinder.Spot s : r.spots()) {
               if (this.sampled >= 3) {
                  break;
               }

               if (s.hidden() && s.candidates() != 0 && !this.filler(s)) {
                  this.sampled++;
                  SpecterClientAddon.LOG
                     .info(
                        "Debris Finder: hidden spot at {}, {}, {} - {} walled-in blocks, block list {}",
                        new Object[]{(s.minX() + s.maxX()) / 2, (s.minY() + s.maxY()) / 2, (s.minZ() + s.maxZ()) / 2, s.candidates(), s.list()}
                     );
               }
            }
         }
      }
   }

   public int[] counts() {
      return new int[]{this.drawnHidden, this.drawnExact, this.fillerSkipped};
   }

   public List<BoundingBox> hiddenBoxes() {
      List<BoundingBox> out = new ArrayList<>();

      for (DebrisFinder.Draw d : this.draws) {
         if (d.hidden()) {
            out.add(new BoundingBox(d.minX(), d.minY(), d.minZ(), d.maxX() - 1, d.maxY() - 1, d.maxZ() - 1));
         }
      }

      return out;
   }

   @Override
   public String getInfoString() {
      return this.drawnHidden + this.drawnExact == 0 ? null : String.valueOf(this.drawnHidden + this.drawnExact);
   }

   private record Draw(long key, boolean hidden, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, double distSq) {
   }

   private record Result(int generation, long chunk, long time, List<DebrisFinder.Spot> spots) {
   }

   public static enum Sort {
      Nearest,
      Deepest;
   }

   private record Spot(
      int chunkX,
      int sectionY,
      int chunkZ,
      long signature,
      long[] exact,
      int candidates,
      int minX,
      int minY,
      int minZ,
      int maxX,
      int maxY,
      int maxZ,
      String list
   ) {
      boolean hidden() {
         return this.exact.length == 0;
      }

      long key() {
         return BlockPos.asLong(this.chunkX, this.sectionY, this.chunkZ);
      }
   }
}

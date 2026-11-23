package com.jfant.specter.modules;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.area.AreaKinds;
import com.jfant.specter.core.events.render.Render2DEvent;
import com.jfant.specter.core.events.render.Render3DEvent;
import com.jfant.specter.core.events.world.BlockUpdateEvent;
import com.jfant.specter.core.events.world.ChunkDataEvent;
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
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.joml.Vector3d;

public class SusAreaAnalyzer extends Module {
   private static final long TICK_BUDGET_NANOS = 2000000L;
   private static final int SWEEP_EVERY_TICKS = 100;
   private static final int REBUILD_EVERY_TICKS = 40;
   private static final int ENTITY_EVERY_TICKS = 20;
   private static final int CELL_SHIFT = 3;
   private static final int MIN_CELL_FLAGS = 3;
   private static final int MAX_GROW = 5;
   private static final int VILLAGE_REACH = 64;
   private static final int VILLAGE_PATHS = 8;
   private static final int DEEPSLATE_Y = 0;
   private static final int MAX_GENERIC_PER_CHUNK = 8192;
   private static final int MIN_PLAIN_STORAGE = 6;
   private static final int MIN_MINERAL = 4;
   private static final String[] COMPASS = new String[]{"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
   private static final AreaKinds.Kind[] KINDS = AreaKinds.KINDS;
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgRender = this.settings.createGroup("Render");
   private final Setting<Integer> sensitivity = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("sensitivity")
            .description("Low: only big, obvious bases. High: any small build counts.")
            .defaultValue(5)
            .range(1, 10)
            .sliderRange(1, 10)
            .build()
      );
   private final Setting<Integer> range = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("range")
            .description("How many chunks around you to look at, counting chunks remembered from earlier this session.")
            .defaultValue(32)
            .range(4, 64)
            .sliderRange(8, 64)
            .build()
      );
   private final Setting<Boolean> deepslateOnly = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("deepslate-only")
            .description("Only count blocks below y 0, in the deepslate layer. DonutSMP only shows that layer about 2 chunks around you.")
            .defaultValue(false)
            .build()
      );
   private final Setting<SettingColor> color = this.sgRender
      .add(new ColorSetting.Builder().name("color").description("The colour of the boxes and labels.").defaultValue(new SettingColor(25, 45, 170, 45)).build());
   private final Setting<Boolean> labels = this.sgRender
      .add(new BoolSetting.Builder().name("labels").description("Label each area with its flags, how far it is and what was found.").defaultValue(true).build());
   private final Long2ObjectOpenHashMap<Long2ByteOpenHashMap> store = new Long2ObjectOpenHashMap();
   private final Long2ObjectOpenHashMap<Long2ByteOpenHashMap> generic = new Long2ObjectOpenHashMap();
   private final Long2LongOpenHashMap biomeMasks = new Long2LongOpenHashMap();
   private final LongArrayList bells = new LongArrayList();
   private final Long2ByteOpenHashMap villageChunks = new Long2ByteOpenHashMap();
   private final Long2IntOpenHashMap flagCells = new Long2IntOpenHashMap();
   private final LongOpenHashSet indexedChunks = new LongOpenHashSet();
   private final LongOpenHashSet captured = new LongOpenHashSet();
   private final LongLinkedOpenHashSet pending = new LongLinkedOpenHashSet();
   private ClientLevel lastWorld;
   private int sweepIn;
   private int rebuildIn;
   private int entityIn;
   private List<SusAreaAnalyzer.Area> areas = List.of();
   private final Long2IntOpenHashMap logged = new Long2IntOpenHashMap();
   private final Vector3d labelPos = new Vector3d();
   private final Color line = new Color();
   private static final Color LABEL_TEXT = new Color(235, 235, 235);

   public SusAreaAnalyzer() {
      super(
         SpecterClientAddon.BASES, "sus-area-analyzer", "Boxes areas full of blocks that don't belong in nature: possible bases, with how many flags each has."
      );
   }

   @Override
   public void onActivate() {
      this.reset();
   }

   @Override
   public void onDeactivate() {
      long built = 0L;
      ObjectIterator var3 = this.generic.values().iterator();

      while (var3.hasNext()) {
         Long2ByteOpenHashMap blocks = (Long2ByteOpenHashMap)var3.next();
         built += blocks.size();
      }

      SpecterClientAddon.LOG
         .info(
            "Sus Area Analyzer: off - {} chunks remembered, {} built/out-of-biome blocks, {} possible bases",
            new Object[]{this.store.size(), built, this.areas.size()}
         );
      this.reset();
   }

   private void reset() {
      this.store.clear();
      this.generic.clear();
      this.biomeMasks.clear();
      this.captured.clear();
      this.pending.clear();
      this.lastWorld = null;
      this.sweepIn = this.rebuildIn = this.entityIn = 0;
      this.areas = List.of();
      this.logged.clear();
   }

   @EventHandler
   private void onChunkData(ChunkDataEvent event) {
      if (event.chunk() != null) {
         this.pending.add(event.chunk().getPos().pack());
      }
   }

   @EventHandler
   private void onBlockUpdate(BlockUpdateEvent event) {
      if (this.mc.level != null && this.lastWorld != null) {
         long pos = event.pos.asLong();
         int cx = event.pos.getX() >> 4;
         int cz = event.pos.getZ() >> 4;
         long chunk = ChunkPos.pack(cx, cz);
         byte kind = AreaKinds.flag(event.newState, this.biomesAround(cx, cz));
         Long2ByteOpenHashMap named = (Long2ByteOpenHashMap)this.store.get(chunk);
         Long2ByteOpenHashMap built = (Long2ByteOpenHashMap)this.generic.get(chunk);
         if (named != null) {
            named.remove(pos);
         }

         if (built != null) {
            built.remove(pos);
         }

         if (kind >= 0) {
            this.put(chunk, pos, kind);
         }
      }
   }

   private void put(long chunk, long pos, byte kind) {
      if (KINDS[kind].generic()) {
         Long2ByteOpenHashMap blocks = (Long2ByteOpenHashMap)this.generic.get(chunk);
         if (blocks == null) {
            this.generic.put(chunk, blocks = new Long2ByteOpenHashMap());
         }

         if (blocks.size() < 8192 || blocks.containsKey(pos)) {
            blocks.put(pos, kind);
         }
      } else {
         Long2ByteOpenHashMap blocksx = (Long2ByteOpenHashMap)this.store.get(chunk);
         if (blocksx == null) {
            this.store.put(chunk, blocksx = new Long2ByteOpenHashMap());
         }

         blocksx.put(pos, kind);
      }
   }

   private long biomesAround(int cx, int cz) {
      long mask = 0L;

      for (int dx = -1; dx <= 1; dx++) {
         for (int dz = -1; dz <= 1; dz++) {
            mask |= this.biomesOf(cx + dx, cz + dz);
         }
      }

      return mask;
   }

   private long biomesOf(int cx, int cz) {
      long key = ChunkPos.pack(cx, cz);
      if (this.biomeMasks.containsKey(key)) {
         return this.biomeMasks.get(key);
      } else {
         LevelChunk chunk = this.mc.level.getChunkSource().getChunkNow(cx, cz);
         if (chunk == null) {
            return 0L;
         } else {
            long mask = 0L;

            for (LevelChunkSection section : chunk.getSections()) {
               if (section != null) {
                  for (int bx = 0; bx < 4; bx++) {
                     for (int by = 0; by < 4; by++) {
                        for (int bz = 0; bz < 4; bz++) {
                           mask |= AreaKinds.biomeBits(section.getNoiseBiome(bx, by, bz));
                        }
                     }
                  }
               }
            }

            this.biomeMasks.put(key, mask);
            return mask;
         }
      }
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      if (this.mc.level != null && this.mc.player != null) {
         if (this.mc.level != this.lastWorld) {
            this.reset();
            this.lastWorld = this.mc.level;
            AreaKinds.build();
         }

         if (this.sweepIn-- <= 0) {
            this.sweep();
            this.sweepIn = 100;
         }

         long start = System.nanoTime();

         while (!this.pending.isEmpty() && System.nanoTime() - start < 2000000L) {
            long key = this.pending.removeFirstLong();
            LevelChunk chunk = this.mc.level.getChunkSource().getChunkNow(ChunkPos.getX(key), ChunkPos.getZ(key));
            if (chunk != null) {
               this.capture(chunk);
               this.captured.add(key);
            }
         }

         if (this.entityIn-- <= 0) {
            this.sweepEntities();
            this.entityIn = 20;
         }

         if (this.rebuildIn-- <= 0) {
            this.rebuild();
            this.rebuildIn = 40;
         }
      }
   }

   private void sweep() {
      ChunkPos at = this.mc.player.chunkPosition();
      int r = this.range.get();

      for (int ring = 0; ring <= r; ring++) {
         for (int dx = -ring; dx <= ring; dx++) {
            for (int dz = -ring; dz <= ring; dz++) {
               if (Math.max(Math.abs(dx), Math.abs(dz)) == ring) {
                  long key = ChunkPos.pack(at.x() + dx, at.z() + dz);
                  if (!this.captured.contains(key) && !this.pending.contains(key) && this.mc.level.getChunkSource().getChunkNow(at.x() + dx, at.z() + dz) != null) {
                     this.pending.add(key);
                  }
               }
            }
         }
      }
   }

   private void capture(LevelChunk chunk) {
      LevelChunkSection[] sections = chunk.getSections();
      int startX = chunk.getPos().getMinBlockX();
      int startZ = chunk.getPos().getMinBlockZ();
      long key = chunk.getPos().pack();
      this.biomeMasks.remove(key);
      long biomes = this.biomesAround(chunk.getPos().x(), chunk.getPos().z());

      for (int i = 0; i < sections.length; i++) {
         LevelChunkSection section = sections[i];
         if (section != null && !section.hasOnlyAir() && section.maybeHas(AreaKinds::maybeFlag)) {
            int baseY = chunk.getSectionYFromSectionIndex(i) * 16;

            for (int y = 0; y < 16; y++) {
               for (int z = 0; z < 16; z++) {
                  for (int x = 0; x < 16; x++) {
                     byte kind = AreaKinds.flag(section.getBlockState(x, y, z), biomes);
                     if (kind >= 0) {
                        this.put(key, BlockPos.asLong(startX + x, baseY + y, startZ + z), kind);
                     }
                  }
               }
            }
         }
      }
   }

   private void sweepEntities() {
      for (Entity entity : this.mc.level.entitiesForRendering()) {
         AreaKinds.Kind kind;
         if (entity instanceof ItemFrame) {
            kind = AreaKinds.Kind.FRAME;
         } else if (entity instanceof ArmorStand) {
            kind = AreaKinds.Kind.ARMOR_STAND;
         } else {
            if (!(entity instanceof Villager)) {
               continue;
            }

            kind = AreaKinds.Kind.VILLAGER;
         }

         long pos = entity.blockPosition().asLong();
         long chunk = ChunkPos.pack(BlockPos.getX(pos) >> 4, BlockPos.getZ(pos) >> 4);
         Long2ByteOpenHashMap blocks = (Long2ByteOpenHashMap)this.store.get(chunk);
         if (blocks == null) {
            this.store.put(chunk, blocks = new Long2ByteOpenHashMap());
         }

         if (!blocks.containsKey(pos)) {
            blocks.put(pos, (byte)kind.ordinal());
         }
      }
   }

   private double threshold() {
      return 60.0 * Math.pow(0.08333333333333333, (this.sensitivity.get() - 1) / 9.0);
   }

   private boolean counted(long pos) {
      return !this.deepslateOnly.get() || BlockPos.getY(pos) < 0;
   }

   private void rebuild() {
      int r = this.range.get();
      int pcx = this.mc.player.getBlockX() >> 4;
      int pcz = this.mc.player.getBlockZ() >> 4;
      this.bells.clear();
      this.villageChunks.clear();
      this.flagCells.clear();
      this.indexedChunks.clear();
      List<Long2ByteOpenHashMap> inRange = new ArrayList<>();
      ObjectIterator coreCells = this.store.long2ObjectEntrySet().iterator();

      while (coreCells.hasNext()) {
         Entry<Long2ByteOpenHashMap> e = (Entry<Long2ByteOpenHashMap>)coreCells.next();
         long key = e.getLongKey();
         if (Math.max(Math.abs(ChunkPos.getX(key) - pcx), Math.abs(ChunkPos.getZ(key) - pcz)) <= r + 4) {
            inRange.add((Long2ByteOpenHashMap)e.getValue());
            int paths = 0;
            ObjectIterator queue = ((Long2ByteOpenHashMap)e.getValue()).long2ByteEntrySet().iterator();

            while (queue.hasNext()) {
               it.unimi.dsi.fastutil.longs.Long2ByteMap.Entry b = (it.unimi.dsi.fastutil.longs.Long2ByteMap.Entry)queue.next();
               AreaKinds.Kind kind = KINDS[b.getByteValue()];
               if (kind == AreaKinds.Kind.BELL || kind == AreaKinds.Kind.VILLAGER) {
                  this.bells.add(b.getLongKey());
               } else if (kind == AreaKinds.Kind.PATH) {
                  paths++;
               }
            }

            if (paths >= 8) {
               this.bells.add(BlockPos.asLong((ChunkPos.getX(key) << 4) + 8, 64, (ChunkPos.getZ(key) << 4) + 8));
            }
         }
      }

      Long2ObjectOpenHashMap<LongArrayList> coreCellsx = new Long2ObjectOpenHashMap();

      for (Long2ByteOpenHashMap blocks : inRange) {
         ObjectIterator found = blocks.long2ByteEntrySet().iterator();

         while (found.hasNext()) {
            it.unimi.dsi.fastutil.longs.Long2ByteMap.Entry b = (it.unimi.dsi.fastutil.longs.Long2ByteMap.Entry)found.next();
            AreaKinds.Kind kind = KINDS[b.getByteValue()];
            long pos = b.getLongKey();
            if (kind.core && this.flagged(kind, pos)) {
               ((LongArrayList)coreCellsx.computeIfAbsent(cellOf(pos), k -> new LongArrayList())).add(pos);
            }
         }
      }

      double threshold = this.threshold();
      List<SusAreaAnalyzer.Area> found = new ArrayList<>();
      LongOpenHashSet seen = new LongOpenHashSet();
      LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
      Long2IntOpenHashMap depth = new Long2IntOpenHashMap();
      LongIterator var39 = coreCellsx.keySet().iterator();

      while (var39.hasNext()) {
         long start = (Long)var39.next();
         if (seen.add(start)) {
            LongOpenHashSet areaCells = new LongOpenHashSet();
            depth.clear();
            depth.put(start, 0);
            queue.enqueue(start);

            while (!queue.isEmpty()) {
               long cell = queue.dequeueLong();
               areaCells.add(cell);
               int d = depth.get(cell);
               int cx = BlockPos.getX(cell);
               int cy = BlockPos.getY(cell);
               int cz = BlockPos.getZ(cell);

               for (int dx = -1; dx <= 1; dx++) {
                  for (int dy = -1; dy <= 1; dy++) {
                     for (int dz = -1; dz <= 1; dz++) {
                        long n = BlockPos.asLong(cx + dx, cy + dy, cz + dz);
                        if (!seen.contains(n)) {
                           int next;
                           if (coreCellsx.containsKey(n)) {
                              next = 0;
                           } else {
                              if (d >= 5 || this.flagsIn(n) < 3) {
                                 continue;
                              }

                              next = d + 1;
                           }

                           seen.add(n);
                           depth.put(n, next);
                           queue.enqueue(n);
                        }
                     }
                  }
               }
            }

            SusAreaAnalyzer.Area area = this.area(areaCells, threshold);
            if (area != null) {
               found.add(area);
            }
         }
      }

      this.areas = found;

      for (SusAreaAnalyzer.Area a : found) {
         if (this.logged.get(a.key()) != a.flags()) {
            this.logged.put(a.key(), a.flags());
            String odd = a.misplaced().isEmpty() ? "" : " | out-of-biome: " + this.misplacedNames(a.misplaced());
            SpecterClientAddon.LOG
               .info(
                  "Sus Area Analyzer: Possible Base at {}, {}, {} ({}x{}x{}), {} flags, score {}: {}{}",
                  new Object[]{
                     (a.minX() + a.maxX()) / 2,
                     a.minY(),
                     (a.minZ() + a.maxZ()) / 2,
                     a.maxX() - a.minX(),
                     a.maxY() - a.minY(),
                     a.maxZ() - a.minZ(),
                     a.flags(),
                     Math.round(a.score()),
                     a.found(),
                     odd
                  }
               );
         }
      }
   }

   private static long cellOf(long pos) {
      return BlockPos.asLong(BlockPos.getX(pos) >> 3, BlockPos.getY(pos) >> 3, BlockPos.getZ(pos) >> 3);
   }

   private boolean flagged(AreaKinds.Kind kind, long pos) {
      return !(kind.points <= 0.0) && this.counted(pos)
         ? !kind.village || !this.villageChunk(ChunkPos.pack(BlockPos.getX(pos) >> 4, BlockPos.getZ(pos) >> 4))
         : false;
   }

   private boolean villageChunk(long chunk) {
      byte known = this.villageChunks.get(chunk);
      if (known != 0) {
         return known == 2;
      } else {
         int x = (ChunkPos.getX(chunk) << 4) + 8;
         int z = (ChunkPos.getZ(chunk) << 4) + 8;
         boolean near = false;

         for (int i = 0; i < this.bells.size() && !near; i++) {
            long b = this.bells.getLong(i);
            near = Math.abs(BlockPos.getX(b) - x) <= 72 && Math.abs(BlockPos.getZ(b) - z) <= 72;
         }

         this.villageChunks.put(chunk, (byte)(near ? 2 : 1));
         return near;
      }
   }

   private int flagsIn(long cell) {
      long chunk = ChunkPos.pack(BlockPos.getX(cell) >> 1, BlockPos.getZ(cell) >> 1);
      if (this.indexedChunks.add(chunk)) {
         for (int map = 0; map < 2; map++) {
            Long2ByteOpenHashMap blocks = (Long2ByteOpenHashMap)(map == 0 ? this.store : this.generic).get(chunk);
            if (blocks != null) {
               ObjectIterator var7 = blocks.long2ByteEntrySet().iterator();

               while (var7.hasNext()) {
                  it.unimi.dsi.fastutil.longs.Long2ByteMap.Entry e = (it.unimi.dsi.fastutil.longs.Long2ByteMap.Entry)var7.next();
                  AreaKinds.Kind kind = KINDS[e.getByteValue()];
                  if (!kind.core && this.flagged(kind, e.getLongKey())) {
                     this.flagCells.addTo(cellOf(e.getLongKey()), 1);
                  }
               }
            }
         }
      }

      return this.flagCells.get(cell);
   }

   private SusAreaAnalyzer.Area area(LongOpenHashSet areaCells, double threshold) {
      LongOpenHashSet chunks = new LongOpenHashSet();
      LongIterator counts = areaCells.iterator();

      while (counts.hasNext()) {
         long cell = (Long)counts.next();
         chunks.add(ChunkPos.pack(BlockPos.getX(cell) >> 1, BlockPos.getZ(cell) >> 1));
      }

      int[] countsx = new int[KINDS.length];
      int minX = 2147483647;
      int minY = 2147483647;
      int minZ = 2147483647;
      int maxX = -2147483648;
      int maxY = -2147483648;
      int maxZ = -2147483648;
      long key = 9223372036854775807L;
      LongArrayList misplaced = new LongArrayList();
      LongIterator coreScore = chunks.iterator();

      while (coreScore.hasNext()) {
         long chunk = (Long)coreScore.next();

         for (int map = 0; map < 2; map++) {
            Long2ByteOpenHashMap blocks = (Long2ByteOpenHashMap)(map == 0 ? this.store : this.generic).get(chunk);
            if (blocks != null) {
               ObjectIterator score = blocks.long2ByteEntrySet().iterator();

               while (score.hasNext()) {
                  it.unimi.dsi.fastutil.longs.Long2ByteMap.Entry e = (it.unimi.dsi.fastutil.longs.Long2ByteMap.Entry)score.next();
                  AreaKinds.Kind kind = KINDS[e.getByteValue()];
                  long p = e.getLongKey();
                  if (areaCells.contains(cellOf(p)) && this.flagged(kind, p)) {
                     countsx[kind.ordinal()]++;
                     if (kind.core) {
                        key = Math.min(key, p);
                     }

                     if (kind == AreaKinds.Kind.MISPLACED && misplaced.size() < 2000) {
                        misplaced.add(p);
                     }

                     int x = BlockPos.getX(p);
                     int y = BlockPos.getY(p);
                     int z = BlockPos.getZ(p);
                     minX = Math.min(minX, x);
                     minY = Math.min(minY, y);
                     minZ = Math.min(minZ, z);
                     maxX = Math.max(maxX, x + 1);
                     maxY = Math.max(maxY, y + 1);
                     maxZ = Math.max(maxZ, z + 1);
                  }
               }
            }
         }
      }

      if (key != 9223372036854775807L && playerStorage(countsx)) {
         double coreScorex = 0.0;
         double extra = 0.0;
         int flags = 0;

         for (AreaKinds.Kind kind : KINDS) {
            int n = countsx[kind.ordinal()];
            flags += n;
            if (kind.core) {
               coreScorex += kind.score(n);
            } else {
               extra += kind.score(n);
            }
         }

         double score = coreScorex + Math.min(extra, coreScorex * 2.0);
         return score < threshold ? null : new SusAreaAnalyzer.Area(minX, minY, minZ, maxX, maxY, maxZ, score, flags, top(countsx, false), key, misplaced);
      } else {
         return null;
      }
   }

   private static boolean playerStorage(int[] counts) {
      if (counts[AreaKinds.Kind.SHULKER.ordinal()]
            + counts[AreaKinds.Kind.HOPPER.ordinal()]
            + counts[AreaKinds.Kind.BEACON.ordinal()]
            + counts[AreaKinds.Kind.ENCHANTING.ordinal()]
            + counts[AreaKinds.Kind.ANVIL.ordinal()]
         > 0) {
         return true;
      } else {
         return counts[AreaKinds.Kind.CHEST.ordinal()] + counts[AreaKinds.Kind.BARREL.ordinal()] + counts[AreaKinds.Kind.SHELF.ordinal()] >= 6
            ? true
            : counts[AreaKinds.Kind.MINERAL.ordinal()] >= 4;
      }
   }

   private String misplacedNames(LongArrayList positions) {
      Map<String, Integer> names = new HashMap<>();
      MutableBlockPos at = new MutableBlockPos();

      for (int i = 0; i < positions.size(); i++) {
         long p = positions.getLong(i);
         if (this.mc.level.getChunkSource().getChunkNow(BlockPos.getX(p) >> 4, BlockPos.getZ(p) >> 4) != null) {
            String name = BuiltInRegistries.BLOCK.getKey(this.mc.level.getBlockState(at.set(p)).getBlock()).getPath();
            names.merge(name, 1, Integer::sum);
         }
      }

      return names.entrySet()
         .stream()
         .sorted((a, b) -> b.getValue() - a.getValue())
         .limit(5L)
         .map(e -> e.getKey() + " " + e.getValue())
         .reduce((a, b) -> a + ", " + b)
         .orElse("");
   }

   private static String top(int[] counts, boolean village) {
      List<AreaKinds.Kind> kinds = new ArrayList<>();

      for (AreaKinds.Kind kind : KINDS) {
         if (!(kind.points <= 0.0) && counts[kind.ordinal()] != 0 && (!village || !kind.village)) {
            kinds.add(kind);
         }
      }

      kinds.sort((a, b) -> Double.compare(b.score(counts[b.ordinal()]), a.score(counts[a.ordinal()])));
      StringBuilder text = new StringBuilder();

      for (int i = 0; i < Math.min(3, kinds.size()); i++) {
         if (i > 0) {
            text.append(", ");
         }

         text.append(kinds.get(i).describe(counts[kinds.get(i).ordinal()]));
      }

      return text.toString();
   }

   @EventHandler
   private void onRender3D(Render3DEvent event) {
      if (!this.areas.isEmpty()) {
         SettingColor c = this.color.get();
         this.line.set(c.r, c.g, c.b, 255);

         for (SusAreaAnalyzer.Area a : this.areas) {
            event.renderer.box(a.minX(), a.minY(), a.minZ(), a.maxX(), a.maxY(), a.maxZ(), c, this.line, ShapeMode.Both, 0);
         }
      }
   }

   @EventHandler
   private void onRender2D(Render2DEvent event) {
      if (this.labels.get() && !this.areas.isEmpty() && this.mc.player != null) {
         TextRenderer text = TextRenderer.get();
         SettingColor c = this.color.get();
         this.line.set(Math.min(255, c.r + 90), Math.min(255, c.g + 90), Math.min(255, c.b + 85), 255);

         for (SusAreaAnalyzer.Area a : this.areas) {
            double cx = (a.minX() + a.maxX()) / 2.0;
            double cz = (a.minZ() + a.maxZ()) / 2.0;
            this.labelPos.set(cx, a.maxY() + 0.8, cz);
            if (NametagUtils.to2D(this.labelPos, 1.4)) {
               String head = "Possible Base " + a.flags() + (a.flags() == 1 ? " Flag" : " Flags");
               String second = this.distance(a) + " - " + a.found();
               NametagUtils.begin(this.labelPos, event.graphics);
               text.begin(event.graphics);
               double h = text.getHeight(true);
               text.render(head, -text.getWidth(head, true) / 2.0, -h * 2.0, this.line, true);
               text.render(second, -text.getWidth(second, true) / 2.0, -h, LABEL_TEXT, true);
               text.end();
               NametagUtils.end(event.graphics);
            }
         }
      }
   }

   private String distance(SusAreaAnalyzer.Area a) {
      double px = this.mc.player.getX();
      double pz = this.mc.player.getZ();
      double dx = Math.max(Math.max(a.minX() - px, 0.0), px - a.maxX());
      double dz = Math.max(Math.max(a.minZ() - pz, 0.0), pz - a.maxZ());
      int d = (int)Math.sqrt(dx * dx + dz * dz);
      if (d == 0) {
         return "here";
      } else {
         double cx = (a.minX() + a.maxX()) / 2.0 - px;
         double cz = (a.minZ() + a.maxZ()) / 2.0 - pz;
         int i = (int)Math.round(Math.toDegrees(Math.atan2(cx, -cz)) / 45.0) & 7;
         return d + "m " + COMPASS[i];
      }
   }

   @Override
   public String getInfoString() {
      return this.areas.isEmpty() ? null : String.valueOf(this.areas.size());
   }

   private record Area(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, double score, int flags, String found, long key, LongArrayList misplaced) {
   }
}

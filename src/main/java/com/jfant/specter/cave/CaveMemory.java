package com.jfant.specter.cave;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.events.entity.player.BreakBlockEvent;
import com.jfant.specter.core.events.game.GameLeftEvent;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2LongMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

public final class CaveMemory {
   private static final long TICK_BUDGET_NANOS = 2000000L;
   private static final long RESCAN_DEBOUNCE_MS = 1000L;
   private static final long ANALYZE_EVERY_MS = 3000L;
   private static final int MAX_CHUNKS = 6000;
   private static final int SWEEP_EVERY_TICKS = 40;
   private static final double MOVE_TO_REANALYZE_SQ = 64.0;
   private static final LongOpenHashSet DUG = new LongOpenHashSet();
   private static boolean tracking;
   private final String name;
   private final Long2ObjectOpenHashMap<CaveSnapshot> memory = new Long2ObjectOpenHashMap();
   private final LongLinkedOpenHashSet pending = new LongLinkedOpenHashSet();
   private final Long2LongOpenHashMap rescanDue = new Long2LongOpenHashMap();
   private ClientLevel lastWorld;
   private int sweepIn;
   private int lastMinY = -2147483648;
   private int lastMaxY = -2147483648;
   private ExecutorService worker;
   private Future<CaveAnalysis.Result> job;
   private boolean dirty;
   private long lastRun;
   private double lastX;
   private double lastY;
   private double lastZ;

   public CaveMemory(String name) {
      this.name = name;
      if (!tracking) {
         tracking = true;
         SpecterCore.EVENT_BUS.subscribe(new CaveMemory.OwnDigs());
      }
   }

   public void start() {
      this.worker = Executors.newSingleThreadExecutor(runnable -> {
         Thread thread = new Thread(runnable, "Specter " + this.name);
         thread.setDaemon(true);
         thread.setPriority(1);
         return thread;
      });
      this.clear();
   }

   public void stop() {
      if (this.worker != null) {
         this.worker.shutdownNow();
      }

      this.worker = null;
      this.clear();
   }

   private void clear() {
      this.memory.clear();
      this.pending.clear();
      this.rescanDue.clear();
      this.lastWorld = null;
      this.sweepIn = 0;
      this.job = null;
      this.dirty = false;
      this.lastRun = 0L;
   }

   public int remembered() {
      return this.memory.size();
   }

   public void onChunkData(LevelChunk chunk, double playerX, double playerZ, int range) {
      if (chunk != null) {
         int dx = Math.abs(chunk.getPos().x() - ((int)Math.floor(playerX) >> 4));
         int dz = Math.abs(chunk.getPos().z() - ((int)Math.floor(playerZ) >> 4));
         if (Math.max(dx, dz) <= range + 1) {
            this.pending.add(chunk.getPos().pack());
         }
      }
   }

   public void onBlockUpdate(BlockPos pos) {
      long key = ChunkPos.pack(pos.getX() >> 4, pos.getZ() >> 4);
      if (this.memory.containsKey(key)) {
         this.rescanDue.putIfAbsent(key, System.currentTimeMillis() + 1000L);
      }
   }

   public CaveAnalysis.Result tick(Minecraft mc, int range, int minY, int maxY, CaveAnalysis.Settings settings) {
      if (mc.level != null && mc.player != null && this.worker != null) {
         if (mc.level != this.lastWorld) {
            this.clear();
            this.lastWorld = mc.level;
            CaveSnapshot.buildClasses();
         }

         if (minY != this.lastMinY || maxY != this.lastMaxY) {
            this.memory.clear();
            this.pending.clear();
            this.rescanDue.clear();
            this.sweepIn = 0;
            this.lastMinY = minY;
            this.lastMaxY = maxY;
         }

         if (this.sweepIn-- <= 0) {
            this.sweep(mc, range);
            this.sweepIn = 40;
         }

         if (!this.rescanDue.isEmpty()) {
            long now = System.currentTimeMillis();
            ObjectIterator<Entry> it = this.rescanDue.long2LongEntrySet().iterator();

            while (it.hasNext()) {
               Entry e = (Entry)it.next();
               if (e.getLongValue() <= now) {
                  this.pending.add(e.getLongKey());
                  it.remove();
               }
            }
         }

         long start = System.nanoTime();

         while (!this.pending.isEmpty() && System.nanoTime() - start < 2000000L) {
            long key = this.pending.removeFirstLong();
            LevelChunk chunk = mc.level.getChunkSource().getChunkNow(ChunkPos.getX(key), ChunkPos.getZ(key));
            if (chunk != null) {
               this.memory.put(key, CaveSnapshot.capture(chunk, minY, maxY).keepingHidden((CaveSnapshot)this.memory.get(key)));
               this.dirty = true;
            }
         }

         if (this.memory.size() > 6000) {
            this.trim(mc);
         }

         return this.analyze(mc, range, settings);
      } else {
         return null;
      }
   }

   private void sweep(Minecraft mc, int range) {
      ChunkPos at = mc.player.chunkPosition();

      for (int ring = 0; ring <= range; ring++) {
         for (int dx = -ring; dx <= ring; dx++) {
            for (int dz = -ring; dz <= ring; dz++) {
               if (Math.max(Math.abs(dx), Math.abs(dz)) == ring) {
                  long key = ChunkPos.pack(at.x() + dx, at.z() + dz);
                  if (!this.memory.containsKey(key) && !this.pending.contains(key) && mc.level.getChunkSource().getChunkNow(at.x() + dx, at.z() + dz) != null) {
                     this.pending.add(key);
                  }
               }
            }
         }
      }
   }

   private void trim(Minecraft mc) {
      int pcx = mc.player.getBlockX() >> 4;
      int pcz = mc.player.getBlockZ() >> 4;
      List<Long> keys = new ArrayList<>(this.memory.keySet());
      keys.sort(Comparator.comparingInt(k -> -Math.max(Math.abs(ChunkPos.getX(k) - pcx), Math.abs(ChunkPos.getZ(k) - pcz))));

      for (int i = 0; i < keys.size() && this.memory.size() > 5000; i++) {
         this.memory.remove(keys.get(i));
      }
   }

   private CaveAnalysis.Result analyze(Minecraft mc, int range, CaveAnalysis.Settings settings) {
      CaveAnalysis.Result done = null;
      if (this.job != null) {
         if (!this.job.isDone()) {
            return null;
         }

         try {
            done = this.job.get();
         } catch (Exception var26) {
            SpecterClientAddon.LOG.warn("{}: analysis failed", this.name, var26);
         }

         this.job = null;
      }

      long now = System.currentTimeMillis();
      double dx = mc.player.getX() - this.lastX;
      double dy = mc.player.getY() - this.lastY;
      double dz = mc.player.getZ() - this.lastZ;
      boolean moved = dx * dx + dy * dy + dz * dz > 64.0;
      if ((this.dirty || moved) && now - this.lastRun >= 3000L && !this.memory.isEmpty()) {
         int pcx = mc.player.getBlockX() >> 4;
         int pcz = mc.player.getBlockZ() >> 4;
         Long2ObjectOpenHashMap<CaveSnapshot> input = new Long2ObjectOpenHashMap();
         ObjectIterator own = this.memory.long2ObjectEntrySet().iterator();

         while (own.hasNext()) {
            it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry<CaveSnapshot> e = (it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry<CaveSnapshot>)own.next();
            long key = e.getLongKey();
            if (Math.max(Math.abs(ChunkPos.getX(key) - pcx), Math.abs(ChunkPos.getZ(key) - pcz)) <= range) {
               input.put(key, (CaveSnapshot)e.getValue());
            }
         }

         LongOpenHashSet ownx = settings.skipOwn() ? new LongOpenHashSet(DUG) : new LongOpenHashSet();
         double px = mc.player.getX();
         double py = mc.player.getY();
         double pz = mc.player.getZ();

         try {
            this.job = this.worker.submit(() -> CaveAnalysis.run(input, px, py, pz, settings, own));
         } catch (Exception var25) {
            return done;
         }

         this.dirty = false;
         this.lastRun = now;
         this.lastX = px;
         this.lastY = py;
         this.lastZ = pz;
         return done;
      } else {
         return done;
      }
   }

   private static final class OwnDigs {
      @EventHandler
      private void onBreak(BreakBlockEvent event) {
         if (event.blockPos != null && CaveMemory.DUG.size() < 500000) {
            CaveMemory.DUG.add(event.blockPos.asLong());
         }
      }

      @EventHandler
      private void onLeft(GameLeftEvent event) {
         CaveMemory.DUG.clear();
      }
   }
}

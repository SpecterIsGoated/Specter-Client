package com.jfant.specter;

import com.jfant.specter.scan.Indicator;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntMaps;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.world.level.ChunkPos;

public class ScoreStore {
   private final Long2ObjectOpenHashMap<int[]> chunks = new Long2ObjectOpenHashMap();
   private final Long2IntOpenHashMap stamps = new Long2IntOpenHashMap();
   private final Object lock = new Object();
   private final AtomicBoolean dirty = new AtomicBoolean(true);
   private volatile int sourceCount;
   private volatile int clusterCount;
   private volatile int discountedChunks;
   private volatile int shaftDiscountedChunks;
   private final LongOpenHashSet flaggedScratch = new LongOpenHashSet();
   private final LongOpenHashSet areaScratch = new LongOpenHashSet();
   private final LongOpenHashSet bestCells = new LongOpenHashSet();
   private final LongOpenHashSet usedSources = new LongOpenHashSet();
   private final LongOpenHashSet outputScratch = new LongOpenHashSet();
   private final LongOpenHashSet deepDarkScratch = new LongOpenHashSet();
   private final LongOpenHashSet trialDiscountScratch = new LongOpenHashSet();
   private final LongOpenHashSet shaftDiscountScratch = new LongOpenHashSet();
   private final LongOpenHashSet discountScratch = new LongOpenHashSet();
   private final Long2IntOpenHashMap clusterScratch = new Long2IntOpenHashMap();
   private final int[] effectiveScratch = new int[Indicator.COUNT];
   private final LongArrayList sourceKeys = new LongArrayList();
   private final IntArrayList sourceRadii = new IntArrayList();
   private final DoubleArrayList sourceScores = new DoubleArrayList();
   private final Long2IntOpenHashMap coverage = new Long2IntOpenHashMap();
   private static final int GLOW_CELLS_PER_POINT = 12;
   private final Long2DoubleOpenHashMap vetoableHeat = new Long2DoubleOpenHashMap();
   private final Long2DoubleOpenHashMap glowHeat = new Long2DoubleOpenHashMap();
   private final LongOpenHashSet budVetoes = new LongOpenHashSet();
   private final LongOpenHashSet heatSources = new LongOpenHashSet();
   private final LongOpenHashSet scannedScratch = new LongOpenHashSet();
   private long[] keysScratch = new long[0];
   private int[][] countsScratch = new int[0][];

   public static long key(int chunkX, int chunkZ) {
      return ChunkPos.pack(chunkX, chunkZ);
   }

   public static int chunkX(long key) {
      return (int)key;
   }

   public static int chunkZ(long key) {
      return (int)(key >> 32);
   }

   public void merge(long key, int[] counts) {
      this.merge(key, counts, Memory.join());
   }

   private void merge(long key, int[] counts, int stamp) {
      synchronized (this.lock) {
         this.stamps.put(key, Math.max(this.stamps.getOrDefault(key, -2147483648), stamp));
         int[] old = (int[])this.chunks.get(key);
         if (old != null) {
            if (covers(old, counts)) {
               return;
            }

            counts = max(old, counts);
         }

         this.chunks.put(key, counts);
      }

      this.dirty.set(true);
   }

   public void raiseIndicatorCount(long key, Indicator indicator, int count) {
      int ordinal = indicator.ordinal();
      synchronized (this.lock) {
         int[] counts = (int[])this.chunks.get(key);
         if (counts == null || ordinal >= counts.length || counts[ordinal] >= count) {
            return;
         }

         int[] next = (int[])counts.clone();
         next[ordinal] = count;
         this.chunks.put(key, next);
      }

      this.dirty.set(true);
   }

   private static boolean covers(int[] old, int[] next) {
      for (int i = 0; i < next.length; i++) {
         if (next[i] > (i < old.length ? old[i] : 0)) {
            return false;
         }
      }

      return true;
   }

   private static int[] max(int[] a, int[] b) {
      int[] out = new int[Math.max(a.length, b.length)];

      for (int i = 0; i < out.length; i++) {
         out[i] = Math.max(i < a.length ? a[i] : 0, i < b.length ? b[i] : 0);
      }

      return out;
   }

   public void clear() {
      synchronized (this.lock) {
         this.chunks.clear();
         this.stamps.clear();
      }

      this.dirty.set(true);
   }

   public int size() {
      synchronized (this.lock) {
         return this.chunks.size();
      }
   }

   public void markDirty() {
      this.dirty.set(true);
   }

   public boolean consumeDirty() {
      return this.dirty.getAndSet(false);
   }

   public long[] computeSnapshot(ScoreStore.ScoringConfig config) {
      int count;
      synchronized (this.lock) {
         count = this.chunks.size();
         if (this.keysScratch.length < count) {
            int capacity = Math.max(count, this.keysScratch.length * 2);
            this.keysScratch = new long[capacity];
            this.countsScratch = new int[capacity][];
         }

         int i = 0;

         for (ObjectIterator key = this.chunks.long2ObjectEntrySet().iterator(); key.hasNext(); i++) {
            it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry<int[]> entry = (it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry<int[]>)key.next();
            this.keysScratch[i] = entry.getLongKey();
            this.countsScratch[i] = (int[])entry.getValue();
         }
      }

      this.flaggedScratch.clear();
      this.areaScratch.clear();
      this.vetoableHeat.clear();
      this.glowHeat.clear();
      this.budVetoes.clear();
      this.heatSources.clear();
      this.usedSources.clear();
      this.outputScratch.clear();
      this.sourceKeys.clear();
      this.sourceRadii.clear();
      this.sourceScores.clear();
      collectDeepDark(this.keysScratch, this.countsScratch, count, this.deepDarkScratch);
      collectTrialDiscount(this.keysScratch, this.countsScratch, count, config.trialChamberRadius(), this.trialDiscountScratch);
      this.discountedChunks = this.trialDiscountScratch.size();
      collectShaftDiscount(this.keysScratch, this.countsScratch, count, config.shaftRadius(), config.minShafts(), this.shaftDiscountScratch);
      this.shaftDiscountedChunks = this.shaftDiscountScratch.size();
      this.discountScratch.clear();
      this.discountScratch.addAll(this.trialDiscountScratch);
      this.discountScratch.addAll(this.shaftDiscountScratch);
      collectGeodes(this.keysScratch, this.countsScratch, count, this.clusterScratch);

      for (int i = 0; i < count; i++) {
         effectiveCounts(this.keysScratch[i], this.countsScratch[i], this.deepDarkScratch, this.discountScratch, this.clusterScratch, this.effectiveScratch);
         double score = score(this.effectiveScratch, config.weights());
         if (score >= config.target()) {
            this.flaggedScratch.add(this.keysScratch[i]);
         }

         if (config.triangulating() && config.heatLocate()) {
            this.addHeat(this.keysScratch[i], this.countsScratch[i], this.effectiveScratch, config);
         } else if (config.triangulating()) {
            int radius = sourceRadius(this.effectiveScratch, config);
            if (radius >= 0) {
               this.sourceKeys.add(this.keysScratch[i]);
               this.sourceRadii.add(radius);
               this.sourceScores.add(score);
            }
         }
      }

      if (config.triangulating() && config.heatLocate()) {
         this.locateByHeat(this.keysScratch, count, config);
      } else {
         this.sourceCount = this.sourceKeys.size();
         if (this.sourceKeys.isEmpty()) {
            this.clusterCount = 0;
         } else {
            this.triangulate(config);
         }
      }

      int loneRadius = Math.max(0, config.loneRadius());
      LongIterator var13 = this.flaggedScratch.iterator();

      while (var13.hasNext()) {
         long key = (Long)var13.next();
         if (!this.usedSources.contains(key)) {
            addSquare(this.outputScratch, key, loneRadius);
         }
      }

      int areaBorder = Math.max(0, config.areaBorder());
      LongIterator var16 = this.areaScratch.iterator();

      while (var16.hasNext()) {
         long key = (Long)var16.next();
         addSquare(this.outputScratch, key, areaBorder);
      }

      return this.outputScratch.toLongArray();
   }

   private void addHeat(long key, int[] raw, int[] effective, ScoreStore.ScoringConfig config) {
      int r = Math.max(0, config.locateRadius());
      int glowIndex = Indicator.HIDDEN_GLOW.ordinal();
      int kelp = Indicator.SURFACE_KELP.ordinal();
      int vine = Indicator.GROUNDED_VINE.ordinal();
      double vetoable = 0.0;
      double glow = 0.0;

      for (int i = 0; i < config.weights().length && i < effective.length; i++) {
         if (!(config.weights()[i] <= 0.0) && effective[i] > 0 && i != kelp && i != vine) {
            if (i == glowIndex) {
               glow += effective[i] / 12 * Indicator.AMETHYST_CLUSTER.weight;
            } else {
               vetoable += effective[i] * config.weights()[i];
            }
         }
      }

      int buds = Indicator.AMETHYST_BUDS.ordinal();
      boolean ungrown = buds < raw.length && raw[buds] > 0;
      if (!(vetoable <= 0.0) || !(glow <= 0.0) || ungrown) {
         if (vetoable > 0.0 || glow > 0.0) {
            this.heatSources.add(key);
         }

         int chunkX = chunkX(key);
         int chunkZ = chunkZ(key);

         for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
               long area = key(chunkX + dx, chunkZ + dz);
               if (vetoable > 0.0) {
                  this.vetoableHeat.addTo(area, vetoable);
               }

               if (glow > 0.0) {
                  this.glowHeat.addTo(area, glow);
               }

               if (ungrown) {
                  this.budVetoes.add(area);
               }
            }
         }
      }
   }

   private void locateByHeat(long[] keys, int count, ScoreStore.ScoringConfig config) {
      int r = Math.max(0, config.locateRadius());
      this.scannedScratch.clear();

      for (int i = 0; i < count; i++) {
         this.scannedScratch.add(keys[i]);
      }

      Long2DoubleOpenHashMap field = new Long2DoubleOpenHashMap(this.vetoableHeat.size() + this.glowHeat.size());
      ObjectIterator var6 = this.vetoableHeat.long2DoubleEntrySet().iterator();

      while (var6.hasNext()) {
         it.unimi.dsi.fastutil.longs.Long2DoubleMap.Entry entry = (it.unimi.dsi.fastutil.longs.Long2DoubleMap.Entry)var6.next();
         if (!this.budVetoes.contains(entry.getLongKey())) {
            field.addTo(entry.getLongKey(), entry.getDoubleValue());
         }
      }

      var6 = this.glowHeat.long2DoubleEntrySet().iterator();

      while (var6.hasNext()) {
         it.unimi.dsi.fastutil.longs.Long2DoubleMap.Entry entry = (it.unimi.dsi.fastutil.longs.Long2DoubleMap.Entry)var6.next();
         field.addTo(entry.getLongKey(), entry.getDoubleValue());
      }

      var6 = field.long2DoubleEntrySet().iterator();

      while (var6.hasNext()) {
         it.unimi.dsi.fastutil.longs.Long2DoubleMap.Entry entry = (it.unimi.dsi.fastutil.longs.Long2DoubleMap.Entry)var6.next();
         long key = entry.getLongKey();
         double value = entry.getDoubleValue();
         if (!(value < config.locateThreshold())) {
            int chunkX = chunkX(key);
            int chunkZ = chunkZ(key);
            if (this.scannedAround(chunkX, chunkZ) >= 3 && isPeak(field, chunkX, chunkZ, value, r)) {
               this.areaScratch.add(key);
            }
         }
      }

      this.usedSources.addAll(this.heatSources);
      this.sourceCount = this.heatSources.size();
      this.clusterCount = 0;
   }

   private int scannedAround(int chunkX, int chunkZ) {
      int n = 0;

      for (int dx = -1; dx <= 1; dx++) {
         for (int dz = -1; dz <= 1; dz++) {
            if ((dx != 0 || dz != 0) && this.scannedScratch.contains(key(chunkX + dx, chunkZ + dz))) {
               n++;
            }
         }
      }

      return n;
   }

   private static boolean isPeak(Long2DoubleOpenHashMap field, int chunkX, int chunkZ, double value, int r) {
      for (int dx = -r; dx <= r; dx++) {
         for (int dz = -r; dz <= r; dz++) {
            if ((dx != 0 || dz != 0) && field.get(key(chunkX + dx, chunkZ + dz)) > value) {
               return false;
            }
         }
      }

      return true;
   }

   private static void addSquare(LongOpenHashSet into, long key, int r) {
      if (r == 0) {
         into.add(key);
      } else {
         int chunkX = chunkX(key);
         int chunkZ = chunkZ(key);

         for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
               into.add(key(chunkX + dx, chunkZ + dz));
            }
         }
      }
   }

   public int sourceCount() {
      return this.sourceCount;
   }

   public int discountedChunks() {
      return this.discountedChunks;
   }

   public int shaftDiscountedChunks() {
      return this.shaftDiscountedChunks;
   }

   public int clusterCount() {
      return this.clusterCount;
   }

   private static double score(int[] counts, double[] weights) {
      double score = 0.0;

      for (int i = 0; i < weights.length && i < counts.length; i++) {
         score += counts[i] * weights[i];
      }

      return score;
   }

   private static void collectDeepDark(long[] keys, int[][] counts, int count, LongOpenHashSet into) {
      into.clear();
      int ordinal = Indicator.DEEP_DARK.ordinal();

      for (int i = 0; i < count; i++) {
         if (ordinal < counts[i].length && counts[i][ordinal] > 0) {
            into.add(keys[i]);
         }
      }
   }

   private static void collectTrialDiscount(long[] keys, int[][] counts, int count, int radius, LongOpenHashSet into) {
      into.clear();
      if (radius >= 0) {
         int ordinal = Indicator.TRIAL_CHAMBER.ordinal();

         for (int i = 0; i < count; i++) {
            if (ordinal < counts[i].length && counts[i][ordinal] > 0) {
               addSquare(into, keys[i], radius);
            }
         }
      }
   }

   private static void collectShaftDiscount(long[] keys, int[][] counts, int count, int radius, int minShafts, LongOpenHashSet into) {
      into.clear();
      if (radius >= 0) {
         int ordinal = Indicator.SHAFT.ordinal();
         Long2IntOpenHashMap near = new Long2IntOpenHashMap();

         for (int i = 0; i < count; i++) {
            if (ordinal < counts[i].length && counts[i][ordinal] > 0) {
               int chunkX = chunkX(keys[i]);
               int chunkZ = chunkZ(keys[i]);

               for (int dx = -radius; dx <= radius; dx++) {
                  for (int dz = -radius; dz <= radius; dz++) {
                     near.addTo(key(chunkX + dx, chunkZ + dz), 1);
                  }
               }
            }
         }

         int needed = Math.max(1, minShafts);
         ObjectIterator var14 = Long2IntMaps.fastIterable(near).iterator();

         while (var14.hasNext()) {
            it.unimi.dsi.fastutil.longs.Long2IntMap.Entry cell = (it.unimi.dsi.fastutil.longs.Long2IntMap.Entry)var14.next();
            if (cell.getIntValue() >= needed) {
               into.add(cell.getLongKey());
            }
         }
      }
   }

   private static void collectGeodes(long[] keys, int[][] counts, int count, Long2IntOpenHashMap clusters) {
      clusters.clear();

      for (int i = 0; i < count; i++) {
         int n = chunkClusters(counts[i]);
         if (n > 0) {
            clusters.put(keys[i], n);
         }
      }
   }

   private static int chunkClusters(int[] c) {
      int glow = Indicator.HIDDEN_GLOW.ordinal();
      int seen = Indicator.AMETHYST_CLUSTER.ordinal();
      return Math.max(glow < c.length ? c[glow] : 0, seen < c.length ? c[seen] : 0);
   }

   private static int geodeClusters(long key, Long2IntOpenHashMap clusters) {
      int chunkX = chunkX(key);
      int chunkZ = chunkZ(key);
      int c = 0;

      for (int dx = -1; dx <= 1; dx++) {
         for (int dz = -1; dz <= 1; dz++) {
            c += clusters.get(key(chunkX + dx, chunkZ + dz));
         }
      }

      return c;
   }

   private static void effectiveCounts(long key, int[] counts, LongOpenHashSet deepDark, LongOpenHashSet discounted, Long2IntOpenHashMap clusters, int[] out) {
      if (discounted.contains(key)) {
         Arrays.fill(out, 0);
      } else {
         int n = Math.min(counts.length, out.length);
         System.arraycopy(counts, 0, out, 0, n);
         Arrays.fill(out, n, out.length, 0);
         if (out[Indicator.AMETHYST_BUDS.ordinal()] > 0) {
            out[Indicator.AMETHYST_CLUSTER.ordinal()] = 0;
         }

         int sculk = Indicator.SCULK.ordinal();
         if (out[sculk] > 0 && nearDeepDark(key, deepDark)) {
            out[sculk] = 0;
         }

         int glow = Indicator.HIDDEN_GLOW.ordinal();
         out[glow] = chunkClusters(counts) > 0 ? geodeClusters(key, clusters) : 0;
      }
   }

   private static boolean nearDeepDark(long key, LongOpenHashSet deepDark) {
      if (deepDark.isEmpty()) {
         return false;
      } else {
         int chunkX = chunkX(key);
         int chunkZ = chunkZ(key);

         for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
               if (deepDark.contains(key(chunkX + dx, chunkZ + dz))) {
                  return true;
               }
            }
         }

         return false;
      }
   }

   private static int sourceRadius(int[] counts, ScoreStore.ScoringConfig config) {
      int radius = 2147483647;

      for (int i = 0; i < config.triangulate().length && i < counts.length; i++) {
         if (config.triangulate()[i] && counts[i] * config.weights()[i] >= config.target()) {
            radius = Math.min(radius, config.radius()[i]);
         }
      }

      return radius == 2147483647 ? -1 : radius;
   }

   private void triangulate(ScoreStore.ScoringConfig config) {
      int n = this.sourceKeys.size();
      if (config.maxSources() > 0 && n > config.maxSources()) {
         it.unimi.dsi.fastutil.Arrays.quickSort(
            0, n, (a, b) -> Double.compare(this.sourceScores.getDouble(b), this.sourceScores.getDouble(a)), this::swapSources
         );
         n = config.maxSources();
         this.sourceKeys.size(n);
         this.sourceRadii.size(n);
         this.sourceScores.size(n);
         this.sourceCount = n;
      }

      int range = Math.max(1, config.clusterRange());
      int[] parent = new int[n];
      int i = 0;

      while (i < n) {
         parent[i] = i++;
      }

      Long2ObjectOpenHashMap<IntArrayList> buckets = new Long2ObjectOpenHashMap();

      for (int ix = 0; ix < n; ix++) {
         long key = this.sourceKeys.getLong(ix);
         long cell = key(Math.floorDiv(chunkX(key), range), Math.floorDiv(chunkZ(key), range));
         ((IntArrayList)buckets.computeIfAbsent(cell, c -> new IntArrayList())).add(ix);
      }

      ObjectIterator var23 = buckets.long2ObjectEntrySet().iterator();

      while (var23.hasNext()) {
         it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry<IntArrayList> entry = (it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry<IntArrayList>)var23.next();
         IntArrayList members = (IntArrayList)entry.getValue();

         for (int j = 1; j < members.size(); j++) {
            union(parent, members.getInt(0), members.getInt(j));
         }
      }

      var23 = buckets.long2ObjectEntrySet().iterator();

      while (var23.hasNext()) {
         it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry<IntArrayList> entry = (it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry<IntArrayList>)var23.next();
         int cellX = chunkX(entry.getLongKey());
         int cellZ = chunkZ(entry.getLongKey());

         for (int dcx = 0; dcx <= 1; dcx++) {
            for (int dcz = -1; dcz <= 1; dcz++) {
               if (dcx != 0 || dcz > 0) {
                  IntArrayList other = (IntArrayList)buckets.get(key(cellX + dcx, cellZ + dcz));
                  if (other != null) {
                     this.unionBuckets(parent, (IntArrayList)entry.getValue(), other, range);
                  }
               }
            }
         }
      }

      int[] clusterSize = new int[n];

      for (int ix = 0; ix < n; ix++) {
         clusterSize[find(parent, ix)]++;
      }

      int minSize = Math.max(1, config.minClusterSize());
      Int2ObjectOpenHashMap<IntArrayList> clusters = new Int2ObjectOpenHashMap();

      for (int ix = 0; ix < n; ix++) {
         int root = find(parent, ix);
         if (clusterSize[root] >= minSize) {
            ((IntArrayList)clusters.computeIfAbsent(root, r -> new IntArrayList())).add(ix);
         }
      }

      int areas = 0;
      ObjectIterator var37 = clusters.values().iterator();

      while (var37.hasNext()) {
         IntArrayList members = (IntArrayList)var37.next();
         IntArrayList left = members;

         while (left.size() >= minSize) {
            this.coverage.clear();

            for (int m = 0; m < left.size(); m++) {
               int ixx = left.getInt(m);
               long sourceKey = this.sourceKeys.getLong(ixx);
               int chunkX = chunkX(sourceKey);
               int chunkZ = chunkZ(sourceKey);
               int radius = this.sourceRadii.getInt(ixx);

               for (int dx = -radius; dx <= radius; dx++) {
                  for (int dz = -radius; dz <= radius; dz++) {
                     this.coverage.addTo(key(chunkX + dx, chunkZ + dz), 1);
                  }
               }
            }

            int best = 0;
            IntIterator it = this.coverage.values().iterator();

            while (it.hasNext()) {
               best = Math.max(best, it.nextInt());
            }

            if (best < minSize) {
               break;
            }

            this.bestCells.clear();
            ObjectIterator var42 = Long2IntMaps.fastIterable(this.coverage).iterator();

            while (var42.hasNext()) {
               it.unimi.dsi.fastutil.longs.Long2IntMap.Entry cell = (it.unimi.dsi.fastutil.longs.Long2IntMap.Entry)var42.next();
               if (cell.getIntValue() == best) {
                  this.bestCells.add(cell.getLongKey());
               }
            }

            this.areaScratch.addAll(this.bestCells);
            areas++;
            IntArrayList rest = new IntArrayList();

            for (int m = 0; m < left.size(); m++) {
               int ixx = left.getInt(m);
               if (this.reaches(ixx, this.bestCells)) {
                  this.usedSources.add(this.sourceKeys.getLong(ixx));
               } else {
                  rest.add(ixx);
               }
            }

            left = rest;
         }
      }

      this.clusterCount = areas;
   }

   private boolean reaches(int i, LongOpenHashSet cells) {
      long sourceKey = this.sourceKeys.getLong(i);
      int chunkX = chunkX(sourceKey);
      int chunkZ = chunkZ(sourceKey);
      int radius = this.sourceRadii.getInt(i);
      LongIterator it = cells.iterator();

      while (it.hasNext()) {
         long cell = it.nextLong();
         if (Math.abs(chunkX(cell) - chunkX) <= radius && Math.abs(chunkZ(cell) - chunkZ) <= radius) {
            return true;
         }
      }

      return false;
   }

   private void unionBuckets(int[] parent, IntArrayList a, IntArrayList b, int range) {
      for (int i = 0; i < a.size(); i++) {
         int ai = a.getInt(i);
         int ax = chunkX(this.sourceKeys.getLong(ai));
         int az = chunkZ(this.sourceKeys.getLong(ai));

         for (int j = 0; j < b.size(); j++) {
            int bj = b.getInt(j);
            if (find(parent, ai) == find(parent, bj)) {
               return;
            }

            long bk = this.sourceKeys.getLong(bj);
            if (Math.max(Math.abs(chunkX(bk) - ax), Math.abs(chunkZ(bk) - az)) <= range) {
               union(parent, ai, bj);
               return;
            }
         }
      }
   }

   private static int find(int[] parent, int i) {
      while (parent[i] != i) {
         parent[i] = parent[parent[i]];
         i = parent[i];
      }

      return i;
   }

   private static void union(int[] parent, int a, int b) {
      parent[find(parent, a)] = find(parent, b);
   }

   private void swapSources(int a, int b) {
      long key = this.sourceKeys.getLong(a);
      this.sourceKeys.set(a, this.sourceKeys.getLong(b));
      this.sourceKeys.set(b, key);
      int radius = this.sourceRadii.getInt(a);
      this.sourceRadii.set(a, this.sourceRadii.getInt(b));
      this.sourceRadii.set(b, radius);
      double score = this.sourceScores.getDouble(a);
      this.sourceScores.set(a, this.sourceScores.getDouble(b));
      this.sourceScores.set(b, score);
   }

   public List<ScoreStore.Entry> top(int n, ScoreStore.ScoringConfig config) {
      long[] keys;
      int[][] counts;
      synchronized (this.lock) {
         keys = new long[this.chunks.size()];
         counts = new int[this.chunks.size()][];
         int i = 0;

         for (ObjectIterator shaftDiscount = this.chunks.long2ObjectEntrySet().iterator(); shaftDiscount.hasNext(); i++) {
            it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry<int[]> entry = (it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry<int[]>)shaftDiscount.next();
            keys[i] = entry.getLongKey();
            counts[i] = (int[])entry.getValue();
         }
      }

      LongOpenHashSet deepDark = new LongOpenHashSet();
      collectDeepDark(keys, counts, keys.length, deepDark);
      LongOpenHashSet trialDiscount = new LongOpenHashSet();
      collectTrialDiscount(keys, counts, keys.length, config.trialChamberRadius(), trialDiscount);
      LongOpenHashSet shaftDiscount = new LongOpenHashSet();
      collectShaftDiscount(keys, counts, keys.length, config.shaftRadius(), config.minShafts(), shaftDiscount);
      trialDiscount.addAll(shaftDiscount);
      Long2IntOpenHashMap clusters = new Long2IntOpenHashMap();
      collectGeodes(keys, counts, keys.length, clusters);
      List<ScoreStore.Entry> entries = new ArrayList<>();

      for (int i = 0; i < keys.length; i++) {
         int[] effective = new int[Indicator.COUNT];
         effectiveCounts(keys[i], counts[i], deepDark, trialDiscount, clusters, effective);
         double score = score(effective, config.weights());
         if (!(score <= 0.0)) {
            entries.add(new ScoreStore.Entry(chunkX(keys[i]), chunkZ(keys[i]), (int[])counts[i].clone(), effective, score));
         }
      }

      entries.sort(Comparator.comparingDouble(ScoreStore.Entry::score).reversed());
      return entries.size() > n ? entries.subList(0, n) : entries;
   }

   public void save(File file) throws IOException {
      Long2ObjectOpenHashMap<int[]> copy;
      Long2IntOpenHashMap stampCopy;
      synchronized (this.lock) {
         copy = this.chunks.clone();
         stampCopy = this.stamps.clone();
      }

      if (!copy.isEmpty()) {
         int now = Memory.join();
         int last = -2147483648;
         LongIterator parent = copy.keySet().iterator();

         while (parent.hasNext()) {
            long key = (Long)parent.next();
            last = Math.max(last, stampCopy.getOrDefault(key, now));
         }

         File parentx = file.getParentFile();
         if (parentx != null && !parentx.exists() && !parentx.mkdirs()) {
            throw new IOException("Could not create directory " + parentx);
         } else {
            File tmp = new File(file.getParentFile(), file.getName() + ".tmp");

            try (BufferedWriter writer = Files.newBufferedWriter(tmp.toPath(), StandardCharsets.UTF_8)) {
               StringBuilder header = new StringBuilder("x,z");

               for (Indicator indicator : Indicator.VALUES) {
                  header.append(',').append(indicator.columnName);
               }

               header.append(',').append("join");
               writer.write("# suschunks v2");
               writer.newLine();
               writer.write("# last-join " + last);
               writer.newLine();
               writer.write(header.toString());
               writer.newLine();
               ObjectIterator var24 = copy.long2ObjectEntrySet().iterator();

               while (var24.hasNext()) {
                  it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry<int[]> entry = (it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry<int[]>)var24.next();
                  int[] counts = (int[])entry.getValue();
                  if (!isAllZero(counts)) {
                     long key = entry.getLongKey();
                     StringBuilder row = new StringBuilder().append(chunkX(key)).append(',').append(chunkZ(key));

                     for (int i = 0; i < Indicator.COUNT; i++) {
                        row.append(',').append(i < counts.length ? counts[i] : 0);
                     }

                     row.append(',').append(stampCopy.getOrDefault(key, now));
                     writer.write(row.toString());
                     writer.newLine();
                  }
               }
            }

            try {
               Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException var18) {
               Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
         }
      }
   }

   private static boolean isAllZero(int[] counts) {
      for (int count : counts) {
         if (count != 0) {
            return false;
         }
      }

      return true;
   }

   public int load(File file) throws IOException {
      if (!file.exists()) {
         return 0;
      } else {
         int loaded = 0;
         int[] columnToIndicator = null;
         int joinColumn = -1;

         String line;
         try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            while ((line = reader.readLine()) != null) {
               line = line.trim();
               if (!line.isEmpty() && !line.startsWith("#")) {
                  if (line.startsWith("x,")) {
                     columnToIndicator = mapHeader(line);
                     joinColumn = Arrays.asList(line.split(",")).indexOf("join");
                  } else {
                     String[] parts = line.split(",");
                     if (parts.length >= 3) {
                        try {
                           int stamp = joinColumn >= 0 && joinColumn < parts.length ? Memory.stamp(parts[joinColumn]) : Memory.start();
                           if (!Memory.expired(stamp)) {
                              long key = key(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
                              int[] counts = new int[Indicator.COUNT];

                              for (int i = 0; i + 2 < parts.length; i++) {
                                 int ordinal = columnToIndicator != null
                                    ? (i < columnToIndicator.length ? columnToIndicator[i] : -1)
                                    : (i < Indicator.COUNT ? i : -1);
                                 if (ordinal >= 0) {
                                    counts[ordinal] = Integer.parseInt(parts[i + 2].trim());
                                 }
                              }

                              this.merge(key, counts, stamp);
                              loaded++;
                           }
                        } catch (NumberFormatException var15) {
                        }
                     }
                  }
               }
            }
         }

         return loaded;
      }
   }

   private static int[] mapHeader(String header) {
      String[] columns = header.split(",");
      int[] map = new int[Math.max(0, columns.length - 2)];

      for (int i = 2; i < columns.length; i++) {
         map[i - 2] = -1;

         for (Indicator indicator : Indicator.VALUES) {
            if (indicator.columnName.equals(columns[i].trim())) {
               map[i - 2] = indicator.ordinal();
               break;
            }
         }
      }

      return map;
   }

   public record Entry(int chunkX, int chunkZ, int[] counts, int[] effective, double score) {
   }

   public record ScoringConfig(
      double[] weights,
      boolean[] triangulate,
      int[] radius,
      double target,
      int maxSources,
      boolean triangulating,
      int clusterRange,
      int minClusterSize,
      int areaBorder,
      int loneRadius,
      int trialChamberRadius,
      boolean heatLocate,
      int locateRadius,
      double locateThreshold,
      int shaftRadius,
      int minShafts
   ) {
   }
}

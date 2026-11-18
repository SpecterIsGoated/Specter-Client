package com.jfant.specter.cave;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;

public final class CaveAnalysis {
   public static final String[] SIZES = new String[]{"Small", "Medium", "Large", "Colossal"};
   private static final int[] SIZE_STARTS = new int[]{5000, 25000, 100000};
   private static final int MAX_RUN = 96;
   private static final int MAX_TUNNELS = 2000;
   private static final int SPOT_GAP = 3;
   private final Long2ObjectOpenHashMap<CaveSnapshot> chunks;
   private final double px;
   private final double py;
   private final double pz;
   private final CaveAnalysis.Settings settings;
   private final LongOpenHashSet own;
   private final Long2ObjectOpenHashMap<long[]> visited = new Long2ObjectOpenHashMap();
   private final List<CaveAnalysis.Tunnel> tunnels = new ArrayList<>();
   private CaveSnapshot cache;
   private int cacheX = -2147483648;
   private int cacheZ = -2147483648;
   private int caveTunnels;

   public static int sizeIndex(int cells) {
      int i = 0;

      while (i < SIZE_STARTS.length && cells >= SIZE_STARTS[i]) {
         i++;
      }

      return i;
   }

   private CaveAnalysis(Long2ObjectOpenHashMap<CaveSnapshot> chunks, double px, double py, double pz, CaveAnalysis.Settings settings, LongOpenHashSet own) {
      this.chunks = chunks;
      this.px = px;
      this.py = py;
      this.pz = pz;
      this.settings = settings;
      this.own = own;
   }

   public static CaveAnalysis.Result run(
      Long2ObjectOpenHashMap<CaveSnapshot> chunks, double px, double py, double pz, CaveAnalysis.Settings settings, LongOpenHashSet own
   ) {
      return new CaveAnalysis(chunks, px, py, pz, settings, own).run();
   }

   private CaveAnalysis.Result run() {
      long start = System.currentTimeMillis();
      List<CaveAnalysis.Cave> caves = new ArrayList<>();
      LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
      LongArrayList cells = new LongArrayList();
      ObjectIterator counts = this.chunks.values().iterator();

      while (counts.hasNext()) {
         CaveSnapshot snap = (CaveSnapshot)counts.next();
         long mask = snap.caveSections;

         while (mask != 0L) {
            int s = Long.numberOfTrailingZeros(mask);
            mask &= mask - 1L;
            if (s >= snap.sectionCount()) {
               break;
            }

            int baseY = snap.lowSection + s << 4;

            for (int index = 0; index < 4096; index++) {
               byte c = snap.get(s, index);
               if (c == 1 || c == 2) {
                  int x = snap.chunkX << 4 | index & 15;
                  int z = snap.chunkZ << 4 | index >>> 4 & 15;
                  int y = baseY + (index >>> 8);
                  if (this.visit(x, y, z)) {
                     CaveAnalysis.Cave cave = this.flood(x, y, z, queue, cells);
                     if (cave != null) {
                        caves.add(cave);
                     }
                  }
               }
            }
         }
      }

      caves.sort(Comparator.comparingDouble(this::nearDistanceSq));
      int[] countsx = new int[SIZES.length];

      for (CaveAnalysis.Cave cave : caves) {
         countsx[cave.size()]++;
      }

      return new CaveAnalysis.Result(List.copyOf(caves), this.spots(), countsx, this.chunks.size(), System.currentTimeMillis() - start);
   }

   private double nearDistanceSq(CaveAnalysis.Cave c) {
      double dx = c.nearX() + 0.5 - this.px;
      double dy = c.nearY() + 0.5 - this.py;
      double dz = c.nearZ() + 0.5 - this.pz;
      return dx * dx + dy * dy + dz * dz;
   }

   private CaveAnalysis.Cave flood(int x0, int y0, int z0, LongArrayFIFOQueue queue, LongArrayList cells) {
      queue.clear();
      cells.clear();
      this.caveTunnels = 0;
      queue.enqueue(BlockPos.asLong(x0, y0, z0));
      long sx = 0L;
      long sy = 0L;
      long sz = 0L;
      double nearD = 1.7976931348623157E308;
      long near = 0L;

      while (!queue.isEmpty()) {
         long p = queue.dequeueLong();
         cells.add(p);
         int x = BlockPos.getX(p);
         int y = BlockPos.getY(p);
         int z = BlockPos.getZ(p);
         sx += x;
         sy += y;
         sz += z;
         double dx = x + 0.5 - this.px;
         double dy = y + 0.5 - this.py;
         double dz = z + 0.5 - this.pz;
         double d = dx * dx + dy * dy + dz * dz;
         if (d < nearD) {
            nearD = d;
            near = p;
         }

         if (this.settings.tunnels() && this.cls(x, y, z) == 1 && this.cls(x, y - 1, z) == 0) {
            this.caveTunnels = this.caveTunnels + this.findTunnels(x, y, z);
         }

         this.step(x + 1, y, z, queue);
         this.step(x - 1, y, z, queue);
         this.step(x, y + 1, z, queue);
         this.step(x, y - 1, z, queue);
         this.step(x, y, z + 1, queue);
         this.step(x, y, z - 1, queue);
      }

      int size = cells.size();
      if (this.settings.caves() && size >= this.settings.minCave()) {
         double cx = (double)sx / size;
         double cy = (double)sy / size;
         double cz = (double)sz / size;
         long best = cells.getLong(0);
         long bestFloored = -9223372036854775808L;
         double bestD = 1.7976931348623157E308;
         double bestFlooredD = 1.7976931348623157E308;

         for (int i = 0; i < size; i++) {
            long c = cells.getLong(i);
            int xx = BlockPos.getX(c);
            int yx = BlockPos.getY(c);
            int zx = BlockPos.getZ(c);
            double dxx = xx - cx;
            double dyx = yx - cy;
            double dzx = zx - cz;
            double dxxx = dxx * dxx + dyx * dyx + dzx * dzx;
            if (dxxx < bestD) {
               bestD = dxxx;
               best = c;
            }

            if (dxxx < bestFlooredD && this.cls(xx, yx, zx) == 1 && this.cls(xx, yx - 1, zx) == 0) {
               bestFlooredD = dxxx;
               bestFloored = c;
            }
         }

         long marker = bestFloored != -9223372036854775808L ? bestFloored : best;
         return new CaveAnalysis.Cave(
            BlockPos.getX(marker),
            BlockPos.getY(marker),
            BlockPos.getZ(marker),
            size,
            BlockPos.getX(near),
            BlockPos.getY(near),
            BlockPos.getZ(near),
            this.caveTunnels
         );
      } else {
         return null;
      }
   }

   private void step(int x, int y, int z, LongArrayFIFOQueue queue) {
      byte c = this.cls(x, y, z);
      if ((c == 1 || c == 2) && this.visit(x, y, z)) {
         queue.enqueue(BlockPos.asLong(x, y, z));
      }
   }

   private int findTunnels(int x, int y, int z) {
      int kept = 0;
      int min = this.settings.minTunnel();

      for (int axis = 0; axis < 2; axis++) {
         int along = axis == 0 ? x : z;
         int c = axis == 0 ? z : x;
         if (this.cls(axis, along, y, c - 1) == 0) {
            for (int w = 1; w <= 3; w++) {
               for (int h = 2; h <= 3; h++) {
                  if (this.slice(axis, along, y, c, w, h) == 1 && this.slice(axis, along - 1, y, c, w, h) < 0) {
                     int n = 0;
                     int strict = 0;

                     int m;
                     while (n < 96 && (m = this.slice(axis, along + n, y, c, w, h)) >= 0) {
                        n++;
                        strict += m;
                     }

                     if (n >= min && strict * 10 >= n * 6 && this.keep(axis, along, y, c, w, h, n, 0)) {
                        kept++;
                     }
                  }
               }
            }
         }
      }

      return kept;
   }

   private int slice(int axis, int along, int y0, int c0, int w, int h) {
      for (int i = 0; i < w; i++) {
         int c = c0 + i;
         if (this.cls(axis, along, y0 - 1, c) != 0 || this.cls(axis, along, y0 + h, c) != 0) {
            return -1;
         }

         for (int j = 0; j < h; j++) {
            if (this.cls(axis, along, y0 + j, c) != 1) {
               return -1;
            }
         }
      }

      for (int jx = 0; jx < h; jx++) {
         if (this.cls(axis, along, y0 + jx, c0 - 1) != 0 || this.cls(axis, along, y0 + jx, c0 + w) != 0) {
            return 0;
         }
      }

      return 1;
   }

   private boolean keep(int axis, int along, int y, int c, int w, int h, int length, int dy) {
      if (this.tunnels.size() >= 2000) {
         return false;
      } else if (this.settings.skipOwn() && this.dugByYou(axis, along, y, c, w, h, length, dy)) {
         return false;
      } else {
         CaveAnalysis.Tunnel t = new CaveAnalysis.Tunnel(axis, axis == 0 ? along : c, y, axis == 0 ? c : along, w, h, length, dy);

         for (int i = 0; i < this.tunnels.size(); i++) {
            CaveAnalysis.Tunnel o = this.tunnels.get(i);
            if (o.sameShapeOverlaps(t)) {
               if (t.length() > o.length()) {
                  this.tunnels.set(i, t);
               }

               return false;
            }
         }

         this.tunnels.add(t);
         return true;
      }
   }

   private boolean dugByYou(int axis, int along, int y, int c, int w, int h, int length, int dy) {
      if (this.own.isEmpty()) {
         return false;
      } else {
         int total = 0;
         int yours = 0;

         for (int n = 0; n < length; n++) {
            for (int i = 0; i < w; i++) {
               for (int j = 0; j < h; j++) {
                  int a = along + n;
                  int yy = y + n * dy + j;
                  int cc = c + i;
                  total++;
                  if (this.own.contains(axis == 0 ? BlockPos.asLong(a, yy, cc) : BlockPos.asLong(cc, yy, a))) {
                     yours++;
                  }
               }
            }
         }

         return yours * 3 >= total;
      }
   }

   private List<CaveAnalysis.Spot> spots() {
      int n = this.tunnels.size();
      if (n == 0) {
         return List.of();
      } else {
         int[] parent = new int[n];
         int i = 0;

         while (i < n) {
            parent[i] = i++;
         }

         for (int ix = 0; ix < n; ix++) {
            for (int j = ix + 1; j < n; j++) {
               if (boxesWithin(this.tunnels.get(ix), this.tunnels.get(j), 3)) {
                  parent[find(parent, ix)] = find(parent, j);
               }
            }
         }

         Long2ObjectOpenHashMap<List<CaveAnalysis.Tunnel>> groups = new Long2ObjectOpenHashMap();

         for (int ix = 0; ix < n; ix++) {
            ((List)groups.computeIfAbsent(find(parent, ix), k -> new ArrayList())).add(this.tunnels.get(ix));
         }

         List<CaveAnalysis.Spot> spots = new ArrayList<>(groups.size());
         ObjectIterator var5 = groups.values().iterator();

         while (var5.hasNext()) {
            List<CaveAnalysis.Tunnel> group = (List<CaveAnalysis.Tunnel>)var5.next();
            CaveAnalysis.Tunnel longest = group.getFirst();
            int length = 0;

            for (CaveAnalysis.Tunnel t : group) {
               length += t.length();
               if (t.length() > longest.length()) {
                  longest = t;
               }
            }

            int mid = longest.length() / 2;
            int cross = (longest.w() - 1) / 2;
            int x = longest.axis() == 0 ? longest.x() + mid : longest.x() + cross;
            int z = longest.axis() == 0 ? longest.z() + cross : longest.z() + mid;
            int y = longest.y() + mid * longest.dy();
            spots.add(new CaveAnalysis.Spot(x, y, z, group.size(), length, longest.w(), longest.h()));
         }

         return spots;
      }
   }

   private static int find(int[] parent, int i) {
      while (parent[i] != i) {
         parent[i] = parent[parent[i]];
         i = parent[i];
      }

      return i;
   }

   private static boolean boxesWithin(CaveAnalysis.Tunnel a, CaveAnalysis.Tunnel b, int gap) {
      return a.minX() < b.maxX() + gap
         && b.minX() < a.maxX() + gap
         && a.minY() < b.maxY() + gap
         && b.minY() < a.maxY() + gap
         && a.minZ() < b.maxZ() + gap
         && b.minZ() < a.maxZ() + gap;
   }

   private boolean visit(int x, int y, int z) {
      long key = SectionPos.asLong(x >> 4, y >> 4, z >> 4);
      long[] bits = (long[])this.visited.get(key);
      if (bits == null) {
         bits = new long[64];
         this.visited.put(key, bits);
      }

      int i = (y & 15) << 8 | (z & 15) << 4 | x & 15;
      long bit = 1L << (i & 63);
      if ((bits[i >>> 6] & bit) != 0L) {
         return false;
      } else {
         bits[i >>> 6] = bits[i >>> 6] | bit;
         return true;
      }
   }

   private byte cls(int axis, int along, int y, int c) {
      return axis == 0 ? this.cls(along, y, c) : this.cls(c, y, along);
   }

   private byte cls(int x, int y, int z) {
      int cx = x >> 4;
      int cz = z >> 4;
      if (cx != this.cacheX || cz != this.cacheZ) {
         this.cache = (CaveSnapshot)this.chunks.get(ChunkPos.pack(cx, cz));
         this.cacheX = cx;
         this.cacheZ = cz;
      }

      return this.cache == null ? 3 : this.cache.get(x, y, z);
   }

   public record Cave(int x, int y, int z, int cells, int nearX, int nearY, int nearZ, int tunnels) {
      public int size() {
         return CaveAnalysis.sizeIndex(this.cells);
      }
   }

   public record Result(List<CaveAnalysis.Cave> caves, List<CaveAnalysis.Spot> spots, int[] sizeCounts, int chunks, long millis) {
   }

   public record Settings(int minCave, boolean caves, boolean tunnels, int minTunnel, boolean skipOwn) {
   }

   public record Spot(int x, int y, int z, int count, int length, int w, int h) {
   }

   public record Tunnel(int axis, int x, int y, int z, int w, int h, int length, int dy) {
      int minX() {
         return this.x;
      }

      int maxX() {
         return this.axis == 0 ? this.x + this.length : this.x + this.w;
      }

      int minZ() {
         return this.z;
      }

      int maxZ() {
         return this.axis == 0 ? this.z + this.w : this.z + this.length;
      }

      int minY() {
         return this.dy >= 0 ? this.y : this.y + this.dy * (this.length - 1);
      }

      int maxY() {
         return (this.dy <= 0 ? this.y : this.y + this.dy * (this.length - 1)) + this.h;
      }

      boolean sameShapeOverlaps(CaveAnalysis.Tunnel o) {
         return this.axis == o.axis && this.dy == o.dy && this.w == o.w && this.h == o.h && CaveAnalysis.boxesWithin(this, o, 0);
      }
   }
}

package com.jfant.specter.render;

import com.jfant.specter.ScoreStore;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

public final class ChunkAreas {
   private final LongOpenHashSet remaining = new LongOpenHashSet();
   private final LongOpenHashSet members = new LongOpenHashSet();
   private final LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
   private final LongArrayList area = new LongArrayList();
   private final DoubleArrayList xs = new DoubleArrayList();
   private final DoubleArrayList zs = new DoubleArrayList();
   private final IntArrayList sizes = new IntArrayList();

   public void find(long[] chunks) {
      this.xs.clear();
      this.zs.clear();
      this.sizes.clear();
      this.members.clear();

      for (long key : chunks) {
         this.members.add(key);
      }

      this.remaining.clear();
      this.remaining.addAll(this.members);

      for (long start : chunks) {
         if (this.remaining.remove(start)) {
            this.area.clear();
            this.queue.enqueue(start);
            double sumX = 0.0;
            double sumZ = 0.0;

            while (!this.queue.isEmpty()) {
               long key = this.queue.dequeueLong();
               this.area.add(key);
               int cx = ScoreStore.chunkX(key);
               int cz = ScoreStore.chunkZ(key);
               sumX += (cx << 4) + 8;
               sumZ += (cz << 4) + 8;

               for (int dx = -1; dx <= 1; dx++) {
                  for (int dz = -1; dz <= 1; dz++) {
                     long next = ScoreStore.key(cx + dx, cz + dz);
                     if (this.members.contains(next) && this.remaining.remove(next)) {
                        this.queue.enqueue(next);
                     }
                  }
               }
            }

            double mx = sumX / this.area.size();
            double mz = sumZ / this.area.size();
            if (!this.members.contains(ScoreStore.key((int)Math.floor(mx) >> 4, (int)Math.floor(mz) >> 4))) {
               double best = 1.7976931348623157E308;
               double bx = mx;
               double bz = mz;

               for (int i = 0; i < this.area.size(); i++) {
                  long key = this.area.getLong(i);
                  double x = (ScoreStore.chunkX(key) << 4) + 8;
                  double z = (ScoreStore.chunkZ(key) << 4) + 8;
                  double d = (x - mx) * (x - mx) + (z - mz) * (z - mz);
                  if (d < best) {
                     best = d;
                     bx = x;
                     bz = z;
                  }
               }

               mx = bx;
               mz = bz;
            }

            this.xs.add(mx);
            this.zs.add(mz);
            this.sizes.add(this.area.size());
         }
      }
   }

   public int count() {
      return this.xs.size();
   }

   public double x(int i) {
      return this.xs.getDouble(i);
   }

   public double z(int i) {
      return this.zs.getDouble(i);
   }

   public int size(int i) {
      return this.sizes.getInt(i);
   }

   public void nearest(double px, double pz, int limit, IntArrayList out) {
      out.clear();

      for (int i = 0; i < this.xs.size(); i++) {
         double d = this.distSq(i, px, pz);
         int at = out.size();

         while (at > 0 && this.distSq(out.getInt(at - 1), px, pz) > d) {
            at--;
         }

         if (at < limit) {
            out.add(at, i);
            if (out.size() > limit) {
               out.removeInt(limit);
            }
         }
      }
   }

   private double distSq(int i, double px, double pz) {
      double dx = this.xs.getDouble(i) - px;
      double dz = this.zs.getDouble(i) - pz;
      return dx * dx + dz * dz;
   }
}

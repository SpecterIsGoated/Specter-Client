package com.jfant.specter.scan;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongArrays;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2IntMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.Arrays;
import net.minecraft.core.BlockPos;

public final class LightBoxes {
   private static final int[][] SIDES = new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

   private LightBoxes() {
   }

   public static LightBoxes.Result build(Long2IntOpenHashMap hits, boolean denoise, int minLight, int maxBoxes, int centerX, int centerZ) {
      if (hits.isEmpty()) {
         return LightBoxes.Result.EMPTY;
      } else {
         LongOpenHashSet skip = new LongOpenHashSet();
         if (denoise) {
            denoise(hits, minLight, skip);
         }

         LongArrayList surface = new LongArrayList();
         ObjectIterator total = hits.long2IntEntrySet().iterator();

         while (total.hasNext()) {
            Entry entry = (Entry)total.next();
            long pos = entry.getLongKey();
            if (!skip.contains(pos) && exposed(hits, skip, pos)) {
               surface.add(pos);
            }
         }

         int totalx = surface.size();
         int dropped = 0;
         if (totalx > maxBoxes) {
            long[] sorted = surface.toLongArray();
            LongArrays.quickSort(sorted, (a, b) -> Long.compare(distSq(a, centerX, centerZ), distSq(b, centerX, centerZ)));
            dropped = totalx - maxBoxes;
            surface = new LongArrayList(sorted, 0, maxBoxes);
         }

         Int2ObjectOpenHashMap<LongArrayList> byLevel = new Int2ObjectOpenHashMap();

         for (int i = 0; i < surface.size(); i++) {
            long pos = surface.getLong(i);
            ((LongArrayList)byLevel.computeIfAbsent(hits.get(pos), level -> new LongArrayList())).add(pos);
         }

         int[] levels = byLevel.keySet().toIntArray();
         Arrays.sort(levels);
         long[][] cells = new long[levels.length][];

         for (int i = 0; i < levels.length; i++) {
            cells[i] = ((LongArrayList)byLevel.get(levels[i])).toLongArray();
         }

         return new LightBoxes.Result(levels, cells, totalx, dropped);
      }
   }

   private static long distSq(long pos, int centerX, int centerZ) {
      long dx = BlockPos.getX(pos) - centerX;
      long dz = BlockPos.getZ(pos) - centerZ;
      return dx * dx + dz * dz;
   }

   private static void denoise(Long2IntOpenHashMap hits, int minLight, LongOpenHashSet skip) {
      LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
      ObjectIterator var4 = hits.long2IntEntrySet().iterator();

      while (var4.hasNext()) {
         Entry entry = (Entry)var4.next();
         long source = entry.getLongKey();
         int light = entry.getIntValue();
         if (light >= minLight + 1 && light <= minLight + 2 && !skip.contains(source) && !brighterAround(hits, source, light)) {
            skip.add(source);
            queue.enqueue(source);

            while (!queue.isEmpty()) {
               long pos = queue.dequeueLong();
               int x = BlockPos.getX(pos);
               int y = BlockPos.getY(pos);
               int z = BlockPos.getZ(pos);

               for (int[] side : SIDES) {
                  long next = BlockPos.asLong(x + side[0], y + side[1], z + side[2]);
                  int value = hits.get(next);
                  if (value >= minLight && value <= light && skip.add(next)) {
                     queue.enqueue(next);
                  }
               }
            }
         }
      }
   }

   private static boolean brighterAround(Long2IntOpenHashMap hits, long pos, int light) {
      int x = BlockPos.getX(pos);
      int y = BlockPos.getY(pos);
      int z = BlockPos.getZ(pos);

      for (int dx = -1; dx <= 1; dx++) {
         for (int dy = -1; dy <= 1; dy++) {
            for (int dz = -1; dz <= 1; dz++) {
               if (hits.get(BlockPos.asLong(x + dx, y + dy, z + dz)) > light) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   private static boolean exposed(Long2IntOpenHashMap hits, LongOpenHashSet skip, long pos) {
      int x = BlockPos.getX(pos);
      int y = BlockPos.getY(pos);
      int z = BlockPos.getZ(pos);

      for (int[] side : SIDES) {
         long next = BlockPos.asLong(x + side[0], y + side[1], z + side[2]);
         if (!hits.containsKey(next) || skip.contains(next)) {
            return true;
         }
      }

      return false;
   }

   public record Result(int[] levels, long[][] cells, int total, int dropped) {
      public static final LightBoxes.Result EMPTY = new LightBoxes.Result(new int[0], new long[0][], 0, 0);
   }
}

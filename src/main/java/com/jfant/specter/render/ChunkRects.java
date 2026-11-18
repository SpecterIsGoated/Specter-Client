package com.jfant.specter.render;

import com.jfant.specter.ScoreStore;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongArrays;

public final class ChunkRects {
   private ChunkRects() {
   }

   public static void rebuild(
      long[] snapshot, int playerChunkX, int playerChunkZ, int maxDistance, int budget, boolean merge, LongArrayList visibleCells, IntArrayList renderRects
   ) {
      visibleCells.clear();

      for (long key : snapshot) {
         int chunkX = ScoreStore.chunkX(key);
         int chunkZ = ScoreStore.chunkZ(key);
         if (Math.abs(chunkX - playerChunkX) <= maxDistance && Math.abs(chunkZ - playerChunkZ) <= maxDistance) {
            visibleCells.add(key);
         }
      }

      if (visibleCells.size() > budget) {
         LongArrays.quickSort(
            visibleCells.elements(),
            0,
            visibleCells.size(),
            (a, b) -> Integer.compare(distance(a, playerChunkX, playerChunkZ), distance(b, playerChunkX, playerChunkZ))
         );
         visibleCells.size(Math.max(0, budget));
      }

      renderRects.clear();
      if (!visibleCells.isEmpty()) {
         LongArrays.quickSort(visibleCells.elements(), 0, visibleCells.size(), (a, b) -> {
            int za = ScoreStore.chunkZ(a);
            int zb = ScoreStore.chunkZ(b);
            return za != zb ? Integer.compare(za, zb) : Integer.compare(ScoreStore.chunkX(a), ScoreStore.chunkX(b));
         });
         if (!merge) {
            for (int i = 0; i < visibleCells.size(); i++) {
               long keyx = visibleCells.getLong(i);
               int chunkX = ScoreStore.chunkX(keyx);
               int chunkZ = ScoreStore.chunkZ(keyx);
               renderRects.add(chunkX);
               renderRects.add(chunkZ);
               renderRects.add(chunkX);
               renderRects.add(chunkZ);
            }
         } else {
            IntArrayList prevRuns = new IntArrayList();
            IntArrayList curRuns = new IntArrayList();
            int prevZ = -2147483648;
            int index = 0;
            int total = visibleCells.size();

            while (index < total) {
               int rowZ = ScoreStore.chunkZ(visibleCells.getLong(index));
               curRuns.clear();

               while (index < total && ScoreStore.chunkZ(visibleCells.getLong(index)) == rowZ) {
                  int x1 = ScoreStore.chunkX(visibleCells.getLong(index));
                  int x2 = x1;
                  index++;

                  while (index < total && ScoreStore.chunkZ(visibleCells.getLong(index)) == rowZ && ScoreStore.chunkX(visibleCells.getLong(index)) == x2 + 1) {
                     x2++;
                     index++;
                  }

                  int rectIndex = -1;
                  if (rowZ == prevZ + 1) {
                     for (int p = 0; p < prevRuns.size(); p += 3) {
                        if (prevRuns.getInt(p) == x1 && prevRuns.getInt(p + 1) == x2) {
                           rectIndex = prevRuns.getInt(p + 2);
                           break;
                        }
                     }
                  }

                  if (rectIndex >= 0) {
                     renderRects.set(rectIndex + 3, rowZ);
                  } else {
                     rectIndex = renderRects.size();
                     renderRects.add(x1);
                     renderRects.add(rowZ);
                     renderRects.add(x2);
                     renderRects.add(rowZ);
                  }

                  curRuns.add(x1);
                  curRuns.add(x2);
                  curRuns.add(rectIndex);
               }

               IntArrayList swap = prevRuns;
               prevRuns = curRuns;
               curRuns = swap;
               prevZ = rowZ;
            }
         }
      }
   }

   private static int distance(long key, int playerChunkX, int playerChunkZ) {
      return Math.max(Math.abs(ScoreStore.chunkX(key) - playerChunkX), Math.abs(ScoreStore.chunkZ(key) - playerChunkZ));
   }
}

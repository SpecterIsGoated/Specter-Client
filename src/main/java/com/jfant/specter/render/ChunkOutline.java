package com.jfant.specter.render;

import com.jfant.specter.ScoreStore;
import com.jfant.specter.core.renderer.Renderer3D;
import com.jfant.specter.core.renderer.ShapeMode;
import com.jfant.specter.core.utils.render.color.Color;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongArrays;
import it.unimi.dsi.fastutil.longs.Long2IntMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectIterator;

public final class ChunkOutline {
   private final Long2IntOpenHashMap drawn = new Long2IntOpenHashMap();
   private final LongArrayList rows = new LongArrayList();
   private final LongArrayList columns = new LongArrayList();

   public void clear() {
      this.drawn.clear();
   }

   public void add(LongArrayList cells, int tier) {
      for (int i = 0; i < cells.size(); i++) {
         this.drawn.put(cells.getLong(i), tier);
      }
   }

   public void rebuild(LongArrayList cells, IntArrayList into) {
      this.clear();
      this.add(cells, 0);
      this.edges(0, into);
   }

   public void edges(int tier, IntArrayList into) {
      into.clear();
      this.rows.clear();
      this.columns.clear();
      ObjectIterator var3 = this.drawn.long2IntEntrySet().iterator();

      while (var3.hasNext()) {
         Entry entry = (Entry)var3.next();
         if (entry.getIntValue() == tier) {
            long key = entry.getLongKey();
            int x = ScoreStore.chunkX(key);
            int z = ScoreStore.chunkZ(key);
            if (!this.drawn.containsKey(ScoreStore.key(x, z - 1))) {
               this.rows.add(edge(z, x));
            }

            if (!this.drawn.containsKey(ScoreStore.key(x, z + 1))) {
               this.rows.add(edge(z + 1, x));
            }

            if (!this.drawn.containsKey(ScoreStore.key(x - 1, z))) {
               this.columns.add(edge(x, z));
            }

            if (!this.drawn.containsKey(ScoreStore.key(x + 1, z))) {
               this.columns.add(edge(x + 1, z));
            }
         }
      }

      join(this.rows, false, into);
      join(this.columns, true, into);
   }

   public static void draw(Renderer3D renderer, IntArrayList rects, IntArrayList edges, double y, Color side, Color line, ShapeMode mode) {
      if (mode.sides()) {
         for (int i = 0; i < rects.size(); i += 4) {
            renderer.quadHorizontal(rects.getInt(i) << 4, y, rects.getInt(i + 1) << 4, rects.getInt(i + 2) + 1 << 4, rects.getInt(i + 3) + 1 << 4, side);
         }
      }

      if (mode.lines()) {
         for (int i = 0; i < edges.size(); i += 4) {
            renderer.line(edges.getInt(i) << 4, y, edges.getInt(i + 1) << 4, edges.getInt(i + 2) << 4, y, edges.getInt(i + 3) << 4, line);
         }
      }
   }

   public static void square(Renderer3D renderer, int chunkX, int chunkZ, double y, Color line) {
      double x1 = chunkX << 4;
      double z1 = chunkZ << 4;
      double x2 = x1 + 16.0;
      double z2 = z1 + 16.0;
      renderer.line(x1, y, z1, x2, y, z1, line);
      renderer.line(x2, y, z1, x2, y, z2, line);
      renderer.line(x2, y, z2, x1, y, z2, line);
      renderer.line(x1, y, z2, x1, y, z1, line);
   }

   private static long edge(int line, int along) {
      return (long)line << 32 | along + 2147483648L;
   }

   private static void join(LongArrayList edges, boolean column, IntArrayList into) {
      if (!edges.isEmpty()) {
         LongArrays.quickSort(edges.elements(), 0, edges.size());
         int i = 0;

         while (i < edges.size()) {
            long first = edges.getLong(i);
            int j = i + 1;

            while (j < edges.size() && edges.getLong(j) == first + (j - i)) {
               j++;
            }

            int line = (int)(first >> 32);
            int from = (int)((first & 4294967295L) - 2147483648L);
            int to = from + (j - i);
            if (column) {
               into.addElements(into.size(), new int[]{line, from, line, to});
            } else {
               into.addElements(into.size(), new int[]{from, line, to, line});
            }

            i = j;
         }
      }
   }
}

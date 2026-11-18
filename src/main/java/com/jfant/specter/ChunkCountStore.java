package com.jfant.specter;

import it.unimi.dsi.fastutil.longs.Long2IntMaps;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.Long2IntMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicBoolean;

public class ChunkCountStore {
   private final Long2IntOpenHashMap chunks = new Long2IntOpenHashMap();
   private final Long2IntOpenHashMap stamps = new Long2IntOpenHashMap();
   private final Object lock = new Object();
   private final AtomicBoolean dirty = new AtomicBoolean(true);
   private volatile int positiveChunkCount;
   private final String tag;
   private final String column;
   private final Long2IntOpenHashMap known = new Long2IntOpenHashMap();
   private final LongArrayList selected = new LongArrayList();

   public ChunkCountStore(String tag, String column) {
      this.tag = tag;
      this.column = column;
      this.known.defaultReturnValue(-1);
   }

   public void put(long key, int count) {
      synchronized (this.lock) {
         this.chunks.put(key, count);
         this.stamps.put(key, Memory.join());
      }

      this.dirty.set(true);
   }

   public void putIfAbsent(long key, int count) {
      this.putIfAbsent(key, count, Memory.join());
   }

   private void putIfAbsent(long key, int count, int stamp) {
      synchronized (this.lock) {
         if (this.chunks.containsKey(key)) {
            return;
         }

         this.chunks.put(key, count);
         this.stamps.put(key, stamp);
      }

      this.dirty.set(true);
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

   public int positiveChunkCount() {
      return this.positiveChunkCount;
   }

   public void markDirty() {
      this.dirty.set(true);
   }

   public boolean consumeDirty() {
      return this.dirty.getAndSet(false);
   }

   public long[] computeClearSnapshot(int radius) {
      this.copyChunks();
      this.selected.clear();
      int positive = 0;
      int r = Math.max(0, radius);
      ObjectIterator var4 = Long2IntMaps.fastIterable(this.known).iterator();

      while (var4.hasNext()) {
         Entry entry = (Entry)var4.next();
         if (entry.getIntValue() > 0) {
            positive++;
         } else {
            long key = entry.getLongKey();
            int chunkX = ScoreStore.chunkX(key);
            int chunkZ = ScoreStore.chunkZ(key);
            if (this.neighbourhoodClear(chunkX, chunkZ, r)) {
               this.selected.add(key);
            }
         }
      }

      this.positiveChunkCount = positive;
      return this.selected.toLongArray();
   }

   public long[] computeThresholdSnapshot(int threshold) {
      this.copyChunks();
      this.selected.clear();
      int positive = 0;
      ObjectIterator var3 = Long2IntMaps.fastIterable(this.known).iterator();

      while (var3.hasNext()) {
         Entry entry = (Entry)var3.next();
         int count = entry.getIntValue();
         if (count > 0) {
            positive++;
         }

         if (count >= threshold) {
            this.selected.add(entry.getLongKey());
         }
      }

      this.positiveChunkCount = positive;
      return this.selected.toLongArray();
   }

   private void copyChunks() {
      synchronized (this.lock) {
         this.known.clear();
         this.known.putAll(this.chunks);
      }
   }

   private boolean neighbourhoodClear(int chunkX, int chunkZ, int r) {
      for (int dx = -r; dx <= r; dx++) {
         for (int dz = -r; dz <= r; dz++) {
            if (this.known.get(ScoreStore.key(chunkX + dx, chunkZ + dz)) != 0) {
               return false;
            }
         }
      }

      return true;
   }

   public void save(File file) throws IOException {
      Long2IntOpenHashMap copy;
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
               writer.write("# specter " + this.tag + " v1");
               writer.newLine();
               writer.write("# last-join " + last);
               writer.newLine();
               writer.write("x,z," + this.column + ",join");
               writer.newLine();
               ObjectIterator var9 = Long2IntMaps.fastIterable(copy).iterator();

               while (var9.hasNext()) {
                  Entry entry = (Entry)var9.next();
                  long key = entry.getLongKey();
                  writer.write(ScoreStore.chunkX(key) + "," + ScoreStore.chunkZ(key) + "," + entry.getIntValue() + "," + stampCopy.getOrDefault(key, now));
                  writer.newLine();
               }
            }

            try {
               Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException var14) {
               Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
         }
      }
   }

   public int load(File file) throws IOException {
      if (!file.exists()) {
         return 0;
      } else {
         int loaded = 0;

         String line;
         try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            while ((line = reader.readLine()) != null) {
               line = line.trim();
               if (!line.isEmpty() && !line.startsWith("#") && !line.startsWith("x,")) {
                  String[] parts = line.split(",");
                  if (parts.length >= 3) {
                     try {
                        int stamp = parts.length > 3 ? Memory.stamp(parts[3]) : Memory.start();
                        if (!Memory.expired(stamp)) {
                           long key = ScoreStore.key(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
                           this.putIfAbsent(key, Integer.parseInt(parts[2].trim()), stamp);
                           loaded++;
                        }
                     } catch (NumberFormatException var10) {
                     }
                  }
               }
            }
         }

         return loaded;
      }
   }
}

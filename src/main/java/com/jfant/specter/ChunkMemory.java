package com.jfant.specter;

import com.jfant.specter.core.SpecterCore;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntMaps;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.world.level.ChunkPos;

public final class ChunkMemory {
   private static final long SAVE_DELAY_NANOS = 10000000000L;
   private final Map<String, ChunkMemory.Bucket> buckets = new HashMap<>();
   private final File folder;
   private final String extension;
   private final ExecutorService writer;
   private ChunkMemory.Bucket active;
   private boolean dirty;
   private long dirtySince;

   public ChunkMemory(String folder, String extension) {
      this.folder = new File(new File(SpecterCore.FOLDER, "specter"), folder);
      this.extension = extension;
      Memory.folder(this.folder);
      this.writer = Executors.newSingleThreadExecutor(task -> {
         Thread thread = new Thread(task, "Specter " + folder + " saves");
         thread.setDaemon(true);
         return thread;
      });
   }

   public void select(String scope) {
      if (scope == null) {
         this.active = null;
      } else {
         this.active = this.buckets.computeIfAbsent(scope, s -> load(new ChunkMemory.Bucket(new File(this.folder, s + this.extension))));
         this.prune(this.active);
      }
   }

   public boolean contains(long chunk) {
      return this.active != null && this.active.values.containsKey(chunk);
   }

   public int get(long chunk, int missing) {
      return this.active != null && this.active.values.containsKey(chunk) ? this.active.values.get(chunk) : missing;
   }

   public void put(long chunk, int value) {
      if (this.active != null) {
         int now = Memory.join();
         boolean known = this.active.values.containsKey(chunk);
         if (!known || this.active.values.get(chunk) != value || this.active.stamps.get(chunk) != now) {
            this.active.values.put(chunk, value);
            this.active.stamps.put(chunk, now);
            this.changed(this.active);
         }
      }
   }

   public void remove(long chunk) {
      if (this.active != null && this.active.values.containsKey(chunk)) {
         this.active.values.remove(chunk);
         this.active.stamps.remove(chunk);
         this.changed(this.active);
      }
   }

   public Long2IntMap all() {
      return (Long2IntMap)(this.active == null ? Long2IntMaps.EMPTY_MAP : Long2IntMaps.unmodifiable(this.active.values));
   }

   public int size() {
      return this.active == null ? 0 : this.active.values.size();
   }

   public void tickAutosave() {
      if (this.dirty && System.nanoTime() - this.dirtySince >= 10000000000L) {
         this.flush();
      }
   }

   public void flush() {
      if (this.dirty) {
         this.dirty = false;

         for (ChunkMemory.Bucket bucket : this.buckets.values()) {
            if (bucket.dirty) {
               bucket.dirty = false;
               long[] chunks = bucket.values.keySet().toLongArray();
               int[] values = new int[chunks.length];
               int[] stamps = new int[chunks.length];

               for (int i = 0; i < chunks.length; i++) {
                  values[i] = bucket.values.get(chunks[i]);
                  stamps[i] = bucket.stamps.get(chunks[i]);
               }

               this.writer.execute(() -> write(bucket.file, chunks, values, stamps));
            }
         }
      }
   }

   private void changed(ChunkMemory.Bucket bucket) {
      bucket.dirty = true;
      if (!this.dirty) {
         this.dirty = true;
         this.dirtySince = System.nanoTime();
      }
   }

   private void prune(ChunkMemory.Bucket bucket) {
      int now = Memory.join();
      if (bucket.prunedIn != now) {
         bucket.prunedIn = now;
         int before = bucket.values.size();
         bucket.stamps.long2IntEntrySet().removeIf(e -> {
            if (!Memory.expired(e.getIntValue())) {
               return false;
            } else {
               bucket.values.remove(e.getLongKey());
               return true;
            }
         });
         if (bucket.values.size() != before) {
            this.changed(bucket);
         }
      }
   }

   private static ChunkMemory.Bucket load(ChunkMemory.Bucket bucket) {
      if (!bucket.file.isFile()) {
         return bucket;
      } else {
         String line;
         try (BufferedReader reader = Files.newBufferedReader(bucket.file.toPath(), StandardCharsets.UTF_8)) {
            while ((line = reader.readLine()) != null) {
               if (!line.isEmpty() && !line.startsWith("#") && !line.startsWith("x,")) {
                  String[] parts = line.split(",");
                  if (parts.length >= 2) {
                     try {
                        long chunk = ChunkPos.pack(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
                        int value = parts.length > 2 ? Integer.parseInt(parts[2].trim()) : 0;
                        int stamp = parts.length > 3 ? Memory.stamp(parts[3]) : Memory.start();
                        if (!Memory.expired(stamp)) {
                           bucket.values.put(chunk, value);
                           bucket.stamps.put(chunk, stamp);
                        }
                     } catch (NumberFormatException var9) {
                     }
                  }
               }
            }
         } catch (IOException var11) {
            SpecterClientAddon.LOG.warn("Specter: could not read {}: {}", bucket.file, var11.toString());
         }

         bucket.prunedIn = Memory.join();
         return bucket;
      }
   }

   private static void write(File file, long[] chunks, int[] values, int[] stamps) {
      if (chunks.length == 0) {
         if (file.isFile() && !file.delete()) {
            SpecterClientAddon.LOG.warn("Specter: could not delete {}", file);
         }
      } else {
         file.getParentFile().mkdirs();
         File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
         int last = -2147483648;

         for (int stamp : stamps) {
            last = Math.max(last, stamp);
         }

         try {
            try (BufferedWriter out = Files.newBufferedWriter(tmp.toPath(), StandardCharsets.UTF_8)) {
               out.write("# last-join " + last);
               out.newLine();
               out.write("x,z,value,join");
               out.newLine();

               for (int i = 0; i < chunks.length; i++) {
                  out.write(ChunkPos.getX(chunks[i]) + "," + ChunkPos.getZ(chunks[i]) + "," + values[i] + "," + stamps[i]);
                  out.newLine();
               }
            }

            try {
               Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException var11) {
               Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
         } catch (IOException var13) {
            SpecterClientAddon.LOG.warn("Specter: could not save {}: {}", file, var13.toString());
         }
      }
   }

   private static final class Bucket {
      final File file;
      final Long2IntOpenHashMap values = new Long2IntOpenHashMap();
      final Long2IntOpenHashMap stamps = new Long2IntOpenHashMap();
      int prunedIn = -2147483648;
      boolean dirty;

      Bucket(File file) {
         this.file = file;
      }
   }
}

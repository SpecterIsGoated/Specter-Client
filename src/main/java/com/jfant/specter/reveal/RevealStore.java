package com.jfant.specter.reveal;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.LongPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

public class RevealStore {
   private final Long2ObjectOpenHashMap<Block> blocks = new Long2ObjectOpenHashMap();
   private final EnumMap<RevealStore.Marker, LongOpenHashSet> markers = new EnumMap<>(RevealStore.Marker.class);
   private final Object lock;
   private volatile boolean dirty;

   public RevealStore() {
      for (RevealStore.Marker marker : RevealStore.Marker.values()) {
         this.markers.put(marker, new LongOpenHashSet());
      }

      this.lock = new Object();
      this.dirty = true;
   }

   public boolean put(long pos, Block block) {
      synchronized (this.lock) {
         Block previous = (Block)this.blocks.put(pos, block);
         if (previous != block) {
            this.dirty = true;
            return previous == null;
         } else {
            return false;
         }
      }
   }

   public boolean remove(long pos) {
      synchronized (this.lock) {
         if (this.blocks.remove(pos) != null) {
            this.dirty = true;
            return true;
         } else {
            return false;
         }
      }
   }

   public boolean contains(long pos) {
      synchronized (this.lock) {
         return this.blocks.containsKey(pos);
      }
   }

   public boolean putMarker(RevealStore.Marker marker, long pos) {
      synchronized (this.lock) {
         if (this.markers.get(marker).add(pos)) {
            this.dirty = true;
            return true;
         } else {
            return false;
         }
      }
   }

   public int markerCount(RevealStore.Marker marker) {
      synchronized (this.lock) {
         return this.markers.get(marker).size();
      }
   }

   public long[] markerSnapshot(RevealStore.Marker marker) {
      synchronized (this.lock) {
         return this.markers.get(marker).toLongArray();
      }
   }

   public int removeIf(LongPredicate test) {
      synchronized (this.lock) {
         int before = this.total();
         this.blocks.keySet().removeIf(test);

         for (LongOpenHashSet set : this.markers.values()) {
            set.removeIf(test);
         }

         int removed = before - this.total();
         if (removed > 0) {
            this.dirty = true;
         }

         return removed;
      }
   }

   public int retainBlocks(Set<Block> keep) {
      synchronized (this.lock) {
         int before = this.blocks.size();
         this.blocks.values().removeIf(block -> !keep.contains(block));
         int removed = before - this.blocks.size();
         if (removed > 0) {
            this.dirty = true;
         }

         return removed;
      }
   }

   public void clear() {
      synchronized (this.lock) {
         this.blocks.clear();

         for (LongOpenHashSet set : this.markers.values()) {
            set.clear();
         }

         this.dirty = true;
      }
   }

   public int size() {
      synchronized (this.lock) {
         return this.blocks.size();
      }
   }

   public int total() {
      synchronized (this.lock) {
         int total = this.blocks.size();

         for (LongOpenHashSet set : this.markers.values()) {
            total += set.size();
         }

         return total;
      }
   }

   public boolean isDirty() {
      return this.dirty;
   }

   public void clearDirty() {
      this.dirty = false;
   }

   public long[] snapshot() {
      synchronized (this.lock) {
         return this.blocks.keySet().toLongArray();
      }
   }

   public RevealStore.Snapshot blockSnapshot() {
      synchronized (this.lock) {
         long[] positions = new long[this.blocks.size()];
         Block[] kinds = new Block[this.blocks.size()];
         int i = 0;
         ObjectIterator var5 = this.blocks.long2ObjectEntrySet().iterator();

         while (var5.hasNext()) {
            Entry<Block> entry = (Entry<Block>)var5.next();
            positions[i] = entry.getLongKey();
            kinds[i++] = (Block)entry.getValue();
         }

         return new RevealStore.Snapshot(positions, kinds);
      }
   }

   public Map<Block, Integer> breakdown() {
      Map<Block, Integer> counts = new LinkedHashMap<>();
      synchronized (this.lock) {
         ObjectIterator var3 = this.blocks.long2ObjectEntrySet().iterator();

         while (var3.hasNext()) {
            Entry<Block> entry = (Entry<Block>)var3.next();
            counts.merge((Block)entry.getValue(), 1, Integer::sum);
         }

         return counts;
      }
   }

   public void save(File file) throws IOException {
      Long2ObjectOpenHashMap<Block> copy;
      synchronized (this.lock) {
         copy = this.blocks.clone();
      }

      EnumMap<RevealStore.Marker, long[]> markerCopy = new EnumMap<>(RevealStore.Marker.class);
      boolean anyMarker = false;

      for (RevealStore.Marker marker : RevealStore.Marker.values()) {
         long[] positions = this.markerSnapshot(marker);
         markerCopy.put(marker, positions);
         anyMarker |= positions.length > 0;
      }

      if (!copy.isEmpty() || anyMarker) {
         File parent = file.getParentFile();
         if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create directory " + parent);
         } else {
            File tmp = new File(file.getParentFile(), file.getName() + ".tmp");

            try (BufferedWriter writer = Files.newBufferedWriter(tmp.toPath(), StandardCharsets.UTF_8)) {
               writer.write("# specter reveals v1 - x,y,z,block");
               writer.newLine();
               ObjectIterator var26 = copy.long2ObjectEntrySet().iterator();

               while (var26.hasNext()) {
                  Entry<Block> entry = (Entry<Block>)var26.next();
                  BlockPos pos = BlockPos.of(entry.getLongKey());
                  Identifier id = BuiltInRegistries.BLOCK.getKey((Block)entry.getValue());
                  writer.write(pos.getX() + "," + pos.getY() + "," + pos.getZ() + "," + id);
                  writer.newLine();
               }

               for (RevealStore.Marker marker : RevealStore.Marker.values()) {
                  for (long key : markerCopy.get(marker)) {
                     BlockPos pos = BlockPos.of(key);
                     writer.write(pos.getX() + "," + pos.getY() + "," + pos.getZ() + "," + marker.csvId);
                     writer.newLine();
                  }
               }
            }

            try {
               Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException var19) {
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
               if (!line.isEmpty() && !line.startsWith("#")) {
                  String[] parts = line.split(",");
                  if (parts.length == 4) {
                     try {
                        long pos = BlockPos.asLong(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()), Integer.parseInt(parts[2].trim()));
                        String rawId = parts[3].trim();
                        RevealStore.Marker marker = markerFor(rawId);
                        if (marker != null) {
                           if (this.putMarker(marker, pos)) {
                              loaded++;
                           }
                        } else {
                           Block block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(rawId));
                           if (block != null && !block.defaultBlockState().isAir() && this.put(pos, block)) {
                              loaded++;
                           }
                        }
                     } catch (IllegalArgumentException var12) {
                     }
                  }
               }
            }
         }

         return loaded;
      }
   }

   private static RevealStore.Marker markerFor(String csvId) {
      for (RevealStore.Marker marker : RevealStore.Marker.values()) {
         if (marker.csvId.equals(csvId)) {
            return marker;
         }
      }

      return null;
   }

   public static enum Marker {
      ITEM_FRAME("specter:item_frame"),
      ARMOR_STAND("specter:armor_stand");

      private final String csvId;

      private Marker(String csvId) {
         this.csvId = csvId;
      }
   }

   public record Snapshot(long[] positions, Block[] blocks) {
   }
}

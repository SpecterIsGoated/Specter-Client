package com.jfant.specter.sound;

import com.jfant.specter.Memory;
import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.SpecterCore;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.core.BlockPos;

public final class SoundSpots {
   private static final long SAVE_DELAY_NANOS = 10000000000L;
   private static final long COUNT_GAP_MS = 5000L;
   private static final int MAX_SPOTS = 4000;
   private final Map<String, SoundSpots.Bucket> buckets = new HashMap<>();
   private final File folder = new File(new File(SpecterCore.FOLDER, "specter"), "sound-detector");
   private final ExecutorService writer = Executors.newSingleThreadExecutor(task -> {
      Thread thread = new Thread(task, "Specter sound-detector saves");
      thread.setDaemon(true);
      return thread;
   });
   private SoundSpots.Bucket active;
   private boolean dirty;
   private long dirtySince;

   public SoundSpots() {
      Memory.folder(this.folder);
   }

   public void select(String scope) {
      if (scope == null) {
         this.active = null;
      } else {
         this.active = this.buckets.computeIfAbsent(scope, s -> load(new SoundSpots.Bucket(new File(this.folder, s + ".csv"))));
         this.prune(this.active);
      }
   }

   public boolean heard(BlockPos pos, SoundSpots.Kind kind, long now) {
      if (this.active == null) {
         return false;
      } else {
         int join = Memory.join();
         long key = pos.asLong();
         SoundSpots.Spot spot = (SoundSpots.Spot)this.active.spots.get(key);
         if (spot == null) {
            if (this.active.spots.size() >= 4000) {
               this.dropLongestUnheard(this.active);
            }

            this.active.spots.put(key, new SoundSpots.Spot(pos.getX(), pos.getY(), pos.getZ(), kind, 1, now, join));
            this.changed(this.active);
            return true;
         } else {
            boolean counts = now - spot.last >= 5000L;
            if (counts) {
               spot.count++;
               spot.last = now;
            }

            if (counts || spot.kind != kind || spot.stamp != join) {
               spot.kind = kind;
               spot.stamp = join;
               this.changed(this.active);
            }

            return false;
         }
      }
   }

   public SoundSpots.Kind kindAt(BlockPos pos) {
      if (this.active == null) {
         return null;
      } else {
         SoundSpots.Spot spot = (SoundSpots.Spot)this.active.spots.get(pos.asLong());
         return spot == null ? null : spot.kind;
      }
   }

   public Collection<SoundSpots.Spot> all() {
      return (Collection<SoundSpots.Spot>)(this.active == null ? List.of() : this.active.spots.values());
   }

   public int size() {
      return this.active == null ? 0 : this.active.spots.size();
   }

   public void tickAutosave() {
      if (this.dirty && System.nanoTime() - this.dirtySince >= 10000000000L) {
         this.flush();
      }
   }

   public void flush() {
      if (this.dirty) {
         this.dirty = false;

         for (SoundSpots.Bucket bucket : this.buckets.values()) {
            if (bucket.dirty) {
               bucket.dirty = false;
               String text = rows(bucket);
               File file = bucket.file;
               this.writer.execute(() -> write(file, text));
            }
         }
      }
   }

   private void changed(SoundSpots.Bucket bucket) {
      bucket.dirty = true;
      if (!this.dirty) {
         this.dirty = true;
         this.dirtySince = System.nanoTime();
      }
   }

   private void dropLongestUnheard(SoundSpots.Bucket bucket) {
      long oldestKey = 0L;
      long oldestAt = 9223372036854775807L;
      ObjectIterator var6 = bucket.spots.long2ObjectEntrySet().iterator();

      while (var6.hasNext()) {
         Entry<SoundSpots.Spot> entry = (Entry<SoundSpots.Spot>)var6.next();
         if (((SoundSpots.Spot)entry.getValue()).last < oldestAt) {
            oldestAt = ((SoundSpots.Spot)entry.getValue()).last;
            oldestKey = entry.getLongKey();
         }
      }

      bucket.spots.remove(oldestKey);
   }

   private void prune(SoundSpots.Bucket bucket) {
      int now = Memory.join();
      if (bucket.prunedIn != now) {
         bucket.prunedIn = now;
         if (bucket.spots.values().removeIf(spot -> Memory.expired(spot.stamp))) {
            this.changed(bucket);
         }
      }
   }

   private static SoundSpots.Bucket load(SoundSpots.Bucket bucket) {
      if (!bucket.file.isFile()) {
         return bucket;
      } else {
         String line;
         try (BufferedReader reader = Files.newBufferedReader(bucket.file.toPath(), StandardCharsets.UTF_8)) {
            while ((line = reader.readLine()) != null) {
               if (!line.isEmpty() && !line.startsWith("#") && !line.startsWith("x,")) {
                  String[] parts = line.split(",");
                  if (parts.length >= 7) {
                     try {
                        int x = Integer.parseInt(parts[0].trim());
                        int y = Integer.parseInt(parts[1].trim());
                        int z = Integer.parseInt(parts[2].trim());
                        SoundSpots.Kind kind = SoundSpots.Kind.valueOf(parts[3].trim());
                        int count = Integer.parseInt(parts[4].trim());
                        long last = Long.parseLong(parts[5].trim());
                        int stamp = Memory.stamp(parts[6]);
                        if (!Memory.expired(stamp)) {
                           bucket.spots.put(BlockPos.asLong(x, y, z), new SoundSpots.Spot(x, y, z, kind, count, last, stamp));
                        }
                     } catch (IllegalArgumentException var13) {
                     }
                  }
               }
            }
         } catch (IOException var15) {
            SpecterClientAddon.LOG.warn("Specter: could not read {}: {}", bucket.file, var15.toString());
         }

         bucket.prunedIn = Memory.join();
         return bucket;
      }
   }

   private static String rows(SoundSpots.Bucket bucket) {
      if (bucket.spots.isEmpty()) {
         return null;
      } else {
         int newest = -2147483648;
         StringBuilder body = new StringBuilder(bucket.spots.size() * 40);
         ObjectIterator var3 = bucket.spots.values().iterator();

         while (var3.hasNext()) {
            SoundSpots.Spot s = (SoundSpots.Spot)var3.next();
            newest = Math.max(newest, s.stamp);
            body.append(s.x)
               .append(',')
               .append(s.y)
               .append(',')
               .append(s.z)
               .append(',')
               .append(s.kind.name())
               .append(',')
               .append(s.count)
               .append(',')
               .append(s.last)
               .append(',')
               .append(s.stamp)
               .append('\n');
         }

         return "# last-join " + newest + "\nx,y,z,kind,count,last,join\n" + body;
      }
   }

   private static void write(File file, String text) {
      if (text == null) {
         if (file.isFile() && !file.delete()) {
            SpecterClientAddon.LOG.warn("Specter: could not delete {}", file);
         }
      } else {
         file.getParentFile().mkdirs();
         File tmp = new File(file.getParentFile(), file.getName() + ".tmp");

         try {
            Files.writeString(tmp.toPath(), text, StandardCharsets.UTF_8);

            try {
               Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException var4) {
               Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
         } catch (IOException var5) {
            SpecterClientAddon.LOG.warn("Specter: could not save {}: {}", file, var5.toString());
         }
      }
   }

   private static final class Bucket {
      final File file;
      final Long2ObjectOpenHashMap<SoundSpots.Spot> spots = new Long2ObjectOpenHashMap();
      int prunedIn = -2147483648;
      boolean dirty;

      Bucket(File file) {
         this.file = file;
      }
   }

   public static enum Group {
      CONTAINERS,
      WORK_BLOCKS,
      REDSTONE,
      DOORS,
      MUSIC,
      EXPLOSIONS;
   }

   public static enum Kind {
      CHEST("Chest", SoundSpots.Group.CONTAINERS),
      BARREL("Barrel", SoundSpots.Group.CONTAINERS),
      SHULKER_BOX("Shulker Box", SoundSpots.Group.CONTAINERS),
      ENDER_CHEST("Ender Chest", SoundSpots.Group.CONTAINERS),
      ANVIL("Anvil", SoundSpots.Group.WORK_BLOCKS),
      ENCHANTING_TABLE("Enchanting Table", SoundSpots.Group.WORK_BLOCKS),
      BREWING_STAND("Brewing Stand", SoundSpots.Group.WORK_BLOCKS),
      SMITHING_TABLE("Smithing Table", SoundSpots.Group.WORK_BLOCKS),
      GRINDSTONE("Grindstone", SoundSpots.Group.WORK_BLOCKS),
      STONECUTTER("Stonecutter", SoundSpots.Group.WORK_BLOCKS),
      LOOM("Loom", SoundSpots.Group.WORK_BLOCKS),
      CARTOGRAPHY_TABLE("Cartography Table", SoundSpots.Group.WORK_BLOCKS),
      RESPAWN_ANCHOR("Respawn Anchor", SoundSpots.Group.WORK_BLOCKS),
      BEACON("Beacon", SoundSpots.Group.WORK_BLOCKS),
      END_PORTAL_FRAME("End Portal Frame", SoundSpots.Group.WORK_BLOCKS),
      PISTON("Piston", SoundSpots.Group.REDSTONE),
      DISPENSER("Dispenser", SoundSpots.Group.REDSTONE),
      LEVER("Lever", SoundSpots.Group.REDSTONE),
      BUTTON("Button", SoundSpots.Group.REDSTONE),
      DOOR("Door", SoundSpots.Group.DOORS),
      TRAPDOOR("Trapdoor", SoundSpots.Group.DOORS),
      GATE("Gate", SoundSpots.Group.DOORS),
      NOTE_BLOCK("Note Block", SoundSpots.Group.MUSIC),
      JUKEBOX("Jukebox", SoundSpots.Group.MUSIC),
      BELL("Bell", SoundSpots.Group.MUSIC),
      GOAT_HORN("Goat Horn", SoundSpots.Group.MUSIC),
      EXPLOSION("Explosion", SoundSpots.Group.EXPLOSIONS);

      public final String title;
      public final SoundSpots.Group group;

      private Kind(String title, SoundSpots.Group group) {
         this.title = title;
         this.group = group;
      }
   }

   public static final class Spot {
      public final int x;
      public final int y;
      public final int z;
      public SoundSpots.Kind kind;
      public int count;
      public long last;
      int stamp;

      Spot(int x, int y, int z, SoundSpots.Kind kind, int count, long last, int stamp) {
         this.x = x;
         this.y = y;
         this.z = z;
         this.kind = kind;
         this.count = count;
         this.last = last;
         this.stamp = stamp;
      }
   }
}

package com.jfant.specter.sus;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.world.level.ChunkPos;

/**
 * The list of suspicious areas for one server + dimension.
 *
 * <p>Detectors hand over the chunks they currently flag ({@link #ingest}). Flagged chunks that touch (or nearly touch)
 * are grouped into one area first; an area then joins any stored find whose box lies within the merge radius, so the
 * same spot flagged again - by the same detector on a later poll, by the other detector, or after a rejoin - updates
 * the existing entry instead of adding a duplicate. Finds that grow into each other are merged too. Pure Java (no game
 * state), so it is unit tested.
 */
public final class SusFindStore {
   /** Flagged chunks this close (Chebyshev, in chunks) belong to the same area before merging with stored finds. */
   public static final int LINK_GAP = 2;
   public static final int MAX_FINDS = 500;
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

   private final List<SusFind> finds = new ArrayList<>();
   private int nextId = 1;
   private boolean dirty;
   private int version;

   public synchronized List<SusFind> snapshot() {
      return new ArrayList<>(this.finds);
   }

   public synchronized int size() {
      return this.finds.size();
   }

   /** Bumped on every change, so views can cache. */
   public synchronized int version() {
      return this.version;
   }

   public synchronized boolean dirty() {
      return this.dirty;
   }

   public synchronized SusFind byId(int id) {
      for (SusFind f : this.finds) {
         if (f.id == id) return f;
      }
      return null;
   }

   public synchronized boolean remove(int id) {
      boolean removed = this.finds.removeIf(f -> f.id == id);
      if (removed) this.changed();
      return removed;
   }

   public synchronized void clear() {
      if (!this.finds.isEmpty()) {
         this.finds.clear();
         this.changed();
      }
   }

   private void changed() {
      this.dirty = true;
      this.version++;
   }

   /** What one ingest did, for chat messages. */
   public record Result(List<SusFind> created, List<SusFind> confirmed) {
      public boolean any() {
         return !this.created.isEmpty() || !this.confirmed.isEmpty();
      }
   }

   /**
    * Takes the chunks one detector flags right now.
    *
    * @param source      {@link SusFind#SUS_CHUNKS} or {@link SusFind#SUS_CHUNKS_V2}
    * @param chunks      flagged chunks, {@link ChunkPos#pack} keys
    * @param strength    evidence strength of this batch (0-2)
    * @param mergeRadius chunks between an area and a stored find that still count as the same spot
    * @param join        current join number (a find flagged again on a later join counts as confirmed)
    */
   public synchronized Result ingest(int source, long[] chunks, int strength, int mergeRadius, long now, int join) {
      List<SusFind> created = new ArrayList<>();
      List<SusFind> confirmed = new ArrayList<>();
      if (chunks.length == 0) return new Result(created, confirmed);

      for (LongArrayList area : groups(chunks)) {
         SusFind probe = new SusFind(-1);
         for (int i = 0; i < area.size(); i++) probe.chunks.add(area.getLong(i));
         probe.recompute();

         SusFind target = null;
         for (int i = 0; i < this.finds.size(); i++) {
            SusFind f = this.finds.get(i);
            if (f.gapTo(probe) <= mergeRadius) {
               if (target == null) {
                  target = f;
               } else {
                  absorb(target, f);
                  this.finds.remove(i--);
               }
            }
         }

         SusFind.Confidence before = target == null ? null : target.confidence();
         if (target == null) {
            target = new SusFind(this.nextId++);
            target.firstSeen = now;
            target.firstJoin = join;
            target.lastJoin = join;
            target.joins = 1;
            this.finds.add(target);
            created.add(target);
         } else if (join != target.lastJoin) {
            target.lastJoin = join;
            target.joins++;
         }

         boolean grew = false;
         for (long key : probe.chunks) {
            if (target.chunks.size() >= SusFind.MAX_CHUNKS) break;
            grew |= target.chunks.add(key);
         }

         int oldSources = target.sources;
         int oldStrength = target.strength;
         target.sources |= source;
         target.strength = Math.max(target.strength, strength);
         target.lastSeen = now;
         if (grew) target.recompute();
         if (grew || oldSources != target.sources || oldStrength != target.strength || created.contains(target)) this.changed();
         else this.dirty = true;
         if (before != null && before != SusFind.Confidence.CONFIRMED && target.confidence() == SusFind.Confidence.CONFIRMED) {
            confirmed.add(target);
         }
      }

      this.mergeTouching(mergeRadius);
      this.cap();
      return new Result(created, confirmed);
   }

   /** Merges stored finds that have grown to within the merge radius of each other. */
   private void mergeTouching(int mergeRadius) {
      boolean merged = true;
      while (merged) {
         merged = false;
         outer:
         for (int i = 0; i < this.finds.size(); i++) {
            for (int j = i + 1; j < this.finds.size(); j++) {
               SusFind a = this.finds.get(i);
               SusFind b = this.finds.get(j);
               if (a.gapTo(b) <= mergeRadius) {
                  SusFind keep = a.id <= b.id ? a : b;
                  SusFind gone = keep == a ? b : a;
                  absorb(keep, gone);
                  this.finds.remove(gone);
                  this.changed();
                  merged = true;
                  break outer;
               }
            }
         }
      }
   }

   private static void absorb(SusFind into, SusFind from) {
      for (long key : from.chunks) {
         if (into.chunks.size() >= SusFind.MAX_CHUNKS) break;
         into.chunks.add(key);
      }
      into.sources |= from.sources;
      into.strength = Math.max(into.strength, from.strength);
      into.firstSeen = Math.min(into.firstSeen, from.firstSeen);
      into.lastSeen = Math.max(into.lastSeen, from.lastSeen);
      into.firstJoin = Math.min(into.firstJoin, from.firstJoin);
      if (from.lastJoin > into.lastJoin) into.lastJoin = from.lastJoin;
      into.joins = Math.max(into.joins, from.joins);
      if (into.note.isEmpty()) into.note = from.note;
      into.recompute();
   }

   /** Keeps the list bounded: drops the oldest uncertain finds first, then the oldest of the rest. */
   private void cap() {
      if (this.finds.size() <= MAX_FINDS) return;
      List<SusFind> order = new ArrayList<>(this.finds);
      order.sort(Comparator.<SusFind>comparingInt(f -> -f.confidence().ordinal()).thenComparingLong(f -> f.lastSeen));
      for (int i = 0; this.finds.size() > MAX_FINDS && i < order.size(); i++) this.finds.remove(order.get(i));
      this.changed();
   }

   /** Forgets finds not flagged for {@code keepMs}. Confirmed finds are kept twice as long. */
   public synchronized int prune(long now, long keepMs) {
      if (keepMs <= 0L) return 0;
      int before = this.finds.size();
      this.finds.removeIf(f -> now - f.lastSeen > (f.confidence() == SusFind.Confidence.CONFIRMED ? keepMs * 2L : keepMs));
      int removed = before - this.finds.size();
      if (removed > 0) this.changed();
      return removed;
   }

   /** Splits flagged chunks into areas: chunks within {@link #LINK_GAP} of each other end up together. */
   static List<LongArrayList> groups(long[] chunks) {
      LongOpenHashSet left = new LongOpenHashSet(chunks);
      List<LongArrayList> out = new ArrayList<>();
      LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
      for (long start : chunks) {
         if (!left.remove(start)) continue;
         LongArrayList area = new LongArrayList();
         queue.enqueue(start);
         while (!queue.isEmpty()) {
            long key = queue.dequeueLong();
            area.add(key);
            int x = ChunkPos.getX(key);
            int z = ChunkPos.getZ(key);
            for (int dx = -LINK_GAP; dx <= LINK_GAP; dx++) {
               for (int dz = -LINK_GAP; dz <= LINK_GAP; dz++) {
                  long next = ChunkPos.pack(x + dx, z + dz);
                  if (left.remove(next)) queue.enqueue(next);
               }
            }
         }
         out.add(area);
      }
      return out;
   }

   // ============================================================================================== files

   public synchronized void save(Path file) throws IOException {
      JsonObject root = new JsonObject();
      root.addProperty("version", 1);
      root.addProperty("nextId", this.nextId);
      JsonArray list = new JsonArray();
      for (SusFind f : this.finds) {
         JsonObject o = new JsonObject();
         o.addProperty("id", f.id);
         o.addProperty("sources", f.sources);
         o.addProperty("strength", f.strength);
         o.addProperty("firstSeen", f.firstSeen);
         o.addProperty("lastSeen", f.lastSeen);
         o.addProperty("firstJoin", f.firstJoin);
         o.addProperty("lastJoin", f.lastJoin);
         o.addProperty("joins", f.joins);
         if (!f.note.isEmpty()) o.addProperty("note", f.note);
         StringBuilder chunks = new StringBuilder(f.chunks.size() * 10);
         for (long key : f.chunks) {
            if (!chunks.isEmpty()) chunks.append(';');
            chunks.append(ChunkPos.getX(key)).append(',').append(ChunkPos.getZ(key));
         }
         o.addProperty("chunks", chunks.toString());
         list.add(o);
      }
      root.add("finds", list);
      Files.createDirectories(file.getParent());
      Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
      Files.writeString(tmp, GSON.toJson(root), StandardCharsets.UTF_8);
      Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      this.dirty = false;
   }

   public synchronized void load(Path file) throws IOException {
      this.finds.clear();
      this.nextId = 1;
      this.dirty = false;
      this.version++;
      if (!Files.isRegularFile(file)) return;
      JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
      this.nextId = root.has("nextId") ? root.get("nextId").getAsInt() : 1;
      for (JsonElement e : root.getAsJsonArray("finds")) {
         JsonObject o = e.getAsJsonObject();
         SusFind f = new SusFind(o.get("id").getAsInt());
         f.sources = o.get("sources").getAsInt();
         f.strength = o.get("strength").getAsInt();
         f.firstSeen = o.get("firstSeen").getAsLong();
         f.lastSeen = o.get("lastSeen").getAsLong();
         f.firstJoin = o.get("firstJoin").getAsInt();
         f.lastJoin = o.get("lastJoin").getAsInt();
         f.joins = o.get("joins").getAsInt();
         f.note = o.has("note") ? o.get("note").getAsString() : "";
         for (String pair : o.get("chunks").getAsString().split(";")) {
            int comma = pair.indexOf(',');
            if (comma <= 0) continue;
            try {
               f.chunks.add(ChunkPos.pack(Integer.parseInt(pair.substring(0, comma)), Integer.parseInt(pair.substring(comma + 1))));
            } catch (NumberFormatException ignored) {
            }
         }
         if (f.chunks.isEmpty()) continue;
         f.recompute();
         this.finds.add(f);
         this.nextId = Math.max(this.nextId, f.id + 1);
      }
   }
}

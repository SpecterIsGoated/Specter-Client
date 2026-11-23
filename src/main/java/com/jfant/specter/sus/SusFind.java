package com.jfant.specter.sus;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.world.level.ChunkPos;

/**
 * One suspicious area: every flagged chunk that belongs to the same spot, however many times and by however many
 * detectors it was flagged. Chunks are stored as {@link ChunkPos#pack} keys.
 */
public final class SusFind {
   /** Bits of {@link #sources}. */
   public static final int SUS_CHUNKS = 1;
   public static final int SUS_CHUNKS_V2 = 2;
   /** Most chunks one find keeps (a find this big is already a very large area). */
   public static final int MAX_CHUNKS = 4096;

   public final int id;
   public final LongOpenHashSet chunks = new LongOpenHashSet();
   public int sources;
   /** Strongest evidence seen: Sus Chunks V2 tier 0-2, Sus Chunks flags count as tier 1. */
   public int strength;
   public long firstSeen;
   public long lastSeen;
   /** {@code Memory} join numbers the find was flagged on: the first and the latest. */
   public int firstJoin;
   public int lastJoin;
   /** Number of different joins it was flagged on. */
   public int joins;
   public String note = "";

   // derived, kept up to date by recompute()
   public int minX;
   public int minZ;
   public int maxX;
   public int maxZ;
   public double centreX;
   public double centreZ;

   public SusFind(int id) {
      this.id = id;
   }

   public void recompute() {
      int nMinX = Integer.MAX_VALUE, nMinZ = Integer.MAX_VALUE, nMaxX = Integer.MIN_VALUE, nMaxZ = Integer.MIN_VALUE;
      double sx = 0.0, sz = 0.0;
      for (long key : this.chunks) {
         int x = ChunkPos.getX(key);
         int z = ChunkPos.getZ(key);
         if (x < nMinX) nMinX = x;
         if (z < nMinZ) nMinZ = z;
         if (x > nMaxX) nMaxX = x;
         if (z > nMaxZ) nMaxZ = z;
         sx += x;
         sz += z;
      }

      int n = Math.max(1, this.chunks.size());
      this.minX = nMinX;
      this.minZ = nMinZ;
      this.maxX = nMaxX;
      this.maxZ = nMaxZ;
      this.centreX = sx / n;
      this.centreZ = sz / n;
   }

   /** World block X of the middle of the area. */
   public int blockX() {
      return (int)Math.floor(this.centreX * 16.0 + 8.0);
   }

   public int blockZ() {
      return (int)Math.floor(this.centreZ * 16.0 + 8.0);
   }

   public int size() {
      return this.chunks.size();
   }

   public boolean both() {
      return (this.sources & (SUS_CHUNKS | SUS_CHUNKS_V2)) == (SUS_CHUNKS | SUS_CHUNKS_V2);
   }

   /** Chebyshev distance in chunks from this area's box to a chunk (0 inside it). */
   public int gapTo(int chunkX, int chunkZ) {
      int dx = chunkX < this.minX ? this.minX - chunkX : (chunkX > this.maxX ? chunkX - this.maxX : 0);
      int dz = chunkZ < this.minZ ? this.minZ - chunkZ : (chunkZ > this.maxZ ? chunkZ - this.maxZ : 0);
      return Math.max(dx, dz);
   }

   /** Chebyshev distance in chunks between two areas' boxes (0 when they touch or overlap). */
   public int gapTo(SusFind other) {
      int dx = Math.max(0, Math.max(other.minX - this.maxX, this.minX - other.maxX));
      int dz = Math.max(0, Math.max(other.minZ - this.maxZ, this.minZ - other.maxZ));
      return Math.max(dx, dz);
   }

   public Confidence confidence() {
      return Confidence.of(this);
   }

   public String sourceText() {
      if (this.both()) return "Sus Chunks + V2";
      return (this.sources & SUS_CHUNKS) != 0 ? "Sus Chunks" : "Sus Chunks V2";
   }

   /**
    * How much a find can be trusted.
    *
    * <ul>
    *   <li>CONFIRMED - two independent detectors flagged it (growth that is too grown AND a hole in overdue growth), or
    *       it was flagged again on a later join (so it is not one odd chunk load).</li>
    *   <li>LIKELY - one detector, but strong evidence: an area of 3+ chunks, or V2's strongest tier.</li>
    *   <li>UNCERTAIN - one detector, small, seen on one join only. Worth a look, not worth a trip.</li>
    * </ul>
    */
   public enum Confidence {
      CONFIRMED("Confirmed", 0xFFD447),
      LIKELY("Likely", 0xFF5DA8),
      UNCERTAIN("Uncertain", 0x9A8CFF);

      public final String title;
      public final int rgb;

      Confidence(String title, int rgb) {
         this.title = title;
         this.rgb = rgb;
      }

      static Confidence of(SusFind find) {
         if (find.both() || find.joins >= 2) return CONFIRMED;
         if (find.size() >= 3 || find.strength >= 2) return LIKELY;
         return UNCERTAIN;
      }
   }
}

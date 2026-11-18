package com.jfant.specter.scan;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;

public final class ChangeBursts {
   public static final long HOLD = 1100000000L;
   private static final long BURST_WINDOW = 1000000000L;
   private static final long SWAP_WINDOW = 2000000000L;
   private static final int BURST_REACH = 2;
   private static final int BURST_OTHERS = 2;
   private static final int RECENT = 32;
   private static final long CHUNK_SETTLE = 2000000000L;
   private static final long TELEPORT_QUIET = 3000000000L;
   private static final long FORGET = 6000000000L;
   private static final long PRUNE_EVERY = 5000000000L;
   private static final long NEVER = -2305843009213693952L;
   public static boolean playerBypass;
   public static boolean blockUpdateFinder;
   private static final Long2ObjectOpenHashMap<ChangeBursts.ChunkChanges> CHUNKS = new Long2ObjectOpenHashMap();
   private static final Long2LongOpenHashMap LOADED = new Long2LongOpenHashMap();
   private static long teleported = -2305843009213693952L;
   private static long pruned;

   private ChangeBursts() {
   }

   private static boolean wanted() {
      return playerBypass || blockUpdateFinder;
   }

   public static long now() {
      return System.nanoTime();
   }

   public static void seen(BlockPos pos, BlockState state) {
      if (wanted()) {
         ClientLevel world = Minecraft.getInstance().level;
         if (world != null) {
            BlockState old = world.getBlockState(pos);
            if (old != state) {
               long now = now();
               long key = ChunkPos.pack(pos.getX() >> 4, pos.getZ() >> 4);
               ChangeBursts.ChunkChanges chunk = (ChangeBursts.ChunkChanges)CHUNKS.get(key);
               if (chunk == null) {
                  CHUNKS.put(key, chunk = new ChangeBursts.ChunkChanges());
               }

               if (AntiXray.isSwap(old, state)) {
                  chunk.lastSwap = now;
                  chunk.last = now;
               } else if (old.getBlock() != state.getBlock()) {
                  chunk.add(now, pos.asLong());
               } else {
                  chunk.last = now;
               }

               if (now - pruned > 5000000000L) {
                  prune(now);
               }
            }
         }
      }
   }

   public static void chunkLoaded(int x, int z) {
      if (wanted()) {
         LOADED.put(ChunkPos.pack(x, z), now());
      }
   }

   public static void teleported() {
      teleported = now();
   }

   public static void clear() {
      CHUNKS.clear();
      LOADED.clear();
      teleported = now();
   }

   public static boolean settled(BlockPos pos, long time) {
      if (time - teleported < 3000000000L) {
         return false;
      } else {
         long loaded = LOADED.getOrDefault(ChunkPos.pack(pos.getX() >> 4, pos.getZ() >> 4), -2305843009213693952L);
         return time - loaded >= 2000000000L;
      }
   }

   public static boolean fake(BlockPos pos, long time, boolean typeChange) {
      ChangeBursts.ChunkChanges chunk = (ChangeBursts.ChunkChanges)CHUNKS.get(ChunkPos.pack(pos.getX() >> 4, pos.getZ() >> 4));
      if (chunk == null) {
         return false;
      } else if (chunk.lastSwap >= time - 2000000000L) {
         return true;
      } else if (!typeChange) {
         return false;
      } else {
         long self = pos.asLong();
         int x = pos.getX();
         int y = pos.getY();
         int z = pos.getZ();
         int others = 0;

         for (int i = 0; i < chunk.size; i++) {
            long other = chunk.positions[i];
            if (other != self
               && Math.abs(chunk.times[i] - time) <= 1000000000L
               && Math.abs(BlockPos.getX(other) - x) <= 2
               && Math.abs(BlockPos.getY(other) - y) <= 2
               && Math.abs(BlockPos.getZ(other) - z) <= 2) {
               if (++others >= 2) {
                  return true;
               }
            }
         }

         return false;
      }
   }

   private static void prune(long now) {
      pruned = now;
      CHUNKS.values().removeIf(chunk -> now - Math.max(chunk.last, chunk.lastSwap) > 6000000000L);
      LOADED.long2LongEntrySet().removeIf(e -> now - e.getLongValue() > 6000000000L);
   }

   private static final class ChunkChanges {
      long lastSwap = -2305843009213693952L;
      long last = -2305843009213693952L;
      final long[] times = new long[32];
      final long[] positions = new long[32];
      int next;
      int size;

      void add(long time, long pos) {
         this.times[this.next] = time;
         this.positions[this.next] = pos;
         this.next = (this.next + 1) % 32;
         if (this.size < 32) {
            this.size++;
         }

         this.last = time;
      }
   }
}

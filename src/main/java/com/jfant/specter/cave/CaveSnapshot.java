package com.jfant.specter.cave;

import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

public final class CaveSnapshot {
   public static final byte WALL = 0;
   public static final byte AIR = 1;
   public static final byte FLUID = 2;
   public static final byte OTHER = 3;
   private static final int LONGS = 128;
   private static final long[] ALL_OTHER = new long[128];
   private static byte[] classes = new byte[0];
   public final int chunkX;
   public final int chunkZ;
   public final int lowSection;
   public final int minY;
   public final int maxY;
   private final long[][] sections;
   public final long caveSections;

   private CaveSnapshot(int chunkX, int chunkZ, int lowSection, int minY, int maxY, long[][] sections, long caveSections) {
      this.chunkX = chunkX;
      this.chunkZ = chunkZ;
      this.lowSection = lowSection;
      this.minY = minY;
      this.maxY = maxY;
      this.sections = sections;
      this.caveSections = caveSections;
   }

   public int sectionCount() {
      return this.sections.length;
   }

   public byte get(int x, int y, int z) {
      if (y >= this.minY && y <= this.maxY) {
         long[] bits = this.sections[(y >> 4) - this.lowSection];
         if (bits == null) {
            return 0;
         } else {
            int i = (y & 15) << 8 | (z & 15) << 4 | x & 15;
            return (byte)(bits[i >>> 5] >>> ((i & 31) << 1) & 3L);
         }
      } else {
         return 3;
      }
   }

   public byte get(int s, int index) {
      long[] bits = this.sections[s];
      return bits == null ? 0 : (byte)(bits[index >>> 5] >>> ((index & 31) << 1) & 3L);
   }

   public boolean sameBand(CaveSnapshot other) {
      return other != null
         && other.lowSection == this.lowSection
         && other.minY == this.minY
         && other.maxY == this.maxY
         && other.sections.length == this.sections.length;
   }

   public CaveSnapshot keepingHidden(CaveSnapshot old) {
      if (!this.sameBand(old)) {
         return this;
      } else {
         long lost = old.caveSections & ~this.caveSections;
         if (lost == 0L) {
            return this;
         } else {
            long[][] merged = (long[][])this.sections.clone();
            long caves = this.caveSections;
            boolean changed = false;

            for (int s = 0; s < merged.length; s++) {
               if ((lost >>> s & 1L) != 0L && merged[s] == null) {
                  merged[s] = old.sections[s];
                  caves |= 1L << s;
                  changed = true;
               }
            }

            return changed ? new CaveSnapshot(this.chunkX, this.chunkZ, this.lowSection, this.minY, this.maxY, merged, caves) : this;
         }
      }
   }

   public static void buildClasses() {
      byte[] table = new byte[Block.BLOCK_STATE_REGISTRY.size()];

      for (BlockState state : Block.BLOCK_STATE_REGISTRY) {
         int id = Block.getId(state);
         if (id >= 0 && id < table.length) {
            table[id] = classify(state);
         }
      }

      classes = table;
   }

   public static byte classOf(BlockState state) {
      int id = Block.getId(state);
      byte[] table = classes;
      return id >= 0 && id < table.length ? table[id] : classify(state);
   }

   private static byte classify(BlockState state) {
      if (state.isAir()) {
         return 1;
      } else {
         boolean passable = state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty();
         if (!state.getFluidState().isEmpty()) {
            return (byte)(passable ? 2 : 3);
         } else if (naturalWall(state)) {
            return 0;
         } else if (!state.is(Blocks.COBWEB) && !state.is(BlockTags.RAILS)) {
            return (byte)(passable ? 1 : 3);
         } else {
            return 3;
         }
      }
   }

   public static boolean naturalWall(BlockState state) {
      if (!state.is(BlockTags.BASE_STONE_OVERWORLD) && !state.is(BlockTags.BASE_STONE_NETHER) && !state.is(BlockTags.DIRT) && !state.is(BlockTags.TERRACOTTA)) {
         String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
         return path.endsWith("_ore")
            || path.startsWith("infested_")
            || path.equals("gravel")
            || path.equals("sand")
            || path.equals("red_sand")
            || path.equals("sandstone")
            || path.equals("red_sandstone")
            || path.equals("calcite")
            || path.equals("dripstone_block")
            || path.equals("smooth_basalt")
            || path.equals("clay")
            || path.equals("bedrock")
            || path.equals("cobblestone")
            || path.equals("cobbled_deepslate")
            || path.equals("obsidian")
            || path.equals("magma_block")
            || path.equals("packed_ice")
            || path.equals("snow_block")
            || path.equals("soul_sand")
            || path.equals("soul_soil")
            || path.equals("end_stone")
            || path.equals("amethyst_block")
            || path.equals("budding_amethyst")
            || path.equals("raw_iron_block")
            || path.equals("raw_copper_block");
      } else {
         return true;
      }
   }

   public static CaveSnapshot capture(LevelChunk chunk, int minY, int maxY) {
      int worldLow = chunk.getMinY();
      int worldHigh = chunk.getMaxY();
      minY = Math.max(minY, worldLow);
      maxY = Math.min(maxY, worldHigh);
      int chunkX = chunk.getPos().x;
      int chunkZ = chunk.getPos().z;
      if (minY > maxY) {
         return new CaveSnapshot(chunkX, chunkZ, minY >> 4, minY, maxY, new long[0][], 0L);
      } else {
         int lowSection = minY >> 4;
         int highSection = maxY >> 4;
         int count = highSection - lowSection + 1;
         LevelChunkSection[] array = chunk.getSections();
         LevelChunkSection[] band = new LevelChunkSection[count];
         boolean[] allWall = new boolean[count];

         for (int s = 0; s < count; s++) {
            int index = chunk.getSectionIndex(lowSection + s << 4);
            LevelChunkSection section = index >= 0 && index < array.length ? array[index] : null;
            band[s] = section;
            allWall[s] = section != null && !section.hasOnlyAir() && !section.maybeHas(state -> classOf(state) != 0);
         }

         int[] surface = new int[256];
         Arrays.fill(surface, -2147483648);
         int open = 256;

         for (int s = count - 1; s >= 0 && open > 0; s--) {
            LevelChunkSection section = band[s];
            if (section != null && !section.hasOnlyAir()) {
               int base = lowSection + s << 4;

               for (int column = 0; column < 256; column++) {
                  if (surface[column] == -2147483648) {
                     int lx = column & 15;
                     int lz = column >>> 4;

                     for (int ly = 15; ly >= 0; ly--) {
                        int y = base + ly;
                        if (y <= maxY && y >= minY && (allWall[s] || classOf(section.getBlockState(lx, ly, lz)) == 0)) {
                           surface[column] = y;
                           open--;
                           break;
                        }
                     }
                  }
               }
            }
         }

         long[][] sections = new long[count][];
         long caves = 0L;

         for (int sx = 0; sx < count; sx++) {
            if (!allWall[sx]) {
               LevelChunkSection section = band[sx];
               boolean empty = section == null || section.hasOnlyAir();
               int base = lowSection + sx << 4;
               long[] bits = new long[128];
               boolean cave = false;
               boolean wall = false;

               for (int lyx = 0; lyx < 16; lyx++) {
                  int y = base + lyx;

                  for (int lz = 0; lz < 16; lz++) {
                     for (int lx = 0; lx < 16; lx++) {
                        int columnx = lz << 4 | lx;
                        byte c;
                        if (y >= minY && y <= maxY) {
                           c = empty ? 1 : classOf(section.getBlockState(lx, lyx, lz));
                           if (c != 0 && y > surface[columnx]) {
                              c = 3;
                           }
                        } else {
                           c = 3;
                        }

                        if (c == 1 || c == 2) {
                           cave = true;
                        } else if (c == 0) {
                           wall = true;
                        }

                        if (c != 0) {
                           int i = lyx << 8 | columnx;
                           bits[i >>> 5] = bits[i >>> 5] | (long)c << ((i & 31) << 1);
                        }
                     }
                  }
               }

               sections[sx] = !cave && !wall ? ALL_OTHER : bits;
               if (cave) {
                  caves |= 1L << sx;
               }
            }
         }

         return new CaveSnapshot(chunkX, chunkZ, lowSection, minY, maxY, sections, caves);
      }
   }

   static {
      Arrays.fill(ALL_OTHER, -1L);
   }
}

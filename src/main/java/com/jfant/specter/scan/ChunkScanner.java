package com.jfant.specter.scan;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.lighting.LayerLightEventListener;

public final class ChunkScanner {
   public static final int GLOW_MAX_Y = 45;
   private static final int MIN_TRIAL_CHAMBER_BLOCKS = 64;
   public final int minVineLength;
   public final int minCaveVineLength;
   public final boolean collectGlow;
   private static final ReferenceOpenHashSet<Block> INDICATOR_BLOCKS = new ReferenceOpenHashSet();
   private static final ReferenceOpenHashSet<Block> TRIAL_CHAMBER_BLOCKS = new ReferenceOpenHashSet();

   public ChunkScanner(int minVineLength, int minCaveVineLength, boolean collectGlow) {
      this.minVineLength = minVineLength;
      this.minCaveVineLength = minCaveVineLength;
      this.collectGlow = collectGlow;
   }

   public ChunkScanner.ScanResult scan(LevelChunk chunk, LevelChunk west, LevelChunk east, LevelChunk north, LevelChunk south, DripstoneScanner.Rule dripstone) {
      ChunkScanner.ScanResult result = this.scan(chunk);
      if (dripstone != null) {
         result.counts()[Indicator.DRIPSTONE.ordinal()] = DripstoneScanner.scan(chunk, west, east, north, south, dripstone).length;
      }

      return result;
   }

   public ChunkScanner.ScanResult scan(LevelChunk chunk) {
      int[] counts = new int[Indicator.COUNT];
      boolean trialSpawner = false;
      int trialChamberBlocks = 0;
      MutableBlockPos cursor = new MutableBlockPos();
      LevelChunkSection[] sections = chunk.getSections();
      int startX = chunk.getPos().getMinBlockX();
      int startZ = chunk.getPos().getMinBlockZ();

      for (LevelChunkSection section : sections) {
         if (section.getBiomes().maybeHas(biome -> biome.is(Biomes.DEEP_DARK))) {
            counts[Indicator.DEEP_DARK.ordinal()] = 1;
            break;
         }
      }

      boolean geodeAnchor = false;

      for (int i = 0; this.collectGlow && i < sections.length && !geodeAnchor; i++) {
         if (!sections[i].hasOnlyAir() && chunk.getSectionYFromSectionIndex(i) * 16 <= 45) {
            geodeAnchor = sections[i].maybeHas(ChunkScanner::isGeodeAnchor);
         }
      }

      boolean listedCluster = false;
      boolean listedBud = false;

      for (int ix = 0; ix < sections.length; ix++) {
         LevelChunkSection sectionx = sections[ix];
         if (!sectionx.hasOnlyAir()) {
            int baseY = chunk.getSectionYFromSectionIndex(ix) * 16;
            if (baseY <= 45 && !sectionx.maybeHas(ChunkScanner::never)) {
               listedCluster |= sectionx.maybeHas(statex -> statex.is(Blocks.AMETHYST_CLUSTER));
               listedBud |= sectionx.maybeHas(ChunkScanner::isBud);
            }

            boolean hasIndicators = sectionx.maybeHas(ChunkScanner::isIndicatorBlock);
            if (hasIndicators) {
               for (int y = 0; y < 16; y++) {
                  for (int x = 0; x < 16; x++) {
                     for (int z = 0; z < 16; z++) {
                        BlockState state = sectionx.getBlockState(x, y, z);
                        if (!state.isAir()) {
                           Block block = state.getBlock();
                           if (block == Blocks.AMETHYST_CLUSTER) {
                              counts[Indicator.AMETHYST_CLUSTER.ordinal()]++;
                           } else if (block == Blocks.SMALL_AMETHYST_BUD || block == Blocks.MEDIUM_AMETHYST_BUD || block == Blocks.LARGE_AMETHYST_BUD) {
                              counts[Indicator.AMETHYST_BUDS.ordinal()]++;
                           } else if (block == Blocks.BEEHIVE) {
                              counts[Indicator.BEEHIVE.ordinal()]++;
                           } else if (block == Blocks.KELP) {
                              if (chunk.getBlockState(cursor.set(startX + x, baseY + y + 1, startZ + z)).isAir()) {
                                 counts[Indicator.SURFACE_KELP.ordinal()]++;
                              }
                           } else if (block == Blocks.VINE) {
                              if (this.isGroundedVineColumn(chunk, cursor, startX + x, baseY + y, startZ + z)) {
                                 counts[Indicator.GROUNDED_VINE.ordinal()]++;
                              }
                           } else if (isSculk(block)) {
                              if (!sectionx.getNoiseBiome(x >> 2, y >> 2, z >> 2).is(Biomes.DEEP_DARK)) {
                                 counts[Indicator.SCULK.ordinal()]++;
                              }
                           } else if (block == Blocks.CAVE_VINES) {
                              if (this.isQualifyingCaveVineColumn(chunk, cursor, startX + x, baseY + y, startZ + z, state)) {
                                 counts[Indicator.CAVE_VINES.ordinal()]++;
                              }
                           } else if (block == Blocks.TRIAL_SPAWNER || block == Blocks.VAULT) {
                              trialSpawner = true;
                           } else if (baseY + y < 0 && TRIAL_CHAMBER_BLOCKS.contains(block)) {
                              trialChamberBlocks++;
                           }
                        }
                     }
                  }
               }
            }
         }
      }

      if (trialSpawner || trialChamberBlocks >= 64) {
         counts[Indicator.TRIAL_CHAMBER.ordinal()] = 1;
      }

      if (listedCluster && !listedBud && counts[Indicator.AMETHYST_CLUSTER.ordinal()] == 0) {
         counts[Indicator.AMETHYST_LISTED.ordinal()] = 1;
      }

      return new ChunkScanner.ScanResult(counts, geodeAnchor);
   }

   private static boolean isIndicatorBlock(BlockState state) {
      return INDICATOR_BLOCKS.contains(state.getBlock());
   }

   private static boolean isTrialChamberBlock(Block block) {
      String path = BuiltInRegistries.BLOCK.getKey(block).getPath();
      return path.startsWith("tuff_brick")
         || path.startsWith("polished_tuff")
         || path.startsWith("chiseled_tuff")
         || path.startsWith("waxed_") && path.contains("copper");
   }

   private static boolean never(BlockState state) {
      return false;
   }

   private static boolean isBud(BlockState state) {
      return state.is(Blocks.SMALL_AMETHYST_BUD) || state.is(Blocks.MEDIUM_AMETHYST_BUD) || state.is(Blocks.LARGE_AMETHYST_BUD);
   }

   private static boolean isGeodeAnchor(BlockState state) {
      return state.is(Blocks.BUDDING_AMETHYST) || state.is(Blocks.AMETHYST_BLOCK);
   }

   public static boolean isHiddenGlow(Level world, MutableBlockPos pos, int x, int y, int z) {
      int light = world.getBrightness(LightLayer.BLOCK, pos.set(x, y, z));
      return light >= 1 && light <= 5
         ? world.getBrightness(LightLayer.BLOCK, pos.set(x, y - 1, z)) <= 5
            && world.getBrightness(LightLayer.BLOCK, pos.set(x, y + 1, z)) <= 5
            && world.getBrightness(LightLayer.BLOCK, pos.set(x - 1, y, z)) <= 5
            && world.getBrightness(LightLayer.BLOCK, pos.set(x + 1, y, z)) <= 5
            && world.getBrightness(LightLayer.BLOCK, pos.set(x, y, z - 1)) <= 5
            && world.getBrightness(LightLayer.BLOCK, pos.set(x, y, z + 1)) <= 5
         : false;
   }

   public static int countGlowClusters(ChunkScanner.LightCopy light, int chunkX, int chunkZ, int minY, int maxY) {
      LongOpenHashSet found = new LongOpenHashSet();
      int startX = chunkX << 4;
      int startZ = chunkZ << 4;

      for (int y = minY; y <= maxY; y++) {
         for (int z = startZ; z < startZ + 16; z++) {
            for (int x = startX; x < startX + 16; x++) {
               if (light.get(x, y, z) == 4) {
                  for (Direction d : Direction.values()) {
                     int cx = x + d.getStepX();
                     int cy = y + d.getStepY();
                     int cz = z + d.getStepZ();
                     if (cx >> 4 == chunkX && cz >> 4 == chunkZ) {
                        long centre = BlockPos.asLong(cx, cy, cz);
                        if (!found.contains(centre) && isClusterCentre(light, cx, cy, cz)) {
                           found.add(centre);
                        }
                     }
                  }
               }
            }
         }
      }

      return found.size();
   }

   private static boolean isClusterCentre(ChunkScanner.LightCopy light, int x, int y, int z) {
      int own = light.get(x, y, z);
      if (own != 0 && own != 5) {
         return false;
      } else {
         int lit = 0;

         for (Direction d : Direction.values()) {
            int fx = x + d.getStepX();
            int fy = y + d.getStepY();
            int fz = z + d.getStepZ();
            int level = light.get(fx, fy, fz);
            if (level > 4) {
               return false;
            }

            if (level >= 4) {
               for (Direction e : Direction.values()) {
                  if (e != d.getOpposite() && light.get(fx + e.getStepX(), fy + e.getStepY(), fz + e.getStepZ()) > 4) {
                     return false;
                  }
               }

               lit++;
            }
         }

         return lit >= 3;
      }
   }

   public static boolean blockLightSent(Level world, int x, int y, int z) {
      DataLayer light = world.getLightEngine().getLayerListener(LightLayer.BLOCK).getDataLayerData(SectionPos.of(x >> 4, y >> 4, z >> 4));
      return light != null && !light.isEmpty();
   }

   public static boolean isLightReady(Level world, int chunkX, int chunkZ) {
      return world.getLightEngine().lightOnInColumn(SectionPos.asLong(chunkX, 0, chunkZ));
   }

   private static boolean isSculk(Block block) {
      return block == Blocks.SCULK
         || block == Blocks.SCULK_CATALYST
         || block == Blocks.SCULK_SENSOR
         || block == Blocks.SCULK_SHRIEKER
         || block == Blocks.SCULK_VEIN;
   }

   private boolean isGroundedVineColumn(LevelChunk chunk, MutableBlockPos cursor, int x, int y, int z) {
      BlockState belowState = chunk.getBlockState(cursor.set(x, y - 1, z));
      if (!belowState.is(Blocks.VINE) && belowState.isRedstoneConductor(chunk, cursor)) {
         int length = 0;

         for (int dy = y; length < this.minVineLength && chunk.getBlockState(cursor.set(x, dy, z)).is(Blocks.VINE); dy++) {
            length++;
         }

         return length >= this.minVineLength;
      } else {
         return false;
      }
   }

   private boolean isQualifyingCaveVineColumn(LevelChunk chunk, MutableBlockPos cursor, int x, int y, int z, BlockState head) {
      int length = 1;
      boolean berries = CaveVines.hasGlowBerries(head);

      for (int dy = y + 1; length < 512; dy++) {
         BlockState state = chunk.getBlockState(cursor.set(x, dy, z));
         if (!state.is(Blocks.CAVE_VINES_PLANT)) {
            break;
         }

         length++;
         berries |= CaveVines.hasGlowBerries(state);
      }

      return length >= this.minCaveVineLength && berries;
   }

   static {
      INDICATOR_BLOCKS.add(Blocks.AMETHYST_CLUSTER);
      INDICATOR_BLOCKS.add(Blocks.SMALL_AMETHYST_BUD);
      INDICATOR_BLOCKS.add(Blocks.MEDIUM_AMETHYST_BUD);
      INDICATOR_BLOCKS.add(Blocks.LARGE_AMETHYST_BUD);
      INDICATOR_BLOCKS.add(Blocks.BEEHIVE);
      INDICATOR_BLOCKS.add(Blocks.KELP);
      INDICATOR_BLOCKS.add(Blocks.VINE);
      INDICATOR_BLOCKS.add(Blocks.SCULK);
      INDICATOR_BLOCKS.add(Blocks.SCULK_CATALYST);
      INDICATOR_BLOCKS.add(Blocks.SCULK_SENSOR);
      INDICATOR_BLOCKS.add(Blocks.SCULK_SHRIEKER);
      INDICATOR_BLOCKS.add(Blocks.SCULK_VEIN);
      INDICATOR_BLOCKS.add(Blocks.CAVE_VINES);
      INDICATOR_BLOCKS.add(Blocks.TRIAL_SPAWNER);
      INDICATOR_BLOCKS.add(Blocks.VAULT);

      for (Block block : BuiltInRegistries.BLOCK) {
         if (isTrialChamberBlock(block)) {
            TRIAL_CHAMBER_BLOCKS.add(block);
         }
      }

      INDICATOR_BLOCKS.addAll(TRIAL_CHAMBER_BLOCKS);
   }

   public static final class LightCopy {
      private static final int[][] OFFSETS = new int[][]{{0, 0}, {-1, 0}, {1, 0}, {0, -1}, {0, 1}};
      private final int chunkX;
      private final int chunkZ;
      private final int bottomSection;
      private final DataLayer[][] columns;

      private LightCopy(int chunkX, int chunkZ, int bottomSection, DataLayer[][] columns) {
         this.chunkX = chunkX;
         this.chunkZ = chunkZ;
         this.bottomSection = bottomSection;
         this.columns = columns;
      }

      public static ChunkScanner.LightCopy capture(Level world, int chunkX, int chunkZ, int minY, int maxY) {
         int bottomSection = minY - 3 >> 4;
         int height = (maxY + 3 >> 4) - bottomSection + 1;
         LayerLightEventListener view = world.getLightEngine().getLayerListener(LightLayer.BLOCK);
         DataLayer[][] columns = new DataLayer[OFFSETS.length][];

         for (int c = 0; c < OFFSETS.length; c++) {
            DataLayer[] column = new DataLayer[Math.max(0, height)];

            for (int s = 0; s < column.length; s++) {
               DataLayer array = view.getDataLayerData(SectionPos.of(chunkX + OFFSETS[c][0], bottomSection + s, chunkZ + OFFSETS[c][1]));
               column[s] = array == null ? null : array.copy();
            }

            columns[c] = column;
         }

         return new ChunkScanner.LightCopy(chunkX, chunkZ, bottomSection, columns);
      }

      public int get(int x, int y, int z) {
         int dx = (x >> 4) - this.chunkX;
         int dz = (z >> 4) - this.chunkZ;
         int column;
         if (dx == 0 && dz == 0) {
            column = 0;
         } else if (dz == 0 && dx == -1) {
            column = 1;
         } else if (dz == 0 && dx == 1) {
            column = 2;
         } else if (dx == 0 && dz == -1) {
            column = 3;
         } else {
            if (dx != 0 || dz != 1) {
               return 0;
            }

            column = 4;
         }

         DataLayer[] arrays = this.columns[column];
         int section = (y >> 4) - this.bottomSection;
         if (section >= 0 && section < arrays.length) {
            DataLayer array = arrays[section];
            return array == null ? 0 : array.get(x & 15, y & 15, z & 15);
         } else {
            return 0;
         }
      }
   }

   public record ScanResult(int[] counts, boolean glow) {
   }
}

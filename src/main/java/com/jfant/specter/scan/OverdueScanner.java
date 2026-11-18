package com.jfant.specter.scan;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome.Precipitation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.material.Fluids;

public final class OverdueScanner {
   private static final double FAST = 1.0;
   private static final double MEDIUM = 0.5;
   private static final double SLOW = 0.25;
   private static final String DONUT_BRAND = "DonutFolia";
   private static final int GROWTH_LIGHT = 9;
   private static final int BAMBOO_LIMIT = 16;
   private static final int SHORT_COLUMN = 3;
   private static final int KELP_MAX_AGE = 25;
   private static final int EYEBLOSSOM_OPENS = 12600;
   private static final int EYEBLOSSOM_CLOSES = 23401;
   private static final int EYEBLOSSOM_SETTLE = 4000;
   private static final int EYEBLOSSOM_MIN = 3;
   private static final int MELT_LIGHT = 10;
   private static final Direction[] DIRECTIONS = Direction.values();

   private OverdueScanner() {
   }

   public static boolean donut(String brand) {
      return brand != null && brand.contains("DonutFolia");
   }

   public static int count(ClientLevel world, LevelChunk chunk, boolean[] counted, boolean donut, int[] byFlag) {
      OverdueScanner.Reader blocks = new OverdueScanner.Reader(world);
      int minX = chunk.getPos().getMinBlockX();
      int minZ = chunk.getPos().getMinBlockZ();
      Boolean bloomsOpen = eyeblossomsOpen(world);
      int count = 0;
      int lateBlooms = 0;
      int settledBlooms = 0;
      LevelChunkSection[] sections = chunk.getSections();

      for (int i = 0; i < sections.length; i++) {
         LevelChunkSection section = sections[i];
         if (section != null && !section.hasOnlyAir() && section.maybeHas(statex -> kindOf(statex, counted) != null)) {
            int baseY = chunk.getSectionYFromSectionIndex(i) << 4;

            for (int y = 0; y < 16; y++) {
               for (int z = 0; z < 16; z++) {
                  for (int x = 0; x < 16; x++) {
                     BlockState state = section.getBlockState(x, y, z);
                     OverdueScanner.Flag kind = kindOf(state, counted);
                     if (kind != null) {
                        if (kind == OverdueScanner.Flag.PROPAGULES) {
                           if (state.getValue(BlockStateProperties.AGE_4) < 4) {
                              count++;
                              byFlag[kind.ordinal()]++;
                           }
                        } else if (kind == OverdueScanner.Flag.EYEBLOSSOMS) {
                           if (bloomsOpen != null) {
                              if (state.is(Blocks.OPEN_EYEBLOSSOM) == bloomsOpen) {
                                 settledBlooms++;
                              } else {
                                 lateBlooms++;
                              }
                           }
                        } else if (owed(blocks, minX + x, baseY + y, minZ + z, state, kind, donut)) {
                           count++;
                           byFlag[kind.ordinal()]++;
                        }
                     }
                  }
               }
            }
         }
      }

      if (settledBlooms == 0 && lateBlooms >= 3) {
         count += lateBlooms;
         byFlag[OverdueScanner.Flag.EYEBLOSSOMS.ordinal()] += lateBlooms;
      }

      if ((counted[OverdueScanner.Flag.SNOW.ordinal()] || counted[OverdueScanner.Flag.ICE.ordinal()]) && chunk.hasPrimedHeightmap(Types.MOTION_BLOCKING)) {
         Heightmap heightmap = chunk.getOrCreateHeightmapUnprimed(Types.MOTION_BLOCKING);
         int seaLevel = world.getSeaLevel();

         for (int z = 0; z < 16; z++) {
            for (int xx = 0; xx < 16; xx++) {
               int y = heightmap.getFirstAvailable(xx, z);
               OverdueScanner.Flag kind = surfaceOwed(world, blocks, minX + xx, y, minZ + z, seaLevel, donut, counted);
               if (kind != null) {
                  count++;
                  byFlag[kind.ordinal()]++;
               }
            }
         }
      }

      return count;
   }

   private static OverdueScanner.Flag kindOf(BlockState state, boolean[] counted) {
      OverdueScanner.Flag kind = classify(state);
      return kind != null && counted[kind.ordinal()] ? kind : null;
   }

   private static OverdueScanner.Flag classify(BlockState state) {
      Block block = state.getBlock();
      if (block == Blocks.KELP) {
         return OverdueScanner.Flag.KELP;
      } else if (block == Blocks.CAVE_VINES) {
         return OverdueScanner.Flag.CAVE_VINES;
      } else if (block == Blocks.WEEPING_VINES) {
         return OverdueScanner.Flag.WEEPING_VINES;
      } else if (block == Blocks.BAMBOO) {
         return OverdueScanner.Flag.BAMBOO;
      } else if (block == Blocks.SUGAR_CANE) {
         return OverdueScanner.Flag.SUGAR_CANE;
      } else if (block == Blocks.CACTUS) {
         return OverdueScanner.Flag.CACTUS;
      } else if (block == Blocks.COCOA) {
         return OverdueScanner.Flag.COCOA;
      } else if (block == Blocks.SWEET_BERRY_BUSH) {
         return OverdueScanner.Flag.BERRIES;
      } else if (block == Blocks.NETHER_WART) {
         return OverdueScanner.Flag.NETHER_WART;
      } else if (block == Blocks.PUMPKIN_STEM || block == Blocks.MELON_STEM) {
         return OverdueScanner.Flag.STEMS;
      } else if (block == Blocks.VINE) {
         return OverdueScanner.Flag.VINES;
      } else if (block == Blocks.BUDDING_AMETHYST || block == Blocks.AMETHYST_CLUSTER || isBud(state)) {
         return OverdueScanner.Flag.AMETHYST;
      } else if (block == Blocks.MANGROVE_PROPAGULE) {
         return state.getValue(BlockStateProperties.HANGING) ? OverdueScanner.Flag.PROPAGULES : null;
      } else if (block == Blocks.OPEN_EYEBLOSSOM || block == Blocks.CLOSED_EYEBLOSSOM) {
         return OverdueScanner.Flag.EYEBLOSSOMS;
      } else if (block instanceof CropBlock) {
         return OverdueScanner.Flag.CROPS;
      } else if (block instanceof LeavesBlock) {
         return OverdueScanner.Flag.LEAVES;
      } else {
         return !isGrass(state) && !state.is(Blocks.DIRT) ? null : OverdueScanner.Flag.GRASS_DIRT;
      }
   }

   private static Boolean eyeblossomsOpen(ClientLevel world) {
      if (world.dimension() == Level.OVERWORLD && !world.dimensionType().hasFixedTime()) {
         int time = (int)Math.floorMod(world.getDayTime(), 24000L);
         boolean open = time >= 12600 && time < 23401;
         int since = open ? time - 12600 : Math.floorMod(time - 23401, 24000);
         return since >= 4000 ? open : null;
      } else {
         return null;
      }
   }

   private static boolean owed(OverdueScanner.Reader blocks, int x, int y, int z, BlockState state, OverdueScanner.Flag kind, boolean donut) {
      if (kind == OverdueScanner.Flag.GRASS_DIRT) {
         return soilOwed(blocks, x, y, z, state);
      } else {
         if (donut) {
            if (kind == OverdueScanner.Flag.KELP) {
               return blocks.state(x, y + 1, z).is(Blocks.WATER) && column(blocks, x, y, z, Blocks.KELP_PLANT, 3) < 3;
            }

            if (kind == OverdueScanner.Flag.AMETHYST) {
               return isBud(state);
            }

            if (kind == OverdueScanner.Flag.CAVE_VINES) {
               return false;
            }
         }

         return growthOwed(blocks, x, y, z, state);
      }
   }

   private static boolean growthOwed(OverdueScanner.Reader blocks, int x, int y, int z, BlockState state) {
      Block block = state.getBlock();
      if (block == Blocks.KELP) {
         return state.getValue(BlockStateProperties.AGE_25) < 25 && blocks.state(x, y + 1, z).is(Blocks.WATER);
      } else if (block == Blocks.CAVE_VINES || block == Blocks.WEEPING_VINES) {
         return state.getValue(BlockStateProperties.AGE_25) < 25 && blocks.state(x, y - 1, z).isAir();
      } else if (block == Blocks.BAMBOO) {
         return state.getValue(BlockStateProperties.STAGE) == 0
            && blocks.state(x, y + 1, z).isAir()
            && bambooBelow(blocks, x, y, z) + 1 < 16
            && blocks.light(x, y + 1, z) >= 9;
      } else if (block == Blocks.SUGAR_CANE) {
         return blocks.state(x, y + 1, z).isAir() && column(blocks, x, y, z, Blocks.SUGAR_CANE, 3) < 3;
      } else if (block == Blocks.CACTUS) {
         return blocks.state(x, y + 1, z).isAir() && (state.getValue(BlockStateProperties.AGE_15) < 15 || column(blocks, x, y, z, Blocks.CACTUS, 3) < 3);
      } else if (block == Blocks.COCOA) {
         return state.getValue(BlockStateProperties.AGE_2) < 2;
      } else if (block == Blocks.NETHER_WART) {
         return state.getValue(BlockStateProperties.AGE_3) < 3;
      } else if (block == Blocks.SWEET_BERRY_BUSH) {
         return state.getValue(BlockStateProperties.AGE_3) < 3 && blocks.light(x, y + 1, z) >= 9;
      } else if (block == Blocks.PUMPKIN_STEM || block == Blocks.MELON_STEM) {
         return state.getValue(BlockStateProperties.AGE_7) < 7 && blocks.light(x, y, z) >= 9;
      } else if (block instanceof CropBlock crop) {
         return !crop.isMaxAge(state) && blocks.light(x, y, z) >= 9;
      } else if (block == Blocks.VINE) {
         return blocks.state(x, y - 1, z).isAir()
            && (
               state.getValue(BlockStateProperties.NORTH)
                  || state.getValue(BlockStateProperties.EAST)
                  || state.getValue(BlockStateProperties.SOUTH)
                  || state.getValue(BlockStateProperties.WEST)
            );
      } else {
         return !(block instanceof LeavesBlock)
            ? amethystOwed(blocks, x, y, z, state)
            : state.hasProperty(BlockStateProperties.DISTANCE)
               && state.hasProperty(BlockStateProperties.PERSISTENT)
               && state.getValue(BlockStateProperties.DISTANCE) == 7
               && !state.getValue(BlockStateProperties.PERSISTENT);
      }
   }

   private static boolean amethystOwed(OverdueScanner.Reader blocks, int x, int y, int z, BlockState state) {
      if (state.is(Blocks.BUDDING_AMETHYST)) {
         for (Direction d : DIRECTIONS) {
            BlockState near = blocks.state(x + d.getStepX(), y + d.getStepY(), z + d.getStepZ());
            if (near.isAir()) {
               return true;
            }

            if (near.is(Blocks.WATER) && near.getFluidState().getAmount() == 8) {
               return true;
            }
         }

         return false;
      } else if (isBud(state) && state.hasProperty(BlockStateProperties.FACING)) {
         Direction facing = state.getValue(BlockStateProperties.FACING);
         return blocks.state(x - facing.getStepX(), y - facing.getStepY(), z - facing.getStepZ()).is(Blocks.BUDDING_AMETHYST);
      } else {
         return false;
      }
   }

   private static int column(OverdueScanner.Reader blocks, int x, int y, int z, Block target, int limit) {
      int length = 1;

      while (length < limit && blocks.state(x, y - length, z).is(target)) {
         length++;
      }

      return length;
   }

   private static int bambooBelow(OverdueScanner.Reader blocks, int x, int y, int z) {
      int below = 0;

      while (below < 16 && blocks.state(x, y - below - 1, z).is(Blocks.BAMBOO)) {
         below++;
      }

      return below;
   }

   private static boolean soilOwed(OverdueScanner.Reader blocks, int x, int y, int z, BlockState state) {
      BlockState upper = blocks.state(x, y + 1, z);
      if (isGrass(state)) {
         return smothers(upper);
      } else if (state.is(Blocks.DIRT) && !smothers(upper) && !upper.getFluidState().is(FluidTags.WATER)) {
         for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
               for (int ny = y - 1; ny <= y + 3; ny++) {
                  if ((dx != 0 || dz != 0 || ny != y)
                     && isGrass(blocks.state(x + dx, ny, z + dz))
                     && !smothers(blocks.state(x + dx, ny + 1, z + dz))
                     && blocks.light(x + dx, ny + 1, z + dz) >= 9) {
                     return true;
                  }
               }
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private static boolean smothers(BlockState upper) {
      if (upper.is(Blocks.SNOW) && upper.getValue(SnowLayerBlock.LAYERS) == 1) {
         return false;
      } else {
         return upper.getFluidState().getAmount() == 8 ? true : upper.getLightBlock() >= 15;
      }
   }

   private static OverdueScanner.Flag surfaceOwed(
      ClientLevel world, OverdueScanner.Reader blocks, int x, int y, int z, int seaLevel, boolean donut, boolean[] counted
   ) {
      boolean snow = counted[OverdueScanner.Flag.SNOW.ordinal()] && (!donut || !blocks.state(x, y - 1, z).is(Blocks.DEEPSLATE));
      if (snow && snowOwed(world, blocks, x, y, z, seaLevel)) {
         return OverdueScanner.Flag.SNOW;
      } else {
         return counted[OverdueScanner.Flag.ICE.ordinal()] && iceOwed(world, blocks, x, y, z, seaLevel) ? OverdueScanner.Flag.ICE : null;
      }
   }

   private static boolean snowOwed(ClientLevel world, OverdueScanner.Reader blocks, int x, int y, int z, int seaLevel) {
      if (!world.isOutsideBuildHeight(y) && blocks.state(x, y, z).isAir()) {
         BlockPos below = new BlockPos(x, y - 1, z);
         if (!holdsSnow(world, blocks.state(x, y - 1, z), below)) {
            return false;
         } else {
            BlockPos pos = new BlockPos(x, y, z);
            return world.getBiome(pos).value().getPrecipitationAt(pos, seaLevel) != Precipitation.SNOW ? false : calmLight(world, blocks, x, y, z);
         }
      } else {
         return false;
      }
   }

   private static boolean holdsSnow(ClientLevel world, BlockState below, BlockPos pos) {
      if (below.is(BlockTags.SNOW_LAYER_CANNOT_SURVIVE_ON)) {
         return false;
      } else if (below.is(BlockTags.SNOW_LAYER_CAN_SURVIVE_ON)) {
         return true;
      } else {
         return below.is(Blocks.SNOW) && below.getValue(SnowLayerBlock.LAYERS) == 8
            ? true
            : Block.isFaceFull(below.getCollisionShape(world, pos), Direction.UP);
      }
   }

   private static boolean iceOwed(ClientLevel world, OverdueScanner.Reader blocks, int x, int y, int z, int seaLevel) {
      int water = y - 1;
      if (world.isOutsideBuildHeight(water)) {
         return false;
      } else {
         BlockState state = blocks.state(x, water, z);
         if (!(state.getBlock() instanceof LiquidBlock) || state.getFluidState().getType() != Fluids.WATER) {
            return false;
         } else if (!loaded(world, x - 1, z) || !loaded(world, x + 1, z) || !loaded(world, x, z - 1) || !loaded(world, x, z + 1)) {
            return false;
         } else if (isWater(blocks, x - 1, water, z)
            && isWater(blocks, x + 1, water, z)
            && isWater(blocks, x, water, z - 1)
            && isWater(blocks, x, water, z + 1)) {
            return false;
         } else {
            return world.getBiome(new BlockPos(x, y, z)).value().warmEnoughToRain(new BlockPos(x, water, z), seaLevel)
               ? false
               : calmLight(world, blocks, x, water, z);
         }
      }
   }

   private static boolean loaded(ClientLevel world, int x, int z) {
      return world.getChunkSource().hasChunk(x >> 4, z >> 4);
   }

   private static boolean isWater(OverdueScanner.Reader blocks, int x, int y, int z) {
      return blocks.state(x, y, z).getFluidState().is(FluidTags.WATER);
   }

   private static boolean calmLight(ClientLevel world, OverdueScanner.Reader blocks, int x, int y, int z) {
      int radius = 5;

      for (int cx = x - radius >> 4; cx <= x + radius >> 4; cx++) {
         for (int cz = z - radius >> 4; cz <= z + radius >> 4; cz++) {
            if (!world.getChunkSource().hasChunk(cx, cz)) {
               return false;
            }
         }
      }

      for (int dx = -radius; dx <= radius; dx++) {
         int restY = radius - Math.abs(dx);

         for (int dy = -restY; dy <= restY; dy++) {
            int restZ = restY - Math.abs(dy);

            for (int dz = -restZ; dz <= restZ; dz++) {
               int emission = blocks.state(x + dx, y + dy, z + dz).getLightEmission();
               if (emission != 0 && emission - Math.abs(dx) - Math.abs(dy) - Math.abs(dz) >= 10) {
                  return false;
               }
            }
         }
      }

      return true;
   }

   public static boolean hasShaft(ClientLevel world, ChunkPos pos) {
      OverdueScanner.Reader blocks = new OverdueScanner.Reader(world);
      int bottom = world.getMinY();
      int top = bottom + world.getHeight();
      int minX = pos.getMinBlockX();
      int minZ = pos.getMinBlockZ();

      for (int x = minX; x < minX + 16; x++) {
         for (int z = minZ; z < minZ + 16; z++) {
            int single = 0;
            int alongX = 0;
            int alongZ = 0;

            for (int y = bottom; y < top; y++) {
               if (singleShaft(world, blocks, x, y, z)) {
                  single++;
               } else {
                  if (shaftEnds(world, blocks, x, y, z, single)) {
                     return true;
                  }

                  single = 0;
               }

               if (wideShaft(world, blocks, x, y, z, 1, 0)) {
                  alongX++;
               } else {
                  if (shaftEnds(world, blocks, x, y, z, alongX)) {
                     return true;
                  }

                  alongX = 0;
               }

               if (wideShaft(world, blocks, x, y, z, 0, 1)) {
                  alongZ++;
               } else {
                  if (shaftEnds(world, blocks, x, y, z, alongZ)) {
                     return true;
                  }

                  alongZ = 0;
               }
            }
         }
      }

      return false;
   }

   private static boolean singleShaft(ClientLevel world, OverdueScanner.Reader blocks, int x, int y, int z) {
      return open(world, blocks, x, y, z)
         && !open(world, blocks, x - 1, y, z)
         && !open(world, blocks, x + 1, y, z)
         && !open(world, blocks, x, y, z - 1)
         && !open(world, blocks, x, y, z + 1);
   }

   private static boolean wideShaft(ClientLevel world, OverdueScanner.Reader blocks, int x, int y, int z, int stepX, int stepZ) {
      for (int i = 0; i < 3; i++) {
         if (!open(world, blocks, x + stepX * i, y, z + stepZ * i)) {
            return false;
         }
      }

      if (!open(world, blocks, x - stepX, y, z - stepZ) && !open(world, blocks, x + stepX * 3, y, z + stepZ * 3)) {
         for (int ix = 0; ix < 3; ix++) {
            int wx = x + stepX * ix;
            int wz = z + stepZ * ix;
            if (open(world, blocks, wx - stepZ, y, wz - stepX) || open(world, blocks, wx + stepZ, y, wz + stepX)) {
               return false;
            }
         }

         return true;
      } else {
         return false;
      }
   }

   private static boolean shaftEnds(ClientLevel world, OverdueScanner.Reader blocks, int x, int y, int z, int run) {
      return run >= 2 && open(world, blocks, x, y, z) && !open(world, blocks, x, y - run - 1, z);
   }

   private static boolean open(ClientLevel world, OverdueScanner.Reader blocks, int x, int y, int z) {
      return blocks.state(x, y, z).getCollisionShape(world, BlockPos.ZERO).isEmpty();
   }

   private static boolean isBud(BlockState state) {
      return state.is(Blocks.SMALL_AMETHYST_BUD) || state.is(Blocks.MEDIUM_AMETHYST_BUD) || state.is(Blocks.LARGE_AMETHYST_BUD);
   }

   private static boolean isGrass(BlockState state) {
      return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.MYCELIUM);
   }

   public static enum Flag {
      KELP("kelp", "Kelp", "Kelp under water that has not reached its full age (on DonutSMP: columns under 3 tall).", 0.5),
      CAVE_VINES("cave-vines", "Cave Vines", "Cave vines with room below that have not reached full length.", 0.5),
      WEEPING_VINES("weeping-vines", "Weeping Vines", "Weeping vines with room below that have not reached full length.", 0.5),
      BAMBOO("bamboo", "Bamboo", "Bamboo that still has room and light to grow taller.", 1.0),
      SUGAR_CANE("sugar-cane", "Sugar Cane", "Sugar cane under 3 tall with room above.", 0.5),
      CACTUS("cactus", "Cactus", "Cactus under 3 tall, or not done growing, with room above.", 0.5),
      COCOA("cocoa", "Cocoa", "Cocoa pods that are not ripe.", 0.5),
      BERRIES("sweet-berries", "Sweet Berries", "Sweet berry bushes in light that are not full.", 0.5),
      NETHER_WART("nether-wart", "Nether Wart", "Nether wart that is not ripe.", 0.5),
      CROPS("crops", "Crops", "Crops in light that are not ripe.", 0.25),
      STEMS("stems", "Stems", "Melon and pumpkin stems in light that are not fully grown.", 0.25),
      AMETHYST("amethyst", "Amethyst", "Budding amethyst with room for a bud and none on it, or a bud still growing (on DonutSMP: any bud).", 0.5),
      VINES("vines", "Vines", "Vines with air below them, which would have grown down.", 1.0),
      LEAVES("leaves", "Leaves", "Natural leaves with no log near enough, which rot within a minute or two of anyone coming close.", 1.0),
      GRASS_DIRT("grass-dirt", "Grass & Dirt", "Grass under a block that would have killed it, or bare dirt next to lit grass that would have grown over.", 1.0),
      PROPAGULES("propagules", "Mangrove Propagules", "Hanging mangrove propagules that are not fully grown.", 0.5),
      EYEBLOSSOMS("eyeblossoms", "Eyeblossoms", "Eyeblossoms still open or closed long after the time of day should have flipped them.", 1.0),
      SNOW("snow", "Snow", "Open ground in a snowy biome with no snow on it.", 0.25),
      ICE("ice", "Ice", "Still water at the edge of a frozen biome's lake that has not frozen.", 0.5);

      public static final OverdueScanner.Flag[] VALUES = values();
      public final String setting;
      public final String title;
      public final String description;
      public final double points;

      private Flag(String setting, String title, String description, double points) {
         this.setting = setting;
         this.title = title;
         this.description = description;
         this.points = points;
      }
   }

   private static final class Reader {
      private static final BlockState VOID = Blocks.VOID_AIR.defaultBlockState();
      private final ClientLevel world;
      private LevelChunk chunk;

      Reader(ClientLevel world) {
         this.world = world;
      }

      BlockState state(int x, int y, int z) {
         if (this.world.isOutsideBuildHeight(y)) {
            return VOID;
         } else {
            LevelChunk at = this.chunkAt(x >> 4, z >> 4);
            if (at == null) {
               return VOID;
            } else {
               LevelChunkSection section = at.getSections()[at.getSectionIndex(y)];
               return section == null ? VOID : section.getBlockState(x & 15, y & 15, z & 15);
            }
         }
      }

      private boolean emptySection(int x, int y, int z) {
         LevelChunk at = this.chunkAt(x >> 4, z >> 4);
         if (at == null) {
            return true;
         } else {
            LevelChunkSection section = at.getSections()[at.getSectionIndex(y)];
            return section == null || section.hasOnlyAir();
         }
      }

      private LevelChunk chunkAt(int cx, int cz) {
         if (this.chunk != null && this.chunk.getPos().x == cx && this.chunk.getPos().z == cz) {
            return this.chunk;
         } else {
            LevelChunk loaded = this.world.getChunkSource().getChunkNow(cx, cz);
            if (loaded != null) {
               this.chunk = loaded;
            }

            return loaded;
         }
      }

      int light(int x, int y, int z) {
         int value = Math.max(this.raw(LightLayer.BLOCK, x, y, z), this.raw(LightLayer.SKY, x, y, z));
         if (value < 15 && this.world.dimensionType().hasSkyLight()) {
            return this.openSky(x, y, z) ? 15 : value;
         } else {
            return value;
         }
      }

      private int raw(LightLayer layer, int x, int y, int z) {
         DataLayer data = this.world.getLightEngine().getLayerListener(layer).getDataLayerData(SectionPos.of(x >> 4, y >> 4, z >> 4));
         return data != null && !data.isEmpty() ? data.get(x & 15, y & 15, z & 15) : 0;
      }

      private boolean openSky(int x, int y, int z) {
         int top = this.world.getMaxY();

         for (int ny = y + 1; ny <= top; ny++) {
            if (this.emptySection(x, ny, z)) {
               ny |= 15;
            } else if (this.state(x, ny, z).getLightBlock() > 0) {
               return false;
            }
         }

         return true;
      }
   }
}

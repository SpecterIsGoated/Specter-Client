package com.jfant.specter.scan;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.BambooSaplingBlock;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DaylightDetectorBlock;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.GrowingPlantBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.PitcherCropBlock;
import net.minecraft.world.level.block.PoweredBlock;
import net.minecraft.world.level.block.RedstoneWireBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.SnowyBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.TargetBlock;
import net.minecraft.world.level.block.TripWireHookBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class BlockKinds {
   public static final int KELP = 0;
   public static final int VINES = 1;
   public static final int BEES = 2;
   public static final int CROPS = 3;
   public static final int SNOW = 4;
   public static final int GRASS = 5;
   public static final int LEAVES = 6;
   public static final int FIRE = 7;

   private BlockKinds() {
   }

   public static boolean isRedstonePart(BlockState state) {
      Block block = state.getBlock();
      return block instanceof RedstoneWireBlock
         || block instanceof DiodeBlock
         || block instanceof RedstoneTorchBlock
         || block instanceof RedstoneLampBlock
         || block instanceof ObserverBlock
         || block instanceof PistonBaseBlock
         || block instanceof PistonHeadBlock
         || block instanceof MovingPistonBlock
         || block instanceof DispenserBlock
         || block instanceof HopperBlock
         || block instanceof NoteBlock
         || block instanceof DaylightDetectorBlock
         || block instanceof LeverBlock
         || block instanceof ButtonBlock
         || block instanceof BasePressurePlateBlock
         || block instanceof TripWireHookBlock
         || block instanceof TargetBlock
         || block instanceof CrafterBlock
         || block instanceof PoweredBlock;
   }

   public static boolean isTorchLight(BlockState state) {
      return state.is(Blocks.TORCH)
         || state.is(Blocks.WALL_TORCH)
         || state.is(Blocks.SOUL_TORCH)
         || state.is(Blocks.SOUL_WALL_TORCH)
         || state.is(Blocks.COPPER_TORCH)
         || state.is(Blocks.COPPER_WALL_TORCH)
         || CaveVines.hasGlowBerries(state);
   }

   public static boolean isPlacedLight(BlockState state) {
      if (!state.isAir() && !isRedstonePart(state)) {
         String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
         return path.endsWith("torch") && !path.startsWith("redstone")
            || path.endsWith("lantern") && !path.equals("soul_lantern")
            || path.equals("glowstone")
            || path.equals("shroomlight")
            || path.endsWith("froglight")
            || path.equals("end_rod")
            || path.equals("campfire")
            || path.equals("beacon")
            || path.equals("conduit")
            || path.equals("respawn_anchor")
            || path.equals("light");
      } else {
         return false;
      }
   }

   public static boolean isAmethyst(BlockState state) {
      return state.is(Blocks.AMETHYST_CLUSTER)
         || state.is(Blocks.LARGE_AMETHYST_BUD)
         || state.is(Blocks.MEDIUM_AMETHYST_BUD)
         || state.is(Blocks.SMALL_AMETHYST_BUD)
         || state.is(Blocks.AMETHYST_BLOCK)
         || state.is(Blocks.BUDDING_AMETHYST);
   }

   public static boolean isRedstoneOre(BlockState state) {
      return state.is(Blocks.REDSTONE_ORE) || state.is(Blocks.DEEPSLATE_REDSTONE_ORE);
   }

   public static boolean isNaturalChurn(BlockState state) {
      Block block = state.getBlock();
      return !state.getFluidState().isEmpty()
         || block instanceof BaseFireBlock
         || block instanceof LeavesBlock
         || block instanceof VegetationBlock
         || block instanceof GrowingPlantBlock
         || block instanceof VineBlock
         || block instanceof SnowLayerBlock
         || block instanceof CactusBlock
         || state.is(BlockTags.ICE)
         || state.is(BlockTags.SNOW);
   }

   public static boolean isNaturalChange(BlockState old, BlockState state) {
      return old.is(BlockTags.DIRT) && state.is(BlockTags.DIRT) ? true : (old.isAir() || isNaturalChurn(old)) && (state.isAir() || isNaturalChurn(state));
   }

   public static int growthKind(BlockState old, BlockState state) {
      Block o = old.getBlock();
      Block n = state.getBlock();
      if (n != Blocks.KELP && n != Blocks.KELP_PLANT) {
         if (n == Blocks.VINE) {
            return !old.isAir() && o != Blocks.VINE ? -1 : 1;
         } else if (n instanceof BeehiveBlock) {
            return o == n && !old.getValue(BeehiveBlock.HONEY_LEVEL).equals(state.getValue(BeehiveBlock.HONEY_LEVEL)) ? 2 : -1;
         } else if (isPlant(n)) {
            if (o == n) {
               return 3;
            } else if (!old.isAir()
               || !(n instanceof SugarCaneBlock) && !(n instanceof CactusBlock) && !(n instanceof BambooStalkBlock) && n != Blocks.CACTUS_FLOWER) {
               return o instanceof BambooSaplingBlock && n instanceof BambooStalkBlock ? 3 : -1;
            } else {
               return 3;
            }
         } else if (n instanceof AttachedStemBlock) {
            return o instanceof StemBlock ? 3 : -1;
         } else if ((n == Blocks.MELON || n == Blocks.PUMPKIN) && old.isAir()) {
            return 3;
         } else if (o instanceof SaplingBlock && !state.isAir()) {
            return 3;
         } else if (n == Blocks.SNOW) {
            return !old.isAir() && o != Blocks.SNOW ? -1 : 4;
         } else if (o == Blocks.SNOW && state.isAir()) {
            return 4;
         } else if ((o != Blocks.WATER || n != Blocks.ICE) && (o != Blocks.ICE || n != Blocks.WATER)) {
            if (n instanceof SnowyBlock && o == n) {
               return 4;
            } else if (o != Blocks.DIRT || n != Blocks.GRASS_BLOCK && n != Blocks.MYCELIUM) {
               if ((o == Blocks.GRASS_BLOCK || o == Blocks.MYCELIUM) && n == Blocks.DIRT) {
                  return 5;
               } else if (!(o instanceof LeavesBlock) || !state.isAir() && o != n) {
                  return !(n instanceof BaseFireBlock) && !(o instanceof BaseFireBlock) ? -1 : 7;
               } else {
                  return 6;
               }
            } else {
               return 5;
            }
         } else {
            return 4;
         }
      } else {
         return o != Blocks.WATER && o != Blocks.KELP && o != Blocks.KELP_PLANT ? -1 : 0;
      }
   }

   private static boolean isPlant(Block b) {
      return b instanceof CropBlock
         || b instanceof StemBlock
         || b instanceof SugarCaneBlock
         || b instanceof CactusBlock
         || b instanceof BambooStalkBlock
         || b instanceof SweetBerryBushBlock
         || b instanceof CocoaBlock
         || b instanceof NetherWartBlock
         || b instanceof SaplingBlock
         || b instanceof PitcherCropBlock
         || b == Blocks.CACTUS_FLOWER;
   }
}

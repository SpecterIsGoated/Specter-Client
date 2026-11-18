package com.jfant.specter.scan;

import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class AntiXray {
   private static final Set<Block> HIDEABLE = new ReferenceOpenHashSet();
   private static final Set<Block> TERRAIN = new ReferenceOpenHashSet();

   private AntiXray() {
   }

   public static boolean isHideable(Block block) {
      return HIDEABLE.contains(block);
   }

   public static boolean isSwap(BlockState old, BlockState state) {
      return isHideable(old.getBlock()) || isHideable(state.getBlock()) || isTerrainSwap(old, state);
   }

   public static boolean isTerrainSwap(BlockState old, BlockState state) {
      Block o = old.getBlock();
      Block n = state.getBlock();
      if (o == n || old.isAir() || state.isAir()) {
         return false;
      } else {
         return isGround(o) && isGround(n) ? !isGrassStep(o, n) : false;
      }
   }

   public static boolean isGrassStep(Block o, Block n) {
      return o == Blocks.DIRT && (n == Blocks.GRASS_BLOCK || n == Blocks.MYCELIUM) || (o == Blocks.GRASS_BLOCK || o == Blocks.MYCELIUM) && n == Blocks.DIRT;
   }

   private static boolean isGround(Block block) {
      return TERRAIN.contains(block) || block == Blocks.WATER || block == Blocks.LAVA;
   }

   static {
      for (Block block : BuiltInRegistries.BLOCK) {
         String path = BuiltInRegistries.BLOCK.getKey(block).getPath();
         if (path.endsWith("_ore")
            || path.startsWith("raw_") && path.endsWith("_block")
            || path.contains("amethyst")
            || path.endsWith("shulker_box")
            || path.startsWith("sculk")
            || path.contains("deepslate")) {
            HIDEABLE.add(block);
         }

         if (path.endsWith("terracotta") && !path.contains("glazed")) {
            TERRAIN.add(block);
         }
      }

      HIDEABLE.addAll(
         List.of(
            Blocks.STONE,
            Blocks.TUFF,
            Blocks.GRAVEL,
            Blocks.CALCITE,
            Blocks.SMOOTH_BASALT,
            Blocks.GLOW_LICHEN,
            Blocks.POINTED_DRIPSTONE,
            Blocks.DRIPSTONE_BLOCK,
            Blocks.CAVE_VINES,
            Blocks.CAVE_VINES_PLANT,
            Blocks.ANCIENT_DEBRIS,
            Blocks.OBSIDIAN,
            Blocks.CRYING_OBSIDIAN,
            Blocks.MOSSY_COBBLESTONE,
            Blocks.SPAWNER,
            Blocks.TRIAL_SPAWNER,
            Blocks.CHEST,
            Blocks.TRAPPED_CHEST,
            Blocks.BARREL,
            Blocks.ENDER_CHEST,
            Blocks.BEDROCK
         )
      );
      TERRAIN.addAll(
         List.of(
            Blocks.DIRT,
            Blocks.GRASS_BLOCK,
            Blocks.PODZOL,
            Blocks.COARSE_DIRT,
            Blocks.MYCELIUM,
            Blocks.ROOTED_DIRT,
            Blocks.MOSS_BLOCK,
            Blocks.PALE_MOSS_BLOCK,
            Blocks.MUD,
            Blocks.MUDDY_MANGROVE_ROOTS,
            Blocks.SAND,
            Blocks.RED_SAND,
            Blocks.SUSPICIOUS_SAND,
            Blocks.SUSPICIOUS_GRAVEL,
            Blocks.SNOW_BLOCK,
            Blocks.POWDER_SNOW,
            Blocks.CLAY,
            Blocks.SANDSTONE,
            Blocks.RED_SANDSTONE,
            Blocks.PACKED_ICE,
            Blocks.BLUE_ICE,
            Blocks.MANGROVE_ROOTS,
            Blocks.GRANITE,
            Blocks.DIORITE,
            Blocks.ANDESITE,
            Blocks.NETHERRACK,
            Blocks.SOUL_SAND,
            Blocks.SOUL_SOIL,
            Blocks.BLACKSTONE,
            Blocks.MAGMA_BLOCK,
            Blocks.END_STONE
         )
      );
   }
}

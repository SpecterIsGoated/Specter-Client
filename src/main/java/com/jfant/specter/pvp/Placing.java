package com.jfant.specter.pvp;

import com.jfant.specter.core.SpecterCore;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;

public final class Placing {
   private Placing() {
   }

   @Nullable
   public static BlockPos target(LocalPlayer player, BlockHitResult hit, BlockState toPlace) {
      BlockState clicked = SpecterCore.mc.level.getBlockState(hit.getBlockPos());
      if (!player.isShiftKeyDown() && reactsToUse(hit.getBlockPos(), clicked)) {
         return null;
      } else {
         BlockPos target = clicked.canBeReplaced() ? hit.getBlockPos() : hit.getBlockPos().relative(hit.getDirection());
         if (SpecterCore.mc.level.getWorldBorder().isWithinBounds(target) && SpecterCore.mc.level.getBlockState(target).canBeReplaced()) {
            return !SpecterCore.mc.level.isUnobstructed(toPlace, target, CollisionContext.of(player)) ? null : target;
         } else {
            return null;
         }
      }
   }

   public static boolean reactsToUse(BlockPos pos, BlockState state) {
      if (state.getMenuProvider(SpecterCore.mc.level, pos) != null) {
         return true;
      } else {
         return !state.is(BlockTags.DOORS)
               && !state.is(BlockTags.TRAPDOORS)
               && !state.is(BlockTags.BUTTONS)
               && !state.is(BlockTags.FENCE_GATES)
               && !state.is(BlockTags.BEDS)
               && !state.is(BlockTags.SHULKER_BOXES)
               && !state.is(BlockTags.ANVIL)
            ? state.is(Blocks.LEVER)
               || state.is(Blocks.REPEATER)
               || state.is(Blocks.COMPARATOR)
               || state.is(Blocks.NOTE_BLOCK)
               || state.is(Blocks.RESPAWN_ANCHOR)
               || state.is(Blocks.CHEST)
               || state.is(Blocks.TRAPPED_CHEST)
               || state.is(Blocks.ENDER_CHEST)
               || state.is(Blocks.CRAFTING_TABLE)
               || state.is(Blocks.ENCHANTING_TABLE)
            : true;
      }
   }
}

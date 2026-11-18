package com.jfant.specter.core.events.entity.player;

import com.jfant.specter.core.events.Cancellable;
import net.minecraft.core.BlockPos;

public class BreakBlockEvent extends Cancellable {
   private static final BreakBlockEvent INSTANCE = new BreakBlockEvent();
   public BlockPos blockPos;

   public static BreakBlockEvent get(BlockPos blockPos) {
      INSTANCE.setCancelled(false);
      INSTANCE.blockPos = blockPos;
      return INSTANCE;
   }
}

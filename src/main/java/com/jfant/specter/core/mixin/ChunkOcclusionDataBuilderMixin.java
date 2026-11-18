package com.jfant.specter.core.mixin;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.events.world.ChunkOcclusionEvent;
import net.minecraft.client.renderer.chunk.VisGraph;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({VisGraph.class})
public abstract class ChunkOcclusionDataBuilderMixin {
   @Inject(
      method = {"setOpaque"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onMarkClosed(BlockPos pos, CallbackInfo info) {
      ChunkOcclusionEvent event = (ChunkOcclusionEvent)SpecterCore.EVENT_BUS.post(ChunkOcclusionEvent.get());
      if (event.isCancelled()) {
         info.cancel();
      }
   }
}

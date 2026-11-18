package com.jfant.specter.mixin.move;

import com.jfant.specter.move.Xray;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({ItemBlockRenderTypes.class})
public abstract class XrayBlockRenderLayersMixin {
   @Inject(
      method = {"getChunkRenderType"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private static void specter$blockLayer(BlockState state, CallbackInfoReturnable<ChunkSectionLayer> cir) {
      int alpha = Xray.layerAlpha(state);
      if (alpha > 0 && alpha < 255) {
         cir.setReturnValue(ChunkSectionLayer.TRANSLUCENT);
      }
   }

   @Inject(
      method = {"getRenderLayer"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private static void specter$fluidLayer(FluidState state, CallbackInfoReturnable<ChunkSectionLayer> cir) {
      int alpha = Xray.layerAlpha(state.createLegacyBlock());
      if (alpha > 0 && alpha < 255) {
         cir.setReturnValue(ChunkSectionLayer.TRANSLUCENT);
      }
   }
}

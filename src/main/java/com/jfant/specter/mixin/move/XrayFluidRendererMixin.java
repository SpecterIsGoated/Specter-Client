package com.jfant.specter.mixin.move;

import com.jfant.specter.move.Xray;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// 26.x: LiquidBlockRenderer became FluidRenderer, vertex colours are packed ints and the chunk layer
// is picked per call to Output#getBuilder.
@Mixin(FluidRenderer.class)
public abstract class XrayFluidRendererMixin {
   @Unique
   private static final ThreadLocal<int[]> specter$alpha = ThreadLocal.withInitial(() -> new int[]{-1});

   @Inject(method = "tesselate", at = @At("HEAD"), cancellable = true)
   private void specter$start(BlockAndTintGetter level, BlockPos pos, FluidRenderer.Output output, BlockState blockState, FluidState fluid, CallbackInfo ci) {
      if (!Xray.active()) {
         specter$alpha.get()[0] = -1;
      } else {
         int alpha = Xray.alpha(fluid.createLegacyBlock(), level, pos);
         if (alpha == 0) {
            ci.cancel();
         } else {
            specter$alpha.get()[0] = alpha;
         }
      }
   }

   @Inject(method = "vertex", at = @At("HEAD"), cancellable = true)
   private void specter$vertexAlpha(VertexConsumer builder, float x, float y, float z, int color, float u, float v, int lightCoords, CallbackInfo ci) {
      int alpha = specter$alpha.get()[0];
      if (alpha != -1) {
         builder.addVertex(x, y, z).setColor(ARGB.red(color), ARGB.green(color), ARGB.blue(color), alpha).setUv(u, v).setLight(lightCoords).setNormal(0.0F, 1.0F, 0.0F);
         ci.cancel();
      }
   }

   @ModifyArg(
      method = "tesselate",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/FluidRenderer$Output;getBuilder(Lnet/minecraft/client/renderer/chunk/ChunkSectionLayer;)Lcom/mojang/blaze3d/vertex/VertexConsumer;"),
      index = 0
   )
   private ChunkSectionLayer specter$fluidLayer(ChunkSectionLayer layer) {
      int alpha = specter$alpha.get()[0];
      return alpha > 0 && alpha < 255 ? ChunkSectionLayer.TRANSLUCENT : layer;
   }
}

package com.jfant.specter.mixin.move;

import com.jfant.specter.move.Xray;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.block.LiquidBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({LiquidBlockRenderer.class})
public abstract class XrayFluidRendererMixin {
   @Unique
   private final ThreadLocal<int[]> specter$alpha = ThreadLocal.withInitial(() -> new int[]{-1});

   @Inject(
      method = {"tesselate"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void specter$start(BlockAndTintGetter world, BlockPos pos, VertexConsumer consumer, BlockState state, FluidState fluid, CallbackInfo ci) {
      if (!Xray.active()) {
         this.specter$alpha.get()[0] = -1;
      } else {
         int alpha = Xray.alpha(fluid.createLegacyBlock(), world, pos);
         if (alpha == 0) {
            ci.cancel();
         } else {
            this.specter$alpha.get()[0] = alpha;
         }
      }
   }

   @ModifyArg(
      method = {"vertex"},
      at = @At(
         value = "INVOKE",
         target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;setColor(FFFF)Lcom/mojang/blaze3d/vertex/VertexConsumer;"
      ),
      index = 3
   )
   private float specter$vertexAlpha(float original) {
      int alpha = this.specter$alpha.get()[0];
      return alpha == -1 ? original : alpha / 255.0F;
   }
}

package com.jfant.specter.mixin.move;

import com.jfant.specter.move.Xray;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ModelBlockRenderer.class})
public abstract class XrayBlockModelRendererMixin {
   @Unique
   private final ThreadLocal<int[]> specter$alpha = ThreadLocal.withInitial(() -> new int[]{-1});

   @Inject(
      method = {"tesselateWithAO", "tesselateWithoutAO"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void specter$start(
      BlockAndTintGetter world,
      List<BlockModelPart> parts,
      BlockState state,
      BlockPos pos,
      PoseStack matrices,
      VertexConsumer consumer,
      boolean cull,
      int overlay,
      CallbackInfo ci
   ) {
      if (!Xray.active()) {
         this.specter$alpha.get()[0] = -1;
      } else {
         int alpha = Xray.alpha(state, world, pos);
         if (alpha == 0) {
            ci.cancel();
         } else {
            this.specter$alpha.get()[0] = alpha;
         }
      }
   }

   @ModifyArg(
      method = {"putQuadData"},
      at = @At(
         value = "INVOKE",
         target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;putBulkData(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lnet/minecraft/client/renderer/block/model/BakedQuad;[FFFFF[II)V"
      ),
      index = 6
   )
   private float specter$quadAlpha(float original) {
      int alpha = this.specter$alpha.get()[0];
      return alpha == -1 ? original : alpha / 255.0F;
   }

   @ModifyReturnValue(
      method = {"shouldRenderFace"},
      at = {@At("RETURN")}
   )
   private static boolean specter$drawFace(boolean original, BlockAndTintGetter world, BlockState state, boolean cull, Direction side, BlockPos pos) {
      return !Xray.active() ? original : Xray.drawFaceAgainst(state, world, pos, side, original);
   }
}

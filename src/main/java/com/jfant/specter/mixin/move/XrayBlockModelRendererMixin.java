package com.jfant.specter.mixin.move;

import com.jfant.specter.move.Xray;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.blaze3d.vertex.QuadInstance;
import java.util.List;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// 26.x: quads carry their own chunk layer, so the translucent switch that used to live in an
// ItemBlockRenderTypes mixin is done here per quad (same approach as Meteor 26.3).
@Mixin(ModelBlockRenderer.class)
public abstract class XrayBlockModelRendererMixin {
   @Shadow
   @Final
   private QuadInstance quadInstance;

   @Unique
   private static final ThreadLocal<int[]> specter$alpha = ThreadLocal.withInitial(() -> new int[]{-1});

   @Inject(method = {"tesselateFlat", "tesselateAmbientOcclusion"}, at = @At("HEAD"), cancellable = true)
   private void specter$start(BlockQuadOutput output, float x, float y, float z, List<BlockStateModelPart> parts, BlockAndTintGetter level, BlockState state, BlockPos pos, CallbackInfo ci) {
      if (!Xray.active()) {
         specter$alpha.get()[0] = -1;
      } else {
         int alpha = Xray.alpha(state, level, pos);
         if (alpha == 0) {
            ci.cancel();
         } else {
            specter$alpha.get()[0] = alpha;
         }
      }
   }

   @Inject(method = "putQuadWithTint", at = @At("HEAD"))
   private void specter$quadAlpha(BlockQuadOutput output, float x, float y, float z, BlockAndTintGetter level, BlockState state, BlockPos pos, BakedQuad quad, CallbackInfo ci) {
      int alpha = specter$alpha.get()[0];
      if (alpha != -1) {
         this.quadInstance.multiplyColor(ARGB.color(alpha, 255, 255, 255));
      }
   }

   @ModifyArg(
      method = "putQuadWithTint",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/BlockQuadOutput;put(FFFLnet/minecraft/client/resources/model/geometry/BakedQuad;Lcom/mojang/blaze3d/vertex/QuadInstance;)V"),
      index = 3
   )
   private BakedQuad specter$translucent(BakedQuad quad) {
      int alpha = specter$alpha.get()[0];
      if (alpha <= 0 || alpha >= 255) {
         return quad;
      }

      BakedQuad.MaterialInfo info = quad.materialInfo();
      if (info.layer() == ChunkSectionLayer.TRANSLUCENT) {
         return quad;
      }

      BakedQuad.MaterialInfo translucent = new BakedQuad.MaterialInfo(
         info.sprite(),
         ChunkSectionLayer.TRANSLUCENT,
         info.itemRenderType(),
         info.itemGlintRenderType(),
         info.itemGlintSpecialRenderType(),
         info.tintIndex(),
         info.shadeDirectionOverride(),
         info.lightEmission()
      );
      return new BakedQuad(
         quad.position0(), quad.position1(), quad.position2(), quad.position3(),
         quad.packedUV0(), quad.packedUV1(), quad.packedUV2(), quad.packedUV3(),
         quad.direction(), translucent
      );
   }

   @ModifyReturnValue(method = "shouldRenderFace", at = @At("RETURN"))
   private static boolean specter$drawFace(boolean original, BlockAndTintGetter level, BlockState state, Direction direction, BlockPos neighborPos) {
      return !Xray.active() ? original : Xray.drawFaceAgainst(state, level, neighborPos.relative(direction.getOpposite()), direction, original);
   }
}

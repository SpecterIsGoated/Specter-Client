package com.jfant.specter.mixin.move.sodium;

import com.jfant.specter.move.Xray;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.DefaultMaterials;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.Material;
import net.caffeinemc.mods.sodium.client.render.model.MutableQuadViewImpl;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
   value = {BlockRenderer.class},
   remap = false
)
public abstract class SodiumXrayBlockRendererMixin {
   @Unique
   private int specter$alpha = -1;

   @Inject(
      method = {"renderModel"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void specter$start(BlockStateModel model, BlockState state, BlockPos pos, BlockPos origin, CallbackInfo ci) {
      if (!Xray.active()) {
         this.specter$alpha = -1;
      } else {
         this.specter$alpha = Xray.alpha(state, null, null);
         if (this.specter$alpha == 0) {
            ci.cancel();
         }
      }
   }

   @Inject(
      method = {"bufferQuad"},
      at = {@At("HEAD")}
   )
   private void specter$tint(MutableQuadViewImpl quad, float[] brightnesses, Material material, CallbackInfo ci) {
      if (this.specter$alpha != -1) {
         for (int i = 0; i < 4; i++) {
            quad.setColor(i, (this.specter$alpha & 0xFF) << 24 | quad.baseColor(i) & 16777215);
         }
      }
   }

   @ModifyArg(
      method = {"processQuad"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/pipeline/BlockRenderer;bufferQuad(Lnet/caffeinemc/mods/sodium/client/render/model/MutableQuadViewImpl;[FLnet/caffeinemc/mods/sodium/client/render/chunk/terrain/material/Material;)V"
      ),
      index = 2
   )
   private Material specter$translucent(Material material) {
      return this.specter$alpha == -1 ? material : DefaultMaterials.TRANSLUCENT;
   }
}

package com.jfant.specter.mixin.move.sodium;

import com.jfant.specter.move.Xray;
import net.caffeinemc.mods.sodium.client.model.color.ColorProvider;
import net.caffeinemc.mods.sodium.client.model.light.LightPipeline;
import net.caffeinemc.mods.sodium.client.model.quad.ModelQuadViewMutable;
import net.caffeinemc.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.buffers.ChunkModelBuilder;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.DefaultFluidRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.DefaultMaterials;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.Material;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.TranslucentGeometryCollector;
import net.caffeinemc.mods.sodium.client.world.LevelSlice;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
   value = {DefaultFluidRenderer.class},
   remap = false
)
public abstract class SodiumXrayFluidMixin {
   @Final
   @Shadow
   private int[] quadColors;
   @Unique
   private int specter$alpha = -1;

   @Inject(
      method = {"render"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void specter$start(
      LevelSlice level,
      BlockState blockState,
      FluidState fluidState,
      BlockPos blockPos,
      BlockPos offset,
      TranslucentGeometryCollector collector,
      ChunkModelBuilder meshBuilder,
      Material material,
      ColorProvider<FluidState> colorProvider,
      TextureAtlasSprite[] sprites,
      CallbackInfo ci
   ) {
      if (!Xray.active()) {
         this.specter$alpha = -1;
      } else {
         this.specter$alpha = Xray.alpha(fluidState.createLegacyBlock(), level, blockPos);
         if (this.specter$alpha == 0) {
            ci.cancel();
         }
      }
   }

   @Inject(
      method = {"updateQuad"},
      at = {@At("TAIL")}
   )
   private void specter$tint(
      ModelQuadViewMutable quad,
      LevelSlice level,
      BlockPos pos,
      LightPipeline lighter,
      Direction dir,
      ModelQuadFacing facing,
      float brightness,
      ColorProvider<FluidState> colorProvider,
      FluidState fluidState,
      CallbackInfo ci
   ) {
      if (this.specter$alpha != -1) {
         for (int i = 0; i < 4; i++) {
            this.quadColors[i] = this.quadColors[i] & 16777215 | this.specter$alpha << 24;
         }
      }
   }

   @ModifyArg(
      method = {"render"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/pipeline/DefaultFluidRenderer;writeQuad(Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/buffers/ChunkModelBuilder;Lnet/caffeinemc/mods/sodium/client/render/chunk/translucent_sorting/TranslucentGeometryCollector;Lnet/caffeinemc/mods/sodium/client/render/chunk/terrain/material/Material;Lnet/minecraft/core/BlockPos;Lnet/caffeinemc/mods/sodium/client/model/quad/ModelQuadView;Lnet/caffeinemc/mods/sodium/client/model/quad/properties/ModelQuadFacing;Z)V"
      ),
      index = 2
   )
   private Material specter$translucent(Material material) {
      return this.specter$alpha == -1 ? material : DefaultMaterials.TRANSLUCENT;
   }
}

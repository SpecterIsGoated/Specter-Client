package com.jfant.specter.mixin.move.sodium;

import com.jfant.specter.move.Xray;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildBuffers;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.TranslucentGeometryCollector;
import net.caffeinemc.mods.sodium.client.world.LevelSlice;
import net.caffeinemc.mods.sodium.fabric.render.FluidRendererImpl;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
   value = {FluidRendererImpl.class},
   remap = false
)
public abstract class SodiumXrayFluidImplMixin {
   @Inject(
      method = {"render"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void specter$hide(
      LevelSlice level,
      BlockState blockState,
      FluidState fluidState,
      BlockPos blockPos,
      BlockPos offset,
      TranslucentGeometryCollector collector,
      ChunkBuildBuffers buffers,
      CallbackInfo ci
   ) {
      if (Xray.active() && Xray.alpha(fluidState.createLegacyBlock(), level, blockPos) == 0) {
         ci.cancel();
      }
   }
}

package com.jfant.specter.core.mixin;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.events.world.AmbientOcclusionEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({BlockBehaviour.class})
public abstract class AbstractBlockMixin {
   @Inject(
      method = {"getShadeBrightness"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onGetAmbientOcclusionLightLevel(BlockState state, BlockGetter world, BlockPos pos, CallbackInfoReturnable<Float> info) {
      AmbientOcclusionEvent event = (AmbientOcclusionEvent)SpecterCore.EVENT_BUS.post(AmbientOcclusionEvent.get());
      if (event.lightLevel != -1.0F) {
         info.setReturnValue(event.lightLevel);
      }
   }
}
